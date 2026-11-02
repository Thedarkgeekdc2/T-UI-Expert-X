# T-UI Expert X — Cyber Command Center

**Android terminal launcher • Cyber tools • Device controls • Automation**

T-UI Expert X is an independent community customization of T-UI Expert. It keeps the original terminal-launcher foundation and adds a new maintainer identity for the next development phase.

- **App name:** T-UI Expert X
- **Maintainer / customization:** TheDarkGeekDC
- **Android application ID:** `com.thedarkgeekdc.tuiexpert`
- **Upstream source:** https://github.com/Yehoslav/T-UI-Expert
- **License:** GNU GPL v3 (see `LICENSE`)

> This project is an independent community fork. It is not affiliated with or endorsed by the original authors. Upstream code, notices, credits, and license are retained.

## Upstream credits

- **Francesco Andreuzzi** — original T-UI Console Launcher creator.
- **v1nc** — T-UI Expert fork and earlier development/maintenance.
- **Yehoslav** — T-UI Expert Community Edition maintainer.
- **All upstream and community contributors** — for the fixes, features, documentation, testing, and other work incorporated into the project.
- **TheDarkGeekDC** — current maintainer and customization work for T-UI Expert X.

See [`ATTRIBUTION.md`](ATTRIBUTION.md) for the attribution chain and [`LICENSE`](LICENSE) for license terms. The in-app `tui -about` command also displays the credits.

## Existing upstream features

- Terminal-style Android launcher and command interface.
- App, contact, alias, and command management.
- Termux integration.
- RSS feeds and custom command output.
- Notification integration.
- Root commands where the device and permissions support them, including airplane mode, battery saver, cellular, NFC, rotation, reboot, recovery, shutdown, and sleep timer.
- Theme and interface customization.

## Existing upstream fixes/features retained

- Hotfix for duplicate notifications.
- Prevention of a battery receiver crash.
- Transparent cursor color support.
- More reliable Wi-Fi command.
- Airplane mode command fix.
- Tap-to-lock behavior preserving fingerprint unlock.

## Build notes

The project is an older Android Gradle project. The wrapper properties file was absent from the supplied source ZIP, so a Gradle 6.1.1 wrapper distribution configuration has been restored. This is a compatibility starting point, not a claim that a release APK has already been built or tested. A suitable Java/Android SDK toolchain is required.

The installed Android application ID is changed to `com.thedarkgeekdc.tuiexpert`. The original Java package/namespace (`de.reckendrees.systems.tui.expert`) is intentionally retained so existing Java and resource references continue to resolve.

## Next development direction

The planned T-UI Expert X roadmap is to add a combined Cyber Command Center and Ultimate Android experience: safe network diagnostics, device monitoring, security/privacy checks, local automation, and quick command workflows. Features will be added incrementally and tested against the existing launcher behavior.

## Phase 1 branding changes

- Branded the launcher as **T-UI Expert X**.
- Changed the Android application ID to `com.thedarkgeekdc.tuiexpert`.
- Updated the app version to `0.5.1` (version code `10`) in Phase 1.
- Added the maintainer and upstream credit chain to the in-app `tui -about` output.
- Added `ATTRIBUTION.md` and retained the upstream GPLv3 `LICENSE` file.
- Restored the missing Gradle wrapper properties file using Gradle 6.1.1, matching the legacy Android Gradle Plugin generation.
- Disabled the predecessor project's hard-coded APK updater until an official T-UI Expert X release endpoint is configured.

### Build environment note

A release APK was **not built in this environment**: an Android SDK and Gradle installation are not available here, and the legacy Android Gradle Plugin must be built with a compatible JDK/toolchain. The source changes and XML resources were statically validated. Before producing an installable APK, run the project build on a machine with the required Android SDK (platform 27/build-tools 28.0.3) and a JDK compatible with Gradle 6.1.1, then test the APK on-device.


# T-UI Expert X — TheDarkGeekDC

This customized community build retains the upstream project and license notices. Original T-UI and T-UI Expert authors/maintainers remain credited in the app's `tui -about` output. Current maintainer: **TheDarkGeekDC**.

## Phase 1 — Advanced Cyber Security Toolbox

New commands: `cyber -ip`, `cyber -net`, `cyber -dns [hostname]`, `cyber -ping [hostname]`, and `cyber -trace [hostname]`. See [PHASE_1_CYBER_TOOLBOX.md](PHASE_1_CYBER_TOOLBOX.md) for scope, safety limits, platform notes and validation status.


## Phase 2 — Live Android Device Monitor

Version: `0.5.2` (version code `11`)

The new `monitor` command provides on-demand device diagnostics and a stoppable live view:

- `monitor -all` — battery, CPU, RAM, internal storage, network, uptime and device summary.
- `monitor -battery` — charge level/status, power source, health, temperature, voltage and chemistry (when reported by Android).
- `monitor -cpu` — whole-device CPU utilisation sampled from aggregate `/proc/stat` counters.
- `monitor -memory` — total, used and available RAM plus low-memory state.
- `monitor -storage` — internal data partition usage.
- `monitor -network` — active connection type and state.
- `monitor -uptime` — time since boot.
- `monitor -device` — manufacturer/model, Android version/API, security patch and CPU ABI.
- `monitor -watch` / `monitor -stop` — refresh a combined status report every five seconds, or stop it.

The monitor reads system status through Android framework APIs and `/proc/stat`. It adds no new Android permissions and does not inspect user files, messages, contacts, location, or installed applications. The watch loop runs in a daemon thread within the launcher process; it stops when explicitly requested or when the app process exits.

Validation for this phase includes XML parsing, command-class compilation against Android API stubs, source/manifest permission checks, and package/version checks. A full APK build and live-device test still require an Android SDK and a compatible Gradle/JDK toolchain.


## T-UI Expert X custom development

This customized build keeps upstream project credits and adds the following maintainer-developed modules:

- **Phase 1 — Cyber Toolbox:** local IP/network information, DNS lookup, bounded ping and traceroute diagnostics.
- **Phase 2 — Device Monitor:** battery, CPU, RAM, internal storage, network state, uptime and device details.
- **Phase 3 — Smart Automation:** opt-in low-battery alerts, charging plug/unplug alerts, periodic coarse network-change alerts, one-time and daily reminders, and allowlisted configuration backups.

Current custom application ID: `com.thedarkgeekdc.tuiexpert`. Current version: `0.5.4` (version code `13`). See `ATTRIBUTION.md` and phase documentation for credits, behaviour and validation limitations.


## Phase 4 — Security & Privacy Center

Version: `0.5.4` (version code `13`)

The new `security` command is a read-only security/privacy audit for this app and its known T-UI config files:

- `security -audit` — overall permission, privacy and component summary.
- `security -permissions` — requested permissions and their grant state.
- `security -privacy` — backup/debug/cleartext policy and exported component summary.
- `security -config` — masked scan for remote credentials, exit passwords, API-key fields, root mode, custom commands, plain HTTP links and malformed RSS URL schemes.
- `security -remote` — review remote-related `expert.xml` fields without printing their values.
- `security -settings` — open Android app settings.
- `security -report` — write a sanitized report in private app storage.

Hardening: Android Auto Backup disabled, cleartext traffic blocked on API 23+, built-in network checks use HTTPS, internal helper services are non-exported, and the broad FileProvider `/storage/` root path is removed. Existing legacy permissions are audited rather than silently revoked.

Important: legacy T-UI XML config remains in shared external storage and is not encrypted at rest. `expert.xml` can contain remote credentials; do not share it. The source has no remote server/listener implementation for the legacy `remote_*` fields, so the audit cannot disable a separate Termux or third-party service. See [PHASE_4_SECURITY_PRIVACY.md](PHASE_4_SECURITY_PRIVACY.md).


## T-UI Expert X Phase 5

TheDarkGeekDC customization adds a terminal-native Custom Command Dashboard and a screenshot-matched first-run profile. See `PHASE_5_CUSTOM_DASHBOARD.md`. Run `dashboard` for command categories, `dashboard -search <keyword>` to find tools, and `dashboard -profile` to apply the screenshot layout after making a local backup. Existing `expert.xml` remote-access values and `alias.txt` are preserved.


## T-UI Expert X home-screen behaviour (v0.5.7)

- The app suggestion strip remains inside T-UI and shows configured favourites followed by frequently used visible apps. Tap a suggestion to launch that app from T-UI; the Android system app drawer is not opened.
- `dashboard -apps` prints the installed visible-app list in the T-UI terminal. Typing part of an app name filters the native T-UI app suggestions.
- `behavior.xml` includes `clear_before_command=true` by default. Each non-empty command clears only the previous terminal transcript, then displays the current command and its result. The top status/header, prompt, input, toolbar, app suggestions and command-history data remain available. Set it to `false` to retain scrolling output.
- Remote credentials in `expert.xml` and personal shortcuts in `alias.txt` remain user-owned local configuration and are not packaged or overwritten by this behaviour update.
