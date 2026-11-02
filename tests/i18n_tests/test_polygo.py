"""Full-catalog transport/writer regression with a loopback fixture, not a real model."""
import http.server
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import threading
import unittest
import xml.etree.ElementTree as ET

from test_policy import ROOT, policy


class FixtureProvider(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, data):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def do_GET(self):
        self.reply({'models': [{'name': 'fixture:1', 'model': 'fixture:1'}]})

    def do_POST(self):
        request = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        user = request['messages'][-1]['content']
        # Preserve multiline source including XML; never use the line-only mock parser.
        pattern = r'^### \d+ key: (.+)\nsource \([^\n]+\): ([\s\S]*?)(?=\n(?:developer comment:|used in:|similar existing translations:)|\n\n(?:### |Now return))'
        translations = [{'key': key, 'translation': '¤ ' + text}
                        for key, text in re.findall(pattern, user, re.M)]
        self.reply({'message': {'content': json.dumps({'translations': translations})}})


@unittest.skipUnless(os.environ.get('POLYGO_BIN'), 'Set POLYGO_BIN to test the actual CLI without inference')
class PolygoTests(unittest.TestCase):
    def test_full_catalog_plurals_markup_multiline_incremental_and_corrections(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            shutil.copytree(ROOT / 'res', root / 'res')
            # The trial starts empty even after production locales are introduced.
            for path in (root / 'res').glob('values-*/strings.xml'):
                path.unlink()
            shutil.copyfile(ROOT / 'glossary.toml', root / 'glossary.toml')
            (root / 'polygo.lock').write_text('version = 2\n')
            server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), FixtureProvider)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            self.addCleanup(server.server_close)
            self.addCleanup(server.shutdown)
            config = (ROOT / 'polygo.toml').read_text()
            config = re.sub(r'^target_locales = .*$', 'target_locales = ["ru", "ar", "zh-Hans"]', config, flags=re.M)
            config = re.sub(r'^model = .*$', 'model = "fixture:1"', config, flags=re.M)
            config = re.sub(r'^base_url = .*$', f'base_url = "http://127.0.0.1:{server.server_port}"', config, flags=re.M)
            (root / 'polygo.toml').write_text(config)

            def cli(*args):
                result = subprocess.run([os.environ['POLYGO_BIN'], '-C', str(root), *args],
                                        env=dict(os.environ, POLYGO_CONFIG_DIR=str(root / 'cfg')),
                                        text=True, capture_output=True, timeout=60)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                return result.stdout

            source = root / 'res/values/strings.xml'
            original = source.read_bytes()
            cli('translate')
            cli('check', '--strict', '--no-baseline')
            policy.check(root, status=json.loads(cli('status', '--json')))
            fixed = {e.get('name') for e in ET.parse(source).getroot() if e.get('translatable') == 'false'}
            for locale, categories in [('ru', {'one', 'few', 'many', 'other'}),
                                       ('ar', {'zero', 'one', 'two', 'few', 'many', 'other'}),
                                       ('zh-Hans', {'other'})]:
                path = root / 'res' / policy.locale_folder(locale) / 'strings.xml'
                resources = ET.parse(path).getroot()
                self.assertFalse(fixed & {e.get('name') for e in resources})
                for plural in resources.findall('plurals'):
                    self.assertEqual(categories, {e.get('quantity') for e in plural})
                self.assertIsNotNone(resources.find('string[@name="delete_confirmation"]/{urn:oasis:names:tc:xliff:document:1.2}g'))
                raw = path.read_text()
                self.assertIn(r'%1$s\n%2$s\n\n%3$s', raw)
                for token in ('{id}', '{name}', '{clone}', 'RUN_ANY_IN_BACKGROUND'):
                    self.assertIn(token, raw)
            self.assertEqual(original, source.read_bytes())
            ru = root / 'res/values-ru/strings.xml'
            ru.write_text(ru.read_text().replace('¤ Settings', 'Human correction'))
            before = ru.read_bytes()
            cli('translate')
            self.assertEqual(before, ru.read_bytes())
            source.write_text(source.read_text().replace('>Refresh</string>', '>Refresh now</string>'))
            cli('translate', '--locale', 'ru')
            state = json.loads(cli('status', '--json'))
            self.assertEqual(0, state['locales']['ru']['stale'])
            self.assertEqual(1, state['locales']['ar']['stale'])
            self.assertEqual(1, state['locales']['zh-Hans']['stale'])
            with self.assertRaisesRegex(ValueError, 'incomplete/stale/quarantined'):
                policy.check(root, status=state)
            cli('translate')
            policy.check(root, status=json.loads(cli('status', '--json')))
            self.assertIn('Human correction', ru.read_text())


if __name__ == '__main__':
    unittest.main()
