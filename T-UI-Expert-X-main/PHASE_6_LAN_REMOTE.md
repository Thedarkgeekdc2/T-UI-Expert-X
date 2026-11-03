# Phase 6 — Local Wi-Fi Remote Console and Share

Version: 0.5.8 (versionCode 17)

## Purpose

Provide a small browser-based remote control page for T-UI Expert X devices on the same private Wi-Fi subnet. The existing T-UI launcher remains the phone's main interface; the web UI is a separate page served by the phone.

## Start and manage

- The server starts automatically when T-UI opens while connected to Wi-Fi (`remote_autostart` defaults to `true`).
- `remote -start` starts it and enables auto-start.
- `remote -stop` stops it and disables auto-start. The Stop action in the foreground notification does the same.
- `remote -status` shows whether it is active and the local URL.
- `remote -credentials` displays the username and password locally on the phone.
- `remote -autoon` / `remote -autooff` control auto-start.
- Default username: `tuiadmin`; default port: `8765`.
- Existing `expert.xml` values for `remote_user`, `remote_pass`, and `remote_port` are read as configured and not replaced. When `remote_pass` is blank, a random password is generated and stored in app-private preferences; it is not written into shared `expert.xml`.

## Browser features

- Login-protected remote T-UI terminal command submission. Keep T-UI running on the phone for best command-output capture.
- Two-pane file manager for listing, uploading, downloading, renaming, deleting, creating folders, and copying/moving files between panes.
- Individual file uploads/downloads and file-to-file transfers are limited to 50 MB. Directory listing is capped at 500 entries per pane. Copy/move currently supports files, not whole folders.
- The current release does not include the reference APK's screen mirroring, screenshot streaming, WebSocket live screen, or remote touch-control features.

## Security boundaries

- The service binds to the phone's Wi-Fi IPv4 address, not all interfaces, and only accepts clients on the same IPv4 subnet.
- Login credentials are sent in a POST body, not in a URL. The server issues random bearer tokens that expire after 12 hours and can be revoked by logout. Login attempts are rate-limited.
- POST keeps the password out of the URL and browser address history, but HTTP is not encrypted: a device able to monitor the same Wi-Fi traffic could still observe credentials or session data. Use only a trusted private Wi-Fi network. Do not configure router port forwarding or expose this port to the public internet.
- File operations are confined to `Download/TUI-Share`. If Android denies access to public Downloads, the service uses app-specific Documents storage as a fallback. It never shares the whole storage root, `expert.xml`, `alias.txt`, or the private generated-password preferences.
- Stop the service with `remote -stop` when it is not needed.

## Manual test checklist

1. Connect phone and a second device to the same Wi-Fi network.
2. Open T-UI on the phone, run `remote -status`, then `remote -credentials`.
3. Open the printed `http://PHONE_IP:PORT` URL on the second device and sign in.
4. Submit a harmless command such as `monitor -battery`; keep T-UI open and confirm the output returns.
5. Upload a small test file to `TUI-Share`, download it, rename it, then delete it.
6. Try a path using `../` and verify that the server rejects it.
7. Try connecting from a different subnet (when available) and verify that it is rejected.
8. Run `remote -stop` and confirm the page is no longer reachable.

The source was statically reviewed in this workspace. A full Android APK build and live phone/network test still need to run through the repository's GitHub Actions workflow and on-device testing.
