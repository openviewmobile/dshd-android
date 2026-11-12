# Android 验收记录（2026-11-10）

设备：LGE-AN10 / Android 12 / arm64-v8a（rooted）。release 包，固定 rootfs。
本轮从**全新安装**开始验证：卸载 → 安装 APK → 下载 506 MB rootfs → SHA-256 校验 →
解包 → Provider 首启配置 → 真实模型对话；随后在同一安装上执行功能/回归测试。

## 交付物

| 文件 | SHA-256 |
|---|---|
| `dist/dshd-android-release-project-lan-fixed19.apk` | `e5bfc531b2f5cd1bf6cbeadba0384284e911b96d77f70917feb056335f3ac1f6` |
| `dist/dshd-rootfs-arm64-webview-pnpm-appboot-mobile-fixed15.tar.gz` | `8a1d3e7eea1bb70575242b775e5b86ab5e3b9ab389fdec86483c9895c42ce6e0` |

APK 通过 `DSHD_ROOTFS_URL` / `DSHD_ROOTFS_SHA256` 指向局域网 HTTP 服务；正式发布前必须换成
HTTPS 托管地址（见“已知限制”）。

## 本轮修复

1. **WebView 附件选择器**：`MainActivity` 的 `WebChromeClient` 没有实现
   `onShowFileChooser`，`input[type=file]` 点击没有任何反应。已实现
   `onShowFileChooser` + `onActivityResult`，支持 SAF 单选/多选和 `content://` URI。
   证据：[ui-131-file-picker-fixed.png](ui-131-file-picker-fixed.png)。
2. **Host 的 DSH_HOME**：Android 上 Node `homedir()`/默认值和 app 私有目录不一致，
   `attachment-local` 把上传写到 `/data/user/0`。Host 启动环境显式设置
   `DSH_HOME=<rootfs>/home/.dsh`。
3. **附件耐久性 fsync 越界**：`attachment-local` 会把 DSH_HOME 到 `/` 的每一级祖先目录
   fsync；Android 沙箱不允许读 `/data/user/0`，导致上传 500/EACCES。现在遇到
   `EACCES/EPERM` 时停止向上 fsync，app 私有目录仍全部 fsync。
4. **桌面专用入口**：设置头的“打开配置文件”依赖桌面 opener，Android 无该处理器；
   手机上隐藏该入口（`mobile.css`），避免死按钮。
5. 保留并复验此前的移动端 CSS（预设卡片换行）、语音 worker `nodeCommand()`/429 重试、
   rootfs 内置 git 2.43.0。

## 验收矩阵

| 范围 | 结果 | 证据/说明 |
|---|---|---|
| 全新安装 / rootfs 下载、校验、解包 | ✅ | 505.7 MB 下载、20500 entries 导入、自动进入设置 |
| Provider 首启表单 + Keystore + 真实对话 | ✅ | 配置后首条 prompt 返回 `E2E-OK` |
| 重复自动化测试 | ✅ | `scripts/android-e2e.sh`，4/4 通过 |
| 会话生命周期：新建、发送、停止、重命名、归档/撤销 | ✅ | 停止后显示“已停止”；重命名 `RenamedSession` 成功；归档 toast 提供撤销/筛选 |
| 进程死亡恢复 | ✅ | 任务中 force-stop，重启后会话历史和结果完整 |
| 离线/超时 | ✅ | 断网后 5 次重试，UI 显示 `TRANSPORT / Connection error.`，网络恢复后可用 |
| 旋转 / 键盘 | ✅ | 横屏 `777×314`、竖屏恢复；键盘弹出不遮挡输入框 |
| 设置：通用、模型、内置插件、Agent 预设 | ✅ | 模型编辑器可打开；模型提供商目录/自定义设置可用；预设页显示 4 个内置模式 |
| 快捷键速查/编辑 | ✅ | 打开快捷键面板、逐项编辑入口可见；关闭按钮可用 |
| Session Log 开关 | ✅ | 开关可切换并持久化；未对官方 API 真正上传（见限制） |
| 附件：文件 | ✅ | SAF 选择 9.3 MB TXT，显示 `TXT 9.3MB`，上传成功 |
| 附件：图片 | ✅ | 1×1 PNG 经 `/api/session/uploadFileBinary?mediaType=image/png` 返回 normalized attachment |
| Agent 工具矩阵 / agent team 真实任务 | ✅ | 此前证据 `ui-120`、`ui-121` |
| 语音输入 | ✅ | 权限弹窗、录音、识别文本插入 composer；模型首次下载 240 MB（有重试） |
| 内置/第三方插件 | ✅ | whale-pet 安装/启用/悬浮层/键盘隐藏；worktree 本地创建/任务；auto-review；web search |
| worktree 本地生命周期 | ✅ | `.agents/worktrees/...` 创建、文件写入、会话运行 |
| 公开 Release | ⏳ | rootfs 仍是局域网 HTTP URL；`check-release-readiness.sh` 会拒绝 |

## 已知限制（非本轮阻塞）

- **公开分发**：rootfs 托管 URL 为 HTTP，正式发布需 HTTPS + 稳定对象存储；这是发布门禁项。
- **Session Log 上传**：开关可用，但只对官方模型 API 有意义；本设备走自建 gateway，未执行真实上传。
- **GitHub PR 跟踪**：worktree 的本地 git 生命周期已验证；设备未安装/登录 `gh`，PR 关联未测。
- **语音模型**：SenseVoice 资产不随 rootfs 分发，首次启用语音需联网下载约 240 MB（已加 429/5xx 重试）。
- **桌面专用“打开配置文件”**：Android 上隐藏；配置文件仍可通过 ADB/文件管理器编辑。

## 复跑命令

```bash
# 安装 fixed19 后，等 WebView 就绪
scripts/android-e2e.sh
# 4/4 checks passed
```

完整证据索引见 [android-acceptance-index.md](android-acceptance-index.md)。
