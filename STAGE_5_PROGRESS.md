# Stage 5 progress — landforms, hydrology and glacial integration

Status: **partially working; Stage 5 is not accepted or complete**.
Stages 6–10 have not been started.

## Implemented

- A single `AnalyticalTerrainMath` envelope now supplies:
  terrain density, diagnostic surface Y, hydrology terrain Y, river bed
  calculation and river-water validation.
- The envelope explicitly composes continent base, province uplift, active
  landform offset, broad valley, canyon incision, glacier carve and bounded
  regional erosion.
- Hydrology nodes now expose canonical ID, downstream ID, basin/outlet ID,
  stream order, bounded upstream accumulation, bed Y, water Y and terminal
  reason.
- Allowed terminal reasons are represented: ocean outlet, bounded lake,
  wetland sink and deterministic overflow outlet.
- Lake terminals have a bounded profile with center, boundary radius, maximum
  area/depth, water surface, inflow and closed/outlet state.
- River centerlines use canonical jittered nodes and curved segments. Signed
  distance, order, bed and water fields are deterministic at negative
  coordinates and chunk boundaries.
- `RiverWaterPass` writes only to the supplied current `ChunkAccess`. It carves
  confirmed channels, places water, gravel/sand/clay sediment and rejects
  terrain mismatches or unrelated cave intersections.
- The river cross-section is no longer one uniform vertical trench. It has
  channel center, deep bed, shallow shelf, wet bank, depositional bank and
  floodplain-transition zones. Depth and sediment depend on signed centerline
  distance, canonical stream order/accumulation, analytical slope, river
  family, province and floodplain weight.
- Bilinear corner maximum is no longer used for final river order. The coarse
  grid is rejection-only; every accepted column is resampled against its
  nearest canonical segment.
- Cave support is checked in a bounded volume across the local channel normal
  and classified as safe ground, thin roof, cave entrance, aquifer, allowed
  river-cave connection or random breakthrough.
- Counters are isolated by `RandomState` using weak run contexts. Survey can
  take an explicit snapshot or snapshot-and-reset for one generation run.
- Lake terminals now have a physical chunk-local basin profile with carve,
  water fill, shoreline and sediment. Open lakes carry a deterministic outlet;
  overflow terminals expose a physical breach-channel profile.
- River pass sampling was reduced to a deterministic 5×5 grid per chunk with
  local interpolation. Canonical node terrain samples are cached by absolute
  coordinate.
- Diagnostics export analytical terrain error, river bed/water/order, canyon,
  glacier, composite landform and four separate mountain-family atlases.
- Terrain error diagnostics now export a classified error map and counts for
  cave exposure, surface-rule effects, aquifers, density mismatch,
  interpolation, landmark/features and unknown errors. Error is also grouped
  by plain, young massif, old massif, plateau, volcano, canyon and glacier
  regions when those regions occur in the surveyed crop.
- Canonical-segment and final-placement order maps are exported separately.
- A GitHub Actions workflow now defines Java 21 setup, JSON validation and a
  clean Gradle build including deterministic/seam tests.

## Verified locally

- Framework-free field tests:
  - 9 synthetic provinces;
  - 8 moods;
  - 6 active landform channels;
  - dramatic share 11.37%;
  - 6,110 chunk-boundary seam checks;
  - 4,211 strictly downhill node edges;
  - 156 confluences;
  - bounded terminal/lake profiles;
  - 664 reverse-order and four-thread deterministic requests.
- Fresh world `nexus-v2-stage5-envelope-smoke-d`:
  - analytical terrain MAE 2.549 blocks;
  - P95 21.633 blocks;
  - maximum error 36.720 blocks.
- Confirmed river location for seed `240802`, center `X=-3040 Z=-4576`:
  - 831 river samples;
  - analytical terrain MAE 1.328 blocks;
  - P95 1.954 blocks;
  - bed depth 1.932–9.039 blocks, mean 6.036;
  - bed above surface 0%;
  - floating water 0%;
  - buried channel 0%.
- Fresh targeted world `nexus-v2-stage5-envelope-smoke-h`:
  - 43,876 channel columns attempted;
  - 42,601 accepted;
  - 202,435 blocks carved;
  - 78,926 water blocks placed;
  - 85,202 sediment blocks placed;
  - 819 terrain mismatches rejected;
  - 456 cave intersections rejected;
  - out-of-bounds attempts 0;
  - neighbour reads 0.
- Fresh profiled-river world `nexus-v2-stage5-profile-lakes-smoke-j`, seed
  `240802`, center `X=-3040 Z=-4576`, radius 12 chunks:
  - server reached `Done` without a registry/worldgen crash;
  - 64,982 profile columns attempted and 44,221 accepted;
  - 36,840 blocks carved, 11,349 water blocks and 46,048 sediment blocks;
  - 33,622 safe-ground profiles, 10,351 aquifers and 248 accepted river-cave
    connections;
  - 261 thin roofs and 335 random breakthroughs rejected;
  - out-of-bounds attempts 0 and neighbour reads 0;
  - terrain error MAE 2.625, P95 11.139 and maximum 14.859 blocks;
  - terrain-error classes: 6,846 surface-rule, 940 unknown, 517 aquifer,
    516 interpolation, 257 density mismatch, 115 cave exposure and 25
    landmark/feature samples.
- The deterministic graph suite now verifies one open lake with an outlet and
  34 physical overflow-channel profiles in the synthetic acceptance area.
- No registry, datapack, cascading-worldgen, deadlock or crash error was found
  in the accepted smoke logs.
- GitHub Actions run
  [30522875305](https://github.com/NexusMC202/Nexus_Landshaft/actions/runs/30522875305)
  completed successfully on commit `6e985f2`: Java 21 setup, all JSON resource
  parsing, clean Gradle build and deterministic/seam tests passed.

## Visually inspected

- The analytical terrain error map.
- The regional river-network, order and water-level atlases.
- The local generated survey map at `X=-3040 Z=-4576`.
- The current network is less lattice-like than the first prototype, but short
  angular branches and sink-star patterns are still visible in some regions.

## Partially working

- Terrain-following river beds work in the verified lowland river area, but
  continuous downhill behavior between nodes still needs a centerline-profile
  metric rather than only the strict node-edge test.
- Lake/overflow geometry is implemented and unit-verified, but has not yet
  produced non-zero physical basin counters in a real targeted lake world.
  The accepted river coordinate is not a lake or overflow site.
- The four mountain families have separate fields/maps, but shape metrics and
  in-game examples for each family are incomplete.
- Glacier mass affects terrain carve, but it is not yet a complete glacier.
- CI is green for the current implementation, but it does not replace the
  missing multi-seed and visual acceptance suite.

## Not implemented

- Raw-graph versus final-warped-centerline map pair and full segment/angle/
  parallel-channel metrics.
- Full ridge continuity, orientation, saddle, terrace, slope, peak isolation,
  canyon continuity and caldera-circularity metrics.
- Complete glacier accumulation zone, downhill ice flow, cirque, tongue,
  U-valley, moraine, glacial-lake eligibility and altitude vegetation bands.
- Five-seed acceptance suite and later 20-seed suite.
- Required vanilla screenshots and atlas crops for every acceptance case.

## Failed criteria / known defects

- Stage 5 cannot be accepted from one seed and one confirmed river.
- P95/max analytical terrain error remains high in the original mountainous
  smoke area, largely where cave/terrain final-density modifications diverge
  from the envelope; this requires classification and reduction.
- River pass previously classified vertically collapsed columns as
  out-of-bounds. The classification was corrected and the fresh targeted run
  proved `out_of_bounds.attempts=0`.
- Physical lake and overflow code exists, but real seed coordinates, non-zero
  runtime counters and shader-free visual evidence are still missing.
- Lattice/parallel/angle acceptance metrics do not yet exist.
- No shader-free in-game screenshot has yet been captured for the confirmed
  river coordinate.

River-mask coverage is diagnostic context only and is not treated as evidence
of river quality.
