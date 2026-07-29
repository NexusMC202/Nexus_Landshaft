# Nexus Landscape — world language

## Goals

The terrain should create memorable routes, not only screenshots. Broad valleys,
river terraces and ridge saddles are kept driveable for Aeronautics vehicles,
while cliffs, arches and peaks form distant landmarks. Extreme terrain is
clustered into regions so it does not turn the entire world into impassable noise.

## Region families

1. **Continental valleys** — wide rivers, floodplains, forest shelves and road-like
   natural terraces.
2. **Humid eastern highlands** — misty karst towers, arches, waterfalls and dense
   vegetation; the planned East-Asian climate region.
3. **Painted badlands** — layered mesas cut by seasonal streams, with broad
   traversable basins.
4. **Volcanic chains** — shield volcanoes, broken calderas, basalt lava fields and
   geothermal springs.
5. **Mycelial reaches** — giant mushroom groves, luminous hollows and coherent
   island chains instead of isolated biome specks.
6. **Ocean archipelagos** — flat coral islands, mountain islands, sea stacks and
   rare floating mythic islands.

## Cave grammar

Caves have three scales: travel tunnels, chambers and landmark caverns. Landmark
caverns may contain a daylight shaft with a large tree, an underground lake, rare
flowers, or a spider-web territory hook. Deep-dark transitions become narrower,
darker and more asymmetric before opening into ancient-city-scale chambers.

## Reality anchors

Small believable places make the fantastic terrain feel grounded: geothermal
basins, mineral terraces, springs, river confluences, talus fields, erosion
arches, sheltered flower pockets and cave skylights.

## Compatibility

Nature's Spirit is optional. Features resolve its plants by registry ID at
runtime and fall back to vanilla vegetation. No hard dependency is required.

## Delivery roadmap

- 0.1: world preset, terrain profile, hot springs, cave sanctums, ocean islands.
- 0.2: macro-climate noise, connected trunk/tributary river valleys, depositional
  river banks and sparse humid-region karst arches.
- 0.3: regional mountain chains, rolling hills, volcanic calderas, river-cut
  vehicle corridors and the `/nexuslandscape route_audit` slope sampler.
- 0.4: clustered oceanic archipelago uplift, mountain islands, coral atolls,
  archipelago-bound floating islands and luminous mycelial groves.
- 0.5: sparse large 3D chambers, tree sanctums, spider territories, Deep Dark
  rifts and rare flower grottos.
- 0.6: Sodium 0.8.12-safe regional atmosphere using stable NeoForge rendering
  events without linking to Sodium internals.
- 0.7: unified river/biome signals, mountain-biome and island-biome alignment,
  all-53-biome coverage audit, traversable mountain arches, biome-aware surface
  landmarks and deadlock-free spider nest decoration.
