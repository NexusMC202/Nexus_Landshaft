# Nexus V2 runtime surface architecture

Статус: физически подключено к генерации новых `nexus_v2` chunks.

## Pipeline

```text
Tectonic-derived density
  -> vanilla/NeoForge biome surface rules
  -> SurfaceProvincePass
  -> RiverWaterPass (финальный владелец channel/lake/overflow)
  -> placed features
```

`SurfaceProvincePass` вызывается только из
`NexusV2ChunkGenerator.buildSurface`. Он не меняет codec/preset и не требует
mixin.

## Chunk-local sampling

Для одного chunk создаются:

- один `NexusV2FieldSampler`;
- один `NexusV2HydrologySampler`;
- сетка 18×18 climate/analytical-height samples;
- один biome lookup на обрабатываемую колонку;
- один immutable `SurfaceContext` на колонку.

Сетка имеет аналитическую рамку в один блок для central-difference slope.
Рамка вычисляется по seed/absolute coordinates и не читает соседний chunk.

Registry IDs всех palette blocks разрешаются один раз при инициализации класса
и хранятся в immutable map. В цикле по блокам registry lookup отсутствует.

## Context

Runtime context содержит seed, абсолютные X/Z, surface Y, biome key, province,
temperature, humidity, continentalness, erosion, weirdness, normalized height,
slope, river distance/mask/influence, lake mask, coast/ocean proxy,
groundwater, volcanic/glacier/alpine/canyon/karst/mycelial/archipelago weights
и два coordinate-noise значения.

## Blending

Приоритет:

```text
river -> lake -> wet bank -> coast -> volcanic -> alpine -> slope -> base
```

Каждая influence mask проходит smoothstep. Выбор внутри перехода dithering-ом
с непрерывным value noise масштаба 48 блоков. Material variation использует
отдельный масштаб 11 блоков. Hash зависит только от seed и absolute X/Z;
границы chunk не входят в формулу.

## Physical application

Resolver выбирает top palette, substrate palette и глубину 1–8. Pass заменяет
только известные естественные surface blocks текущего chunk. Air, fluids,
ores, structures и неизвестные blocks не заменяются.

Под водой `OCEAN_FLOOR_WG` выбирает seabed вместо поверхности воды.
`RiverWaterPass` выполняется после profile pass и поэтому сохраняет authority
над физическим руслом, basin carve, water fill, shoreline и overflow.

## Unknown and optional biomes

Все 53 vanilla Overworld biome IDs имеют явный profile. Неизвестные и optional
mod biome keys используют climate-aware family:

- cold → snowy plains;
- hot/dry → desert;
- saturated → swamp;
- humid → forest;
- neutral → plains;
- underground wet/dry → lush/dripstone cave profile.

Nature’s Spirit не импортируется и не является зависимостью. Unknown key
логируется один раз в bounded set максимум на 256 записей.

## Ограничения текущей итерации

- наземная vegetation grammar ещё не подключена;
- отдельный cave-material pass ещё не подключён;
- runtime palette counters и timing telemetry ещё не экспортируются;
- первый fresh-world smoke seed 240802 дошёл до server `Done`, но survey hook
  после старта не сформировал отчёт и требует исправления.
