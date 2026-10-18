package com.meshi.code.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.meshi.code.MeshiConfig
import com.meshi.code.protocol.MeshCoreBle
import com.meshi.code.protocol.NodeFrame
import com.meshi.code.protocol.PacketFramer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

data class ScanDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val device: BluetoothDevice,
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

@SuppressLint("MissingPermission")
class MeshiBleManager(private val context: Context) {

    private val adapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ---- Public state ----
    private val _scanResults = MutableStateFlow<List<ScanDevice>>(emptyList())
    val scanResults: StateFlow<List<ScanDevice>> = _scanResults

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    /** All decoded frames — both sync responses and async push events. */
    private val _frames = MutableSharedFlow<NodeFrame>(extraBufferCapacity = 128)
    val frames: Flow<NodeFrame> = _frames

    /** Self info received after handshake. */
    private val _selfInfo = MutableStateFlow<NodeFrame.SelfInfo?>(null)
    val selfInfo: StateFlow<NodeFrame.SelfInfo?> = _selfInfo

    // ---- Internal ----
    private var gatt: BluetoothGatt? = null
    /** Characteristic we WRITE to (NUS RX = 6E400002). */
    private var writeChar: BluetoothGattCharacteristic? = null
    private var scanJob: Job? = null

    /**
     * Channel used during handshake to synchronously wait for specific sync responses.
     * Only non-push frames (code < 0x80) are sent here.
     */
    private val syncChannel = Channel<NodeFrame>(Channel.UNLIMITED)

    // ---- Scanning ----

    fun startScan() {
        val seen = mutableMapOf<String, ScanDevice>()
        val leScanner = adapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName
                val dev = ScanDevice(result.device.address, name, result.rssi, result.device)
                seen[dev.address] = dev
                _scanResults.value = seen.values.sortedByDescending { it.rssi }
            }
        }

        leScanner.startScan(null, settings, callback)
        scanJob = scope.launch {
            try { awaitCancellation() } finally { leScanner.stopScan(callback) }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    // ---- Connection ----

    /** Connect directly by saved MAC address without scanning first. */
    fun connectByAddress(address: String) {
        try {
            val device = adapter.getRemoteDevice(address)
            connect(ScanDevice(address, null, 0, device))
        } catch (_: Exception) { /* invalid address — ignore */ }
    }

    fun connect(dev: ScanDevice) {
        scope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            val gattCallback = makeGattCallback()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dev.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                dev.device.connectGatt(context, false, gattCallback)
            }
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _selfInfo.value = null
    }

    // ---- Write ----

    @Suppress("DEPRECATION")
    suspend fun sendRaw(frame: ByteArray): Boolean {
        val g = gatt ?: return false
        val wc = writeChar ?: return false
        return withContext(Dispatchers.Main) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(
                        wc, frame,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    wc.value = frame
                    wc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    g.writeCharacteristic(wc)
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    // ---- Convenience senders ----

    suspend fun sendAdvert(flood: Boolean = false) = sendRaw(PacketFramer.sendSelfAdvertFrame(flood))

    suspend fun sendChannelMessage(text: String, channelIdx: Int = 0) =
        sendRaw(PacketFramer.sendChannelMessageFrame(channelIdx, text))

    suspend fun sendDirectMessage(recipientPubKeyPrefix: ByteArray, text: String) =
        sendRaw(PacketFramer.sendDirectMessageFrame(recipientPubKeyPrefix, text))

    suspend fun pollNextMessage() = sendRaw(PacketFramer.syncNextMessageFrame())

    // ---- GATT callback ----

    private fun makeGattCallback() = object : BluetoothGattCallback() {

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            // MTU negotiated — now discover services
            g.discoverServices()
        }

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Request larger MTU before service discovery
                    g.requestMtu(MeshiConfig.BLE_MTU_SIZE)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    gatt = null; writeChar = null; _selfInfo.value = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect(); return
            }

            val service = g.getService(MeshCoreBle.SERVICE_UUID)
            if (service == null) { g.disconnect(); return }

            writeChar = service.getCharacteristic(MeshCoreBle.RX_CHAR_UUID) // App writes here
            val notifyChar = service.getCharacteristic(MeshCoreBle.TX_CHAR_UUID) // App reads here

            if (writeChar == null || notifyChar == null) { g.disconnect(); return }

            // Enable BLE notifications on the TX characteristic
            g.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(MeshCoreBle.CCCD_UUID)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            }
            gatt = g
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            // Notifications enabled — now run the handshake
            scope.launch { performHandshake() }
        }

        // API < 33
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                characteristic.value?.let { handleBytes(it) }
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleBytes(value)
        }
    }

    // ---- Frame routing ----

    private fun handleBytes(data: ByteArray) {
        val frame = PacketFramer.decode(data)
        scope.launch {
            _frames.emit(frame)
            if (!frame.isAsyncPush) {
                syncChannel.trySend(frame)
            }
        }
    }

    // ---- Handshake ----

    /**
     * Required sequence every BLE session:
     * DEVICE_QUERY → APP_START → SET_DEVICE_TIME → poll queued messages
     */
    private suspend fun performHandshake() {
        // Clear any stale responses
        while (!syncChannel.isEmpty) syncChannel.tryReceive()

        // 1. Device query
        sendRaw(PacketFramer.deviceQueryFrame())
        waitForSync<NodeFrame.DeviceInfo>(timeoutMs = MeshiConfig.HANDSHAKE_DEVICE_QUERY_TIMEOUT_MS) ?: run { disconnect(); return }

        // 2. App start — returns SELF_INFO with node's public key and config
        sendRaw(PacketFramer.appStartFrame("Meshi"))
        val selfInfo = waitForSync<NodeFrame.SelfInfo>(timeoutMs = MeshiConfig.HANDSHAKE_APP_START_TIMEOUT_MS) ?: run { disconnect(); return }
        _selfInfo.value = selfInfo

        // 3. Sync clock
        sendRaw(PacketFramer.setDeviceTimeFrame())
        waitForSync<NodeFrame.Ok>(timeoutMs = MeshiConfig.HANDSHAKE_CLOCK_SYNC_TIMEOUT_MS) // best-effort

        // Session is now active
        _connectionState.value = ConnectionState.CONNECTED

        // 4. Drain any queued messages
        pollQueuedMessages()
    }

    /** Pull all messages waiting in the node's offline queue. */
    suspend fun pollQueuedMessages() {
        while (true) {
            sendRaw(PacketFramer.syncNextMessageFrame())
            val resp = waitForSync<NodeFrame>(timeoutMs = MeshiConfig.HANDSHAKE_CLOCK_SYNC_TIMEOUT_MS) ?: break
            if (resp is NodeFrame.NoMoreMessages) break
        }
    }

    private suspend inline fun <reified T : NodeFrame> waitForSync(timeoutMs: Long): T? {
        return withTimeoutOrNull(timeoutMs) {
            var frame: NodeFrame
            do { frame = syncChannel.receive() } while (frame !is T)
            frame as T
        }
    }
}
