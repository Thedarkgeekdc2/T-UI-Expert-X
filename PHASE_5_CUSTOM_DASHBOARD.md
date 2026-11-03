# T-UI Expert X — Phase 5: Custom Command Dashboard

**App:** T-UI Expert X  
**Version:** 0.5.5 (version code 14)  
**Application ID:** `com.thedarkgeekdc.tuiexpert`  
**Maintainer:** TheDarkGeekDC

## Dashboard commands

- `dashboard` — terminal-native command home screen.
- `dashboard -cyber` — Phase 1 network/security diagnostics.
- `dashboard -device` — Phase 2 device-monitor command index.
- `dashboard -automation` — Phase 3 automation command index.
- `dashboard -security` — Phase 4 security/privacy command index.
- `dashboard -launcher` — common launcher/productivity commands.
- `dashboard -search <keyword>` — search command descriptions and examples.
- `dashboard -favorites` — list only pinned shortcut names beginning with `dkx_`; command values are not printed.
- `dashboard -pin dkx_name <safe-command>` — create a shortcut alias.
- `dashboard -unpin dkx_name` — remove only a dashboard-created alias.
- `dashboard -profile` — back up and apply the screenshot-matched profile.

Pinned shortcuts are constrained to a small allowlist of built-in T-UI commands and safe options. Shell chaining, paths, shell operators, root shells, Termux commands, and arbitrary command execution are blocked by the dashboard pin operation. Existing user aliases are not rewritten.

## Screenshot-matched default profile

The APK includes `app/src/main/assets/default_profile/`, seeded automatically only when a file is missing from `/sdcard/t-ui`. It is based on the user's supplied XML files and screenshots:

- Black background and green terminal output.
- Red input/device labels.
- Yellow/red rounded status-line borders.
- Lucida Console, fullscreen, portrait orientation and bottom input.
- User's header art, note box and toolbar preferences.
- The `sysinfo` script uses HTTPS endpoints.
- Invalid UTF-16 surrogate XML references in `behavior.xml` and `notifications.xml` were converted to legal Unicode.
- Invalid duplicate RSS package-name entries were removed.

### Apply on an existing installation

After installing a Phase 5 APK, run:

```text
dashboard -profile
restart
```

The profile command creates a local dated backup under `/sdcard/t-ui/phase5-profile-backup-*` before replacing the visual profile files. It also backs up `expert.xml` locally because that file can contain remote-access credentials.

**Credential preservation:** `expert.xml` is never copied from the APK and is never replaced by the screenshot profile. Only these five visual fields are updated in-place: `custom_command`, `custom_command_timeout`, `custom_command_size`, `custom_command_index`, and `show_custom_command`. Existing remote-access ID/password/port, exit password, root setting and all other expert entries remain untouched. The existing `alias.txt` is also never overwritten, so current personal shortcuts and remote-service aliases remain in place. Existing `weather_key` is preserved when applying the profile; the key itself is not shipped in the APK assets.

First-run seeding copies only files that do not already exist. This also preserves any existing `/sdcard/t-ui/expert.xml` and `alias.txt`.

## Command shortcuts examples

```text
dashboard -pin dkx_battery monitor -battery
dashboard -pin dkx_dns cyber -dns google.com
dashboard -pin dkx_audit security -audit
dashboard -favorites
dkx_battery
dashboard -unpin dkx_battery
```

## Credits

- Francesco Andreuzzi — original T-UI Console Launcher.
- v1nc — T-UI Expert fork and earlier development.
- Yehoslav — Community Edition maintenance.
- Upstream and community contributors — prior code, fixes and documentation.
- TheDarkGeekDC — current T-UI Expert X customization and maintenance.

Original GPLv3 licensing and upstream notices are retained. See `ATTRIBUTION.md` and `LICENSE`.

## Verification status

- Static validation covers Android source XML, profile XML, dashboard parameters, profile backup rules, credentials not shipped, safe shortcut allowlist, screen profile settings and credits.
- Java syntax/type checks for the new command and profile installer are performed against lightweight API stubs where available.
- A full APK build and physical-device test are not claimed unless the Android SDK platform and legacy Gradle distribution are available and the build succeeds.
