package com.example.ridscanner.ble

import com.example.ridscanner.data.UasRecord

/**
 * 简单的内存共享：列表页把选中的设备记录放进来，地图页读取。
 * 进程内有效，无需序列化。
 */
object SharedStore {
    val records = LinkedHashMap<String, UasRecord>()

    fun upsert(record: UasRecord) {
        records[record.mac] = record
    }

    fun get(mac: String): UasRecord? = records[mac]
}
