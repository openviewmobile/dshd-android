# YouTube 宣传物料（DSHD for Android 0.3.2）

本页给出可直接复制到 YouTube 的标题、简介、标签、章节和上传检查项。中英文成片
**分开**，不要混用字幕/旁白。

## 成片文件

| 语言 | 文件 | 规格 |
| --- | --- | --- |
| English | `docs/assets/youtube/dshd-promo-en.mp4`（本地生成，未入库） | 1920×1080，30 fps，H.264/AAC，约 31 秒 |
| 中文 | `docs/assets/youtube/dshd-promo-zh.mp4`（本地生成，未入库） | 1920×1080，30 fps，H.264/AAC，约 31 秒 |
| English thumbnail | `docs/assets/youtube/dshd-promo-en-thumb.jpg` | 1280×720，< 2 MB |
| 中文 thumbnail | `docs/assets/youtube/dshd-promo-zh-thumb.jpg` | 1280×720，< 2 MB |
| English captions | `docs/assets/youtube/dshd-promo-en.srt` | 可选上传 |
| 中文字幕 | `docs/assets/youtube/dshd-promo-zh.srt` | 可选上传 |

视频均为 `+faststart`，适合网页/移动端直接播放。英文版用于频道主推；中文版用于中文
受众，不建议把两版合并成一个视频。

## English upload

**Title**

```text
DSHD — DeepSeek Harness Desktop on Android
```

**Description**

```text
DSHD puts a real AI workstation on your Android phone.

DeepSeek Harness Desktop combines a built-in Linux runtime, the DeepSeek Harness web UI, a file workspace, and a terminal — without tethering to a PC. Connect any OpenAI-compatible model endpoint; the runtime and workspace stay in app-private storage.

WHAT YOU GET
• Built-in runtime — one-time unpack, then open the workspace
• Bring your own model — OpenAI-compatible endpoint, key stored on device
• Files & preview — browse the workspace and read documents in the app
• Real terminal — local commands with live output
• Native PDF preview for Chromium 92
• Apache-2.0, source available

DOWNLOAD / SOURCE
GitHub: [GITHUB_REPO_URL]
Release: [RELEASE_URL]

REQUIREMENTS
Arm64 Android 9+ (API 28), Android System WebView, about 2 GB free storage. This is a sideloaded APK, not a Google Play app. First launch unpacks the runtime (typically 1–3 minutes).

PRIVACY
The runtime and workspace are stored locally on the device. Prompts and any file context you send are transmitted to the model endpoint you configure; review that provider's terms.

PDF pages are rendered natively, so text selection inside PDFs is not available. DSHD is an independent project; DeepSeek Harness and third-party components keep their own licenses.

#Android #DeepSeek #LocalAI #AIWorkstation #OpenSource
```

**Tags** (paste comma-separated, keep under 500 chars)

```text
DSHD, DeepSeek Harness, DeepSeek Harness Desktop, DeepSeek Android, Android AI, local AI, AI workstation, OpenAI compatible, Android terminal, WebView, ARM64, open source Android, developer tools
```

**Chapters** (YouTube requires ≥3 chapters, each ≥10 seconds)

```text
00:00 Overview
00:10 Runtime and model setup
00:20 Workspace, terminal, and privacy
```

**Category / language**

- Category: Science & Technology
- Video language: English
- Caption language: English (upload SRT or use YouTube captions)
- Visibility: Public
- Playlist: `DSHD — Android`

## 中文上传

**标题**

```text
DSHD：把完整 AI 工作站装进 Android（DeepSeek Harness Desktop）
```

**简介**

```text
DSHD 把一套完整的 AI 工作站装进 Android 手机。

DeepSeek Harness Desktop 在手机本地运行内置 Linux 运行时、DeepSeek Harness Web UI、文件工作区和终端，不需要连一台电脑。你可以接入任意兼容 OpenAI 接口的模型服务；运行时和工作区保存在应用私有存储中。

包含
• 内置运行时：首次解包一次，之后直接打开工作区
• 接入自己的模型：兼容 OpenAI 接口，密钥保存在设备上
• 文件与预览：在应用内浏览工作区、阅读文档
• 真正的终端：本地执行命令，实时查看输出
• Chromium 92 下的原生 PDF 预览
• Apache-2.0 开源

下载 / 源码
GitHub：[GITHUB_REPO_URL]
Release：[RELEASE_URL]

要求
arm64 Android 9+（API 28）、Android System WebView、约 2 GB 可用空间。APK 为侧载安装，不上架 Google Play。首次启动需要解包运行时，通常 1–3 分钟。

隐私
运行时和工作区保存在设备本地。你发送的提示词和文件上下文会传输到你配置的模型服务端点，请阅读该服务商的条款。

PDF 页面由原生渲染，因此 PDF 内文字暂不支持选择/复制。DSHD 是独立项目；DeepSeek Harness 及第三方组件遵循各自许可证。

#Android #DeepSeek #本地AI #AI工作站 #开源
```

**标签**

```text
DSHD, DeepSeek Harness, DeepSeek Harness Desktop, Android AI, 本地 AI, AI 工作站, OpenAI 兼容, Android 终端, WebView, ARM64, 开源 Android, 开发工具
```

**章节**

```text
00:00 概览
00:10 运行时与模型接入
00:20 工作区、终端与隐私
```

**分类 / 语言**

- 分类：科学与技术
- 视频语言：中文
- 字幕语言：中文
- 可见性：公开
- 播放列表：`DSHD — Android`

## Shorts（可选，竖屏）

现有 9:16 演示可直接作为 Shorts：

- `docs/assets/demo-en.mp4`（English，约 25 秒，`demo/build.sh` 生成）
- `docs/assets/demo-zh.mp4`（中文，约 25 秒，`demo/build.sh` 生成）

建议标题：

```text
A full AI workstation on Android — DSHD #Shorts
```

```text
把完整 AI 工作站装进 Android — DSHD #Shorts
```

Shorts 描述里放同一个 GitHub 链接；不要在 Shorts 里使用横版宣传片的字幕。

## 缩略图

- 使用 1280×720 JPEG，文字在手机屏幕上仍可读；
- 英文缩略图主打 `A full AI workstation. On your phone.`；
- 中文缩略图主打 `完整的 AI 工作站，装进手机。`；
- 不要把 GitHub URL 塞进缩略图（无法点击，且容易过期）；用 “GitHub link in description”。

## 上传检查

- [ ] 选择对应语言版本，不要混音/混字幕。
- [ ] 上传自定义缩略图，检查 1280×720、< 2 MB。
- [ ] 上传 SRT（若使用），检查首句和结尾 CTA。
- [ ] 简介中的 `[GITHUB_REPO_URL]` / `[RELEASE_URL]` 已替换为真实链接。
- [ ] 添加结束画面（订阅/仓库链接）和卡片。
- [ ] 播放列表、频道语言、字幕语言设置正确。
- [ ] 发布后把链接回填到 README / `docs/release-notes-0.3.2.md`。
- [ ] 置顶评论放 GitHub 链接和“首次解包 1–3 分钟”的预期管理。

## 说明

- 视频旁白来自现有中英文首次上手演示；结尾 CTA 为单独生成的旁白，不包含背景音乐。
- 若要在 YouTube 编辑器里加背景音乐，请使用 YouTube Audio Library 或已授权音乐，
  避免版权索赔。
- 宣传文案避免“完全离线”“无需网络”等绝对说法：模型请求会发往用户配置的端点。
