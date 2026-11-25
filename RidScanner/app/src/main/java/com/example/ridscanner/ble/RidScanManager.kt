package com.example.ridscanner.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.example.ridscanner.data.OdidParser
import com.example.ridscanner.data.UasRecord
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE 扫描管理器：扫描 0xFFFA 服务，解析 ODID 消息，按 MAC 聚合。
 * 注意：本类只负责“被动接收”，不连接飞机、不发送任何控制指令。
 */
class RidScanManager(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var leScanner: BluetoothLeScanner? = null

    private val records = ConcurrentHashMap<String, UasRecord>()
    private val handler = Handler(Looper.getMainLooper())

    /** 每次有新数据时回调（主线程）。 */
    var onUpdate: ((List<UasRecord>) -> Unit)? = null

    @Volatile
    var scanning = false
        private set

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            // 扫描失败：errorCode 1=ALREADY_STARTED, 2=APPLICATION_REGISTRATION_FAILED 等
        }
    }

    private fun handleResult(result: ScanResult) {
        val device: BluetoothDevice = result.device
        val mac = device.address ?: return
        // 只认 0xFFFA 服务数据
        val serviceData = result.scanRecord?.getServiceData(
            ParcelUuid.fromString(OdidParser.SERVICE_UUID)
        ) ?: return

        val messages = OdidParser.extractMessages(serviceData)
        if (messages.isEmpty()) return

        val record = records.getOrPut(mac) { UasRecord(mac = mac) }
        messages.forEach { OdidParser.parseInto(record, it) }
        record.rssi = result.rssi
        record.lastSeen = System.currentTimeMillis()
        SharedStore.upsert(record)

        emitUpdate()
    }

    private fun emitUpdate() {
        handler.post {
            onUpdate?.invoke(records.values.sortedByDescending { it.lastSeen })
        }
    }

    fun start(): Boolean {
        if (scanning) return true
        if (adapter == null || !adapter.isEnabled) return false
        leScanner = adapter.bluetoothLeScanner ?: return false

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(OdidParser.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        leScanner?.startScan(listOf(filter), settings, scanCallback)
        scanning = true
        return true
    }

    fun stop() {
        if (!scanning) return
        leScanner?.stopScan(scanCallback)
        scanning = false
    }

    fun records(): List<UasRecord> =
        records.values.sortedByDescending { it.lastSeen }

    fun clear() = records.clear()
}
