# Meshi — How-To Guide

Step-by-step instructions for every major feature in Meshi.

---

## Table of Contents

1. [Connecting to Your Node](#1-connecting-to-your-node)
2. [Sending & Receiving Messages](#2-sending--receiving-messages)
3. [Replying to a Message](#3-replying-to-a-message)
4. [Reacting to a Message](#4-reacting-to-a-message)
5. [Searching Messages](#5-searching-messages)
6. [Exporting Chat History](#6-exporting-chat-history)
7. [Direct Messaging a Contact](#7-direct-messaging-a-contact)
8. [Managing Contacts](#8-managing-contacts)
9. [Creating & Joining Private Channels](#9-creating--joining-private-channels)
10. [Sharing a Channel via QR Code](#10-sharing-a-channel-via-qr-code)
11. [Advertising Your Presence](#11-advertising-your-presence)
12. [Enabling Location Sharing](#12-enabling-location-sharing)
13. [Activating SOS Beacon](#13-activating-sos-beacon)
14. [Enabling Stealth Mode](#14-enabling-stealth-mode)
15. [Blocking a Contact](#15-blocking-a-contact)
16. [Unblocking a Contact](#16-unblocking-a-contact)
17. [Backing Up Contacts](#17-backing-up-contacts)
18. [Restoring Contacts from Backup](#18-restoring-contacts-from-backup)
19. [Pinging a Contact](#19-pinging-a-contact)
20. [Viewing the Channel Utilisation Graph](#20-viewing-the-channel-utilisation-graph)
21. [Viewing Node & Radio Stats](#21-viewing-node--radio-stats)
22. [Viewing the SNR Signal Trend](#22-viewing-the-snr-signal-trend)
23. [Replying from a Notification](#23-replying-from-a-notification)
24. [Playing Games](#24-playing-games)
25. [Viewing Game History](#25-viewing-game-history)
26. [Performing a Panic Wipe](#26-performing-a-panic-wipe)
27. [Changing the App Theme](#27-changing-the-app-theme)
28. [Configuring Notifications](#28-configuring-notifications)
29. [Configuring the Battery Alert](#29-configuring-the-battery-alert)
30. [Viewing the Crash Log](#30-viewing-the-crash-log)

---

## 1. Connecting to Your Node

1. Power on your MeshCore LoRa node and ensure Bluetooth is enabled on your phone.
2. Open Meshi. The **Scan** screen appears automatically.
3. Tap **Scan** to discover nearby BLE devices.
4. Tap your node in the list.
5. The app connects and navigates to the main screen.

> **Tip:** Meshi saves the last connected address. Next time you open the app it will attempt to reconnect automatically — you may not need to scan at all.

To forget the saved device: **Settings → Device → Clear saved device**.

---

## 2. Sending & Receiving Messages

**Public channel:**
1. Open the **Channel** tab (speech bubble icon in the bottom bar).
2. Type your message in the input field at the bottom.
3. Tap the send arrow or press the send key.

**Private channel:**
1. Open the **Channels** tab (# icon).
2. Tap the channel you want.
3. Type and send as above.

Messages are limited to **160 characters**. A counter appears below the input as you approach the limit.

> **Tip:** If you navigate away without sending, your text is saved as a draft and restored when you return.

---

## 3. Replying to a Message

1. In any chat screen, **swipe a message bubble to the right**.
2. A quoted excerpt of that message appears above the input field.
3. Type your reply and tap send.
4. The recipient will see your reply with the original text quoted inline.

To clear the quote without replying, tap the **×** on the reply bar.

---

## 4. Reacting to a Message

1. In any chat screen, **long-press a received message bubble**.
2. An emoji picker appears showing: 👍 ❤️ 😂 😮 😢 👎
3. Tap an emoji. Your reaction is sent to the message author over the mesh.
4. The emoji appears as a small pill chip beneath the bubble. If multiple people react with the same emoji, a count is shown (e.g. 👍 3).

> **Note:** You can only react to messages sent by others, not your own.

---

## 5. Searching Messages

1. In any chat screen, tap the **magnifying glass** icon in the top bar.
2. Type your search query. Results filter in real time.
3. Tap the back arrow to exit search and return to the full message list.

---

## 6. Exporting Chat History

1. In any chat screen, tap the **share** icon in the top bar.
2. The system share sheet opens with the full chat formatted as plain text.
3. Choose an app to save or forward the export (Files, email, etc.).

---

## 7. Direct Messaging a Contact

1. Open the **Nodes** tab (people icon).
2. Find the contact you want to message.
3. Tap **Message** on their contact card.
4. The DM screen opens. Type and send as usual.

> **Tip:** The contact must be in range (or reachable via the mesh) for the message to be delivered.

---

## 8. Managing Contacts

### Save a contact
In the **Nodes** tab, tap **Save** on a contact card. The contact is stored in the local database and will persist across sessions.

### Set a nickname
1. Tap the **pencil** icon on a contact card.
2. Enter a display name.
3. Tap **Save**.

The nickname replaces the node's broadcast name everywhere in the app.

### Assign a tag
1. Tap the **tag** icon (label icon) on a contact card.
2. Enter a short tag (e.g. "Base Camp", "Team A").
3. Tap **Save**.

Tags appear as filter chips at the top of the Nodes list.

### Mark as verified
1. Tap the **shield** icon on a contact card.
2. Confirm to mark the contact as verified.

A blue shield badge appears beside their name in chat and on the contact card. Use this after confirming a contact's identity (e.g. by comparing public key fingerprints in person).

---

## 9. Creating & Joining Private Channels

### Create a new channel
1. Open the **Channels** tab.
2. Tap **New Channel** (or the + button).
3. Enter a channel name.
4. A random 128-bit key is generated automatically. Tap **Create**.
5. The channel appears in the list. Tap it to open the chat.

### Join an existing channel manually
1. Open the **Channels** tab.
2. Tap **Join** or the + button and choose manual entry.
3. Enter the channel name and the 32-character hex key shared by the channel owner.
4. Tap **Join**.

### Join via QR code
See [Section 10](#10-sharing-a-channel-via-qr-code).

---

## 10. Sharing a Channel via QR Code

**To share:**
1. Open the **Channels** tab.
2. Tap the QR code icon on the channel you want to share.
3. A QR code is displayed encoding a `meshichan://` deep link.
4. Have the other person scan it.

**To join from a QR code:**
1. Scan the QR code with any standard QR scanner.
2. The scanner opens the Meshi app (or a browser link).
3. If Meshi is installed, a dialog appears: **"Join Channel? Join private channel '…' shared via QR code?"**
4. Tap **Join**. The channel is added to a free slot automatically.

---

## 11. Advertising Your Presence

Advertising broadcasts your node name, ID, and optionally your GPS location so nearby nodes can discover you.

**One-shot advertise:**
1. In the **Channel** tab, tap the **tower icon** (📡) in the top bar.
2. If location permission is granted, your current GPS coordinates are included.
3. If not yet granted, a permission request appears. Grant it to include coordinates.

---

## 12. Enabling Location Sharing

Persistent location sharing automatically includes your GPS coordinates in every outgoing advertisement.

1. In the **Channel** tab, tap the **location pin** icon in the top bar.
2. Grant location permission if prompted.
3. The icon turns **blue** — all future advertisements will include your current coordinates.
4. Tap the icon again to turn location sharing off (icon returns to grey).

Your coordinates will appear as a marker on other users' Maps.

---

## 13. Activating SOS Beacon

SOS mode sends a flood advertisement every 30 seconds to maximise your visibility to nearby nodes.

1. In the **Channel** tab, tap the **SOS** button in the top bar.
2. The button turns **red** — the repeating beacon starts immediately.
3. Tap the SOS button again to stop.

> **Note:** SOS mode respects Stealth mode — if Stealth is active, SOS advertisements are suppressed.

---

## 14. Enabling Stealth Mode

Stealth mode stops all outgoing advertisements so other nodes cannot detect your presence. You can still receive messages.

1. In the **Channel** tab, tap the **eye** icon in the top bar.
2. The icon changes to a **crossed eye** and turns teal — stealth is active.
3. Tap again to resume normal advertising.

---

## 15. Blocking a Contact

1. Open the **Nodes** tab.
2. Find the contact you want to block.
3. Tap the **block icon** (🚫) on their contact card.
4. Confirm in the dialog.

Blocked contacts' messages are silently ignored — they are not saved to the database. The contact card turns red to indicate blocked status.

---

## 16. Unblocking a Contact

**From the Nodes tab:**
1. Find the blocked contact (shown with a red card background).
2. Tap the **block icon** again to unblock.

**From Settings:**
1. Open **Settings** (gear icon).
2. Scroll to **Blocked Contacts**.
3. Tap **Unblock** next to the contact ID.

---

## 17. Backing Up Contacts

1. Open **Settings** (gear icon from any top bar).
2. Scroll to **Device → Backup contacts**.
3. Tap it. The system share sheet opens with a JSON file containing all nicknames, tags, and verified flags.
4. Save to Files, email to yourself, or send to another device.

---

## 18. Restoring Contacts from Backup

1. Ensure the backup JSON file is accessible on the device (e.g. in Downloads or Files).
2. Open **Settings → Device → Restore contacts**.
3. The file picker opens — navigate to and select the backup JSON file.
4. A dialog confirms how many contacts were imported (e.g. "Imported 12 contacts.").

> **Note:** Restoring merges into existing data — it does not delete contacts already on the device. If a contact already exists, the backup values overwrite them.

---

## 19. Pinging a Contact

Ping sends a latency probe to a contact and measures the round-trip time.

**From the Nodes tab:**
1. Find the contact in the list.
2. Tap the **Ping** button on their card.
3. The RTT appears in a snackbar at the bottom of the screen (e.g. "Ping Alice: ~340ms RTT").

**From a DM conversation:**
1. Open the DM screen with the contact.
2. Tap the **ping icon** (network ping icon) in the top bar.
3. The RTT appears in a snackbar.

---

## 20. Viewing the Channel Utilisation Graph

1. Open any chat screen (public channel, private channel, or DM is not applicable — only channel chats).
2. Tap the **bar chart** icon in the top bar.
3. A bottom sheet opens showing a bar chart of messages per hour over the last 24 hours.
4. Tap anywhere outside the sheet or swipe it down to close.

---

## 21. Viewing Node & Radio Stats

1. In the **Channel** tab (public channel), tap the **info icon** (ℹ) in the top bar.
2. A dialog shows:
   - Node name and firmware version
   - Hardware model
   - Battery percentage and free storage
   - Radio settings (frequency, bandwidth, spreading factor, coding rate, TX power)
3. Tap **Close** to dismiss.

---

## 22. Viewing the SNR Signal Trend

### What is it?

The three-bar signal icon in a DM conversation top bar is the **SNR trend graph toggle**. SNR stands for **Signal-to-Noise Ratio**, measured in decibels (dB). It reflects how cleanly your node is receiving radio packets from that contact through the mesh. A higher SNR means a stronger, cleaner signal; a lower or negative SNR means the signal is weak or noisy.

Tapping the icon reveals a **sparkline chart** inside the message list showing the SNR value recorded for each recent incoming message — up to the last 30 readings. This lets you see at a glance whether link quality is stable, improving, or degrading over time.

### Why is it useful?

- Helps you judge whether you and your contact are within reliable mesh range
- A downward trend may mean you are moving apart or conditions are changing — useful cue to relay through another node or reposition
- Useful for comparing different locations or antenna setups

### How to use it

Available once at least two messages have been received from the contact.

1. Open the DM conversation.
2. Tap the **signal bars icon** in the top bar (only visible when enough readings exist).
3. A sparkline chart appears at the top of the message list showing received SNR (dB) over recent messages.
4. Tap the icon again to hide the chart.

> **Note:** The data comes from SNR values embedded in each incoming radio packet by the MeshCore node. Up to 30 readings per contact are stored locally in the app's database.

---

## 23. Replying from a Notification

When a new message arrives while Meshi is in the background:

1. A notification appears in the status bar.
2. Expand the notification (swipe down on it or long-press).
3. Tap **Reply**.
4. Type your reply in the inline text field.
5. Tap the send arrow.

The reply is sent over the mesh and the notification is dismissed. You do not need to open the app.

---

## 24. Playing Games

All games are initiated from the **Nodes** tab.

### Challenging a contact

1. Open the **Nodes** tab.
2. Find the contact you want to play.
3. Tap the game button on their card:
   - **Reversi** — classic Othello
   - **Chess** — full chess
   - **TTT** — Tic-tac-toe
   - **BSH** — Battleship
4. An invite is sent. Wait for the contact to accept.
5. The game screen opens automatically.

### Accepting an invite
When someone challenges you, the game screen opens automatically on your device showing the game board.

### Battleship placement phase
1. Select a ship from the chip bar at the top.
2. Toggle **H** (horizontal) / **V** (vertical) to choose orientation.
3. Tap a cell on the grid to place the ship.
4. Use **Undo** to remove the last placed ship.
5. Once all 5 ships are placed, tap **Ready** to start.

### During a game
- It is always clear whose turn it is — the screen title shows "Your Turn" or "Opponent's Turn".
- In Battleship and Chess, valid moves are highlighted.
- Tap **Resign** or the back arrow to end the game early.

---

## 25. Viewing Game History

1. Open **Settings** (gear icon).
2. Tap **Game History** under the Debug section.
3. A leaderboard shows wins, losses, and draws for Reversi, Chess, Tic-tac-toe, and Battleship.

---

## 26. Performing a Panic Wipe

> **Warning:** This is irreversible. All data will be permanently deleted.

1. Open **Settings**.
2. Scroll to the **Danger Zone** section at the bottom.
3. Tap **Panic Wipe**.
4. Read the confirmation dialog carefully.
5. Tap **Wipe Everything** to proceed.

The app will delete all messages, contacts, nicknames, game records, and settings, close the BLE connection, and return to the scan screen.

---

## 27. Changing the App Theme

1. Open **Settings**.
2. Under **Appearance**, tap one of the three chips:
   - **Dark** — always use dark theme
   - **Light** — always use light theme
   - **System** — follow the device system setting

The theme changes immediately.

---

## 28. Configuring Notifications

1. Open **Settings**.
2. Under **Notifications**:
   - Toggle **Direct message notifications** on or off.
   - Toggle **Channel message notifications** on or off.

Changes take effect immediately. When disabled, no notifications are posted for that message type.

---

## 29. Configuring the Battery Alert

1. Open **Settings**.
2. Under **Battery Alert**, toggle **Low battery alert** on.
3. Use the slider to set the alert threshold (5%–50%, default 20%).
4. The foreground service checks battery level every 5 minutes. When the node's battery drops below the threshold, a high-priority notification is posted.

The alert fires once per charge cycle and automatically resets when the battery recovers above the threshold (e.g. when the node is charging).

---

## 30. Viewing the Crash Log

If the app has crashed previously:

1. Open **Settings → Debug → Crash Log**.
2. The most recent unhandled exception stack trace is shown.
3. Use the **Copy** button to copy it to the clipboard for sharing with a developer.

If no crash has occurred, a message indicates the log is empty.
