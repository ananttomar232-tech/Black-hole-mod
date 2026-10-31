#!/usr/bin/env python3
"""
Writes every JSON asset and data file of Living Black Hole: 3D element models, blockstates,
particle definitions, sounds.json, language file, recipes, loot tables, tags, damage type and
advancements. Run from the repository root:

    python3 tools/generate_data.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
MOD = "livingblackhole"
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data")

FACES = ("north", "south", "east", "west", "up", "down")
EMISSIVE = {"block_light": 15, "sky_light": 15}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def asset(rel, obj):
    write(os.path.join(ASSETS, rel), obj)


def data(namespace, rel, obj):
    write(os.path.join(DATA, namespace, rel), obj)


def ns(path):
    return f"{MOD}:{path}"


# --------------------------------------------------------------------------- model helpers

def box(frm, to, tex, rot=None, glow=False, full_uv=False, name=None):
    faces = {}
    for face in FACES:
        f = {"texture": tex}
        if full_uv:
            f["uv"] = [0, 0, 16, 16]
        faces[face] = f
    el = {"from": frm, "to": to, "faces": faces}
    if name:
        el["name"] = name
    if rot:
        axis, angle, origin = rot
        el["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    if glow:
        el["shade"] = False
        el["forge_data"] = dict(EMISSIVE)
    return el


def display(gui=(25, 45, 0), gui_scale=0.8, ground=0.45, fixed=0.8, tp=(0, 45, 0), tp_t=(0, 2.5, 1.5), tp_s=0.5,
            fp=(0, -40, 0), fp_t=(1.2, 3.2, 1.0), fp_s=0.55, head_t=(0, 14, 0), head_s=0.6):
    return {
        "gui": {"rotation": list(gui), "translation": [0, 0, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [ground] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [fixed] * 3},
        "thirdperson_righthand": {"rotation": list(tp), "translation": list(tp_t), "scale": [tp_s] * 3},
        "thirdperson_lefthand": {"rotation": list(tp), "translation": list(tp_t), "scale": [tp_s] * 3},
        "firstperson_righthand": {"rotation": list(fp), "translation": list(fp_t), "scale": [fp_s] * 3},
        "firstperson_lefthand": {"rotation": list(fp), "translation": list(fp_t), "scale": [fp_s] * 3},
        "head": {"rotation": [0, 0, 0], "translation": list(head_t), "scale": [head_s] * 3},
    }


# --------------------------------------------------------------------------- models

def pocket_models():
    c = [8, 8, 8]
    elements = [
        # Event-horizon core: three interpenetrating slabs read as a sphere.
        box([4.5, 5.5, 5.5], [11.5, 10.5, 10.5], "#core", glow=True, full_uv=True, name="core_x"),
        box([5.5, 4.5, 5.5], [10.5, 11.5, 10.5], "#core", glow=True, full_uv=True, name="core_y"),
        box([5.5, 5.5, 4.5], [10.5, 10.5, 11.5], "#core", glow=True, full_uv=True, name="core_z"),
        box([5.1, 5.3, 5.1], [10.9, 10.7, 10.9], "#core", rot=("y", 45, c), glow=True, full_uv=True, name="core_round"),
    ]
    # Containment ring A: square frame in the XY plane, turned 45 degrees into a diamond.
    ring_a = [([3, 12, 7.25], [13, 13, 8.75]), ([3, 3, 7.25], [13, 4, 8.75]),
              ([3, 4, 7.25], [4, 12, 8.75]), ([12, 4, 7.25], [13, 12, 8.75])]
    for i, (f, t) in enumerate(ring_a):
        elements.append(box(f, t, "#ring", rot=("z", 45, c), glow=True, name=f"ring_a{i}"))
    # Containment ring B: frame in the YZ plane, tilted 22.5 degrees - a gyroscope cage.
    ring_b = [([7.4, 12.6, 3.4], [8.6, 13.4, 12.6]), ([7.4, 3.4, 3.4], [8.6, 4.2, 12.6]),
              ([7.4, 4.2, 3.4], [8.6, 12.6, 4.2]), ([7.4, 4.2, 11.8], [8.6, 12.6, 12.6])]
    for i, (f, t) in enumerate(ring_b):
        elements.append(box(f, t, "#ring", rot=("x", -22.5, c), glow=True, name=f"ring_b{i}"))
    elements += [
        box([5.5, 0, 5.5], [10.5, 1.5, 10.5], "#casing", name="base"),
        box([4.5, 0.5, 4.5], [11.5, 1.0, 11.5], "#casing", name="base_flange"),
        box([6.5, 1.5, 6.5], [9.5, 3.2, 9.5], "#casing", name="neck"),
        box([6.5, 12.8, 6.5], [9.5, 14.4, 9.5], "#casing", name="cap"),
        box([7.5, 14.4, 7.5], [8.5, 16, 8.5], "#ring", glow=True, name="antenna"),
        box([2.2, 7.4, 7.4], [3.4, 8.6, 8.6], "#casing", name="emitter_w"),
        box([12.6, 7.4, 7.4], [13.8, 8.6, 8.6], "#casing", name="emitter_e"),
    ]
    base = {
        "credit": "Living Black Hole - containment lattice",
        "gui_light": "side",
        "ambientocclusion": False,
        "textures": {"particle": "#casing"},
        "elements": elements,
        "display": display(gui_scale=0.85, ground=0.4, tp=(10, 45, 0), tp_t=(0, 3, 1.5), tp_s=0.45,
                           fp=(0, -35, 0), fp_t=(1.6, 3.4, 0.8), fp_s=0.4),
    }
    asset("models/item/pocket_black_hole_base.json", base)

    def variant(name, core, ring):
        return {"parent": ns("item/pocket_black_hole_base"),
                "textures": {"casing": ns("item/pocket_casing"), "core": ns(f"item/pocket_core_{core}"),
                             "ring": ns(f"item/pocket_ring_{ring}"), "particle": ns("item/pocket_casing")}}

    main = variant("stable", "stable", "stable")
    main["overrides"] = [
        {"predicate": {ns("instability"): 0.5}, "model": ns("item/pocket_black_hole_unstable")},
        {"predicate": {ns("instability"): 0.85}, "model": ns("item/pocket_black_hole_critical")},
    ]
    asset("models/item/pocket_black_hole.json", main)
    asset("models/item/pocket_black_hole_unstable.json", variant("unstable", "unstable", "unstable"))
    asset("models/item/pocket_black_hole_critical.json", variant("critical", "critical", "critical"))


def chunk_model():
    # A miniature landscape cube (grass top, strata sides) floating in a glowing containment cage.
    full = {"uv": [0, 0, 16, 16]}
    terrain = {
        "from": [4.5, 3.5, 4.5], "to": [11.5, 10.5, 11.5], "name": "terrain",
        "faces": {"up": dict(full, texture="#top"), "down": dict(full, texture="#bottom"),
                  "north": dict(full, texture="#side"), "south": dict(full, texture="#side"),
                  "east": dict(full, texture="#side"), "west": dict(full, texture="#side")},
    }
    elements = [terrain,
                box([6.3, 10.5, 6.3], [7.3, 12.2, 7.3], "#side", name="tree_trunk"),
                box([5.5, 12.2, 5.5], [8.1, 13.6, 8.1], "#top", name="tree_canopy"),
                box([8.6, 10.5, 8.4], [10.6, 11.9, 10.4], "#bottom", name="house"),
                box([8.4, 11.9, 8.2], [10.8, 12.6, 10.6], "#casing", name="house_roof")]
    lo, hi, t = 2.5, 13.5, 0.7
    ylo, yhi = 1.5, 12.5
    k = 0
    for a in (lo, hi - t):
        for b in (lo, hi - t):
            elements.append(box([a, ylo, b], [a + t, yhi, b + t], "#frame", glow=True, name=f"cage_post_{k}"))
            k += 1
    for y in (ylo, yhi - t):
        for b in (lo, hi - t):
            elements.append(box([lo, y, b], [hi, y + t, b + t], "#frame", glow=True, name=f"cage_bar_x_{k}"))
            elements.append(box([b, y, lo], [b + t, y + t, hi], "#frame", glow=True, name=f"cage_bar_z_{k}"))
            k += 1
    elements.append(box([7.0, 0.5, 7.0], [9.0, 1.5, 9.0], "#core", glow=True, full_uv=True, name="singularity_seed"))
    asset("models/item/compressed_chunk.json", {
        "gui_light": "side",
        "ambientocclusion": False,
        "textures": {"top": ns("item/chunk_top"), "side": ns("item/chunk_side"), "bottom": ns("item/chunk_bottom"),
                     "frame": ns("item/pocket_ring_stable"), "core": ns("item/pocket_core_stable"),
                     "casing": ns("item/pocket_casing"), "particle": ns("item/chunk_side")},
        "elements": elements,
        "display": display(gui_scale=0.95, ground=0.5, fixed=0.9, tp_s=0.5, tp_t=(0, 3, 1),
                           fp=(0, -35, 0), fp_s=0.5, fp_t=(1.2, 3.4, 1.0)),
    })


def bomb_model():
    c = [8, 7, 8]
    elements = [
        box([3.5, 3, 4.5], [12.5, 11, 11.5], "#shell", name="shell_x"),
        box([4.5, 2, 4.5], [11.5, 12, 11.5], "#shell", name="shell_y"),
        box([4.5, 3, 3.5], [11.5, 11, 12.5], "#shell", name="shell_z"),
        box([4.3, 2.6, 4.3], [11.7, 11.4, 11.7], "#shell", rot=("y", 45, c), name="shell_round_y"),
        box([4.3, 3.3, 3.6], [11.7, 10.7, 12.4], "#shell", rot=("x", 45, c), name="shell_round_x"),
        box([3, 6.25, 3], [13, 7.75, 13], "#band", glow=True, name="equator"),
        box([7.25, 1.5, 3], [8.75, 12.5, 13], "#band", glow=True, name="meridian"),
        box([7.5, 4.5, 1.5], [8.5, 9.5, 14.5], "#metal", rot=("y", 45, c), name="fins_a"),
        box([1.5, 4.5, 7.5], [14.5, 9.5, 8.5], "#metal", rot=("y", 45, c), name="fins_b"),
        box([6, 12, 6], [10, 13, 10], "#metal", name="cap"),
        box([7.5, 13, 7.5], [8.5, 14.5, 8.5], "#metal", name="pin"),
        box([7, 14.5, 7], [9, 15.6, 9], "#band", glow=True, name="beacon"),
        box([6, 1, 6], [10, 2, 10], "#metal", name="base"),
    ]
    asset("models/item/anti_gravity_bomb.json", {
        "gui_light": "side",
        "ambientocclusion": False,
        "textures": {"shell": ns("item/bomb_shell"), "band": ns("item/bomb_band"), "metal": ns("item/bomb_metal"),
                     "particle": ns("item/bomb_shell")},
        "elements": elements,
        "display": display(gui_scale=0.9, ground=0.45, tp_s=0.5, tp_t=(0, 3, 1),
                           fp=(0, -35, 0), fp_s=0.55, fp_t=(1.1, 3.2, 1.0)),
    })


def core_fragment_model():
    elements = [
        box([3, 0, 3], [13, 2, 13], "#base", name="bedrock_a"),
        box([4.5, 0, 2], [11.5, 1.5, 14], "#base", name="bedrock_b"),
        box([2, 0, 4.5], [14, 1.2, 11.5], "#base", name="bedrock_c"),
        box([6, 1.5, 6], [10, 13.5, 10], "#crystal", rot=("y", 45, [8, 8, 8]), glow=True, name="spire"),
        box([6.75, 13.5, 6.75], [9.25, 15.5, 9.25], "#crystal", rot=("y", 45, [8, 8, 8]), glow=True, name="spire_tip"),
        box([3, 1, 6.5], [6, 9, 9.5], "#crystal", rot=("z", 22.5, [4.5, 1, 8]), glow=True, name="shard_w"),
        box([3.5, 9, 7], [5.5, 10.5, 9], "#crystal", rot=("z", 22.5, [4.5, 1, 8]), glow=True, name="shard_w_tip"),
        box([10, 1, 6.5], [13, 8, 9.5], "#crystal", rot=("z", -22.5, [11.5, 1, 8]), glow=True, name="shard_e"),
        box([6.5, 1, 10], [9.5, 7.5, 13], "#crystal", rot=("x", 22.5, [8, 1, 11.5]), glow=True, name="shard_s"),
        box([6.5, 1, 3], [9.5, 10, 6], "#crystal", rot=("x", -22.5, [8, 1, 4.5]), glow=True, name="shard_n"),
        box([7, 10, 3.5], [9, 11.5, 5.5], "#crystal", rot=("x", -22.5, [8, 1, 4.5]), glow=True, name="shard_n_tip"),
    ]
    asset("models/block/core_fragment.json", {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"base": ns("block/core_fragment_base"), "crystal": ns("block/core_fragment_crystal"),
                     "particle": ns("block/core_fragment_crystal")},
        "elements": elements,
    })
    asset("models/item/core_fragment.json", {"parent": ns("block/core_fragment")})
    asset("blockstates/core_fragment.json", {"variants": {"": {"model": ns("block/core_fragment")}}})


# --------------------------------------------------------------------------- particles & sounds

def particles():
    defs = {
        "accretion": ["glow_0", "glow_1", "glow_2", "glow_3"],
        "debris_streak": ["streak"],
        "horizon_spark": ["spark_0", "spark_1", "spark_2", "spark_3"],
        "matter_stream": ["mote_0", "mote_1", "glow_0"],
        "antigrav_spark": ["ring_0", "mote_1", "spark_1"],
        "core_glint": ["crystal_0", "crystal_1", "crystal_2", "crystal_3"],
        "shock_spark": ["streak"],
        "jet_plasma": ["glow_1", "glow_3"],
    }
    for name, tex in defs.items():
        asset(f"particles/{name}.json", {"textures": [ns(t) for t in tex]})


SOUNDS = {
    "black_hole_ambient": ("Singularity roars", True),
    "black_hole_birth": ("Singularity ignites", True),
    "containment_breach": ("Containment lattice shatters", False),
    "alarm": ("Breach alarm blares", False),
    "warning_beep": ("Containment warning", False),
    "vacuum": ("Pocket Black Hole swallows a chunk", False),
    "chunk_place": ("Compressed Chunk unfolds", False),
    "collapse_buildup": ("Singularity destabilizes", True),
    "singularity_explosion": ("Singularity detonates", True),
    "bomb_throw": ("Anti-Gravity Bomb thrown", False),
    "bomb_captured": ("Bomb caught by gravity", False),
    "bomb_fizzle": ("Anti-Gravity Bomb powers down", False),
    "fragment_hum": ("Core Fragment hums", False),
    "fragment_collect": ("Core Fragment secured", False),
}


def sounds():
    out = {}
    for name, (subtitle, stream) in SOUNDS.items():
        entry = {"name": ns(name)}
        if stream:
            entry["stream"] = True
        out[name] = {"subtitle": f"subtitles.{MOD}.{name}", "sounds": [entry]}
    asset("sounds.json", out)


# --------------------------------------------------------------------------- language

def lang():
    L = {
        "itemGroup.livingblackhole": "Living Black Hole",
        "item.livingblackhole.pocket_black_hole": "Pocket Black Hole",
        "item.livingblackhole.compressed_chunk": "Compressed Chunk",
        "item.livingblackhole.anti_gravity_bomb": "Anti-Gravity Bomb",
        "block.livingblackhole.core_fragment": "Core Fragment",
        "entity.livingblackhole.black_hole": "Living Black Hole",
        "entity.livingblackhole.gravitational_debris": "Gravitational Debris",
        "entity.livingblackhole.anti_gravity_bomb": "Anti-Gravity Bomb",

        "tooltip.livingblackhole.pocket_black_hole.lore": "A captive singularity, humming inside a magnetic lattice.",
        "tooltip.livingblackhole.pocket_black_hole.chunks": "Chunks consumed: %s / %s",
        "tooltip.livingblackhole.pocket_black_hole.integrity": "Containment integrity: %s%%",
        "tooltip.livingblackhole.pocket_black_hole.critical": "⚠ CONTAINMENT FAILURE IMMINENT",
        "tooltip.livingblackhole.pocket_black_hole.usage": "Right-click a block to swallow the 16\u00d716\u00d716 chunk around it.",
        "tooltip.livingblackhole.anti_gravity_bomb.lore": "Three Core Fragments folded around a phase-inverted Compressed Chunk.",
        "tooltip.livingblackhole.anti_gravity_bomb.usage": "Throw it within 40 blocks of a black hole: gravity does the rest.",
        "tooltip.livingblackhole.anti_gravity_bomb.inert": "Inert outside a gravity well - it drops back intact.",
        "tooltip.livingblackhole.compressed_chunk.lore": "A piece of the world folded into a fist-sized lattice.",
        "tooltip.livingblackhole.compressed_chunk.empty": "Empty - nothing is folded inside.",
        "tooltip.livingblackhole.compressed_chunk.blocks": "Contains %s blocks:",
        "tooltip.livingblackhole.compressed_chunk.origin": "Taken from %s, %s, %s",
        "tooltip.livingblackhole.compressed_chunk.usage": "Right-click a surface to unfold it. The block you originally clicked lands on the block you click now.",
        "tooltip.livingblackhole.core_fragment.lore": "A shard of the singularity's own lattice.",
        "tooltip.livingblackhole.core_fragment.usage": "3 Core Fragments + 1 Compressed Chunk = Anti-Gravity Bomb",

        "message.livingblackhole.breach": "☢ CONTAINMENT BREACH! A black hole has escaped at %s!",
        "message.livingblackhole.fragments": "Core Fragments crashed down at: %s",
        "message.livingblackhole.fragment_secured": "✔ %s secured a Core Fragment (%s/%s)",
        "message.livingblackhole.all_fragments": "All Core Fragments secured! Craft the Anti-Gravity Bomb: 3 Core Fragments + 1 Compressed Chunk.",
        "message.livingblackhole.nothing_to_consume": "Nothing here the singularity can swallow.",
        "message.livingblackhole.aim_at_block": "Aim at a block - the singularity swallows the 16\u00d716\u00d716 chunk around it.",
        "message.livingblackhole.chunk_consumed": "Swallowed %s blocks - chunk %s / %s",
        "message.livingblackhole.chunk_placed": "Unfolded %s blocks",
        "message.livingblackhole.chunk_empty": "This Compressed Chunk is empty.",
        "message.livingblackhole.chunk_blocked": "Nothing could be placed here.",
        "message.livingblackhole.collapse_started": "The Anti-Gravity Bomb reached the event horizon - the singularity is destabilizing!",
        "message.livingblackhole.neutralized": "✦ %s neutralized the singularity. The world is safe.",
        "message.livingblackhole.unknown_hero": "An unknown hero",
        "message.livingblackhole.vanished": "The singularity collapsed on its own and vanished.",
        "message.livingblackhole.bomb_captured": "Gravity lock acquired - the bomb is falling into the singularity!",
        "message.livingblackhole.bomb_inert": "No gravity well nearby - the bomb powered down.",
        "message.livingblackhole.integrity_warning.1": "Containment integrity %s%% - lattice under strain",
        "message.livingblackhole.integrity_warning.2": "Containment integrity %s%% - WARNING: lattice destabilizing",
        "message.livingblackhole.integrity_warning.3": "Containment integrity %s%% - CRITICAL: breach imminent!",

        "hud.livingblackhole.warning": "⚠ WARNING: BLACK HOLE ESCAPED! ⚠",
        "hud.livingblackhole.destabilizing": "⚠ SINGULARITY DESTABILIZING ⚠",
        "hud.livingblackhole.collect": "To stop this black hole, collect Core Fragments at: %s, %s, %s",
        "hud.livingblackhole.fragment": "◆ FRAGMENT %s   [%s, %s, %s]",
        "hud.livingblackhole.fragment_secured": "✔ FRAGMENT %s   SECURED",
        "hud.livingblackhole.status": "MASS %s blocks  ·  REACH %sm  ·  DISTANCE %sm",
        "hud.livingblackhole.status_elsewhere": "MASS %s blocks  ·  singularity located in %s",
        "hud.livingblackhole.status_craft": "▶ Craft the ANTI-GRAVITY BOMB and throw it into the black hole",
        "hud.livingblackhole.status_collapse": "▶ Bomb delivered - get clear of the blast!",

        "banner.livingblackhole.breach": "CONTAINMENT BREACH",
        "banner.livingblackhole.breach.sub": "The singularity has escaped. Find the Core Fragments.",
        "banner.livingblackhole.collapse": "SINGULARITY COLLAPSE",
        "banner.livingblackhole.collapse.sub": "Detonation in %ss",
        "banner.livingblackhole.neutralized": "SINGULARITY NEUTRALIZED",
        "banner.livingblackhole.neutralized.sub": "Spacetime is healing.",
        "banner.livingblackhole.captured": "GRAVITY LOCK",
        "banner.livingblackhole.captured.sub": "The bomb is falling into the event horizon",
        "banner.livingblackhole.fragment": "CORE FRAGMENT %s/%s",
        "banner.livingblackhole.fragment.sub": "Secured. Keep hunting.",
        "banner.livingblackhole.fragment.all": "All fragments secured - forge the Anti-Gravity Bomb!",

        "death.attack.livingblackhole.singularity": "%1$s was spaghettified by a black hole",
        "death.attack.livingblackhole.singularity.player": "%1$s was spaghettified by a black hole whilst fighting %2$s",

        "command.livingblackhole.none": "No singularities are active.",
        "command.livingblackhole.spawned": "A singularity has been unleashed.",
        "command.livingblackhole.no_target": "No stable black hole found to neutralize.",
        "command.livingblackhole.neutralizing": "Collapse sequence initiated.",
        "command.livingblackhole.cleared": "Removed %s singularities.",
        "command.livingblackhole.not_player": "This command must be run by a player.",
        "command.livingblackhole.not_holding": "Hold a Pocket Black Hole first.",
        "command.livingblackhole.charged": "Pocket Black Hole charged to %s / %s chunks.",

        "advancements.livingblackhole.root.title": "Living Black Hole",
        "advancements.livingblackhole.root.description": "Hold a singularity in the palm of your hand",
        "advancements.livingblackhole.pocket_dimension.title": "Pocket Dimension",
        "advancements.livingblackhole.pocket_dimension.description": "Fold a 16\u00d716\u00d716 piece of the world into a Compressed Chunk",
        "advancements.livingblackhole.containment_breach.title": "Containment Breach",
        "advancements.livingblackhole.containment_breach.description": "Feed the Pocket Black Hole ten chunks until it breaks free",
        "advancements.livingblackhole.shards_of_creation.title": "Shards of Creation",
        "advancements.livingblackhole.shards_of_creation.description": "Recover a Core Fragment from its crash site",
        "advancements.livingblackhole.defuser.title": "Phase Inversion",
        "advancements.livingblackhole.defuser.description": "Forge an Anti-Gravity Bomb",
        "advancements.livingblackhole.cosmic_savior.title": "Cosmic Savior",
        "advancements.livingblackhole.cosmic_savior.description": "Collapse an escaped singularity with an Anti-Gravity Bomb",
    }
    for name, (subtitle, _) in SOUNDS.items():
        L[f"subtitles.{MOD}.{name}"] = subtitle
    asset("lang/en_us.json", L)


# --------------------------------------------------------------------------- data

def recipes():
    data(MOD, "recipes/pocket_black_hole.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["CAC", "AEA", "CAC"],
        "key": {"C": {"item": "minecraft:crying_obsidian"}, "A": {"item": "minecraft:amethyst_shard"},
                "E": {"item": "minecraft:ender_eye"}},
        "result": {"item": ns("pocket_black_hole")},
        "show_notification": True,
    })
    data(MOD, "recipes/anti_gravity_bomb.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "equipment",
        "ingredients": [{"item": ns("core_fragment")}, {"item": ns("core_fragment")},
                        {"item": ns("core_fragment")}, {"item": ns("compressed_chunk")}],
        "result": {"item": ns("anti_gravity_bomb")},
    })


def self_drop(block, explosion=True):
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": ns(block)}]}
    if explosion:
        pool["conditions"] = [{"condition": "minecraft:survives_explosion"}]
    return {"type": "minecraft:block", "pools": [pool], "random_sequence": f"{MOD}:blocks/{block}"}


def loot():
    data(MOD, "loot_tables/blocks/core_fragment.json", self_drop("core_fragment", explosion=False))


def tags():
    data("minecraft", "tags/blocks/mineable/pickaxe.json", {"replace": False, "values": [ns("core_fragment")]})
    data(MOD, "tags/blocks/singularity_immune.json", {"replace": False, "values": [
        ns("core_fragment"), "minecraft:bedrock", "minecraft:barrier", "minecraft:light", "minecraft:structure_void",
        "minecraft:end_portal", "minecraft:end_portal_frame", "minecraft:end_gateway", "minecraft:command_block",
        "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:structure_block",
        "minecraft:jigsaw", "minecraft:reinforced_deepslate", "minecraft:moving_piston"]})
    data(MOD, "tags/entity_types/singularity_immune.json", {"replace": False, "values": [
        "minecraft:marker", "minecraft:interaction", "minecraft:block_display", "minecraft:item_display",
        "minecraft:text_display"]})
    for tag in ("bypasses_armor", "no_knockback", "bypasses_shield"):
        data("minecraft", f"tags/damage_type/{tag}.json", {"replace": False, "values": [ns("singularity")]})
    data(MOD, "damage_type/singularity.json", {"message_id": f"{MOD}.singularity", "exhaustion": 0.0,
                                               "scaling": "never", "effects": "hurt"})


def advancement(name, parent, icon, criteria, frame="task", hidden=False, rewards=None, background=None,
                toast=True, chat=True):
    adv = {
        "display": {
            "icon": {"item": icon},
            "title": {"translate": f"advancements.{MOD}.{name}.title"},
            "description": {"translate": f"advancements.{MOD}.{name}.description"},
            "frame": frame,
            "show_toast": toast,
            "announce_to_chat": chat,
            "hidden": hidden,
        },
        "criteria": criteria,
    }
    if background:
        adv["display"]["background"] = background
    if parent:
        adv["parent"] = parent
    if rewards:
        adv["rewards"] = rewards
    data(MOD, f"advancements/{name}.json", adv)


def has(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [item]}]}}


IMPOSSIBLE = {"triggered": {"trigger": "minecraft:impossible"}}


def advancements():
    advancement("root", None, ns("pocket_black_hole"), {"has_pocket": has(ns("pocket_black_hole"))},
                background=f"{MOD}:textures/gui/advancements/backgrounds/void.png", chat=False)
    advancement("pocket_dimension", ns("root"), ns("compressed_chunk"), {"has_chunk": has(ns("compressed_chunk"))})
    advancement("containment_breach", ns("pocket_dimension"), "minecraft:tnt", IMPOSSIBLE, frame="goal")
    advancement("shards_of_creation", ns("containment_breach"), ns("core_fragment"), {"has_fragment": has(ns("core_fragment"))})
    advancement("defuser", ns("shards_of_creation"), ns("anti_gravity_bomb"), {"has_bomb": has(ns("anti_gravity_bomb"))}, frame="goal")
    advancement("cosmic_savior", ns("defuser"), "minecraft:nether_star", IMPOSSIBLE, frame="challenge",
                rewards={"experience": 250})

    def recipe_unlock(recipe, item, trigger_item):
        data(MOD, f"advancements/recipes/{recipe}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {"has_item": has(trigger_item), "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": ns(recipe)}}},
            "requirements": [["has_item", "has_the_recipe"]],
            "rewards": {"recipes": [ns(recipe)]},
        })

    recipe_unlock("pocket_black_hole", ns("pocket_black_hole"), "minecraft:ender_eye")
    recipe_unlock("anti_gravity_bomb", ns("anti_gravity_bomb"), ns("core_fragment"))


def main():
    pocket_models()
    chunk_model()
    bomb_model()
    core_fragment_model()
    particles()
    sounds()
    lang()
    recipes()
    loot()
    tags()
    advancements()
    print("data written under", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
