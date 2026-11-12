# rootfs 体积审计

## 当前结果

| 版本 | 压缩 | 解包 | 说明 |
|---|---:|---:|---|
| fixed17 | 506 MB gzip | 1.7 GB | 含测试缓存 |
| fixed18 | 276 MB gzip | 1.05 GiB | 剔除 npm/apt 缓存 |
| fixed18-xz | 150 MB xz | 1.05 GiB | 同内容，xz -6 |
| fixed20-strip | 143 MB xz | 1.00 GiB | 再 strip Node、删非 arm64 预编译 |
| fixed22-nativepdf | 143 MB xz | 1.00 GiB | fixed20-strip + Chromium 92 客户端补丁，0.3.2 默认内置 |
| fixed20-clean | 132 MB xz | 0.84 GiB | 再删 source map/类型/测试/文档（已验证） |

## APK 内置后的体积

0.3.2 默认把 fixed22-nativepdf xz 放进 `assets/rootfs.tar.xz`：

- APK：**144 MB**（xz 原样存储，不再二次压缩）；
- rootfs：143 MB xz / 约 1.00 GiB 解包；
- 不内置 rootfs 的 lite APK：**856 KB**；
- 首次安装设备峰值约 2 GB（APK + 解包树 + 临时树）。

## 已做的两件事

### 1. strip Node

Node 24 自带调试符号：

```text
opt/node24/bin/node  118 MB → 99 MB
```

使用 `strip --strip-unneeded`，不改变运行逻辑。真机替换后 Host 启动正常，
`scripts/android-e2e.sh` 4/4 通过。

### 2. 删除非 arm64 预编译

主要删除：

- `node-pty/prebuilds/win32-x64`、`win32-arm64`、`darwin-x64`、`darwin-arm64`、`linux-x64`；
- `node-pty/third_party/conpty`（Windows ConPTY）；
- `pnpm/dist/reflink.{darwin,win32}*.node` 和 `fastlist-*-x64.exe`。

保留 `node-pty/prebuilds/linux-arm64` 和 pnpm 的 arm64 路径。容器内用 arm64
glibc 验证过 `node-pty` 能启动 `/bin/sh`，`pnpm --version` 正常。

## 基础包审计

以下大小来自 `/tmp/dshd-rootfs-strip`：

| 文件/目录 | 大小 | 结论 |
|---|---:|---|
| `opt/node24/include` | 64 MB | 仅编译 native addon 需要；运行不需要 |
| `usr/include` | 12 MB | C/C++ 头文件；仅编译需要 |
| `usr/lib/**/*.a` | ~7 MB | 静态库；运行时不需要 |
| npm `docs`/`man` | 3 MB | 文档；可删 |
| `usr/share/doc`、`man`、`info` | ~7 MB | 文档；可删 |
| node_modules `*.map` | 48 MB | source map；仅调试需要 |
| node_modules `*.d.ts` | 46 MB | TypeScript 类型；运行不需要 |
| node_modules `test/tests/__tests__` | ~95 MB | 测试；运行不需要 |
| `usr/share/lintian`、`gitweb`、perl 文档 | ~1 MB | 可删 |
| `usr/share/zoneinfo` | 3.7 MB | 时区；建议保留 |
| `usr/bin/git`、`perl`、`openssh`、`curl`、`bash` | ~20 MB | agent 能力，保留 |
| `var/lib/apt`、`var/lib/dpkg` | ~62 MB | apt/dpkg 功能，保留 |
| `opt/node24/lib/node_modules/npm` | 16 MB | npm 功能，保留 |
| `opt/node24/lib/node_modules/pnpm` | 20 MB | 插件/包管理，保留 |
| `libreoffice-kit-wasm` | 145 MB | Office 预览功能，保留 |
| `sherpa-onnx-linux-arm64` | 38 MB | 语音功能，保留 |

### 重要结论

- 删 `source map + .d.ts + tests + docs` 可再省约 11 MB 压缩体积（150 → 132 MB）；
- 删 `opt/node24/include + usr/include + *.a` 只再省约 **3 MB** 压缩体积，因为头文件压缩率很高，但会破坏“在本机编译 native 模块”的能力，不建议默认做；
- 删 git/perl/openssh/apt/npm/pnpm 能省几十 MB，但会删 agent 的真实能力，不做。

## 可复现命令

```bash
# macOS 没有 aarch64 strip，可用 arm64 Docker 先 strip：
ROOTFS=/tmp/dshd-rootfs
docker run --rm --platform linux/arm64 \
  -v "$(dirname "$ROOTFS/opt/node24/bin/node"):/out" \
  docker.m.daocloud.io/library/debian:bookworm-slim \
  sh -c 'apt-get update >/dev/null && apt-get install -y --no-install-recommends binutils >/dev/null && strip --strip-unneeded /out/node'

# 删除非 arm64 预编译（可选 --clean 再删 map/类型/测试/文档）
scripts/slim-rootfs.sh "$ROOTFS" --clean

# 打包
scripts/pack-rootfs.sh "$ROOTFS" dist/dshd-rootfs-fixed22-nativepdf.tar.xz
```

## 推荐

- **正式发布：fixed22-nativepdf（143 MB）**，含原生 PDF 桥补丁；
- fixed20-clean（132 MB）是历史低带宽包，不含原生 PDF 桥，需重新合入补丁后才能用于 0.3.2；
- 不要为了几 MB 删 Node 头文件/静态库，除非明确不需要本地编译 native 模块；
- 若要低于 120 MB 且保留 Office/语音/agent 全部能力，下一步只能做模块化 rootfs 或远端 Host。
