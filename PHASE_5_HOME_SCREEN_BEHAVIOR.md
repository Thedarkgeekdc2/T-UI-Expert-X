# T-UI Expert X — Home Screen Behaviour Update

Version: 0.5.7 (versionCode 16)

## Apps stay inside T-UI
- `dashboard -apps` prints installed visible apps inside the T-UI terminal.
- The home suggestion strip is an in-TUI app browser: it lists configured app favourites first, then fills remaining slots with other visible installed apps.
- Suggestion chips remain clickable through the existing T-UI AppsManager. Selecting a chip launches that app directly; it does not open the Android/system app drawer.
- Typing part of an installed app name filters the native T-UI suggestions.
- Empty-input suggestions are enabled, with up to eight visible app suggestions. The app strip is horizontally scrollable.
- On startup, a one-time compatibility migration changes only the previous stock defaults (`0(0)1(15)2(3)3(5)` and `always`). Any custom suggestion settings are preserved. Before changing legacy defaults, the original `suggestions.xml` is backed up as `phase5-home-suggestions-original.xml`.

## Clear before every command
- New preference: `behavior.xml` → `<clear_before_command value="true"/>`.
- Before each non-empty command, only the terminal transcript is cleared. The current command is then printed, followed by its output.
- The launcher status/header, prompt/input field, toolbar and app suggestion strip are not removed. Command-history data remains stored.
- A missing setting is automatically added as `true`; set it to `false` to restore scrolling command history.

## Existing user data
- `expert.xml` and `alias.txt` are not bundled or overwritten by this update.
- Remote-access ID/password, port and personal aliases remain unchanged.
