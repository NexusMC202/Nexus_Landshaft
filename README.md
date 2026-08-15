# Nexus Landscape

World-generation mod for the NexusMC NeoForge 1.21.1 modpack.

Version **0.8.0** replaces the experimental macro terrain with an adapted,
self-contained Tectonic 3.0.26 density graph. It produces coherent continents,
long ridges, deep oceans, eroded valleys and regional island chains without
requiring Tectonic or Lithostitched at runtime. Nexus adds its own climate layer:
large temperature and humidity regions, coastal moderation, elevation cooling,
inland drying and wetter river corridors. The complete set of Minecraft 1.21.1
Overworld biomes remains reachable, including mushroom fields and underground
biomes.

The preset includes connected valleys and underground rivers, climate regions,
concentrated mountain chains, rolling vehicle corridors, volcanic calderas,
coherent archipelagos, coral atolls, mycelial groves, hot springs, humid karst
arches, large cave chambers, tree sanctums, safe spider territories, Deep Dark
rifts, rare flower grottos and floating islands. Large traversable mountain
arches and biome-aware landmarks make regions recognizable without filling
every open route with decoration. Nature's Spirit and Nexus Mobs remain
optional integrations.

Hot springs are now excavated into the ground, atolls rise from shallow ocean
floors, floating islands are substantially larger and rarer, and humid karst
appears as asymmetric tower clusters rather than repeated stone gates.

The optional client visual layer targets **Sodium 0.8.12.x** without mixins or
references to Sodium internals. It uses stable NeoForge viewport events for
regional fog colour and distance, and falls back to the vanilla renderer when
Sodium is absent. Humid karst, volcanic, mycelial and Deep Dark regions each
have their own restrained atmospheric profile.

See [DESIGN.md](DESIGN.md) for the terrain language, biome rules and roadmap.
Third-party attribution is recorded in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Development

Requirements: Java 21.

```powershell
.\gradlew.bat clean build
```

The installable mod is written to `build/libs/`. Use the versioned
`nexus_landscape-*.jar`; do not install sources, javadoc, or an older JAR left
by a previous build.

The opt-in development survey generates real full chunks, exports a biome and
height overview, counts placed landmarks and can audit all 53 Overworld biomes.
It is disabled in normal installations.

Operators can measure local vehicle traversal with:

```text
/nexuslandscape route_audit 64
```
