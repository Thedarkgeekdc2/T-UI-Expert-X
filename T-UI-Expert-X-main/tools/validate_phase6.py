#!/usr/bin/env python3
"""Static checks for T-UI Expert X v0.5.8 home-screen, automation and LAN remote fixes."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []
def check(label, condition):
    checks.append((label, bool(condition)))

# Parse all XML source/resources and bundled profile data.
xml_files = list((ROOT / "app/src").rglob("*.xml"))
xml_errors = []
for path in xml_files:
    try:
        ET.parse(path)
    except Exception as exc:
        xml_errors.append(f"{path.relative_to(ROOT)}: {exc}")
check(f"all {len(xml_files)} XML source/profile files parse", bool(xml_files) and not xml_errors)

base = ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert"
remote = (base / "managers/remote/RemoteServerService.java").read_text(encoding="utf-8")
remote_cmd = (base / "commands/main/raw/remote.java").read_text(encoding="utf-8")
automate = (base / "commands/main/raw/automate.java").read_text(encoding="utf-8")
receiver = (base / "commands/main/raw/AutomationReceiver.java").read_text(encoding="utf-8")
suggestions = (base / "managers/suggestions/SuggestionsManager.java").read_text(encoding="utf-8")
runnable = (base / "managers/suggestions/SuggestionRunnable.java").read_text(encoding="utf-8")
installer = (base / "managers/ProfileInstaller.java").read_text(encoding="utf-8")
launcher = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/LauncherActivity.java").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"

# Release metadata and dependency.
check("application ID remains com.thedarkgeekdc.tuiexpert", 'applicationId "com.thedarkgeekdc.tuiexpert"' in gradle)
check("version is 0.5.8 / versionCode 17", 'versionName "0.5.8"' in gradle and 'versionCode 17' in gradle)
check("NanoHTTPD dependency is declared", "org.nanohttpd:nanohttpd:2.3.1" in gradle)

# Battery automation reliability.
check("battery command checks current battery immediately", "AutomationReceiver.checkBattery(pack.context)" in automate)
check("automation test command exists", re.search(r"\btest\s*\{", automate) is not None and "-test -> send a test notification" in (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8"))
check("poll is one-shot idle-aware on Android M+", "setAndAllowWhileIdle" in automate)
check("poll is re-armed by the receiver", "automate.updatePollForReceiver(app)" in receiver)
check("notification state is reported", "notificationsAvailable(context)" in automate and "Notification permission" in automate)
check("battery latch is only set after notification posts", "if (posted) p.edit().putBoolean(\"battery_fired\", true).apply()" in receiver)

# Classic home screen and aliases.
profile_suggestions = (ROOT / "app/src/main/assets/default_profile/suggestions.xml").read_text(encoding="utf-8")
check("classic alias-first home strip is restored", '<noinput_suggestions_order value="0(0)1(15)2(3)3(5)"/>' in profile_suggestions)
check("previous broken zero-alias preference is migrated", '"0(8)1(0)2(0)3(0)".equals(emptyOrder.trim())' in installer)
check("migration backs up suggestions.xml first", "phase5-home-suggestions-original.xml" in installer and "copyFile(suggestions, backup)" in installer)
check("home alias shortcuts remain tappable", "(homeShortcut || clickToLaunch) && !a.isParametrized" in suggestions)
check("existing alias file is not rewritten by home migration", '"alias.txt"' not in installer.split("migrateRequestedHomeBehaviour(File destination)", 1)[1].split("    }", 1)[0])
check("Nerd Font asset restored", (ROOT / "app/src/main/assets/NerdFont.ttf").is_file())
check("Nerd Font seeded only when no user font is present", "seedNerdFontIfNoCustomFont" in installer and "font already selected by the user" in installer)
check("app suggestion chips launch regardless of click preference", "canClickToLaunch ? l : null" in suggestions and "canClickToLaunch ? i : null" in suggestions)
check("native launcher icon is used for apps", "getActivityIcon(launchInfo.componentName)" in runnable)
check("app icons are cleared from recycled non-app suggestions", "view.setCompoundDrawables(null, null, null, null)" in runnable)
check("old T-UI UI is not replaced by a new root activity", 'android:name="de.reckendrees.systems.tui.expert.LauncherActivity"' in (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8"))

# LAN server / commands / manifest.
for name in ["start", "stop", "status", "credentials", "autoon", "autooff", "help"]:
    check(f"remote -{name} command exists", re.search(r"\b" + name + r"\s*\{", remote_cmd) is not None)
service_list = manifest.findall("application/service")
remote_service = [s for s in service_list if s.attrib.get(android + "name", "").endswith(".RemoteServerService")]
check("remote service is declared non-exported", len(remote_service) == 1 and remote_service[0].attrib.get(android + "exported") == "false")
permissions = {p.attrib.get(android + "name") for p in manifest.findall("uses-permission")}
check("internet, Wi-Fi and network permissions exist", {"android.permission.INTERNET", "android.permission.ACCESS_WIFI_STATE", "android.permission.ACCESS_NETWORK_STATE"}.issubset(permissions))
check("server binds to phone Wi-Fi IPv4 address", "new RemoteHttpServer(hostAddress, port, this)" in remote and "getIpAddress()" in remote)
check("same subnet is checked for every request", "isSameWifiSubnet(session)" in remote and "getRemoteIpAddress()" in remote)
check("login uses POST and bearer token", '"/api/login".equals(uri) && method == Method.POST' in remote and '"Bearer "' in remote)
check("password is not read from a query-string field", 'session.getParms()' in remote and 'get("password")' in remote and 'getQueryParameterString' not in remote)
check("password fallback is stored only in private preferences", 'getSharedPreferences(PREFS, Context.MODE_PRIVATE)' in remote and 'putString("generated_password", generated)' in remote)
check("existing remote_pass is returned without modifying it", "return configured; // Preserve existing remote credentials byte-for-byte" in remote)
check("login sessions expire and logout revokes token", "12L * 60L * 60L * 1000L" in remote and "tokens.remove(bearer(session))" in remote and "fetch('/api/logout'" in remote)
check("login attempts are rate-limited", "failedLogins >= 8" in remote)
check("share root is restricted to TUI-Share", 'new File(downloads, "TUI-Share")' in remote and 'new File(appFiles, "TUI-Share")' in remote)
check("path traversal is rejected with canonical path check", "getCanonicalPath()" in remote and "Path is outside the TUI-Share folder" in remote)
check("uploads and downloads are limited to 50 MB", "MAX_FILE_BYTES = 50L * 1024L * 1024L" in remote)
check("whole device storage and credential files are not shared", "expert.xml" in remote and "alias.txt" in remote and "getCanonicalPath()" in remote and "Environment.getExternalStorageDirectory()" not in remote)
check("foreground service notification can stop server and disable auto-start", "PendingIntent.getForegroundService" in remote and 'write(Expert.remote_autostart, "false")' in remote)
check("server auto-start checked on launcher open", "RemoteServerService.startIfEnabled(this)" in launcher)
check("remote page has two file panes and terminal", "Pane 1" in remote and "Pane 2" in remote and "Remote T-UI terminal" in remote)
check("screen mirroring limitation is documented", "does not include" in (ROOT / "PHASE_6_LAN_REMOTE.md").read_text(encoding="utf-8") and "screen mirroring" in (ROOT / "PHASE_6_LAN_REMOTE.md").read_text(encoding="utf-8"))
check("LAN HTTP limitation and no Internet exposure are documented", "HTTP is not encrypted" in (ROOT / "PHASE_6_LAN_REMOTE.md").read_text(encoding="utf-8") and "port forwarding" in (ROOT / "PHASE_6_LAN_REMOTE.md").read_text(encoding="utf-8"))
check("personal alias and credential files are not packaged", not (ROOT / "app/src/main/assets/default_profile/alias.txt").exists() and '<remote_pass value=""/>' in (ROOT / "app/src/main/assets/default_profile/expert.xml").read_text(encoding="utf-8"))

for label, ok in checks:
    print(("PASS" if ok else "FAIL") + "  " + label)
passed = sum(ok for _, ok in checks)
print(f"\n{passed}/{len(checks)} checks passed")
if xml_errors:
    print("XML errors:")
    for error in xml_errors:
        print("  " + error)
sys.exit(0 if passed == len(checks) and not xml_errors else 1)
