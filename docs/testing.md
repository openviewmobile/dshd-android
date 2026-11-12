# Android 测试

## 前置条件

- rooted Android 设备（当前验收设备 LGE-AN10 / Android 12 / arm64）；
- 已安装 `com.dshd.android`，rootfs 已就绪（内置版首次启动会自动解包），Provider 已配置并可用；
- `adb` 可访问设备；Node.js >= 22（E2E 需要全局 `fetch`/`WebSocket`）。

## 自动化 E2E

```bash
scripts/android-e2e.sh
```

脚本会启动 App、等待 WebView DevTools、创建新会话、发一条真实 prompt，并验证：

1. WebView shell 与 composer 可加载；
2. Agent 真实回复（默认 `E2E-OK`）；
3. 附件上传 API 返回 `ok:true`；
4. 页面存在原生 `input[type=file]`（WebChromeClient 文件选择集成）。

当前结果：

```text
PASS webview shell and composer
PASS agent round trip — E2E-OK
PASS attachment upload route — HTTP 200
PASS file chooser input present
4/4 checks passed
```

## 手工/验收矩阵

| 范围 | 覆盖 |
|---|---|
| 全新安装 | 内置 fixed22-nativepdf 自动解包（xz，143 MB），Provider 首启，真实对话 |
| 会话生命周期 | 新建、发送、停止、重命名、归档/撤销、搜索 |
| 进程/网络 | 任务中 force-stop 恢复；断网 5 次重试后显示 TRANSPORT 错误 |
| 布局 | 横屏/竖屏、软键盘、旋转恢复 |
| 设置 | 通用、模型/Provider 编辑、内置插件、Agent 预设、快捷键、Session Log |
| 附件 | SAF 选择 9.3 MB TXT；PNG 图片 normalized attachment |
| PDF | 文本/嵌入字体 PDF 在 375/320 CSS px 页面可见；原生桥渲染，缩放控件可用 |
| Agent | 工具矩阵、双队友 agent-team 真实任务 |
| 插件 | whale-pet 安装/启用/隐藏；worktree、auto-review、web search |
| 语音 | 权限、录音、SenseVoice 识别、插入 composer |

详细证据与结论见
../evidence/android-acceptance-2026-11-10.md。

## 已知限制

- E2E 依赖已配置的真实 Provider，离线环境会失败；
- Session Log 开关已测，但自建 gateway 不执行官方 API 上传；
- GitHub PR 跟踪未测（设备无 `gh`）；
- 语音模型首次使用需下载约 240 MB；
- 桌面专用“打开配置文件”入口在手机上隐藏。


## Android 权限与原生桥

真机检查（应用在前台时）：

```bash
# 权限状态
adb shell pm list permissions -g -d | grep com.dshd.android
# 权限中心在原生顶栏 Permissions；工具状态由 agent 调用 android_permissions

# 桥健康（token 在 rootfs home/.dsh/android-bridge.json）
curl -H "X-DSHD-Token: $TOKEN" http://127.0.0.1:$PORT/v1/health
curl -H "X-DSHD-Token: $TOKEN" http://127.0.0.1:$PORT/v1/permissions
```

覆盖：

- 高危二次确认：撤销 `CALL_PHONE`/`READ_CONTACTS`，点权限中心对应项；
  先弹 `Confirm sensitive permission`，`Cancel` 不出现系统权限框，`Continue` 才出现。
- 权限中心在 375 宽和 320 宽下可滚动；授予/拒绝/去系统设置。
- WebView 麦克风、相机、定位各一次成功。
- agent 调用 `android_permissions`、`android_location` 至少各一次。
- 直接拨号、BLE 扫描、联系人查询、通知分别调用一次；无 SIM/无设备/无联系人时断言
  返回明确的空结果或错误，而不是伪成功。

## 内置 rootfs 回归

1. 卸载/清除 App 数据后安装 0.3.2 内置 APK；
2. 冷启动，确认深色启动页出现且无白屏；观察 “Preparing… → Unpacking… →
   Starting the local AI engine → Opening your workspace” 的分阶段文案和进度条；
3. 等 rootfs.version 写入内置包 SHA-256，Provider 首启；
4. 配置 Provider，确认启动页收起后 Web UI 的 composer 与历史记录已就绪；跑
   `scripts/android-e2e.sh` 4/4；
5. 覆盖安装带新 rootfs 的 APK（或临时把 `files/rootfs.version` 改旧），冷启动应出现
   `Updating bundled rootfs…`，完成后 `.dsh` 会话仍存在；
6. 同一版本再次冷启动不应重复解包。

2026-11-10 实测：内置 fixed22-nativepdf（82,000 entries）首次解包、升级迁移、
二次启动跳过、Host/bridge/E2E 4/4 均通过。

## 外部 xz rootfs 回归（备用路径）

1. 用 `DSHD_EMBED_ROOTFS=none` 构建小 APK，或使用 debuggable 包的 `rootfsArchive`；
2. 将 `fixed22-nativepdf.tar.xz` 放入应用可读目录；
3. 冷启动并触发 rootfs 导入；
4. 观察 `Importing rootfs…` 走到 `Finalizing rootfs…`；
5. 校验 `rootfs` 内 Node、dsh `bin.js`、`dsh-android-bridge` 插件存在；
6. 启动 Host，`/v1/health` 返回 `{"ok":true}`；
7. 跑 `scripts/android-e2e.sh`，应 4/4 通过。

本轮未重复外部导入；0.3.1 的 fixed21 外部导入路径已通过，本次仅改了内置包内容和 PDF 客户端，导入器未变。
