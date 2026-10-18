# Meshi v1.101

Off-grid Android chat and gaming for the MeshCore LoRa mesh network. Connects to a MeshCore node over Bluetooth LE — no internet, no cellular, no infrastructure.

## Downloads

| APK | Signing | Notes |
|-----|---------|-------|
| **app-release.apk** | Release key, R8-minified (~2.5 MB) | Recommended for general use |
| **app-debug.apk** | Debug key (~17 MB) | For troubleshooting; includes debug symbols |

> The two APKs are signed with different keys and share the same application ID. To switch between them on one device, uninstall the existing install first.

Install: enable "install from unknown sources" on your device, then open the APK.

## What's new in 1.101

This release focuses on reliability and release hardening.

### Reliability
- **Automatic send retry** with exponential backoff for transient BLE failures
- **Thread-safe** delivery-confirmation and typing-indicator tracking
- **Input validation** on all data received from the mesh (names, messages, GPS, channels) to guard against malformed packets
- **Advert expiry** — stale node advertisements time out after 30 minutes

### Architecture
- Frame-routing logic extracted into a dedicated `FrameRouter`
- Game-invite and SOS-beacon handling extracted into focused managers
- Centralised tunable constants in `MeshiConfig`

### Release hardening
- R8 code + resource shrinking for release builds (~85% smaller APK)
- On-device-only storage: cloud backup and device-to-device transfer disabled for messages, contacts, and location
- Unit test suite for game logic, input validation, and retry behaviour

## Requirements
- Android 5.0 (API 21) or newer
- A MeshCore-compatible LoRa node reachable over Bluetooth LE

## License
GNU General Public License v3.0 — see [LICENSE](LICENSE).
