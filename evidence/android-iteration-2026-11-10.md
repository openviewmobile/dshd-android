# Android 0.2.0 增量验收（2026-11-10）

范围：包名确认、自适应图标、release 签名、手机屏幕适配、外部存储工作区。
设备：LGE-AN10 / Android 12 / arm64-v8a / rooted；测试时用 `wm size`/`wm density`
覆盖视口，结束后恢复 1148×2492 @490dpi（375 CSS px）。

## 包名与版本

- `applicationId` / `namespace`：`com.dshd.android`（保持稳定，见 `docs/packaging.md`）。
- `versionCode 2`，`versionName 0.2.0`，显示名 `DSHD`。
- `aapt dump badging` 确认 package/version/icon；Manifest 使用
  `@mipmap/ic_launcher` 与 `@mipmap/ic_launcher_round`。

## 图标

- 设计稿 `docs/android-icon.svg`，预览 `evidence/android-icon-512.png`。
- 自适应图标 foreground/background/monochrome 均为矢量，Android 8+ 可用；
  不再使用 `sym_def_app_icon`。

## 签名

- Alias `dshd-release`，RSA 4096，证书 SHA-256
  `1C:98:8D:F8:9D:62:51:A1:B6:C8:AA:1D:74:C3:5F:4C:39:4A:0E:C0:CF:52:A9:4A:A9:B6:AD:F3:EF:4D:36:EC`。
- `apksigner verify --verbose`：`Verifies`，APK Signature Scheme v3（minSdk 28）。
- 覆盖安装成功（versionCode 1 → 2），数据和 rootfs 保留。
- keystore/密码未进入仓库。

## 屏幕适配

| 视口 | 结果 |
|---|---|
| 320×526 | 设置（通用/模型/内置插件/Agent 预设）、插件页、自动化、新会话无横向滚动 |
| 360×686 | 主界面、设置、插件正常 |
| 375×729 | 正常 |
| 411×820 | 正常 |
| 480×800 | 正常 |
| 800×276 横屏 | 设置弹窗可用，原生栏压缩为一行 |

修复：

- 320px 设置导航收窄到 104px，模型“添加模型提供商”按钮取消 180px 最小宽度。
- ≤380px 时 Agent/插件卡片单列。
- 原生顶部两行按钮合并为一行。
- 隐藏 Android 上无效的“打开配置文件”按钮（按文案，不依赖 CSS Module 哈希）。

证据：

- `evidence/android-responsive-settings-320.png`
- `evidence/android-responsive-agent-320.png`
- `evidence/android-responsive-plugins-320.png`
- `evidence/android-responsive-landscape.png`
- `evidence/android-storage-dialog-320.png`

## 外部存储工作区

- 实现 `StorageConfig`：内部 / 外部应用目录；外部优先真实可移除 SD 卡
  (`getExternalFilesDirs()[1]`)，否则 `getExternalFilesDir(null)`。
- Host 以 `--patch runtime/workspace-storage.yml` 注入
  `workspace-controller.documentsDirectory`。
- 首次默认工作区实测：清空注册表后启动，外部目录生成
  `.../documents/deepseek-harness/default-workspace`；测试后恢复原注册表。
- 约束：现有 Workspace 不自动迁移；rootfs/Node 必须留在内部存储（外置存储 noexec）；
  当前测试机无真实可移除 SD 卡，真实 SD 卡仍需带卡设备复测。

## 回归

```text
scripts/android-e2e.sh
PASS webview shell and composer
PASS agent round trip — E2E-OK
PASS attachment upload route — HTTP 200
PASS file chooser input present
4/4 checks passed
```

## 产物

- `dist/dshd-android-release.apk`
  SHA-256 `81f48297a632d1bfe8a15f43640ebf8d07a0e229ad2b7d335130cf6dbefff1af`
- `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed18-xz.tar.xz`
  SHA-256 `7764ddbce6478ba16ecd6cf839e80ea81758703eaefe738e4dadb672ebbb8644`
- `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed18.tar.gz`
  SHA-256 `c7c60899d8ba2ef24e3e47d61a1665a0bb46ab1c37f92893f4338e2bce0ca86f`

fixed17 由 fixed15 解包后注入更新后的 `mobile.css`/`mobile-fix.js` 和
`@deepseek-ai/dsh-android-bridge` 插件再打包；已检查包含 Node、ld-linux、补丁后的
前端和插件，且未包含 sessions/storages/凭据。

## 权限与原生桥增量

- 新增权限中心与 7 类运行时权限；详见 `docs/permissions.md`。
- 新增 loopback token 原生桥和 dsh 插件 `@deepseek-ai/dsh-android-bridge`，
  agent 可调用 `android_*` 工具。
- 修复 Android 12 `BackgroundServiceStartNotAllowedException`：Host 改用
  `startForegroundService()` 并在 Activity 真正 resumed 后启动。
- 修复 Android 12 蓝牙扫描仍需 `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` 的问题。
- 真机验证：权限中心、WebView 麦克风/相机/定位、直接拨号、BLE 扫描、通知、
  agent 工具调用；证据 `evidence/android-permissions-2026-11-10.txt`。

## 高危权限二次确认 + 权限审计

- 电话、通讯录、定位、蓝牙在申请前增加应用内 `Confirm sensitive permission`；
  `Cancel` 不触发 Android 权限框，`Continue` 才继续。
- 麦克风/相机仍由 WebView 的 `getUserMedia` 直接触发系统权限框，避免双重弹窗。
- 权限审计：已实现能力无缺失声明；`BLUETOOTH_SCAN` 增加
  `neverForLocation`；Android 11 及以下蓝牙权限要求定位一并授权。
- 证据：`evidence/ui-permissions-confirm-375.png`、
  `evidence/android-permissions-2026-11-10.txt`。

## rootfs 体积优化

fixed17 的 506 MB 中包含测试期缓存：`home/.npm/_cacache` 253 MB、
`var/cache/apt` 114 MB、`var/lib/apt/lists` 等。`scripts/pack-rootfs.sh` 已加入排除规则，
重新打包为 fixed18：

- gzip：276 MB（fixed17 506 MB，减少 45%）；
- xz：**150 MB**（`xz -6`，推荐）；
- 解包：约 1.05 GiB（fixed17 约 1.7 GiB）；
- SHA-256：gzip `c7c608...ca86f`；xz `7764ddbc...bb8644`；
- `gzip -t`/`xz -t` 通过，Node/dsh/bin.js 和 `dsh-android-bridge` 插件均在包内。

`RootfsInstaller` 增加纯 Java `org.tukaani:xz` 解码，支持 `.tar.xz`；APK 从 714 KB
增至 829 KB。真机验证：xz 导入 82,000 entries，Host 启动，bridge health 正常，
`scripts/android-e2e.sh` 4/4 通过。测试中发现旧 rootfs 递归删除失败会导致 rename
失败，已改为先把旧目录改名为 `.old` 再替换，导入后清理。

额外实验性裁剪（xz）：仅去掉 `libreoffice-kit-wasm` 为 120 MB；再去掉语音和开发文件
为 98 MB。后两者未作为默认发布物。

## Node strip + 非 arm64 预编译裁剪

在 fixed18-xz 的基础上：

- `opt/node24/bin/node` 使用 `strip --strip-unneeded`，118 MB → 99 MB；
- 删除 node-pty 的 `win32-x64`、`win32-arm64`、`darwin-x64`、`darwin-arm64`、
  `linux-x64` prebuilds 和 `third_party/conpty`（Windows ConPTY）；
- 删除 pnpm 的 darwin/win32 reflink 和 x64 fastlist；
- 保留 `node-pty/prebuilds/linux-arm64`、pnpm arm64 路径。

打包结果：

- `fixed20-strip.tar.xz`：**143 MB**，SHA-256 `5dc9f4866021b9b6296035fea5acaee7a72ab22167985f884dbbe00261d8c034`；
- 解包约 1.00 GiB；`xz -t` 通过，Node 为 stripped，插件仍在包内；
- 真机替换 stripped Node 后 Host 启动正常，`scripts/android-e2e.sh` 4/4 通过；
- arm64 glibc 容器内验证 `node-pty` 能 spawn `/bin/sh`，`pnpm --version` 正常。

进一步 clean 变体（删除 node_modules 的 `*.map`、`*.d.ts`、test/tests/__tests__
和文档）：`fixed20-clean.tar.xz` **132 MB**，SHA-256
`8926522ab07ef4bc392f44d62400f1a24399a454f37cc597ea8ed03e1733def2`。
该变体只做过 dsh core 烟雾测试，尚未做完整真机 E2E，作为实验包。

基础包审计结论：

- `opt/node24/include` 64 MB、`usr/include` 12 MB、`*.a` 约 7 MB 仅编译需要；
  删除只再省约 3 MB 压缩体积，不建议默认删；
- source map 48 MB、`.d.ts` 46 MB、tests 约 95 MB、文档约 10 MB，合计约
  200 MB 解包体积，clean 后压缩节省约 11 MB；
- git/perl/openssh/curl/apt/npm/pnpm/Office/语音均保留，避免删 agent/产品功能。

### fixed20-clean 真机验证

将 `fixed20-clean.tar.xz` 通过 xz 导入设备（132 MB，82k 级别 entries），恢复
`.dsh` 和 provider key 后：

- `rootfs` 约 0.9G，Node/dsh/bridge 插件存在；
- Host 启动，`/v1/health` 返回 `{"ok":true}`；
- BLE 扫描发现 4 个设备，包含 Mijia Scale；
- agent 终端工具执行 `echo TERMINAL-OK` 并返回 `TERMINAL-OK`；
- `scripts/android-e2e.sh` 4/4 通过；
- 测试后重新安装非 debuggable release，APK SHA-256 仍为
  `81f48297a632d1bfe8a15f43640ebf8d07a0e229ad2b7d335130cf6dbefff1af`。

结论：clean 没有运行时功能损失；只移除 source map、`.d.ts`、包内测试和文档，
影响调试/类型检查/包内自测，不影响用户功能。

### 最终采用 fixed20-strip

clean 验证通过后，按决策切回 fixed20-strip：

- 通过 xz 导入 143 MB 包，恢复 `.dsh` 和 provider key；
- Host 启动，`/v1/health` 正常；
- BLE 扫描返回 5 个设备，包含 Mijia Scale；
- agent 终端工具执行 `echo STRIP-OK` 返回 `STRIP-OK`；
- `scripts/android-e2e.sh` 4/4 通过；
- 重装非 debuggable release，APK SHA-256 仍为
  `81f48297a632d1bfe8a15f43640ebf8d07a0e229ad2b7d335130cf6dbefff1af`。

当前设备 rootfs 为 fixed20-strip；clean 保留为可选低带宽包。

## 0.3.0 内置 rootfs 验证

构建默认把 fixed20-strip 作为 `assets/rootfs.tar.xz`：

- APK：144 MB，SHA-256 `33a96a0b94cf620602f2c9e07340caaa777f49b9a1d62001182fb6cc8e64d96b`；
- 内置资产 SHA-256 与 fixed20-strip 一致：
  `5dc9f4866021b9b6296035fea5acaee7a72ab22167985f884dbbe00261d8c034`；
- `DSHD_EMBED_ROOTFS=none` 的 lite APK：834 KB，
  SHA-256 `ea9de3a7e4ccfbb924bf46e9e35beb0092c6d4a8bda405515f37f68d5fced378`。

真机验证：

1. 从已有 fixed20-strip rootfs 覆盖安装内置 APK（versionCode 3/0.3.0）；
   `files/rootfs.version` 不存在，App 自动 `Updating bundled rootfs…`；
2. 升级后 `.dsh/sessions/--home-Documents-deepseek-harness-default-workspace--`
   仍在，Provider 配置仍在，E2E 4/4；
3. 强制同一版本二次启动，不再出现 `Importing rootfs…`，直接启动 Host；
4. 删除 rootfs/rootfs.version 模拟首次安装，冷启动自动 `Importing rootfs… 24500 entries`，
   完成后 rootfs.version 为内置 SHA-256，Provider 首启正常；
5. 把 rootfs.version 改成 `old` 再次冷启动，自动迁移并恢复用户数据，E2E 4/4；
6. `scripts/check-release-readiness.sh` 对内置 APK 通过，不再要求 rootfs URL。

升级迁移保留 `home/.dsh`、`Documents`、`workspace`、`.ssh`、`.gnupg`、`.npmrc`；
不保留旧 `android-bridge.json`，由新 Host 重新生成。

### rootfs 替换健壮性修复

迁移回归发现两个 Android 私有目录问题：

- 固定名 `rootfs.old` 在上一次清理后可能留下空目录骨架，导致下一次
  `rootfs.renameTo(rootfs.old)` 失败；
- 删除 dangling symlink 时，`File.exists()` 会跟随目标返回 false，导致
  symlink 和空目录残留。

修复：

- 临时解包目录和旧 rootfs 都使用时间戳唯一名；
- 旧 rootfs rename 失败时直接报错并保留旧树，不再原地递归删除；
- `deleteTree` 先 `lstat` 识别 symlink（含 dangling），删除使用 `Os.remove`，
  失败再回退 `File.delete`。

修复后重新触发迁移：`Importing rootfs… 24500 entries` → 新版本号写入、
`.dsh` 会话保留、旧目录被清理、E2E 4/4。
