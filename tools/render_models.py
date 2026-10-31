#!/usr/bin/env python3
"""
Software preview of the mod's JSON block/item models (inventory "gui" pose), used to check the
3D geometry without launching the game and to produce the README gallery:

    python3 tools/render_models.py docs/models.png
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets")
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def load_model(rl):
    ns, path = rl.split(":") if ":" in rl else ("minecraft", rl)
    file = os.path.join(ASSETS, ns, "models", path + ".json")
    if not os.path.exists(file):
        return {}
    m = json.load(open(file))
    if "parent" in m:
        parent = load_model(m["parent"])
        merged = dict(parent)
        merged.update({k: v for k, v in m.items() if k not in ("textures",)})
        tex = dict(parent.get("textures", {}))
        tex.update(m.get("textures", {}))
        merged["textures"] = tex
        if "elements" not in m and "elements" in parent:
            merged["elements"] = parent["elements"]
        if "display" not in m and "display" in parent:
            merged["display"] = parent["display"]
        return merged
    return m


def resolve(tex, key, depth=0):
    v = tex.get(key.lstrip("#"), key)
    if v.startswith("#") and depth < 8:
        return resolve(tex, v, depth + 1)
    return v


def texture(rl, cache={}):
    if rl not in cache:
        ns, path = rl.split(":")
        img = Image.open(os.path.join(ASSETS, ns, "textures", path + ".png")).convert("RGBA")
        w, h = img.size
        if h > w:
            img = img.crop((0, 0, w, w))
        cache[rl] = np.asarray(img).astype(float) / 255.0
    return cache[rl]


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def face_corners(f, t, face):
    x0, y0, z0 = f
    x1, y1, z1 = t
    # Corners ordered (u0,v0) (u1,v0) (u1,v1) (u0,v1) as Minecraft maps face UVs.
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def auto_uv(f, t, face):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
        "up": (x0, z0, x1, z1), "down": (x0, 16 - z1, x1, 16 - z0),
    }[face]


def render(rl, size=256, bg=(28, 28, 34)):
    m = load_model(rl)
    if "elements" not in m and "all" in m.get("textures", {}):
        # minecraft:block/cube_all is not shipped with the mod - rebuild it.
        m["elements"] = [{"from": [0, 0, 0], "to": [16, 16, 16],
                          "faces": {f: {"texture": "#all"} for f in SHADE}}]
    disp = m.get("display", {}).get("gui", {"rotation": [30, 225, 0], "scale": [0.625] * 3, "translation": [0, 0, 0]})
    R = rot_matrix("y", -disp["rotation"][1]) @ rot_matrix("x", disp["rotation"][0])
    R = rot_matrix("x", disp["rotation"][0]) @ rot_matrix("y", -disp["rotation"][1])
    scale = disp.get("scale", [1, 1, 1])[0]
    img = np.zeros((size, size, 3))
    img[:] = np.array(bg) / 255.0
    zbuf = np.full((size, size), -1e9)
    ys, xs = np.mgrid[0:size, 0:size]
    for el in m.get("elements", []):
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        for face, fd in el["faces"].items():
            pts = np.array(face_corners(f, t, face), dtype=float)
            if rot:
                o = np.array(rot["origin"], dtype=float)
                pts = (pts - o) @ rot_matrix(rot["axis"], rot["angle"]).T + o
            p = (pts - 8.0) @ R.T * scale
            sx = size / 2 + p[:, 0] * size / 16 * 0.92
            sy = size / 2 - p[:, 1] * size / 16 * 0.92
            sz = p[:, 2]
            e1 = np.array([sx[1] - sx[0], sy[1] - sy[0]])
            e2 = np.array([sx[3] - sx[0], sy[3] - sy[0]])
            det = e1[0] * e2[1] - e1[1] * e2[0]
            if abs(det) < 1e-6:
                continue
            dx = xs + 0.5 - sx[0]
            dy = ys + 0.5 - sy[0]
            a = (dx * e2[1] - dy * e2[0]) / det
            b = (e1[0] * dy - e1[1] * dx) / det
            inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
            if not inside.any():
                continue
            z = sz[0] + a * (sz[1] - sz[0]) + b * (sz[3] - sz[0])
            uv = fd.get("uv") or auto_uv(f, t, face)
            tex = texture(resolve(m.get("textures", {}), fd["texture"]))
            th, tw = tex.shape[:2]
            u = (uv[0] + a * (uv[2] - uv[0])) / 16.0
            v = (uv[1] + b * (uv[3] - uv[1])) / 16.0
            ti = np.clip((v * th).astype(int), 0, th - 1)
            tj = np.clip((u * tw).astype(int), 0, tw - 1)
            col = tex[ti, tj]
            visible = inside & (z > zbuf) & (col[..., 3] > 0.1)
            light = 1.0 if el.get("shade") is False else SHADE[face]
            img[visible] = col[visible][:, :3] * light
            zbuf[visible] = z[visible]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "models.png"
    models = ["livingblackhole:item/pocket_black_hole", "livingblackhole:item/pocket_black_hole_critical",
              "livingblackhole:item/compressed_chunk", "livingblackhole:item/anti_gravity_bomb",
              "livingblackhole:block/core_fragment"]
    tiles = [render(m) for m in models]
    sheet = Image.new("RGB", (256 * len(tiles), 256))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 256, 0))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
