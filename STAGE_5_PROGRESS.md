# Stage 5 progress — physical hydrology

Status: **strong intermediate result; Stage 5 is not accepted or complete**.
Stages 6–10 have not been started.

## Implemented

- Physical river profiles have six lateral zones: channel center, deep bed,
  shallow shelf, wet bank, depositional bank and floodplain transition.
  Depth and sediment vary with signed centerline distance, stream order,
  slope, river family, province and floodplain weight.
- Final stream order is sampled from the nearest canonical segment. It is not
  propagated by taking the maximum of four bilinear-grid corners.
- Cave support is classified across the confirmed channel width as safe
  ground, thin roof, cave entrance, aquifer, allowed river-cave connection or
  random breakthrough.
- Physical basins now carve a bed, fill water, form a shoreline and sediment
  profile, accept inflow, and create a deterministic physical outlet/overflow
  channel for open basins.
- Every basin has one canonical water surface derived from perimeter samples.
  Local `surfaceY + 2` no longer raises individual columns.
- Basin placement precomputes the current chunk mask and analytically checks
  out-of-chunk neighbours. Isolated lake candidates are rejected before block
  placement without loading neighbouring chunks.
- Chunk-edge support uses analytical terrain confidence for the missing part
  of the support volume. Deep profiles are conservatively rejected when the
  analytical support is too thin.
- Runtime counters are held in weak `RandomState` contexts and support explicit
  snapshot/reset, so survey data from different worlds is not mixed.
- The seed-aware case finder reports open lakes, closed basins, deterministic
  overflow, confluences, ocean outlets and mountain sources.
- Diagnostics export raw drainage graph, final warped centerlines, defect
  overlay, canonical/final order, water level, local atlas crops and terrain
  error classifications.

## Synthetic verified

- `RegionalFieldMathSelfTest` passes:
  - 6,110 hydrology seam samples;
  - 4,211 strictly downhill canonical edges;
  - 156 confluences and bounded terminal/lake profiles;
  - 664 reverse-order and four-thread graph queries;
  - one open lake and 34 deterministic overflow profiles.
- Basin seam suite passes 656 samples over six targeted basins and 17
  continuity transitions. It compares basin ID, outlet ID, reason, mask,
  shoreline weight, bed Y, water Y and radial distance at:
  - ordinary chunk boundaries;
  - four-chunk corners;
  - hydrology tile boundaries;
  - negative coordinates;
  - cold/warm cache;
  - forward/reverse query order;
  - multithreaded sampling.
- `clean build` passes on Java 21, including JSON resources and all deterministic
  tests.

## Runtime verified

Real coordinates found on three seeds:

- Seed `240802`:
  - open lake/outlet: `X=-18024 Z=-34970`;
  - closed basin: `X=19504 Z=-34244`;
  - deterministic overflow: `X=13294 Z=-36491`;
  - confluence: `X=-32627 Z=-36537`;
  - ocean outlet: `X=-21095 Z=-36500`;
  - mountain source: `X=-27204 Z=-524`.
- Seed `918273645`:
  - open lake/outlet: `X=32785 Z=-36440`;
  - closed basin: `X=-4134 Z=-35697`;
  - deterministic overflow: `X=-7326 Z=-35017`;
  - confluence/ocean outlet: `X=-36532 Z=-36510`;
  - mountain source: `X=18704 Z=-6564`.
- Seed `-41027`:
  - open lake/outlet: `X=-11150 Z=-36521`;
  - closed basin: `X=-15826 Z=-34132`;
  - deterministic overflow and five-way confluence:
    `X=-19534 Z=-36470`;
  - ocean outlet: `X=-28675 Z=-36556`.

Fresh targeted runtime worlds:

- Open lake, seed `240802`, `X=-18024 Z=-34970`, radius 10 chunks:
  - lake columns carved: 47,806;
  - basin water blocks: 192,482;
  - shoreline columns: 20,571;
  - overflow columns: 6,520;
  - raised columns, mask leaks, floating water, isolated columns and
    water-level mismatches: 0;
  - neighbour reads and out-of-bounds attempts: 0.
- Closed basin, seed `918273645`, `X=-4134 Z=-35697`, radius 13 chunks,
  fresh confirmation after isolated-column rejection:
  - lake columns carved: 113,442;
  - basin water blocks: 512,865;
  - shoreline columns: 50,212;
  - overflow columns: 0, as required for a closed basin;
  - 1,131 isolated candidates rejected before placement;
  - raised columns, mask leaks, floating water, isolated columns and
    water-level mismatches: 0;
  - neighbour reads and out-of-bounds attempts: 0;
- Deterministic overflow and five-way confluence, seed `-41027`,
  `X=-19534 Z=-36470`, radius 10 chunks:
  - overflow columns: 8,999;
  - basin water blocks: 9,397;
  - accepted channel columns: 57,729;
  - raised columns, mask leaks, floating water, isolated columns and
    water-level mismatches: 0;
  - neighbour reads and out-of-bounds attempts: 0.
- Ocean outlet, seed `-41027`, `X=-28675 Z=-36556`, radius 6 chunks:
  - attempted channel columns: 44,813;
  - accepted channel columns: 19,106;
  - neighbour reads and out-of-bounds attempts: 0;
  - zero new water blocks is expected where the channel enters existing ocean
    water and is not counted as lake-fill evidence.
- Mountain source, seed `918273645`, `X=18704 Z=-6564`, radius 5 chunks:
  - attempted channel columns: 14,564;
  - accepted channel columns: 11,994;
  - water blocks placed: 4,740;
  - neighbour reads and out-of-bounds attempts: 0.

Saved evidence is under `docs/worldgen/stage5/`, grouped by seed and case.

Measured network diagnostics for seed `-41027`:

- segment length P10/P50/P90: 744.28 / 1,018.70 / 1,135.45 blocks;
- short branches: 0;
- angle P10/P50/P90: 0.96° / 43.53° / 88.78°;
- parallel channels: 0;
- right-angle junctions: 415;
- sink stars: 232;
- mean confluence angle: 37.90°;
- mean sinuosity: 1.0041;
- trunk continuity: 93.66%;
- monotonically downhill sampled centerline: 93.72%;
- canonical/final order mismatches: 0.

## Visually accepted

None yet. Generated shader-free diagnostic maps and atlas crops are saved, but
they are not substitutes for the required in-game screenshots with F3.

## Failed

- Stage 5 is not accepted or complete.
- River-network quality is not accepted: the current atlas exposes 415
  right-angle junctions, 232 sink-star defects, low mean sinuosity (1.0041),
  and only 93.72% monotonically downhill centerline samples.
- Shader-free in-game screenshots are still missing for top view, along-river
  view, cross-section, bank, outlet/overflow and F3 seed/coordinates.
- No case is marked visually accepted from an analytical image alone.
- Stages 6–10, glaciers and biome expansion remain intentionally untouched.
