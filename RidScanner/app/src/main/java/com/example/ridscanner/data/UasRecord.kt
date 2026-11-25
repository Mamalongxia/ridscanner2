package com.example.ridscanner.data

/**
 * 一台无人机（UAS）聚合后的远程识别数据。
 * 同一台飞机会轮流广播 BasicID / Location / System 等多条 25 字节消息，
 * 这里按 MAC 地址把它们合并成一条完整记录。
 */
data class UasRecord(
    val mac: String,
    // BasicID
    var uasId: String = "",
    var idType: Int = 0,
    var uaType: Int = 0,
    // Location
    var status: Int = -1,
    var direction: Float = Float.NaN,      // 真北航向 0-359 度
    var speedHorizontal: Float = Float.NaN, // m/s
    var speedVertical: Float = Float.NaN,   // m/s
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var altBaro: Float = Float.NaN,
    var altGeo: Float = Float.NaN,
    var height: Float = Float.NaN,
    var heightRefGround: Boolean = false,
    // System（飞手位置）
    var operatorLat: Double = 0.0,
    var operatorLon: Double = 0.0,
    var operatorAlt: Float = Float.NaN,
    var operatorLocType: Int = -1,
    // SelfID 描述
    var selfDesc: String = "",
    // 信号与时间
    var rssi: Int = Int.MIN_VALUE,
    var lastSeen: Long = 0L,
    var hasLocation: Boolean = false,
    var hasOperator: Boolean = false
) {
    fun hasPosition() = hasLocation && latitude != 0.0 && longitude != 0.0
}
