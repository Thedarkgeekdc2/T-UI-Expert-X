#!/usr/bin/env python3
"""Static validation for T-UI Expert X Phase 3 automation source."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []
def require(condition, label):
    checks.append((label, bool(condition)))

# Every Android XML resource and manifest must parse.
xml_files = list((ROOT / 'app/src').rglob('*.xml'))
xml_ok = True
for path in xml_files:
    try:
        ET.parse(path)
    except Exception as exc:
        xml_ok = False
        print(f'XML ERROR: {path.relative_to(ROOT)}: {exc}')
require(bool(xml_files) and xml_ok, f'All {len(xml_files)} Android XML files parse')

base = ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw'
automate = (base / 'automate.java').read_text(encoding='utf-8')
receiver = (base / 'AutomationReceiver.java').read_text(encoding='utf-8')
for name in ['status', 'battery', 'test', 'batteryoff', 'chargeon', 'chargeoff', 'wifion', 'wifioff', 'timer', 'daily', 'cancel', 'alloff', 'backup', 'help']:
    require(re.search(r'\b' + name + r'\s*\{', automate) is not None, f'automate -{name} handler exists')
require('extends ParamCommand' in automate, 'Automation command integrates with T-UI ParamCommand framework')
require('getApplicationContext()' in automate, 'Persistent operations use application context')
require('setAndAllowWhileIdle' in automate and 'setInexactRepeating' in automate, 'Recurring checks use idle-aware one-shot alarms on Android 6+ and inexact repeating alarms on older Android')
require('POLL_INTERVAL_MS = 15L * 60L * 1000L' in automate, 'Battery/network polling interval is 15 minutes')
require('threshold < 5 || threshold > 50' in automate, 'Battery alert threshold constrained to 5–50%')
require('minutes < 1 || minutes > 10080' in automate, 'One-time reminder limited to 1 minute–7 days')
require('matches("(?:[01]?[0-9]|2[0-3]):[0-5][0-9]")' in automate, 'Daily routine time uses 24-hour HH:mm validation')
require('scheduleDailyFromPrefs' in receiver and 'ACTION_BOOT_COMPLETED' in automate, 'Daily reminder can restore on boot')
require('ACTION_TIME_CHANGED' in receiver and 'ACTION_TIMEZONE_CHANGED' in receiver, 'Daily reminder rescheduled after clock/timezone changes')
require('ACTION_POWER_CONNECTED' in receiver and 'ACTION_POWER_DISCONNECTED' in receiver, 'Charging connect/disconnect receiver handlers exist')
require('NotificationChannel' in receiver and 'setChannelId(CHANNEL_ID)' in receiver, 'Android 8+ notification channel is configured')
require('ConnectivityManager' in receiver and 'getActiveNetworkInfo' in receiver, 'Network monitoring observes coarse connection state')
require('expert.xml' in automate and 'crash.txt' in automate and 'allowed' in automate and 'String[] allowed' in automate, 'Backup has explicit allowlist and excludes credentials/crash logs')
require('getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)' in automate, 'Backup is stored in app-specific Documents area')
require('Runtime.getRuntime().exec' not in automate and 'ProcessBuilder' not in automate and 'Shell.SU' not in automate, 'Automation never executes shell or root commands')
require('automate -backup' in (ROOT / 'PHASE_3_AUTOMATION_ENGINE.md').read_text(), 'Phase 3 guide documents backup command')

manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
permissions = {x.attrib.get(android + 'name') for x in manifest.findall('uses-permission')}
require('android.permission.RECEIVE_BOOT_COMPLETED' in permissions, 'Boot restore permission is declared')
require('android.permission.SCHEDULE_EXACT_ALARM' not in permissions, 'No special exact-alarm access is requested')
receivers = manifest.findall('application/receiver')
auto_receiver = [r for r in receivers if r.attrib.get(android + 'name', '').endswith('.AutomationReceiver')]
require(len(auto_receiver) == 1 and auto_receiver[0].attrib.get(android + 'exported') == 'false', 'Automation receiver is explicit and non-exported')

strings_root = ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()
string_names = {x.attrib.get('name') for x in strings_root.findall('string')}
require('help_automate' in string_names, 'Automation help string resource exists')
require('ic_launcher.png' in str(ROOT / 'app/src/main/res/mipmap-mdpi/ic_launcher.png'), 'Notification launcher icon resource path exists')

build = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
require('applicationId "com.thedarkgeekdc.tuiexpert"' in build, 'TheDarkGeekDC application ID retained')
require('versionName "0.5.8"' in build and 'versionCode 17' in build, 'current version is 0.5.8 / 17')
require((base / 'cyber.java').exists(), 'Phase 1 Cyber Toolbox retained')
require((base / 'monitor.java').exists(), 'Phase 2 Device Monitor retained')
credits = (ROOT / 'ATTRIBUTION.md').read_text(encoding='utf-8')
require(all(name in credits for name in ['Francesco Andreuzzi', 'v1nc', 'Yehoslav', 'TheDarkGeekDC']), 'Original upstream credits and current maintainer attribution retained')

for label, ok in checks:
    print(('PASS' if ok else 'FAIL') + '  ' + label)
passed = sum(ok for _, ok in checks)
print(f'\n{passed}/{len(checks)} checks passed')
sys.exit(0 if passed == len(checks) else 1)
