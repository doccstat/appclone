import importlib.util
import json
from pathlib import Path
import re
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('i18n_policy', ROOT / 'tools/check_i18n.py')
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class PolicyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'res/values').mkdir(parents=True)
        (self.root / 'res/values/strings.xml').write_text('<resources/>')
        (self.root / 'polygo.lock').write_text('version = 2\n')
        self.config()

    def config(self, locales=(), model=''):
        (self.root / 'polygo.toml').write_text(
            'source_locale = "en"\ncontext = false\nmemory = false\n'
            f'target_locales = {json.dumps(locales)}\n[provider]\nkind = "ollama"\n'
            f'model = {json.dumps(model)}\nbase_url = "http://127.0.0.1:11434"\n')

    def test_empty_catalog_is_explicit_but_generation_refuses_it(self):
        policy.check(self.root)
        with self.assertRaisesRegex(ValueError, 'Choose target_locales'):
            policy.check(self.root, generation=True)

    def test_configured_missing_and_unconfigured_catalogs_fail(self):
        self.config(['de'], 'fixture:1')
        with self.assertRaisesRegex(ValueError, 'Catalogs and target_locales'):
            policy.check(self.root)
        policy.check(self.root, generation=True)
        (self.root / 'res/values-fr').mkdir()
        (self.root / 'res/values-fr/strings.xml').write_text('<resources/>')
        with self.assertRaisesRegex(ValueError, 'Catalogs and target_locales'):
            policy.check(self.root, generation=True)

    def test_xml_is_independent_and_legacy_lock_refused(self):
        self.config(['de'], 'fixture:1')
        path = self.root / 'res/values-de/strings.xml'
        path.parent.mkdir()
        path.write_text('<resources><string name="x"><xliff:g>%1$s</xliff:g></string></resources>')
        with self.assertRaises(policy.ET.ParseError):
            policy.check(self.root)
        path.write_text('<resources/>')
        (self.root / 'polygo.lock').write_text('version = 1\n')
        with self.assertRaisesRegex(ValueError, 'legacy migration'):
            policy.check(self.root)

    def test_all_locales_and_pending_states_must_be_checked(self):
        self.config(['de'], 'fixture:1')
        (self.root / 'res/values-de').mkdir()
        (self.root / 'res/values-de/strings.xml').write_text('<resources/>')
        counts = dict.fromkeys(policy.PENDING, 0) | {'edited': 2, 'up_to_date': 3}
        policy.check(self.root, status={'locales': {'de': counts}})
        for state in policy.PENDING:
            with self.subTest(state=state), self.assertRaisesRegex(ValueError, 'incomplete/stale/quarantined'):
                policy.check(self.root, status={'locales': {'de': counts | {state: 1}}})
        with self.assertRaisesRegex(ValueError, 'every configured locale'):
            policy.check(self.root, status={'locales': {}})

    def test_locale_paths_are_bounded_and_duplicates_fail(self):
        for locale in ('../fr', 'fr/../../x', 'pt_rBR', 'en', 'en-XA', 'ar-XB', ''):
            with self.subTest(locale=locale), self.assertRaises(ValueError):
                policy.locale_folder(locale)
        self.assertEqual('values-pt-rBR', policy.locale_folder('pt-BR'))
        self.assertEqual('values-b+zh+Hant', policy.locale_folder('zh-Hant'))
        self.config(['pt-br', 'pt-BR'], 'fixture:1')
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            policy.check(self.root, generation=True)

    def test_generation_refuses_unselected_model_and_remote_fallback(self):
        self.config(['de'])
        with self.assertRaisesRegex(ValueError, 'Choose target_locales'):
            policy.check(self.root, generation=True)
        self.config(['de'], 'fixture:1')
        config = self.root / 'polygo.toml'
        config.write_text(config.read_text().replace('127.0.0.1', 'example.com'))
        with self.assertRaisesRegex(ValueError, 'no remote inference fallback'):
            policy.check(self.root, generation=True)

    def test_identical_exemptions_contain_no_english_prose(self):
        config = policy.tomllib.loads((ROOT / 'polygo.toml').read_text())
        source = policy.ET.parse(ROOT / 'res/values/strings.xml').getroot()
        for key, ignored in config['keys']['ignore'].items():
            text = ''.join(source.find(f'string[@name="{key}"]').itertext())
            self.assertEqual(['identical'], ignored)
            self.assertNotRegex(re.sub(r'\\[nt]|%\d+\$[sd]', '', text), r'[A-Za-z]')

    def test_workflows_gate_builds_without_release_credentials_or_automatic_generation(self):
        for name in ('android.yml', 'release.yml'):
            text = (ROOT / '.github/workflows' / name).read_text()
            self.assertIn('uses: ./.github/workflows/translations.yml', text)
            self.assertIn('needs: translations', text)
            self.assertNotIn('generate: true', text)
        workflow = (ROOT / '.github/workflows/translations.yml').read_text()
        for required in ('--strict --no-baseline', '--status', 'build.py --lint',
                         'build.py --self-check', '--locked', 'persist-credentials: false'):
            self.assertIn(required, workflow)
        for forbidden in ('secrets.', 'contents: write', 'git push', 'pull_request_target:', 'uses: doccstat/'):
            self.assertNotIn(forbidden, workflow)
        self.assertLess(workflow.index('Independently validate'), workflow.index('actions/upload-artifact@'))


if __name__ == '__main__':
    unittest.main()
