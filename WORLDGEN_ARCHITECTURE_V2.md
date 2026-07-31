# Nexus Landscape — Worldgen Architecture V2

Статус: обязательный архитектурный контракт для этапов 4–10.  
Minecraft: `1.21.1`, NeoForge `21.1`, Java `21`.  
Основание: `WORLDGEN_AUDIT.md`, `BIOME_MATRIX.md` и исходное техническое
задание.

Этот документ описывает целевую систему. Он не является заявлением, что V2
уже реализована. Любая реализация, которая отклоняется от контракта, должна
сначала изменить этот документ и объяснить совместимость, стоимость и способ
проверки.

## 1. Цели и границы

V2 должна:

1. формировать континенты, океанические бассейны и крупные острова до выбора
   локальных объектов;
2. выбирать геологическую провинцию независимо от ванильного biome ID;
3. создавать протяжённые региональные формы, а не россыпь одинаковых
   landmarks;
4. использовать один согласованный набор сигналов для terrain, hydrology,
   caves, surfaces и vegetation;
5. сохранять все 53 ванильных Overworld-биома доступными;
6. работать детерминированно по seed;
7. не читать незагруженные соседние чанки;
8. сохранять существующие публичные идентификаторы и features до наличия
   проверенной замены;
9. измерять качество и стоимость минимум на 20 seed.

V2 не должна:

- подменять региональную форму Java-feature с тысячами `setBlock`;
- считать ноль ridge-noise настоящей рекой;
- выбирать geology из biome tag;
- делать cave family локальной декоративной комнатой;
- использовать клиент, Sodium или shader API для серверной генерации;
- создавать обязательную зависимость от Nature's Spirit, Tectonic или других
  внешних модов;
- обращаться к heightmap за границами текущего генерируемого чанка.

## 2. Базовые инварианты

### 2.1 Координатная чистота

Все региональные поля являются чистыми функциями:

```text
sample = f(worldSeed, dimensionId, blockX, blockY, blockZ, configVersion)
```

Результат не зависит от порядка генерации чанков, уже размещённых блоков,
клиента или загруженности соседей.

### 2.2 Один источник истины

Каждый смысловой сигнал имеет одного владельца:

| Сигнал | Владелец | Основные потребители |
|---|---|---|
| continentalness | continent graph | terrain, coast, biome climate |
| shelf/deep ocean | ocean graph | terrain, coast, ocean surfaces |
| province weights | province atlas | terrain, caves, surfaces, vegetation |
| base elevation | landform graph | hydrology, final density, diagnostics |
| slope/exposure | terrain sampler | surfaces, vegetation, landmarks |
| river distance/order | hydrology atlas | terrain, water, surfaces, vegetation |
| glacier mass | glacial graph | terrain, ice, caves, vegetation |
| cave family weights | cave graph | final density, cave surfaces, landmarks |
| ecotone weight | biome transition sampler | surfaces, vegetation |
| mood weights | composition intent atlas | landforms, vegetation, clutter, fog eligibility |
| rhythm phase | composition intent atlas | relief, negative space, landmark eligibility |
| hierarchy anchor/level | composition intent atlas | silhouettes, vistas, landmarks |
| openness/vista direction | composition sampler | vegetation, framing, routes, diagnostics |
| negative-space protection | composition sampler | terrain detail, vegetation, landmarks |
| wonder budget | composition budget atlas | landmark reservation and diagnostics |
| scenic-site score | composition sampler | human-scale places, routes, diagnostics |

Нельзя создавать параллельные «похожие» noise-поля для разных подсистем.
Например, влажность речного коридора обязана читать `river_distance`, а не
близость произвольного ridge-noise к нулю.

### 2.3 Непрерывные веса вместо жёстких швов

Провинции и landform families представлены весами `0..1`. В каждой точке
есть dominant ID для статистики и несколько весов для смешивания. Сумма
нормализованных весов равна `1`, кроме сквозных масок river/coast/ecotone.

Переход между dominant ID не должен автоматически создавать разрыв высоты,
материала или плотности пещер.

### 2.4 Разделение regional form и landmark

Формы длиннее примерно `128` блоков или меняющие связность terrain создаются
density graph либо regional atlas. Feature может добавлять только
ограниченный chunk-local объект, который подчиняется уже существующему
региону.

## 3. Полный pipeline

```text
L0 seed and version
  -> L1 continent/ocean skeleton
  -> L2 geological province + composition intent atlases
  -> L3 regional landform fields
  -> L4 climate and base elevation
  -> L5 hydrology/glacial derived fields
  -> L6 derived composition/vista fields
  -> L7 biome climate selection
  -> L8 surface and cave density
  -> L9 chunk-local water/surface completion
  -> L10 vegetation communities, ecotones and framing
  -> L11 budgeted landmarks
  -> L12 diagnostics
```

### 3.1 L0 — seed and config version

Все random sequences получают устойчивые salt по namespace и версии. Salt не
зависит от порядка регистрации:

```text
nexus_landscape:v2/continent
nexus_landscape:v2/province
nexus_landscape:v2/composition
nexus_landscape:v2/hydrology
nexus_landscape:v2/caves
nexus_landscape:v2/vegetation
nexus_landscape:v2/landmarks
```

Изменение salt или семантики сигнала требует новой версии settings.

### 3.2 L1 — continent and ocean skeleton

Выходы:

- `continent_core`;
- `coast_distance`;
- `shelf_weight`;
- `ocean_basin_depth`;
- `large_island_weight`;
- `archipelago_weight`.

Континентальное поле использует низкочастотную основу, domain warp и
раздельные erosion/detail octaves. Шельф является широким переходом между
сушей и бассейном, а не одинаковой песчаной каймой.

Целевые масштабы:

| Поле | Типичный масштаб |
|---|---:|
| continent core | 8 000–30 000 блоков |
| ocean basin | 6 000–24 000 |
| large island | 700–4 000 |
| archipelago belt | 1 500–8 000 |
| continental shelf | 192–1 024 шириной |

### 3.3 L2 — geological province and composition intent atlases

Province atlas выбирается до biome и не читает блоки мира. Он хранит
непрерывные веса и dominant province.

Composition intent atlas на том же региональном уровне выбирает непрерывные
`mood_weights`, `rhythm_phase`, hierarchy anchors и исходный wonder budget.
Он независим от biome ID. Эти intent-сигналы доступны L3, поэтому настроение
и ритм могут ограничить relief до создания final terrain, не образуя обратной
зависимости от готового вида.

Обязательные провинции:

| ID | Смысл | Основная stone family |
|---|---|---|
| `sedimentary_lowland` | осадочные равнины и террасы | S1 |
| `wetland_basin` | заболоченные низины и озёрные чаши | S2 |
| `old_eroded_highland` | старые округлые горы | S3 |
| `young_fold_mountains` | молодые складчатые горы | S4 |
| `glacial_massif` | ледниковые горы и котловины | S5 |
| `dry_plateau` | плато, mesas и каньоны | S6 |
| `volcanic_belt` | вулканические дуги и кальдеры | S7 |
| `karst_belt` | известняковые поля и башенный карст | S2 |
| `oceanic_crust` | шельф, склон и глубокий океан | S8 |
| `mycelial_craton` | древние грибные острова | S3/S8 |

Province seed строится из warped Voronoi/low-frequency fields. Границы
искажаются двумя независимыми octave-полями. Размер ячейки не является
фиксированным кругом: соседние центры имеют anisotropy и directional bias.

Province placement ограничивается контекстом:

- `oceanic_crust` доминирует в ocean basin;
- `mycelial_craton` только на редких крупных океанических поднятиях;
- `glacial_massif` требует холодного потенциала и горного uplift;
- `volcanic_belt` формирует цепь вдоль directional tectonic field;
- `karst_belt` требует осадочной основы и достаточной влажности;
- `wetland_basin` требует низкого relief и положительного water balance.

### 3.4 L3 — regional landforms

Landform graph получает province weights, но не biome ID.
Он также получает composition intent: dramatic weight усиливает только
разрешённые dominant forms, quiet/recovery weight расширяет долины и
buildable terraces, а hierarchy anchor задаёт приоритет силуэта. Mood не
может отменить геологические ограничения провинции.

Обязательные выходы:

- `regional_uplift`;
- `ridge_axis_distance`;
- `ridge_direction`;
- `spur_weight`;
- `saddle_weight`;
- `valley_weight`;
- `plateau_level`;
- `canyon_incision`;
- `caldera_weight`;
- `karst_relief`;
- `basin_weight`;
- `terrain_roughness`.

Горная цепь строится как искривлённая направленная ось с главным хребтом,
боковыми отрогами и седловинами. Одиночный isotropic peak-noise не может быть
главным mountain signal.

Четыре семейства:

1. young alpine — узкие ridge axes, cirques, высокий uplift и scree;
2. old mountains — широкие ослабленные ridge axes, седловины и речные долины;
3. plateau — несколько устойчивых высотных уровней, scarps и canyon incision;
4. volcanic — directional chains, shield/strato profile blend и разрушенные
   кальдеры.

У каждого семейства должен существовать `buildable_terrace_weight`. Он
ограничивает долю экстремального relief и сохраняет долины, седловины и
естественные площадки для транспорта и строительства.

### 3.5 L4 — climate and base elevation

Климатические выходы:

- `temperature`;
- `humidity`;
- `continentality`;
- `wind_exposure`;
- `rain_shadow`;
- `water_balance`;
- `snow_potential`.

Текущие `climate/temperature`, `climate/humidity` и `climate/erosion`
сохраняются как исходная база, но их речная влажность заменяется зависимостью
от L5. Rain shadow вычисляется из prevailing-wind projection и regional
uplift, без симуляции блоков.

Conceptual base elevation:

```text
base =
    continent_base
  + province_uplift
  + regional_landform
  - regional_erosion
  - broad_valley
```

Detail noise применяется после regional composition и затухает на
строительных террасах, river floors, glacier beds и пляжах.

### 3.6 L5 — derived hydrology and glacial fields

Hydrology читает только L1–L4. Она не читает final terrain, чтобы избежать
цикла. Glacial graph читает climate, uplift, slope и hydrology potential.

После L5 запрещено менять base elevation способом, который нарушает
направление стока. Разрешены bounded detail и surface material.

### 3.7 L6 — derived composition and vista fields

После появления analytical base elevation, hydrology и glacier fields
composition sampler выводит:

- `openness`;
- `probable_view_direction`;
- `focal_distance_range`;
- `silhouette_strength`;
- `foreground_framing_score`;
- `reveal_eligibility`;
- `negative_space_protection`;
- `scenic_site_score`.

Sampler использует только аналитические поля L1–L5. Он не выполняет ray
tracing по блокам и не загружает соседние чанки.

### 3.8 L7 — biome climate selection

На первом V2-релизе сохраняется ванильный
`minecraft:multi_noise/minecraft:overworld`, чтобы не терять совместимость
53 биомов. Router получает согласованные temperature, humidity,
continentalness, erosion, depth и ridges.

Province не заменяет biome. Она определяет геологическое выражение выбранного
биома. Один `minecraft:forest` может выглядеть как осадочный лес, старый
горный лес или влажный карст, сохраняя biome ID.

Если vanilla multi-noise не сможет выполнить measured region-size и biome
speck criteria, допускается custom biome source только отдельной версией
settings после сравнительного 20-seed отчёта.

### 3.9 L8–L11 — density, completion and decoration

Surface terrain и cave carve объединяются в `final_density`. Затем
chunk-local pass завершает речную воду и материалы, surface rules выбирают
верхние слои, community placement создаёт растительность и framing, а
landmarks добавляются последними в пределах wonder budget.

Landmark не имеет права менять водораздел, перекрывать основной речной канал
или создавать новую гору.

## 4. Зависимости сигналов

```text
seed
├─ continent noises
│  ├─ continent_core
│  ├─ coast_distance
│  └─ shelf/ocean_depth
├─ tectonic directional noises
│  └─ province_weights
│     ├─ ridge/plateau/volcanic/karst fields
│     └─ stone profile
├─ composition intent noises
│  ├─ mood_weights
│  ├─ rhythm_phase
│  ├─ hierarchy_anchor/level
│  └─ wonder_budget
├─ climate base noises
│  ├─ temperature_base
│  ├─ humidity_base
│  └─ prevailing_wind
│
continent + province + landform + climate
├─ base_elevation
├─ hydrology_atlas
│  ├─ flow_direction
│  ├─ accumulation/order
│  ├─ river_distance
│  ├─ floodplain/delta/estuary
│  └─ lake/wetland
├─ glacial_fields
│  ├─ accumulation_zone
│  ├─ cirque/tongue
│  └─ moraine/glacial_lake
├─ cave_family_weights
└─ biome climate parameters

base_elevation + hydrology + glacier + composition_intent
└─ composition_fields
   ├─ openness
   ├─ probable_view_direction/focal_range
   ├─ silhouette_strength
   ├─ foreground_framing/reveal
   ├─ negative_space_protection
   └─ scenic_site_score

base_elevation + hydrology + glacier + caves
└─ final_density

biome + province + slope + height + water distance + composition_fields
├─ surface profile
├─ vegetation community
├─ ecotone
├─ framing and route eligibility
└─ budgeted landmark eligibility
```

Запрещённые обратные зависимости:

- province <- biome;
- hydrology <- фактический heightmap уже созданного чанка;
- density <- placed feature;
- cave family <- cave landmark;
- vegetation <- клиентская погода или shader state.
- composition intent <- готовые блоки, screenshot или выбранная игроком
  camera position;
- wonder budget <- результат случайных попыток feature placement.

## 5. Density graph V2

### 5.1 Сохраняемые части

До замены тестами сохраняются:

- ore vein router;
- aquifer barrier/floodedness/spread/lava inputs;
- Tectonic cheese, spaghetti, noodle и pillar primitives;
- существующий world height `min_y=-64`, `height=384`;
- текущий `minecraft:overworld` biome source;
- namespace `nexus_landscape`.

Tectonic functions становятся библиотекой низкоуровневых primitives, а не
семантической архитектурой Nexus.

### 5.2 Новый ownership router

Целевые публичные узлы:

```text
nexus_landscape:v2/continent/*
nexus_landscape:v2/province/*
nexus_landscape:v2/landform/*
nexus_landscape:v2/climate/*
nexus_landscape:v2/hydrology/*
nexus_landscape:v2/glacial/*
nexus_landscape:v2/caves/*
nexus_landscape:v2/router/*
```

`router/final_density` должен ссылаться только на документированные V2
aggregate nodes. Прямые ссылки на десятки внутренних Tectonic paths из
noise settings запрещены.

Conceptual composition:

```text
surface_solid =
    terrain_gradient
  + continent_shape
  + province_landform
  + hydrology_carve
  + glacier_carve
  + bounded_detail

final_density =
    apply_cave_carve(surface_solid, cave_family_density)
```

Знак и clamp каждой операции фиксируются golden-column test до переноса
router. Нельзя полагаться на словесное толкование «плюс carve».

### 5.3 Staged cutover

1. создать V2 aggregate nodes параллельно активным;
2. экспортировать карты обоих графов на одинаковых seed;
3. подключить только continents/provinces;
4. затем landforms;
5. затем hydrology/glaciers;
6. затем caves;
7. удалить или архивировать недостижимые узлы только после reachability
   test и отдельного migration note.

## 6. Geological province system

### 6.1 Runtime representation

`ProvinceSample`:

```text
dominantId
primaryWeight
secondaryId
secondaryWeight
boundaryWeight
stoneProfile
erosionProfile
```

API принимает координаты и immutable generation context. Оно не принимает
`Level`, `ChunkAccess` или `Heightmap`.

### 6.2 Sampling

Province atlas использует coarse cells с halo. Для каждого cell
детерминированно вычисляются:

- jittered center;
- anisotropic axes;
- tectonic direction;
- eligible province set;
- uplift and age;
- warp parameters.

Смешивание идёт по warped distance до двух ближайших eligible centers.
Cell size и anisotropy ограничены config, поэтому карта воспроизводима и
имеет известную верхнюю стоимость.

### 6.3 Consumer contract

| Consumer | Что получает |
|---|---|
| terrain | uplift, landform family, erosion resistance |
| caves | weights Q1–Q6 |
| surfaces | stone/soil palette и boundary blend |
| hydrology | permeability, runoff, erosion resistance |
| vegetation | soil drainage, fertility, disturbance |
| landmarks | eligibility, но не готовую форму |
| diagnostics | dominant ID и weights |

## 7. Hydrology architecture

### 7.1 Почему нужен atlas

Vanilla density function вычисляет точку, но не хранит направленный граф
соседей. Притоки, confluences и устранение закрытых sinks требуют
регионального контекста. Поэтому surface hydrology реализуется как
детерминированный `HydrologyAtlas`, а не как одиночный noise threshold.

Atlas не генерирует и не загружает чанки. Он работает с аналитическим
`base_elevation`.

### 7.2 Tile model

Начальная целевая конфигурация, подлежащая benchmark:

| Параметр | Значение |
|---|---:|
| macro drainage spacing | 256 блоков |
| canonical drainage supercell | 8 192 × 8 192 блоков |
| hydrology sample spacing | 32 блока |
| core tile | 2 048 × 2 048 блоков |
| analytical halo | 512 блоков |
| downstream stitching key | world seed + tile X/Z + version |
| cache | bounded immutable LRU |

Один halo не гарантирует одинаковый flow accumulation по обе стороны tile.
Поэтому верхний уровень строит координатный macro-drainage DAG между
каноническими basin anchors и ocean outlets. Он назначает boundary ports и
направление trunk river. Локальный tile детализирует tributaries между уже
заданными ports. Каждая sample-точка принадлежит одному canonical owner;
соседние tiles запрашивают одно и то же immutable значение owner, а не
пересчитывают собственную версию границы.

### 7.3 Алгоритм

1. выбрать macro basin anchors и ocean outlets;
2. построить ациклический macro drainage graph и canonical boundary ports;
3. sample analytical base elevation локального tile;
4. вычислить slope и восемь downhill candidates;
5. обработать локальные sinks bounded priority-flood/breach с сохранением
   назначенного downstream port;
6. определить local flow direction с детерминированным tie-break;
7. накопить upstream area из macro и local уровней;
8. назначить stream order;
9. построить warped centerline внутри cell corridors;
10. вычислить signed distance до channel;
11. вывести valley, bank, floodplain, lake, delta и estuary masks.

River threshold зависит от water balance, permeability и province, но не
может удалить уже существующий downstream trunk.

### 7.4 Terrain and water application

Hydrology выдаёт:

- `channel_distance`;
- `channel_bed_y`;
- `water_surface_y`;
- `stream_order`;
- `valley_width`;
- `bank_weight`;
- `floodplain_weight`;
- `lake_weight`;
- `delta_weight`;
- `estuary_weight`;
- `waterfall_drop`.

Terrain carve выполняется в density graph либо эквивалентном аналитическом
sampler. Вода выше sea level добавляется отдельным chunk-local generation
pass только в пределах текущего chunk bounding box. Pass получает atlas
sample, но не читает соседние чанки.

До реализации такого pass запрещено заявлять, что elevated rivers являются
полноценной системой. Sea-level river carve является только промежуточным
milestone.

### 7.5 Правила целостности

- bed elevation монотонно не возрастает downstream, кроме epsilon для
  численной устойчивости;
- tributary соединяется с каналом того же или большего order;
- основной канал не обрывается внутри суши без lake/sink/outlet;
- delta и estuary разрешены только около coast-distance zero;
- waterfall появляется на подтверждённом bed drop, а не случайно;
- floodplain width растёт с order и уменьшается в canyon province;
- river placement не использует biome boundary как русло.

## 8. Glacial system

Glacier является производной системой `snow_potential + uplift + basin`,
а не случайным blue-ice feature.

Поля:

- `accumulation_zone`;
- `cirque_bowl`;
- `ice_flow_direction`;
- `glacier_thickness`;
- `glacier_tongue`;
- `u_valley_carve`;
- `hanging_valley`;
- `moraine_deposit`;
- `glacial_lake`;
- `crevasse_weight`;
- `exposed_rock`.

Поток льда следует сглаженному downhill potential. Tongue заканчивается при
потере mass balance. Морены располагаются вдоль боковой и конечной границы
толщи. Glacial lake допускается за terminal moraine или в overdeepened basin.

Высотный переход:

```text
permanent ice
  -> fractured ice / exposed rock
  -> seasonal snow and moraine
  -> alpine meadow
  -> subalpine forest
```

Glacial cave family Q3 получает толщину льда, crevasse и meltwater fields из
этой же системы.

## 9. Coasts and oceans

Coast profile выбирается по:

- coast distance;
- shelf gradient;
- wave exposure;
- sediment supply from hydrology;
- province stone resistance;
- temperature;
- local relief.

Профили C0–C8 из `BIOME_MATRIX.md` являются потребителями этих полей.

Примеры выбора:

- низкий relief + высокий sediment supply -> beach/dune/delta;
- высокий relief + resistant stone -> cliff/stacks;
- glacial valley intersecting coast -> fjord;
- low wave exposure behind barrier -> lagoon;
- warm shallow isolated uplift -> coral bank/atoll eligibility;
- river outlet + tidal coast -> estuary.

Ocean graph обязан различать shelf, continental slope, basin floor,
submarine canyon, bank и isolated seamount. Coral Atoll и Floating Island не
заменяют ocean terrain.

## 10. Cave provinces

### 10.1 Family selection

Шесть весов Q1–Q6 происходят из surface province, depth, water balance и
glacial/volcanic fields:

| Код | Семейство | Основной контекст |
|---|---|---|
| Q1 | limestone karst | karst/sedimentary, влажно |
| Q2 | volcanic tubes | volcanic belt |
| Q3 | glacial caves | glacier mass и meltwater |
| Q4 | deep tectonic caverns | young mountains/deep faults |
| Q5 | wet caves | wetland, river corridor, high water balance |
| Q6 | dry caves | dry plateau/badlands |

В переходе могут смешиваться две семьи. Dominant surface biome не выбирает
семью напрямую.

### 10.2 Cave composition

Каждая family задаёт:

- chamber field;
- connector field;
- vertical shaft/fracture field;
- entrance probability;
- water/lava tendency;
- collapse/debris field;
- material palette;
- depth envelope.

Tectonic cheese/spaghetti/noodle/lava-tunnel functions переиспользуются как
primitives с разными masks и amplitudes.

Conceptual carve:

```text
family_carve =
  primary_chambers
  union connectors
  union shafts
  minus protected_surface
  minus stability_mask
```

Entrance обязан быть связан с cave volume. Декоративная дыра без связанного
пространства не считается entrance.

### 10.3 Existing cave landmarks

Сохраняются IDs:

- `cave_sanctum`;
- `spider_nest`;
- `deep_dark_rift`;
- `rare_flower_grotto`.

После этапа 7 они получают eligibility от cave family и размещаются только
внутри подтверждённого объёма:

| Landmark | Допустимые семьи |
|---|---|
| Cave Sanctum | Q1/Q4/Q5 |
| Spider Nest | Q1/Q4/Q6 |
| Deep Dark Rift | Q4 |
| Rare Flower Grotto | Q1/Q3/Q5 |

## 11. Surfaces

Surface material зависит от:

```text
biome profile
+ province stone/soil
+ slope
+ height belt
+ moisture/water distance
+ exposure
+ erosion/deposition
+ ecotone
```

Порядок правил:

1. bedrock/deep material;
2. cave exposed material;
3. river/lake/ocean bed;
4. glacier/snow line;
5. cliff and scree;
6. province-specific substrate;
7. biome top/filler;
8. ecotone overlay;
9. sparse detail patches.

Приоритет предотвращает появление grass под ледником или песчаной пляжной
полосы на отвесном фьорде.

### 11.1 Поддерживаемость

Vanilla noise settings не умеет ссылаться на отдельные registry surface-rule
фрагменты. Источником истины должны стать небольшие именованные fragments,
которые build task детерминированно собирает в итоговый
`noise_settings/nexus_v2.json`.

Generated JSON коммитится либо сравнивается в CI. Ручное редактирование
сгенерированного блока запрещено. Custom surface condition codecs допустимы
для province, river distance и exposure, если они:

- чисто координатные;
- сериализуемы;
- не читают соседние чанки;
- имеют unit tests для codec round-trip.

## 12. Vegetation communities

Vegetation создаётся community profile, а не независимыми равномерными
одиночными features.

Слои:

1. canopy;
2. subcanopy;
3. understory;
4. shrubs;
5. ground cover;
6. deadwood/fallen logs;
7. clearings and disturbance;
8. wet pockets/rocky gaps.

`CommunitySample` получает biome profile V0–V5, province, altitude belt,
slope, moisture, water distance и deterministic patch field.

Деревья используют configured feature pools/template variants. Placement
modifier выбирает cluster, morphology и возраст, но не строит каждое дерево
полностью случайным voxel-loop.

Целевые варианты для каждого лесного семейства:

- минимум 3 canopy morphology;
- минимум 2 молодых формы;
- минимум 2 fallen-log states;
- отдельная edge/clearing density;
- slope cutoff и altitude response.

Nature's Spirit интегрируется через необязательные block/tag lookups.
Отсутствующий мод заменяется ванильной palette без ошибки registry и без
изменения геометрии сообщества.

## 13. Ecotones

Ecotone — широкая mask, а не новый обязательный biome ID.

Обязательные переходы из `BIOME_MATRIX.md`:

- forest -> meadow;
- taiga -> snowy slopes;
- desert -> badlands;
- swamp -> humid forest;
- plains -> foothills;
- jungle -> karst;
- ocean -> coast -> dunes;
- glacier -> moraine -> alpine meadow;
- mushroom coast -> ocean.

Mask определяется расстоянием до climate/province boundary и локально
искажается detail noise. Типичная ширина `24–160` блоков; для
glacier/moraine допускается `64–320`.

Ecotone управляет одновременно top material, кустами, canopy density,
deadwood и debris. Изменение только цвета травы не считается реализацией.

## Landscape Art Direction and Composition System

Техническая правдоподобность является основанием, но не доказательством
выразительного мира. Composition system управляет тем, как формы читаются в
движении: где возникает доминанта, откуда она раскрывается, что её
поддерживает и где мир оставляет спокойное пространство.

Composition не строит декорацию поверх готового мира. Её intent-сигналы
выбираются на L2 и участвуют в L3 landform graph. Derived-сигналы вычисляются
на L6 из аналитического terrain. Система не использует screenshots, camera
position игрока, client shader state или ray tracing по соседним чанкам.

### 1. Regional mood profiles

Mood представлен десятью непрерывными весами `0..1`:

- `tranquil`;
- `pastoral`;
- `mysterious`;
- `ancient`;
- `monumental`;
- `desolate`;
- `enchanted`;
- `dangerous`;
- `sacred`;
- `melancholic`.

В каждой точке веса нормализуются. Для dominant mood reporting используется
максимум, но generation consumers смешивают до трёх старших весов. Типичный
размер mood region — `2 048–8 192` блоков, ширина перехода — не менее `256`
блоков. Mood не зависит от biome ID: одинаковый forest biome может выражать
tranquil, ancient или mysterious region.

Mood является модификатором допустимых геологических форм, а не их заменой.
Например, `monumental` усиливает hierarchy и высотный контраст существующей
горной провинции, но не создаёт альпийский пик в wetland basin.

`MoodInfluence` выдаёт ограниченные коэффициенты:

| Канал | Допустимое воздействие |
|---|---|
| openness | ширина полян, долин, берегов и canopy gaps |
| relief intensity | множитель regional relief в пределах province envelope |
| forest density | cluster coverage, а не равномерное число деревьев |
| canopy height | выбор morphology/age pool |
| fog eligibility | вероятность сервер-независимого atmosphere profile |
| water frequency | eligibility малых озёр/wet pockets, не направление рек |
| landmark rarity | стоимость и разрешённый hierarchy level |
| deadwood | fallen logs, snags и debris rate |
| colour-compatible palette | пересечение biome и province block palettes |
| visual clutter | ground cover/debris/detail density |
| enclosed/open ratio | доля corridor, clearing и negative space |

Все коэффициенты имеют config bounds. Ни один mood не может:

- изменить flow direction;
- превысить province height envelope;
- отменить protected negative space;
- гарантировать landmark;
- требовать клиентский мод.

Fog eligibility — только сигнал возможности атмосферы. Серверный terrain,
surface и vegetation не зависят от того, отображает ли клиент fog. Sodium и
shaders не участвуют в выборе mood.

Начальные художественные тенденции:

| Mood | Пространственная тенденция |
|---|---|
| tranquil | низкий clutter, спокойная вода, широкое negative space |
| pastoral | пологие террасы, поляны, water access, human-scale sites |
| mysterious | enclosed corridors, высокий framing, редкие раскрытия |
| ancient | старые silhouettes, deadwood, weathered stone, низкая симметрия |
| monumental | сильная доминанта, длинная focal distance, мало конкурентов |
| desolate | открытость, exposed substrate, редкая растительность |
| enchanted | редкие необычные palette accents и minor wonder eligibility |
| dangerous | высокий локальный contrast, enclosed spaces, cave exposure |
| sacred | один защищённый focal site, низкий clutter вокруг него |
| melancholic | широкие тихие пространства, water/shore emphasis, deadwood |

Эти тенденции не являются фиксированными биомными пресетами. Итог всегда
ограничен geology, climate, hydrology и `BIOME_MATRIX.md`.

### 2. Visual hierarchy

Каждый composition region содержит четыре уровня чтения:

1. primary landmark либо dominant natural silhouette;
2. secondary supporting forms;
3. foreground detail and framing;
4. quiet negative space.

Primary необязательно является placed feature. Им может быть mountain crest,
lake basin, fjord wall, volcano, canyon opening, island massif или изгиб
берега. `hierarchy_anchor` задаёт expected center/direction, а
`hierarchy_level` — local, minor, regional либо world.

Landform graph распределяет высотный и detail contrast:

- primary получает самый читаемый silhouette strength;
- secondary forms имеют `35–70%` primary contrast и направляют взгляд;
- foreground detail использует малый spatial scale и не закрывает focal arc;
- negative space получает detail suppression, но сохраняет surface texture.

Горная система выделяет один dominant crest segment на composition cell либо
небольшую согласованную группу в clustered profile. Остальные пики снижаются
по hierarchy envelope и поддерживают главную ось.

Два regional/world anchors не могут иметь перекрывающиеся influence zones,
кроме явно выбранного `clustered_region_profile`. Такой профиль обязан иметь
одну общую композиционную ось; он не разрешает случайную россыпь равнозначных
объектов.

### 3. Vista and reveal system

Vista system повышает вероятность выразительного раскрытия, не обещая
идеальный кадр из каждой координаты.

Поддерживаемые archetypes:

- enclosed forest corridor;
- gradual opening;
- distant focal landmark;
- river-directed vista;
- ridge saddle viewpoint;
- lake basin reveal;
- canyon entrance;
- coast overlook.

`VistaSample` содержит:

```text
openness
probableViewDirection
focalDistanceMin
focalDistanceMax
silhouetteStrength
foregroundFramingScore
revealEligibility
vistaArchetype
```

Алгоритм использует analytical base elevation, broad slope, hydrology,
canopy-density potential и hierarchy anchors:

1. выбирает route/view candidate на valley floor, river corridor, saddle,
   forest edge, basin rim или coast overlook;
2. оценивает высотные профили по ограниченному набору направлений и дистанций
   `128, 256, 512, 1 024, 2 048` блоков;
3. вычисляет долю незакрытого углового сектора без block ray tracing;
4. ищет direction с focal silhouette и поддерживающими боковыми формами;
5. проверяет последовательность enclosed -> transitional -> open;
6. выводит framing и reveal scores.

Это bounded sampling чистых координатных полей. Оно не вызывает
`Level#getChunk`, не читает готовые деревья и не зависит от render distance.

Consumers:

| Сигнал | Потребители |
|---|---|
| openness | canopy density, clearing size, clutter, diagnostics |
| view direction | asymmetric framing, route preference, landmark orientation |
| focal range | hierarchy spacing и silhouette scale |
| silhouette strength | landform contrast и landmark eligibility |
| framing score | vegetation gaps, cliff/island secondary forms |
| reveal eligibility | clearing edge, saddle, canyon/coast transition |

### 4. Landscape rhythm

`rhythm_phase` имеет четыре смешиваемых состояния:

- `quiet`;
- `transitional`;
- `dramatic`;
- `recovery`.

Они образуют региональную последовательность, а не независимый white noise.
Composition intent atlas использует низкочастотное phase field и adjacency
penalty. После dramatic core вероятность recovery повышается в направлении
естественного выхода: downstream valley, mountain foreland, basin, shelf или
coast.

Ограничения для canonical composition supercell `8 192 × 8 192`:

- dramatic terrain занимает не более `35%` площади суши;
- protected quiet/recovery занимает не менее `25%` площади суши;
- непрерывный dramatic corridor не длиннее `4 096` блоков без recovery
  interval шириной минимум `512` блоков;
- regional/world landmark influence занимает не более `20%` площади;
- clustered dramatic profile может превысить локальный лимит, но не лимит
  всего supercell.

Water basin или крупный океанский участок может выполнять роль recovery.
Ритм ограничивает relief intensity, clutter и landmark eligibility
одновременно. Снижение только высоты без снижения визуального шума не
считается recovery.

### 5. Negative space

`negative_space_protection` сохраняет:

- луга;
- широкие берега;
- спокойные речные долины;
- естественные поляны;
- открытые снежные поля;
- водную гладь;
- строительные площадки;
- участки с низкой плотностью декораций.

Целевое покрытие protected negative space — `25–45%` доступной поверхности
composition supercell и не менее `15%` даже внутри dramatic province.
Конкретная доля зависит от mood, geology и water.

Protection уменьшает:

- high-frequency height detail;
- canopy closure;
- boulder/deadwood density;
- minor landmark attempts;
- насыщенность competing palette patches.

Она не обнуляет детали. Разрешены low grass, редкие камни, береговые наносы,
небольшие цветочные группы, следы эрозии и единичное deadwood, если их
contrast ниже primary silhouette.

Water negative space сохраняет читаемую гладь: islands, reeds и rocks
размещаются группами с крупными свободными интервалами, а не равномерной
сеткой.

### 6. Framing

Framing использует вероятное view direction и не создаёт симметричную
«фоторамку».

Поддерживаемые механизмы:

- foreground framing vegetation;
- asymmetric cliff framing;
- valley walls directing view;
- river leading lines;
- shoreline curvature;
- controlled canopy gaps;
- secondary islands or spurs offset from focal axis.

Для каждой стороны focal corridor вычисляется независимый weight и seeded
offset. Левая и правая формы не зеркалятся. Минимальная асимметрия проверяется
по разнице высоты, длины или density двух framing sides.

Canopy gap формируется снижением cluster eligibility вдоль широкого
аналитического сектора. Отдельные деревья всё ещё могут пересекать сектор,
поэтому раскрытие остаётся природным, а не вырубленным коридором.

River leading line следует реальному downstream channel. Shoreline framing
читает curvature coast field. Скалы не поворачиваются к фактической камере
игрока.

### 7. Landmark rarity and wonder budget

Каждый composition cell `4 096 × 4 096` имеет детерминированный
`wonder_budget`. Базовый диапазон — `4–12` wonder units; mood и rhythm могут
перераспределять, но не создавать дополнительные units после неудачной
попытки.

Уровни:

| Уровень | Примеры | Стоимость | Минимальная дистанция до равного уровня |
|---|---|---:|---:|
| local detail | rocks, logs, small springs | 0 | 8–64 |
| minor landmark | grove, small grotto, minor fall | 1 | 256–768 |
| regional landmark | giant tree, major arch, exceptional fall | 4 | 1 536–4 096 |
| world landmark | floating massif, sacred cavern system, monumental formation | 12 | 8 192–20 000 |

Distance выбирается по visual strength и silhouette footprint внутри указанного
диапазона. Сильный объект всегда получает не меньшую дистанцию, чем более
слабый объект того же уровня.

World landmark требует отдельного rare anchor на сетке не мельче
`16 384 × 16 384`, потребляет совокупный budget своей influence zone и
запрещает конкурирующий regional/world landmark рядом. Его отсутствие в
обследуемой области является нормальным результатом.

Budget reservation происходит до feature attempt и является чистой функцией
seed/anchor/type. Неудачная попытка из-за site constraints не отдаёт units
случайному конкуренту в зависимости от порядка чанков. Допускается
детерминированный заранее ранжированный fallback меньшего уровня.

К wonder budget относятся:

- floating islands;
- giant trees;
- monumental arches;
- sacred grottos;
- exceptional waterfalls;
- unusual ice formations.

Обычная геология, реки, лесные сообщества и surface detail не расходуют
budget, но участвуют в visual hierarchy.

### 8. Human-scale places

Большой регион обязан включать защищённые места человеческого масштаба:

- sheltered valley;
- clearing;
- coastal or river terrace;
- natural building bench;
- quiet lake;
- short forest passage;
- settlement-capable area.

`scenic_site_score` объединяет:

```text
buildable_terrace_weight
+ route_access
+ water_access
+ shelter
+ vista/reveal value
- flood_risk
- extreme_slope
- landmark_exclusion
```

Начальные требования к buildable candidate:

- contiguous analytical area не меньше `32 × 32` блоков;
- основной slope не больше `2` блоков на `8` по большинству sample edges;
- отсутствует active channel и high floodplain weight;
- пресная вода находится ориентировочно в `32–192` блоках либо site является
  безопасной coastal terrace;
- есть связь с route suitability corridor шириной минимум `12` блоков;
- canopy/clutter допускают расчистку без уничтожения primary silhouette.

Route network здесь означает аналитическую сеть проходимости: valley floors,
saddles, river terraces, plateau passes и coast benches. Она не размещает
дороги автоматически.

На каждые `2 048 × 2 048` блоков пригодной суши требуется минимум один
human-scale candidate либо явная диагностическая причина отсутствия:
ocean, extreme protected massif, canyon core или glacier accumulation zone.

### 9. Composition diagnostics

Обязательные exports:

- `openness.png`;
- `dominant-silhouette.png`;
- `focal-point-candidates.png`;
- `vista-direction.png`;
- `negative-space-coverage.png`;
- `landmark-hierarchy.png`;
- `wonder-budget-usage.png`;
- `regional-rhythm.png`;
- `buildable-scenic-sites.png`;
- табличный `composition-metrics.json`.

Метрики:

- доля quiet/transitional/dramatic/recovery;
- максимальная длина continuous dramatic corridor;
- negative-space coverage;
- число primary anchors без supporting/quiet space;
- число конфликтующих regional/world influence zones;
- wonder units available/reserved/placed/rejected;
- vista candidates по archetype и focal range;
- buildable scenic sites на `1 000` chunks;
- доля sites с route и water access;
- распределение openness и framing score.

Для каждого seed visual review проверяет минимум:

1. один дальний горный вид;
2. одну спокойную долину;
3. одно раскрытие из лесного corridor;
4. одно выразительное озеро либо побережье;
5. один регион без крупных landmarks;
6. один world landmark, только если такой anchor присутствует в области
   обследования.

Review сохраняет seed, координаты, направление взгляда, shader state и
идентификаторы dominant province/mood/rhythm. Обязательная доказательная
серия снимается без shaders; shader-серия хранится отдельно.

### 10. Anti-slop rules

Запрещено считать художественную часть выполненной только из-за:

- увеличенной высоты гор;
- большого количества водопадов;
- чрезмерно плотного леса;
- множества арок;
- постоянного тумана;
- насыщенных материалов;
- высокой плотности мелких деталей;
- одного специально выбранного удачного seed.

Красота подтверждается одновременно:

- regional rhythm;
- visual hierarchy;
- читаемыми silhouettes;
- measurable negative space;
- разнообразием composition profiles;
- wonder-budget discipline;
- human-scale places;
- повторяемостью требований на фиксированных 20 seed.

Отдельный удачный screenshot является примером, но не acceptance evidence.
Провал rhythm, hierarchy или negative-space metrics нельзя закрыть shader
изображением.

## 14. Landmarks and procedural form quality

### 14.1 Eligibility

До voxel placement feature получает chunk-local `SiteAnalysis`:

- province and boundary weights;
- biome/profile;
- cached 18×18 local height/slope buffer;
- river/coast/glacier distance;
- cave family/volume, если объект подземный;
- overlap reservation;
- buildable/support score.

Buffer включает только текущий chunk и допустимую одноблочную аналитическую
границу. Данные за пределами chunk берутся из чистых atlas functions, не из
heightmap соседнего chunk.

### 14.2 Shape contract

Крупный объект обязан иметь:

1. primary asymmetric mass;
2. secondary masses;
3. warped boundary;
4. erosion/breakup;
5. terrain transition;
6. debris;
7. province material palette;
8. минимум несколько seeded templates/profiles.

Perfect sphere, circle, cylinder или одинаковый radial loop может быть только
внутренним primitive с заметным warp и decomposition, но не финальным
силуэтом.

### 14.3 Existing feature migration

Все 13 IDs сохраняются. В первую очередь:

- удалить far-chunk heightmap reads из Floating Island;
- заменить `NoneFeatureConfiguration` на data-driven configuration постепенно;
- добавить attempts/success/rejection/time/block-write counters;
- добавить bounding-box и overlap reservation;
- перенести региональные массы кальдер, островов и карста в regional fields;
- оставить Java feature для detail/debris/completion.

## 15. Runtime components and registries

Целевые Java-компоненты:

```text
worldgen/v2/
├─ field/
│  ├─ NexusFieldContext
│  ├─ ProvinceSampler
│  ├─ LandformSampler
│  ├─ HydrologyAtlas
│  ├─ GlacierSampler
│  ├─ CaveProvinceSampler
│  ├─ CompositionIntentAtlas
│  ├─ CompositionSampler
│  └─ WonderBudget
├─ cache/
│  ├─ AtlasTileKey
│  └─ BoundedTileCache
├─ chunk/
│  ├─ NexusV2ChunkGenerator
│  └─ RiverWaterPass
├─ surface/
│  ├─ ProvinceCondition
│  ├─ RiverDistanceCondition
│  └─ ExposureCondition
├─ placement/
│  ├─ ProvinceFilter
│  ├─ CommunityPlacement
│  └─ LandmarkSiteFilter
└─ diagnostics/
   ├─ FieldExporter
   ├─ CompositionExporter
   ├─ ChunkTimingRecorder
   └─ MultiSeedSuite
```

Имена являются целевым layout, не обязательством создать пустые классы.
Класс добавляется только вместе с тестируемой ответственностью.

Новые codecs/registries регистрируются через NeoForge `DeferredRegister`.
Client classes не импортируются серверными worldgen packages.

## 16. Performance strategy

### 16.1 Запрещённые операции

- `Level#getChunk` из feature или atlas;
- height lookup за пределами текущего writable region;
- unbounded cache;
- global mutable random;
- миллионы одиночных writes для regional landform;
- повторный полный atlas calculation для каждой точки density sampling;
- синхронное ожидание другой worldgen-задачи.

### 16.2 Обязательные меры

- immutable tile result;
- bounded cache с настраиваемым memory ceiling;
- coarse calculation + interpolation для медленных regional fields;
- thread-safe compute без lock nesting;
- precomputed local buffers;
- early rejection до shape generation;
- bounded feature volume;
- batched/current-chunk writes;
- JFR либо monotonic timing around chunk stages;
- counters отдельно для attempts, rejected, placed и blocks written.

### 16.3 Начальные budgets

Budgets являются gates для разработки, а не обещанными измерениями:

| Компонент | Начальный budget |
|---|---:|
| province sample после cache | <= 2 µs |
| hydrology tile cold build | <= 50 ms |
| hydrology tile memory | <= 2 MiB |
| один landmark | <= 25 000 block checks |
| один landmark | <= 8 000 writes |
| far-chunk reads | 0 |
| cascading warnings | 0 |

Chunk P95/P99 baseline измеряется до этапа 4 на той же JVM, render distance,
seed set и storage. Финальный допустимый regression определяется после
baseline; выдумывать число без замера запрещено.

## 17. Diagnostics and test gates

### 17.1 Test pyramid

1. unit tests: math, codec, determinism, cache boundaries;
2. graph validation: resource parsing, reachability, no missing registry IDs;
3. golden fields: фиксированные coordinate samples;
4. seam tests: одинаковые значения на tile/chunk boundaries;
5. headless server smoke test;
6. 20-seed survey;
7. visual review без shaders;
8. отдельная client review с Sodium/shaders.

### 17.2 20-seed suite

Фиксированный manifest содержит 20 seed. Для каждого:

- spawn;
- точки примерно 2 000, 5 000 и 10 000 блоков минимум в четырёх
  направлениях;
- target-biome/province samples;
- cave slices на нескольких Y.

Экспорт:

- height;
- biome;
- continentalness;
- erosion;
- temperature;
- humidity;
- river order/distance;
- province dominant/weights;
- slope;
- glacier;
- cave density/family;
- landmark distribution;
- openness;
- dominant silhouette;
- focal-point candidates;
- vista direction;
- negative-space coverage;
- landmark hierarchy;
- wonder-budget usage;
- dramatic/quiet rhythm;
- buildable scenic sites.

Метрики:

- coverage 53 biome;
- region size and biome specks;
- ridge continuity;
- valley width;
- river continuity/confluences/uphill violations;
- coast profile distribution;
- traversable surface;
- cave family coverage;
- landmark attempts/success/rejections per 1 000 chunks;
- conflicting landmark influence zones;
- wonder units reserved/placed/rejected;
- quiet/dramatic/recovery coverage;
- negative-space coverage;
- vista archetype coverage;
- buildable scenic sites with route/water access;
- chunk mean/P95/P99;
- far-chunk access and cascading warnings.

### 17.3 Gate per implementation stage

| Этап | Обязательный gate |
|---|---|
| 4 macro/provinces | deterministic maps, seams=0, >=6 provinces observed, mood/rhythm intents exported |
| 5 terrain systems | 4 mountain families, connected rivers, glacier/canyon samples |
| 6 surfaces/vegetation | 53 matrix rows have explicit profile evidence |
| 7 caves | Q1–Q6 observed and correlated with province |
| 8 landmarks | all 13 counted, hierarchy/budget/overlap/far-read checks pass |
| 9 diagnostics | full 20-seed report, composition exports and measured P95/P99 |
| 10 final | acceptance table, composition review plus shader/no-shader screenshots |

## 18. Compatibility and migration

### 18.1 Registry compatibility

Существующие IDs `nexus_landscape:nexus` и 13 feature IDs не удаляются.
Vanilla biome IDs остаются неизменными. Optional integrations не входят в
required dependency list.

### 18.2 World compatibility

Изменение density graph под тем же noise-settings ID создаёт швы между
старыми и новыми чанками. Поэтому:

1. текущий `nexus_landscape:nexus` замораживается как legacy settings;
2. V2 создаётся как `nexus_landscape:nexus_v2`;
3. новый preset `nexus_landscape:nexus_v2` использует V2 settings;
4. существующий preset и старые миры продолжают разрешать legacy ID;
5. автоматическое преобразование существующего мира не выполняется;
6. перенос мира допускается только копированием и с явным предупреждением о
   границах старых/новых chunks.

После полного acceptance можно изменить UI-рекомендацию для новых миров, но
не удалять legacy resources в той же major-линейке.

`NexusV2ChunkGenerator` должен быть зарегистрирован как делегирующая оболочка
до создания первого пользовательского V2-мира. На этапе 4 он повторяет
стандартные noise-generation stages, а на этапе 5 получает RiverWaterPass.
Нельзя сначала выпускать preset с `minecraft:noise`, а затем менять generator
type под тем же V2 preset: уже созданные `level.dat` сохранили бы старый
generator codec. V2 preset не становится рекомендуемым до codec round-trip и
dedicated-server smoke test этой оболочки.

## 19. Implementation sequence

### Этап 4 — macro terrain and provinces

1. зарегистрировать делегирующий V2 chunk-generator codec;
2. зарегистрировать V2 settings/preset параллельно legacy;
3. реализовать field context, deterministic salts и province sampler;
4. реализовать composition intent atlas: mood, rhythm, hierarchy anchors и
   wonder budget без placed landmarks;
5. создать continent/ocean aggregate graph;
6. реализовать минимум 6 наблюдаемых province masks;
7. подключить province uplift/erosion и rhythm constraints без hydrology;
8. экспортировать province/mood/rhythm/hierarchy maps и выполнить
   codec/seam/determinism/server tests.

### Этап 5 — mountains, rivers, glaciers and canyons

1. четыре landform families;
2. analytical base elevation;
3. hydrology atlas и river terrain carve;
4. chunk-local elevated water pass;
5. glacial mass balance fields;
6. plateau/canyon incision;
7. coast/ocean profiles;
8. derived openness, silhouette, vista, negative-space и scenic-site fields.

### Этап 6 — surfaces and vegetation

1. generated surface-rule fragments;
2. custom coordinate-only conditions;
3. 53 soil/stone/surface profiles;
4. vegetation community pools;
5. altitude belts and nine ecotones;
6. composition-driven clearings, clutter, canopy gaps и framing;
7. optional Nature's Spirit palettes.

### Этап 7 — caves

1. Q1–Q6 weights;
2. family density composition;
3. entrances/materials/water;
4. cave map and province-correlation tests.

### Этап 8 — landmarks

1. instrumentation всех 13 features;
2. SiteAnalysis and overlap reservation;
3. hierarchy anchors and deterministic wonder reservation;
4. remove far-chunk reads;
5. data-driven configurations;
6. family-aware placement;
7. shape decomposition and variants.

### Этапы 9–10

Расширить WorldgenSurvey до multi-seed suite, измерить performance, провести
визуальную проверку и закрывать acceptance только доказательствами.

## 20. Decisions and open risks

Принятые решения:

- vanilla biome source сохраняется на первом V2-релизе;
- Tectonic primitives переиспользуются, но скрываются за Nexus aggregate
  nodes;
- связная гидрология требует atlas, потому что pointwise noise не хранит
  flow graph;
- elevated water требует chunk-local generator pass;
- surfaces собираются из fragments в generated JSON;
- legacy и V2 получают разные settings IDs;
- художественная композиция задаётся серверными analytical fields и
  измеряется независимо от shaders.

Оставшиеся риски:

1. custom chunk generator codec и water pass требуют проверки совместимости с
   NeoForge, vanilla structures и datafix/loading;
2. atlas cache может увеличить память или contention без benchmark;
3. vanilla biome source может не выполнить размеры регионов и ecotones;
4. surface-rule generated pipeline усложняет сборку datapack;
5. aquifer и elevated river water могут конфликтовать на cave entrances;
6. швы старых миров нельзя убрать без регенерации;
7. художественные targets требуют итераций после реальных карт и screenshots;
8. текущие Java landmarks остаются техническим долгом до этапа 8;
9. analytical vista является вероятностной оценкой и может расходиться с
   финальной видимостью после structures и vegetation;
10. жёсткие composition thresholds могут сделать регионы однообразными, если
    не проверить распределение на 20 seed.

## 21. Acceptance статуса архитектуры

Этап 3 считается завершённым, когда:

- описан полный pipeline;
- у каждого сигнала есть owner и consumers;
- отсутствуют циклические зависимости;
- определены province, hydrology, glacial, coast, cave, surface и vegetation
  contracts;
- определены mood, hierarchy, vista, rhythm, negative-space, framing,
  wonder-budget и human-scale contracts;
- определены performance rules и test gates;
- определён versioning/migration path;
- документ не выдаёт целевую систему за уже реализованную.

Завершение этапа 3 не закрывает критерии готовности генератора. Реализация
начинается с этапа 4 после фиксации этого контракта.
