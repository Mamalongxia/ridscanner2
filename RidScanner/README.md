# RidScanner — 附近无人机 RID（远程识别）扫描器

一个 Android 手机 App：被动扫描附近广播的 Open Drone ID（ASTM F3411）蓝牙广播，
在列表里显示飞机身份、状态、速度、高度、RSSI，点进地图页用标注标出**飞机位置**和**飞手位置**。

> 本 App 只做“被动接收 + 显示”，不连接飞机、不发送任何控制指令，不破解、不干扰飞控链路。

---

## 1. 它是怎么“看到”飞机的

合规无人机在飞行时会周期性地向外广播远程识别（Remote ID）报文，主要有三种物理载体：

| 载体 | 说明 | 普通手机能否收到 |
| --- | --- | --- |
| BLE Legacy（传统蓝牙广播） | Service UUID `0xFFFA`，App Code `0x0D`，每条 25 字节 | ✅ 本 App 已支持 |
| BLE Long Range（S8 长距离编码） | 国内国标主推 | ❌ 多数手机蓝牙栈解不了 |
| Wi-Fi Beacon / Wi-Fi NAN | 帧格式不同 | ❌ 仅少数机型支持 |

App 通过 `BluetoothLeScanner` 监听 `0xFFFA` 服务数据，跳过 App Code 后按 25 字节切分消息，
再按报文类型解码：

- **BasicID（0x00）**：序列号 / 民航注册号、机型；
- **Location（0x01）**：经纬度（int32 ×1e7）、气压/几何高度、相对高度、航向、水平/垂直速度、状态；
- **System（0x04）**：飞手（Operator）经纬度、飞手高度、活动区域；
- **SelfID（0x03）**：自选描述文字；
- **OperatorID（0x05）**：操作员编号。

同一台飞机会轮流发多条消息，App 按 MAC 地址把它们聚合成一条完整记录。

---

## 2. 在本机运行（Android Studio）

环境要求：Android Studio Hedgehog 或更新，JDK 17，Android SDK 34，minSdk 26（Android 8.0）。

1. 用 Android Studio 打开本目录 `RidScanner/`。
2. 首次打开会自动下载 Gradle 8.9 与依赖（需要能访问 google() / mavenCentral()）。
3. 连接一台 Android 手机（打开开发者选项 + USB 调试），点 Run。
4. App 内点「开始扫描」，授予蓝牙扫描、连接和定位权限。

> 如果提示缺少 `gradle/wrapper/gradle-wrapper.jar`：在 Android Studio 里点
> `File → Sync Project with Gradle Files` 会自动补全；或本机装好 Gradle 8.9 后执行 `gradle wrapper`。

### 权限说明

- `BLUETOOTH_SCAN`（Android 12+）：扫描广播；已声明 `neverForLocation`。
- `BLUETOOTH_CONNECT`：保留（本 App 不连接，后续扩展用）。
- `ACCESS_FINE_LOCATION`：Android 11 及以下蓝牙扫描系统要求附带定位；Android 12+ 用 `neverForLocation`。
- `INTERNET` / `ACCESS_NETWORK_STATE`：仅用于加载在线地图瓦片。

---

## 3. 地图

地图用开源的 **osmdroid**（OpenStreetMap 瓦片），不需要 Google / 高德 / 百度 API key：

- 蓝色罗盘标注 = 飞机位置；
- 绿色定位标注 = 飞手位置；
- 底部信息栏显示机型、ID、坐标、高度、描述。

首次显示地图需要联网下载瓦片；想离线使用，可在 osmdroid 里预置 MBTiles 或改用 mapsforge 离线地图（依赖已引入 `osmdroid-mapsforge`）。

---

## 4. 重要限制（务必看）

1. **只覆盖 ASTM F3411 / OpenDroneID 的 BLE Legacy。**
   国内国标 **GB 46750-2025**（2026-05-01 实施）报文格式与 ASTM 不同，且强制使用
   BLE Long Range S8 + Wi-Fi Beacon，传统蓝牙只是可选。因此很多国产合规机发出的信号，
   普通手机原生蓝牙栈可能收不到或解不出。这不是 App 的 bug，是物理/协议层限制。

2. **接收距离有限。** BLE Legacy 广播在开阔地通常十几米到几十米，遮挡后更短；
   RSSI 只反映信号强弱，不等于精确距离。

3. **位置来自飞机自己上报。** 若飞机 GPS 漂移、未起飞、未广播位置，地图上就没有点。

4. **收不到 ≠ 没有飞机。** 也可能是飞机用了 S8 / Wi-Fi 通道，或距离太远、被遮挡。

### 想收全（含国标 S8 / Wi-Fi）

手机方案覆盖不到时，推荐加一块专用接收硬件：

- **ESP32-S3 / ESP32-C6**：支持 BLE Long Range（C6 还能收 Wi-Fi），串口/蓝牙把解析结果回传给手机显示；
- 参考开源项目：
  - 接收端 App：https://github.com/opendroneid/receiver-android
  - 编解码库：https://github.com/opendroneid/opendroneid-core-c
  - 兼容机型清单：https://github.com/opendroneid/receiver-android/blob/master/supported-smartphones.md

---

## 5. 目录结构

```
RidScanner/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/ridscanner/
│       │   ├── data/
│       │   │   ├── UasRecord.kt      # 聚合后的数据模型
│       │   │   └── OdidParser.kt     # F3411 报文解码（经纬度/高度/速度/航向）
│       │   ├── ble/
│       │   │   ├── RidScanManager.kt  # BLE 扫描 + 按 MAC 聚合
│       │   │   └── SharedStore.kt     # 跨页面共享记录
│       │   └── ui/
│       │       ├── DeviceListActivity.kt
│       │       ├── DeviceAdapter.kt
│       │       └── MapActivity.kt     # osmdroid 标注飞机/飞手
│       └── res/                      # 布局、字符串、图标
├── build.gradle / settings.gradle / gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

---

## 6. 合规提示

本工具用于个人学习、观测与安全了解周边空域。请勿用于跟踪他人、规避监管或任何违法飞行。
实际飞行请遵守民航局无人机实名登记、空域申请与当地低空管理规定。
