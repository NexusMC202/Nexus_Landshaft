# Nexus V2 vegetation architecture

Статус: подключено к runtime decoration; визуальная приёмка не завершена.

## Lifecycle

`NexusV2ChunkGenerator.applyBiomeDecoration` сначала вызывает vanilla/NeoForge
decoration, затем `VegetationProvincePass`. Это сохраняет совместимость с
biome modifiers и не требует mixin.

Nexus pass не удаляет vanilla blocks: слепое удаление logs/leaves после
decoration могло бы повреждать структуры. Вместо этого он добавляет
детерминированные vegetation accents с жёсткими ограничениями.

## Profiles

`VegetationProfileCatalog` содержит 53/53 профиля и семьи:

- temperate/boreal forest;
- warm dry woodland/savanna/arid;
- tropical humid;
- wetland;
- volcanic pioneer;
- mycelial;
- coastal/meadow/alpine/polar;
- aquatic;
- lush/dripstone/deep-dark cave.

Профиль задаёт tree/shrub/ground/flower/deadwood/rock/old-growth density,
clearing share, maximum tree slope, tree line, tree shapes и ground palette.

Ocean/cave profiles документируют намеренное отсутствие terrestrial grammar.
Unknown optional biomes получают climate-aware fallback без прямого импорта
Nature’s Spirit.

## Spatial grammar

- forest core noise: 176 blocks;
- clearing noise: 104 blocks;
- ground candidate lattice: 4 blocks с coordinate jitter;
- tree candidate lattice: 14 blocks с seed-dependent absolute jitter;
- profile tree/shrub/ground density модифицируется groundwater, height и slope.

Candidate ownership определяется абсолютными lattice cells. Shared mutable
Random и состояние последнего chunk отсутствуют.

## Exclusions

Trees получают smooth reduction:

- slope приближается к `maxTreeSlope`;
- высота приближается к `treeLineY`;
- river mask/influence приближается к active channel;
- clearing field объявляет открытую поляну;
- surface содержит fluid;
- context является underground.

Наземная grammar не запускается в cave profiles. Cave decoration отдельно
добавляет редкие moss/dripstone/sculk accents в подходящем воздушном объёме.

## Runtime cost model

На chunk:

- 16 ground candidates;
- приблизительно 1–4 owned tree candidates;
- 12 ограниченных cave probes;
- biome/context lookup только на candidates, не на каждый block;
- registry IDs ground palette разрешаются один раз в immutable map.

Известное ограничение: vanilla tree placement пока не получает Nexus slope
predicate. Survey должен измерить итоговую, а не только Nexus-added, tree
density на cliffs и в river center.
