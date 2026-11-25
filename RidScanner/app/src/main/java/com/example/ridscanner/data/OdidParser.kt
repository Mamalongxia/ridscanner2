package com.example.ridscanner.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OpenDroneID / ASTM F3411 广播报文解析器。
 *
 * 广播方式：
 *  - BLE Legacy：Service UUID = 0xFFFA，Service Data 首字节 App Code = 0x0D，其后为 25 字节消息；
 *    多条消息可打包（MessagePack，首字节高 4 位 = 0xF）。
 *
 * 字节布局（25 字节/条，小端）与 opendroneid-core-c 保持一致：
 *  BasicID:  b0=[MsgType:4][Proto:4] b1=[UAType:4][IDType:4] b2..21 UASID(20) b22..24 reserved
 *  Location: b0 同上；b1=[Status:4][Reserved:1][HeightType:1][EW:1][SpeedMult:1]（LSb 在前）；
 *            b2 Direction, b3 SpeedH, b4 SpeedV(int8), b5..8 Lat(int32 LE), b9..12 Lon(int32 LE),
 *            b13..14 AltBaro(uint16 LE), b15..16 AltGeo, b17..18 Height,
 *            b19=[HorizAcc:4][VertAcc:4], b20=[SpeedAcc:4][BaroAcc:4],
 *            b21..22 Timestamp(uint16 LE), b23=[TSAcc:4][Reserved:4], b24 reserved
 *  System:   b0 同上；b1=[OpLocType:2][ClassType:3][Reserved:3]；
 *            b2..5 OpLat(int32 LE), b6..9 OpLon(int32 LE), b10..11 AreaCount, b12 AreaRadius,
 *            b13..14 AreaCeiling, b15..16 AreaFloor, b17=[ClassEU:4][CategoryEU:4],
 *            b18..19 OpAlt(uint16 LE), b20..23 Timestamp(uint32 LE), b24 reserved
 *  SelfID:   b0 同上；b1 DescType；b2..24 Desc(23)
 *  OperatorID: b0 同上；b1 OpIdType；b2..21 OpId(20)；b22..24 reserved
 */
object OdidParser {

    const val SERVICE_UUID = "0000fffa-0000-1000-8000-00805f9b34fb"
    const val APP_CODE = 0x0D
    const val MSG_SIZE = 25

    private const val LATLON_MULT = 10_000_000.0
    private const val ALT_DIV = 0.5f
    private const val ALT_ADDER = 1000f
    private val SPEED_DIV = floatArrayOf(0.25f, 0.75f)
    private const val VSPEED_DIV = 0.5f

    /** 从 BLE Service Data 中取出 ODID 消息体（跳过 App Code），返回若干条 25 字节消息。 */
    fun extractMessages(serviceData: ByteArray): List<ByteArray> {
        if (serviceData.isEmpty()) return emptyList()
        var offset = 0
        // 首字节 App Code
        if (serviceData[0].toInt() and 0xFF == APP_CODE) offset = 1
        val out = ArrayList<ByteArray>()
        while (offset + MSG_SIZE <= serviceData.size) {
            val msg = serviceData.copyOfRange(offset, offset + MSG_SIZE)
            out.add(msg)
            val msgType = msg[0].toInt() ushr 4
            if (msgType == 0xF) {
                // MessagePack：b1=单条大小，b2=包内条数，随后连续 25 字节消息
                val count = msg[2].toInt() and 0xFF
                offset += MSG_SIZE
                for (i in 0 until count) {
                    if (offset + MSG_SIZE > serviceData.size) break
                    out.add(serviceData.copyOfRange(offset, offset + MSG_SIZE))
                    offset += MSG_SIZE
                }
            } else {
                offset += MSG_SIZE
            }
        }
        return out
    }

    /** 把一条消息合并进 record。返回 true 表示这是一条可识别的 ODID 消息。 */
    fun parseInto(record: UasRecord, msg: ByteArray): Boolean {
        if (msg.size < MSG_SIZE) return false
        val msgType = msg[0].toInt() ushr 4
        return when (msgType) {
            0x00 -> parseBasicId(record, msg)
            0x01 -> parseLocation(record, msg)
            0x03 -> parseSelfId(record, msg)
            0x04 -> parseSystem(record, msg)
            0x05 -> parseOperatorId(record, msg)
            else -> false
        }
    }

    private fun parseBasicId(r: UasRecord, m: ByteArray): Boolean {
        r.idType = m[1].toInt() and 0x0F
        r.uaType = (m[1].toInt() ushr 4) and 0x0F
        r.uasId = asciiString(m, 2, 22)
        return true
    }

    private fun parseLocation(r: UasRecord, m: ByteArray): Boolean {
        val b1 = m[1].toInt() and 0xFF
        r.status = b1 and 0x0F
        val heightType = (b1 ushr 2) and 1
        val ewDir = (b1 ushr 1) and 1
        val speedMult = b1 and 1

        val dirEnc = m[2].toInt() and 0xFF
        r.direction = if (ewDir == 1) (dirEnc + 180).toFloat() else dirEnc.toFloat()

        val speedEnc = m[3].toInt() and 0xFF
        r.speedHorizontal = if (speedMult == 1)
            speedEnc * SPEED_DIV[1] + (255 * SPEED_DIV[0])
        else
            speedEnc * SPEED_DIV[0]

        r.speedVertical = m[4].toInt() * VSPEED_DIV

        r.latitude = int32LE(m, 5) / LATLON_MULT
        r.longitude = int32LE(m, 9) / LATLON_MULT
        r.altBaro = decodeAlt(uint16LE(m, 13))
        r.altGeo = decodeAlt(uint16LE(m, 15))
        r.height = decodeAlt(uint16LE(m, 17))
        r.heightRefGround = heightType == 1
        r.hasLocation = true
        return true
    }

    private fun parseSystem(r: UasRecord, m: ByteArray): Boolean {
        val b1 = m[1].toInt() and 0xFF
        r.operatorLocType = b1 and 0x03
        r.operatorLat = int32LE(m, 2) / LATLON_MULT
        r.operatorLon = int32LE(m, 6) / LATLON_MULT
        r.operatorAlt = decodeAlt(uint16LE(m, 18))
        r.hasOperator = r.operatorLat != 0.0 || r.operatorLon != 0.0
        return true
    }

    private fun parseSelfId(r: UasRecord, m: ByteArray): Boolean {
        r.selfDesc = asciiString(m, 2, 25)
        return true
    }

    private fun parseOperatorId(r: UasRecord, m: ByteArray): Boolean {
        // OperatorID 消息同样携带注册/操作员编号，可作为辅助展示
        r.uasId = if (r.uasId.isBlank()) asciiString(m, 2, 22) else r.uasId
        return true
    }

    // ---- 解码工具 ----
    private fun decodeAlt(raw: Int): Float = raw * ALT_DIV - ALT_ADDER

    private fun int32LE(b: ByteArray, off: Int): Int {
        return ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun uint16LE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun asciiString(b: ByteArray, start: Int, end: Int): String {
        val sb = StringBuilder()
        for (i in start until minOf(end, b.size)) {
            val c = b[i].toInt() and 0xFF
            if (c == 0) break
            if (c in 32..126) sb.append(c.toChar())
        }
        return sb.toString().trim()
    }

    /** 机型枚举（ASTM F3411 UAType）中文描述。 */
    fun uaTypeName(type: Int): String = when (type) {
        0 -> "未分类"
        1 -> "固定翼飞机"
        2 -> "直升机/多旋翼"
        3 -> "旋翼机"
        4 -> "滑翔机"
        5 -> "伞翼机"
        6 -> "滑翔翼"
        7 -> "风筝"
        8 -> "自由气球"
        9 -> "系留气球"
        10 -> "飞艇"
        11 -> "自由落体降落伞（无动力）"
        12 -> "火箭"
        13 -> "地面遥控载具"
        14 -> "其他"
        else -> "未知($type)"
    }

    /** 飞行状态（Location Status）。 */
    fun statusName(status: Int): String = when (status) {
        0 -> "未起飞"
        1 -> "在地面"
        2 -> "在空中"
        3 -> "紧急状态"
        4 -> "失控"
        else -> "未知($status)"
    }

    /** ID 类型。 */
    fun idTypeName(type: Int): String = when (type) {
        0 -> "无"
        1 -> "序列号"
        2 -> "民航注册号"
        3 -> "UTM 分配 UUID"
        4 -> "特定会话 ID"
        else -> "未知($type)"
    }
}
