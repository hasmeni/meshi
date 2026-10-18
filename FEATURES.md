# Meshi — Features Overview

Meshi is an Android chat application that communicates over a LoRa mesh radio network via a Bluetooth Low Energy (BLE) connection to a MeshCore node. All messages travel wirelessly through the mesh — no internet, no cellular, no infrastructure required.

---

## Table of Contents

1. [Connection & Device Management](#1-connection--device-management)
2. [Public Channel Chat](#2-public-channel-chat)
3. [Private Channels](#3-private-channels)
4. [Direct Messaging](#4-direct-messaging)
5. [Message Features](#5-message-features)
6. [Contacts & Node Discovery](#6-contacts--node-discovery)
7. [Map](#7-map)
8. [Advertising & Location](#8-advertising--location)
9. [Privacy & Security](#9-privacy--security)
10. [Notifications & Background Service](#10-notifications--background-service)
11. [Games](#11-games)
12. [Diagnostics & Stats](#12-diagnostics--stats)
13. [Settings](#13-settings)

---

## 1. Connection & Device Management

### BLE Scanning
The Scan screen lists nearby Bluetooth Low Energy devices. Tap a device to connect. Meshi remembers the last connected device and reconnects automatically on launch.

### Auto-Reconnect
The last successfully connected BLE address is saved. When you open the app, it attempts to reconnect without any manual input.

### Foreground Service
Once connected, a persistent foreground service keeps the BLE link alive and watches for incoming messages even when the app is in the background. A permanent status notification shows the connected node name.

### Disconnect
Use the disconnect button in the top bar to cleanly close the BLE link and return to the scan screen.

---

## 2. Public Channel Chat

The **Channel** tab shows the shared public channel — all nodes within mesh range can read and send messages here.

- Messages are displayed as chat bubbles (your messages on the right, others on the left)
- Each bubble shows sender name, timestamp, signal strength (SNR dB), and hop count
- Long-press any received message to react with an emoji
- Swipe right on any message to quote-reply
- A nearby-nodes strip shows recent advertisements at the top of the feed
- Character counter (160-character limit) appears as you type
- Draft text is saved if you navigate away mid-message

---

## 3. Private Channels

The **Channels** tab lists all configured private channels (slots 1–7 in addition to the public channel 0).

- Each channel has a unique name and a 128-bit AES encryption key
- Create a channel or generate a new random key from the Channels screen
- Share a channel with another user via QR code — they scan it with a standard QR reader and the app opens a join dialog via the `meshichan://` deep link
- Tap a channel to open its dedicated chat screen
- Channel utilisation graph: tap the bar-chart icon in any chat screen to see a 24-hour histogram of message activity (messages per hour)

---

## 4. Direct Messaging

Tap **Message** on any contact in the Nodes tab to open a private one-to-one chat.

- Messages are end-to-end encrypted using the contact's public key
- Delivery receipts show when a message was sent (✓) or confirmed delivered (✓✓)
- Round-trip time (RTT) is shown beside the delivered tick
- Typing indicators — a "typing…" label appears when the other person is composing
- Signal trend graph — if enough SNR readings exist, a sparkline chart shows link quality over time
- Long-press any received message to react with an emoji
- Swipe right on any message to quote-reply
- Ping button in the top bar sends a latency probe and shows the RTT in a snackbar

---

## 5. Message Features

### Swipe to Reply / Quote
Swipe any message rightward to pre-fill the input with a quoted excerpt. The reply is sent with the original text inline so the recipient can see context.

### Emoji Reactions
Long-press any received message bubble to choose from six reactions: 👍 ❤️ 😂 😮 😢 👎. Reactions are sent to the sender over the mesh and displayed as pill chips beneath the bubble. Multiple reactions from different people are aggregated with a count.

### Message Search
Tap the search icon in the Channel or private channel top bar to search message history. Results filter the list in real time.

### Message Export
Tap the share icon to export the full chat history as plain text. The system share sheet lets you save or forward it.

### Draft Persistence
Unsent text is saved automatically per channel and per DM. It is restored the next time you open the same conversation.

### Character Counter
A counter appears below the input field as you approach the 160-character limit, turning red near the limit.

---

## 6. Contacts & Node Discovery

### Nodes Tab
Lists all contacts the app has seen — either saved manually or observed via advertisements.

### Saving a Contact
Tap **Save** on any contact card to store them in the local database for future sessions.

### Nicknames
Tap the pencil icon on a contact card and set a friendly display name. The nickname overrides the node's raw broadcast name everywhere in the app.

### Contact Tags
Assign a short tag (e.g. "Team", "Camp A") to a contact for grouping. Tags appear as filter chips at the top of the Nodes list so you can quickly view a subset.

### Verified Badge
Mark a contact as verified (e.g. after confirming their public key in person). A blue shield icon appears beside their name in chat and on the contact card.

### Contact Backup & Restore
Export all saved nicknames, tags, and verified flags as a JSON file via **Settings → Device → Backup contacts**. Restore from a backup file with **Restore contacts** — useful when switching phones or recovering after a panic wipe.

### Block List
Long-press the block icon on a contact card (or use the block button) to silently ignore all future messages from that node. Blocked contacts appear with a red tint and can be unblocked from the card or from **Settings → Blocked Contacts**.

---

## 7. Map

The **Map** tab plots all contacts and recent advertisements that include GPS coordinates on an interactive map.

- Each node is shown as a marker with its name or short ID
- Tap a marker to see the node's name, short ID, and last-seen coordinates
- Your own location is shown if location sharing is enabled
- The map updates automatically as new GPS-tagged advertisements arrive

---

## 8. Advertising & Location

### One-Shot Advertise
Tap the tower icon (📡) in the Channel top bar to broadcast your presence with your current GPS location. Other nodes receive your name, node ID, and coordinates.

### Persistent Location Sharing
Tap the location icon in the Channel top bar to toggle ongoing location sharing. When active, every outgoing advertisement automatically includes your current GPS coordinates. The icon turns blue when sharing is on.

### SOS Beacon
Tap the SOS button in the Channel top bar to activate a repeating flood advertisement sent every 30 seconds. Useful in emergencies to continuously broadcast your presence to all nearby nodes. Tap again to stop.

### Stealth Mode
Tap the eye icon in the Channel top bar to suppress all outgoing advertisements. In stealth mode the node can still receive messages but does not advertise its presence. The icon changes to a crossed eye when active.

---

## 9. Privacy & Security

### Block List
Messages from blocked contacts are silently dropped at the repository layer — they are never written to the database. Blocked contacts can be managed in the Nodes tab or in Settings.

### Panic Wipe
**Settings → Danger Zone → Panic Wipe** permanently deletes all messages, contacts, nicknames, game records, and resets all app settings. The BLE connection is closed and the app returns to the scan screen. This action is irreversible.

### Private Channels
Private channels use a shared 128-bit AES key. Only nodes that have been given the key (via QR code or manual entry) can decrypt messages in that channel.

### Direct Messages
DMs are encrypted with the recipient's public key so only they can decrypt the content.

---

## 10. Notifications & Background Service

### Incoming Message Notifications
New DMs and channel messages trigger Android notifications with the sender name and a preview of the message text. Notification delivery for DMs and channel messages can be toggled independently in Settings.

### Quick Reply
Tap **Reply** directly in the notification shade to send a response without opening the app. The reply is routed through the mesh and the notification is dismissed automatically when the message is sent.

### Battery Low Alert
The background service periodically polls the connected node's battery level. If it drops below a configurable threshold (default 20%), a high-priority notification is shown. The alert fires once per charge cycle and resets when the battery recovers.

---

## 11. Games

All games are played peer-to-peer over the mesh using DM messages. Invite a contact from the **Nodes** tab. Game history (wins, losses, draws) is tracked and visible in the Game History screen accessible from Settings.

### Reversi (Othello)
Classic 8×8 Reversi. Tap a valid square to place your piece. Valid moves are highlighted. The game ends when the board is full or neither player can move.

### Chess
Full 8×8 chess with move validation and check detection. Tap a piece to see its legal moves highlighted, then tap the destination square. Supports castling, en passant, and pawn promotion.

### Tic-Tac-Toe
3×3 grid. First to three in a row wins. Fast-paced and low-bandwidth — ideal for low-signal conditions.

### Battleship
Classic 10×10 Battleship. Place five ships (Carrier 5, Battleship 4, Cruiser 3, Submarine 3, Destroyer 2) in the placement phase, then take turns calling shots. Ships are displayed with hit/sunk overlays. The game ends when all of one player's ships are sunk.

---

## 12. Diagnostics & Stats

### Node Info
Tap the info icon in the Public Channel top bar to see the connected node's firmware version, hardware model, free storage, and battery percentage.

### Radio Stats
View transmit power, frequency, bandwidth, spreading factor, and coding rate for the connected radio.

### SNR Trend Graph
In a DM conversation, if at least two signal readings exist, a sparkline chart is available showing received SNR (dB) over recent messages. Tap the signal-bars icon in the DM top bar to show or hide it.

### Channel Utilisation Graph
Tap the bar-chart icon in any chat screen to open a bottom sheet showing a 24-hour histogram of message activity broken into hourly buckets.

### Ping / Latency Test
In a DM conversation, tap the ping icon in the top bar to send a latency probe. The round-trip time appears in a snackbar. In the Nodes tab, tap the **Ping** button on any contact card for the same result.

### Crash Log
**Settings → Debug → Crash Log** displays the most recent unhandled exception stack trace, useful when reporting bugs.

---

## 13. Settings

| Setting | Description |
|---|---|
| Theme | Dark, Light, or follow System |
| DM notifications | Enable/disable notifications for incoming direct messages |
| Channel notifications | Enable/disable notifications for channel messages |
| Low battery alert | Toggle and configure the threshold (5%–50%) |
| Backup contacts | Export nicknames, tags, verified flags to JSON |
| Restore contacts | Import contacts from a backup JSON file |
| Clear saved device | Forget the auto-connect BLE address |
| Blocked Contacts | View and unblock any blocked contacts |
| Crash Log | View recorded crash traces |
| Game History | View win/loss/draw records for all games |
| Panic Wipe | Erase everything and reset to factory state |
| Version / Build | App version and build number |
