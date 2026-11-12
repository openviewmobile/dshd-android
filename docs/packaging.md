# 包名、图标、签名与屏幕适配

## 包名（最终决定）

Android 应用 ID 保持 **`com.dshd.android`**，不在此版本重命名。

理由：

- 它已经是所有已安装版本、Keystore 授权、rootfs 路径和升级链的稳定身份；改名会导致无法覆盖升级，只能卸载重装；0.3.1 起 rootfs 内置，重装后首次启动会自动解包。
- 命名空间与 Java 包一致，避免构建期 `namespace`/`applicationId` 分叉。
- 没有使用 `com.deepseek.*`，避免让非官方构建看起来像 DeepSeek 官方应用。
- 显示名称是 **DSHD**（**DeepSeek Harness Desktop**），与包名解耦；未来品牌变化不需要改应用 ID。

版本：`versionCode 5` / `versionName 0.3.2`。发布后 `applicationId` 和签名证书都不得再变。

## 图标

设计稿：[android-icon.svg](android-icon.svg)，预览：../evidence/android-icon-512.png。

- 深蓝黑底 `#0B1220`；白色 `D` 表示 DeepSeek Harness；青色 `#38BDF8` 方块和 `>` 表示终端/Agent。
- Android 8+ 使用自适应图标：
  - `mipmap-anydpi-v26/ic_launcher.xml`
  - `mipmap-anydpi-v26/ic_launcher_round.xml`
  - `drawable/ic_launcher_foreground.xml`
  - Android 13 主题图标使用 `drawable/ic_launcher_monochrome.xml`
- `minSdk 28`，因此不需要旧版 PNG mipmap；资源全部是矢量，任意 DPI 清晰。
- Manifest 使用 `@mipmap/ic_launcher` 和 `@mipmap/ic_launcher_round`，不再使用系统默认图标。

## Release 签名

签名配置从环境变量读取，仓库不保存 keystore 或密码：

```bash
DSHD_KEYSTORE="$HOME/.dshd/android-release.jks" \
DSHD_KEYSTORE_PASSWORD=... \
DSHD_KEY_ALIAS=dshd-release \
DSHD_KEY_PASSWORD=... \
scripts/build-release-apk.sh
```

当前发布证书：

| 项 | 值 |
|---|---|
| Alias | `dshd-release` |
| 密钥 | RSA 4096 |
| 证书 DN | `CN=DSHD Project Release, O=DSHD, C=CN` |
| 有效期 | 2026-11-09 – 2054-03-27 |
| SHA-256 | `1C:98:8D:F8:9D:62:51:A1:B6:C8:AA:1D:74:C3:5F:4C:39:4A:0E:C0:CF:52:A9:4A:A9:B6:AD:F3:EF:4D:36:EC` |
| 签名方案 | APK Signature Scheme v3（Android 9+；`minSdk 28`） |

要求：

- **必须离线备份** `~/.dshd/android-release.jks` 和密码，且密码与 keystore 分开保存；仅 `~/.dshd/` 本机副本不算备份，丢失后无法发布可覆盖升级的 APK。
- 不提交 keystore、密码、`keystore.properties` 到 Git。
- 正式发布前运行 `scripts/check-release-readiness.sh`；内置 rootfs 后不再要求 rootfs 下载地址；外部托管仅作为可选更新路径。
- 如果未来必须轮换密钥，需要发布迁移说明和新包名，不能静默换签名。

## 内置 rootfs

0.3.2 默认把 `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed22-nativepdf.tar.xz`
作为 `assets/rootfs.tar.xz` 打进 release APK：

- APK 约 144 MB；rootfs 以 xz 原样存储，不再二次压缩；
- 首次安装启动时自动解包；不需要下载地址；
- APK 升级时按内置包 SHA-256 比较 `files/rootfs.version`，不同则迁移用户数据后替换；
- 构建时可用 `DSHD_EMBED_ROOTFS=/path/to/rootfs.tar.xz` 覆盖；
- `DSHD_EMBED_ROOTFS=none` 构建不带 rootfs 的约 856 KB 小 APK，走原来的导入/下载路径。

版本记录在 `files/rootfs.version`，不写进 rootfs；清除 App 数据会重新解包。

## 手机屏幕适配

已在真机用 `wm size`/`wm density` 覆盖测试：

| CSS 视口 | 设备设置 | 结果 |
|---|---|---|
| 320×526 | 640×1280 @320dpi | 设置、模型、Agent 预设、内置插件、插件页、自动化、新会话均无横向滚动 |
| 360×686 | 720×1600 @320dpi | 主界面、设置、插件正常 |
| 375×729 | 1148×2492 @490dpi | 原始验收尺寸 |
| 411×820 | 1080×2400 @420dpi | 正常 |
| 480×800 | 960×1600 @320dpi | 正常 |
| 800×276 横屏 | 1600×720 @320dpi | 设置弹窗可用；原生操作栏压缩为一行 |

本轮修复：

- 原生顶部合并为一行 `Provider | Storage | Permissions`，横屏少占约 28 CSS px。
- 320 px 下设置导航列从 132 px 收窄到 104 px。
- 模型页“添加模型提供商”按钮取消 `min-width:180px`，改为 100% 宽。
- Agent 预设和内置插件卡片在 ≤380 px 时单列显示。
- 通过 `mobile-fix.js` 按文案隐藏 Android 上无效的“打开配置文件”，不再依赖会变化的 CSS Module 类名。
- 证据：settings 320、Agent 320、插件 320、横屏。

## 外部 SD 卡上的工作区

### 结论

可以，但只把 **用户工作区/文档** 放到外部应用专属目录；**Linux rootfs、Node、dsh 程序、会话存储和密钥仍留在内部存储**。

原因：Android 的共享存储/外置卡挂载为 `noexec`，ELF loader 和 Node 不能从 SD 卡执行；rootfs 必须内部存储。工作区是普通数据文件，可以放外置卡。

### 实现

- 原生操作栏中间的 `Storage: Internal/External` 打开存储选择；右侧 `Permissions` 打开权限中心。
- 选择 External 后：
  - 无真实 SD 卡时使用 `getExternalFilesDir(null)/documents`（模拟外部存储）；
  - 有可移除 SD 卡时使用 `getExternalFilesDirs(null)[1]/documents`（应用专属目录，不需要 `MANAGE_EXTERNAL_STORAGE`）。
- Host 启动时生成 `runtime/workspace-storage.yml`，以 `--patch` 注入：
  ```yaml
  - id: "workspace-controller"
    config:
      documentsDirectory: '/storage/.../Android/data/com.dshd.android/files/documents'
  ```
  首次初始化默认工作区时，dsh 会落到 `<documentsDirectory>/deepseek-harness/default-workspace`。
- 选择后自动重启 Host 生效；不需要存储权限。
- 已在真机做首次默认工作区实测：清空 Workspace 注册表并删除外部测试目录后启动，
  成功生成
  `/storage/emulated/0/Android/data/com.dshd.android/files/documents/deepseek-harness/default-workspace`，
  随后恢复原会话注册表。

### 限制

- **只影响尚未创建的默认工作区**；已经注册的 Workspace 不会被隐式移动，避免破坏会话路径。
- 当前测试机没有真实可移除 SD 卡，只验证了“外部应用存储”路径和配置注入；真实 SD 卡需在带卡设备上复测。
- 卸载应用会删除应用专属外部目录；需要长期保留的文件应导出。
- 工作区内的文件读写正常，但不要在 SD 卡上放需要执行的文件（脚本可读，不能 `exec`）。
- 截图：存储选择。
