# Nexus Landscape — Tree System v2

## Status

Architecture foundation for a deterministic, biome-aware and high-quality procedural tree system.

This document is intentionally written before runtime integration. The existing vegetation system is stable enough to keep serving Alpha 2 while Tree System v2 is developed and tested in isolation.

## 1. Current system audit

### What is already strong

The current vegetation layer already provides several valuable systems that must be preserved:

- deterministic coordinate-driven placement;
- one vegetation profile for each of the 53 Stage 6 surface profiles;
- biome fallback based on temperature and humidity;
- slope, altitude, river, groundwater and clearing constraints;
- forest-core and clearing fields that cross chunk boundaries;
- immutable profiles and pure selection logic;
- thread/order determinism tests;
- telemetry for attempts, placements and rejection reasons;
- Stage 6 profiling around sampling and block placement.

### What blocks high-quality trees

The current tree implementation is intentionally minimal:

- `VegetationProfile.TreeShape` contains only broad labels;
- a selected shape changes mostly block materials, height and canopy radius;
- `buildTree()` creates a straight vertical trunk;
- every canopy is a small rounded volume around one crown point;
- `oldGrowth` is only a boolean height modifier;
- there is no branch graph, age, damage, wind response or crown competition;
- there is no distinction between cheap forest trees and rare hero trees;
- there is no complete pre-placement model or validation pass;
- trees are applied after vanilla biome decoration, so vanilla and Nexus trees may overlap or double density;
- the 14×14 ownership lattice controls spacing, but not forest composition or social grouping.

The current implementation should not be incrementally expanded into a large procedural generator. The pure biome/placement layer should remain, while tree anatomy becomes a separate subsystem.

## 2. Product goal

Tree System v2 must produce trees that are:

- recognizable by species and habitat;
- asymmetrical for understandable environmental reasons;
- visually coherent from every direction;
- attractive both individually and as a forest mass;
- deterministic for the same world seed and coordinates;
- safe across chunk borders and generation order;
- bounded in memory, block count and execution time;
- configurable through data profiles instead of one Java generator per species.

The target is not botanical simulation for its own sake. The target is a convincing Minecraft silhouette with readable structure and a plausible growth history.

## 3. Core design principle

A tree is generated as a complete immutable model before any block is placed.

```text
TreeSpeciesProfile
+ TreeVariantProfile
+ TreeEnvironment
+ TreeLifeHistory
+ deterministic seed
        ↓
TreeGrowthEngine
        ↓
BranchGraph
        ↓
CanopyField
        ↓
VoxelTreeModel
        ↓
TreeValidator
        ↓
TreePlacer
```

No growth algorithm may directly call `WorldGenLevel.setBlock()`.

## 4. Tree quality tiers

### BASIC

Purpose: common forest population.

- low branch count;
- compact model bounds;
- simplified canopy field;
- predictable placement cost;
- enough variation to avoid obvious repetition;
- visually designed to work in groups rather than as a landmark.

### MID

Purpose: mature trees, forest edges, riverbanks, clearings and visible foreground trees.

- multiple primary branches;
- stronger asymmetry;
- environmental response;
- canopy gaps and separated masses;
- possible damaged or dead branch;
- more detailed trunk taper and roots.

### HERO

Purpose: rare old-growth and landmark trees.

- complex branch hierarchy;
- strong individual silhouette;
- life-history events;
- large canopy masses with negative space;
- roots, scars, secondary leaders and deadwood;
- strict rarity and hard execution/block budgets.

A biome must use a mixture of tiers. Forests must not consist only of HERO trees.

## 5. Data model

### TreeSpeciesProfile

Defines species-level biology and visual grammar:

- species id;
- growth family;
- log, wood, leaf and optional accent palettes;
- trunk height and radius ranges;
- taper curve;
- branch start-height distribution;
- primary/secondary branch count ranges;
- branching angles;
- apical dominance;
- phototropism;
- gravitropism and branch sag;
- wind sensitivity;
- crown dimensions and preferred shape;
- canopy density and gap ratio;
- root style;
- allowed terrain, moisture, altitude and temperature ranges;
- supported quality tiers.

### TreeVariantProfile

Defines a role within one species:

- sapling/young;
- forest-column;
- open-grown;
- riverbank;
- slope;
- windswept;
- damaged;
- old-growth;
- dead;
- hero.

Variants modify a species profile; they do not duplicate the growth algorithm.

### TreeEnvironment

Captured from the world before growth:

- base position and surface height;
- slope vector and magnitude;
- local curvature;
- terrain openness in cardinal and diagonal directions;
- river distance and river influence;
- groundwater and soil moisture;
- elevation and treeline distance;
- biome/family/province;
- forest-core and clearing values;
- local neighbour occupancy field;
- prevailing wind vector;
- estimated light/open-sky field;
- maximum permitted bounds.

### TreeLifeHistory

A deterministic narrative generated from seed, species, variant and environment:

- age class;
- dominant growth direction;
- crown bias;
- trunk lean;
- lost leader event;
- replacement leader;
- dead branch sectors;
- storm or snow damage;
- recovery growth;
- hollow/scar allowance;
- vitality.

Randomness alone is not accepted as a substitute for life history. Shape changes must be correlated with these causes.

### BranchGraph

An immutable tree skeleton:

- node positions in continuous local coordinates;
- parent/child hierarchy;
- segment radius at both ends;
- segment age and vitality;
- branch role (trunk, leader, primary, secondary, twig, deadwood);
- local frame and preferred leaf-support region.

### CanopyField

A set of connected foliage masses rather than spheres attached to every endpoint:

- attraction/occupancy points;
- light exposure;
- branch support weight;
- density field;
- negative-space masks;
- edge roughness;
- dead/cut sectors;
- species-specific clustering.

### VoxelTreeModel

A complete placement proposal:

- ordered log/wood block map;
- leaf block map;
- roots and accents;
- bounding box;
- touched chunk set;
- total block counts by category;
- support/base requirements;
- model fingerprint for determinism tests.

## 6. Growth families

Tree System v2 should support a small number of reusable growth families rather than one algorithm per species:

1. `BROADLEAF_SPREADING` — oak-like, beech-like, maple-like.
2. `BROADLEAF_UPRIGHT` — compact upright deciduous trees.
3. `BIRCH_COLUMNAR` — slender trunks, light segmented crowns, occasional multi-stem forms.
4. `CONIFER_LAYERED` — spruce/fir forms with damaged or missing tiers.
5. `CONIFER_SPARSE` — tall pine and old sparse conifers.
6. `FLAT_CROWN_DRYLAND` — acacia and dry woodland forms.
7. `TROPICAL_EMERGENT` — tall jungle canopy trees.
8. `WETLAND_ROOTED` — mangrove and swamp forms.
9. `WEEPING_RIPARIAN` — willow-like riverbank forms.
10. `DEAD_OR_SNAG` — standing deadwood and broken trunks.
11. `FANTASY_MASSIVE` — rare stylized hero trees only.

Species profiles refine these families.

## 7. Forest composition

The existing coordinate lattice remains useful for deterministic ownership, but it must become a forest composition layer.

A cell chooses:

- whether it owns a tree candidate;
- species based on biome profile;
- ecological role;
- quality tier;
- age/size class;
- cluster membership;
- spacing radius;
- whether it is suppressed by a stronger neighbouring candidate.

Example oak forest composition:

- 55% BASIC forest-column oak;
- 20% BASIC young oak;
- 15% MID mature oak;
- 7% MID damaged/open-grown oak near clearings or rivers;
- 2.5% old-growth oak;
- 0.5% HERO oak.

Composition weights are biome data, not hard-coded generator branches.

## 8. Vanilla vegetation integration

Current Nexus vegetation runs after vanilla biome decoration. Tree System v2 must not simply add high-detail trees on top of vanilla trees.

Before runtime activation, choose one controlled integration policy:

### Preferred policy: replace vanilla tree features for supported biomes

- preserve vanilla non-tree vegetation and structures;
- remove or suppress only tree placed features for biomes controlled by Nexus;
- let Tree System v2 own tree density and composition;
- provide an explicit compatibility fallback for unsupported/modded biomes.

### Temporary policy: exclusion-aware coexistence

- scan a bounded local occupancy field;
- reject Nexus candidates near existing trunks/canopies;
- keep density low;
- use only while replacement integration is under development.

The final release must not rely on uncontrolled double-decoration.

## 9. Chunk safety and generation order

Requirements:

- candidate ownership is absolute-coordinate based;
- a tree model is generated once by its owner cell;
- cross-chunk blocks are written only through a deterministic deferred-placement mechanism or a region-safe generation stage;
- no neighbouring chunk reads outside the allowed generation region;
- model generation never depends on mutable worldgen random order;
- no partial tree may be committed if validation fails;
- block placement order must be logs/roots first, then leaves/accents;
- failures must be telemetry-visible.

## 10. Performance budgets

Initial hard budgets per model:

| Tier | Max branch segments | Max model blocks | Max horizontal radius | Max height |
|---|---:|---:|---:|---:|
| BASIC | 80 | 900 | 8 | 24 |
| MID | 220 | 2,800 | 14 | 38 |
| HERO | 650 | 9,000 | 24 | 64 |

These are safety ceilings, not targets.

Additional requirements:

- all algorithms are bounded by explicit iteration counts;
- no recursive method may rely on unbounded depth;
- spatial queries use bounded grids or hashes;
- generated models may be cached only with a strict bounded cache;
- telemetry records generation time, validation time, placement time and block counts;
- CI must test worst-case profiles.

## 11. Visual quality gates

A tree profile is not accepted only because it compiles or passes numeric invariants.

Every species/variant must satisfy:

### Silhouette

- identifiable from at least four horizontal directions;
- no obvious perfect sphere, cone or repeated ring unless species requires it;
- visible large, medium and small shape hierarchy;
- non-uniform crown edge;
- readable trunk-to-primary-branch transition.

### Structure

- branch thickness decreases with hierarchy;
- child branches are supported by parent thickness;
- no floating logs or isolated leaf masses;
- no excessive branch self-intersections;
- branch sag correlates with length/weight;
- damage produces recovery response, not merely deleted blocks.

### Canopy

- connected to living branch support;
- contains deliberate negative space;
- includes dense core and lighter edges;
- does not fill a simple bounding sphere;
- respects dead or wind-suppressed sectors.

### Habitat response

- forest trees grow taller and narrower under competition;
- open-grown trees are broader;
- riverbank trees bias toward open water/space without entering the active channel;
- slope trees compensate at roots and bias toward open terrain;
- windswept trees show correlated lean and crown suppression.

### Forest-level composition

- repeated silhouettes are not obvious in a 128×128 sample;
- neighbouring crowns do not merge into uniform walls everywhere;
- clearings and understory remain readable;
- size and age distributions are mixed;
- HERO trees remain rare enough to feel special.

## 12. Testing strategy

### Pure deterministic tests

- identical input produces identical model fingerprint;
- generation order and thread scheduling do not change models;
- profile validation rejects invalid ranges and unsupported combinations;
- every growth family produces a valid model;
- segment radius is monotonic along each branch path;
- every leaf cluster has living branch support within its species limit;
- no model exceeds tier budgets;
- all model blocks stay within declared bounds;
- no duplicate/conflicting block states exist at one position.

### Statistical tests

Across a fixed seed suite:

- required variants are reachable;
- tier frequencies remain within tolerance;
- crown asymmetry is neither zero nor unbounded;
- dead/damaged events remain rare and plausible;
- model size distribution remains stable;
- forest spacing does not collapse.

### Visual fixtures

Create a development-only tree gallery generator:

- fixed flat test world or generated structure area;
- rows by species;
- columns by BASIC/MID/HERO and environmental variant;
- labels and fixed seeds;
- screenshots from four standard angles.

Visual review remains a release gate.

## 13. Migration plan

### Phase A — foundation (no runtime replacement)

- introduce pure tree model records;
- introduce profile validation;
- introduce deterministic seed stream;
- implement model fingerprint and budget tests;
- keep existing `buildTree()` active.

### Phase B — first growth family

- implement `CONIFER_LAYERED` first;
- create spruce BASIC/MID variants;
- build voxel rasterization and validation;
- add gallery/debug command;
- do not enable world placement until visual approval.

### Phase C — broadleaf foundation

- implement `BROADLEAF_SPREADING`;
- create oak BASIC/MID variants;
- add canopy negative-space system;
- add open-grown and forest-column environmental response.

### Phase D — biome integration

- add species composition profiles;
- add candidate suppression and spacing;
- select vanilla replacement policy;
- enable only a small biome allowlist first.

### Phase E — expansion

- birch, pine, dark oak, acacia, jungle and mangrove families;
- old-growth and damaged variants;
- HERO layer;
- compatibility hooks for modded biomes.

## 14. Non-goals for the foundation

The foundation phase will not:

- immediately replace every vanilla tree;
- generate every Minecraft species at once;
- enable HERO trees throughout normal forests;
- place blocks before the model/validator tests exist;
- merge tree generation into `VegetationProvincePass.buildTree()` as a large method;
- depend on HTML/WebGL code at runtime.

The HTML prototype may be used as a visual laboratory and algorithm reference, but Java runtime code must be designed for deterministic Minecraft world generation and chunk safety.
