#!/usr/bin/env python3
"""Generate narration, SRT cues, and the shared demo timeline.

Requires /tmp/ttsvenv/bin/edge-tts (see demo/README.md).
Writes:
  audio/segments/<lang>-<n>.mp3, .srt
  audio/<lang>.wav          (48 kHz mono, padded to the frame-quantized timeline)
  audio/<lang>.mp3          (same, mp3 for reference)
  out/<lang>.srt
  out/timeline.<lang>.json
"""
from __future__ import annotations

import json
import math
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"
AUDIO = ROOT / "audio"
SEG = AUDIO / "segments"
FPS = 30
EDGE_TTS = "/tmp/ttsvenv/bin/edge-tts"

VOICES = {"zh": "zh-CN-XiaoxiaoNeural", "en": "en-US-AvaNeural"}
RATE = "+6%"

# The six beats. `narration` is spoken and becomes the caption; the visible
# copy is deliberately shorter than the narration.
BEATS = {
    "zh": [
        dict(
            id="title",
            kicker="首次使用演示",
            title="DSHD",
            subtitle="随身智能工作站",
            narration="DSHD，随身智能工作站。",
        ),
        dict(
            id="boot",
            kicker="02 · 首次启动",
            title="内置运行时",
            subtitle="一次解包，之后即用",
            narration="运行时内置，首次启动只需一次解包。",
        ),
        dict(
            id="provider",
            kicker="03 · 模型接入",
            title="连接你的模型",
            subtitle="地址与密钥，只存本机",
            narration="填入地址和密钥，连接你的模型。",
        ),
        dict(
            id="files",
            kicker="04 · 工作区",
            title="文件与预览",
            subtitle="随手点开，即刻阅读",
            narration="文件随手点开，即刻预览。",
        ),
        dict(
            id="terminal",
            kicker="05 · 真实终端",
            title="命令在本地执行",
            subtitle="真实终端，实时输出",
            narration="真实终端，命令在本地执行。",
        ),
        dict(
            id="privacy",
            kicker="06 · 隐私",
            title="文件与运行时留在本机",
            subtitle="权限透明，访问范围清楚",
            narration="权限可控，文件与运行时留在本机。",
        ),
    ],
    "en": [
        dict(
            id="title",
            kicker="FIRST-RUN DEMO",
            title="DSHD",
            subtitle="Your workstation, in your pocket",
            narration="DSHD. Your AI workstation, in your pocket.",
        ),
        dict(
            id="boot",
            kicker="02 · FIRST LAUNCH",
            title="Runtime built in",
            subtitle="One-time unpack, then ready",
            narration="Built-in runtime. One-time unpack, then straight to work.",
        ),
        dict(
            id="provider",
            kicker="03 · MODEL ACCESS",
            title="Connect your model",
            subtitle="Address and key stored on device",
            narration="Add a service address and key. Connect your model.",
        ),
        dict(
            id="files",
            kicker="04 · WORKSPACE",
            title="Files & preview",
            subtitle="Tap to open, read instantly",
            narration="Open any file for an instant preview.",
        ),
        dict(
            id="terminal",
            kicker="05 · TERMINAL",
            title="Real terminal",
            subtitle="Local commands, live output",
            narration="A real terminal runs locally on the phone.",
        ),
        dict(
            id="privacy",
            kicker="06 · PRIVACY",
            title="Files & runtime stay local",
            subtitle="Clear permissions, scoped access",
            narration="Clear permissions. Files and runtime stay on your device.",
        ),
    ],
}

SRT_TIME = re.compile(
    r"(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})"
)


def run(cmd: list[str]) -> None:
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def probe_duration(path: Path) -> float:
    out = subprocess.check_output(
        [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "csv=p=0",
            str(path),
        ],
        text=True,
    ).strip()
    return float(out)


def parse_srt(path: Path) -> list[tuple[float, float, str]]:
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()
    cues: list[tuple[float, float, str]] = []
    for block in re.split(r"\n\s*\n", text):
        lines = [ln for ln in block.split("\n") if ln.strip()]
        if len(lines) < 2:
            continue
        m = None
        body_start = 1
        for i, ln in enumerate(lines[:2]):
            m = SRT_TIME.search(ln)
            if m:
                body_start = i + 1
                break
        if not m:
            continue
        a = [int(x) for x in m.groups()[:4]]
        b = [int(x) for x in m.groups()[4:]]
        start = a[0] * 3600 + a[1] * 60 + a[2] + a[3] / 1000
        end = b[0] * 3600 + b[1] * 60 + b[2] + b[3] / 1000
        body = " ".join(ln.strip() for ln in lines[body_start:] if ln.strip())
        body = re.sub(r"\s+", " ", body)
        if body:
            cues.append((start, end, body))
    return cues


def fmt_srt(t: float) -> str:
    ms = int(round(t * 1000))
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def wrap_caption(text: str, lang: str) -> str:
    if lang == "zh":
        width = 18
        lines, line = [], ""
        for ch in text:
            line += ch
            if len(line) >= width and ch in "。，、；：！？ ":
                lines.append(line.strip())
                line = ""
        if line.strip():
            lines.append(line.strip())
        if len(lines) > 2:
            lines = [lines[0], "".join(lines[1:])]
        return "\n".join(lines)
    words = text.split()
    width = 46
    lines, line = [], ""
    for w in words:
        if len(line) + len(w) + 1 > width and line:
            lines.append(line)
            line = w
        else:
            line = f"{line} {w}".strip()
    if line:
        lines.append(line)
    if len(lines) > 2:
        lines = [lines[0], " ".join(lines[1:])]
    return "\n".join(lines)


def build_lang(lang: str) -> dict:
    voice = VOICES[lang]
    beats = BEATS[lang]
    lead_in = 0.25
    audio_offset = 0.30
    tail = 0.35
    final_tail = 0.55

    seg_durs: list[float] = []
    seg_cues: list[list[tuple[float, float, str]]] = []
    for i, beat in enumerate(beats, start=1):
        base = SEG / f"{lang}-{i:02d}"
        mp3 = base.with_suffix(".mp3")
        srt = base.with_suffix(".srt")
        run(
            [
                EDGE_TTS,
                "--voice",
                voice,
                f"--rate={RATE}",
                "--text",
                beat["narration"],
                "--write-media",
                str(mp3),
                "--write-subtitles",
                str(srt),
            ]
        )
        d = probe_duration(mp3)
        seg_durs.append(d)
        seg_cues.append(parse_srt(srt))
        print(f"  {lang} beat {i} ({beat['id']}): {d:.3f}s")

    starts: list[float] = []
    t = lead_in
    for d in seg_durs:
        starts.append(t)
        t += audio_offset + d + tail
    total = t + final_tail
    total = math.ceil(total * FPS) / FPS  # keep video/audio frame aligned

    # Per-beat visual windows: from this beat's start to the next beat's start.
    beat_entries = []
    for i, beat in enumerate(beats):
        start = max(0.0, starts[i] - audio_offset)
        end = starts[i + 1] - audio_offset if i + 1 < len(beats) else total
        entry = {
            "id": beat["id"],
            "index": i,
            "start": round(start, 3),
            "dur": round(end - start, 3),
            "kicker": beat["kicker"],
            "title": beat["title"],
            "subtitle": beat["subtitle"],
        }
        beat_entries.append(entry)

    cues = []
    for i, (beat, d, raw) in enumerate(zip(beats, seg_durs, seg_cues)):
        base = starts[i]
        if not raw:
            raw = [(0.06, max(0.6, d - 0.12), beat["narration"])]
        for (cs, ce, text) in raw:
            s = base + cs
            e = base + min(ce, d + 0.05)
            # Keep captions inside their beat and inside the frame.
            s = max(s, beat_entries[i]["start"] + 0.12)
            e = min(e, beat_entries[i]["start"] + beat_entries[i]["dur"] - 0.12)
            if e - s < 0.35:
                e = s + 0.35
            cues.append({"start": round(s, 3), "end": round(e, 3), "text": text})
    cues.sort(key=lambda c: c["start"])
    # edge-tts occasionally overlaps adjacent cues by ~50 ms; make them
    # strictly sequential so only one caption is ever on screen.
    for i in range(1, len(cues)):
        if cues[i]["start"] <= cues[i - 1]["end"]:
            cues[i]["start"] = round(cues[i - 1]["end"] + 0.05, 3)
        if cues[i]["end"] - cues[i]["start"] < 0.4:
            cues[i]["end"] = round(cues[i]["start"] + 0.4, 3)

    # ---- audio assembly -------------------------------------------------
    concat_dir = AUDIO / "parts"
    concat_dir.mkdir(parents=True, exist_ok=True)
    parts: list[Path] = []

    def silence(seconds: float, name: str) -> Path:
        p = concat_dir / name
        run(
            [
                "ffmpeg",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "anullsrc=r=48000:cl=mono",
                "-t",
                f"{seconds:.6f}",
                "-c:a",
                "pcm_s16le",
                str(p),
            ]
        )
        return p

    parts.append(silence(lead_in, f"{lang}-lead.wav"))
    for i, d in enumerate(seg_durs, start=1):
        wav = concat_dir / f"{lang}-{i:02d}.wav"
        run(
            [
                "ffmpeg",
                "-y",
                "-i",
                str(SEG / f"{lang}-{i:02d}.mp3"),
                "-ar",
                "48000",
                "-ac",
                "1",
                "-c:a",
                "pcm_s16le",
                str(wav),
            ]
        )
        parts.append(wav)
        parts.append(silence(tail, f"{lang}-{i:02d}-tail.wav"))
    parts.append(silence(final_tail, f"{lang}-final.wav"))

    concat_list = concat_dir / f"{lang}-concat.txt"
    concat_list.write_text("".join(f"file '{p}'\n" for p in parts), encoding="utf-8")
    assembled = AUDIO / f"{lang}.wav"
    run(
        [
            "ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(concat_list),
            "-c:a",
            "pcm_s16le",
            "-ar",
            "48000",
            "-ac",
            "1",
            str(assembled),
        ]
    )
    # Pad/trim to the exact frame-quantized duration.
    exact = AUDIO / f"{lang}-exact.wav"
    run(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(assembled),
            "-af",
            f"apad=whole_dur={total:.6f},atrim=0:{total:.6f}",
            "-ar",
            "48000",
            "-ac",
            "1",
            "-c:a",
            "pcm_s16le",
            str(exact),
        ]
    )
    exact.replace(assembled)
    run(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(assembled),
            "-c:a",
            "libmp3lame",
            "-b:a",
            "192k",
            str(AUDIO / f"{lang}.mp3"),
        ]
    )

    # ---- srt + timeline -------------------------------------------------
    OUT.mkdir(parents=True, exist_ok=True)
    srt_lines = []
    for i, cue in enumerate(cues, start=1):
        srt_lines.append(
            f"{i}\n{fmt_srt(cue['start'])} --> {fmt_srt(cue['end'])}\n"
            f"{wrap_caption(cue['text'], lang)}\n"
        )
    (OUT / f"{lang}.srt").write_text("\n".join(srt_lines), encoding="utf-8")

    timeline = {
        "lang": lang,
        "fps": FPS,
        "total": round(total, 3),
        "frames": int(round(total * FPS)),
        "beats": beat_entries,
        "cues": cues,
    }
    (OUT / f"timeline.{lang}.json").write_text(
        json.dumps(timeline, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(
        f"  {lang}: total {total:.3f}s / {timeline['frames']} frames, "
        f"{len(cues)} caption cues"
    )
    return timeline


def main() -> None:
    SEG.mkdir(parents=True, exist_ok=True)
    for lang in ("zh", "en"):
        print(f"[audio] {lang}")
        build_lang(lang)


if __name__ == "__main__":
    sys.exit(main())
