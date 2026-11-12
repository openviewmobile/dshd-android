# ADR-0007：全本地产品范围评估（不做远程版）

> 说明：本 ADR 来自跨端仓库；未随本项目迁移的文档引用以纯文本保留。

- 状态：已确认（v1）：macOS/Windows/Linux/Android 本地版；iOS 保留研究，不进 v1
- 日期：2026-11-08
- 决策者：产品/架构
- 相关：ADR-0002、ADR-0005、[ADR-0006](0006-direct-apk-ipa-distribution.md)、设备本地运行研究、iOS 本地可行性、v1 交付范围

## 背景

产品目标改为：

- **不提供远程版**；
- **所有平台统一提供本地版**；
- 内核仍然是 dsh（Node + 本地 shell/文件系统）。

问题不是“能不能打包”，而是：**哪些平台在操作系统层面允许一个 App 本地运行完整 dsh 内核。**

## 结论

| 平台 | 本地完整 dsh | 判断 |
|---|---|---|
| macOS | ✅ | Electron/本地 Node 24；可行 |
| Windows | ✅ | Electron/本地 Node 24；可行 |
| Linux | ✅ | Electron/本地 Node 24；可行 |
| Android | ✅（有条件） | Cortex/PRoot + targetSdk 28；可行但非 Play |
| iOS | ⚠️ 研究中 | 完整 L2 无产品级证据；iSH/UTM/Node-in-process（L0/L1）待实测 |
| Web | ❌ | 浏览器无本地文件系统/子进程；必须有 Host |

**当前证据：完整 L2 的“六个平台全部本地版”不成立。**
如果“全本地”是硬要求，当前可交付范围是：**macOS + Windows + Linux + Android**；iOS 仍需 4–8 周 spike 验证，不能现在永久删除。
如果“六个平台”是硬要求，则 iOS/Web 要么远程，要么接受 dsh Lite/模拟器路线的能力裁剪。

## 1. 桌面三端：没有争议

- Electron 44 + 内置 Node 24 + dsh；
- 本地文件、bash、ripgrep、pnpm、Python、原生模块；
- 签名/公证/更新；
- GitHub Releases 分发。

这是当前架构已经验证的路径。

## 2. Android：可行，但要走独立 SKU

- 不发 Play，可以用 `targetSdk 28`，从 App 私有目录执行 Linux 二进制；
- 路线：Cortex（glibc 原生、Apache-2.0）或 PRoot（成熟、GPL）；
- 分发：GitHub APK / 企业 MDM；
- 代价：
  - 未知来源/Play Protect/受限设置；
  - 16 KB page size；
  - 1 GB 级 runtime；
  - 前台服务/后台被杀/电量；
  - 原生模块需逐项验证；
  - GPL/rootfs 许可。

**Android 是“全本地”里唯一能新增的移动平台。**

## 3. iOS：IPA 不等于本地

### 3.1 系统限制没有变化

- 禁止 `fork/exec`：不能启动 bash/node/pnpm；
- 禁止 JIT：Node/V8 只能解释执行；
- 后台挂起：不能常驻 Host；
- 沙箱：不能安装 Linux 用户态。

**Sideload/IPA 只改变安装方式，不改变上述限制。**

### 3.2 候选技术路径（均待实测）

| 路径 | 原理 | 问题 | 当前判断 |
|---|---|---|---|
| **iSH 嵌入** | App 内 x86 Linux 模拟 + syscall 翻译；Alpine 里装 Node/dsh | GPLv3；32 位；无 JIT；5–100x 慢；已知 Node 非法指令；不是 SDK | 最接近完整本地；需真机验证 Node 24 |
| **UTM SE/VM 嵌入** | App 内 ARM64 Linux VM（QEMU 解释器） | 无 JIT；更慢；包体巨大；后台；不是 SDK | 可跑 ARM64 Node 24；产品性能存疑 |
| **Node 24 in-process** | 把 Node 24 编成 iOS 库；不跑 Linux 用户态 | 无 `child_process`/bash/PTY；原生模块需重编；nodejs-mobile 仅 Node 18 | 最可能做出 **dsh Lite（L0/L1）** |
| **自研 Swift/JS 轻量 Agent** | 不走 Node/dsh，重写工具 | 不是 dsh；工具/插件/Session 不兼容 | 换产品，不是本地 dsh |
| **jailbreak/TrollStore** | 绕过限制 | 不支持、不安全、不可持续 | 不做 |

iSH 嵌入理论上能让“iPhone 本地跑一个 Linux + Node”，但：

- iSH 是 32 位 i386；Alpine edge x86 虽有 Node 24.22.1，但 Node 24 官方无 linux-x86 二进制，属发行版补丁；
- iSH 已有 Node/NPM `Illegal instruction` 报告（Node 20+ 需退回旧版），Node 24 能否启动必须真机实测；
- iSH 官方给出的性能量级是比原生慢 5–100 倍；
- GPLv3，闭源/商业分发受限；
- IPA 只能自签/Ad Hoc/企业，不是普通用户渠道；
- 后台被杀，长任务不可靠。

**当前判断：完整 L2 没有产品级证据；iSH 路线不能因“理论上能跑”就判死，也不能当成已可行。**

## 4. Web：没有“本地内核”这回事

浏览器不能：

- 启动 Node 进程；
- 执行 bash/pnpm；
- 访问任意本地文件系统；
- 常驻后台。

WebAssembly 也不能提供完整 Node API、子进程和系统工具。所谓“Web 本地”只有两种：

1. 浏览器连接同机/局域网/云端的 Host → 仍然是远程；
2. 桌面 App 内置 Host，浏览器只是它的 UI → 不是独立 Web 产品。

**结论：不做远程版，就没有 Web 版。**

## 5. 四种产品范围

### 方案 A：全本地四端（推荐，如果“本地”是硬要求）

| 平台 | 形态 | 内核 |
|---|---|---|
| macOS | 桌面 App | 本机 dsh |
| Windows | 桌面 App | 本机 dsh |
| Linux | 桌面 App | 本机 dsh |
| Android | 本地 APK | 本机 Cortex/PRoot + dsh |
| iOS | 不提供 | — |
| Web | 不提供 | — |

- 优点：真正全本地；无云端依赖；隐私叙事最强。
- 缺点：放弃 iOS/Web；Android 维护成本高；市场规模受限。
- 工期：桌面 2–3 个月；Android 本地 6–12 个月；合计 9–15 个月（3–5 人）。

### 方案 B：全本地六端 + iOS/Web “Lite”

- iOS/Web 不跑 dsh，改用轻量 Agent（Swift/JS/WASM）或只读 UI。
- 结果：不是同一个内核、不是同一套工具、不是同一个产品。
- 不推荐：用户会认为“功能残缺”，维护两套 Agent。

### 方案 C：六端，但 iOS/Web 远程（违背“不做远程”）

- 桌面/Android 本地；iOS/Web 连接本地 Host 或云端。
- 这是唯一能保留六端完整 dsh 的方案。
- 如果“不做远程”不能妥协，此方案不成立。

### 方案 D：iOS 用 iSH/UTM 硬上

- 技术 demo 可以，产品不可行。
- 不建议投入。

## 6. 如果选方案 A，架构怎么定

```
┌─────────────────────────────┐
│ 统一 UI / 客户端逻辑         │
│ （Flutter 或 Electron/React）│
└──────────────┬──────────────┘
               │ 本地 HTTP/WS
     ┌─────────┴─────────┐
     │                   │
┌────▼──────┐      ┌─────▼──────┐
│ Desktop   │      │ Android    │
│ Electron  │      │ Cortex/    │
│ + Node 24 │      │ PRoot +    │
│ + dsh     │      │ Node 24    │
└───────────┘      └────────────┘
```

关键决策：

- UI 是否统一：
  - Electron/React：桌面复用上游，Android 用 Capacitor/Flutter；
  - Flutter：桌面+Android 统一 UI，但要重写协议/壳；
- Android runtime：Cortex 优先（Apache-2.0、性能好），PRoot 备选；
- 分发：GitHub Releases；Android APK targetSdk 28；桌面签名/公证；
- 无 iOS/Web 构建、无远程配对/中继/云端。

## 7. 成本与风险

| 项目 | 估算 |
|---|---|
| 桌面三端 | 2–3 个月（复用上游） |
| Android 本地 | 6–12 个月（Cortex/PRoot、原生模块、后台、存储） |
| 统一 UI（若 Flutter） | +9–15 个月 |
| iOS 本地尝试 | 3–6 个月也只能到 demo |
| Web 本地 | 不可能 |
| 维护 | Android runtime 每月跟 dsh/Node/rootfs 升级 |

主要风险：

- Android targetSdk 28 的兼容性/安全警告；
- Play Protect/厂商限制；
- 16 KB page size；
- 原生模块编译；
- 后台被杀；
- GPL/rootfs 许可；
- 没有 iOS/Web 导致用户覆盖不足。

## 8. 建议

1. **确认“全本地”是否高于“六端覆盖”。**
   - 如果是：选方案 A，产品范围写死 macOS/Windows/Linux/Android。
   - 如果六端不可放弃：选方案 C，iOS/Web 远程。
2. **不要用 iSH/UTM 硬做 iOS 本地**；投入产出比极差。
3. **Android 先验证再立项**：Cortex/PRoot 跑 dsh 的 Node 24、工具、原生模块、后台；通过再投入。
4. **UI 统一可以后置**：先桌面 Electron + Android 本地，验证需求；再决定 Flutter。
5. **文档/官网明确“不支持 iOS/Web 本地”**，避免用户预期错位。

## 9. 最终判断

- **v1 范围：macOS/Windows/Linux/Android 本地版；不做 iOS/Web/远程。**
- **全本地四端（macOS/Windows/Linux/Android）：可行。**
- **全本地六端：当前证据不支持完整 L2。**
- **iOS 本地完整 dsh：研究中；iSH/UTM 可做 demo，Node-in-process 可能做 L0/L1 dsh Lite。**
- **Web 本地 dsh：概念上不成立。**
- **如果“不做远程”是硬约束，产品就必须放弃 iOS/Web；如果六端是硬约束，就必须接受远程。**

## 复审条件

- Apple 开放 JIT/子进程/本地运行时（可能性低）；
- Web 标准出现可用的本地进程/文件系统能力（可能性低）；
- Android 本地 runtime 验证失败；
- iOS spike 完成并确认 L0/L1 或 L2 不可行；
- 产品决定放弃 iOS；
- 产品决定恢复远程版以保六端。
