# V2EX / 中文社区草稿

标题：我把 Ubuntu、Node 24 和 DeepSeek Harness 塞进了 Android APK

正文：

做了一个 Android 应用 DSHD：APK 内置裁剪过的 Linux 运行时、Node 24 和 dsh
Host，启动后在本机跑完整工作区，不依赖另一台电脑，也不需要 Termux。

可以用文件、PDF 预览、终端和 Agent 会话；模型接任意 OpenAI 兼容接口，API Key
用 Android Keystore 加密保存。项目开源，APK 由 GitHub Actions 构建和签名。

限制也说清楚：
- Android 9+，仅 arm64；
- APK 约 146 MB，首次解包约需 2 GB；
- 需要 sideload；
- 模型推理走你配置的接口，不是手机本地跑大模型；
- targetSdk 28 是为了 glibc 运行时。

GitHub：https://github.com/openviewmobile/dshd-android
Release：https://github.com/openviewmobile/dshd-android/releases/latest

欢迎反馈机型兼容性和安装问题。
