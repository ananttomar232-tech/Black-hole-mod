# Living Black Hole

A cinematic singularity event for **Minecraft 1.20.1 (Forge 47.x)**.

Feed a Pocket Black Hole 500 blocks and its containment fails. A living black hole tears the world apart while three Core Fragments crash down within 300 blocks. Recover them, forge an Anti-Gravity Bomb, and throw it into the event horizon before there is nothing left to save.

**Download:** grab `LivingBlackHole-1.0.0-forge-1.20.1.jar` from the [latest release](https://github.com/ananttomar232-tech/Black-hole-mod/releases/latest) and drop it into `.minecraft/mods`.

Every release is built by CI, then played end to end on a dedicated server and in a real client before it is published. Screenshots from the latest automated run are attached to the [`ci-evidence` release](https://github.com/ananttomar232-tech/Black-hole-mod/releases/tag/ci-evidence).

---

## The event, step by step

### 1. The Pocket Black Hole
Crafted from crying obsidian, amethyst shards and an eye of ender:

```
C A C      C = Crying Obsidian
A E A      A = Amethyst Shard
C A C      E = Eye of Ender
```

- Hold **use** to charge (the FOV tightens and matter spirals into the device), then release to fire a **consumption beam** with a 15-block reach. The cone widens with charge.
- Every block and every non-player entity inside the beam is swallowed nearest-first and converted **1:1 into Compressed Matter**, which flows visibly back into your hand and lands in your inventory.
- Block removal respects claim and protection mods (it fires `BlockEvent.BreakEvent`) and never touches your own pets, bosses or unbreakable blocks.
- The durability bar shows **containment integrity** (cyan → violet → red). The 3D model escalates through stable, unstable and critical states, and warnings fire at 50%, 75% and 90%.
- **Hidden limit: 500 blocks.**

### 2. Containment breach
When the 500th block goes in:
- The Pocket Black Hole is **deleted** from your inventory.
- A **Living Black Hole** spawns exactly where you stand. The breach throws you clear, with slow falling and resistance so you survive.
- **Three Core Fragments** crash down at random surface coordinates within 300 blocks, each in a scorched crater with a sky beam you can see from far away.
- Alarms sound, the screen glitches and flashes red, and a **permanent HUD** appears with flashing red text:
  > **⚠ WARNING: BLACK HOLE ESCAPED! ⚠**
  > To stop this black hole, collect Core Fragments at: [x, y, z], [x, y, z], [x, y, z]

  The HUD also gives a live distance and compass arrow for every fragment, ticks them off as they are secured, and shows the singularity's mass, reach and distance. It survives relogs, deaths and dimension changes, and only disappears once the black hole is destroyed.

### 3. The Living Black Hole
- It never moves, but it **devours every block within a 30-block radius**, nearest matter first, its reach growing as it feeds. Torn-out blocks become **gravitational debris**: real entities that tumble, glow red-hot and stretch (spaghettify) as they spiral across the horizon.
- Mobs, items and other non-player entities are dragged in and destroyed. **Players are never pulled**, so you can move freely to complete the quest. Flying into the horizon itself is fatal.
- Rendering, in layers:
  - a pitch-black event horizon
  - a Doppler-beamed accretion disk with Keplerian differential rotation
  - a photon ring and lensed far-side arcs that track the spin axis
  - relativistic jets and a corona
  - thousands of orbiting plasma particles
  - a **screen-space gravitational lensing shader** that bends the world behind the hole, with an Einstein ring, chromatic dispersion, frame-dragging swirl and heat shimmer, while foreground blocks stay crisp
- The sky dims to violet and the stars come out near it, even at noon. The camera trembles and its roar fills the air.

### 4. The Anti-Gravity Bomb
Mine the three Core Fragments with any tool (they always drop, never despawn and survive lava), then craft:

```
3 × Core Fragment + 1 × Compressed Matter  →  Anti-Gravity Bomb   (shapeless)
```

The bomb has a glowing emissive 3D model and sheds sparks that fall *upward* while held.

### 5. The climax
Throw the bomb anywhere within **40 blocks** of the black hole. Its gravity seizes the bomb, ignoring walls and water, and spirals it into the centre. Then:
1. **Destabilisation:** the horizon swells and flickers, the disk spins up and turns white-blue, a countdown appears on the HUD, and a Shepard-tone whine rises.
2. **Implosion:** everything collapses to a single point.
3. **Detonation:** a deafening custom explosion plays for every player in the dimension, followed by a full-screen white flash, violent camera shake and a colossal Fresnel shockwave shell with disk-plane rings racing outward 150+ blocks.
4. The black hole is gone, the destruction stops, the HUD clears into *SINGULARITY NEUTRALIZED*, and a burst of experience rains down.

---

## Extras
- **Block of Compressed Matter:** 9 Compressed Matter. A decorative block with pulsing, glowing veins (reversible).
- **Advancement tree:** Living Black Hole → Event Horizon → Containment Breach → Shards of Creation → Phase Inversion → *Cosmic Savior* (challenge).
- **Explosions inside a black hole's reach are swallowed:** they hurt nothing and destroy no blocks.
- **Chunk loading:** the singularity keeps its own chunk ticking, so it keeps eating while you are away (configurable).
- **Multiplayer-ready:** every player sees the HUD, and all players near the collapse are credited.
- **Custom death message:** *"Steve was spaghettified by a black hole"*.

## Commands (operators)
| Command | Effect |
|---|---|
| `/blackhole list` | Every active singularity, its mass and fragment status |
| `/blackhole spawn [pos]` | Start a full escape event (fragments included) |
| `/blackhole neutralize` | Collapse the nearest black hole with the full cinematic |
| `/blackhole clear` | Instantly remove all black holes and clear the HUD |
| `/blackhole charge <blocks>` | Set the mass stored in the held Pocket Black Hole |

The creative tab also includes a Pocket Black Hole one shot away from breaching.

## Configuration
`config/livingblackhole-common.toml`: block limit, beam reach, blocks per shot, suction radius, devour rate, debris cap, block destruction toggle, chunk loading, entity pulling, fragment radius and minimum distance, bomb capture radius, experience reward.

`config/livingblackhole-client.toml`: lensing on/off and strength, screen shake, particle density, sky darkening, full-screen flashes (photosensitivity).

## Building from source
Requires JDK 17.

```bash
./gradlew build
```

The jar is written to `build/libs/`. Run the automated end-to-end tests locally with:

```bash
./gradlew runServer -Pselftest
```

```bash
./gradlew runClient -Pselftest
```

All art and audio are generated procedurally and can be regenerated with Python 3, numpy, Pillow and ffmpeg:

```bash
python3 tools/generate_textures.py
```

```bash
python3 tools/generate_sounds.py
```

```bash
python3 tools/generate_data.py
```

## License
MIT. See [LICENSE](LICENSE).
