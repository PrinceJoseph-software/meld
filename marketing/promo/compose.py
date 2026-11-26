#!/usr/bin/env python3
"""Composites the recorded phones + branded assets into promo videos (16:9 and 9:16) and a GIF."""
import json, subprocess, sys, os

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
A = f"{D}/assets"
FPS = 30
X264 = ["-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", str(FPS), "-movflags", "+faststart"]


def run(cmd):
    print(" ".join(cmd[:6]), "…")
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def dur(path):
    out = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path])
    return float(out)


def still(img, seconds, out, fade=0.4):
    run(["ffmpeg", "-y", "-loop", "1", "-t", str(seconds), "-i", img,
         "-vf", f"fps={FPS},format=yuv420p,fade=t=in:st=0:d={fade},fade=t=out:st={seconds - fade}:d={fade}",
         *X264, out])


def main_segment(fmt, out):
    lay = json.load(open(f"{A}/layout_{fmt}.json"))
    tl = json.load(open(f"{D}/timeline.json"))
    length = min(dur(f"{D}/alice.webm"), dur(f"{D}/bob.webm"))
    pw, ph, x1, x2, y = lay["pw"], lay["ph"], lay["x1"], lay["x2"], lay["y"]
    inputs = ["-loop", "1", "-t", f"{length}", "-i", f"{A}/bg_{fmt}.png",
              "-i", f"{D}/alice.webm", "-i", f"{D}/bob.webm",
              "-loop", "1", "-t", f"{length}", "-i", f"{A}/mask_{fmt}.png",
              "-loop", "1", "-t", f"{length}", "-i", f"{A}/frame_{fmt}.png"]
    caps = [(i, s) for i, s in enumerate(tl) if s["caption"] != "END"]
    for i, _ in caps:
        inputs += ["-loop", "1", "-t", f"{length}", "-i", f"{A}/cap_{fmt}_{i}.png"]
    f = [
        f"[3:v]scale={pw}:{ph},format=gray,split=2[m1][m2]",
        f"[1:v]fps={FPS},scale={pw}:{ph}:flags=lanczos,format=yuva420p[a0]",
        f"[2:v]fps={FPS},scale={pw}:{ph}:flags=lanczos,format=yuva420p[b0]",
        "[a0][m1]alphamerge[a]", "[b0][m2]alphamerge[b]",
        f"[0:v]fps={FPS},format=yuva420p[bg]",
        f"[bg][a]overlay={x1}:{y}[s1]", f"[s1][b]overlay={x2}:{y}[s2]", "[s2][4:v]overlay=0:0[s3]",
    ]
    last = "s3"
    for n, (i, s) in enumerate(caps):
        start = s["t"]
        end = tl[i + 1]["t"] if i + 1 < len(tl) else length
        end = min(end, length)
        idx = 5 + n
        f.append(f"[{idx}:v]format=rgba,fade=t=in:st={start:.2f}:d=0.35:alpha=1,fade=t=out:st={max(start, end - 0.35):.2f}:d=0.35:alpha=1[c{n}]")
        f.append(f"[{last}][c{n}]overlay=0:0[o{n}]")
        last = f"o{n}"
    f.append(f"[{last}]format=yuv420p,fade=t=in:st=0:d=0.3,fade=t=out:st={length - 0.4:.2f}:d=0.4[v]")
    run(["ffmpeg", "-y", *inputs, "-filter_complex", ";".join(f), "-map", "[v]", "-t", f"{length}", *X264, out])


def build(fmt):
    parts = [f"{D}/seg_{fmt}_{n}.mp4" for n in range(4)]
    still(f"{A}/title_{fmt}.png", 2.8, parts[0])
    main_segment(fmt, parts[1])
    still(f"{A}/card_{fmt}.png", 4.0, parts[2])
    still(f"{A}/end_{fmt}.png", 3.6, parts[3])
    lst = f"{D}/concat_{fmt}.txt"
    open(lst, "w").write("".join(f"file '{p}'\n" for p in parts))
    out = f"{D}/meld-demo-{'16x9' if fmt == 'land' else '9x16'}.mp4"
    run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", "-movflags", "+faststart", out])
    make_gif(out, [dur(p) for p in parts], 960 if fmt == "land" else 540,
             f"{D}/meld-demo{'' if fmt == 'land' else '-vertical'}.gif")
    return out


def make_gif(video, seg, width, out):
    """25 s highlight: title beat, the demo at 2x speed, result card, end card."""
    t, m, c, _ = seg
    cuts = [
        (0.5, t - 0.3, "1"),
        (t, t + m, "2"),
        (t + m + 0.3, t + m + c - 0.7, "1"),
        (t + m + c + 0.3, t + m + c + 2.8, "1"),
    ]
    f = [f"[0:v]trim={a:.2f}:{b:.2f},setpts=(PTS-STARTPTS)/{sp}[s{i}]" for i, (a, b, sp) in enumerate(cuts)]
    f.append("".join(f"[s{i}]" for i in range(len(cuts))) + f"concat=n={len(cuts)}:v=1[v]")
    f.append(f"[v]fps=15,scale={width}:-1:flags=lanczos,split[a][b]")
    f.append("[a]palettegen=max_colors=192:stats_mode=diff[p]")
    f.append("[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle")
    run(["ffmpeg", "-y", "-i", video, "-filter_complex", ";".join(f), "-loop", "0", out])


if __name__ == "__main__":
    fmts = sys.argv[1:] or ["land", "vert"]
    for fmt in fmts:
        print("built", build(fmt))
