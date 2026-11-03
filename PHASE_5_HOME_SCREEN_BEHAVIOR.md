# T-UI Expert X — Classic Home Screen Compatibility

Version: 0.5.8 (versionCode 17)

## Preserve the original T-UI interface

The classic T-UI layout remains the app's home screen. The status/header bars, wallpaper/background, terminal prompt, input area, suggestion strip and toolbar are not replaced by the custom command dashboard. The dashboard is only an optional command/help entry point.

## Restore the personal alias strip

- The no-input suggestion order is restored to the classic alias-first profile: `0(0)1(15)2(3)3(5)`.
- The earlier Phase-5 default `0(8)1(0)2(0)3(0)` hid the alias category. A one-time migration recognizes only that exact bundled value and restores the alias-first order.
- Before migration, the existing `suggestions.xml` is backed up as `phase5-home-suggestions-original.xml`. If a backup cannot be made, the existing file is not modified.
- Any other custom suggestion order is preserved.
- Non-parameterized aliases in the empty-input home strip execute on tap. The configured `click_to_launch` preference continues to control ordinary typed suggestions.
- Existing `alias.txt` is user-owned. It is never packaged, reset, replaced or rewritten by the migration.
- The reference APK's Terminess Nerd Font is installed only when there is no custom font in the user's T-UI folder. This allows configured Nerd Font glyph icons to render while preserving any custom font.
- Where an alias directly resolves to a visible installed app, the suggestion may also display that app's launcher icon.

## App launch behaviour

- Tapping an installed-app suggestion launches that app directly through the existing `AppsManager` launch path, regardless of `click_to_launch` typed-input preference.
- Typing an app name still filters native T-UI app suggestions.
- `dashboard -apps` remains a help/listing command in the terminal; it does not open the Android system app drawer.

## Command transcript behaviour

`behavior.xml` keeps `clear_before_command=true` by default. It clears only the prior terminal transcript before printing each new command and result. The status/header, wallpaper/background, prompt/input, toolbar and suggestion strip remain on screen. Set this value to `false` to retain a scrolling transcript.

## Existing user data

- `expert.xml` and `alias.txt` are not bundled or overwritten.
- Existing remote username/password/port values remain untouched. The LAN server uses the configured values and stores a generated fallback password only in app-private preferences when the configured password is blank.
