# Android 权限与原生能力评估

状态：2026-11-10。目标不是“把所有权限都申请一遍”，而是把**能安全、可解释、对 agent 有用**的
Android 能力接进来；受限权限和系统级权限明确不接。

## 1. 结论

已接入并可从 agent 调用：

| 能力 | Android 权限 | 接入方式 | 状态 |
|---|---|---|---|
| 麦克风 | `RECORD_AUDIO` | WebView `getUserMedia` + 权限中心 | ✅ 真机验证 |
| 相机 | `CAMERA` | WebView `getUserMedia` + 权限中心 | ✅ 真机验证 |
| 前台定位 | `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | `android_location` 一次性定位 | ✅ 真机验证；失败时返回明确 stale/超时 |
| 通知 | `POST_NOTIFICATIONS`（13+） | `android_notify` + Host 前台服务 | ✅ 真机验证 |
| 蓝牙扫描/已配对设备 | `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`（12+）；旧版 `BLUETOOTH`/`BLUETOOTH_ADMIN` + 定位 | `android_bluetooth_status`、`android_bluetooth_scan` | ✅ 真机验证（测试环境未发现附近设备） |
| 直接拨号 | `CALL_PHONE` | `android_phone_call` | ✅ 真机验证拉起 `InCallActivity` |
| 读取联系人 | `READ_CONTACTS` | `android_contacts_search` | ✅ 权限/接口验证；测试机无联系人 |
| 权限查询/申请 | 上述运行时权限 | `android_permissions`、`android_request_permission` + 原生权限中心 | ✅ 工具调用验证 |

agent 侧工具名统一为 `android_*`，由 rootfs 内的
`@deepseek-ai/dsh-android-bridge` 插件注册。插件不直接碰 Android API，只调用本机回环桥。

## 2. 明确不接入 / 暂缓

| 权限/能力 | 决定 | 原因 |
|---|---|---|
| 后台定位 `ACCESS_BACKGROUND_LOCATION` | ❌ 不接 | 需要“始终允许”特殊授权，Google Play 审核严格；agent 只做一次性前台定位 |
| 短信 `SEND_SMS` / `READ_SMS`、通话记录 `READ_CALL_LOG` | ❌ 不接 | Play 仅允许默认短信/电话应用；侧载也不应让 agent 静默发短信 |
| `READ_PHONE_STATE` / IMEI、`MANAGE_OWN_CALLS`、接听/挂断电话 | ❌ 不接 | 敏感且无明确 agent 场景；直接拨号已满足“打电话” |
| `MANAGE_EXTERNAL_STORAGE`、广泛文件访问 | ❌ 不接 | 用 SAF/系统文件选择器；Workspace 用应用专属外部目录，见 `packaging.md` |
| 通讯录写入、日历读写 | ⏸ 暂缓 | 技术上可行，但需要逐项设计冲突/确认；先只读联系人 |
| 身体传感器、活动识别、附近 Wi-Fi、NFC | ⏸ 暂缓 | 可行，但当前 dsh 场景弱，先不扩大权限面 |
| 无障碍服务、设备管理员、使用情况访问、悬浮窗、`QUERY_ALL_PACKAGES` | ❌ 不接 | 权限过大，等于把设备控制权交给 agent；不符合最小权限 |
| 精确闹钟、忽略电池优化、全屏 Intent | ❌ 不接 | 当前 Host 前台服务不需要 |
| 后台麦克风/相机 | ❌ 不接 | Android 10+ 限制且隐私风险高；仅前台 WebView 使用 |

## 3. 运行时权限中心

原生顶栏 `Permissions` 打开权限中心，列出：

`Microphone`、`Camera`、`Location`、`Notifications`、`Bluetooth devices`、
`Phone calls`、`Contacts (read)`。

- 点击未授权项先经过高危二次确认（电话/通讯录/定位/蓝牙），再调用系统运行时权限弹窗；
  已授权项显示 `✓ Granted`。
- 用户拒绝且不再弹窗时，提示去系统应用设置。
- 通知权限在首次 Host 就绪时请求一次（Android 13+）。
- 权限不能由 agent 静默授予；`android_request_permission` 只能**打开权限中心**，用户必须点允许。

## 4. agent 原生桥

### 架构

```
dsh agent
  └─ @deepseek-ai/dsh-android-bridge（dsh 工具插件）
       └─ HTTP 127.0.0.1:<随机端口>，X-DSHD-Token
            └─ DeviceBridge（Android 前台服务内）
                 ├─ DevicePermissions
                 ├─ LocationManager
                 ├─ BluetoothLeScanner
                 ├─ ContactsContract
                 ├─ Intent.ACTION_CALL
                 └─ NotificationManager
```

- 只绑定 `127.0.0.1`，不监听局域网。
- 每次 Host 服务启动生成 32 字节随机 token；写入 rootfs
  `home/.dsh/android-bridge.json`，权限 `0600`。
- token 随服务重启轮换；插件每次调用重新读取。
- 桥只接受回环请求；没有 CORS/远程入口。
- 桥随 `HostKeepAliveService` 启停，Host 不在时工具返回明确的 `bridge unavailable`。

### 工具

| 工具 | 作用 | 权限 |
|---|---|---|
| `android_permissions` | 查询所有已接入权限状态 | 无 |
| `android_request_permission` | 打开权限中心并请求指定权限 | 用户交互 |
| `android_location` | 一次性前台经纬度；无新定位时返回 `stale: true` 缓存 | 定位 |
| `android_bluetooth_status` | 蓝牙开关、本机名、已配对设备 | 蓝牙 |
| `android_bluetooth_scan` | BLE 扫描 3–15 秒 | 蓝牙（旧版另需定位） |
| `android_contacts_search` | 按姓名/号码搜索联系人，最多 50 条 | 读联系人 |
| `android_phone_call` | **立即拨出**指定号码，5 秒冷却 | 电话 |
| `android_notify` | 发本地通知 | 通知（13+） |

直接拨号是用户明确选择的行为；工具描述要求模型只在用户明确要求时调用，且不得猜号码。

## 5. 高危权限的二次确认

除了 Android 系统权限框，高危权限在申请前还有一层**应用内确认**：

| 权限 | 二次确认文案要点 |
|---|---|
| `Phone calls` | agent 可直接拨出号码，不再询问 |
| `Contacts (read)` | agent 可读取/搜索整个通讯录，只读 |
| `Location` | agent 可在前台获取当前位置，无后台跟踪 |
| `Bluetooth devices` | agent 可扫描附近蓝牙设备 |

流程：

1. 用户点权限中心的 `Grant`，或 agent 调 `android_request_permission`；
2. 原生弹出 `Confirm sensitive permission`，说明该权限会让 agent 做什么；
3. 用户点 `Continue` 后才调用 Android `requestPermissions()`；点 `Cancel` 则完全不向系统申请；
4. 已授权的权限不会重复弹二次确认；
5. 麦克风/相机仍由 WebView 在真正调用 `getUserMedia` 时直接走系统权限框，避免双重打扰。

说明：这是**申请权限时的确认**，不是每次拨号都确认；用户此前明确选择“授权后直接拨号”，
因此电话授权后 `android_phone_call` 会直接拨出。若要改成每次拨号都确认，需要另做操作级确认。

## 5.1 权限审计

2026-11-10 二次检查了 Manifest、`DevicePermissions`、原生桥和插件。当前已实现能力没有
发现缺声明权限；同时确认以下权限**故意不加**：

| 权限/能力 | 审计结论 |
|---|---|
| `VIBRATE` | 当前通知不承诺振动；需要振动时再加，并设置 channel 振动 |
| `WAKE_LOCK` | Host 是前台服务但不持有 CPU 锁；长任务跨息屏需要另行设计电量策略 |
| `ACCESS_NETWORK_STATE` | 下载/HTTP 失败可直接从异常处理，不需要额外权限 |
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` | 文件选择走 SAF；Workspace 用应用专属外部目录，不需要广泛存储权限 |
| `FOREGROUND_SERVICE_LOCATION/MICROPHONE/CAMERA` | `targetSdk 28`，且没有后台定位/后台相机麦克风 |
| `ACCESS_WIFI_STATE` / `NEARBY_WIFI_DEVICES` | 没有 Wi-Fi 工具 |
| `NFC` / `BLUETOOTH_ADVERTISE` | 没有 NFC/蓝牙广播功能 |
| `BODY_SENSORS` / `ACTIVITY_RECOGNITION` / 日历 | 没有对应工具 |
| 短信、通话记录、`READ_PHONE_STATE`、接听/挂断 | 明确不接，见第 2 节 |

本次同时补了两个边界：

- `BLUETOOTH_SCAN` 增加 `android:usesPermissionFlags="neverForLocation"`，明确不用蓝牙扫描
  推断位置；实测 BLE 扫描仍能发现设备。
- Android 11 及更低的 `Bluetooth devices` 现在要求 **精确** 定位也已授权，避免权限中心显示
  `Granted` 但 BLE 扫描实际不可用；单独的前台定位仍允许粗略定位。

## 6. 平台/上架限制

- `targetSdk 28`：Android 12 仍需为蓝牙扫描申请 `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`；
  已在真机确认仅靠旧 `BLUETOOTH` 会抛 `SecurityException`。
- Android 11 及更早：BLE 扫描需要定位授权。
- `CALL_PHONE`、`READ_CONTACTS`、`BLUETOOTH_SCAN` 在 Google Play 属于敏感/受审查权限；
  本项目当前是侧载分发。若要上 Play，需要逐项声明用途，短信/通话记录类权限不要加。
- 直接拨号在后台受 Android 10+ 后台启动 Activity 限制；真机验证从 Host 服务拉起
  `InCallActivity` 成功，但锁屏/系统策略仍可能拦截，失败会返回错误而不是假装成功。
- 没有真实 SIM 的测试机无法完成通话建立；验证到系统通话界面。

## 7. 测试记录

```text
GET  /v1/health                      -> ok
GET  /v1/permissions                 -> mic/camera/location/bluetooth/phone/contacts 状态
GET  /v1/location                    -> 30.234052,120.026504 (network, 40m)
GET  /v1/bluetooth                   -> available, enabled, name=Magic4 Pro
POST /v1/bluetooth/scan              -> ok, 4 个 BLE 广播（含 Mijia Scale S800 BB09）
POST /v1/phone/call {"10086"}        -> 拉起 com.android.incallui/.InCallActivity
POST /v1/notify                      -> 通知中心出现标题/正文
WebView getUserMedia(audio)          -> 成功
WebView getUserMedia(video)          -> 成功（front camera）
WebView navigator.geolocation        -> 成功
agent 调用 android_permissions       -> 返回各权限 granted 状态
agent 调用 android_location          -> 工具执行（无新鲜定位时超时，属预期失败路径）
```

证据：`evidence/android-permissions-2026-11-10.txt`、`evidence/ui-permissions-320.png`、
`evidence/ui-permissions-375.png`、`evidence/ui-permissions-confirm-375.png`、
`evidence/android-iteration-2026-11-10.md`。

## 8. 已知缺口

- 位置缓存只在成功定位后写入；设备锁屏/无网络且无历史定位时会返回超时。
- 联系人测试机为空；接口返回 `[]`，没有覆盖有联系人的排序/去重。
- BLE 扫描最终验证到 4 个真实广播设备；未验证 GATT 连接/读写。
- 未实现 agent 拍照/录音落盘、蓝牙 GATT 连接、短信、日历、NFC。
