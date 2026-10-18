# Meshi v1.101 — Architecture & Reliability Improvements

**Date:** May 1, 2026
**Base Version:** v1.100 (build 30)

---

## Overview

This release focuses on internal architecture improvements: thread-safety, retry logic for BLE operations, input validation on mesh data, centralised configuration, and splitting oversized classes into focused components. No UI changes — all improvements are under the hood.

---

## High Priority Changes

### 1. Thread-Safety on Shared Mutable State

**Problem:** Several mutable maps (`pendingAcks`, `pendingPings`, `typingJobs`) were plain `mutableMapOf()` instances accessed from multiple coroutines without synchronisation. This created potential data races that could cause lost delivery confirmations or crashes.

**Fix:**
- `pendingAcks` and `pendingPings` in `FrameRouter` now use `java.util.concurrent.ConcurrentHashMap`
- `typingJobs` in `ChatViewModel` now uses `ConcurrentHashMap`
- `contactsAccumulator` in `FrameRouter` is now guarded by `synchronized` blocks during contact list sync

**Files changed:**
- `app/src/main/java/com/meshi/code/repository/FrameRouter.kt` (new)
- `app/src/main/java/com/meshi/code/viewmodel/ChatViewModel.kt`

---

### 2. Retry Logic with Exponential Backoff

**Problem:** Failed BLE sends surfaced an error immediately with no retry. On a mesh network where transient failures are common (radio busy, node processing), a single failure doesn't mean the operation can't succeed.

**Fix:** New `withBleRetry()` utility that:
- Attempts the operation once
- On failure, retries up to 3 times with exponential backoff (500ms → 1s → 2s)
- Returns `true` on first success, `false` only after all attempts fail

Applied to all send operations in `MeshRepository`:
- Direct messages
- Channel messages
- Game protocol messages
- Ping sends
- Login requests
- Channel create/update/delete

**Files added:**
- `app/src/main/java/com/meshi/code/ble/BleRetry.kt`

**Files changed:**
- `app/src/main/java/com/meshi/code/repository/MeshRepository.kt`

---

### 3. Input Validation on Received Mesh Data

**Problem:** Data arriving from the mesh network is untrusted. Contact names, message text, GPS coordinates, and channel info were passed directly to the UI and database without validation. Malformed data could cause UI rendering issues or database corruption.

**Fix:** New `InputValidator` object that sanitises all incoming data at the protocol boundary:

| Validator | What it does |
|-----------|-------------|
| `sanitizeName()` | Strips control characters, trims whitespace, enforces 32-char max |
| `sanitizeChannelName()` | Same rules for channel names |
| `sanitizeMessageText()` | Strips control characters (preserves newlines), trims |
| `isValidPubKeyPrefix()` | Checks length ≥ 6 bytes and not all zeros |
| `isValidGps()` | Validates lat ∈ [-90, 90] and lon ∈ [-180, 180] |
| `isValidChannelIdx()` | Validates index ∈ [0, 7] |

All validation is applied in `FrameRouter` before data reaches StateFlows or the Room database.

**Files added:**
- `app/src/main/java/com/meshi/code/protocol/InputValidator.kt`

**Files changed:**
- `app/src/main/java/com/meshi/code/repository/FrameRouter.kt` (new, uses validators throughout)

---

### 4. Centralised Configuration Constants

**Problem:** Magic numbers were scattered across the codebase — timeouts (6000ms, 3000ms, 2000ms), limits (160 chars, 50 adverts, 100 SNR samples), thresholds (20% battery), and intervals (30s SOS, 5min battery poll). Changing any value required hunting through multiple files.

**Fix:** New `MeshiConfig` object with all constants in one place:

```kotlin
object MeshiConfig {
    // BLE / Protocol
    const val HANDSHAKE_DEVICE_QUERY_TIMEOUT_MS = 6_000L
    const val HANDSHAKE_APP_START_TIMEOUT_MS = 6_000L
    const val HANDSHAKE_CLOCK_SYNC_TIMEOUT_MS = 3_000L
    const val SEND_ACK_TIMEOUT_MS = 2_000L
    const val BLE_WRITE_SPACING_MS = 150L
    const val BLE_MTU_SIZE = 185

    // Retry
    const val SEND_MAX_RETRIES = 3
    const val SEND_RETRY_BASE_DELAY_MS = 500L

    // Messages
    const val MESSAGE_MAX_LENGTH = 160
    const val ADVERTS_MAX_SIZE = 50
    const val ADVERTS_TTL_MS = 30 * 60 * 1000L

    // Typing
    const val TYPING_INDICATOR_TTL_MS = 5_000L
    const val TYPING_SEND_DEBOUNCE_MS = 3_000L

    // SOS / Battery / DB limits ...
}
```

**Files added:**
- `app/src/main/java/com/meshi/code/MeshiConfig.kt`

**Files changed:**
- `app/src/main/java/com/meshi/code/ble/BleManager.kt` — uses `MeshiConfig` for handshake timeouts and MTU
- `app/src/main/java/com/meshi/code/repository/MeshRepository.kt` — uses `MeshiConfig` for send timeouts and delays
- `app/src/main/java/com/meshi/code/viewmodel/ChatViewModel.kt` — uses `MeshiConfig` for typing debounce
- `app/src/main/java/com/meshi/code/service/MeshiForegroundService.kt` — uses `MeshiConfig` for battery poll interval
- `app/src/main/java/com/meshi/code/repository/FrameRouter.kt` — uses `MeshiConfig` for advert TTL and limits

---

## Medium Priority Changes

### 5. ChatViewModel Split — Game Invite & SOS Extraction

**Problem:** `ChatViewModel` was ~400 lines handling messaging, game invites for 4 games, SOS beacon management, typing indicators, reactions, search, and more. Too many responsibilities in one class.

**Fix:** Extracted two focused managers:

**`GameInviteManager`** — Handles all incoming game invites:
- Listens to `MeshRepository.gameMessages`
- Routes `[RV]INVITE`, `[CH]INVITE`, `[TTT]INVITE`, `[BSH]INVITE` to per-game StateFlows
- Provides `clearPending*Invite()` methods
- `ChatViewModel` delegates all invite state and clearing to this manager

**`SosManager`** — Handles the SOS beacon:
- Manages the repeating flood advert loop
- Persists active state to `AppPreferences`
- Resumes on app restart if SOS was active
- Respects stealth mode (skips adverts when stealth is on)
- `ChatViewModel` delegates `isSosActive` and `toggleSos()` to this manager

**Files added:**
- `app/src/main/java/com/meshi/code/viewmodel/GameInviteManager.kt`
- `app/src/main/java/com/meshi/code/viewmodel/SosManager.kt`

**Files changed:**
- `app/src/main/java/com/meshi/code/viewmodel/ChatViewModel.kt` — delegates to both managers, removes ~80 lines of inline logic

---

### 6. MeshRepository Split — Frame Router Extraction

**Problem:** `MeshRepository` was ~500 lines handling both frame dispatch (the large `when(frame)` block) and send/command operations. The frame handling logic was interleaved with state management.

**Fix:** Extracted `FrameRouter` class that owns:
- The entire `handleFrame(frame: NodeFrame)` dispatch
- Direct message routing (game protocol, reactions, typing, regular messages)
- Channel message routing
- Advert processing with GPS updates
- Contact list accumulation during sync
- Delivery confirmation (ack) tracking with thread-safe maps
- All `SharedFlow` emissions for game messages, typing events, reactions, pings, and login results

`MeshRepository` now:
- Creates and owns the `FrameRouter` instance
- Focuses on send operations, scan control, and channel/contact management
- Exposes `FrameRouter`'s SharedFlows as pass-through properties

**Files added:**
- `app/src/main/java/com/meshi/code/repository/FrameRouter.kt`

**Files changed:**
- `app/src/main/java/com/meshi/code/repository/MeshRepository.kt` — reduced by ~150 lines

---

### 7. Advert TTL (Time-to-Live)

**Problem:** The advert list kept the last 50 entries but never expired old ones. Adverts from nodes that went offline hours ago remained visible in the UI.

**Fix:**
- Added `timestampMs` field to `AdvertEvent` (defaults to `System.currentTimeMillis()`)
- `FrameRouter.addAdvert()` now filters out adverts older than 30 minutes (`MeshiConfig.ADVERTS_TTL_MS`) before adding new ones
- Combined with the existing max-size cap of 50

---

## File Inventory

### New Files (6)

| File | Purpose |
|------|---------|
| `app/.../MeshiConfig.kt` | Centralised constants |
| `app/.../ble/BleRetry.kt` | Exponential backoff retry utility |
| `app/.../protocol/InputValidator.kt` | Mesh data sanitisation and validation |
| `app/.../repository/FrameRouter.kt` | Frame dispatch logic extracted from MeshRepository |
| `app/.../viewmodel/GameInviteManager.kt` | Game invite handling extracted from ChatViewModel |
| `app/.../viewmodel/SosManager.kt` | SOS beacon management extracted from ChatViewModel |

### Modified Files (5)

| File | Changes |
|------|---------|
| `app/.../ble/BleManager.kt` | Uses `MeshiConfig` for timeouts and MTU |
| `app/.../repository/MeshRepository.kt` | Delegates to `FrameRouter`, uses `withBleRetry()`, uses `MeshiConfig` |
| `app/.../viewmodel/ChatViewModel.kt` | Delegates to `GameInviteManager` and `SosManager`, uses `ConcurrentHashMap`, uses `MeshiConfig` |
| `app/.../service/MeshiForegroundService.kt` | Uses `MeshiConfig` for battery poll interval |
| `app/.../repository/AdvertEvent` (in MeshRepository.kt) | Added `timestampMs` field |

---

## Build

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Build status: **SUCCESSFUL** — all warnings are pre-existing (deprecated Material icons, unused parameters in protocol parsers).
