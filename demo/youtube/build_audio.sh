#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"
mkdir -p out/audio
OUTRO_EN=out/audio/outro-en.mp3
OUTRO_ZH=out/audio/outro-zh.mp3
if [ ! -f "$OUTRO_EN" ]; then
  /tmp/ttsvenv/bin/edge-tts --voice en-US-AvaNeural --rate=+6% \
    --text "DSHD is open source. Find it on GitHub. Link in the description." \
    --write-media "$OUTRO_EN"
fi
if [ ! -f "$OUTRO_ZH" ]; then
  /tmp/ttsvenv/bin/edge-tts --voice zh-CN-XiaoxiaoNeural --rate=+6% \
    --text "DSHD 已在 GitHub 开源。链接见视频简介。" --write-media "$OUTRO_ZH"
fi

build_one() {
  local lang="$1" demo="$2" outro="$3" total="$4"
  ffmpeg -y -loglevel error -i "../out/dshd-demo-${demo}.mp4" -vn \
    -ac 2 -ar 48000 "out/audio/main-${lang}.wav"
  ffmpeg -y -loglevel error -i "$outro" -ac 2 -ar 48000 "out/audio/outro-${lang}.wav"
  ffmpeg -y -loglevel error \
    -i "out/audio/main-${lang}.wav" -i "out/audio/outro-${lang}.wav" \
    -filter_complex "[0:a]apad=pad_dur=0.60[a0];[a0][1:a]concat=n=2:v=0:a=1,loudnorm=I=-16:TP=-1.5:LRA=11,apad=pad_dur=0.40[a]" \
    -map "[a]" -t "$total" "out/audio/${lang}-mix.wav"
  ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "out/audio/${lang}-mix.wav"
}
# Duration is deliberately identical for both languages; the outro starts
# after the vertical demo and the last frames hold the CTA.
build_one en en "$OUTRO_EN" 31.2
build_one zh zh "$OUTRO_ZH" 31.2
