# Nexus Landscape — live multi-seed QA

This checklist is for new `nexus_landscape:nexus_v2` worlds on Minecraft
1.21.1. Keep PR #5 in draft while collecting results. Use Java 21, cheats or
operator permission level 2, shaders off, and `F3+G` for chunk borders.

Start Minecraft with profiling enabled:

```powershell
$env:NEXUS_LANDSCAPE_PROFILE = '1'
.\gradlew.bat runClient
```

## Fixed seed matrix

Do not replace seeds because of unattractive output.

| Run | Purpose | Decimal seed |
|---|---|---:|
| Q1 | ordinary random-like | `240802` |
| Q2 | known terrain/hydrology regression | `918273645` |
| Q3 | negative regression | `-41027` |
| Q4 | large positive | `6764932314836472` |
| Q5 | different high-bit pattern | `4611686018427387905` |

Create a fresh world for every run. For each seed inspect:

- A. spawn and nearby terrain;
- B. two or three distinct climate regions;
- C. snowy/alpine terrain (`/locate biome minecraft:snowy_slopes`);
- D. a river network and banks;
- E. a tree-heavy forest;
- F. lush, dripstone, or deep-dark cave access in spectator mode;
- G. at least one regional landmark encountered during travel;
- H. X and Z chunk borders across river, surface, vegetation, cave accents,
  landmark, and Tree v2 where available.

At every retained QA point run:

```mcfunction
/nexuslandscape debug inspect
/nexuslandscape debug export
```

`inspect` is read-only and prints seed, dimension, block/chunk coordinates,
Minecraft biome, Nexus climate/profile family, dominant province/mood,
surface and vegetation profiles, hydrology influences, cave profile, and tree
eligibility. `export` writes the same reproduction context plus session
telemetry to `<instance>/nexuslandscape-debug/`.

## Profiler workflow

Before generating new chunks:

```mcfunction
/nexuslandscape debug profiler reset
```

Teleport, fly, or explore only fresh chunks, then capture:

```mcfunction
/nexuslandscape debug profiler
/nexuslandscape debug counters
/nexustree counters
```

The profiler reports calls, total, average, p50, p95, and maximum time for
vanilla/Nexus surface, river water, placed features, vegetation, surface field
sampling, biome lookup, context preparation, block replacement, tree-bearing
vegetation placement, and cave accents. Tree counters include placement
outcomes, collision/quota failures, cache hits/misses, probe cost, generated
segments, and crown balance. Reset tree telemetry separately when isolating a
run:

```mcfunction
/nexustree reset
```

## Long-distance route

For every seed visit fresh terrain near each row. Vary signs as shown rather
than generating a dense square of chunks.

| Leg | Teleport |
|---|---|
| L1 | `/tp @s 2000 180 -2000` |
| L2 | `/tp @s -8000 180 8000` |
| L3 | `/tp @s 20000 180 20000` |
| L4 | `/tp @s -20000 180 -20000` |

At each leg wait for generation to settle, record the visible generation
spike, run `inspect`, and review macro-pattern repetition, transitions, rivers,
landmarks, and chunk seams. Do not pre-generate millions of chunks.

## Visual acceptance

- Rivers remain connected and avoid rectangular masks or trees in channels.
- Snow layers and palettes cross chunk borders without a straight seam.
- Vegetation ownership has no missing or doubled border strip.
- Landmarks have connected silhouettes, no impossible overhangs, and no
  rounded-boulder regression.
- Cave accents are sparse, biome-appropriate, supported, and do not overwrite
  vanilla cave identity.
- Tree v2 has no clipped crowns, floating branches, detached leaves, exposed or
  excessively deep roots, partial trees, obvious clones, excessive HERO
  density, or strange wind deformation.

Transactional rollback remains a manual observation risk. When a tree reports
a collision or failed placement, inspect the reported area for partial trunk,
roots, or leaves. There is no safe small runtime command that can intentionally
force a real `WorldGenLevel` failure without adding mutation-only QA code.

## Bug reproduction record

For every failure preserve:

- exact mod JAR/build HEAD and mod list;
- world seed and dimension;
- block X/Y/Z and chunk X/Z;
- Minecraft biome, Nexus family/profile, province/mood, and regional influences;
- `/nexuslandscape debug export` JSON;
- profiler, Stage 6 counters, and tree counters after an isolated reset/run;
- shader-free overview, close-up, F3, and F3+G screenshots;
- `latest.log` and the exact steps needed to reproduce in a fresh world.

Do not tune Tree v2 silhouettes or optimize a profiler phase without one of
these concrete reproduction records.
