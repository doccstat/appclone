"""Bundled first-batch regressions, independent of the translation provider."""
from collections import Counter
import re
import unittest

from test_policy import ROOT, policy


LOCALES = ('zh-Hans', 'zh-Hant', 'es', 'hi', 'bn', 'ru', 'ja', 'tr')
QUANTITIES = {locale: {'one', 'other'} for locale in LOCALES}
QUANTITIES.update({locale: {'other'} for locale in ('zh-Hans', 'zh-Hant', 'ja')})
QUANTITIES['ru'] = {'one', 'few', 'many', 'other'}
QUANTITIES['es'] = {'one', 'many', 'other'}
XLIFF = '{urn:oasis:names:tc:xliff:document:1.2}g'


def text(node):
    return ''.join(node.itertext())


class CatalogTests(unittest.TestCase):
    def test_first_batch_is_bundled_and_complete_not_just_declared(self):
        cfg = policy.tomllib.loads((ROOT / 'polygo.toml').read_text())
        self.assertTrue(set(LOCALES) <= set(cfg['target_locales']))
        source = {e.get('name'): e for e in policy.ET.parse(ROOT / 'res/values/strings.xml').getroot()
                  if e.get('translatable') != 'false'}
        for locale in LOCALES:
            with self.subTest(locale=locale):
                path = ROOT / 'res' / policy.locale_folder(locale) / 'strings.xml'
                entries = list(policy.ET.parse(path).getroot())
                self.assertEqual(len(source), len(entries))
                self.assertEqual(set(source), {e.get('name') for e in entries})
                for entry in entries:
                    original = source[entry.get('name')]
                    self.assertEqual(original.tag, entry.tag)
                    if entry.tag == 'plurals':
                        self.assertEqual(QUANTITIES[locale], {e.get('quantity') for e in entry})
                        self.assertEqual(len(QUANTITIES[locale]), len(entry))
                        forms = {e.get('quantity'): e for e in original}
                        pairs = [(forms.get(e.get('quantity'), forms['other']), e) for e in entry]
                    else:
                        pairs = [(original, entry)]
                    for src, dst in pairs:
                        self.assertTrue(text(dst).strip())
                        for pattern in (r'%\d+\$[sd]', r'\{\w+\}', r'\\n'):
                            self.assertEqual(Counter(re.findall(pattern, text(src))),
                                             Counter(re.findall(pattern, text(dst))), entry.get('name'))
                        protected = lambda node: sorted((e.get('id'), e.get('example'), text(e))
                                                        for e in node.iter(XLIFF))
                        self.assertEqual(protected(src), protected(dst))
                        self.assertNotIn('¤ ', text(dst))
                        if entry.get('name') not in cfg['keys']['ignore']:
                            # Spanish and English genuinely share the plural "clones".
                            if not (locale == 'es' and entry.get('name') == 'clone_count'
                                    and dst.get('quantity') in ('many', 'other')):
                                self.assertNotEqual(text(src), text(dst), (locale, entry.get('name')))

    def test_spanish_many_forms_cannot_drift_from_the_tracked_other_forms(self):
        # shortcut: the pinned CLI omits Spanish many from status; keep these integer forms paired until it tracks CLDR millions.
        lock = policy.tomllib.loads((ROOT / 'polygo.lock').read_text())
        catalog = policy.ET.parse(ROOT / 'res/values-es/strings.xml').getroot()
        for plural in catalog.findall('plurals'):
            forms = {e.get('quantity'): text(e) for e in plural}
            self.assertEqual(forms['other'], forms['many'])
            records = [lock['keys'][plural.get('name') + '#plural.' + q]['locales']['es']
                       for q in ('other', 'many')]
            for field in ('source', 'hash'):
                self.assertEqual(records[0][field], records[1][field])

    def test_every_bundled_unit_has_source_tracking_and_provenance(self):
        lock = policy.tomllib.loads((ROOT / 'polygo.lock').read_text())
        source = policy.ET.parse(ROOT / 'res/values/strings.xml').getroot()
        for locale in LOCALES:
            for entry in source:
                if entry.get('translatable') == 'false':
                    continue
                keys = ([entry.get('name') + '#plural.' + q for q in QUANTITIES[locale]]
                        if entry.tag == 'plurals' else [entry.get('name')])
                for key in keys:
                    record = lock['keys'][key]['locales'][locale]
                    self.assertTrue(record['provider'])
                    self.assertTrue(record['model'])
                    self.assertRegex(record['source'], r'^[a-f0-9]{32}$')
                    self.assertRegex(record['hash'], r'^[a-f0-9]{32}$')
        self.assertTrue(all(not key.get('review') for key in lock['keys'].values()))


if __name__ == '__main__':
    unittest.main()
