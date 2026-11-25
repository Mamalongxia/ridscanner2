# Open Drone ID (ASTM F3411) 报文速查（25 字节/条，小端）

来源：opendroneid-core-c（https://github.com/opendroneid/opendroneid-core-c）

## 公共头
- `byte0 = [MessageType:4][ProtoVersion:4]`，高 4 位是消息类型：
  - 0x00 BasicID / 0x01 Location / 0x02 Auth / 0x03 SelfID /
    0x04 System / 0x05 OperatorID / 0xF MessagePack
- BLE 服务：Service UUID `0000fffa-0000-1000-8000-00805f9b34fb`，
  Service Data 首字节 App Code = 0x0D，其后才是消息。

## BasicID（0x00）
| 偏移 | 内容 |
| --- | --- |
| 0 | 公共头 |
| 1 | [UAType:4][IDType:4] |
| 2..21 | UASID（20 字节 ASCII，0 填充） |
| 22..24 | reserved |

## Location（0x01）
| 偏移 | 内容 |
| --- | --- |
| 0 | 公共头 |
| 1 | [Status:4][Reserved:1][HeightType:1][EWDirection:1][SpeedMult:1]（LSb 在前） |
| 2 | Direction（EW=1 则 +180） |
| 3 | SpeedHorizontal（mult=0: ×0.25；mult=1: ×0.75 + 63.75） |
| 4 | SpeedVertical（int8 ×0.5 m/s） |
| 5..8 | Latitude（int32 LE，÷1e7） |
| 9..12 | Longitude（int32 LE，÷1e7） |
| 13..14 | AltitudeBaro（uint16 LE，×0.5 − 1000） |
| 15..16 | AltitudeGeo（同上） |
| 17..18 | Height（同上） |
| 19 | [HorizAcc:4][VertAcc:4] |
| 20 | [SpeedAcc:4][BaroAcc:4] |
| 21..22 | Timestamp（uint16 LE，÷10 = 整点后秒；0xFFFF 无效） |
| 23 | [TSAcc:4][Reserved:4] |
| 24 | reserved |

## System（0x04）
| 偏移 | 内容 |
| --- | --- |
| 0 | 公共头 |
| 1 | [OpLocType:2][ClassType:3][Reserved:3] |
| 2..5 | OperatorLatitude（int32 LE，÷1e7） |
| 6..9 | OperatorLongitude（int32 LE，÷1e7） |
| 10..11 | AreaCount（uint16 LE） |
| 12 | AreaRadius（×10 米） |
| 13..14 | AreaCeiling（uint16 LE，×0.5 − 1000） |
| 15..16 | AreaFloor（同上） |
| 17 | [ClassEU:4][CategoryEU:4] |
| 18..19 | OperatorAltitudeGeo（uint16 LE，×0.5 − 1000） |
| 20..23 | Timestamp（uint32 LE） |
| 24 | reserved |

## SelfID（0x03）
byte0 公共头；byte1 DescType；byte2..24 Desc（23 字节 ASCII）。

## OperatorID（0x05）
byte0 公共头；byte1 OpIdType；byte2..21 OpId（20 字节）；byte22..24 reserved。

## 解码常量
- 经纬度：raw / 10,000,000.0（无效为 0）
- 高度：raw × 0.5 − 1000（无效为 −1000）
- 水平速度：mult=0 → raw×0.25；mult=1 → raw×0.75 + 255×0.25
- 垂直速度：int8 × 0.5
- 航向：EW=1 → raw + 180
