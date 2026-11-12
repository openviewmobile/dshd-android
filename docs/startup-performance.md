# 启动性能：测量、已做优化与后续空间

适用版本：DSHD for Android 0.3.2（versionCode 5）。  
测量设备：LGE-AN10，Android 12，Android System WebView Chromium 92，release APK。  
测量时间：2026-11-11。所有冷启动测量前均执行 `adb shell am force-stop com.dshd.android`。

## 结论

启动慢主要不是原生壳或启动动画造成的，而是两段不可避免的工作：

1. **`dsh web` Host 启动**：Node 24 + `dsh` 插件/工作区初始化，约 **6–7 秒**；
2. **WebView 加载并水合 Host UI**：首帧到 composer 可交互，约 **3 秒**。

原生 Activity 首帧只需 **0.3–0.4 秒**。因此继续压缩启动页动画的收益很小；真正有收益的是
**保持 Host 进程常驻**、减少 Host 自身初始化，以及升级/预热 WebView。应用已经使用前台
`HostKeepAliveService`，从后台回到应用（Activity 仍存活）实测 **约 0.12 秒**。

## 测量结果

| 阶段 | 冷启动（force-stop） | 说明 |
| --- | ---: | --- |
| Activity 首帧（`am start -W` TotalTime） | 0.30–0.42 s | 原生壳 + 启动覆盖层 |
| Host 可访问（`dsh web: http://127.0.0.1:...`） | 6.3–7.0 s | 从 service 启动 Host 算起；网络/插件缓存会影响 |
| WebView 首次可见绘制 | 6.7–7.5 s | `onPageCommitVisible` |
| 页面加载完成 | 9.0–9.8 s | `onPageFinished` |
| Web UI 可交互（覆盖层开始淡出） | 9.3–10.2 s | composer/历史就绪轮询 |
| 后台恢复（Activity 存活） | **0.12 s** | `am start -W`，Host 和 WebView 均未重建 |

> Host 启动时间有明显波动。开发过程中曾观察到约 **2.4 秒** 的样本；这与设备负载、
> Provider/网络状态、`dsh` 插件初始化有关。上表是同一轮连续冷启动的稳定区间。

## 已保留的改动

- **启动覆盖层不再每帧重绘全屏动画**：背景、光晕、星点改为静态；进度条的
  `LinearGradient` 只创建一次；不确定进度只在进度条自身刷新。这样在 Host/WebView
  最需要 CPU 的几秒内，避免每帧创建 shader、绘制三层全屏渐变和 60 fps 失效。
- **更早开始探测 Web UI 就绪**：在 `onPageCommitVisible` 就开始轮询，`onPageFinished`
  只作为兜底；仍以 composer 出现或历史加载完成作为真正结束条件。
- **去掉页面完成时的 `navigator.userAgent` JS 注入**：该调用只用于调试日志，启动路径
  不再多一次 JS 往返。
- **增加阶段日志**：`boot: first paint after ...`、`boot: page finished after ...`、
  `boot: Web UI ready after ...`，便于以后回归对比。
- 启动覆盖层仍保留真实 rootfs 解包/下载百分比；首次解包耗时与启动优化无关。

## 试过但未保留

| 试验 | 结果 | 处理 |
| --- | --- | --- |
| 在 Host 启动前预加载本地空白页，预热 Chromium renderer | 首帧/可交互时间无改善，`TotalTime` 略增 | 已回退 |
| `NODE_COMPILE_CACHE` 复用 V8 字节码 | 连续四次冷启动无改善，部分样本略慢 | 已回退 |
| 把 Host 启动提前到 `onCreate` | Android 12 前台服务启动限制，存在与前台切换竞态 | 未采用，保留 `onResume` 启动 |
| 缩短覆盖层最小展示时间 | 覆盖层不是瓶颈，且会牺牲“真正就绪”判断 | 未采用 |

## 后续可做的优化（按收益/风险排序）

1. **保持 Host 常驻**（已实现，建议作为产品默认行为）
   - 前台服务已经存在；不要用 `force-stop` 衡量日常启动。
   - 从最近任务划掉应用是否保留服务取决于 OEM；可在设置中说明“保持后台运行”以获得快速恢复。
2. **分析 `dsh web` 的 6–7 秒**
   - 在 rootfs 内对 `dsh` 启动做 profile（插件加载、workspace 扫描、Provider 探测）；
   - 优先实现插件懒加载/延迟初始化；这是最大的一块。
   - 需要上游 `dsh` 配合，不建议在 APK 里硬改第三方包。
3. **升级/更新 Android System WebView**
   - Chromium 92 较旧；新 WebView 对 ES modules、CSS 和 JS 启动通常更快，也能减少兼容层。
   - 这是设备/系统侧更新，应用只能检测并提示。
4. **可选的开机预热服务**
   - 用 `BOOT_COMPLETED` 启动 Host 可让第一次点击接近热启动，但会增加常驻内存/耗电，
     且需要用户明确开启；不建议默认启用。
5. **前端首屏裁剪**
   - 若 `dsh web` 首屏加载了非首屏必需的插件/历史，可做按需加载；需要前端配合。
6. **应用基线 Profile**
   - 原生代码量很小，收益预计低于 100 ms；优先级最低。

## 复测命令

```bash
adb logcat -c
adb shell am force-stop com.dshd.android
adb shell am start -W -n com.dshd.android/.MainActivity
sleep 12
adb logcat -d -v time | grep -E 'dshd.*(starting service host|host: dsh web|boot:)'
```

后台恢复：

```bash
adb shell input keyevent HOME
adb shell am start -W -n com.dshd.android/.MainActivity
```

## 验收边界

本次优化不改变启动流程、rootfs 解包、Provider 配置或权限行为；只减少启动期间的 UI
重绘/JS 调用，并增加日志。最终 APK 仍需跑 `scripts/android-e2e.sh` 和
`scripts/check-release-readiness.sh`。
