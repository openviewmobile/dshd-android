# ADR-0006：直接分发 APK/IPA，不上 Google Play / App Store

> 说明：本 ADR 来自跨端仓库；未随本项目迁移的文档引用以纯文本保留。

- 状态：评估中（Evaluation）；全本地模式（[ADR-0007](0007-local-only-scope.md)）下不提供 iOS，本文 IPA 分析仅作对照
- 日期：2026-11-08
- 决策者：产品/架构
- 相关：ADR-0005、发布矩阵、设备本地运行研究

## 背景

产品计划：

- **Android 不发 Google Play**；
- **iOS 不上 App Store**；
- 提供 **IPA 包**给用户安装；
- 目标仍然是设备本地跑 dsh 内核。

需要评估：这种“直接分发”是否成立，尤其 iOS 的 IPA 能否成为普通用户可用的安装渠道。

## 结论

1. **Android 不发 Play：可行，而且让本地内核更容易。**
   - 可以直接使用 `targetSdk 28`，从 App 私有目录执行 Linux 二进制。
   - Cortex/PRoot/Termux 路线不再受 Play targetSdk 限制。
   - 代价是：未知来源安装、Play Protect 警告、更新/信任/支持成本。

2. **iOS 提供 IPA：技术上可签，但不是普通用户的安装渠道。**
   - IPA 不是“下载后双击安装”的格式；必须用 Apple 证书 + provisioning profile 签名。
   - 公开分发签名 IPA 受 Apple 开发者协议限制；Ad Hoc 只能装注册设备，Enterprise 只能内部使用，TestFlight 是 beta。
   - 用户自行用 AltStore/Sideloadly/Apple ID 签名，免费账号 7 天过期、最多 3 个 App，且需要电脑/刷新。
   - 因此：**IPA 适合极客/企业/内测，不适合“普通用户开箱即用”。**

3. **IPA 不改变 iOS 的本地内核限制。**
   -  sideload 不会获得 `fork/exec`、JIT、后台常驻权限。
   - 完整 Node + bash + pnpm 仍然不能本地运行。
   - iSH/UTM 嵌入理论上可做，但纯模拟、极慢、GPL、包体大，不是产品方案。

4. **如果目标是普通用户，建议保留商店作为“远程版”渠道：**
   - iOS App Store 只放远程伴生客户端（审核风险低）；
   - Android Google Play 只放远程版；
   - Android 本地版继续 GitHub APK；
   - 不要让“本地内核”绑架整个分发策略。

## iOS IPA 的六种签名/分发方式

| 方式 | 设备限制 | 有效期 | 能否公开给普通用户 | 结论 |
|---|---|---|---|---|
| **Ad Hoc** | 每类设备 100 台/年 | 描述文件 1 年 | ❌ 需收集 UDID | 小范围内测 |
| **Development** | 100 台/年 | 1 年（免费账号 7 天） | ❌ | 开发调试 |
| **Enterprise** | 不限（内部） | 1 年 | ❌ 仅内部员工；公开违反协议 | 企业内部分发 |
| **TestFlight** | 外部 10,000 人 | 每个构建 90 天 | ⚠️ 需 Beta 审核，非永久 | 公测 |
| **AltStore/SideStore** | 用户自己的 Apple ID | 免费 7 天 / 付费 1 年 | ⚠️ 需电脑/刷新 | 极客 |
| **EU Web Distribution / 替代市场** | 仅 EU，iOS 17.5+ | 按 Apple 规则 | ⚠️ 区域限制、公证/费用 | 未来选项，非全球 |
| **TrollStore** | 依赖漏洞/特定版本 | 不定 | ❌ 不安全、不可持续 | 不做 |

### 关键限制

- **Ad Hoc**：要用户提供设备 UDID，你把设备加入 provisioning profile，再生成 IPA；100 台/年，设备数一满就得等下一年。无法规模化。
- **Enterprise**：$299/年，需要 D-U-N-S、100+ 员工、内部使用；公开分发会被 Apple 吊销证书，已安装 App 直接无法启动。
- **TestFlight**：不是“直接发 IPA”；要走 Beta 审核，构建 90 天过期，适合 beta，不适合正式产品。
- **AltStore/SideStore**：用户要装 AltServer/配对、每 7 天（免费）或 1 年（付费）重签；普通用户不会接受。
- **EU Web Distribution**：只适用于欧盟，且需要公证、开发者账号、替代市场/费用；不能作为全球分发。
- **TrollStore**：利用系统漏洞，版本受限、安全风险高，不能作为产品基础。

## 法律与协议风险

- Apple Developer Program License Agreement 对 **Ad Hoc 设备注册**、**Enterprise 内部使用**、**签名证书分发**有明确限制。
- 公开提供“企业签名 IPA”或共享证书，可能导致证书吊销、账号封禁；已安装用户 App 失效。
- 开源代码不等于自动获得 Apple 证书/描述文件的分发权；GPL 也不解决 Apple 协议问题。
- 如果 IPA 内含 GPLv3 组件（如 iSH/UTM），还要满足 GPL 源码义务；与 Apple 协议可能冲突，需法务评估。
- 建议：**不发布带企业证书的公开 IPA；不共享签名证书；只提供未签名 IPA + 用户自行签名说明**（面向开发者），或者走 Ad Hoc/TestFlight 的合规路径。

## 技术影响：iOS 本地 dsh 仍然不可行

即使 sideload：

- App 仍在 iOS 沙箱内，不能 `fork/exec`；
- 没有 JIT entitlement，Node/V8 只能解释执行；
- 后台会被挂起，不能常驻 Host；
- 不能安装 bash/pnpm/Python 系统包。

可选“本地”路径只有：

- **iSH 嵌入**：GPLv3，x86 模拟，无 JIT；Node/dsh 极慢；包体/电量/后台不可接受；不是产品。
- **UTM SE/VM 嵌入**：无 JIT，更慢；包体巨大；不是产品。
- **jailbreak/TrollStore**：不可支持、不可持续。

**结论：iOS IPA 只能承载远程伴生客户端，不能承载完整 dsh 内核。**

## Android：不发 Play 的收益与代价

### 收益

- 可用 `targetSdk 28`，本地 Linux 用户态可执行；Cortex/PRoot 路线成立。
- 不受 Play targetSdk/16 KB/动态代码/终端类政策约束。
- 可直接发 GitHub APK，迭代快，无需审核。
- 本地版与远程版可以合并或拆分成不同 APK，自由度高。

### 代价

- 用户要开“未知来源”，部分厂商/Android 版本还有“受限设置”。
- Play Protect 可能提示“不安全/未扫描”；targetSdk 28 会有兼容性警告。
- 没有商店信任背书；钓鱼/篡改风险由用户承担。
- 更新要自己做：应用内下载 APK → 校验 → 调起安装器；需要 `REQUEST_INSTALL_PACKAGES`。
- 16 KB page size：本地 runtime 的 Node/native `.so` 必须支持，否则 Android 15+ 设备可能装不上/跑不起来。
- 国内厂商商店/企业 MDM 另计；F-Droid 对 targetSdk/非自由 rootfs 可能不接受。

## 建议的分发组合

### 方案 A：只做极客/企业（与当前计划一致）

| 平台 | 渠道 | 说明 |
|---|---|---|
| macOS/Windows/Linux | GitHub Releases | 签名/公证 |
| Android 本地版 | GitHub APK | targetSdk 28；未知来源 |
| Android 远程版 | GitHub APK | 可选；target 最新 |
| iOS 远程版 | **未签名 IPA + 自签说明** 或 Ad Hoc/TestFlight | 只面向开发者/内测 |
| iOS 本地版 | 不提供 | 技术不可行 |

**适合**：技术用户、企业内部分发、开源社区。
**不适合**：普通消费者；iOS 安装门槛极高。

### 方案 B：商店 + 直发混合（推荐，如果还要普通用户）

| 平台 | 渠道 | 说明 |
|---|---|---|
| 桌面 | GitHub Releases | 主渠道 |
| Android 本地版 | GitHub APK | 独立 SKU，targetSdk 28 |
| Android 远程版 | Google Play + GitHub APK | 普通用户主推 |
| iOS 远程版 | App Store/TestFlight | 普通用户唯一可行 |
| iOS 本地版 | 不提供 | — |

**适合**：既要本地 Android，又要普通 iOS 用户。
**关键**：iOS 只上远程客户端，审核风险低；本地内核留在 Android。

### 方案 C：全直接分发（不推荐）

- Android APK + iOS 企业签/AltStore。
- 结果：Android 可用但信任/更新差；iOS 只能服务极客；企业证书随时可能被吊销。
- 不建议作为主产品策略。

## 成本与支持

- Apple Developer $99/年（Ad Hoc/TestFlight）；Enterprise $299/年（需资质）。
- 每个 Ad Hoc 设备要收集 UDID、更新 profile、重签；100 台/年上限。
- IPA 自签用户要教程、证书排障、7 天/1 年过期提醒；支持成本高。
- Android 未知来源/Play Protect/厂商限制的客服成本。
- 没有商店审核，安全/恶意篡改责任更多落在项目方；需要签名、校验和、SBOM、漏洞响应。

## 最终评估

| 目标 | 结论 |
|---|---|
| Android 不发 Play，GitHub APK 本地跑 dsh | ✅ 可行，且比上 Play 更自由 |
| iOS 不上 App Store，发 IPA 给普通用户 | ❌ 不是普通用户渠道 |
| iOS IPA + 远程 dsh 客户端 | ⚠️ 技术可行，但只适合极客/内测 |
| iOS IPA + 本地 dsh 内核 | ❌ 沙箱/JIT/子进程限制不变 |
| 开源 + 直接分发，服务技术用户 | ✅ 成立 |
| 开源 + 直接分发，服务普通用户 | ❌ iOS 是硬伤 |

**一句话：Android 可以彻底离开 Play；iOS 不能靠 IPA 离开 Apple 的签名/设备体系。IPA 能解决“不上架”，不能解决“普通用户安装”和“本地内核”。**

## 复审条件

- Apple 在 EU/其他地区开放 Web Distribution 或替代市场，且覆盖目标市场；
- Apple 允许 sideload 或 JIT/子进程（可能性低）；
- 产品明确只服务技术用户/企业，不接受普通消费者；
- Android 本地 runtime 验证失败，退回远程版；
- 企业证书/Ad Hoc 的合规路径被法务否决。
