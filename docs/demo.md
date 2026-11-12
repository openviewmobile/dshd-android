# Demo assets

> The generated GIF/MP4 files are not committed. Run `demo/build.sh` to create
them locally; the repository keeps the source, narration script and renderer.

The first-use demo is produced in two fully separate language versions:

| Asset | Language | Size | Duration |
| --- | --- | --- | --- |
| `docs/assets/demo-zh.gif` | 中文 | 4.8 MB | 24.9 s |
| `docs/assets/demo-zh.mp4` | 中文旁白 | 12 MB | 24.9 s |
| `docs/assets/demo-en.gif` | English | 5.0 MB | 25.4 s |
| `docs/assets/demo-en.mp4` | English narration | 12 MB | 25.4 s |

The GIFs are silent by design; the MP4s carry the neural narration. There is no
bilingual overlay: each file contains only its own language (product/brand
names excepted).

## Storyboard

1. Title — DSHD for Android.
2. First launch: embedded rootfs, explicitly a **one-time unpack** (not
   “instant”); this matches the real 1–3 minute import.
3. Provider settings; API key stays in Android Keystore.
4. File preview and full-path behavior.
5. Real terminal running locally.
6. Permissions, app-private storage and the on-device runtime. The copy avoids
   claiming “no network”: prompts still go to the model endpoint the user
   configures.

## Production

The demo is not a screen recording. It is a deterministic three.js scene with
shader transitions, rendered frame-by-frame through Playwright/Chrome and
encoded with ffmpeg:

- 1080×1920, 30 fps, H.264 + AAC MP4;
- 360×640, 8 fps, 64-color silent GIF;
- `zh-CN-XiaoxiaoNeural` / `en-US-AvaNeural` narration, loudness-normalized.

Full regeneration instructions, dependencies and source layout:
[demo/README.md](../demo/README.md).

The local ffmpeg build has no `drawtext`/`libass`, so all captions are rendered
inside the HTML/CSS scene rather than burned in afterwards.
