# Nexus Landscape

World-generation mod for the NexusMC NeoForge 1.21.1 modpack.

Version **0.3.0** adds a separate **Nexus** world preset, large-scale
terrain shaping, connected trunk-and-tributary river valleys, macro-climate
regions, regional mountain chains, rolling hills, volcanic calderas, hot
springs, humid karst arches, cave sanctums and ocean islands. The project is
designed to stay compatible with vehicle-heavy exploration and to integrate
with Nature's Spirit when it is installed.

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
