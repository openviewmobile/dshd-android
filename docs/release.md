# Android 发布

> 包名、图标、证书指纹、手机屏幕适配和 SD 卡工作区见 [packaging.md](packaging.md)。

## 构建

```bash
# debug
scripts/build-apk.sh

# release：默认内置 fixed22-nativepdf rootfs，必须提供仓库外的 keystore
DSHD_KEYSTORE=/secure/release.jks \
DSHD_KEYSTORE_PASSWORD=... \
DSHD_KEY_ALIAS=... \
DSHD_KEY_PASSWORD=... \
scripts/build-release-apk.sh

# 可选：不带 rootfs 的 856 KB 小 APK
DSHD_EMBED_ROOTFS=none DSHD_KEYSTORE=/secure/release.jks \
DSHD_KEYSTORE_PASSWORD=... DSHD_KEY_ALIAS=... DSHD_KEY_PASSWORD=... \
scripts/build-release-apk.sh
```

默认输入是 `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed22-nativepdf.tar.xz`；
可用 `DSHD_EMBED_ROOTFS=/path/to/rootfs.tar.xz` 覆盖。构建会把 xz 以
`assets/rootfs.tar.xz` 存入 APK（不再二次压缩），APK 约 144 MB。

如果仍要做外部 rootfs 下载/更新，可同时提供：

```bash
DSHD_ROOTFS_URL=https://example.com/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed22-nativepdf.tar.xz \
DSHD_ROOTFS_SHA256=4fa3b4fd9bb4bf1877c5738633f3f46e6ba2c6910a1c3384fca08f4ed8caba46 \
scripts/build-release-apk.sh
```

输出：`dist/dshd-android-release.apk`。

## 当前产物

| 产物 | SHA-256 |
|---|---|
| `dist/dshd-android-release.apk`（0.3.2，内置 fixed22-nativepdf，144 MB，v3 签名） | `73b7c945590a5dd37475256744046548d61ec403f8aba60c9fa2ea3697be0db5` |
| `dist/dshd-android-release-lite.apk`（0.3.2，不内置 rootfs，856 KB） | `4e4c4e737d91d0dd27ad6714a2e0a61963ab7b512033f43420edc8573fb3377f` |
| `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed18.tar.gz`（276 MB，gzip 兼容包） | `c7c60899d8ba2ef24e3e47d61a1665a0bb46ab1c37f92893f4338e2bce0ca86f` |
| `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed18-xz.tar.xz`（150 MB） | `7764ddbce6478ba16ecd6cf839e80ea81758703eaefe738e4dadb672ebbb8644` |
| `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed22-nativepdf.tar.xz`（143 MB，推荐） | `4fa3b4fd9bb4bf1877c5738633f3f46e6ba2c6910a1c3384fca08f4ed8caba46` |
| `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed20-clean.tar.xz`（132 MB，已验证） | `8926522ab07ef4bc392f44d62400f1a24399a454f37cc597ea8ed03e1733def2` |

fixed17/fixed19/fixed15 是历史产物，仍保留在 `dist/` 供回溯；当前发布
以 0.3.2 内置 APK 为准（rootfs 就在 APK 内）；fixed22-nativepdf 是当前包。fixed20-strip/clean 保留为历史回溯，不含原生 PDF 桥补丁。

## 发布门禁

```bash
DSHD_KEYSTORE=/secure/release.jks \
scripts/check-release-readiness.sh
```

检查项：APK 存在、v2/v3 签名、classes.dex 无 loopback rootfs URL、keystore 非测试 key；
若 APK 内置 `assets/rootfs.tar.xz`，检查 xz magic 后不再要求 URL；否则才要求
HTTPS rootfs URL 和 64 位 SHA-256。

## 当前限制

- 内置 rootfs 使 APK 约 144 MB；每次发版都要传完整 APK。
- 首次安装需要约 2 GB 峰值空间（APK 144 MB + 解包 rootfs 约 1 GB + 临时树）。
- 不走 Play；GitHub Release/MDM 侧载。144 MB 在 Play 200 MB base 限制内，但上架政策另评估。
- 升级覆盖安装已验证；rootfs 迁移会保留用户数据，但进行中的任务仍会中止。

## 版本与升级

- `targetSdk 28`，不上 Play，走 GitHub Release / MDM 侧载。
- APK 更新时，若内置 rootfs 的 SHA-256 与 `files/rootfs.version` 不同，App 自动把
  `.dsh`、Documents、workspace、SSH/GPG 和 `.npmrc` 复制到临时目录，替换 rootfs 后恢复；
  `android-bridge.json` 不保留，由新 Host 重建。
- 内置 rootfs 版本号就是 xz 包的 SHA-256；同一版本二次启动不会重复解包。
- Release 签名必须固定 keystore；不要把 keystore 或密码提交到仓库。

## rootfs 体积

`fixed22-nativepdf` 在 fixed20-strip 基础上加入 Chromium 92 PDF 客户端补丁和原生 `PdfRenderer` 桥，功能与 0.3.2 一致：

- 解包后约 **1.00 GiB**；
- gzip 包 **276 MB**（fixed18）；xz 包 **150 MB**（fixed18-xz）；
  fixed22-nativepdf 为 **143 MB**（推荐）；
- 剔除 `home/.npm/_cacache`（约 253 MB）、`var/cache/*` 和
  `var/lib/apt/lists/*`（约 177 MB）、日志；
- Node 24 的调试符号被 strip（118 MB → 99 MB）；删除 node-pty 的
  Windows/macOS/linux-x64 预编译和 pnpm 的非 arm64 reflink；
- 不含 sessions/storages/credentials，仍包含 Node 24、dsh、Web 前端和 Android bridge 插件；
- 打包规则在 `scripts/pack-rootfs.sh`；输出 `.tar.xz` 时使用 xz，输出 `.tar.gz` 时使用 gzip。

`RootfsInstaller` 已支持 gzip 和 xz 两种格式；xz 解码使用纯 Java 的
`org.tukaani:xz`，不依赖 NDK，APK 增加约 115 KB。真机已通过 xz 导入、Host 启动和 E2E。

按功能的实验性裁剪（未作为默认发布物，需逐项回归）：

| 变体 | 压缩大小 | 代价 |
|---|---:|---|
| `fixed22-nativepdf` 当前标准 | 143 MB | 内置 rootfs + 原生 PDF 桥（推荐） |
| `fixed20-strip` 历史标准 | 143 MB | 无原生 PDF 桥，仅回溯 |
| `fixed18-xz` 上一版 | 150 MB | 无原生 PDF 桥 |
| `fixed20-clean` | 132 MB | 历史低带宽包，不含原生 PDF 桥 |
| gzip 兼容包 | 276 MB | 无功能损失，下载更大 |
| 去掉 `libreoffice-kit-wasm` | 120 MB | 文档/Office 预览与转换不可用 |
| 再去掉语音、开发头文件/类型/测试/map | 98 MB | 语音输入、原生模块编译和部分开发诊断不可用 |

结论：**143 MB fixed22-nativepdf 是 0.3.2 正式目标**；132 MB fixed20-clean 是历史低带宽包，不含原生 PDF 桥。
