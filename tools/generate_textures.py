#!/usr/bin/env python3
"""
Procedural texture foundry for Living Black Hole.

Every texture in the mod is synthesised here from maths - spectral (1/f^beta) noise, Voronoi
facets, polar swirl fields and physically motivated radial profiles - so the art is
reproducible and resolution independent. Run from the repository root:

    python3 tools/generate_textures.py

Atlas textures (items/blocks) are 128-256 px per face (8-16x vanilla density) with 16-frame
interpolated animation; free-standing entity/effect textures go up to 1024 px.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
TEX = os.path.join(ROOT, "assets", "livingblackhole", "textures")
TAU = math.tau


# --------------------------------------------------------------------------- utilities

def out(rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def save_rgba(arr, rel, mcmeta=None):
    arr = np.clip(arr, 0.0, 1.0)
    img = Image.fromarray((arr * 255.0 + 0.5).astype(np.uint8), "RGBA")
    path = out(rel)
    img.save(path, optimize=True)
    if mcmeta is not None:
        with open(path + ".mcmeta", "w") as f:
            json.dump(mcmeta, f, indent=2)
            f.write("\n")


def rgba(rgb, alpha=None):
    h, w = rgb.shape[:2]
    a = np.ones((h, w, 1)) if alpha is None else alpha[..., None]
    return np.concatenate([rgb, a], axis=-1)


def additive(intensity_rgb):
    """Additive-blend texture: colour carries the light, alpha stays opaque."""
    return rgba(np.clip(intensity_rgb, 0, 1))


def norm(a):
    lo, hi = a.min(), a.max()
    return (a - lo) / (hi - lo + 1e-12)


def spectral(h, w, beta=2.0, seed=0, ax=1.0, ay=1.0, phase_shift=None):
    """Periodic 1/f^beta noise via inverse FFT. ax/ay stretch features along x/y."""
    rng = np.random.default_rng(seed)
    fy = np.fft.fftfreq(h)[:, None] * h / max(h, w) * ay
    fx = np.fft.fftfreq(w)[None, :] * w / max(h, w) * ax
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    amp = f ** (-beta / 2.0)
    amp[0, 0] = 0.0
    phase = rng.uniform(0, TAU, (h, w))
    if phase_shift is not None:
        k = rng.integers(-2, 3, (h, w))
        phase = phase + k * phase_shift
    spec = amp * np.exp(1j * phase)
    return norm(np.real(np.fft.ifft2(spec)))


def ridged(n):
    return 1.0 - np.abs(n * 2.0 - 1.0)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def gradient(t, stops):
    """Map t in [0,1] through colour stops [(pos, (r,g,b)), ...]."""
    t = np.clip(t, 0.0, 1.0)
    pos = np.array([s[0] for s in stops])
    cols = np.array([s[1] for s in stops], dtype=float)
    return np.stack([np.interp(t, pos, cols[:, c]) for c in range(3)], axis=-1)


def grid(h, w):
    y, x = np.mgrid[0:h, 0:w].astype(float)
    return (x + 0.5) / w, (y + 0.5) / h


def polar(h, w, cx=0.5, cy=0.5):
    u, v = grid(h, w)
    dx, dy = (u - cx) * 2.0, (v - cy) * 2.0
    return np.sqrt(dx * dx + dy * dy), np.arctan2(dy, dx)


def sample_wrap(tex, u, v):
    """Bilinear sample of a periodic 2D field at fractional coordinates (u, v) in texture units."""
    h, w = tex.shape
    x = (u % 1.0) * w - 0.5
    y = (v % 1.0) * h - 0.5
    x0 = np.floor(x).astype(int)
    y0 = np.floor(y).astype(int)
    fx = x - x0
    fy = y - y0
    x0 %= w
    y0 %= h
    x1 = (x0 + 1) % w
    y1 = (y0 + 1) % h
    a = tex[y0, x0] * (1 - fx) + tex[y0, x1] * fx
    b = tex[y1, x0] * (1 - fx) + tex[y1, x1] * fx
    return a * (1 - fy) + b * fy


def blur(arr, radius):
    """Periodic Gaussian blur via FFT (keeps tileable textures seamless)."""
    if radius <= 0:
        return arr
    if arr.ndim == 3:
        return np.stack([blur(arr[..., c], radius) for c in range(arr.shape[-1])], axis=-1)
    h, w = arr.shape
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    kernel = np.exp(-2.0 * (math.pi ** 2) * (radius ** 2) * (fx * fx + fy * fy))
    return np.real(np.fft.ifft2(np.fft.fft2(arr) * kernel))


def voronoi(h, w, n, seed, wrap=True):
    """Returns (F1, F2, cell_id) distance fields for n random sites (periodic if wrap)."""
    rng = np.random.default_rng(seed)
    pts = rng.uniform(0, 1, (n, 2))
    u, v = grid(h, w)
    f1 = np.full((h, w), 9.0)
    f2 = np.full((h, w), 9.0)
    cid = np.zeros((h, w), dtype=int)
    for i, (px, py) in enumerate(pts):
        dx = np.abs(u - px)
        dy = np.abs(v - py)
        if wrap:
            dx = np.minimum(dx, 1 - dx)
            dy = np.minimum(dy, 1 - dy)
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < f1
        f2 = np.where(closer, f1, np.minimum(f2, d))
        cid = np.where(closer, i, cid)
        f1 = np.where(closer, d, f1)
    return f1, f2, cid, rng.uniform(0, 1, n)


def animate(frames):
    return np.concatenate(frames, axis=0)


ANIM = {"animation": {"frametime": 2, "interpolate": True}}
BLUR = {"texture": {"blur": True}}


# --------------------------------------------------------------------------- items

def pocket_casing():
    s = 256
    u, v = grid(s, s)
    base = 0.08 + 0.05 * spectral(s, s, 2.4, 11) + 0.035 * spectral(s, s, 1.2, 12, ax=0.08, ay=3.0)
    col = np.stack([base * 0.92, base * 0.95, base * 1.12], -1)

    # Panel grooves every 64px with a lit lower lip.
    gx = (u * s) % 64
    gy = (v * s) % 64
    groove = ((gx < 2.5) | (gy < 2.5)).astype(float)
    lip = (((gx >= 2.5) & (gx < 4)) | ((gy >= 2.5) & (gy < 4))).astype(float)
    col = col * (1 - 0.65 * groove[..., None]) + lip[..., None] * np.array([0.10, 0.10, 0.13])

    # Bevelled bolts at panel corners.
    cx = (np.round(u * s / 64) * 64) / s
    cy = (np.round(v * s / 64) * 64) / s
    for ox, oy in [(10, 10), (-10, 10), (10, -10), (-10, -10)]:
        d = np.sqrt((u - cx - ox / s) ** 2 + (v - cy - oy / s) ** 2) * s
        bolt = smoothstep(4.5, 3.5, d)
        shade = 0.25 + 0.35 * np.clip(1 - (u - cx - ox / s - (v - cy - oy / s)) * s / 6, 0, 1)
        col = col * (1 - bolt[..., None]) + bolt[..., None] * np.stack([shade * 0.9, shade * 0.92, shade], -1)

    # Glowing containment seams along a subset of grooves.
    seam = (((gx < 2.0) & (((v * s) // 64) % 2 == 0)) | ((gy < 2.0) & (((u * s) // 64) % 2 == 1))).astype(float)
    glow = blur(seam, 3.0) * 1.6 + seam
    pulse = 0.75 + 0.25 * spectral(s, s, 3.0, 13)
    col += (glow * pulse)[..., None] * np.array([0.62, 0.24, 1.0])
    # Edge wear highlights.
    wear = smoothstep(0.82, 0.95, spectral(s, s, 1.6, 14)) * 0.12
    col += wear[..., None]
    save_rgba(rgba(col), "item/pocket_casing.png")


CORE_PALETTES = {
    "stable": [(0.0, (0.0, 0.0, 0.0)), (0.35, (0.07, 0.02, 0.18)), (0.6, (0.32, 0.08, 0.62)),
               (0.82, (0.72, 0.35, 1.0)), (1.0, (1.0, 0.9, 1.0))],
    "unstable": [(0.0, (0.0, 0.0, 0.0)), (0.35, (0.16, 0.02, 0.12)), (0.6, (0.7, 0.18, 0.12)),
                 (0.82, (1.0, 0.6, 0.15)), (1.0, (1.0, 0.95, 0.75))],
    "critical": [(0.0, (0.02, 0.0, 0.0)), (0.3, (0.35, 0.02, 0.0)), (0.55, (0.95, 0.2, 0.05)),
                 (0.8, (1.0, 0.75, 0.35)), (1.0, (1.0, 1.0, 1.0))],
}


def pocket_core(state):
    s = 128
    frames = 16
    field = spectral(256, 256, 1.8, 21 + len(state))
    fine = spectral(256, 256, 1.2, 31)
    r, th = polar(s, s)
    out_frames = []
    for f in range(frames):
        rot = f / frames * TAU
        phi = th - rot + 2.6 * np.log(r + 0.05)
        arms = 0.5 + 0.5 * np.cos(3.0 * phi)
        n = sample_wrap(field, phi / TAU, np.log(r + 0.05) * 0.35)
        n2 = sample_wrap(fine, phi / TAU * 2.0, r * 1.7 - f / frames)
        swirl = (0.55 * arms + 0.45 * n) * (0.7 + 0.3 * n2)
        radial = smoothstep(0.26, 0.42, r) * (1 - smoothstep(0.75, 1.15, r))
        value = swirl * radial
        ring = np.exp(-((r - 0.3) / 0.025) ** 2)
        value = np.clip(value * 1.15 + ring * 0.9, 0, 1)
        col = gradient(value, CORE_PALETTES[state])
        col *= (r > 0.255)[..., None] * 1.0 + (r <= 0.255)[..., None] * 0.0
        if state == "critical":
            cr = ridged(sample_wrap(fine, th / TAU * 3 + rot / TAU, r * 2.0))
            cracks = smoothstep(0.93, 0.99, cr) * smoothstep(0.3, 0.5, r)
            col = col + cracks[..., None] * np.array([1.0, 0.85, 0.6])
        out_frames.append(rgba(col))
    save_rgba(animate(out_frames), f"item/pocket_core_{state}.png", ANIM)


RING_PALETTES = {
    "stable": [(0.0, (0.02, 0.05, 0.12)), (0.45, (0.05, 0.4, 0.75)), (0.75, (0.45, 0.35, 1.0)), (1.0, (0.92, 0.98, 1.0))],
    "unstable": [(0.0, (0.1, 0.03, 0.02)), (0.45, (0.65, 0.25, 0.05)), (0.75, (1.0, 0.55, 0.15)), (1.0, (1.0, 0.95, 0.8))],
    "critical": [(0.0, (0.12, 0.0, 0.0)), (0.45, (0.8, 0.05, 0.02)), (0.75, (1.0, 0.35, 0.2)), (1.0, (1.0, 1.0, 1.0))],
    "charging": [(0.0, (0.08, 0.02, 0.15)), (0.45, (0.5, 0.2, 1.0)), (0.75, (0.9, 0.55, 1.0)), (1.0, (1.0, 1.0, 1.0))],
}


def energy_band(rel, palette, seed, frames=16, s=128, rims=True):
    flow = spectral(s, s, 1.7, seed, ax=0.25, ay=1.6)
    flow2 = spectral(s, s, 1.3, seed + 1, ax=0.5, ay=2.5)
    u, v = grid(s, s)
    out_frames = []
    for f in range(frames):
        shift = f / frames
        n = sample_wrap(flow, u - shift, v)
        n2 = sample_wrap(flow2, u - shift * 2.0, v + 0.0)
        current = np.exp(-((v - 0.5 - 0.12 * np.sin((u - shift) * TAU * 2)) / 0.09) ** 2)
        value = np.clip(0.45 * n + 0.35 * n2 + 0.6 * current, 0, 1) ** 1.2
        col = gradient(value, palette)
        if rims:
            rim = ((v < 0.12) | (v > 0.88)).astype(float)
            metal = 0.25 + 0.15 * spectral(s, s, 2.0, seed + 7)
            col = col * (1 - rim[..., None]) + rim[..., None] * np.stack([metal * 0.85, metal * 0.9, metal], -1)
            edge = ((np.abs(v - 0.12) < 0.012) | (np.abs(v - 0.88) < 0.012)).astype(float)
            col += edge[..., None] * 0.25
        out_frames.append(rgba(col))
    save_rgba(animate(out_frames), rel, ANIM)


def compressed_matter():
    s = 256
    f1, f2, cid, rnd = voronoi(s, s, 40, 41)
    facet = rnd[cid]
    base = 0.035 + 0.06 * spectral(s, s, 2.2, 42) + 0.035 * facet
    col = np.stack([base * 1.0, base * 0.85, base * 1.25], -1)
    edges = smoothstep(0.012, 0.0, f2 - f1)
    col += edges[..., None] * np.array([0.05, 0.03, 0.09])
    veins = ridged(spectral(s, s, 2.0, 43)) ** 9
    veins = smoothstep(0.35, 0.9, veins)
    glow = blur(veins, 3.5) * 1.4 + veins
    col += glow[..., None] * np.array([0.6, 0.22, 1.0])
    rng = np.random.default_rng(44)
    stars = np.zeros((s, s))
    for _ in range(70):
        x, y = rng.integers(0, s, 2)
        stars[y, x] = rng.uniform(0.5, 1.0)
    col += blur(stars, 0.8)[..., None] * np.array([0.9, 0.85, 1.0]) * 3.0
    save_rgba(rgba(col), "item/compressed_matter.png")


def compressed_matter_core():
    s = 128
    frames = 8
    u, v = grid(s, s)
    r, th = polar(s, s)
    out_frames = []
    for f in range(frames):
        n = spectral(s, s, 1.9, 51, phase_shift=TAU * f / frames)
        pulse = 0.75 + 0.25 * math.sin(TAU * f / frames)
        value = np.clip(n * 0.8 + np.exp(-(r / 0.6) ** 2) * 0.5 * pulse, 0, 1)
        col = gradient(value, [(0.0, (0.05, 0.0, 0.12)), (0.5, (0.45, 0.12, 0.9)), (0.8, (0.8, 0.5, 1.0)), (1.0, (1.0, 0.95, 1.0))])
        out_frames.append(rgba(col))
    save_rgba(animate(out_frames), "item/compressed_matter_core.png", ANIM)


def bomb_shell():
    s = 256
    u, v = grid(s, s)
    size = 22.0 / s
    # Hex grid (pointy-top) cell coordinates.
    qx = u / (math.sqrt(3) * size)
    qy = v / (1.5 * size)
    row = np.floor(qy)
    qx_shift = qx - 0.5 * (row % 2)
    col_i = np.floor(qx_shift)
    best = np.full((s, s), 9.0)
    cell = np.zeros((s, s, 2))
    for dr in (-1, 0, 1):
        for dc in (-1, 0, 1):
            rr = row + dr
            cc = np.floor(qx - 0.5 * (rr % 2)) + dc
            cxp = (cc + 0.5 + 0.5 * (rr % 2)) * math.sqrt(3) * size
            cyp = (rr + 0.5) * 1.5 * size
            d = np.sqrt((u - cxp) ** 2 + (v - cyp) ** 2)
            m = d < best
            best = np.where(m, d, best)
            cell[..., 0] = np.where(m, u - cxp, cell[..., 0])
            cell[..., 1] = np.where(m, v - cyp, cell[..., 1])
    # Hex distance for bevels.
    ax = np.abs(cell[..., 0])
    ay = np.abs(cell[..., 1])
    hexd = np.maximum(ax * math.sqrt(3) / 2 + ay * 0.5, ay) / size
    bevel = smoothstep(0.78, 0.92, hexd)
    light = np.clip(0.5 - (cell[..., 0] + cell[..., 1]) / size * 0.6, 0, 1)
    base = 0.14 + 0.06 * spectral(s, s, 2.2, 61)
    metal = base * (1 - 0.5 * bevel) + bevel * light * 0.22
    col = np.stack([metal * 0.86, metal * 0.93, metal * 1.05], -1)
    groove = smoothstep(0.93, 0.98, hexd)
    col *= (1 - 0.7 * groove)[..., None]
    rng = np.random.default_rng(62)
    lights = np.zeros((s, s))
    for _ in range(9):
        x, y = rng.uniform(0, 1, 2)
        d = np.sqrt((u - x) ** 2 + (v - y) ** 2) * s
        lights += np.exp(-(d / 2.2) ** 2)
    col += (blur(lights, 2.5) * 1.5 + lights)[..., None] * np.array([0.2, 0.9, 1.0])
    save_rgba(rgba(col), "item/bomb_shell.png")


def bomb_metal():
    s = 256
    u, v = grid(s, s)
    brushed = spectral(s, s, 1.0, 71, ax=0.04, ay=4.0)
    base = 0.42 + 0.14 * brushed + 0.06 * spectral(s, s, 2.0, 72)
    col = np.stack([base * 0.95, base * 0.98, base * 1.04], -1)
    border = np.minimum(np.minimum(u, 1 - u), np.minimum(v, 1 - v)) * s
    col *= (0.65 + 0.35 * smoothstep(0, 8, border))[..., None]
    for x, y in [(0.15, 0.15), (0.85, 0.15), (0.15, 0.85), (0.85, 0.85)]:
        d = np.sqrt((u - x) ** 2 + (v - y) ** 2) * s
        screw = smoothstep(9, 7.5, d)
        slot = (np.abs((u - x) * s) < 1.2) & (d < 7)
        col = col * (1 - screw[..., None] * 0.35)
        col[slot] *= 0.3
    save_rgba(rgba(col), "item/bomb_metal.png")


# --------------------------------------------------------------------------- blocks

def core_crystal():
    s = 128
    frames = 16
    f1, f2, cid, rnd = voronoi(s, s, 26, 81)
    u, v = grid(s, s)
    hue = rnd[cid]
    palette = [(0.0, (0.1, 0.85, 1.0)), (0.35, (0.2, 1.0, 0.85)), (0.65, (0.55, 0.45, 1.0)), (1.0, (0.95, 0.4, 1.0))]
    base = gradient(hue, palette)
    shade = 0.45 + 0.55 * (1 - f1 / (f1.max() + 1e-9)) ** 0.7
    edges = smoothstep(0.02, 0.0, f2 - f1)
    sparkle_field = spectral(s, s, 0.6, 82)
    out_frames = []
    for f in range(frames):
        t = f / frames
        sweep = np.exp(-(((u + v) * 0.5 - t) % 1.0 - 0.5) ** 2 / 0.006)
        sweep = np.exp(-((((u + v) * 0.5 - t + 0.5) % 1.0 - 0.5) / 0.08) ** 2)
        tw = smoothstep(0.985, 1.0, sample_wrap(sparkle_field, u + t * 0.37, v - t * 0.21))
        col = base * shade[..., None] * (0.75 + 0.25 * np.sin(TAU * (t + hue)))[..., None]
        col += edges[..., None] * np.array([0.7, 1.0, 1.0]) * 0.8
        col += sweep[..., None] * 0.55 + tw[..., None] * 1.5
        out_frames.append(rgba(col))
    save_rgba(animate(out_frames), "block/core_fragment_crystal.png", ANIM)


def core_base():
    s = 256
    f1, f2, cid, rnd = voronoi(s, s, 34, 91)
    base = 0.035 + 0.05 * spectral(s, s, 2.4, 92) + 0.02 * rnd[cid]
    col = np.stack([base * 0.85, base * 0.7, base * 1.3], -1)
    gloss = smoothstep(0.86, 0.98, spectral(s, s, 1.0, 93, ax=0.3, ay=2.0)) * 0.18
    col += gloss[..., None] * np.array([0.6, 0.55, 0.9])
    crack = smoothstep(0.007, 0.0, f2 - f1) * smoothstep(0.35, 0.65, spectral(s, s, 2.0, 94))
    glow = blur(crack, 2.5) * 1.3 + crack
    col += glow[..., None] * np.array([0.2, 0.95, 1.0])
    save_rgba(rgba(col), "block/core_fragment_base.png")


def compressed_block():
    s = 256
    frames = 8
    f1, f2, cid, rnd = voronoi(s, s, 22, 101)
    u, v = grid(s, s)
    base = 0.04 + 0.05 * spectral(s, s, 2.2, 102) + 0.03 * rnd[cid]
    rock = np.stack([base, base * 0.85, base * 1.25], -1)
    edges = smoothstep(0.01, 0.0, f2 - f1)
    rock += edges[..., None] * np.array([0.08, 0.05, 0.14])
    veins_raw = ridged(spectral(s, s, 2.0, 103)) ** 8
    veins = smoothstep(0.3, 0.9, veins_raw)
    phase = spectral(s, s, 1.5, 104)
    frame_edge = np.minimum(np.minimum(u, 1 - u), np.minimum(v, 1 - v)) * s
    bevel = smoothstep(0, 10, frame_edge)
    out_frames = []
    for f in range(frames):
        t = f / frames
        pulse = 0.55 + 0.45 * np.sin(TAU * (t + phase * 2.0)) ** 2
        glow = (blur(veins, 3.0) * 1.3 + veins) * pulse
        col = rock + glow[..., None] * np.array([0.62, 0.24, 1.0])
        col *= (0.55 + 0.45 * bevel)[..., None]
        out_frames.append(rgba(col))
    save_rgba(animate(out_frames), "block/compressed_matter_block.png", ANIM)


# --------------------------------------------------------------------------- particles

def particle_set():
    s = 64
    u, v = grid(s, s)
    x, y = (u - 0.5) * 2, (v - 0.5) * 2
    r = np.sqrt(x * x + y * y)
    white = lambda a: additive(np.repeat(np.clip(a, 0, 1)[..., None], 3, axis=-1))
    edge = 1 - smoothstep(0.85, 1.0, r)

    glows = [
        np.exp(-(r / 0.35) ** 2) * 0.9 + np.exp(-(r / 0.12) ** 2) * 0.6,
        np.exp(-(r / 0.5) ** 2) * 0.8,
        np.exp(-(r / 0.25) ** 2) + np.exp(-((r - 0.55) / 0.08) ** 2) * 0.35,
        np.exp(-(r / 0.2) ** 2) + (np.exp(-(np.abs(x) / 0.05)) * np.exp(-(np.abs(y) / 0.6)) + np.exp(-(np.abs(y) / 0.05)) * np.exp(-(np.abs(x) / 0.6))) * 0.5,
    ]
    for i, g in enumerate(glows):
        save_rgba(white(g * edge), f"particle/glow_{i}.png")

    for i, (width, length, rot) in enumerate([(0.035, 0.9, 0.0), (0.05, 0.7, math.pi / 4), (0.03, 1.0, 0.3), (0.06, 0.55, 0.0)]):
        c, sn = math.cos(rot), math.sin(rot)
        xr, yr = x * c - y * sn, x * sn + y * c
        rays = np.exp(-(np.abs(yr) / width)) * np.exp(-np.abs(xr) / length * 2.2) + np.exp(-(np.abs(xr) / width)) * np.exp(-np.abs(yr) / length * 2.2)
        core = np.exp(-(r / 0.13) ** 2)
        save_rgba(white((rays * 0.85 + core) * edge), f"particle/spark_{i}.png")

    save_rgba(white((np.exp(-(r / 0.16) ** 2) + np.exp(-(r / 0.45) ** 2) * 0.35) * edge), "particle/mote_0.png")
    save_rgba(white((np.exp(-(r / 0.12) ** 2) + np.exp(-((r - 0.6) / 0.07) ** 2) * 0.6) * edge), "particle/mote_1.png")
    save_rgba(white((np.exp(-((r - 0.62) / 0.06) ** 2) + np.exp(-(r / 0.2) ** 2) * 0.4) * edge), "particle/ring_0.png")

    # Streak: bright head at u=0 tapering to a tail at u=1, soft across v.
    head = np.exp(-u * 2.8) * 0.85 + np.exp(-(u / 0.08) ** 2) * 0.5
    across = np.exp(-((v - 0.5) / 0.2) ** 2)
    save_rgba(white(head * across * (1 - smoothstep(0.92, 1.0, u))), "particle/streak.png")

    # Crystal glint lifecycle: grow, flare, shrink.
    for i, size in enumerate([0.35, 0.65, 0.9, 0.5]):
        diamond = np.abs(x) + np.abs(y)
        outline = np.exp(-((diamond - size * 0.8) / 0.05) ** 2)
        core = np.exp(-(r / (0.18 * size + 0.05)) ** 2)
        cross = (np.exp(-np.abs(x) / 0.03) * np.exp(-np.abs(y) / (0.5 * size + 0.05)) +
                 np.exp(-np.abs(y) / 0.03) * np.exp(-np.abs(x) / (0.5 * size + 0.05))) * 0.6
        save_rgba(white((outline * 0.7 + core + cross) * edge), f"particle/crystal_{i}.png")


# --------------------------------------------------------------------------- entity / effect textures

DISK_COLORS = [(0.0, (1.0, 0.97, 0.88)), (0.12, (1.0, 0.85, 0.55)), (0.3, (1.0, 0.6, 0.22)),
               (0.6, (0.85, 0.3, 0.1)), (1.0, (0.45, 0.08, 0.12))]


def disk():
    w, h = 1024, 256
    u, v = grid(h, w)
    streaks = spectral(h, w, 1.7, 111, ax=0.12, ay=1.0)
    filaments = spectral(h, w, 1.1, 112, ax=0.06, ay=1.0)
    clumps = spectral(h, w, 2.6, 113, ax=0.5, ay=1.0)
    turbulence = 0.3 + 0.7 * (0.55 * streaks + 0.3 * filaments + 0.15 * clumps) ** 1.6
    profile = smoothstep(0.0, 0.035, v) * (v + 0.04) ** -0.85 * 0.18
    profile *= 1 - smoothstep(0.7, 1.0, v)
    hot = np.exp(-((v - 0.03) / 0.025) ** 2) * 0.6
    intensity = np.clip(profile * turbulence * 1.25 + hot, 0, 1.6)
    col = gradient(v, DISK_COLORS) * np.clip(intensity, 0, 1.0)[..., None]
    col += np.clip(intensity - 1.0, 0, 1)[..., None] * 0.6
    save_rgba(additive(col), "entity/black_hole/disk.png", BLUR)


def halo():
    s = 1024
    r, th = polar(s, s)
    noise = spectral(64, 512, 1.5, 121, ax=0.3, ay=1.0)
    ang = sample_wrap(noise, th / TAU, np.zeros_like(th) + 0.5)
    rp = 0.447
    ring = np.exp(-((r - rp) / 0.0055) ** 2)
    inner_glow = np.exp(-((r - rp) / 0.03) ** 2) * 0.35 * (0.8 + 0.4 * ang)
    outer = np.exp(-np.clip(r - rp, 0, None) / 0.12) * 0.22 * (r > rp)
    i = (ring + inner_glow + outer) * (1 - smoothstep(0.85, 1.0, r)) * (r > 0.42)
    col = np.stack([i, i * 0.9, i * 0.75], -1)
    save_rgba(additive(col), "entity/black_hole/halo.png", BLUR)


def arcs():
    s = 1024
    r, th = polar(s, s)
    rin = 0.345
    weight = np.abs(np.sin(th)) ** 1.3
    streaks = spectral(128, 1024, 1.6, 131, ax=0.15, ay=1.0)
    n = sample_wrap(streaks, th / TAU * 2.0, (r - rin) * 3.0)
    radial = smoothstep(rin - 0.005, rin + 0.01, r) * np.exp(-np.clip(r - rin, 0, None) / 0.09)
    i = radial * weight * (0.45 + 0.75 * n ** 1.4) * (1 - smoothstep(0.7, 0.9, r))
    t = np.clip((r - rin) / 0.25, 0, 1)
    col = gradient(t, DISK_COLORS) * np.clip(i * 1.4, 0, 1)[..., None]
    save_rgba(additive(col), "entity/black_hole/arcs.png", BLUR)


def corona():
    s = 512
    r, th = polar(s, s)
    wisps = spectral(64, 512, 1.4, 141, ax=0.25, ay=1.0)
    n = sample_wrap(wisps, th / TAU, r * 0.8)
    i = (np.exp(-r / 0.16) * 0.65 + np.exp(-(r / 0.5) ** 2) * 0.35) * (0.7 + 0.45 * n)
    i *= 1 - smoothstep(0.75, 1.0, r)
    save_rgba(additive(np.repeat(i[..., None], 3, -1)), "entity/black_hole/corona.png", BLUR)


def jet():
    w, h = 128, 512
    u, v = grid(h, w)
    knots = spectral(h, w, 1.6, 151, ax=1.5, ay=0.2)
    fil = spectral(h, w, 1.1, 152, ax=3.0, ay=0.15)
    across = np.exp(-((u - 0.5) / 0.14) ** 2) + np.exp(-((u - 0.5) / 0.36) ** 2) * 0.4
    i = across * (0.45 + 0.55 * knots) * (0.75 + 0.35 * fil)
    col = np.stack([i * 0.9, i * 0.95, i], -1)
    save_rgba(additive(col), "entity/black_hole/jet.png", BLUR)


def flare():
    s = 512
    u, v = grid(s, s)
    x, y = (u - 0.5) * 2, (v - 0.5) * 2
    r = np.sqrt(x * x + y * y)
    th = np.arctan2(y, x)
    i = np.exp(-(r / 0.06) ** 2) + np.exp(-r / 0.09) * 0.55
    for k in range(8):
        a = k * math.pi / 4 + 0.13
        along = x * math.cos(a) + y * math.sin(a)
        perp = -x * math.sin(a) + y * math.cos(a)
        length = 0.55 if k % 2 == 0 else 0.32
        i += np.exp(-(perp / 0.006) ** 2) * np.exp(-np.abs(along) / length) * (0.8 if k % 2 == 0 else 0.45)
    i += np.exp(-(y / 0.012) ** 2) * np.exp(-np.abs(x) / 0.6) * 0.6
    i += np.exp(-((r - 0.38) / 0.01) ** 2) * 0.12 * (0.6 + 0.4 * np.cos(th * 12))
    i *= 1 - smoothstep(0.85, 1.0, r)
    col = np.stack([np.clip(i, 0, 1), np.clip(i * 0.97, 0, 1), np.clip(i * 1.0, 0, 1)], -1)
    save_rgba(additive(col), "entity/black_hole/flare.png", BLUR)


def soft_glow(rel, s=256, sigma=0.35):
    r, _ = polar(s, s)
    i = np.exp(-(r / sigma) ** 2) * (1 - smoothstep(0.85, 1.0, r))
    save_rgba(additive(np.repeat(i[..., None], 3, -1)), rel, BLUR)


def shockwave():
    w, h = 1024, 128
    u, v = grid(h, w)
    breakup = spectral(h, w, 1.5, 161, ax=0.2, ay=1.0)
    front = np.exp(-((v - 0.93) / 0.035) ** 2)
    trail = (v ** 2.2) * 0.45
    i = (front + trail) * (0.55 + 0.6 * breakup) * (1 - smoothstep(0.97, 1.0, v)) * smoothstep(0.0, 0.1, v)
    col = np.stack([i, i, i], -1)
    save_rgba(additive(np.clip(col, 0, 1)), "entity/black_hole/shockwave.png", BLUR)


def plasma():
    s = 512
    n = 0.6 * ridged(spectral(s, s, 1.9, 171)) ** 2 + 0.4 * spectral(s, s, 1.3, 172)
    i = 0.25 + 0.75 * n
    save_rgba(additive(np.repeat(i[..., None], 3, -1)), "entity/black_hole/plasma.png", BLUR)


def void():
    s = 64
    save_rgba(rgba(np.zeros((s, s, 3))), "entity/black_hole/void.png")


def bomb_halo():
    s = 256
    r, th = polar(s, s)
    i = np.exp(-(r / 0.3) ** 2) * 0.55 + np.exp(-((r - 0.62) / 0.05) ** 2) * 0.6 * (0.7 + 0.3 * np.cos(th * 6))
    i *= 1 - smoothstep(0.85, 1.0, r)
    save_rgba(additive(np.repeat(i[..., None], 3, -1)), "entity/bomb_halo.png", BLUR)


def bomb_ring():
    s = 256
    r, th = polar(s, s)
    ring = np.exp(-((r - 0.86) / 0.025) ** 2)
    ticks = (np.cos(th * 24) > 0.85) * np.exp(-((r - 0.76) / 0.03) ** 2) * 0.8
    inner = np.exp(-((r - 0.7) / 0.012) ** 2) * 0.5
    i = (ring + ticks + inner) * (1 - smoothstep(0.95, 1.0, r))
    save_rgba(additive(np.repeat(i[..., None], 3, -1)), "entity/bomb_ring.png", BLUR)


def beam(rel, w, h, seed, core=0.13, pulses=6):
    u, v = grid(h, w)
    n = spectral(h, w, 1.5, seed, ax=1.2, ay=0.25)
    across = np.exp(-((u - 0.5) / core) ** 2) + np.exp(-((u - 0.5) / 0.34) ** 2) * 0.35
    bands = 0.75 + 0.25 * np.sin(v * TAU * pulses) ** 2
    i = across * bands * (0.6 + 0.5 * n)
    save_rgba(additive(np.repeat(np.clip(i, 0, 1)[..., None], 3, -1)), rel, BLUR)


def core_halo():
    s = 256
    u, v = grid(s, s)
    x, y = (u - 0.5) * 2, (v - 0.5) * 2
    r = np.sqrt(x * x + y * y)
    th = np.arctan2(y, x)
    hexr = np.cos(math.pi / 6) / np.cos((th % (math.pi / 3)) - math.pi / 6)
    hexd = r / hexr
    i = np.exp(-(r / 0.25) ** 2) * 0.6 + np.exp(-((hexd - 0.7) / 0.03) ** 2) * 0.55 + np.exp(-((hexd - 0.82) / 0.015) ** 2) * 0.3
    i *= 1 - smoothstep(0.88, 1.0, r)
    save_rgba(additive(np.repeat(i[..., None], 3, -1)), "entity/core_halo.png", BLUR)


# --------------------------------------------------------------------------- gui

def vignette():
    s = 512
    u, v = grid(s, s)
    x, y = (u - 0.5) * 2, (v - 0.5) * 2
    r = np.sqrt((x ** 2) * 0.9 + (y ** 2) * 1.1)
    a = smoothstep(0.55, 1.35, r) ** 1.35
    a = np.clip(a + 0.06 * (spectral(s, s, 2.0, 181) - 0.5) * a, 0, 1)
    save_rgba(rgba(np.ones((s, s, 3)), a), "gui/vignette.png", BLUR)


def static_noise():
    s = 256
    rng = np.random.default_rng(191)
    n = rng.uniform(0, 1, (s, s))
    bands = np.repeat(rng.uniform(0.6, 1.0, (s, 1)), s, axis=1)
    i = np.clip(n * bands, 0, 1)
    save_rgba(rgba(np.repeat(i[..., None], 3, -1)), "gui/static.png")


def advancement_bg():
    s = 64
    neb = spectral(s, s, 2.3, 201)
    neb2 = spectral(s, s, 1.8, 202)
    col = np.stack([0.02 + 0.10 * neb ** 2, 0.005 + 0.03 * neb2 ** 3, 0.05 + 0.16 * neb ** 2], -1)
    rng = np.random.default_rng(203)
    for _ in range(26):
        x, y = rng.integers(0, s, 2)
        b = rng.uniform(0.5, 1.0)
        col[y, x] = np.clip(col[y, x] + b, 0, 1)
        if b > 0.85:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                col[(y + dy) % s, (x + dx) % s] += b * 0.3
    save_rgba(rgba(col), "gui/advancements/backgrounds/void.png")


# --------------------------------------------------------------------------- logo

def logo():
    w, h = 600, 300
    u, v = grid(h, w)
    x = (u - 0.5) * 2 * w / h
    y = (v - 0.47) * 2
    rng = np.random.default_rng(301)
    sky = np.zeros((h, w, 3))
    neb = spectral(h, w, 2.2, 302)
    sky += np.stack([0.05 * neb ** 2, 0.0 * neb, 0.1 * neb ** 2], -1)
    for _ in range(420):
        sx, sy = rng.integers(0, w), rng.integers(0, h)
        sky[sy, sx] += rng.uniform(0.3, 1.0)

    rs = 0.28
    # Lensing of the starfield: sample the sky through a point-lens mapping.
    r = np.sqrt(x * x + y * y) + 1e-6
    e = rs * 1.6
    beta = r - e * e / r
    src = np.sign(beta) * np.sqrt(beta * beta + (1.1 * rs) ** 2)
    sxp = x / r * src
    syp = y / r * src
    px = np.clip(((sxp * h / w / 2) + 0.5) * w, 0, w - 1).astype(int)
    py = np.clip(((syp / 2) + 0.47) * h, 0, h - 1).astype(int)
    lensed = sky[py, px]
    img = lensed.copy()

    # Tilted accretion disk (front part) and lensed back arcs.
    incl = 0.22
    dz = y / incl
    dr = np.sqrt(x * x + dz * dz)
    disk_t = (dr - rs * 1.35) / (rs * 3.2)
    disk_mask = (disk_t > 0) & (disk_t < 1)
    ang = np.arctan2(dz, x)
    streak = spectral(256, 1024, 1.6, 303, ax=0.12, ay=1.0)
    n = sample_wrap(streak, ang / TAU * 3, np.clip(disk_t, 0, 1))
    dop = 1 + 0.6 * np.cos(ang)
    disk_i = np.where(disk_mask, np.clip(1 - disk_t, 0, 1) ** 1.6 * (0.35 + 0.8 * n) * dop, 0)
    disk_col = gradient(np.clip(disk_t, 0, 1), DISK_COLORS) * np.clip(disk_i, 0, 1.2)[..., None]
    front = (y > 0) | (r > rs)
    img = np.where((disk_mask & front)[..., None], img * 0.3 + disk_col, img)

    arc_r = rs * 1.25
    arc = np.exp(-((r - arc_r) / 0.035) ** 2) * (np.abs(y) / r) ** 1.2
    img += arc[..., None] * np.array([1.0, 0.75, 0.4]) * 0.9
    ring = np.exp(-((r - rs * 1.04) / 0.008) ** 2)
    img += ring[..., None] * np.array([1.0, 0.92, 0.8])
    img = np.where((r < rs)[..., None] & ~(disk_mask & (y > 0))[..., None], 0, img)
    img += blur(np.clip(img - 0.7, 0, 1), 6) * 0.6

    pil = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")
    draw = ImageDraw.Draw(pil)
    font_path = "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"
    try:
        font = ImageFont.truetype(font_path, 46)
        small = ImageFont.truetype(font_path, 16)
    except OSError:
        font = ImageFont.load_default()
        small = font
    text = "LIVING BLACK HOLE"
    tw = draw.textlength(text, font=font)
    glow = Image.new("RGBA", pil.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.text(((w - tw) / 2, h - 78), text, font=font, fill=(170, 90, 255, 255))
    glow = glow.filter(ImageFilter.GaussianBlur(7))
    pil = Image.alpha_composite(pil, glow)
    draw = ImageDraw.Draw(pil)
    draw.text(((w - tw) / 2, h - 78), text, font=font, fill=(245, 236, 255, 255))
    sub = "FORGE 1.20.1  -  SINGULARITY EVENT"
    sw = draw.textlength(sub, font=small)
    draw.text(((w - sw) / 2, h - 26), sub, font=small, fill=(255, 120, 90, 255))
    os.makedirs(ROOT, exist_ok=True)
    pil.save(os.path.join(ROOT, "logo.png"), optimize=True)


def main():
    pocket_casing()
    for state in ("stable", "unstable", "critical"):
        pocket_core(state)
    for i, (state, palette) in enumerate(RING_PALETTES.items()):
        energy_band(f"item/pocket_ring_{state}.png", palette, 200 + i)
    compressed_matter()
    compressed_matter_core()
    bomb_shell()
    energy_band("item/bomb_band.png", [(0.0, (0.0, 0.08, 0.12)), (0.45, (0.0, 0.55, 0.75)), (0.75, (0.3, 0.95, 1.0)), (1.0, (0.92, 1.0, 1.0))], 251, rims=False)
    bomb_metal()
    core_crystal()
    core_base()
    compressed_block()
    particle_set()
    disk()
    halo()
    arcs()
    corona()
    jet()
    flare()
    soft_glow("entity/black_hole/glow.png")
    shockwave()
    plasma()
    void()
    bomb_halo()
    bomb_ring()
    beam("entity/core_beam.png", 64, 512, 401, core=0.12, pulses=8)
    beam("entity/pocket_beam.png", 64, 256, 402, core=0.1, pulses=4)
    core_halo()
    vignette()
    static_noise()
    advancement_bg()
    logo()
    print("textures written to", os.path.normpath(TEX))


if __name__ == "__main__":
    main()
