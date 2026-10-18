# Meshi

**An off-grid Android chat and gaming app for the [MeshCore](https://meshcore.co.uk/) LoRa mesh network.**

Meshi connects to a MeshCore node over Bluetooth Low Energy (BLE) and lets you send messages, share your location, and play peer-to-peer games across a LoRa mesh — no internet, no cellular, no infrastructure required. Everything travels wirelessly through the mesh.

> ⚠️ Meshi is an independent, community-built client. It is not affiliated with or endorsed by the MeshCore project.

---

## Features

- **Mesh messaging** — public channel chat, private AES-encrypted channels (slots 1–7), and end-to-end encrypted direct messages
- **Message extras** — emoji reactions, swipe-to-quote replies, delivery receipts with round-trip time, typing indicators, full-text search, and plain-text export
- **Contacts & discovery** — save contacts, set nicknames and tags, mark contacts verified, and back up/restore your contact list as JSON
- **Map** — plot GPS-tagged nodes and advertisements on an interactive OpenStreetMap view
- **Location & presence** — one-shot advertise, persistent location sharing, a repeating **SOS beacon**, and a **stealth mode** that silences outgoing adverts
- **Games over the mesh** — Reversi, Chess, Tic-Tac-Toe, and Battleship, all played peer-to-peer with win/loss history
- **Background service** — persistent BLE link, incoming-message notifications with inline quick-reply, and a configurable low-battery alert for your node
- **Diagnostics** — node info, radio stats, SNR trend graphs, channel-utilisation histograms, ping/latency tests, and a crash-log viewer
- **Privacy** — block list, panic wipe, and on-device-only storage (cloud/device backup disabled)

A full feature walkthrough is in [FEATURES.md](FEATURES.md), and a usage guide is in [HOW_TO.md](HOW_TO.md).

---

## Requirements

- **Android 5.0 (API 21) or newer** — `minSdk 21`, `targetSdk 34`
- A **MeshCore-compatible LoRa node** reachable over Bluetooth LE
- Bluetooth and (for GPS-tagged adverts) location permissions

---

## Installation

### From a release APK

Download the latest `app-debug.apk` or signed release APK from the [Releases](../../releases) page, transfer it to your device, and install it (you may need to allow installation from unknown sources).

### Via Obtainium / IzzyOnDroid

Point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository's releases to receive automatic update notifications.

---

## Building from source

### Prerequisites

- JDK 17
- Android SDK with API 34
- (Signing a release only) a Java keystore — see below

### Debug build

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Release build

Release builds are minified with R8 and must be signed. Create a keystore and a `keystore.properties` file in the project root:

```bash
keytool -genkey -v -keystore meshi-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias meshi
```

Copy [`keystore.properties.template`](keystore.properties.template) to `keystore.properties` and fill in your values:

```properties
# storeFile is resolved relative to the project root
storeFile=meshi-release.jks
storePassword=your_store_password
keyAlias=meshi
keyPassword=your_key_password
```

Then build:

```bash
./gradlew assembleRelease
```

> `keystore.properties` and `*.jks` are gitignored and must never be committed. Keep your keystore backed up — **every future update must be signed with the same key**, or users cannot install the update over an existing install.

If `keystore.properties` is absent, the release build still compiles but is left **unsigned** (you would need to sign it manually with `apksigner`).

---

## Architecture

Meshi is a single-module Android app written in Kotlin with Jetpack Compose.

```
BLE layer  →  Protocol layer  →  Repository  →  ViewModels  →  Compose UI
(BleManager)  (PacketFramer,     (MeshRepository,  (ChatViewModel,
               InputValidator,    FrameRouter)      ScanViewModel,
               MeshCoreProtocol)                    game VMs)
```

- **BLE layer** (`ble/`) — scanning, connection, GATT handshake, and raw frame I/O over the Nordic UART service
- **Protocol layer** (`protocol/`) — frame encode/decode (`PacketFramer`), untrusted-input validation (`InputValidator`)
- **Repository** (`repository/`) — single source of truth; `FrameRouter` dispatches incoming frames, `MeshRepository` handles sends with retry/backoff
- **Persistence** (`db/`) — Room database for messages, contacts, game records, and SNR samples
- **UI** (`ui/`) — Compose screens and navigation; **ViewModels** (`viewmodel/`, `game/`) expose state via Kotlin `StateFlow`
- **Service** (`service/`) — foreground service for the persistent connection and notifications

Tunable constants (timeouts, limits, thresholds) live in `MeshiConfig`.

---

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Kotlin Coroutines & Flow
- Room (persistence)
- osmdroid (offline-capable maps)
- ZXing (QR encode/decode for channel sharing)

---

## Contributing

Issues and pull requests are welcome. Please keep changes focused and match the existing architecture and code style. By contributing you agree your contributions are licensed under GPLv3.

---

## License

Meshi is free software licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

```
Copyright (C) 2026 Meshi contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
