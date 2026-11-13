# GitHub 发布物料清单（v0.3.2）

本清单按“公开仓库 + GitHub Release”整理。当前仓库**没有配置 GitHub remote**，以下
“⬜”项需要在真正发布前完成；不要为了填清单而提交 keystore、密码或 `dist/`。

## 1. 仓库基础信息

| 物料 | 状态 | 说明 |
| --- | --- | --- |
| 仓库名 | ⬜ | 建议 `dshd-android`，与包名/文档一致 |
| 一句话描述 | ⬜ | `DeepSeek Harness Desktop for Android — a local AI workstation in your pocket.` |
| Topics | ⬜ | `android`, `deepseek`, `ai`, `local-first`, `webview`, `rootfs`, `open-source` |
| 默认分支 | ⬜ | `main` |
| Social preview | ✅ 已生成 | `docs/assets/social-preview.png`（1280×640，上传到仓库设置） |
| README 顶部预览 | ✅ | `docs/assets/social-preview.png` |
| 英文 README（默认） | ✅ | `README.md` |
| 中文 README | ✅ | `README.zh.md` |

## 2. 法律与第三方

| 物料 | 状态 | 说明 |
| --- | --- | --- |
| LICENSE | ✅ | Apache-2.0 |
| NOTICE | ✅ | 仓库与二进制再分发说明 |
| 第三方清单 | ✅ | `docs/third-party.md` |
| rootfs 内 Ubuntu/Node 许可 | ✅ | 见 `NOTICE`、`docs/third-party.md`；发布二进制时保留 |
| 贡献指南 | ✅ | `CONTRIBUTING.md` |
| 安全策略 | ✅ | `SECURITY.md` |
| CODE_OF_CONDUCT | ⬜（可选） | 公开协作建议补 `CODE_OF_CONDUCT.md` |
| 商标/非官方声明 | ✅ | README/NOTICE 已说明非 DeepSeek 官方应用 |

## 3. 发布产物

只上传需要的文件，不要把 `dist/` 全目录或中间 APK 上传。

| 产物 | 状态 | 说明 |
| --- | --- | --- |
| `dshd-android-release.apk` | ✅ 本地已构建 | 内置 rootfs，约 144 MB；最终重建后更新哈希 |
| `dshd-android-release-lite.apk` | ✅ 本地已构建 | 无 rootfs，约 856 KB，首次启动需下载/导入 |
| rootfs 归档 | ✅ 本地已有 | 仅在需要单独分发/校验时上传；约 145 MB xz |
| `SHA256SUMS` | ⬜ 需重新生成 | 必须对**最终** APK/rootfs 生成；见下方命令 |
| 签名证书指纹 | ✅ 已记录 | SHA-256 见 `docs/release.md`；发布说明可附 |
| 发布 keystore 离线备份 | ⬜ **阻塞项** | 当前只有本机 `$HOME/.dshd/`，无离线备份；丢失后无法覆盖升级 |
| 版本号/tag | ⬜ | `0.3.2` / `v0.3.2`；versionCode 5 |
| Release notes | ✅ | `docs/release-notes-0.3.2.md`，发布时粘贴/调整 |

生成校验和：

```bash
cd dist
shasum -a 256 dshd-android-release.apk dshd-android-release-lite.apk \
  dshd-rootfs-*.tar.xz > SHA256SUMS
```

发布前必须确认 `SHA256SUMS` 里的文件就是上传的那一份；不要使用旧构建的哈希。

## 4. 文档与验收

| 物料 | 状态 | 说明 |
| --- | --- | --- |
| CHANGELOG | ✅ | `CHANGELOG.md` |
| 架构说明 | ✅ | `docs/architecture.md` |
| 打包/签名 | ✅ | `docs/packaging.md`、`docs/release.md` |
| 权限说明 | ✅ | `docs/permissions.md` |
| 测试说明 | ✅ | `docs/testing.md` |
| 真机验收报告 | ✅ | `evidence/android-acceptance-0.3.2.md` |
| 启动性能报告 | ✅ | `docs/startup-performance.md` |
| 首次使用演示 | ✅ 可重建 | 中英文分离的 GIF/MP4 由 `demo/build.sh` 生成；大文件不入库 |
| YouTube 宣传片 | ✅ 可生成，待上传 | 见 `docs/youtube-promo.md`；缩略图/SRT 在库，MP4 本地生成 |
| 已知限制 | ✅ | 发布说明/验收报告已列（PDF 文本不可选、Host UI 未全本地化等） |

## 5. CI、模板与仓库治理

| 物料 | 状态 | 说明 |
| --- | --- | --- |
| GitHub Actions | ✅ | `.github/workflows/android.yml` 构建 lite debug APK |
| Issue 模板 | ✅ | bug / feature |
| PR 模板 | ✅ | `.github/pull_request_template.md` |
| 分支保护 | ⬜ | 要求 CI 通过；禁止直接 push main |
| Release 权限 | ⬜ | 只给维护者；release 资产不可被 PR 修改 |
| Dependabot | ⬜（可选） | Gradle/npm/GitHub Actions |
| Secret scanning | ⬜ | 开启 GitHub secret scanning / push protection |

## 6. 安全与隐私检查（发布前必须做）

- [ ] `git status` 干净，只有预期文件。
- [ ] `git log -p` / `gitleaks` 检查历史中没有 keystore、`*.password`、Provider key、token。
- [ ] `dist/`、`demo/node_modules/`、`demo/frames/`、`demo/youtube/frames/`、`*.jks` 均在 `.gitignore`。
- [ ] APK 中不含发布 keystore；`assets/` 只含 rootfs 和前端资源。
- [ ] `docs/release.md` 说明 keystore 备份与轮换流程，但**不包含密码**。
- [ ] 发布说明明确：运行时/文件在本地；提示词和文件上下文会发往用户配置的模型端点。
- [ ] 首次启动解包 1–3 分钟、非 Play 商店分发、targetSdk 28 的兼容性边界已说明。

## 7. GitHub Release 发布步骤

1. 用**最终代码**重建全量 APK 和 lite APK；确认 `versionName=0.3.2`、`versionCode=5`。
2. 跑 `scripts/check-release-readiness.sh dist/dshd-android-release.apk`，全部 PASS。
3. 在真机跑 `scripts/android-e2e.sh`，4/4 PASS；保存新的验收截图（如启动覆盖层有变化）。
4. 生成最终 `SHA256SUMS`，更新 `docs/release-notes-0.3.2.md` 的表格。
5. 提交并打 tag：`git tag -s v0.3.2 -m "DSHD for Android 0.3.2"`（如配置了 GPG）。
6. 在 GitHub 创建 Release：
   - tag `v0.3.2`，标题 `DSHD for Android 0.3.2`;
   - 正文使用 `docs/release-notes-0.3.2.md`；
   - 上传全量 APK、lite APK、`SHA256SUMS`（可选 rootfs）；
   - 勾选 “Set as the latest release”。
7. 下载刚上传的资产，重新计算 SHA-256，确认与正文一致。
8. 更新 README/YouTube 描述中的 Release 链接；发布公告/置顶评论。

## 8. 当前阻塞项

1. **签名 keystore 没有离线备份**。这不是“物料缺失”，而是发布风险：一旦本机损坏，
   已安装用户无法覆盖升级。先备份到离线介质/密码管理器，再公开可升级的 release。
2. **没有 GitHub 仓库地址**。YouTube 描述、README 徽章、Release 链接都只能留占位符；
   需要先确定账号/组织名与仓库 URL。
3. **YouTube 成片/缩略图/metadata 尚未上传**。文件已生成，仍需人工上传、设置缩略图/
   字幕/结束画面，并替换描述中的仓库链接。
