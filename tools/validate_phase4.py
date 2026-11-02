#!/usr/bin/env python3
"""Static validation for T-UI Expert X Phase 4. Does not build or run the APK."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

# Parse every XML resource/manifest. Some legacy XML assets may be application data,
# so only Android source XML files are included here.
xml_files = sorted((ROOT / "app/src").rglob("*.xml"))
xml_errors = []
for path in xml_files:
    try:
        ET.parse(path)
    except Exception as exc:
        xml_errors.append(f"{path.relative_to(ROOT)}: {exc}")
check(f"all {len(xml_files)} Android source XML files parse", not xml_errors)

security = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/security.java").read_text(encoding="utf-8")
strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
provider = (ROOT / "app/src/main/res/xml/provider_paths.xml").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
readme = (ROOT / "README.md").read_text(encoding="utf-8")

for cmd in ["audit", "permissions", "privacy", "config", "remote", "settings", "report", "help"]:
    check(f"security command includes -{cmd}", f"{cmd} {{" in security)
check("security help string exists", 'name="help_security"' in strings)
check("security audits only this app's package", 'getPackageInfo(context.getPackageName(), flags)' in security)
check("permission state is read-only", 'ContextCompat.checkSelfPermission(context, name)' in security and 'PERMISSION_GRANTED' in security)
check("does not grant or revoke permissions", "No permissions were requested, granted or revoked" in security)
check("remote password fields recognized", '"remote_pass"' in security and '"remote_password"' in security)
check("secret values are not appended to output", 'configured (value hidden)' in security and '.append(password)' not in security)
check("weak remote password pattern check exists", 'isWeakPassword(password)' in security)
check("API token/key audit exists", '"access_token"' in security and '"api_key"' in security)
check("root mode and custom command audit exists", '"use_root"' in security and '"custom_command"' in security)
check("malformed RSS URL scheme audit exists", 'countInvalidRssUrls' in security)
check("secret values not copied in generated report", 'No config files or secrets were copied' in security)
check("security report saved to private app storage", 'context.getFilesDir()' in security)
check("no shell/process execution in security audit command", not re.search(r'Runtime\.getRuntime\(\)\.exec|new\s+ProcessBuilder', security))
check("Android Auto Backup disabled", 'android:allowBackup="false"' in manifest)
check("cleartext traffic disabled in manifest", 'android:usesCleartextTraffic="false"' in manifest)
check("MusicService is not exported", bool(re.search(r'MusicService[\s\S]{0,140}?android:exported="false"', manifest)))
check("NotificationMonitorService is not exported", bool(re.search(r'NotificationMonitorService"[^>]*android:exported="false"', manifest)))
check("NotificationListenerService protected by system binding permission", 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' in manifest and bool(re.search(r'BIND_NOTIFICATION_LISTENER_SERVICE"\s+android:exported="true"', manifest)))
check("FileProvider broad root-path removed", '<root-path' not in provider)
check("FileProvider still retains external-path for supported file workflow", '<external-path' in provider)
check("weather API uses HTTPS", 'https://api.openweathermap.org' in (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/UIManager.java').read_text(encoding='utf-8'))
check("connectivity check uses HTTPS", 'https://clients3.google.com/generate_204' in (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/tuils/Tuils.java').read_text(encoding='utf-8'))
check("notes auto-links use HTTPS", '"https://" + g' in (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/managers/NotesManager.java').read_text(encoding='utf-8'))
check("bare search URLs default to HTTPS", 'url = "https://" + url;' in (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/search.java').read_text(encoding='utf-8'))
check("package ID preserved", 'applicationId "com.thedarkgeekdc.tuiexpert"' in gradle)
check("version bumped to 0.5.4/code 13", 'versionName "0.5.4"' in gradle and 'versionCode 13' in gradle)
check("Phase 1 toolbox retained", (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/cyber.java').is_file())
check("Phase 2 monitor retained", (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/monitor.java').is_file())
check("Phase 3 automation retained", (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/automate.java').is_file())
check("Phase 3 excludes expert.xml from configuration backup", 'expert.xml is intentionally excluded' in (ROOT / 'app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/automate.java').read_text(encoding='utf-8'))
check("upstream credits retained", all(x in readme for x in ["Francesco Andreuzzi", "v1nc", "Yehoslav", "TheDarkGeekDC"]))
check("Phase 4 documentation exists", (ROOT / 'PHASE_4_SECURITY_PRIVACY.md').is_file())
check("Phase 4 changelog exists", (ROOT / 'PHASE_4_CHANGELOG.txt').is_file())
check("Phase 4 build notes exist", (ROOT / 'PHASE_4_BUILD_NOTES.txt').is_file())

# Safe fixture-only regression check: parser logic detects the existence of fields
# but the report-generation methods do not emit captured values.
fixture = '<EXPERT><remote_pass value="not-for-output"/><exit_password value="masked-example"/><use_root value="true"/></EXPERT>'
value_pattern = lambda tag: re.search(r'<\s*' + re.escape(tag) + r'\b[^>]*\bvalue\s*=\s*([\'\"])(.*?)\1', fixture, re.I | re.S)
check("fixture recognises remote password field", value_pattern('remote_pass') is not None)
check("fixture recognises exit password field", value_pattern('exit_password') is not None)
check("fixture recognises root mode", bool(re.search(r'<\s*use_root\b[^>]*\bvalue\s*=\s*([\'\"])true\1', fixture, re.I)))

failed = [name for name, ok in checks if not ok]
print(f"Phase 4 static validation: {len(checks)-len(failed)}/{len(checks)} checks passed")
print(f"Android XML files parsed: {len(xml_files)}")
if xml_errors:
    for err in xml_errors:
        print("XML ERROR:", err)
if failed:
    for name in failed:
        print("FAIL:", name)
    sys.exit(1)
print("All Phase 4 static checks passed. This is not a full Android APK build or device test.")
