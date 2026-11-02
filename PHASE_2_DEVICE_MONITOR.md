# T-UI Expert X — Phase 2: Live Android Device Monitor

**Version:** 0.5.2 (versionCode 11)  
**Package:** `com.thedarkgeekdc.tuiexpert`  
**Maintainer:** TheDarkGeekDC

## Commands

| Command | Description |
|---|---|
| `monitor -all` | Collect a complete snapshot: battery, CPU, RAM, storage, network, uptime and device |
| `monitor -battery` | Battery charge, status, health, power source, temperature, voltage and technology |
| `monitor -cpu` | Whole-device CPU usage sampled from `/proc/stat` |
| `monitor -memory` | Total, used and available RAM plus Android low-memory state |
| `monitor -storage` | Internal data partition total, used and available storage |
| `monitor -network` | Active connection state, type and subtype |
| `monitor -uptime` | Time elapsed since boot |
| `monitor -device` | Manufacturer, model, Android release/API, security patch, CPU ABI |
| `monitor -watch` | Send an updated combined report every five seconds |
| `monitor -stop` | Stop the live report loop |
| `monitor -help` | Show command help |

## Design and privacy

- Uses Android framework status APIs (`BatteryManager` sticky intent, `ActivityManager.MemoryInfo`, `StatFs`, `SystemClock`, and `ConnectivityManager`).
- CPU utilisation is estimated from the delta between aggregate `/proc/stat` counters over a short sample interval. It is not a per-process profiler.
- No new Android permission was added. No root is required.
- Does not inspect messages, contacts, call logs, location, user documents, or installed-app lists.
- `-watch` uses a daemon scheduled executor with a five-second fixed delay. It uses the application context, can be stopped with `monitor -stop`, and ends with the app process. It is not a boot-persistent Android service.
- Some firmware may withhold individual metrics; unavailable values are reported rather than fabricated. Battery health/temperature may be omitted by some vendors.
- Storage refers to the app-visible internal data partition, not the total physical flash including inaccessible partitions.

## Build and test status

- Resource XML parsing: validated.
- New Java command class: compiled with lightweight Android API stubs for Java syntax/type sanity.
- Package name and version: validated.
- Permission delta: no new manifest permission.
- Full APK build: not completed in this workspace because Android SDK/platform tools are not installed.
- On-device behaviour: not yet verified on a physical Android device.
