#!/usr/bin/env bash
# End-to-end regeneration of the two DSHD first-use demo videos + GIFs.
# Run from anywhere; paths are relative to this script.
set -euo pipefail
cd "$(dirname "$0")"

echo "== 1/4 narration + timeline =="
python3 build_audio.py

echo "== 2/4 deterministic frames (Playwright / Chrome, 1080x1920@30fps) =="
node render.mjs zh en

echo "== 3/4 H.264 + AAC encode =="
for lang in zh en; do
  ffmpeg -y -hide_banner -loglevel error \
    -framerate 30 -i "frames/$lang/%05d.jpg" \
    -i "audio/$lang.wav" \
    -af "loudnorm=I=-16:TP=-1.5:LRA=11" \
    -c:v libx264 -preset medium -crf 18 -profile:v high -level 4.1 \
    -pix_fmt yuv420p \
    -c:a aac -b:a 192k -ar 48000 \
    -movflags +faststart -shortest \
    "out/dshd-demo-$lang.mp4"
  echo "  out/dshd-demo-$lang.mp4"
done

echo "== 4/4 optimized 360px GIFs =="
for lang in zh en; do
  ffmpeg -y -hide_banner -loglevel error \
    -i "out/dshd-demo-$lang.mp4" \
    -vf "fps=8,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=64:stats_mode=diff[p];[s1][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
    -loop 0 "out/dshd-demo-$lang.gif"
  echo "  out/dshd-demo-$lang.gif"
done

echo "== done =="
ls -lh out/dshd-demo-*.mp4 out/dshd-demo-*.gif
