# T-UI Expert X — Phase 4: Security & Privacy Center

**Version:** 0.5.4 (version code 13)  
**Application ID:** `com.thedarkgeekdc.tuiexpert`  
**Maintainer:** TheDarkGeekDC  
**Upstream credits and GPLv3 notices:** retained; see `ATTRIBUTION.md` and `LICENSE`.

## New command

Run `security -help` for the in-app help.

- `security -audit` — summary of this app's requested/granted permissions, backup/debug/cleartext policy, exported component exposure and configuration risk indicators.
- `security -permissions` — lists this app's requested permissions and their current grant state. It does not request, grant or revoke permissions.
- `security -privacy` — reviews app backup, debug and cleartext flags, exported components, and the shared-storage configuration location.
- `security -config` — checks known T-UI XML files for risky settings such as remote-password fields, exit-password fields, root mode, custom commands, API-key fields, plain HTTP links, and malformed RSS URL schemes.
- `security -remote` — reviews `remote_port`, `remote_user`, and `remote_pass`/`remote_password` fields in `expert.xml`. Credential values are never displayed.
- `security -settings` — opens Android's app details page so the device owner can review permissions and special access.
- `security -report` — writes a sanitized security audit report to the app's private internal storage. No raw config files or secret values are copied.

## Security hardening in this phase

- Android app backup is disabled (`allowBackup=false`) so app-private data is not included in Android Auto Backup/device-transfer backup.
- Cleartext traffic is disallowed by app manifest policy on Android 6.0/API 23 and later. Built-in weather and connectivity-check URLs were changed to HTTPS.
- The internal music service and notification-monitor helper service are explicitly non-exported. The notification-listener service remains exported only with Android's protected `BIND_NOTIFICATION_LISTENER_SERVICE` permission. The launcher, text editor and disabled alternate-home activity keep the exports needed for their documented Android entry points.
- The broad `/storage/` `root-path` entry was removed from the FileProvider paths. The primary external-storage path used by the legacy file-open/share workflow remains; shared-file access is still granted only through explicit content URIs.
- Existing permissions required by legacy launcher features were not silently removed. The audit makes their requested/granted state visible and lets the device owner review them in Android settings.
- Security reports deliberately mask remote credentials, exit passwords and API tokens. `expert.xml` remains excluded from the allowlisted `automate -backup` archive.

## Important limitations and honest findings

- T-UI's legacy configuration directory is under shared external storage (`/sdcard/t-ui` on conventional devices). This app does not encrypt legacy XML configuration files at rest. Other apps with broad storage access may be able to read them. Avoid sharing `expert.xml`; rotate any remote password stored there.
- The source inspected for this phase does not implement a remote server/listener for the `remote_*` fields found in user-supplied configuration. `security -remote` therefore audits the XML fields only; it cannot stop a separate Termux script, service or third-party remote-access tool that may consume them.
- `security -config` is a best-effort pattern-based audit of a fixed allowlist of known T-UI configuration files. It does not prove that every arbitrary script or configuration is safe.
- `security -permissions` is informational and read-only. It does not change Android settings or revoke permissions because that would risk breaking existing T-UI functions without the device owner's informed choice.
- `usesCleartextTraffic=false` is enforced by Android 6.0/API 23 and later. Older Android releases do not enforce this manifest policy, although the built-in app network endpoints changed in this phase use HTTPS.
- An exported text-editor activity remains available for other apps to open text/file content; the notification listener is protected by Android's signature-level binding permission. These exports are intentional and appear in the component audit.
- A sanitized report is saved under the app's private `files/security-reports/` directory. It does not include passwords, tokens, raw configuration values, or configuration-file contents.

## Manual device test checklist

1. Run `security -audit` and verify the report does not display any credential values.
2. Run `security -permissions` and compare the grant state with Android Settings > Apps > T-UI Expert X > Permissions.
3. Run `security -privacy`; confirm backup is disabled and cleartext is blocked on API 23+.
4. Run `security -config`; confirm it reports presence of risky fields without printing their values.
5. Run `security -remote`; confirm it masks remote user/password/port values and warns when weak credentials are found.
6. Run `security -settings`; confirm Android app details opens.
7. Run `security -report`; confirm a sanitized report is written to private app storage.
8. Test app launching, text-file opening/sharing, notification access, weather refresh and user-supplied HTTPS links.
9. Verify `automate -backup` still creates an allowlisted archive and excludes `expert.xml` and `crash.txt`.
10. Run the full Android Gradle build and test the APK on Android 6.0/API 23 and one supported older Android version before release.

## Build status

Source-level validations and the new command's Java 8 syntax/type check against lightweight Android/app API stubs pass. A complete APK build and real-device test have not been verified in this workspace because an Android SDK platform (`android.jar`) is unavailable and the legacy Gradle distribution is not cached here.
