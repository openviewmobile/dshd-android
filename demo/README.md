# DSHD first-use demo (separate languages)

Two deterministic, silent-caption motion demos built with three.js and
shader/GLSL transitions, plus neural narration. There is **no bilingual
overlay**: the Chinese file contains Chinese only (brand names excepted), and
the English file contains English only.

| Output | Size | Duration | Notes |
| --- | --- | --- | --- |
| `docs/assets/demo-zh.gif` | 4.8 MB | 24.9 s | 360×640, 8 fps, silent |
| `docs/assets/demo-zh.mp4` | 12 MB | 24.9 s | 1080×1920, H.264 + AAC, Mandarin |
| `docs/assets/demo-en.gif` | 5.0 MB | 25.4 s | 360×640, 8 fps, silent |
| `docs/assets/demo-en.mp4` | 12 MB | 25.4 s | 1080×1920, H.264 + AAC, English |

Voice: `zh-CN-XiaoxiaoNeural` and `en-US-AvaNeural` (edge-tts, 24 kHz, rate +6%,
loudness-normalized to −16 LUFS). The MP4s carry the narration; GIF cannot.

## Regenerate

Prerequisites: Node.js 22+, Python 3.10+, `ffmpeg` with libx264 and
palettegen/paletteuse, Google Chrome, and network access for edge-tts.

```bash
cd demo
npm install

# one-time TTS tool venv (path is referenced by build_audio.py)
python3 -m venv /tmp/ttsvenv
/tmp/ttsvenv/bin/pip install edge-tts==7.3.1

bash build.sh
cp out/dshd-demo-zh.mp4 ../docs/assets/demo-zh.mp4
cp out/dshd-demo-en.mp4 ../docs/assets/demo-en.mp4
cp out/dshd-demo-zh.gif ../docs/assets/demo-zh.gif
cp out/dshd-demo-en.gif ../docs/assets/demo-en.gif
```

`build.sh` runs four stages:

1. `build_audio.py` synthesizes per-beat MP3/SRT, measures real durations, and
   writes `out/timeline.<lang>.json`.
2. `render.mjs` serves the scene locally and drives Chrome (Playwright,
   `channel: 'chrome'`, `--use-angle=metal`) at 1080×1920, 30 fps. Every frame is
   rendered after `window.__setTime(t)`, so output is reproducible.
3. ffmpeg encodes H.264 High/yuv420p + AAC, `+faststart`.
4. ffmpeg makes the lightweight GIF: 360 px, 8 fps, 64-color palette.

## Design notes

- Beats: title → embedded runtime (one-time unpack) → model/provider → file
  preview → real terminal → permissions/privacy. Progress labels are 1-based
  and consistent (`02 / 06` pairs with `02 · FIRST LAUNCH`).
- The privacy beat says **files and runtime stay on device**, not “no network”:
  prompts still go to the model endpoint configured by the user.
- The first-launch beat says **one-time unpack**, not “starts instantly”, so it
  matches the real 1–3 minute embedded-rootfs import.
- All captions are HTML/CSS in the page; the local ffmpeg build has no
  `drawtext` filter.
- three.js is MIT-licensed and installed from npm, not vendored. The demo uses
  no hosted HyperFrames service; the transition language is implemented
  directly with three.js shaders.

## Files

- `src/scene.html`, `src/scene.css`, `src/scene.js` — the 3D scene and mock UI.
- `build_audio.py` — narration, SRT, timeline.
- `render.mjs` — local server + Playwright frame capture.
- `build.sh` — end-to-end build.

## 16:9 YouTube cut

`youtube/` reuses the vertical demos as a product-demo panel and adds a
1920×1080 typographic layout, a GitHub CTA, and separate English/Chinese
thumbnails. The upload copy lives in `docs/youtube-promo.md`.

```bash
cd demo
bash youtube/build_audio.sh
node youtube/render.mjs en zh

cd youtube
for lang in en zh; do
  ffmpeg -y -framerate 30 -i frames/$lang/%05d.jpg -i out/audio/$lang-mix.wav \
    -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p \
    -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart \
    out/dshd-promo-$lang.mp4
done
```

The wrapper does not autoplay the vertical file in real time: `render.mjs`
seeks it to the exact frame time, so the output is deterministic. The outro
narration is synthesized once by `build_audio.sh` (`en-US-AvaNeural` /
`zh-CN-XiaoxiaoNeural`, rate +6%) and concatenated after the demo audio with a
0.6 s gap. There is no background music; add licensed music in YouTube Studio
if desired.

Files:

- `youtube/src/scene.html`, `scene.css`, `scene.js` — 16:9 layout/captions.
- `youtube/src/logo.svg` — DSHD mark.
- `youtube/build_audio.sh` — main + outro voice tracks.
- `youtube/render.mjs` — range-aware server + Playwright frame capture.
- `youtube/out/` and `youtube/frames/` — generated, gitignored.
