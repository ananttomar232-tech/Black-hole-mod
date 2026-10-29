#!/usr/bin/env python3
"""
Sound design for Living Black Hole - every effect is synthesised from scratch (oscillators,
filtered noise, FM, biquad filters, convolution reverb) and encoded to Ogg Vorbis with ffmpeg.

    python3 tools/generate_sounds.py

Mono 44.1 kHz so Minecraft can position every sound in 3D. The ambient loop is built only from
components periodic in its 8 s length, so it loops without a seam.
"""
import math
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "livingblackhole", "sounds")
RNG = np.random.default_rng(2024)


# --------------------------------------------------------------------------- primitives

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def osc(freq, seconds=None, t=None, shape="sine", phase=0.0):
    """Oscillator with (possibly time-varying) frequency in Hz."""
    if t is None:
        t = t_axis(seconds)
    freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
    ph = 2 * np.pi * np.cumsum(freq) / SR + phase
    if shape == "sine":
        return np.sin(ph)
    if shape == "saw":
        # Band-limited-ish saw from 12 harmonics.
        return sum(np.sin(ph * k) / k for k in range(1, 13)) * (2 / np.pi)
    if shape == "square":
        return sum(np.sin(ph * k) / k for k in range(1, 16, 2)) * (4 / np.pi)
    raise ValueError(shape)


def noise(seconds):
    return RNG.standard_normal(int(seconds * SR))


def spectral_noise(seconds, slope=0.0, lo=20.0, hi=20000.0):
    """Periodic coloured noise: power ~ f^-slope, band-limited to [lo, hi]."""
    n = int(seconds * SR)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    amp = np.zeros_like(freqs)
    band = (freqs >= lo) & (freqs <= hi)
    amp[band] = freqs[band] ** (-slope / 2)
    spec = amp * np.exp(1j * RNG.uniform(0, 2 * np.pi, len(freqs)))
    x = np.fft.irfft(spec, n)
    return x / (np.max(np.abs(x)) + 1e-9)


def biquad(x, kind, f0, q=0.707):
    """RBJ biquad with per-sample cutoff (array or scalar)."""
    f0 = np.clip(np.broadcast_to(np.asarray(f0, dtype=float), x.shape), 10.0, SR * 0.45)
    w0 = 2 * np.pi * f0 / SR
    alpha = np.sin(w0) / (2 * q)
    cw = np.cos(w0)
    if kind == "lp":
        b0, b1, b2 = (1 - cw) / 2, 1 - cw, (1 - cw) / 2
    elif kind == "hp":
        b0, b1, b2 = (1 + cw) / 2, -(1 + cw), (1 + cw) / 2
    elif kind == "bp":
        b0, b1, b2 = alpha, np.zeros_like(alpha), -alpha
    else:
        raise ValueError(kind)
    a0, a1, a2 = 1 + alpha, -2 * cw, 1 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    xs = x.tolist()
    B0, B1, B2, A1, A2 = b0.tolist(), b1.tolist(), b2.tolist(), a1.tolist(), a2.tolist()
    out = [0.0] * len(xs)
    for n, xn in enumerate(xs):
        yn = B0[n] * xn + B1[n] * x1 + B2[n] * x2 - A1[n] * y1 - A2[n] * y2
        x2, x1 = x1, xn
        y2, y1 = y1, yn
        out[n] = yn
    return np.asarray(out)


def env_exp(t, decay):
    return np.exp(-t / decay)


def env_adsr(n, a, d, s, r, sustain_level=0.7):
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    s_len = max(0, n - a - d - r)
    e = np.concatenate([
        np.linspace(0, 1, max(a, 1), endpoint=False),
        np.linspace(1, sustain_level, max(d, 1), endpoint=False),
        np.full(s_len, sustain_level),
        np.linspace(sustain_level, 0, max(r, 1)),
    ])
    return np.pad(e, (0, max(0, n - len(e))))[:n]


def fade(x, fin=0.005, fout=0.02):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def place(dst, src, at):
    i = int(at * SR)
    end = min(len(dst), i + len(src))
    dst[i:end] += src[:end - i]
    return dst


def reverb(x, seconds=2.5, wet=0.35, damp=4000.0, pre=0.02):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = RNG.standard_normal(n) * np.exp(-t / (seconds / 6.0))
    ir = biquad(ir, "lp", damp)
    ir = np.concatenate([np.zeros(int(pre * SR)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    size = len(x) + len(ir)
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x) + len(ir) - 1]
    dry = np.pad(x, (0, len(y) - len(x)))
    return dry * (1 - wet) + y * wet * 3.0


def saturate(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def normalize(x, peak=0.89):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def trim_tail(x, threshold=1e-3):
    idx = np.where(np.abs(x) > threshold)[0]
    return x[: idx[-1] + int(0.05 * SR)] if len(idx) else x


def write_ogg(name, x, quality=6):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    out = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis",
                    "-q:a", str(quality), out], check=True)
    os.unlink(wav_path)
    print(f"  {name}.ogg  {len(x) / SR:5.2f}s  {os.path.getsize(out) // 1024} KiB")


# --------------------------------------------------------------------------- sounds

def black_hole_ambient():
    L = 8.0
    t = t_axis(L)
    # Sub-bass drone: every partial completes an integer number of cycles in 8 s.
    drone = (np.sin(2 * np.pi * 32.5 * t) * 1.0 + np.sin(2 * np.pi * 41.0 * t) * 0.55
             + np.sin(2 * np.pi * 48.75 * t) * 0.4 + np.sin(2 * np.pi * 65.0 * t) * 0.25)
    drone *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.25 * t)
    # Gravitational groan: a slowly bending tone (FM periodic in 8 s).
    groan = np.sin(2 * np.pi * 55.0 * t + 6.0 * np.sin(2 * np.pi * 0.125 * t)) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.375 * t + 1.0))
    # Roar: periodic brown noise, low-passed in the spectrum itself.
    roar = spectral_noise(L, slope=2.0, lo=18, hi=420)
    roar *= 0.7 + 0.3 * np.sin(2 * np.pi * 0.5 * t + 0.4)
    hiss = spectral_noise(L, slope=1.0, lo=1500, hi=6000) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.25 * t + 2.0)) ** 2
    # Eerie upper partials beating against each other.
    whine = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(220.0, 0.08), (221.0, 0.08), (330.25, 0.05), (440.5, 0.03)])
    x = drone * 0.55 + groan * 0.3 + roar * 0.9 + hiss * 0.05 + whine * (0.5 + 0.5 * np.sin(2 * np.pi * 0.125 * t))
    x = saturate(normalize(x), 1.3)
    write_ogg("black_hole_ambient", normalize(x, 0.8), quality=5)


def sub_boom(seconds, f_start, f_end, sweep, decay):
    t = t_axis(seconds)
    f = f_end + (f_start - f_end) * np.exp(-t / sweep)
    return osc(f, t=t) * env_exp(t, decay)


def black_hole_birth():
    L = 6.0
    x = np.zeros(int(L * SR))
    t = t_axis(1.7)
    swell = noise(1.7)
    swell = biquad(swell, "lp", 200 + 4000 * (t / 1.7) ** 3, 0.9) * (t / 1.7) ** 2.5
    rising = osc(60 + 400 * (t / 1.7) ** 2, t=t, shape="saw") * (t / 1.7) ** 3 * 0.3
    place(x, swell + rising, 0.0)
    place(x, sub_boom(4.0, 140, 30, 0.12, 1.4) * 1.4, 1.65)
    tail_t = t_axis(4.2)
    tail = biquad(noise(4.2), "lp", 600 * np.exp(-tail_t / 1.5) + 80) * env_exp(tail_t, 1.2) * 0.9
    place(x, tail, 1.65)
    x = reverb(x, 3.0, 0.35, 2500)
    write_ogg("black_hole_birth", fade(normalize(saturate(normalize(x), 1.6)), 0.01, 0.4))


def containment_breach():
    L = 4.5
    x = np.zeros(int(L * SR))
    # Shattering lattice: inharmonic resonant pings.
    for _ in range(90):
        at = abs(RNG.normal(0.05, 0.12))
        f = RNG.uniform(1800, 9000)
        dur = RNG.uniform(0.08, 0.5)
        t = t_axis(dur)
        ping = np.sin(2 * np.pi * f * t) * env_exp(t, dur / 5) * RNG.uniform(0.2, 0.7)
        place(x, ping, min(at, 0.6))
    t = t_axis(0.35)
    place(x, biquad(noise(0.35), "hp", 2500) * env_exp(t, 0.08) * 1.2, 0.0)
    # Electrical crackle.
    crackle = np.zeros(int(1.5 * SR))
    for i in RNG.integers(0, len(crackle), 400):
        crackle[i] = RNG.uniform(-1, 1)
    crackle = biquad(crackle, "hp", 1200) * np.exp(-np.arange(len(crackle)) / SR / 0.5)
    place(x, crackle * 1.5, 0.02)
    place(x, sub_boom(3.5, 160, 35, 0.08, 0.9) * 1.6, 0.04)
    t2 = t_axis(3.0)
    place(x, biquad(noise(3.0), "lp", 900 * np.exp(-t2 / 0.8) + 120) * env_exp(t2, 0.9) * 0.8, 0.05)
    x = reverb(x, 2.8, 0.3, 5000)
    write_ogg("containment_breach", fade(normalize(saturate(normalize(x), 1.4)), 0.001, 0.3))


def alarm():
    L = 3.2
    t = t_axis(L)
    hi = (np.floor(t / 0.4) % 2 == 0)
    f = np.where(hi, 780.0, 560.0)
    f = biquad(f.astype(float), "lp", 60.0)  # glide between tones
    tone = osc(f, t=t, shape="saw") * 0.6 + osc(f * 1.005, t=t, shape="square") * 0.3
    tone = biquad(tone, "lp", 3500)
    tone *= 0.8 + 0.2 * np.sin(2 * np.pi * 7 * t)
    x = saturate(tone * env_adsr(len(t), 0.02, 0.1, 0.0, 0.3, 0.95), 2.0)
    x = reverb(x, 1.8, 0.28, 6000)
    write_ogg("alarm", fade(normalize(x, 0.8), 0.01, 0.3))


def warning_beep():
    x = np.zeros(int(0.75 * SR))
    for at in (0.0, 0.22):
        t = t_axis(0.16)
        b = (osc(1400, t=t) * 0.7 + osc(2800, t=t) * 0.15) * env_adsr(len(t), 0.005, 0.02, 0.0, 0.05, 0.8)
        place(x, b, at)
    x = reverb(x, 0.8, 0.2, 8000)
    write_ogg("warning_beep", fade(normalize(x, 0.75)))


def pocket_charge():
    L = 1.7
    t = t_axis(L)
    p = t / L
    f = 180 + 900 * p ** 1.6
    tone = osc(f, t=t, shape="saw") * 0.35 + osc(f * 2.01, t=t) * 0.2 + osc(f * 0.5, t=t) * 0.25
    tone *= 1 + 0.4 * np.sin(2 * np.pi * (4 + 20 * p) * t)
    tone = biquad(tone, "lp", 900 + 5000 * p)
    whoosh = biquad(noise(L), "bp", 300 + 3000 * p, 1.5) * p ** 2 * 0.8
    crackle = np.zeros(len(t))
    for i in RNG.integers(0, len(t), 220):
        crackle[i] = RNG.uniform(-1, 1) * (i / len(t))
    crackle = biquad(crackle, "hp", 2000)
    x = (tone + whoosh + crackle * 0.8) * env_adsr(len(t), 0.05, 0.1, 0.0, 0.12, 1.0)
    write_ogg("pocket_charge", fade(normalize(saturate(normalize(x), 1.3), 0.8), 0.01, 0.1))


def pocket_fire():
    L = 1.8
    x = np.zeros(int(L * SR))
    t = t_axis(0.35)
    zap = osc(3200 * np.exp(-t / 0.06) + 140, t=t, shape="saw") * env_exp(t, 0.12)
    place(x, zap * 0.8, 0.0)
    t2 = t_axis(1.4)
    suck = biquad(noise(1.4), "bp", 4000 * np.exp(-t2 / 0.35) + 150, 2.0) * env_adsr(len(t2), 0.02, 0.2, 0.5, 0.7, 0.6)
    place(x, suck * 1.6, 0.03)
    place(x, sub_boom(1.0, 110, 40, 0.05, 0.25) * 1.2, 0.0)
    x = reverb(x, 1.6, 0.25, 4000)
    write_ogg("pocket_fire", fade(normalize(saturate(normalize(x), 1.5)), 0.001, 0.25))


def collapse_buildup():
    L = 5.2
    t = t_axis(L)
    p = np.clip(t / 4.7, 0, 1)
    x = np.zeros(len(t))
    # Shepard-style ascent: octave-spaced voices sliding upward, faded at the extremes.
    for k in range(6):
        base = 40 * 2 ** k
        f = base * 2 ** (p * 2.2)
        loud = np.exp(-((np.log2(f) - np.log2(640)) / 1.6) ** 2)
        x += osc(f, t=t) * loud * 0.4
    rumble = biquad(noise(L), "lp", 120 + 400 * p) * (0.4 + 1.2 * p ** 2)
    pulses = np.zeros(len(t))
    tt = 0.0
    while tt < 4.6:
        pt = t_axis(0.25)
        pulses = place(pulses, sub_boom(0.25, 90, 45, 0.03, 0.08), tt)
        tt += 0.55 * (1 - tt / 5.2) + 0.07
    x = x + rumble + pulses * 1.1
    x *= np.where(t < 4.7, 1.0, np.exp(-(t - 4.7) / 0.03))
    write_ogg("collapse_buildup", fade(normalize(saturate(normalize(x), 1.6)), 0.05, 0.05))


def singularity_explosion():
    L = 9.0
    x = np.zeros(int(L * SR))
    t = t_axis(0.06)
    place(x, noise(0.06) * env_exp(t, 0.015) * 1.5, 0.0)
    place(x, sub_boom(7.0, 95, 26, 0.18, 2.4) * 2.0, 0.0)
    place(x, sub_boom(3.0, 220, 60, 0.05, 0.5) * 0.8, 0.0)
    t2 = t_axis(6.0)
    roar = biquad(noise(6.0), "lp", 2500 * np.exp(-t2 / 0.7) + 140, 0.8) * env_exp(t2, 1.6)
    place(x, roar * 1.4, 0.01)
    debris = np.zeros(int(6.0 * SR))
    for i in RNG.integers(int(0.2 * SR), len(debris), 900):
        debris[i] = RNG.uniform(-1, 1) * np.exp(-i / SR / 1.8)
    debris = biquad(debris, "bp", 1800, 0.8)
    place(x, debris * 2.0, 0.1)
    # Ears ringing after the blast.
    t3 = t_axis(6.5)
    ring = osc(4180, t=t3) * np.clip((t3 - 0.3) / 0.6, 0, 1) * env_exp(t3, 2.4) * 0.06
    x = reverb(x, 4.0, 0.4, 2200, pre=0.04)
    x = saturate(normalize(x), 2.2)
    x = place(np.pad(x, (0, 0)), ring, 0.4)
    write_ogg("singularity_explosion", fade(normalize(trim_tail(x)), 0.001, 0.8), quality=6)


def bomb_throw():
    L = 0.9
    t = t_axis(L)
    whoosh = biquad(noise(L), "bp", 600 + 2400 * np.sin(np.pi * t / L), 1.2) * np.sin(np.pi * t / L) ** 2
    hum = osc(220 + 15 * np.sin(2 * np.pi * 9 * t), t=t) * 0.3 * env_exp(t, 0.5) + osc(440, t=t) * 0.1 * env_exp(t, 0.3)
    write_ogg("bomb_throw", fade(normalize(whoosh + hum, 0.75)))


def bomb_captured():
    L = 2.3
    t = t_axis(L)
    p = t / L
    f = 260 * 2 ** (p * 2.2) * (1 + 0.05 * np.sin(2 * np.pi * (6 + 18 * p) * t))
    tone = osc(f, t=t, shape="saw") * 0.4 + osc(f * 1.5, t=t) * 0.2
    tone = biquad(tone, "lp", 1200 + 4000 * p)
    suck = biquad(noise(L), "bp", 200 + 2500 * p, 1.4) * p
    x = (tone + suck) * env_adsr(len(t), 0.03, 0.2, 0.0, 0.2, 1.0)
    x = reverb(x, 1.5, 0.25, 5000)
    write_ogg("bomb_captured", fade(normalize(saturate(normalize(x), 1.4), 0.8)))


def bomb_fizzle():
    L = 1.3
    t = t_axis(L)
    f = 900 * np.exp(-t / 0.35) + 70
    tone = osc(f, t=t, shape="square") * 0.3 * env_exp(t, 0.45)
    tone = biquad(tone, "lp", 2500)
    crackle = np.zeros(len(t))
    for i in RNG.integers(0, len(t), 160):
        crackle[i] = RNG.uniform(-1, 1) * np.exp(-i / SR / 0.4)
    x = tone + biquad(crackle, "hp", 1500) * 0.8
    write_ogg("bomb_fizzle", fade(normalize(x, 0.7)))


def bell(f, seconds, decay):
    t = t_axis(seconds)
    partials = [(1.0, 1.0), (2.76, 0.4), (5.4, 0.2), (8.93, 0.08), (2.0, 0.3)]
    return sum(np.sin(2 * np.pi * f * r * t) * a * env_exp(t, decay / (1 + r * 0.3)) for r, a in partials)


def fragment_hum():
    L = 3.2
    t = t_axis(L)
    chord = sum(osc(f * (1 + d), t=t) * a for f, a, d in [(523.25, 0.3, 0.0), (659.25, 0.22, 0.002), (783.99, 0.18, -0.002),
                                                          (1046.5, 0.12, 0.001), (1567.98, 0.05, 0.0)])
    chord *= 0.7 + 0.3 * np.sin(2 * np.pi * 1.5 * t)
    shimmer = biquad(noise(L), "bp", 6000, 3.0) * 0.05
    x = (chord + shimmer) * env_adsr(len(t), 0.6, 0.3, 0.0, 1.4, 0.8)
    x = reverb(x, 2.0, 0.4, 7000)
    write_ogg("fragment_hum", fade(normalize(x, 0.6), 0.05, 0.3))


def fragment_collect():
    L = 2.6
    x = np.zeros(int(L * SR))
    for i, f in enumerate([1046.5, 1318.5, 1568.0, 2093.0]):
        place(x, bell(f, 1.8, 0.9) * 0.5, i * 0.085)
    t = t_axis(1.0)
    place(x, biquad(noise(1.0), "hp", 5000) * env_exp(t, 0.25) * 0.15, 0.0)
    x = reverb(x, 2.2, 0.35, 8000)
    write_ogg("fragment_collect", fade(normalize(trim_tail(x), 0.8), 0.001, 0.3))


def main():
    print("synthesising sounds ->", os.path.normpath(OUT))
    black_hole_ambient()
    black_hole_birth()
    containment_breach()
    alarm()
    warning_beep()
    pocket_charge()
    pocket_fire()
    collapse_buildup()
    singularity_explosion()
    bomb_throw()
    bomb_captured()
    bomb_fizzle()
    fragment_hum()
    fragment_collect()


if __name__ == "__main__":
    main()
