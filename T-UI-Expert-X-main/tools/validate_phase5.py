#!/usr/bin/env python3
"""Static validation for T-UI Expert X Phase 5 dashboard and screen profile."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []
def check(label, ok):
    checks.append((label, bool(ok)))

# Source and bundled profile XML must all parse.
source_xml = sorted((ROOT / "app/src").rglob("*.xml"))
profile_xml = sorted((ROOT / "app/src/main/assets/default_profile").glob("*.xml"))
errors = []
for path in source_xml + profile_xml:
    try:
        ET.parse(path)
    except Exception as exc:
        errors.append(f"{path.relative_to(ROOT)}: {exc}")
check(f"all {len(source_xml)} source + {len(profile_xml)} profile XML files parse", not errors)

base = ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw"
dashboard = (base / "dashboard.java").read_text(encoding="utf-8")
installer = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/managers/ProfileInstaller.java").read_text(encoding="utf-8")
xml_manager = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/managers/xml/XMLPrefsManager.java").read_text(encoding="utf-8")
strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

for name in ["home", "cyber", "device", "automation", "security", "launcher", "apps", "search", "favorites", "pin", "unpin", "profile", "help"]:
    check(f"dashboard -{name} handler exists", re.search(r"\b" + name + r"\s*\{", dashboard) is not None)
check("dashboard integrates with ParamCommand", "extends ParamCommand" in dashboard)
check("dashboard category command tables include all four feature phases", all(f'\"{cmd}' in dashboard for cmd in ["cyber -ip", "monitor -all", "automate -status", "security -audit"]))
check("dashboard search index spans every category", "combine(CYBER, DEVICE, AUTOMATION, SECURITY, LAUNCHER)" in dashboard)
check("in-TUI app browser uses AppsManager rather than launching system app drawer", "appBrowser((MainPack) pack)" in dashboard and "pack.appsManager.printApps(AppsManager.SHOWN_APPS)" in dashboard)
check("shortcut names are forced under dkx_ namespace", 'Pattern.compile("dkx_[a-z0-9_]{1,20}")' in dashboard)
check("shortcuts accept only allowlisted T-UI commands", "ALLOWED_COMMANDS" in dashboard and '"termux"' not in dashboard.split("ALLOWED_COMMANDS",1)[1].split("};",1)[0])
check("shortcut shell chaining and special tokens blocked", "SAFE_TOKEN" in dashboard and "Shell syntax, paths and command chaining are blocked" in dashboard)
check("unpin only removes dkx_ shortcuts", "Only dashboard shortcuts beginning with dkx_ can be removed" in dashboard)
check("favorites show names only, not alias command values", 'out.append(Tuils.NEWLINE).append("  ").append(name)' in dashboard and 'append(line.substring(separator' not in dashboard)
check("dashboard profile command calls explicit backed-up profile installer", "ProfileInstaller.applyScreenshotProfile" in dashboard)
check("dashboard help resource exists", 'name="help_dashboard"' in strings)
check("dashboard help exposes in-TUI app browser", "dashboard -apps" in strings)

check("first-run profile seed is called before XML preferences load", "ProfileInstaller.seedMissing(context, folder)" in xml_manager)
check("first-run seed uses only-missing behavior", "destination, null, true, true" in installer)
check("existing legacy T-UI config is migrated during first-run seed", "migrateRequestedHomeBehaviour(destination)" in installer)
check("migration only replaces the previous broken zero-alias Phase-5 default", '"0(8)1(0)2(0)3(0)".equals(emptyOrder.trim())' in installer)
check("migration only replaces the original hide-when-empty default", '"always".equalsIgnoreCase(hideEmpty.trim())' in installer)
check("suggestion settings are backed up before migration", "phase5-home-suggestions-original.xml" in installer and "Do not modify a user's existing settings if backup failed" in installer)
check("migration adds clear-before-command only if setting is absent", 'readXmlValue(behavior, "clear_before_command") == null' in installer)
check("home migration does not touch remote credentials or aliases", "expert.xml" not in installer.split("migrateRequestedHomeBehaviour(File destination)",1)[1].split("    }",1)[0] and "alias.txt" not in installer.split("migrateRequestedHomeBehaviour(File destination)",1)[1].split("    }",1)[0])
check("explicit profile apply excludes expert.xml", "destination, null, false, false" in installer and "backupExistingAssets(context.getAssets(), ASSET_ROOT, destination, backup)" in installer)
check("explicit profile apply backs up before copying", "phase5-profile-backup-" in installer and "copyFile(target, backup)" in installer)
check("remote ID/password keys are not written by installer", all(k not in installer for k in ['"remote_user"', '"remote_pass"', '"remote_port"']))
check("profile updates only visual expert fields", all(k in installer for k in ["custom_command", "custom_command_timeout", "custom_command_size", "custom_command_index", "show_custom_command"]))
check("weather key is stripped from APK profile template", '<weather_key value=""' in (ROOT / "app/src/main/assets/default_profile/behavior.xml").read_text(encoding="utf-8"))
check("existing weather key is preserved during explicit profile apply", 'readXmlValue(new File(destination, "behavior.xml"), "weather_key")' in installer)
check("personal alias.txt not bundled in default profile", not (ROOT / "app/src/main/assets/default_profile/alias.txt").exists())
check("profile contains user's header file", (ROOT / "app/src/main/assets/default_profile/header").is_file())
check("profile contains sysinfo script", (ROOT / "app/src/main/assets/default_profile/script/sysinfo").is_file())
check("sysinfo endpoints use HTTPS", "https://ifconfig.me" in (ROOT / "app/src/main/assets/default_profile/script/sysinfo").read_text(encoding="utf-8") and "https://www.google.com" in (ROOT / "app/src/main/assets/default_profile/script/sysinfo").read_text(encoding="utf-8"))
check("portrait orientation selected", '<orientation value="1"/>' in (ROOT / "app/src/main/assets/default_profile/behavior.xml").read_text(encoding="utf-8"))
check("black background and green terminal output profile", 'value="black"' in (ROOT / "app/src/main/assets/default_profile/theme.xml").read_text(encoding="utf-8") and 'value="#FF00FF00"' in (ROOT / "app/src/main/assets/default_profile/theme.xml").read_text(encoding="utf-8"))
check("yellow/red rounded status bar profile present", 'status_lines_bgrectcolor value="yellow,red"' in (ROOT / "app/src/main/assets/default_profile/theme.xml").read_text(encoding="utf-8"))
check("invalid RSS package-name pseudo URLs removed", not re.search(r'<rss\b[^>]*url="com\.whatsapp"', (ROOT / "app/src/main/assets/default_profile/rss.xml").read_text(encoding="utf-8"), re.I))
check("Phase 1 cyber toolbox retained", (base / "cyber.java").is_file())
check("Phase 2 device monitor retained", (base / "monitor.java").is_file())
check("Phase 3 automation retained", (base / "automate.java").is_file())
check("Phase 4 security center retained", (base / "security.java").is_file())
check("application ID unchanged", 'applicationId "com.thedarkgeekdc.tuiexpert"' in gradle)
check("current version updated for home-screen compatibility release", 'versionName "0.5.8"' in gradle and 'versionCode 17' in gradle)
check("original upstream credits remain", all(x in (ROOT / "ATTRIBUTION.md").read_text(encoding="utf-8") for x in ["Francesco Andreuzzi", "v1nc", "Yehoslav", "TheDarkGeekDC"]))
check("Phase 5 docs exist", (ROOT / "PHASE_5_CUSTOM_DASHBOARD.md").is_file())

# Home screen behaviour requested by user: in-TUI app selection and clean command output.
terminal_manager = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/managers/TerminalManager.java").read_text(encoding="utf-8")
behavior = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/managers/xml/options/Behavior.java").read_text(encoding="utf-8")
suggestions = (ROOT / "app/src/main/java/de/reckendrees/systems/tui/expert/managers/suggestions/SuggestionsManager.java").read_text(encoding="utf-8")
suggestions_xml = (ROOT / "app/src/main/assets/default_profile/suggestions.xml").read_text(encoding="utf-8")
apps_xml = (ROOT / "app/src/main/assets/default_profile/apps.xml").read_text(encoding="utf-8")
behavior_xml = (ROOT / "app/src/main/assets/default_profile/behavior.xml").read_text(encoding="utf-8")
check("clear_before_command defaults to true", "clear_before_command" in behavior and '<clear_before_command value="true"/>' in behavior_xml)
check("terminal clears old transcript before printing current command", "if (clearBeforeCommand && cmd.length() > 0)" in terminal_manager and terminal_manager.index("mTerminalView.setText(Tuils.EMPTYSTRING)", terminal_manager.index("private boolean onNewInput")) < terminal_manager.index("writeToView(input, CATEGORY_INPUT)", terminal_manager.index("private boolean onNewInput")))
check("clear-before-command preserves command history data", "cmdList.add(cmdList.size(), cmd)" in terminal_manager)
check("in-TUI app suggestions merge configured favorites and visible apps", "getSuggestedApps()" in suggestions and "pack.appsManager.shownApps()" in suggestions)
check("app suggestion chips launch directly", "canClickToLaunch ? l : null" in suggestions and "canClickToLaunch ? i : null" in suggestions)
check("classic home suggestion strip restores aliases first", '<noinput_suggestions_order value="0(0)1(15)2(3)3(5)"/>' in suggestions_xml and '<hide_suggestions_when_empty value="false"/>' in suggestions_xml)
check("remaining app suggestion slots learn most-used apps", all(f'<default_app_n{i} value="most_used"/>' in apps_xml for i in (3, 4, 5)))
check("app browser route appears in dashboard home and launcher category", "[07] IN-TUI APP BROWSER   dashboard -apps" in dashboard and '"dashboard -apps"' in dashboard)
check("release docs preserve classic interface and clean-screen behaviour", "does not open the Android system app drawer" in (ROOT / "PHASE_5_HOME_SCREEN_BEHAVIOR.md").read_text(encoding="utf-8") and "terminal transcript" in (ROOT / "PHASE_5_HOME_SCREEN_BEHAVIOR.md").read_text(encoding="utf-8"))

for label, ok in checks:
    print(("PASS" if ok else "FAIL") + "  " + label)
passed = sum(ok for _, ok in checks)
print(f"\n{passed}/{len(checks)} checks passed")
if errors:
    print("XML errors:")
    for error in errors: print("  " + error)
sys.exit(0 if passed == len(checks) else 1)
