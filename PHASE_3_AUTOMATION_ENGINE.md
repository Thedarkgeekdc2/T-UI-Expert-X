# T-UI Expert X — Phase 3: Smart Automation Engine

**App version:** 0.5.3 (versionCode 12)  
**Package:** `com.thedarkgeekdc.tuiexpert`  
**Maintainer:** TheDarkGeekDC

## Commands

| Command | Purpose |
|---|---|
| `automate -status` | Show enabled automations and reminders |
| `automate -battery 20` | Notify when charge reaches 20% or lower (5–50% configurable) |
| `automate -batteryoff` | Disable low-battery checks |
| `automate -chargeon` / `-chargeoff` | Notify on plug/unplug events |
| `automate -wifion` / `-wifioff` | Detect connection type/state changes |
| `automate -timer 15 Take a break` | One-time reminder after N minutes (maximum seven days) |
| `automate -daily 08:30 Morning routine` | Daily local-time reminder |
| `automate -cancel` | Cancel one-time and daily reminders |
| `automate -alloff` | Disable battery, charging and network alerts plus all reminders |
| `automate -backup` | Create an allowlisted configuration backup |
| `automate -help` | Show help |

## Behaviour and limits

- Battery and network polling use inexact `AlarmManager.setInexactRepeating` every 15 minutes and are active only while the corresponding feature is enabled.
- Battery alert threshold accepts 5–50%. A hysteresis of four percentage points prevents repeated alerts until the battery recharges above the threshold.
- Charging alerts use Android power-connected/disconnected system broadcasts.
- Network monitoring records only coarse connection state/type; it does not collect SSIDs, MAC addresses, traffic contents or location. Changes are detected at the next 15-minute poll, not instantly.
- Daily reminders use inexact repeating alarms and are restored after `BOOT_COMPLETED`. Android may defer delivery under Doze/battery restrictions.
- Notifications require device notifications to be enabled for T-UI Expert X. Android 8.0+ uses the `Automation alerts` notification channel.
- Configuration backup is written to the app-specific external Documents directory (or internal app documents fallback). Files are allowlisted. `expert.xml` is deliberately excluded because it may store remote-access credentials; crash reports and unknown files are excluded.
- Automations only notify. They do not run arbitrary shell commands, change Wi-Fi/mobile settings, collect private user content, or silently execute user routines.
- A reboot receiver is registered with `RECEIVE_BOOT_COMPLETED`; this is a normal permission, not a dangerous runtime permission.

## Suggested manual tests

1. Enable `automate -chargeon`, unplug and reconnect power; confirm each notification.
2. Set `automate -battery 50` temporarily while the battery is below 50% and wait for the next poll (up to 15 minutes); confirm one alert. Disable with `automate -batteryoff`.
3. Enable `automate -wifion`, change network state, and confirm a notification at the next poll (up to 15 minutes). Disable with `automate -wifioff`.
4. Run `automate -timer 1 Test reminder`; confirm notification.
5. Run a daily reminder for a few minutes ahead; confirm it fires and remains scheduled for the next day.
6. Run `automate -backup`; confirm ZIP exists in app-specific Documents. Verify it does not contain `expert.xml` or `crash.txt`.
7. Reboot and confirm the daily reminder and enabled polling are restored.

## Build verification

All Phase 3 Android XML files were parsed successfully. The two new Java files were syntax/type checked using isolated lightweight Android and application API stubs; this is not a substitute for a real Android SDK build. The full Gradle APK build and physical-device behaviour have not been verified because this workspace does not contain Android SDK platform files (`android.jar`) or a cached Gradle 6.1.1 distribution.
