#!/usr/bin/env python3
"""Generate every SFX for 7 سنين (numpy only) -> audio/*.ogg (mono), then QA check.
Usage: python3 tools/make_audio.py        (writes audio/ and audio/qa_report.txt)"""
import numpy as np, wave, subprocess, os, sys, json, shutil

SR = 32000
OUT = os.path.join(os.path.dirname(__file__), "..", "audio")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

def t(d): return np.arange(int(d * SR)) / SR
def noise(d): return rng.standard_normal(int(d * SR))
def env_exp(d, k): return np.exp(-t(d) * k)

def onepole_lp(x, fc):
    a = np.exp(-2 * np.pi * fc / SR); y = np.empty_like(x); s = 0.0
    for i in range(len(x)):
        s = (1 - a) * x[i] + a * s; y[i] = s
    return y

def fft_band(x, lo, hi):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    m = ((f >= lo) & (f <= hi)).astype(float)
    # soft edges
    m = np.convolve(m, np.hanning(31) / np.hanning(31).sum(), "same")
    return np.fft.irfft(X * m, len(x))

def modes(d, freqs, decays, amps):
    tt = t(d); y = np.zeros_like(tt)
    for f, k, a in zip(freqs, decays, amps):
        y += a * np.sin(2 * np.pi * f * tt + rng.uniform(0, 6)) * np.exp(-tt * k)
    return y

def reverb(x, wet=0.3, length=1.2):
    n = int(length * SR); ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR * 5.0)
    ir = onepole_lp(ir, 3000); ir /= np.abs(ir).sum() ** 0.5 * 4
    y = np.convolve(x, ir)[: len(x) + n]
    out = np.zeros(len(y)); out[: len(x)] += x * (1 - wet); out += y * wet
    return out

def pad(x, pre=0.0, post=0.05):
    return np.concatenate([np.zeros(int(pre * SR)), x, np.zeros(int(post * SR))])

def fade(x, a=0.004, b=0.02):
    x = x.copy(); na = max(1, int(a * SR)); nb = max(1, int(b * SR))
    x[:na] *= np.linspace(0, 1, na); x[-nb:] *= np.linspace(1, 0, nb); return x

def norm(x, peak_db=-3.0):
    x = x - np.mean(x); p = np.max(np.abs(x)) + 1e-9
    return x / p * 10 ** (peak_db / 20)

def loopify(x, xf=1.0):
    n = int(xf * SR); head = x[:n]; body = x[n:].copy()
    w = np.linspace(0, 1, n); body[-n:] = body[-n:] * (1 - w) + head * w
    return body

FILES = {}
def save(name, x, peak=-3.0, loop=False):
    x = norm(x if loop else fade(x), peak)
    wav = os.path.join(OUT, name + ".wav")
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    if shutil.which("ffmpeg"):
        ogg = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", ogg], check=True)
        os.remove(wav)
    FILES[name] = (x, loop)

# ---------- sounds ----------
for i in range(4):  # footsteps on dirt
    d = 0.16; x = fft_band(noise(d), 300, 2500) * env_exp(d, 38 + 6 * i)
    grit = (rng.random(int(d * SR)) > 0.996) * rng.standard_normal(int(d * SR)) * 2
    x += fft_band(grit, 2000, 7000) * env_exp(d, 25)
    save(f"step_dirt_{i+1}", pad(x, rng.uniform(0, .01)), -9)

def cane(far=False):
    d = 0.35; f0 = rng.uniform(0.95, 1.05)
    x = modes(d, [280 * f0, 540 * f0, 905 * f0, 1480 * f0], [35, 48, 65, 90], [1, .7, .45, .2])
    x[: int(.004 * SR)] += noise(.004) * .8
    if far: x = reverb(onepole_lp(x, 1400), .55, 1.6) * .6
    return pad(x)
for i in range(3): save(f"cane_{i+1}", cane(), -4)
for i in range(2): save(f"cane_far_{i+1}", cane(True), -10)

def bark(far=False):
    parts = []
    for b in range(rng.integers(1, 4)):
        d = rng.uniform(.12, .24); tt = t(d)
        f = np.linspace(rng.uniform(380, 450), rng.uniform(230, 280), len(tt))
        ph = 2 * np.pi * np.cumsum(f) / SR; saw = 2 * ((ph / (2 * np.pi)) % 1) - 1
        v = fft_band(saw, 500, 900) * 1.2 + fft_band(saw, 1300, 1900) * .8
        v += fft_band(noise(d), 800, 3000) * np.exp(-tt * 60) * .6
        v *= np.minimum(1, tt / .01) * np.exp(-tt * 9)
        parts += [v, np.zeros(int(rng.uniform(.12, .3) * SR))]
    x = np.concatenate(parts)
    if far: x = reverb(onepole_lp(x, 900), .6, 2.0) * .5
    return pad(x, 0, .3)
for i in range(3): save(f"dog_near_{i+1}", bark(), -5)
for i in range(3): save(f"dog_far_{i+1}", bark(True), -12)

d = 24.0  # wind loop with gusts
w = onepole_lp(np.cumsum(noise(d)) * .02, 500); w = fft_band(w, 60, 900)
lfo = .55 + .45 * np.sin(2 * np.pi * t(d) / 8.0) * np.sin(2 * np.pi * t(d) / 13.0 + 1)
leaves = fft_band(noise(d), 2500, 6000) * .08 * lfo ** 2
save("wind_loop", loopify(w * lfo + leaves, 2.0), -14, loop=True)

d = 22.0; tt = t(d); cr = np.zeros_like(tt)  # crickets
for f, rate, ph in [(4300, 18, 0), (4900, 23, 1.3), (5600, 15, 2.1)]:
    puls = (np.sin(2 * np.pi * rate * tt + ph) > .6).astype(float)
    chirp = (np.sin(2 * np.pi * .6 * tt + ph * 2) > -.2).astype(float)
    cr += np.sin(2 * np.pi * f * tt) * onepole_lp(puls * chirp, 300)
save("crickets_loop", loopify(cr, 2.0), -20, loop=True)

for i in range(3):  # distant fireworks
    d = 2.6; tt = t(d); x = np.zeros_like(tt)
    for k in range(rng.integers(2, 4)):
        o = int(rng.uniform(0, .9) * SR); n = len(tt) - o
        thump = np.sin(2 * np.pi * rng.uniform(50, 75) * tt[:n]) * np.exp(-tt[:n] * 7)
        crack = fft_band((rng.random(n) > .985) * rng.standard_normal(n), 1500, 6000) * np.exp(-tt[:n] * 2.5) * .5
        x[o:] += thump + crack
    save(f"fireworks_{i+1}", reverb(onepole_lp(x, 1800), .5, 2.0), -10)

for i in range(2):  # gate creak (swept resonant FM)
    d = rng.uniform(1.6, 2.4); tt = t(d)
    f = 520 + 260 * np.sin(2 * np.pi * tt / d * rng.uniform(1.2, 2.0)) + rng.uniform(-40, 40)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph + 2.5 * np.sin(ph * 2.01)) * (.6 + .4 * np.sin(2 * np.pi * 7 * tt))
    x *= np.minimum(1, tt / .2) * np.minimum(1, (d - tt) / .3) * (.5 + .5 * (rng.random(len(tt)) > .4))
    x = onepole_lp(x, 2500)
    save(f"gate_creak_{i+1}", reverb(x, .25, 1.0), -8)
for i in range(2):
    x = modes(.6, [820, 1370, 2210, 3170], [9, 12, 16, 22], [1, .6, .4, .2]); x[:80] += noise(80 / SR)
    save(f"gate_latch_{i+1}", pad(x), -7)

for i in range(2):
    x = modes(.05, [2400 + 300 * i, 4100], [160, 220], [1, .5]); x[:40] += noise(40 / SR) * .6
    save(f"flash_click_{i+1}", pad(x), -12)
for i in range(2):  # lamp flicker buzz
    d = .7; tt = t(d); hum = np.sin(2 * np.pi * 100 * tt) + .5 * np.sin(2 * np.pi * 200 * tt)
    gate = (rng.random(int(d * 40)) > .45).repeat(int(SR / 40))[: len(tt)]
    gate = np.pad(gate, (0, len(tt) - len(gate)))
    x = hum * onepole_lp(gate.astype(float), 200) + fft_band(noise(d), 2000, 8000) * gate * .15
    save(f"lamp_flicker_{i+1}", x, -16)
for i in range(2):
    x = modes(.12, [1800, 3300], [80, 120], [1, .4]); save(f"lamp_off_{i+1}", pad(x), -14)
for i in range(3):  # paper rustle
    d = .55; tt = t(d); x = fft_band(noise(d), 1200, 9000)
    x *= onepole_lp((rng.random(len(tt)) > .9).astype(float), 40) * 3 * np.sin(np.pi * tt / d)
    save(f"paper_{i+1}", x, -10)
for i in range(2):  # cat meow
    d = rng.uniform(.45, .7); tt = t(d)
    f = 420 + 380 * np.sin(np.pi * tt / d) ** 1.5 + rng.uniform(-30, 30)
    ph = 2 * np.pi * np.cumsum(f) / SR; src = np.sign(np.sin(ph)) * .5 + np.sin(ph)
    form = np.linspace(700, 1200, len(tt))
    x = fft_band(src, 400, 1400) + .5 * fft_band(src, 1800, 3200)
    x *= np.sin(np.pi * tt / d) ** .7; _ = form
    save(f"cat_meow_{i+1}", reverb(x, .2, .8), -9)
for i in range(2):
    d = .6; x = fft_band(noise(d), 3000, 8000) * np.minimum(1, t(d) / .03) * env_exp(d, 4)
    save(f"cat_hiss_{i+1}", x, -6)
for i in range(2):  # cat jump: thud + cloth
    d = .5; tt = t(d); x = np.sin(2 * np.pi * 60 * tt) * np.exp(-tt * 14) + fft_band(noise(d), 300, 3000) * np.exp(-tt * 20) * .7
    save(f"cat_jump_{i+1}", pad(x), -2)
d = 4.0; tt = t(d)  # fall / faint
thud = np.sin(2 * np.pi * 55 * tt) * np.exp(-tt * 9) + fft_band(noise(d), 200, 2000) * np.exp(-tt * 25)
sweep = np.zeros_like(tt); n = fft_band(noise(d), 80, 3000)
for j in range(0, len(tt), 1600):
    sweep[j:j + 1600] = onepole_lp(n[j:j + 1600], 3000 * np.exp(-j / SR * 1.4) + 60)
save("fall_1", thud + sweep * np.exp(-tt * .9) * .4, -4)
d = 5.0; tt = t(d)
save("wake_1", fft_band(noise(d), 100, 900) * (tt / d) ** 2 * np.exp(-((tt - 4) ** 2)) * 1.5 + np.sin(2 * np.pi * 110 * tt) * .1 * np.sin(np.pi * tt / d), -14)
for i in range(2):
    x = modes(.06, [1500 + 200 * i, 3000], [120, 180], [1, .3]); save(f"ui_click_{i+1}", pad(x), -14)
d = 3.5; tt = t(d)  # splash: flicker buzz + single far cane tap
buzz = np.sin(2 * np.pi * 100 * tt) * (rng.random(len(tt)) > .3) * np.exp(-tt * .5) * .25
sp = onepole_lp(buzz, 1200); c = cane(True); sp[int(2.2 * SR): int(2.2 * SR) + len(c)] += c[: len(sp) - int(2.2 * SR)]
save("splash_1", sp, -10)

# ---------- QA ----------
lines = []; ok = True
for name, (x, loop) in sorted(FILES.items()):
    rms = np.sqrt(np.mean(x ** 2)); peak = np.max(np.abs(x)); dc = abs(np.mean(x))
    jump = abs(x[0] - x[-1]) if loop else 0
    bad = rms < 1e-3 or peak > .99 or dc > .01 or jump > .05
    ok &= not bad
    lines.append(f"{'FAIL' if bad else 'ok  '} {name:18s} rms={rms:.3f} peak={peak:.2f} dc={dc:.4f} loopjump={jump:.3f}")
total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
lines.append(f"files={len(FILES)} total_bytes={total}")
open(os.path.join(OUT, "qa_report.txt"), "w").write("\n".join(lines) + "\n")
print("\n".join(lines[-3:])); sys.exit(0 if ok else 1)
