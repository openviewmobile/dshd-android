# DSHD for Android（DeepSeek Harness Desktop）

[![Android APK](https://github.com/openviewmobile/dshd-android/actions/workflows/android.yml/badge.svg)](https://github.com/openviewmobile/dshd-android/actions/workflows/android.yml)

[English](README.md) · 中文

![DSHD](docs/assets/social-preview.png)

DSHD 是 **DeepSeek Harness Desktop** 的缩写。本目录是一个可单独复制、构建和测试的
Android 项目，不依赖仓库里的其他项目：包含原生 APK、WebView 前端资源、rootfs
打包/修补脚本、真机 E2E 和验收证据。

- 运行时：Cortex/Ubuntu glibc 用户态 + 官方 Linux Node 24 + `dsh` Host；
- 前端：`dsh web` 在原生 WebView 中加载（Chromium 92 兼容层）；
- 启动体验：原生深色启动页显示品牌标识、分阶段状态和 rootfs 解包进度，Host Web UI
  真正可交互后淡出，不再暴露白屏或 `HARNESS` 加载页；
- 首次启动：从 APK 内置的 fixed22-nativepdf rootfs 自动解包，原生表单配置 Provider；
  外部导入/下载保留为备用路径；
- 测试：`scripts/android-e2e.sh`（需要已安装 APK 和 adb 设备）。

## 目录

```
app/        原生 Android 工程（Java + WebView）
rootfs/     移动端 CSS/前端注入
scripts/    构建、rootfs 打包/修补、E2E、发布检查
evidence/   真机验收截图与报告
dist/       本地构建产物（gitignore）
```

## 快速开始

```bash
# debug（需要 Android SDK + Gradle）
scripts/build-apk.sh
adb install -r app/app/build/outputs/apk/debug/app-debug.apk

# release（默认内置 rootfs，需要发布 keystore）
DSHD_KEYSTORE=/secure/release.jks DSHD_KEYSTORE_PASSWORD=... \
  DSHD_KEY_ALIAS=... DSHD_KEY_PASSWORD=... scripts/build-release-apk.sh

# 可选：构建不带 rootfs 的小 APK（约 856 KB）
DSHD_EMBED_ROOTFS=none DSHD_KEYSTORE=/secure/release.jks \
  DSHD_KEYSTORE_PASSWORD=... DSHD_KEY_ALIAS=... DSHD_KEY_PASSWORD=... \
  scripts/build-release-apk.sh

# 真机回归（应用已安装且 Provider 已配置）
scripts/android-e2e.sh
```

包名/图标/签名/屏幕适配/存储见 [docs/packaging.md](docs/packaging.md)；完整设计、测试与发布文档见 [docs/](docs/README.md)；验收边界、修复记录和已知限制见
[evidence/android-acceptance-0.3.2.md](evidence/android-acceptance-0.3.2.md)。

0.2.0 增量验收（图标、签名、320px 适配、SD 卡工作区）：
evidence/android-iteration-2026-11-10.md。

0.3.1 起 rootfs 默认内置进 APK；首次安装自动解包，APK 升级按内置包 SHA-256
自动迁移并保留 `.dsh`、Documents、workspace、SSH/GPG 和 `.npmrc`。

首次使用演示的源码与重建方法见 [demo/README.md](demo/README.md)：中英文完全分开，运行时生成 GIF/MP3/MP4，不把大体积媒体放进 Git 仓库。

YouTube 16:9 宣传片的标题/简介/标签/章节/缩略图见 [docs/youtube-promo.md](docs/youtube-promo.md)；成片由 `demo/youtube/render.mjs` 在本地生成，不把视频大文件放进仓库。启动耗时实测与优化边界见
[docs/startup-performance.md](docs/startup-performance.md)。

目标：在 Android 真机上跑本地 dsh（Node 24 + dsh Host + Web UI），并封装成可安装 APK。

## 2026-11-10 真机全量验收（fixed19）

从卸载后的全新安装开始：下载并校验 506 MB rootfs、解包、Provider 首启配置、真实模型回复，
随后覆盖设置子页、会话生命周期、附件、旋转/键盘、离线失败、进程死亡恢复、Agent 工具/
agent-team、worktree、auto-review、语音和 whale-pet。结论与证据：
evidence/android-acceptance-2026-11-10.md。

当前 0.3.2 **本地验收构建**（未提交到仓库；包名/图标/签名/屏幕适配/SD 卡见
[docs/packaging.md](docs/packaging.md)）：

- `dist/dshd-android-release.apk`（内置 fixed22-nativepdf，144 MB）
  SHA-256 `73b7c945590a5dd37475256744046548d61ec403f8aba60c9fa2ea3697be0db5`
- `dist/dshd-android-release-lite.apk`（无 rootfs，856 KB）
  SHA-256 `4e4c4e737d91d0dd27ad6714a2e0a61963ab7b512033f43420edc8573fb3377f`
- `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed22-nativepdf.tar.xz`（143 MB）
  SHA-256 `4fa3b4fd9bb4bf1877c5738633f3f46e6ba2c6910a1c3384fca08f4ed8caba46`

默认 APK 不再需要 rootfs 下载地址；CI/调试可用 `DSHD_EMBED_ROOTFS=none` 构建约 1 MB 的 lite 包。

GitHub Actions 在 push/PR 时用仓库源码构建 **lite debug APK** 并上传为 workflow artifact（含 SHA-256），不会提交或上传本地 `dist/` APK。完整内置 rootfs 的发布 APK 仍需另行托管 rootfs 并配置签名 secrets，见 [docs/release.md](docs/release.md)。
fixed20-strip / fixed20-clean 是历史回溯包，不含本次原生 PDF 桥补丁；0.3.2 请使用 `fixed22-nativepdf`。体积构成和可删项见 [docs/rootfs-size.md](docs/rootfs-size.md)。

重复测试：

```bash
scripts/android-e2e.sh
# PASS webview shell and composer
# PASS agent round trip — E2E-OK
# PASS attachment upload route — HTTP 200
# PASS file chooser input present
```

0.3.2 修复了 Chromium 92 下文本/嵌入字体 PDF 的空白页：WebView 把 PDF 页交给 Android `PdfRenderer` 渲染为图片，同时保留 PDF.js 的页码/缩放控件；320 CSS px 输入框占位文字也改为完整可见的短提示。0.3.1 的终端、文件地址、Provider 回填与文档路径换行修复继续保留。

启动体验也在 0.3.2 重做：深色品牌启动页覆盖原生 tab 与 WebView，按“解包 → 启动本地引擎 →
打开工作区”显示分阶段文案和进度条；内置 rootfs 以压缩字节数计算百分比，Host UI 的
composer 与历史记录就绪后才淡出。Android 12 的系统启动背景/图标同步改为深色，避免首帧白闪。
启动页证据：[05-startup-overlay.png](evidence/acceptance-0.3.2/05-startup-overlay.png)。

本轮修复了 WebView `onShowFileChooser`（附件选择此前完全无反应）、Host 的 `DSH_HOME`、
附件存储向上 fsync 到 `/data/user/0` 导致的 EACCES，并在手机端隐藏桌面专用的
“打开配置文件”入口。内置 rootfs 后，公开 Release 不再被 HTTP rootfs 地址阻塞。

## 0.3.2 已知限制

- PDF 页面由原生桥渲染为图片，因此 PDF 内文字不可选中/复制；大文件首次打开需要写入缓存。
- Host 界面尚未做完整的应用内中英文切换；0.3.2 的“中英文分开”指演示视频，不是 Host UI。
- 320 CSS px 下短占位提示为“Ask or run a task” / “输入消息或运行任务”，不再截断。

## 开源与文档

- 许可证：[LICENSE](LICENSE)（Apache-2.0）；第三方组件见 [NOTICE](NOTICE) 和
  [docs/third-party.md](docs/third-party.md)。
- 贡献指南：[CONTRIBUTING.md](CONTRIBUTING.md)；安全策略：[SECURITY.md](SECURITY.md)；
  变更记录：[CHANGELOG.md](CHANGELOG.md)。
- 文档索引：[docs/README.md](docs/README.md)。
- 0.3.2 验收报告：[evidence/android-acceptance-0.3.2.md](evidence/android-acceptance-0.3.2.md)。

## 实机结果（2026-11-08）

**已跑通：Cortex（Ubuntu 24.04 glibc 用户态）+ 官方 Linux Node 24 + dsh 0.2.2-rc.1 + 原生 APK WebView。**
APK 启动 Host、解析 token URL、加载 dsh UI 成功；截图：evidence/android-cortex-ui.png。

- 设备：LGE-AN10 / Android 12 / arm64-v8a（rooted，adb root）。
- 运行时：Cortex v1.26.50（Apache-2.0，targetSdk 28），Ubuntu 24.04、glibc 2.39、无 PRoot/ptrace。
- Node：官方 `linux-arm64` v24.22.2；dsh 0.2.2-rc.1（npmmirror）。
- APK：`com.dshd.android`，Java + 原生 WebView，约 756 KB（含转译前端，不含 rootfs）；`targetSdk 28`；`dist/dshd-android-debug.apk` SHA-256 `bcb84d5b81c6db209ac7992ee542523393d5d4ff440706487c65d0b1fe9334c6`。
- Host：`ld.so --library-path … node …/dsh/lib/bin.js web --no-open --port 3080`；token 由 stdout 解析。
- 首启：rootfs `.tar.gz` 可通过下载 URL（带 SHA-256 校验）或 SAF 本地导入；进度/失败/重试都在原生界面；前台服务保活。

Cortex 的关键机制：用 `libcortex-hook.so`（`LD_PRELOAD`）把 `/usr`、`/etc` 等路径映射到 app 私有 rootfs；ELF 程序通过 rootfs 的 `ld.so` 启动，不拦截 syscall，因此不会遇到 Android `untrusted_app` seccomp 下 PRoot 的 `ENOSYS`。

### 为什么放弃 PRoot 作为 App 运行时

PRoot Debian + 官方 Node 24 + dsh 在 Termux/`run-as` 下可跑通，但同一命令由普通 Android app 的 `ProcessBuilder` 启动时，PRoot 无法翻译 `process.cwd()` 和 `package.json` 读取，报：

```
ENOSYS: process.cwd failed with error function not implemented, uv_cwd
Cannot read package config .../safe-buffer/package.json: function not implemented.
```

已尝试 `-w /`、`PWD=/`、`UV_USE_IO_URING=0`、`/system/bin/sh -c`、`PROOT_NO_SECCOMP=1`；`process.cwd()` 可通过 dsh prelude 绕过，文件读取仍失败。失败截图：evidence/android-app-runtime.png。结论：PRoot 仅保留为验证路径，产品用 Cortex。

### Cortex + dsh 手工验证（真机）

```bash
# 1. 安装 Cortex APK 并首次打开，让它 bootstrap
adb install -r Cortex-arm64-v8a-signed.apk

# 2. Cortex 内启动 SSH（终端里执行一次）
cortex-sshd
cortex-sshd key "$(cat ~/.ssh/id_ed25519.pub)"
# 3. 宿主机
adb forward tcp:8022 tcp:8022
ssh -p 8022 root@127.0.0.1

# 4. Cortex 内安装 Node/dsh
export PATH=/opt/node24/bin:$PATH
curl -fL -o /tmp/node.tar.xz https://nodejs.org/dist/v24.22.2/node-v24.22.2-linux-arm64.tar.xz
mkdir -p /opt/node24 && tar -xJf /tmp/node.tar.xz -C /opt/node24 --strip-components=1
npm config set registry https://registry.npmmirror.com
npm i -g @deepseek-ai/dsh@0.2.2-rc.1 \
  --allow-scripts=@deepseek-ai/dsh-subprocess-local,koffi,node-pty,@google/genai,protobufjs

# 5. 启动
cd /root
dsh web --no-open --port 3084
```

浏览器打开打印的 token URL；UI 正常。Cortex 的 Node/dsh 在 `/opt/node24`，rootfs 中同样可用。

### 当前 APK 的 WebView 兼容处理

设备系统 WebView 是 Huawei `12.0.0.324`（Chromium 92），而 dsh 前端按现代浏览器构建，直接加载会白屏：

- `Promise.withResolvers`、`Object.hasOwn`、`AbortSignal.any`、`AbortSignal.prototype.throwIfAborted`、`AbortSignal.timeout`、`Array.prototype.toSorted/toReversed/toSpliced/with` 缺失 → index.html 注入 polyfill；
- 前端 bundle 含 class static block（Chrome 94+）→ 用 Babel `preset-env` targets `chrome92` 转译 `dsh-web-frontend/dist/assets/*.js`；
- 插件 `client.js` 也依赖 `AbortSignal.any`；polyfill 在入口页面生效。

复现步骤（宿主机）：

```bash
mkdir -p /tmp/babel-tool && cd /tmp/babel-tool
npm i @babel/core @babel/preset-env @babel/plugin-transform-class-static-block
# 将 rootfs 的 dsh-web-frontend/dist/assets 拉到本地，逐个转译：
# node transform.cjs <in.js> <out.js>
# 再把 index.html 的 polyfill 脚本放在 module script 之前
adb push assets/. <rootfs>/.../dsh-web-frontend/dist/assets/
```

长期方案：升级 WebView/Chrome、改用 GeckoView，或在构建 dsh 前端时直接输出 legacy target；不应长期在设备上跑 Babel。

## 原生 APK（当前状态）

代码：[app/](app/)；构建/安装脚本：

- [scripts/build-apk.sh](scripts/build-apk.sh)
- [scripts/install-cortex-rootfs.sh](scripts/install-cortex-rootfs.sh)（原型：从 Cortex 复制 rootfs，并自动应用前端/默认工作区修复；最终产品不能依赖 Cortex app）
- [scripts/install-test.sh](scripts/install-test.sh)

APK 逻辑：

1. 释放 `assets/libcortex-hook.so` 到 `files/runtime`；
2. 检查 `files/rootfs` 里的 `lib/ld-linux-aarch64.so.1`、`opt/node24/bin/node`、dsh `bin.js`；
3. 用 `ProcessBuilder` 直接启动 loader，设置 `CORTEX_ROOT`/`LD_LIBRARY_PATH`/`LD_PRELOAD`/`GLIBC_TUNABLES`；
4. 读取 stdout 的 `http://127.0.0.1:3080/?token=...`，交给 WebView。

rootfs 解包后约 1.00 GiB（fixed22-nativepdf；Cortex bootstrap + `/opt/node24` 的 Node/dsh），压缩包 **143 MB**，内置进 APK 后安装包 144 MB。fixed17 的 506 MB 中有 253 MB npm cache 和 177 MB apt cache/lists；fixed18 剔除缓存后 gzip 为 276 MB、xz 为 150 MB；fixed22 再裁掉 Node 调试符号和非 arm64 预编译文件。

### 已支持 / 未支持

- ✅ 本地 Host 启动、token 登录、dsh UI 加载
- ✅ 无 PRoot/ptrace；普通 app 进程可运行
- ✅ 已卸载 Cortex 后仍可启动（rootfs 已复制到 dshd 私有目录）
- ✅ Node 24 + koffi/node-pty/sharp 可加载（Cortex rootfs）
- ✅ **rootfs 首启导入**：SAF 选择 `.tar.gz`，纯 Java tar/gzip 解包，保留目录/文件/符号链接/硬链接/权限；导入后自动启动 Host
- ✅ **rootfs 下载 + SHA-256**：`HttpURLConnection` 下载、进度、SHA-256 校验；URL/hash 由 `res/values/strings.xml` 的 `rootfs_download_url`/`rootfs_sha256` 配置（当前 release 占位为空）
- ✅ **Host 由前台服务托管**：`HostKeepAliveService` 持有 `ProcessBuilder`/token/端口/重启逻辑，Activity 只订阅状态并加载 WebView；通知带 Stop 动作；HOME/重开 Activity 不重启 Host
- ✅ **端口冲突 + 崩溃退避**：3080 被占用时自动选 3081–3099；Host 异常退出按 2/4/8/16/30s 退避重试；验证见 android-host-recovery.txt
- ✅ **真机复测**：1.7 GB rootfs → 574 MB tar.gz，约 18,000 entries，下载→校验→解包→启动 Host→完整 UI 全链路通过；证据见下方截图
- ✅ **Provider 首启配置**：原生表单输入 base URL / API key / model，写入 app 私有 rootfs 的 `cordis.patch.yml` + `.credentials.yaml`；通过后自动启动 Host
- ✅ **rootfs 断点续传**：HTTP Range + `.part` + SHA-256 校验，已在真机用 2 MiB Range 服务中断/续传验证
- ✅ **Keystore 密钥存储**：API Key 用 Android Keystore AES-GCM 加密到 `provider-key.enc`，Host 启动时解密并通过环境变量注入；`.credentials.yaml` 不再保存明文 key
- ✅ **权限中心 + agent 原生桥**：麦克风/相机/前台定位/通知/蓝牙/直接拨号/读联系人；电话/通讯录/定位/蓝牙申请前有应用内二次确认；agent 通过 loopback token 桥调用 `android_*` 工具。详见 [docs/permissions.md](docs/permissions.md)
- ✅ **真实模型对话**：Keystore 迁移后，真机 UI 用 `deepseek-v4.1-flash` 收到真实补全；截图 android-keystore-chat.png
- ❌ 正式 rootfs 托管/发布 URL（机制已通，当前只在本机 HTTP 测试；release 需填入真实 URL/hash）
- ❌ SAF 工作区选择（默认 workspace 已通过 `xdg-user-dir` shim 自动创建；切换目录仍无 native picker）
- ❌ 会话持久化/升级/签名（debug 版）
- ⚠️ 依赖 Chromium 92 polyfill/转译；`assets/dsh-frontend-chrome92.zip` 是预转译前端，升级 dsh 后需重新生成
- ✅ 设置写入/插件 reload：root Include 修复后，Provider 保存与插件配置不再返回 `settings/rejected`

## 路线与下一步

| 路线 | 状态 | 说明 |
|---|---|---|
| Termux | ✅ 验证过 | 仅测试基线，不随产品分发 |
| PRoot + APK | ❌ 不可用 | app seccomp 下 syscall 翻译失败 |
| **预置 rootfs + APK 导入** | ✅ 已跑通 | 当前路线；APK 运行时不再依赖 Cortex app，首启导入 `.tar.gz` |
| rootfs 正式托管 | ⏳ 下一步 | 下载/校验机制已通过本机测试；还差正式 URL、对象存储、断点续传 |
| Provider 首启配置 | ✅ 已完成 | 原生表单 + Android Keystore 加密 |

下一轮优先级：

1. **rootfs 正式发布**：把固定下载 URL 和 SHA-256 填进资源；下载器已支持断点续传。
2. **端口/崩溃恢复**：已支持端口扫描与退避重启；仍需处理连续失败 5 次后的用户提示与手动恢复入口。
3. **SAF/工作区**：native directory picker，或 Host 侧默认 workspace。
4. **WebView 方案**：评估 GeckoView/升级系统 WebView，去掉 Babel/polyfill 维护。
5. 工具矩阵：bash/rg/pnpm/Python/Office 等在 Cortex rootfs 的可用性。
6. 签名 release APK + 升级策略；继续只走 GitHub APK/MDM，不上 Play（targetSdk 28）。

## 风险

- rootfs 1.7 GB，首启/存储/下载体验；
- Cortex 是 Apache-2.0 但非 SDK，fork 维护成本；
- Huawei WebView 92 与现代前端兼容性；
- 后台被杀/电量；Android 12 执行限制；
- 原生模块 ABI/预编译、16 KB page size；
- 设备差异（Huawei/Honor 尤其）。

## 真实模型对话（本机配置）

设备 UI 已经用本机 dsh 的 provider 跑通真实对话。设备不能解析 `ds923plus`，所以用局域网地址：

```bash
DSH_GATEWAY_BASE_URL=http://192.0.2.10:3000/v1 \
  scripts/configure-local-model.sh
```

脚本从 `~/.dsh/profiles/web/cordis.patch.yml` 复制 provider/model 列表，从
`~/.dsh/.credentials.yaml` 复制 credential ref，推入 app 私有 rootfs 后重启 App；不会把
密钥写入仓库。实测 `deepseek-v4.1-flash` 秒回，截图：
android-real-model-chat.png。

正式产品现在可用原生首启表单完成同样配置；本机脚本只用于自动化/回归测试。截图：
android-provider-setup.png。API Key 已迁入 Android
Keystore（AES-GCM），Host 启动时才解密注入环境变量；旧版明文 key 会在首次启动时自动迁移并删除。

## 首启导入实测（2026-11-08）

- 生成 payload：`scripts/make-rootfs-archive.sh`（从已 provision 的设备导出，1.7 GB → gzip 574 MB）；
- **下载链路实测**：本机 `python3 -m http.server` + `adb reverse tcp:8765 tcp:8765`，debug intent 传入
  `rootfsUrl=http://127.0.0.1:8765/dsh-rootfs.tar.gz` 与 SHA-256
  `6480e2ad4b0212fe288d307de54f2cdc72cce7aac012694826666a7f72faba28`；APK 显示
  `Downloading rootfs… 232.0 / 573.2 MB`，校验/解包后自动启动 Host 并加载 UI。截图：
  下载进度。
- 本地 SAF 路径：把存档放到设备 app 私有目录，用 debug intent 启动导入：

```bash
adb shell am start -n com.dshd.android/.MainActivity \
  --es rootfsArchive /data/data/com.dshd.android/files/rootfs.tar.gz
```

- 导入完成后 UI 直接加载，Host 仍为 `ld.so + Node 24 + dsh`；前台服务托管验证见
  android-service-host.txt、UI；截图：
  导入界面、
  导入中、
  导入后完整 UI。
- 导入结束后已删除设备上的旧 rootfs 备份和 tar.gz，app 只保留 `files/rootfs`（约 1.7 GB）。

## 历史证据

- PRoot Debian UI：evidence/android-dsh-ui.png
- PRoot app 失败：evidence/android-app-runtime.png
- Cortex 浏览器 UI（手工验证）：`/tmp/cortex-ui.png`（未入库）

## Release signing

```bash
DSHD_KEYSTORE=/secure/dshd-release.jks \
DSHD_KEYSTORE_PASSWORD=... DSHD_KEY_ALIAS=... DSHD_KEY_PASSWORD=... \
scripts/build-release-apk.sh
```

`targetSdk 28` is intentional (Cortex rootfs); release lint disables only
`ExpiredTargetSdkVersion`. Do not commit the keystore.

Rootfs hosting URL/SHA-256 can be injected at build time instead of editing `strings.xml`:

```bash
DSHD_ROOTFS_URL=https://example.com/dshd-rootfs.tar.gz \
DSHD_ROOTFS_SHA256=<sha256> ... scripts/build-apk.sh
```

`strings.xml` remains the fallback for lower environments.

## 发布就绪门禁

```bash
DSHD_KEYSTORE=/path/release.jks DSHD_ROOTFS_URL=https://... DSHD_ROOTFS_SHA256=<64hex> \
  scripts/check-release-readiness.sh
```

脚本检查 APK 签名、classes.dex 是否残留 `127.0.0.1`、keystore 是否为测试 key、
rootfs URL 是否 HTTPS、SHA-256 格式。当前测试包的失败项见
android-release-readiness.txt。

## 项目本地 release key

项目 release key 位于仓库外（`~/.dshd/android-release.jks`，密码
`~/.dshd/android-release.password`，均 chmod 600）。用它构建的干净包
`dist/dshd-android-release-project.apk` 已通过签名校验，但还没有正式
rootfs URL/SHA，readiness gate 故意失败。详见
android-release-project-key.txt。

## Project-key LAN 端到端

卸载测试 key 包后，用项目 key + Mac LAN rootfs URL 的 release 包完成从零下载、
Provider 配置和真实对话，回复 “project key release”。见
android-project-lan-e2e.txt。
Readiness 只剩 HTTPS 一项：android-release-readiness-lan.txt。

## 逐界面/插件验收（2026-11-09）

已用真机逐屏截图，清单见 ui-screens.txt。

发现并修复：

- WebView Chromium 92 缺 `URL.canParse` / `URL.parse`：app 资源和 rootfs 的
  `dsh-index.html` 都补了 polyfill；rootfs 必须同步，否则仍白屏。
- macOS `com.apple.provenance` 被 tar 存成 AppleDouble，Android 解压出
  `locale/._de.json`，插件元数据校验失败。host 侧打包改用
  `pack-rootfs.sh`（`COPYFILE_DISABLE=1 tar --no-xattrs`）。
- Host 的 PATH 没有 `/opt/node24/bin`，rootfs 也没有 pnpm，插件安装报
  `spawn pnpm ENOENT`。已内置 pnpm 并把 PATH 前移。

已修复：

- 插件安装/启用/停用报
  `dsh: profile reload requires the root Include entry`。根因是
  `mountRootInclude` 用 `ctx.get("loader")` 取 loader，Android web profile
  上该服务查询可能为 undefined，函数提前 return，root Include 没有登记到
  `bootstrapIncludes`；HMR reload 随后必然找不到。
  改用 `ctx.loader` 并在 reload 时回退到 loader entries 中查找 include。
  修复后安装、启用、停用、卸载都不再需要重启，组件状态实时变为运行中/已关闭。
  复现补丁：`scripts/patch-rootfs-appboot.sh`。

### 全量验收补充（2026-11-09 fixed5/fixed6）

- **Settings 移动端布局**：375px 下旧 panel 正文仅 139px、标签一字一行，
  内置插件卡片两列约 90px。新增 `rootfs/mobile.css`（<600px 单列、164px 导航、
  行换行），由 `scripts/patch-rootfs-mobile-css.sh` 注入 rootfs frontend，
  APK assets `dsh-index.html` 同步内联；fixed5 全新安装后已持久生效
  （`ui-73`、`ui-47`、`ui-51`）。
- **默认权限**：Android 没有可用 Linux sandbox，`workspace-write` 下每次
  shell 都失败。ProviderConfig 在 profile patch 覆盖 `permission.defaultPreset =
  danger-full-access`，Host 同时设置 `DSH_PERMISSION_MODE=danger-full-access`；
  fixed6 新建会话直接完全权限（`ui-61`、`ui-75`）。
- **rg**：rootfs 的 `@vscode/ripgrep-linux-arm64` 已软链到 `/opt/node24/bin/rg`；
  fixed8 起把 `/home`、`/tmp` 绝对参数映射到 `CORTEX_ROOT`，绝对路径 glob 也可用
  （输出为物理路径，属显示层小问题）。
- **Agent Teams**：把 `@deepseek-ai/dsh-experimental-agent-team-profile` 加入
  `profiles/web/package.json` 的 bundles 即可实时启用；真实双队友任务通过
  （`ui-76`~`ui-78`）。
- **生命周期/键盘**：force-stop 后会话历史/停止状态保留；竖屏键盘正常。
  横屏键盘曾把正文与输入框顶出视口，根因是 Activity 的 `configChanges` 包含
  `orientation|screenSize`，WebView 不随旋转重建；fixed10 移除这两项并保留
  `adjustResize` 后横屏完全可用（`ui-87`~`ui-91`）。通知 STOP、
  `adb install -r` 覆盖升级均验证；升级会中止进行中的任务并明确提示
  （`ui-67`~`ui-74`）。
- **工作区**：内置目录选择器可新建/选择目录，支持第二工作区、重命名、删除；
  会话支持置顶/取消置顶/归档（`ui-81`~`ui-84`、`ui-96`）。
- **实验性插件**：插件页 5 项逐个操作：智能体团队真实任务通过；语音输入
  4 组件启用（未下载模型/未录音）；自定义插件索引出现“发现”标签；自动授权
  审查启用；变更请求与工作树依赖 `gh` 登录，未验收（`ui-85`、`ui-86`、`ui-92`~`ui-95`）。
- **未覆盖/限制**：系统 SAF 文件附件未接入；Settings 默认权限对真正的新会话生效，
  但“新建会话”会复用空白会话并保留其已固定权限（blank reuse）；全新安装的首个
  空白会话也以 workspace-write 初始化，之后新建会话才是完全权限。

制品：`dist/dshd-android-release-project-lan-fixed10.apk`
（SHA-256 `5347ef60…`）与
`dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed8.tar.gz`
（SHA-256 `e8b9126a…`）。fixed6~fixed9 仅保留用于回归。
