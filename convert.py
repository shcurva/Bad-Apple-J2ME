#!/usr/bin/env python3
"""
Bad Apple!! -> 1bpp video bin for J2ME.
Deps: numpy + ffmpeg (system binary or imageio-ffmpeg wheel).

Format v1 (big-endian, Java DataInputStream-friendly):
  off  size  field
  0    4s    magic "BAPL"
  4    u16   version (1)
  6    u16   width
  8    u16   height
  10   u16   fps
  12   u32   frame_count
  16   ...   frames: w*h/8 bytes each, row-major, bit 1 = white, MSB first
"""
from __future__ import annotations

import argparse
import shutil
import struct
import subprocess
import sys
from pathlib import Path

import numpy as np

MAGIC = b"BAPL"
VERSION = 1
HEADER = struct.Struct(">4sHHHHI")  # 16 bytes


def find_ffmpeg() -> str:
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        sys.exit("ffmpeg not found: apt install ffmpeg OR pip install imageio-ffmpeg")


def convert(src: Path, dst: Path, w: int, h: int, fps: int, thr: int) -> None:
    if w % 8:
        sys.exit("width must be a multiple of 8 (byte packing)")
    ff = find_ffmpeg()
    cmd = [
        ff, "-v", "error", "-i", str(src),
        "-vf", f"scale={w}:{h}:flags=area,fps={fps},format=gray",
        "-f", "rawvideo", "-pix_fmt", "gray", "-",
    ]
    frame_n = w * h
    dst.parent.mkdir(parents=True, exist_ok=True)
    frames = 0

    with subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=1 << 20) as proc, \
            open(dst, "wb") as out:
        out.write(HEADER.pack(MAGIC, VERSION, w, h, fps, 0))  # count patched below
        while True:
            raw = proc.stdout.read(frame_n)
            if len(raw) < frame_n:
                break
            bits = np.frombuffer(raw, dtype=np.uint8) >= thr
            out.write(np.packbits(bits).tobytes())
            frames += 1
            if frames % 250 == 0:
                print(f"  {frames} frames, {out.tell() // 1024} KiB", flush=True)
        rc = proc.wait()

    if rc != 0:
        dst.unlink(missing_ok=True)
        sys.exit(f"ffmpeg exited with {rc}")
    if frames == 0:
        dst.unlink(missing_ok=True)
        sys.exit("no frames decoded — is bad_apple.mp4 actually there?")

    with open(dst, "r+b") as out:
        out.seek(12)
        out.write(struct.pack(">I", frames))

    size = dst.stat().st_size
    print(f"OK: {dst} | {w}x{h} @ {fps} fps | {frames} frames "
          f"({frames / fps:.1f} s) | {size / 2**20:.2f} MiB")
    if size > 5 * 2**20:
        print("NOTE: >5 MiB. Поздние кнопочники переварят (сам сказал), "
              "а в v2 дожмём RLE примерно втрое.")


def main() -> None:
    p = argparse.ArgumentParser(description="Bad Apple!! -> J2ME 1bpp bin")
    p.add_argument("src", type=Path, nargs="?", default=Path("bad_apple.mp4"))
    p.add_argument("-o", "--out", type=Path, default=Path("build/badapple.bin"))
    p.add_argument("-W", "--width", type=int, default=160)
    p.add_argument("-H", "--height", type=int, default=120)
    p.add_argument("-f", "--fps", type=int, default=10)
    p.add_argument("-t", "--threshold", type=int, default=128)
    a = p.parse_args()
    convert(a.src, a.out, a.width, a.height, a.fps, a.threshold)


if __name__ == "__main__":
    main()
