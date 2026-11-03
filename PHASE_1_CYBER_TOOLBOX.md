# T-UI Expert X — Phase 1: Advanced Cyber Security Toolbox

Maintainer: TheDarkGeekDC  
Application ID: `com.thedarkgeekdc.tuiexpert`  
Version: `0.5.1` (versionCode `10`)

## New terminal command

The toolbox is available from the T-UI command prompt:

| Command | Purpose |
|---|---|
| `cyber -help` | Show the toolbox help |
| `cyber -ip` | List the device's active non-loopback IPv4/IPv6 addresses |
| `cyber -net` | Show active network type/state, DNS servers (Android 6.0+), and route count |
| `cyber -dns example.com` | Resolve a hostname to IP addresses |
| `cyber -ping example.com` | Send up to four reachability probes |
| `cyber -trace example.com` | Attempt a traceroute capped at eight hops |

Network lookups, ping and traceroute are run in background tasks so they do not block the launcher command receiver/UI thread.

## Safety and platform notes

- Use diagnostics only for your own device and networks/hosts you are authorized to test.
- Host input is restricted to a single hostname or IP literal. Shell metacharacters, URLs, whitespace and command fragments are rejected.
- Ping resolves the host first and executes a fixed argument vector; it does not interpolate user input into a shell command.
- Ping is limited to four packets. Traceroute is limited to eight hops and one query per hop when a compatible Android traceroute utility is present.
- Some Android builds omit `ping` or `traceroute`. In that case the command reports that the platform utility is unavailable; it does not silently download or install tools.
- `cyber -net` reads the active connection and DNS details. DNS server details are displayed on Android 6.0+; older versions show a compatibility note.
- The original app already declares `INTERNET` and `ACCESS_NETWORK_STATE`; Phase 1 adds no new dangerous permissions.

## Implementation

- Added `app/src/main/java/de/reckendrees/systems/tui/expert/commands/main/raw/cyber.java`.
- Added command help text in `app/src/main/res/values/strings.xml`.
- The command is discovered through the existing package-reflection command loader and retained by the existing ProGuard keep rule for `commands.main.raw.*`.
- Updated app version to `0.5.1` / versionCode `10`.
- Existing package ID, upstream license notices and attribution remain intact.

## Validation

XML parsing and source-level structural checks are included in the delivery report. A real Android APK build/device runtime test has not been performed in this environment because no Android SDK is installed. Run the project Gradle build with a compatible Android SDK/toolchain before distributing an APK.
