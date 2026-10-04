"""Compile and exercise the actual config getter, without touching /data/adb."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def function(source, name):
    start = source.index('pub fn ' + name + '(')
    brace = source.index('{', start)
    depth = 0
    for end in range(brace, len(source)):
        depth += (source[end] == '{') - (source[end] == '}')
        if not depth:
            return source[start:end + 1]
    raise AssertionError('Unbalanced Rust function')


class RiskDefaultTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('rustc'), 'rustc is required for runtime regression cases')
    def test_actual_config_getter(self):
        risk = (ROOT / 'userspace/ksud/src/risk.rs').read_text(encoding='utf8')
        config = (ROOT / 'userspace/ksud/src/module_config.rs').read_text(encoding='utf8')
        harness = '''
const RISK_CONFIG_MODULE_ID: &str = "internal.ksud.risk";
const RISK_CONFIG_KEY: &str = "enabled";
mod module_config {
    use std::cell::RefCell;
    type Value = Result<Option<String>, &'static str>;
    thread_local! { static CONFIG: RefCell<Value> = const { RefCell::new(Ok(None)) }; }
    pub enum ConfigType { Persist }
    pub fn get_config_value(id: &str, key: &str, _: ConfigType) -> Value {
        assert_eq!(id, "internal.ksud.risk");
        assert_eq!(key, "enabled");
        CONFIG.with(|value| value.borrow().clone())
    }
    pub fn set(value: Value) { CONFIG.with(|slot| *slot.borrow_mut() = value); }
    __PARSE_BOOL__
}
__GET_RISK__
#[test]
fn missing_config_is_disabled() {
    module_config::set(Ok(None));
    assert!(!is_risk_detection_enabled());
}
#[test]
fn unreadable_config_is_disabled() {
    module_config::set(Err("unreadable configuration"));
    assert!(!is_risk_detection_enabled());
}
#[test]
fn explicit_enable_and_disable_are_preserved() {
    for (text, expected) in [("true", true), (" TRUE ", true), ("1", true),
                             ("false", false), ("0", false), ("invalid", false)] {
        module_config::set(Ok(Some(text.to_owned())));
        assert_eq!(is_risk_detection_enabled(), expected, "{text}");
    }
}
'''
        harness = harness.replace('__PARSE_BOOL__', function(config, 'parse_bool_config'))
        harness = harness.replace('__GET_RISK__', function(risk, 'is_risk_detection_enabled'))
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / 'risk_default.rs'
            binary = Path(temp) / 'risk_default_test'
            source.write_text(harness, encoding='utf8')
            subprocess.run(['rustc', '--edition=2024', '--test', str(source), '-o', str(binary)], check=True)
            subprocess.run([str(binary)], check=True)

    def test_disabled_scan_is_short_circuited(self):
        source = (ROOT / 'userspace/ksud/src/module.rs').read_text(encoding='utf8')
        self.assertIn('if is_risk_detection_enabled() && let Some(risk_match) = contains_risk_in_module(&zip_path)', source)
        self.assertIn('validate_module_id(module_id)', source)


if __name__ == '__main__':
    unittest.main()
