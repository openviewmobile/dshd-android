# 设备本地运行 dsh：Android / iOS 可行方案研究

> 说明：本文来自跨端仓库；未随本项目迁移的文档引用以纯文本保留。

> 目标：不依赖云端，在手机/平板本地跑 dsh 内核（Node + 工具 + 会话）。
>
> 结论：
> - **Android：技术上可行，且有比 Termux 更适合嵌入的路线；但“可嵌入 + 可上架 + 开箱即用”三者仍不能同时满足。**
> - **iOS：没有可嵌入的 Termux 等价物；本地跑完整 dsh 内核在生产环境不可行。**
> - 如果必须本地：Android 做“本地引擎”独立 SKU（sideload/企业分发）；iOS 只能远程。

---

## 1. 平台约束先讲清

### 1.1 Android 的“exec 墙”

Android 10（API 29）起，`targetSdk >= 29` 的 App **不能直接执行私有可写目录中的文件**（SELinux W^X 限制）。这直接决定：

- Termux 长期保持 **targetSdk 28**，才能在自己的 `files/usr` 里执行 bash/node/pnpm。
- Google Play 要求现代 targetSdk（35/36），所以 **Termux 不在 Play**。
- 任何“把 Linux 用户态放进 App 私有目录再执行”的方案，要么 targetSdk 28（不能上 Play），要么改用 `nativeLibraryDir`/`memfd` 等复杂技巧，要么使用系统级虚拟化（第三方 App 拿不到）。

这不是 Termux 的偶然选择，而是 Android 安全模型与“本地 Linux 运行时”的结构性冲突。

### 1.2 iOS 的“双墙”

- **禁止 `fork/exec`**：不能启动 bash/node/pnpm 子进程。
- **禁止 JIT**（普通 App 没有 entitlement）：V8/Node 只能解释执行。
- 后台挂起、沙箱、App Store 2.5.2（不得下载/执行改变 App 功能的代码）。

因此 iOS 上不存在“把 Linux 用户态嵌进 App 跑 Node”的正路；只有“模拟器/虚拟机 App”这种整体方案。

---

## 2. Android：可选方案

### 2.1 Termux（基线，不可嵌入）

- 独立 App，GPLv3，targetSdk 28，不在 Google Play。
- `nodejs` 包 26.13.0，满足 dsh `>=24.9`；bash/rg/python/clang 齐全。
- 集成方式只有 `RUN_COMMAND` Intent：要求用户装 Termux、授权限、开 `allow-external-apps`。
- 结论：**技术验证可用，不是 SDK，不能做开箱即用产品。**

### 2.2 PRoot + 自建 rootfs（最成熟的“可嵌入”核心）

- PRoot 是用户态 chroot（ptrace 拦截 syscall），不需要 root。
- 可自带 Debian/Ubuntu/Alpine rootfs，里面装 Node 24 + dsh + bash/rg/python。
- 没有官方“Android SDK”；你要自己把 `proot` 二进制、rootfs、启动脚本、终端/进程管理封进 App。
- 许可：PRoot 本体 GPLv2；`proot-distro` GPLv3；rootfs 内各包各自许可。
- 性能：syscall 拦截有开销（通常个位数到数十个百分点），但可接受。
- 关键风险：targetSdk 29+ 的 exec 限制；rootfs 在私有目录，target 35 可能执行失败。现有可用实现（Termux/UserLAnd/Tiny Container）大多 targetSdk 28。
- 结论：**能嵌入、能跑完整 Linux 用户态，但要接受 targetSdk/Play 限制与 GPL 义务；适合 sideload/企业版。**

### 2.3 Cortex（glibc 原生，Apache-2.0，最值得研究）

- 项目：`ssfuisu/cortex`，Apache-2.0，Android App。
- 机制：`LD_PRELOAD` 的 `libcortex-hook.so` 把 `/usr`、`/etc` 等路径重定向到 App 私有目录；Ubuntu 24.04 glibc 二进制直接在 Android 内核上执行，**不用 PRoot、不用 VM**。
- 它明确写着：`targetSdk = 28`，注释说明这是为了允许从 `filesDir` 直接 `execve` 二进制和 glibc linker；Android 10+ targetSdk>=29 会阻止。
- 已支持 apt、Node/Bun、OpenJDK、SSH、本地 Web 服务；对 dsh 来说，glibc + Node 24 + 原生模块的兼容性可能比 Termux bionic 更好。
- 但它是一个**完整 App，不是 library/SDK**；要集成只能 fork/抽取其运行时（Apache-2.0 允许，但工程与合规自担）。
- 结论：**目前最接近“Android 本地 dsh 内核”的技术路线；仍需 targetSdk 28 / 非 Play 分发；成熟度需验证。**

### 2.4 iTermux（“可嵌入 Termux”的实验）

- 项目：`2kDarki/iTermux`，自称“embeddable Android terminal runtime”，是 Termux 的 library-first fork，含 `core`、`proot-plugin`、`sample-app`。
- 但：**1 star、无 LICENSE、实验性**；不能作为生产依赖。
- 结论：**方向对，成熟度不足；可阅读其设计，不建议直接集成。**

### 2.5 Tiny Container / UserLAnd / Andronix

- 都是“普通用户可用的 Linux 容器 App”，大多基于 PRoot/Termux。
- Tiny Container：GPLv3，4k+ stars，一键 Debian + 桌面，证明“普通用户也能用本地 Linux”。
- 但都是**独立 App，不是可嵌入 SDK**；GPL/闭源各异；targetSdk/Play 同样受限。
- 结论：**产品形态参考，不是集成方案。**

### 2.6 Android Virtualization Framework / Linux Terminal

- Android 15+ 的“Linux Terminal”用 AVF 跑 Debian VM，官方、隔离好、性能接近原生。
- 但它是系统/OEM 能力，第三方 App 不能随意创建 VM（需要系统权限）；不是通用 SDK。
- 结论：**未来可能，当前不可用于第三方产品。**

### 2.7 自建 Node/libnode 运行时

- 把 Node 24 编译成 Android `libnode`，JNI 嵌入 App；工具二进制放 `nativeLibraryDir`（`lib*.so`）绕过私有目录 exec 限制。
- 优点：可能满足现代 targetSdk/Play；不需要完整 Linux rootfs。
- 缺点：dsh 依赖大量 Node 原生模块、bash/rg/pnpm/Python；要逐个交叉编译；`child_process`/PTY/信号在 Android 上不完整；工程量极大。
- 结论：**理论可行，工程风险最高；不如 PRoot/Cortex 现实。**

### 2.8 Android 方案对比

| 方案 | 可嵌入 | 完整 Linux 用户态 | 性能 | targetSdk/Play | 许可 | 成熟度 | 结论 |
|---|---|---|---|---|---|---|---|
| Termux 外部调用 | ❌（依赖 App） | ✅ | 高 | 不可 Play | GPLv3 | 高 | 极客验证 |
| PRoot + rootfs | ✅（自建） | ✅ | 中 | 通常 target 28 | GPLv2/各包 | 高（核心） | 可选，sideload |
| Cortex | ⚠️（fork App） | ✅（glibc 原生） | 高 | target 28 | Apache-2.0 | 中 | **最值得验证** |
| iTermux | ✅（自称） | ✅ | 高 | 未知 | 无 License | 极低 | 不生产 |
| Tiny Container/UserLAnd | ❌（App） | ✅ | 中 | 通常 28 | GPL/各异 | 高 | 参考 |
| AVF/Linux Terminal | ❌（系统） | ✅ | 高 | 系统级 | — | 中 | 不可用 |
| libnode 自建 | ✅ | ❌（需自备工具） | 高 | 可能 | Node | 低 | 高风险 |

---

## 3. iOS：可选方案

### 3.1 iSH

- 开源（GPLv3 + 附加 iOS 条款），App Store 上有 iSH Shell。
- 机制：用户态 **x86 模拟 + syscall 翻译**，在 App 内跑 Alpine Linux；没有 JIT。
- 能跑 Linux 命令，理论上也能装 x86 Node；但：
  - 纯解释执行，Node/dsh 这种重 IO/计算会非常慢；
  - 不能嵌入闭源 App（GPLv3）；
  - 是独立 App，不是 SDK；
  - 后台会被挂起，长任务不可靠。
- 结论：**演示/极客可用，不能作为 dsh 本地内核的产品方案。**

### 3.2 UTM SE

- UTM 是 Apache-2.0 的 VM App；UTM SE 是 App Store 版本，**无 JIT**，用解释执行。
- 可跑完整 Linux VM，但性能比 iSH 更差；不能嵌入；体积/电量/后台都不适合消费级。
- 结论：**技术存在，产品不可行。**

### 3.3 a-Shell / LibTerm / Play.js

- a-Shell：iOS 本地 shell，内置 Python/Lua/JS 等，但不是完整 Linux，也没有 Node 24 + 子进程。
- LibTerm：类似，偏命令/脚本。
- Play.js：iOS 上的 Node.js IDE/运行环境，但不是完整 Linux 用户态，不能作为 dsh 内核载体。
- 结论：**都不满足 dsh 的 Node 24 + bash/pnpm/原生模块要求。**

### 3.4 nodejs-mobile（iOS）

- 基于 Node 18.20.4，低于 dsh 要求；iOS 上不能 `fork/exec`，bash/pnpm/Python 工具链不可用。
- 即使移植 Node 24，dsh 的工具面也无法工作。
- 结论：**不可行。**

### 3.5 iOS 方案对比

| 方案 | 本地 Linux | Node 24 | 可嵌入 | 性能 | 许可 | 结论 |
|---|---|---|---|---|---|---|
| iSH | ✅（x86 模拟） | 理论可装，极慢 | ❌ GPLv3 | 低 | GPLv3 | 极客演示 |
| UTM SE | ✅（VM，无 JIT） | 理论可装，极慢 | ❌ App | 很低 | Apache-2.0（含 QEMU GPL） | 不可行 |
| a-Shell/LibTerm | ❌ 非完整 Linux | ❌ | ❌ | — | 各异 | 不满足 |
| Play.js | ❌ 非完整 Linux | 有限 | ❌ | — | 闭源 | 不满足 |
| nodejs-mobile | ❌ | ❌ Node 18 | ✅ | — | Node | 不可行 |
| 远程 Host | — | ✅ | ✅ | 高 | — | **唯一推荐** |

---

## 4. 关键结论：为什么“可嵌入 + 本地 + 上架”不能同时成立

1. **Android 的 exec 墙**：targetSdk 28 才能从私有目录执行 Linux 二进制；Play 要求现代 targetSdk。二者冲突。
2. **iOS 的进程/JIT 墙**：不能 fork/exec、不能 JIT；本地完整 Node 工具链没有合法路径。
3. **GPL/许可**：Termux/iSH/PRoot 等核心组件多为 GPL；闭源商业 App 嵌入需开源或法律设计。
4. **体积/后台/性能**：完整 Linux 用户态 + Node + dsh deps 是 1 GB 级；手机后台会杀；电池/发热明显。
5. **安全**：本地 shell Agent 能访问用户文件、执行命令；提示注入/恶意插件风险比桌面更高。

因此：

- **Android 本地**：只能作为 **sideload / 企业 / 极客版**，不能作为 Play 商店的普通消费者版本。
- **iOS 本地**：生产不可行；远程是唯一可靠路径。
- **“设备本地 + 开箱即用 + 六端上架”**：不存在。

---

## 5. 建议的落地架构

### 5.1 Android 本地版（可选 SKU）

```
Flutter/Capacitor UI
   │ localhost HTTP/WS
   ▼
Local Runtime (Cortex-style glibc 或 PRoot Debian)
   │
   ├─ Node 24 + dsh
   ├─ bash / rg / pnpm / python
   └─ workspace (app-private + SAF)
```

- 运行时：优先验证 **Cortex 机制**（glibc 原生、Apache-2.0、性能好）；PRoot 作为备选（成熟、GPL、慢一些）。
- 分发：GitHub/官网 APK、企业 MDM；**不上 Google Play**（targetSdk 28）。
- 保活：前台服务 + 唤醒锁 + 电池优化引导；长任务通知。
- 存储：App 私有 workspace + SAF 导入/导出；不要默认全盘访问。
- 安全：默认审批、工作区白名单、插件禁用、网络出口限制。
- 版本：dsh/runtime 与 App 同版本打包；OTA 更新 runtime（注意动态代码政策，仅 sideload 可做）。

### 5.2 iOS

- 不做本地内核；Flutter/Capacitor 客户端 + 远程 Host。
- 若必须“本地演示”：iSH/UTM SE 作为外部 App，不作为产品承诺。

### 5.3 产品分层建议

| 版本 | Android | iOS | Web |
|---|---|---|---|
| 标准版（商店） | 远程 Host 伴生 | 远程 Host 伴生 | 远程 Host |
| 本地版（sideload/企业） | Cortex/PRoot 本地内核 | 不提供 | 不适用 |
| 极客实验 | Termux 外部调用 | iSH 演示 | — |

---

## 6. Android 本地验证清单

在投入集成前，先用 Cortex 或 Termux 手动验证：

1. Node 24+（`node -v`）与 `pnpm`。
2. `npm i @deepseek-ai/dsh` 成功；`dsh --version`。
3. `dsh web --no-open --port 0` 启动；本地浏览器/WebView 能加载。
4. 工具：bash、read/write、ripgrep、图片、diff。
5. 原生模块：`node-pty`、`koffi`、`sharp`、`node:sqlite`。
6. 长任务/后台 30 分钟；锁屏/切后台；前台服务。
7. 内存/电量/发热；冷启动时间。
8. 文件：workspace、SAF 导入/导出、中文路径。
9. 安全：审批、工作区越界、命令注入、网络出口。
10. 许可：PRoot/Cortex/Termux/rootfs 包的义务。

**判定**：如果 3/4/5 有任一失败，先不要谈产品化；dsh 的工具面会放大问题。

---

## 7. 参考项目与许可

| 项目 | 作用 | 许可 | 可嵌入 |
|---|---|---|---|
| [Termux](https://github.com/termux/termux-app) | Android Linux 环境 | GPLv3 | ❌（独立 App） |
| [PRoot](https://github.com/proot-me/proot) | 用户态 chroot | GPLv2 | ✅（二进制/自封装） |
| [proot-distro](https://github.com/termux/proot-distro) | rootfs 管理 | GPLv3 | ⚠️（脚本） |
| [Cortex](https://github.com/ssfuisu/cortex) | glibc Ubuntu on Android | Apache-2.0 | ⚠️（fork App） |
| [iTermux](https://github.com/2kDarki/iTermux) | 可嵌入 Termux fork | 无 License | ⚠️（实验） |
| [Tiny Container](https://github.com/Cateners/tiny_container) | 一键 Debian 容器 | GPLv3 | ❌（App） |
| [UserLAnd](https://github.com/CypherpunkArmory/UserLAnd) | Android Linux | 未标注 | ❌（App） |
| [iSH](https://github.com/ish-app/ish) | iOS x86 Linux | GPLv3 | ❌（App） |
| [UTM](https://github.com/utmapp/UTM) | iOS/macOS VM | Apache-2.0 | ❌（App） |
| [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile) | 移动 Node | Node | ✅（但 Node 18） |

---

## 8. 最终建议

- **如果“本地”是硬要求，且只要求 Android**：做 **Android 本地版**，优先验证 Cortex 机制，PRoot 备选；接受 targetSdk 28、非 Play、GPL/许可、1 GB 级体积。
- **如果必须上 Google Play**：Android 只能远程 Host；本地内核与 Play 的 targetSdk/exec 规则冲突。
- **如果必须 iOS 本地**：没有可产品化的路径；iSH/UTM SE 只能演示。
- **如果六端都要“开箱即用”**：桌面本地 + 移动/Web 远程，是唯一现实的组合。

---

## 9. 参考资料

- [Android 10：执行私有目录文件的限制](https://developer.android.com/about/versions/10/behavior-changes-10#execute-app-data)
- [Google Play Target API 要求](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Termux App（GPLv3，targetSdk 28）](https://github.com/termux/termux-app)
- [Termux nodejs 26.13.0](https://packages.termux.dev/apt/termux-main/pool/main/n/nodejs/)
- [Cortex（Apache-2.0，targetSdk 28）](https://github.com/ssfuisu/cortex)
- [PRoot](https://github.com/proot-me/proot)
- [iSH（GPLv3）](https://github.com/ish-app/ish)
- [UTM（Apache-2.0）](https://github.com/utmapp/UTM)
- [Apple App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
