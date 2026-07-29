# Nexus Landscape

World-generation mod for the NexusMC NeoForge 1.21.1 modpack.

Version **0.7.0** rebuilds the macro terrain of the separate **Nexus** world
preset. Rivers now follow the same climate signal as river biomes, mountain
uplift also drives mountain-biome selection, and raised archipelagos participate
in continentalness instead of inheriting ocean biomes. The complete set of 53
Minecraft 1.21.1 Overworld biomes remains reachable, including mushroom fields
and all three underground biomes.

The preset includes connected trunk-and-tributary valleys, climate regions,
concentrated mountain chains, rolling vehicle corridors, volcanic calderas,
coherent archipelagos, coral atolls, mycelial groves, hot springs, humid karst
arches, large cave chambers, tree sanctums, safe spider territories, Deep Dark
rifts, rare flower grottos and floating islands. Large traversable mountain
arches and biome-aware landmarks make regions recognizable without filling
every open route with decoration. Nature's Spirit and Nexus Mobs remain
optional integrations.

The optional client visual layer targets **Sodium 0.8.12.x** without mixins or
references to Sodium internals. It uses stable NeoForge viewport events for
regional fog colour and distance, and falls back to the vanilla renderer when
Sodium is absent. Humid karst, volcanic, mycelial and Deep Dark regions each
have their own restrained atmospheric profile.

See [DESIGN.md](DESIGN.md) for the terrain language, biome rules and roadmap.

## Development

Requirements: Java 21.

```powershell
.\gradlew.bat build
```

The built mod is written to `build/libs/`.

The opt-in development survey generates real full chunks, exports a biome and
height overview, counts placed landmarks and can audit all 53 Overworld biomes.
It is disabled in normal installations.

Operators can measure local vehicle traversal with:

```text
/nexuslandscape route_audit 64
```
