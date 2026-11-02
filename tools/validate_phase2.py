#!/usr/bin/env python3
"""Static validation for T-UI Expert X Phase 2 source changes."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []
def require(condition, label):
    checks.append((label, bool(condition)))

# Parse all XML files under app resources and manifests.
xml_files = list((ROOT / 'app/src').rglob('*.xml'))
xml_ok = True
for file in xml_files:
    try:
        ET.parse(file)
    except Exception as exc:
        xml_ok = False
        print(f'XML ERROR: {file.relative_to(ROOT)}: {exc}')
require(bool(xml_files) and xml_ok, f'All {len(xml_files)} Android XML files parse')

monitor = ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/monitor.java'
source = monitor.read_text(encoding='utf-8') if monitor.exists() else ''
for name in ['all', 'battery', 'cpu', 'memory', 'storage', 'network', 'uptime', 'device', 'watch', 'stop', 'help']:
    require(re.search(r'\b' + name + r'\s*\{', source) is not None, f'monitor -{name} handler exists')
require('scheduleWithFixedDelay' in source and 'WATCH_INTERVAL_SECONDS = 5L' in source, 'Watch refresh interval is five seconds')
require('shutdownNow()' in source and 'watchTask.cancel(true)' in source, 'Watch mode can be stopped')
require('getApplicationContext()' in source, 'Background work uses application context')
require('ProcessBuilder' not in source and 'Runtime.getRuntime().exec' not in source, 'Monitor does not spawn shell commands')
require('registerReceiver(null' in source and 'ACTION_BATTERY_CHANGED' in source, 'Battery state uses sticky battery broadcast')
require('getMemoryInfo' in source and 'totalMem' in source, 'RAM reporting uses ActivityManager')
require('new StatFs' in source and 'getAvailableBlocksLong' in source, 'Storage reporting uses StatFs')
require('/proc/stat' in source, 'CPU reads aggregate procfs counters')
require('elapsedRealtime()' in source, 'Uptime uses monotonic elapsed realtime')

strings = ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()
string_names = {x.attrib.get('name') for x in strings.findall('string')}
require('help_monitor' in string_names, 'monitor help resource exists')
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
permissions = {x.attrib.get(android+'name') for x in manifest.findall('uses-permission')}
require('android.permission.ACCESS_NETWORK_STATE' in permissions, 'Existing network state permission retained')
require('android.permission.BATTERY_STATS' not in permissions, 'No privileged battery stats permission requested')

build = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
require('applicationId "com.thedarkgeekdc.tuiexpert"' in build, 'TheDarkGeekDC application ID retained')
require('versionName "0.5.2"' in build and 'versionCode 11' in build, 'Phase 2 version is 0.5.2 / 11')
readme = (ROOT / 'app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
require('Francesco Andreuzzi' in readme and 'v1nc' in readme and 'Yehoslav' in readme and 'TheDarkGeekDC' in readme, 'Upstream and maintainer credits retained in About')
require((ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/cyber.java').exists(), 'Phase 1 cyber command retained')

for label, ok in checks:
    print(('PASS' if ok else 'FAIL') + '  ' + label)
passed = sum(1 for _, ok in checks if ok)
print(f'\n{passed}/{len(checks)} checks passed')
sys.exit(0 if passed == len(checks) else 1)
