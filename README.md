# Nexus Landscape

World-generation mod for the NexusMC NeoForge 1.21.1 modpack.

Version **0.6.0** adds a separate **Nexus** world preset, large-scale
terrain shaping, connected trunk-and-tributary river valleys, macro-climate
regions, regional mountain chains, rolling hills, volcanic calderas, coherent
archipelago chains, coral atolls, mycelial groves, hot springs, humid karst
arches, large cave chambers, tree sanctums, spider territories, Deep Dark
rifts, rare flower grottos and floating islands. The project is designed to
stay compatible with vehicle-heavy exploration and to integrate with Nature's
Spirit and Nexus Mobs when they are installed.

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

Operators can measure local vehicle traversal with:

```text
/nexuslandscape route_audit 64
```
