# Android 实机审计：LGE-AN10 能否本地跑 dsh

> 说明：本文来自跨端仓库；未随本项目迁移的文档引用以纯文本保留。

> 审计方式：macOS 通过 `adb` 对已连接真机做**只读**探测（未安装任何 App、未执行 dsh 安装、未改动系统设置）。
> 目的：为[设备本地运行研究](device-local-runtime.md)提供实机事实，判断 Termux / Cortex / PRoot 三条路线在本机的真实阻塞点。
>
> 结论速览：
> - **设备本身完全够用**：8 核 arm64、6.9 GiB RAM、159 GB 可用空间、网络通。
> - **Termux 已装且可跑**（官方 GitHub debug 构建，0.119.0-beta.2，targetSdk 28），但**没有 Node**。
> - **可安装 Node 24**：Termux 上游现为 `nodejs-lts` 24.22.2 / `nodejs` 26.13.0；但本机 apt 源（清华 TUNA）返回 403，**换源后才能装**。
> - **最大硬阻塞不是 Node，而是原生模块与内核能力**：dsh 的 `flock` 原生插件在 Android bionic 上缺 `__errno_location`；`landlock` 沙箱在本机内核（5.10）返回 `ENOSYS`。
> - **推荐下一步**：先在 Termux 直跑验证核心（最快），再用 **PRoot Debian（本机已装）**提供 glibc 以满足原生模块；**Cortex 不装**。
>
> **后续实测（2026-11-08，已完成）**：Termux 直跑在启动阶段失败，报 `No usable native binding found for node-addon-require-builtin-android-arm64`；dsh 没有 Android native binding。改用 **PRoot Debian + 官方 Linux Node 24** 后，dsh 0.2.2-rc.1 成功启动，token 换 cookie，Android 浏览器加载完整 UI（截图见 [dshd-android/evidence/android-dsh-ui.png](../../../dshd-android/evidence/android-dsh-ui.png)）。**结论：Android 本地版必须带 glibc/Linux 用户态，不能只靠 Termux bionic。**

---

## 1. 设备与系统事实

| 项目 | 值 |
|---|---|
| 机型 / 品牌 | LGE-AN10 / HONOR（device `HNLGE`，board `taro`） |
| Android / SDK | 12 / 31 |
| 构建 | `6.0.0.121ENTC143`，`user` + `release-keys` |
| 安全补丁 | **2022-08-01**（约落后 4 年） |
| 内核 | `5.10.66-android12-9-g6aed817a23b0` aarch64 |
| ABI | `arm64-v8a,armeabi-v7a,armeabi`（64 位优先） |
| CPU | 8 核 |
| RAM | 7,259,836 kB（≈6.9 GiB），可用 ≈2.87 GiB，Swap 5.4 GiB |
| 存储 `/data` `/sdcard` | 211 G 总 / 已用 52 G / **可用 159 G** |
| SELinux | `Enforcing`（`/sys/fs/selinux/enforce = 1`） |
| 调试状态 | `ro.debuggable=1`、`ro.secure=0`、`verifiedbootstate=orange` |
| adb shell 身份 | **`uid=0(root)`，`context=u:r:su:s0`**（即 adbd 已是 root） |
| 已装应用数 | 253 |

关键含义：**bootloader 已解锁、adbd 为 root**，因此“权限不足”不是问题；反过来这也意味着该机不适合存放真实凭证（见第 8 节）。

```bash
adb shell getprop ro.product.model            # LGE-AN10
adb shell getprop ro.build.version.security_patch  # 2022-08-01
adb shell getprop ro.product.cpu.abilist      # arm64-v8a,armeabi-v7a,armeabi
adb shell getenforce                          # Enforcing
adb shell id                                  # uid=0(root) ... u:r:su:s0
adb shell df -h /data                         # 211G 52G 159G 25%
```

---

## 2. 终端 / Linux 相关应用盘点

`pm list packages` 共 253 项，按终端相关关键词过滤后**只有 Termux 一个**：

| 包名 | 状态 | 说明 |
|---|---|---|
| `com.termux` | ✅ 已装 | Termux 本体 |
| `com.termux.api` | ❌ 未装 | 无 termux-api（取电池/剪贴板等命令不可用） |
| `com.ssfuisu.cortex` | ❌ 未装 | Cortex 未安装 |
| `io.userland.*` / `com.pd.linuxdeploy` | ❌ 未装 | UserLAnd / Linux Deploy 未安装 |
| iSH | ❌ | iOS 专用，Android 无对应 |

```bash
adb shell pm list packages | wc -l                     # 253
adb shell pm list packages | grep -iE 'termux|cortex|userland|proot|node'   # 仅 com.termux
adb shell pm path com.termux
# package:/data/app/~~UKXeA7.../com.termux-L3wMX8WjRPFDkLzmVIEb4A==/base.apk
```

---

## 3. Termux：已装、可跑，但没有 Node

| 项目 | 值 |
|---|---|
| 版本 | `0.119.0-beta.2`（versionCode 1021，**beta**） |
| targetSdk / minSdk | **28** / 24 |
| 首次安装 / 上次更新 | 2025-03-22 / 2025-04-23 |
| 签名证书 SHA-256 | `b6da0148…48ee5e1` |
| 签名来源 | **与官方 `app/testkey_untrusted.jks` 逐字节一致**（Termux 官方 GitHub debug 构建） |
| prefix 状态 | 完整：dpkg 记录 **257** 个包，`usr/bin` 有 1215 个文件 |
| 已装工具链 | `clang`、`gcc`、`make`、`cmake`、`python 3.12.10`、`git`、`openssl`、`proot`、`proot-distro` |
| `node` / `npm` / `pnpm` | **全部不存在** |
| 运行态 | `am start` 后进程存在（PID 18694），Activity 已 resumed |

**targetSdk 28 是本机能跑 Linux 用户态的根本原因**：Android 10+ 对 `targetSdk>=29` 的应用禁止在其私有可写目录 `execve`；Termux 保持 28 才豁免。实测 `bash` 可直接执行：

```bash
# 以 Termux 自身 uid 执行（Debuggable App 可用 run-as）
adb shell 'run-as com.termux /data/data/com.termux/files/usr/bin/bash -c "echo OK; id; uname -m"'
# OK / uid=10201(u0_a201) ... / aarch64

# 以 root + Termux prefix 执行
adb shell 'export PREFIX=/data/data/com.termux/files/usr LD_LIBRARY_PATH=$PREFIX/lib PATH=$PREFIX/bin;
           $PREFIX/bin/bash -lc "command -v node npm pnpm; bash --version | head -1"'
# （无 node/npm/pnpm 输出） / GNU bash, version 5.2.37(1)-release
```

> 备注：prefix 有轻微损坏——`dpkg-query` 缺失，`dpkg -l` 会报 `unable to execute dpkg-query`，但 `status` 数据库（88 KB）完好，可继续 `pkg install`。

---

## 4. 存储与 `adb push`

`/sdcard/Download` 存在且可写，`adb push` 往返验证通过（验证后已删除探针文件，未留残留）。

| 路径 | 总 / 已用 / 可用 |
|---|---|
| `/data`、`/sdcard`（同一 `/dev/block/dm-13` / FUSE） | 211 G / 52 G / **159 G** |
| `/sdcard/Download` | 存在，内容较多（历史截图/XML/书籍样本），可写 |

```bash
echo probe > /tmp/p.txt
adb push /tmp/p.txt /sdcard/Download/dshd_probe.txt   # 1 file pushed
adb shell 'cat /sdcard/Download/dshd_probe.txt'        # 内容一致
adb shell 'rm -f /sdcard/Download/dshd_probe.txt'      # 已清理
```

结论：存储**不是**阻塞项（dsh + node_modules 即使按 1 GB 量级也仅占 0.6%）。

---

## 5. 设备网络出口

| 目标 | 结果 |
|---|---|
| `ping github.com` | 2/2 收到，**98 ms** |
| `https://github.com` | 200，但**吞吐很低**（首页 287 KB / 15 s ≈ 19 KB/s） |
| GitHub Release 资产 | **≈8.5 KB/s**（2 MB / 240 s，未下完） |
| `https://nodejs.org/dist/…linux-arm64.tar.xz` | 206，**≈1.09 MB/s**（8 MB / 7.4 s） |
| `https://registry.npmjs.org/@deepseek-ai/dsh` | 200，**≈86 KB/s** |
| 本机 apt 源 `mirrors.tuna.tsinghua.edu.cn/termux` | **403**（`InRelease`、`pool/*.deb` 全部 403） |
| `packages.termux.dev`（官方） | 206 可用，但慢（≈620 KB / 60 s） |

**这说明两件事**：① 网络本身通，`nodejs.org` 速度良好，适合直接取官方 Node 24 tarball；② **GitHub 与 npm 官方源偏慢**，且**当前配置的清华镜像已对 Termux 仓库返回 403**——`pkg install` 现在必然失败，必须先换源。

---

## 6. Node 24 可用性

| 来源 | 版本 | 说明 |
|---|---|---|
| 本机 apt 缓存索引（2025-04-27） | `nodejs` 23.11.0 / `nodejs-lts` 22.15.0 | `apt-cache policy` 确认，**没有 24** |
| Termux 上游 `packages/nodejs/build.sh` | **26.13.0** | 当前 main |
| Termux 上游 `packages/nodejs-lts/build.sh` | **24.22.2** | 满足 dsh 要求 |
| nodejs.org 官方 | 有 `linux-arm64` glibc 构建 | 可在 PRoot 内用；**不能在 bionic 直接跑** |

dsh 侧版本门槛（来自 npm registry 元数据）：

| dsh 版本 | `engines.node` |
|---|---|
| `0.2.1-rc.5`（本机 npx 缓存跑的这个） | 未声明 |
| `0.2.2-alpha.2` 起 ~ `0.2.2-rc.1`（latest） | **`>=24.9.0`** |

→ 结论：**Node 24 拿得到**，`nodejs-lts 24.22.2` 或官方 tarball 均满足 `>=24.9.0`；难点在安装通道（403）与原生模块，不在版本。

---

## 7. 真正的阻塞项：原生模块与内核能力

### 7.1 `flock` 原生插件在 bionic 上不可用（最硬的阻塞）

dsh 的会话写锁来自 `@deepseek-ai/node-addon-system`（被 `dsh-session-persistence-jsonl` 直接 import）。其平台包 `…-linux-arm64` 内含两份二进制：`bin/glibc/system.node` 与 `bin/musl/system.node`。选择逻辑是：

```js
// node-addon-system/lib/flock.js
if (platform === 'linux') {
  const report = process.report.getReport();
  filename = join(report.header.glibcVersionRuntime ? 'glibc' : 'musl', 'system.node');
}
```

Android **既不是 glibc 也不是 musl**（是 bionic），且 `process.report` 没有 `glibcVersionRuntime` 字段 → 走 **musl** 分支。实测差异：

| 二进制 | 依赖 | 在 Android 上的结果 |
|---|---|---|
| `musl/system.node` | 需要 `__errno_location` | **bionic 未导出该符号**（只有 `__errno`）→ 绑定失败 |
| `glibc/system.node` | `DT_NEEDED: libc.so.6` | `dlopen failed: library "libc.so.6" not found` |

符号级核对（`llvm-readelf --dyn-syms` 对比 `/system/lib64/libc.so`）：该插件除 N-API 外的强未定义符号中，`calloc`/`free`/`flock`/`__stack_chk_fail`/`__stack_chk_guard`/`__cxa_finalize` 在 bionic 全部存在，**唯一缺失的是 `__errno_location`**。

```bash
# 在设备上实测 dlopen（bionic）
adb shell '/data/local/tmp/dlprobe /data/local/tmp/system-musl.node'
#  -> cannot locate symbol "napi_get_undefined" ...（裸进程无 N-API；真实 Node 进程里仅剩 __errno_location 缺口）
adb shell '/data/local/tmp/dlprobe /data/local/tmp/system-glibc.node'
#  -> library "libc.so.6" not found
```

**影响**：Termux（bionic）直跑 dsh 时，会话持久化写锁会失败；这不是“缺功能”，而是会话写入路径可能直接报错。**PRoot/Cortex 的 glibc 用户态可绕过**（`glibcVersionRuntime` 存在 → 走 `glibc/system.node`）。

### 7.2 Landlock 沙箱不可用（内核级）

`@deepseek-ai/node-addon-system-*-linux-arm64` 还带 `bin/landlock-run`（静态链接），dsh 的 sandbox launcher 依赖它。Landlock 于 Linux **5.13** 合入，本机内核 **5.10.66**。用 10 行 C 探针实测系统调用：

```bash
adb shell '/data/local/tmp/llprobe'
# landlock_create_ruleset: FAILED errno=38 (Function not implemented)   ← ENOSYS
```

→ **沙箱不可用**，只能以 `danger-full-access` / 关闭 sandbox 的方式运行（与当前桌面会话模式一致）。这是**功能性降级**，不是启动阻塞。

### 7.3 其它原生依赖无 Android 预编译

| 依赖 | 用途 | Android 预编译 | 影响 |
|---|---|---|---|
| `node-pty` | 终端 PTY 工具 | ❌（只有 darwin/win32/linux-gnu） | PTY 终端不可用，需源码编译或改用 bash 工具 |
| `sharp` + libvips | 图片处理 | ❌（glibc/darwin） | 图片能力缺失，需系统 libvips 自建 |
| `sherpa-onnx` | 语音输入（experimental） | ❌ | 相关 bundle 不可用 |

本机 macOS 侧 `node_modules` 为 **844 MB**（含各平台变体），设备侧只需 linux-arm64，但仍需处理上述 3 个原生包的构建。

### 7.4 后台存活：本机已调优，风险可控

| 项目 | 值 |
|---|---|
| `max_phantom_processes` | **32**（`max_cached_processes` 32 / `CUR_MAX_CACHED` 128） |
| Termux 电池优化 | **已在 `deviceidle whitelist`**（`user,com.termux,10201`） |
| App standby bucket | **5（EXEMPTED）** |
| 进程状态 | `stopped=false notLaunched=false enabled=0` |

→ 已做过电池白名单，长时间跑 node 服务可行；但 Android 12 的 phantom process killer 仍会在子进程数超过 32 时杀进程，建议前台服务 + 唤醒锁。

---

## 8. 已具备的替代路线：PRoot Debian

`proot-distro` 已装，且**Debian 12.10 rootfs 已存在**（`usr/var/lib/proot-distro/installed-rootfs/debian`，bookworm，glibc 2.36），登录实测可用：

```bash
adb shell 'run-as com.termux env PREFIX=/data/data/com.termux/files/usr ... \
  /data/data/com.termux/files/usr/bin/proot-distro login debian -- cat /etc/debian_version'
# 12.10
```

rootfs 内**尚未安装 Node**。这条路线的价值：提供真正的 glibc 用户态，`glibc/system.node`（flock）与 nodejs.org 官方 arm64 构建都能直接工作，从而一次性解决 7.1 与 7.3 中的多数问题；代价是 ptrace 拦截带来的性能开销，以及 PTY/信号在 proot 下的额外不确定性。

---

## 9. 阻塞项清单（按优先级）

| 级别 | 阻塞项 | 现状 | 解除方式 |
|---|---|---|---|
| **P0** | apt 源 403（清华 TUNA） | 所有仓库路径 403 | 换到 `packages.termux.dev` 或其它镜像后 `pkg update` |
| **P0** | 无 Node | `node`/`npm`/`pnpm` 均无 | 装 `nodejs-lts`（24.22.2）或官方 arm64 tarball |
| **P0** | `flock` 原生插件与 bionic 不兼容 | `__errno_location` 缺失 | 用 PRoot/Cortex 的 glibc；或 patch `flock.js` 回退 `O_EXCL` 锁文件 |
| P1 | Landlock 沙箱不可用 | 内核 5.10，`ENOSYS` | 以 `danger-full-access` 运行（放弃沙箱） |
| P1 | `node-pty` / `sharp` / `sherpa-onnx` 无 Android 预编译 | 缺预编译产物 | 源码编译（Termux 有 clang/python）或降级功能 |
| P2 | 安全补丁 2022-08、bootloader 解锁、adbd root | 见第 1 节 | 仅作实验机，不存凭证 |
| P2 | Termux 用官方 **公开 testkey** 签名（`untrusted`） | 任何人可伪造升级包 | 仅从可信渠道更新；产品化需自签/自有分发 |
| P2 | Termux targetSdk 28 | 结构性限制 | 无法上 Google Play，只能 sideload/企业分发 |

---

## 10. 推荐的下一步实验

**顺序：Termux 直跑（快） → PRoot Debian（全） → 再谈 Cortex。** 三者都**不**在本轮执行完整 dsh 安装。

### 阶段 A：Termux 直跑（验证 dsh 核心是否能在 bionic 上启动）

1. 换源并刷新（清华镜像已 403）：
   `sed -i 's#mirrors.tuna.tsinghua.edu.cn/termux#packages.termux.dev/apt#g' $PREFIX/etc/apt/sources.list` → `pkg update`
2. `pkg install nodejs-lts` → 期望 `node -v` = **v24.22.2**（≥24.9.0）
3. 用 npm 镜像装 dsh（官方源仅 ≈86 KB/s）：`npm i -g @deepseek-ai/dsh@0.2.2-rc.1 --registry=https://registry.npmmirror.com`
4. 关闭 sandbox 启动，验证 `127.0.0.1:3080` 可被设备浏览器打开（`dsh web` / headless）
5. **重点观察**：启动是否因 `flock` 失败；若失败，改 patch `flock.js` 为 `fs.open(path,'wx')` 回退，记录可行性
6. 记录：冷启动时间、RSS、CPU、是否触发 phantom 限制

### 阶段 B：PRoot Debian（补齐 glibc，验证原生模块）

1. `proot-distro login debian` → 装 Node 24 官方 arm64 tarball（nodejs.org 1.09 MB/s，可用）
2. 同版本装 dsh，验证 **`flock`（`glibc/system.node`）能否正常加载**、`node-pty` 能否源码编译
3. 对比 A/B：启动耗时、内存、会话写入稳定性、PTY 可用性（proot 下 `/dev/ptmx` 行为需实测）

### 阶段 C：Cortex

- **本轮不安装**（未验证来源，且任务明确禁止）。
- 仅当 A/B 均失败、且目标是“产品化嵌入”时再评估；其 Apache-2.0 许可比 Termux GPLv3 更适合嵌入，但需要独立供应链与合规审计。

### 判定标准（Gate）

- A 阶段能跑通“启动 + 一次 bash 工具调用 + 会话落盘”，即证明**功能可行**；
- B 阶段能稳定处理 `flock`，即证明**可用于长期使用**；
- 两者都过 → Android 本地内核可进入 spike；任一不过 → 回退到远程 Host 方案。

---

## 11. 风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| 设备安全基线低 | 补丁停在 2022-08、bootloader 解锁、adbd 常驻 root | 只做实验机；不登录真实账号/不放凭证 |
| 供应链 | Termux 用**公开 testkey** 签名，任何人有私钥可伪造更新 | 固定 APK 哈希、用自建签名分发；产品化不复用该渠道 |
| 许可 | Termux 为 GPLv3；proot 为 GPLv2 / proot-distro GPLv3；Cortex 为 Apache-2.0 | 闭源产品嵌入 GPL 组件需法务设计；Cortex 更友好但需 fork 维护 |
| 分发 | targetSdk 28 → 不能上 Google Play | 只能 GitHub/官网 APK、企业 MDM（见 [ADR-0006](../adr/0006-direct-apk-ipa-distribution.md)） |
| 性能/功耗 | proot syscall 拦截、Node + dsh 常驻、旧 SoC | 实测启动与 RSS；长任务用前台服务 + 通知 |
| 后台被杀 | phantom process killer 上限 32 | 已加电池白名单；仍需前台服务+唤醒锁 |
| 安全面 | 本地 shell Agent 可读写手机文件、执行命令 | 默认审批、工作区白名单、禁用未知插件、限制网络出口 |
| 提示注入 | 设备上更容易碰到不可信文本 | 保持审批与工具白名单，不默认全盘访问 |

---

## 12. 本轮审计边界（做了什么 / 没做什么）

- **做了**：只读探测（`getprop`/`pm`/`df`/`dumpsys`/`settings`）、Termux 执行与启动、`adb push` 往返、网络测速、APK 签名核验、原生插件 ELF/符号分析、Landlock syscall 探针、proot 登录验证。
- **没做**：未安装任何 App（含 Cortex、Termux 新版）；未执行 `pkg install` / `npm install`；未改系统设置；未 factory reset；未安装 dsh 到设备。
- **清理**：`/sdcard/Download/dshd_probe.txt`、`/data/local/tmp/*` 探针与临时 APK 均已删除；Termux 应用被启动一次（验证可运行），仍在前台。
- **暂停项**：`packages.termux.dev` / GitHub 资产下载过慢（≈8.5–10 KB/s），未强行拉取完整索引或 APK；签名已通过与官方 keystore 比对确认，无需下载整包。
