# Android 架构

## 范围

一个**全本地** Android App：默认把 fixed22-nativepdf rootfs（xz 143 MB）内置进 APK，
DSHD（DeepSeek Harness Desktop）首次启动自动解包到 App 私有目录；之后由该 glibc 用户态运行官方 Linux Node 24 和
`dsh` Host，原生 WebView 加载 `dsh web` 前端。外部导入/下载保留为备用路径。
没有远程 Host、没有伴生客户端。

## 组件

| 组件 | 职责 |
|---|---|
| `MainActivity` | 原生设置表单、WebView、SAF 文件选择、生命周期；协调启动页显隐 |
| `BootOverlay` | 深色品牌启动页、分阶段文案、解包/启动进度；Web UI 就绪后淡出 |
| `HostKeepAliveService` | 前台服务持有 Host 进程、token/端口、失败重启与通知 |
| `RootfsInstaller` | 内置/外部 rootfs 流式导入、gzip/xz 解包、用户数据复制 |
| `ProviderConfig` / `ProviderSecretStore` | Provider 表单；API key 用 Android Keystore AES-GCM 加密 |
| `PdfBridge` | 把 PDF 字节缓存到 app cache，用 Android `PdfRenderer` 逐页渲染为 PNG 给 WebView |
| `files/rootfs` | app 私有的 Ubuntu/glibc 用户态、Node 24、dsh 与插件 |
| `app/src/main/assets` | rootfs.tar.xz（默认）、polyfill、Chromium 92 兼容前端、Cortex hook |

## 启动链路

1. APK 启动，先由 `BootOverlay` 覆盖原生 tab 与 WebView；同时检查 `files/rootfs`
   是否包含 loader、Node 和 dsh `bin.js`。
2. 内置版且没有 rootfs 时，直接从 `assets/rootfs.tar.xz` 流式解包；内置包存在但
   `files/rootfs.version` 不一致时，先备份用户 home 数据，替换 rootfs 后再恢复。
3. 没有内置包或导入失败时，进入原生首启页：HTTP(S) 下载或 SAF 导入。
4. 没有 Provider 时显示原生表单，写入 rootfs 内的 `cordis.patch.yml`；key 单独加密。
5. `HostKeepAliveService` 通过 rootfs 的 `ld-linux-aarch64.so.1` 启动 Node，设置
   `CORTEX_ROOT`、`LD_LIBRARY_PATH`、`LD_PRELOAD`、`HOME` 和 `DSH_HOME`。
6. 从 stdout 解析带一次性 token 的 `dsh web:` URL，交给 WebView；token 换成 cookie。
7. WebView 的 composer 出现且历史记录加载完成后，启动页淡出；Provider 首启或错误
   状态会直接收起启动页，避免遮挡表单。
8. Activity 销毁不影响 Host；前台服务被杀后按退避重启。

## 数据布局

```
/data/user/0/com.dshd.android/files/
  rootfs/                 # app 私有 rootfs
    home/.dsh/            # DSH_HOME：profiles、sessions、storages、speech
    opt/node24/           # Node 24
    opt/node24/lib/node_modules/@deepseek-ai/dsh/...
  rootfs.version          # 当前 rootfs 的 SHA-256（内置包版本）
  rootfs-userdata.<ts>/    # 仅升级迁移失败时保留的用户数据备份
  provider-key.enc        # Keystore 加密的 API key
```

内置 rootfs 升级保留 `home/.dsh`、`home/Documents`、`home/workspace`、`.ssh`、
`.gnupg` 和 `.npmrc`；不保留旧 `android-bridge.json`，由新 Host 重新生成。
`DSH_HOME` 必须显式传给 Host；Android 的 `homedir()` 语义与 app 私有目录不一致。
附件存储会对 DSH_HOME 及祖先做 fsync，Android 沙箱禁止读 `/data/user/0`，因此
`dsh-attachment-local` 在 `EACCES/EPERM` 时停止向上 fsync。

## WebView 兼容层

设备 WebView 为 Chromium 92，前端需要：

- polyfill：`Promise.withResolvers`、`Object.hasOwn`、`AbortSignal.any/timeout`、
  `Array.prototype.toSorted/toReversed/toSpliced/with` 等；
- Babel 将 class static block 等语法转译到 chrome92；
- 插件 `client.js` 也在入口页 polyfill 生效后加载。

PDF 页面不走 Chromium 92 的 PDF.js worker 字体链路：`PdfBridge` 用平台
`PdfRenderer` 输出页面 PNG，PDF.js 只保留页码、缩放和工具栏状态；原生桥不可用时
回退到原 PDF.js 路径。

长期应升级 WebView 或在构建前端时直接输出 legacy target，不应依赖设备端 Babel。

## 安全与权限

- 原生权限中心统一管理麦克风、相机、前台定位、通知、蓝牙、直接拨号、读取联系人；
  详细矩阵与“不接入”清单见 [permissions.md](permissions.md)。
- `RECORD_AUDIO` / `MODIFY_AUDIO_SETTINGS` 用于 WebView 语音输入；相机/麦克风
  通过 `onPermissionRequest` 在运行时请求，定位通过 `GeolocationPermissions` 请求。
- agent 通过 loopback HTTP 原生桥调用 Android 能力：随机 token、仅 `127.0.0.1`、
  token 文件 `0600`，对应 dsh 插件 `@deepseek-ai/dsh-android-bridge`。
- `CALL_PHONE` 是**直接拨号**；`READ_CONTACTS` 只读；没有短信、通话记录、后台定位、
  无障碍、设备管理员等高风险权限。
- Provider key 不写入源码/仓库；release 构建使用仓库外的 keystore。
- `targetSdk 28` 是 Cortex rootfs 兼容选择，不上 Play；通过 GitHub/APK 侧载。
- Host token 只在本机 loopback 使用；WebView 不接受任意外部 URL。
