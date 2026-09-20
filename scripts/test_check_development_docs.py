"""Regression checks for mistakes observed in the handoff session."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('checks', Path(__file__).with_name('check-development-docs.py'))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


class DocumentChecks(unittest.TestCase):
    def test_literal_newlines_are_not_real_newlines(self):
        self.assertTrue(checks.problems(Path('plan.md'), 'next step' + r'\r\n' + 'one'))
        self.assertFalse(checks.problems(Path('plan.md'), 'next step\none\n'))

    def test_duplicate_daily_section_is_rejected(self):
        text = '\n' + '\n'.join('## ' + h + '\n' for h in (
            'Scope', 'Progress', 'Verification', 'Outstanding work and blockers', 'Next step'))
        path = Path('docs/development/2026-09-20.md')
        self.assertFalse(checks.problems(path, text))
        self.assertTrue(checks.problems(path, text + '\n## Next step\n'))


if __name__ == '__main__':
    unittest.main()
