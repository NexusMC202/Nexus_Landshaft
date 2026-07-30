# Stage 4 — Macro Terrain and Geological Provinces

Статус: реализован первый рабочий V2 foundation; legacy preset не изменён.
Проверочный seed: `240802`.
Дата проверки: `2026-07-30`.

## 1. Изученные файлы и API

- `WORLDGEN_AUDIT.md`;
- `BIOME_MATRIX.md`;
- `WORLDGEN_ARCHITECTURE_V2.md`;
- `build.gradle` и `gradle.properties`;
- `NexusLandscape.java`;
- `WorldgenSurvey.java`;
- `worldgen/noise_settings/nexus.json`;
- `worldgen/world_preset/nexus.json`;
- активные Tectonic `base_terrain`, `final_density`, `initial_density` и
  continentalness functions;
- Minecraft 1.21.1 `NoiseBasedChunkGenerator`, `ChunkGenerator`,
  `DensityFunction`, `DensityFunctions` и `RandomState` sources;
- NeoForge registries `CHUNK_GENERATOR` и `DENSITY_FUNCTION_TYPE`.

## 2. Изменённые системы

### 2.1 Versioned V2 world

Добавлены:

- `nexus_landscape:nexus_v2` world preset;
- `nexus_landscape:nexus_v2` noise settings;
- `nexus_landscape:nexus_v2` chunk-generator codec;
- `nexus_landscape:regional_field` density-function codec.

`nexus_landscape:nexus` остался legacy preset/settings. V2 не подменяет
генератор уже существующих миров.

`NexusV2ChunkGenerator` на этапе 4 полностью делегирует стандартному
`NoiseBasedChunkGenerator`. Благодаря этому generator type уже сохранён в
`level.dat`, а будущий chunk-local `RiverWaterPass` можно добавить без смены
codec существующего V2-мира.

### 2.2 Continent field

`v2/continent/base` состоит из:

```text
0.78 * low-frequency V2 continent shape
+ 0.22 * Tectonic continental detail
+ 0.04 land bias
```

V2 continent noise:

- `firstOctave = -12`;
- amplitudes `[1.0, 1.0, 0.5, 0.25]`;
- shifted-noise XZ scale `0.28`.

`v2/continent/delta_from_legacy` подключён к base terrain и preliminary
density с коэффициентом `0.38`. Это промежуточный migration bridge:
крупномасштабная форма уже V2, а проверенные Tectonic splines пока
сохраняются как detail library.

### 2.3 Province and composition field

`RegionalFieldMath` определяет десять province weights:

1. sedimentary lowland;
2. wetland basin;
3. old eroded highland;
4. young fold mountains;
5. glacial massif;
6. dry plateau;
7. volcanic belt;
8. karst belt;
9. oceanic crust;
10. mycelial craton.

Эта же реализация определяет десять mood weights, четыре rhythm phases,
hierarchy strength и macro uplift. Province и mood не выбираются из biome ID.

Field использует:

- два warp noise;
- province macro/detail/ridge;
- volcanic arc;
- composition;
- существующие climate temperature/humidity noise parameters;
- V2 continentalness.

Warp amplitude — `420` блоков. Macro/detail scales лежат между `1/1536` и
`1/5120`; composition scale — `1/4096`.

`macro_uplift` ограничен диапазоном `-0.12..1.0`, умножается на `0.18` в
terrain density и обёрнут в `flat_cache`, потому что не зависит от Y.
Erosion получает ограниченную поправку `-0.08 * macro_uplift`.

### 2.4 Один источник математики

`RegionalFieldDensityFunction` и `NexusV2FieldSampler` вызывают один
`RegionalFieldMath`. Density graph и diagnostic maps поэтому используют
одинаковые:

- province scores;
- mood scores;
- rhythm thresholds;
- hierarchy;
- uplift.

Noise instances создаются `RandomState` из world seed и resource keys.
Поля не зависят от порядка генерации чанков.

### 2.5 Diagnostics

Обычный chunk survey теперь экспортирует:

- `province.png`;
- `mood.png`;
- `rhythm.png`;
- `hierarchy.png`;
- `macro-uplift.png`.

Field-only atlas не вызывает `getChunk` и покрывает область
`49 152 × 49 152` блоков с шагом `128`:

- `province-atlas.png`;
- `mood-atlas.png`;
- `rhythm-atlas.png`;
- `hierarchy-atlas.png`;
- `macro-uplift-atlas.png`;
- `regional-atlas.txt`.

Сохранённые доказательства:

- [province atlas](docs/worldgen/stage4/seed-240802/province-atlas.png);
- [mood atlas](docs/worldgen/stage4/seed-240802/mood-atlas.png);
- [rhythm atlas](docs/worldgen/stage4/seed-240802/rhythm-atlas.png);
- [hierarchy atlas](docs/worldgen/stage4/seed-240802/hierarchy-atlas.png);
- [macro uplift atlas](docs/worldgen/stage4/seed-240802/macro-uplift-atlas.png);
- [regional metrics](docs/worldgen/stage4/seed-240802/regional-atlas.txt);
- [generated terrain survey](docs/worldgen/stage4/seed-240802/survey.txt).

## 3. Проверки

### 3.1 Math verification

Команда:

```powershell
.\gradlew.bat regionalFieldTest
```

Проверено:

- сумма province weights = `1`;
- сумма mood weights = `1`;
- диапазоны outputs;
- повторный sample совпадает;
- непрерывность между X=`15` и X=`16`;
- synthetic coverage: `9` provinces;
- synthetic coverage: `8` moods;
- dramatic share: `11.37%`, лимит `35%`.

### 3.2 Resource and build verification

- `261` JSON прочитан без syntax errors;
- `gradlew check` завершён успешно;
- custom density codec загружен datapack parser;
- custom chunk generator codec прочитан из world preset;
- Java 21 compilation завершена успешно.

### 3.3 Dedicated server

Создан новый мир:

```text
level-name=nexus-v2-stage4-smoke-b
level-type=nexus_landscape:nexus_v2
level-seed=240802
```

Server:

- создал Overworld;
- подготовил spawn;
- завершил survey;
- сохранил все dimensions;
- остановился штатно;
- не записал registry/datapack exceptions;
- не записал cascading или deadlock warnings.

Локальный generated survey:

```text
height.min=63
height.max=138
height.mean=87.93
height.standard_deviation=23.29
slope.mean_per_4_blocks=4.11
water.percent=40.72
```

### 3.4 Regional atlas, seed 240802

```text
samples=147456
province.unique=6
mood.unique=6
rhythm.unique=4
dramatic.percent=4.21
hierarchy.mean=0.0347
macro_uplift.mean=0.0332
```

Наблюдались:

- sedimentary lowland;
- oceanic crust;
- old eroded highland;
- wetland basin;
- karst belt;
- glacial massif.

## 4. Почему выбрана эта реализация

Vanilla JSON density functions умеют непрерывную математику, но не имеют
семантического province output. Custom density codec позволяет:

- сохранить стандартный noise chunk generator;
- получить seed-aware поля через штатный `RandomState`;
- использовать один алгоритм в terrain и diagnostics;
- не читать блоки или соседние чанки;
- позднее предоставить те же weights surfaces, caves и vegetation.

Отдельный V2 preset предотвращает изменение незагруженных чанков legacy-мира.

## 5. Компромиссы

1. Tectonic terrain splines и cave primitives пока сохранены.
2. V2 continentalness подключён через bounded density delta, а не через
   полную замену всех Tectonic spline coordinates.
3. Из десяти определённых провинций один seed/atlas наблюдал шесть; gate этапа
   требует минимум шесть.
4. Composition intent влияет на uplift/rhythm, но vegetation, vistas и wonder
   budget будут потреблять его на следующих этапах.
5. Dedicated smoke test использовал небольшой generated chunk radius; крупный
   atlas является field-only и не измеряет время генерации всех этих chunks.

## 6. Оставшиеся риски

1. Нужен 20-seed distribution test для частоты всех десяти провинций.
2. Коэффициенты `0.38` continent delta и `0.18` uplift требуют визуальной
   калибровки на больших generated maps.
3. P95/P99 chunk time ещё не измерены.
4. Young, dry, volcanic и mycelial provinces могут быть слишком редкими.
5. Полный continent spline cutover остаётся техническим долгом.
6. Hydrology, glacier forms, canyon incision и четыре mountain families
   относятся к этапу 5.

## 7. Статус критериев

Выполнено для этапа 4:

- versioned V2 settings/preset;
- стабильный delegating generator codec;
- deterministic province/composition fields;
- десять определённых province profiles;
- минимум шесть провинций наблюдались на реальном seed;
- крупный continent field;
- province/mood/rhythm/hierarchy exports;
- math, build и dedicated-server gates;
- legacy IDs сохранены.

Не выполнено для всего проекта:

- четыре законченных mountain families;
- связная surface hydrology;
- glacier/canyon/coast systems;
- 53 surface/vegetation signatures;
- шесть cave provinces;
- migration всех 13 landmarks;
- 20-seed performance и visual acceptance.
