# Nexus Landscape — аудит перед этапом 6

Дата аудита: 2026-07-31
Minecraft: `1.21.1`
NeoForge: `21.1.235`
Проверяемый preset: `nexus_landscape:nexus_v2`
Статус: аудит исходников и generated resources; это не отчёт о завершении этапа 6.

## 0. Runtime status после начала реализации

| Категория | Статус | Подтверждение |
|---|---|---|
| Profile catalog 53/53 | IMPLEMENTED | `SurfaceProfileCatalog`, self-test |
| Immutable context/resolver | IMPLEMENTED | `SurfaceContext`, `SurfaceProfileResolver` |
| Physical surface blocks | IMPLEMENTED | `SurfaceProvincePass` вызывается из `buildSurface` |
| River/lake final authority | IMPLEMENTED | `RiverWaterPass` вызывается после surface pass |
| Smooth coordinate blending | IMPLEMENTED | `SurfaceNoise`, smooth influence masks |
| Unknown/modded biome fallback | PARTIALLY IMPLEMENTED | climate-aware fallback; runtime Nature’s Spirit instance ещё не проверен |
| Vegetation profiles | DATA ONLY | целевые записи есть в Matrix C, Java grammar отсутствует |
| Cave biome material pass | NOT CONNECTED TO RUNTIME | cave profiles есть в catalog, surface pass не обрабатывает underground volumes |
| Runtime survey | PARTIALLY IMPLEMENTED | fresh seed 240802 reached server Done; report hook не завершился |
| F3 screenshots | BLOCKED BY RUNTIME UI | dedicated-server smoke не создаёт клиентские screenshots |

Ни одна строка выше не означает завершение Stage 6: vegetation и cave runtime
integration остаются обязательными.

## 1. Проверенная цепочка генерации

`nexus_v2` использует:

1. `NexusV2ChunkGenerator`;
2. vanilla `minecraft:multi_noise` с preset `minecraft:overworld`;
3. noise settings `nexus_landscape:nexus_v2`;
4. Tectonic-derived router как основу, поверх которой V2 заменяет только
   `continents`, `erosion`, `initial_density_without_jaggedness` и
   `final_density`;
5. встроенные vanilla biome generation settings для растительности;
6. общий surface rule из `noise_settings/nexus_v2.json`;
7. `RiverWaterPass` после `super.buildSurface`;
8. тринадцать Nexus placed features через NeoForge biome modifiers.

Следовательно, климат участвует в выборе vanilla biome и в V2 regional/density
fields, но пока не выбирает отдельный surface/vegetation profile. Общая
поверхность в основном различает biome ID, а Nexus features в основном
используют biome tag, heightmap и локальные блоки.

## 2. Состояние общих полей

| Поле | Фактическое использование до этапа 6 | Пробел |
|---|---|---|
| temperature/humidity | vanilla multi-noise biome source, regional field и analytical terrain | не выбирают surface и vegetation grammar |
| continentalness/erosion/weirdness | density graph и выбор биома | не представлены единым surface context |
| elevation | density graph, heightmaps, часть feature predicates | нет общей высотной зональности поверхности и деревьев |
| slope | аналитически вычисляется в `RiverWaterPass` | обычная поверхность и vanilla vegetation slope не учитывают |
| river mask/distance/order | физическое русло, sediment и floodplain в `RiverWaterPass` | отдельный `river_bank` не читает canonical river profile |
| lake basin/overflow | физический lake/overflow pass | нет общей lake-shore vegetation grammar |
| coast weight | отсутствует как reusable runtime field | берег определяется biome ID и vanilla surface condition |
| province weights | density/diagnostics | обычные surface rules и большинство features их не используют |

## 3. Текущие surface-профили

Общие правила включают bedrock, deepslate, underwater gravel, stone, dirt и
grass fallback. Они умеют vanilla-подобные проверки floor/ceiling, water depth,
stone depth и steepness, но не являются системой surface provinces.

Сокращения:

- `fallback` — grass/dirt/stone с общими underwater правилами;
- `river pass` — последующая физическая коррекция русла, берега, sediment,
  озера или overflow;
- `vanilla vegetation` — generation settings встроенного vanilla biome;
- `RL` — `regional_landmark`, rarity 1/24 chunk attempt.

## 4. Аудит всех 53 Overworld-биомов

| Biome ID | Текущая surface rule | Текущая vegetation/features Nexus | Связь с climate / hydrology / elevation-slope | Текущий вывод |
|---|---|---|---|---|
| `minecraft:plains` | fallback | vanilla vegetation; hot spring; RL | climate только выбор biome; river pass; feature heightmap | ванильный/неотличимый |
| `minecraft:sunflower_plains` | fallback | vanilla; hot spring; RL | то же | отличие почти только vanilla flowers |
| `minecraft:snowy_plains` | fallback | vanilla; ice boulder через RL | climate biome; river pass; без slope profile | ванильный |
| `minecraft:ice_spikes` | snow top через cold branch и vanilla biome features | vanilla; packed-ice RL | climate biome; elevation косвенно | identity зависит от vanilla feature |
| `minecraft:desert` | sand/sandstone | vanilla; hoodoo RL | climate biome; river pass без desert grammar | частично отличим |
| `minecraft:swamp` | локальная water surface, затем fallback | vanilla; mossy boulder RL | climate biome; hydrology не управляет болотной сетью | ванильный/feature overlap |
| `minecraft:mangrove_swamp` | mud/water | vanilla; mossy boulder RL | climate biome; нет coast/tidal field | частично отличим |
| `minecraft:forest` | fallback | vanilla; spruce fallen log RL | climate biome; river pass; heightmap | ванильный |
| `minecraft:flower_forest` | fallback | vanilla; spruce fallen log RL | то же | отличие почти только vanilla flowers |
| `minecraft:birch_forest` | fallback | vanilla; spruce fallen log RL | то же | vanilla identity |
| `minecraft:dark_forest` | fallback | vanilla; spruce fallen log RL | то же | vanilla identity |
| `minecraft:old_growth_birch_forest` | fallback | vanilla; spruce fallen log RL | то же | vanilla identity |
| `minecraft:old_growth_pine_taiga` | podzol/coarse dirt | vanilla; spruce fallen log RL; hot spring | climate biome; heightmap; без slope belts | частично отличим |
| `minecraft:old_growth_spruce_taiga` | podzol/coarse dirt | vanilla; spruce fallen log RL; hot spring | то же | частично отличим |
| `minecraft:taiga` | fallback | vanilla; spruce fallen log RL; hot spring | climate biome; river pass | ванильный |
| `minecraft:snowy_taiga` | fallback | vanilla; packed-ice RL | climate biome; river pass | ванильный |
| `minecraft:savanna` | fallback | vanilla; meadow outcrop RL | climate biome; river pass | ванильный |
| `minecraft:savanna_plateau` | fallback | vanilla; meadow outcrop RL | то же | ванильный |
| `minecraft:windswept_hills` | exposed stone branch | vanilla; mountain arch; andesite boulder RL; hot spring | elevation через biome/height; нет общей slope palette | частично отличим, overlaps |
| `minecraft:windswept_gravelly_hills` | gravel/stone/coarse dirt/grass | vanilla; mountain arch; andesite boulder RL | то же | отличим surface, overlaps |
| `minecraft:windswept_forest` | fallback | vanilla; mountain arch; forest RL; hot spring | climate biome; feature heightmap | перегружен пересекающимися features |
| `minecraft:windswept_savanna` | stone/coarse dirt | vanilla; caldera; mountain-like RL | climate biome; нет volcanic province predicate | высокий риск ложных вулканов |
| `minecraft:jungle` | fallback | vanilla; humid karst arch; forest RL | humid tag, но не macro humidity/karst weight | перегружен canopy + arches |
| `minecraft:sparse_jungle` | fallback | vanilla; humid karst arch; forest RL | то же | surface неотличим от fallback |
| `minecraft:bamboo_jungle` | fallback | vanilla; humid karst arch; forest RL | то же | identity в основном vanilla bamboo |
| `minecraft:badlands` | terracotta/red sand/red sandstone | vanilla; caldera; hoodoo RL | climate biome; нет canyon/volcanic gating | surface отличим, feature conflict |
| `minecraft:eroded_badlands` | terracotta/red sand/red sandstone | vanilla; caldera; hoodoo RL | то же | перегружен hoodoo/caldera риском |
| `minecraft:wooded_badlands` | badlands palette + coarse dirt/dirt/grass | vanilla; caldera; hoodoo RL; hot spring | то же | много независимых random features |
| `minecraft:meadow` | fallback | vanilla; hot spring; RL | elevation через biome selection; без slope belts | vanilla identity |
| `minecraft:cherry_grove` | fallback | vanilla; hot spring; forest RL | elevation через biome; без zonation | vanilla identity |
| `minecraft:grove` | dirt/snow/powder snow | vanilla; mountain arch; andesite RL | elevation/cold biome; без continuous treeline | частично отличим, overlaps |
| `minecraft:snowy_slopes` | snow/stone/powder snow | vanilla; mountain arch; andesite RL | elevation/steepness частично vanilla rules | отличим, но не province-aware |
| `minecraft:frozen_peaks` | packed ice/ice/snow/stone | vanilla; mountain arch; andesite RL | elevation через biome; glacier field не выбирает palette | отличим, overlaps |
| `minecraft:jagged_peaks` | stone/snow | vanilla; mountain arch; andesite RL | elevation через biome; slope ограниченно | отличим, overlaps |
| `minecraft:stony_peaks` | stone/calcite | vanilla; mountain arch; andesite RL | elevation через biome; нет karst/volcanic variants | отличим, overlaps |
| `minecraft:river` | общий fallback до river pass | vanilla; river bank 1/8; RL; physical river pass | strongest hydrology link; order/slope/floodplain в pass | физически развит, decoration дублирует sediment |
| `minecraft:frozen_river` | общий cold/fallback до river pass | vanilla; river bank 1/8; RL; physical river pass | hydrology есть; freeze grammar не едина | физически развит, decoration conflict |
| `minecraft:beach` | sand/sandstone | vanilla; oak driftwood RL | biome coast only; нет coast weight/slope | ванильный берег |
| `minecraft:snowy_beach` | sand/sandstone в общей beach branch | vanilla; oak driftwood RL | climate biome; нет ice/coast profile | ошибочно похож на тёплый beach |
| `minecraft:stony_shore` | stone/gravel | vanilla; oak driftwood RL | biome coast only; нет wave/slope field | частично отличим |
| `minecraft:warm_ocean` | sand/sandstone seabed | vanilla aquatic; coral atoll; floating island; RL | climate biome; нет coast weight | feature overlap; редкие крупные объекты |
| `minecraft:lukewarm_ocean` | sand/sandstone seabed | vanilla aquatic; coral atoll; floating island; RL | то же | feature overlap |
| `minecraft:deep_lukewarm_ocean` | sand/sandstone seabed | vanilla aquatic; coral atoll; floating island; RL | depth biome; нет shelf field | atoll eligibility спорна |
| `minecraft:ocean` | общий underwater gravel/stone fallback | vanilla aquatic; floating island; RL | climate biome; нет shelf/coast grammar | vanilla seabed |
| `minecraft:deep_ocean` | общий underwater fallback | vanilla aquatic; floating island; RL | depth biome; no deep profile | vanilla seabed |
| `minecraft:cold_ocean` | общий underwater fallback | vanilla aquatic; floating island; RL | climate biome | vanilla seabed |
| `minecraft:deep_cold_ocean` | общий underwater fallback | vanilla aquatic; floating island; RL | climate/depth biome | vanilla seabed |
| `minecraft:frozen_ocean` | water/ice/air cold surface + underwater fallback | vanilla aquatic; floating island; RL | climate biome | частично отличим |
| `minecraft:deep_frozen_ocean` | water/ice/air cold surface + underwater fallback | vanilla aquatic; floating island; RL | climate/depth biome | частично отличим |
| `minecraft:mushroom_fields` | mycelium | vanilla; mycelial grove 1/5; RL | biome tag; no mycelial regional weight | высокий риск перегрузки grove |
| `minecraft:dripstone_caves` | stone branch | vanilla cave vegetation; sanctum; spider nest; flower grotto; RL | 3D biome; height ranges; no cave surface context | перегружен тремя cave overlays |
| `minecraft:lush_caves` | общий underground fallback | vanilla cave vegetation; sanctum; flower grotto; RL | 3D biome; height ranges; no cave hydrology grammar | перегружен overlays |
| `minecraft:deep_dark` | общий underground fallback | vanilla sculk; sanctum; spider nest; rift; RL | 3D biome; height ranges; no depth/slope profile | наиболее перегруженный cave biome |

Статическая достижимость: biome source — стандартный Overworld multi-noise, поэтому
все 53 holder остаются в source. Runtime-достижимость и фактическая площадь
каждого биома в трёх seed на начало этапа 6 **не подтверждены**. Нельзя считать
запись в source доказательством достаточной площади или визуальной доступности.

## 5. Nexus placed features и конфликты

| Feature | Rarity | Scope | Основной риск |
|---|---:|---|---|
| cave sanctum | 1/30 | lush, dripstone, deep dark | пересечение с grotto/nest/rift |
| coral atoll | 1/320 | warm/lukewarm/deep lukewarm ocean | крупный footprint пересекает chunks |
| deep dark rift | 1/34 | deep dark | overlap с другими cave features |
| floating island | 1/900 | все oceans | не связан с archipelago/province weight |
| hot spring | 1/120 | selected temperate/highland biomes | biome tag вместо groundwater/volcanic field |
| humid karst arch | 1/140 | jungle family | нет проверки macro karst weight |
| mountain arch | 1/180 | mountain/windswept family | overlap с landmark и caldera |
| mycelial grove | 1/5 | mushroom fields | высокая частота и крупный footprint |
| rare flower grotto | 1/42 | lush/dripstone caves | overlap с sanctum |
| regional landmark | 1/24 | весь Overworld | самый широкий источник decorative noise |
| river bank | 1/8 | river/frozen river | не согласован с canonical centerline/order |
| spider nest | 1/48 | dripstone/deep dark | overlap с cave features |
| volcanic caldera | 1/96 | badlands/windswept savanna/peaks | biome tag шире реального volcanic belt |

`regional_landmark` применяется ко всему Overworld на шаге
`local_modifications`. В одних и тех же биомах на том же шаге работают hot
springs, arches, calderas, atolls, floating islands и river banks. Отсутствует
единый deterministic exclusion/priority field. Это создаёт визуальные и
геометрические конфликты даже при невысокой индивидуальной rarity.

## 6. Nature’s Spirit

В исходниках, resources, metadata и optional registry resolution интеграция с
Nature’s Spirit не найдена. Обязательной зависимости также нет. Текущий fallback
полностью vanilla, но механизм безопасного optional resolution ещё не
реализован и не протестирован.

## 7. Производительность и детерминизм

Подтверждённые проблемы:

1. `NexusV2HydrologySampler` содержит две неограниченные
   `ConcurrentHashMap`: terrain Y по координате и active channel по canonical
   node ID. Они seed-local через `RandomState`, но не bounded и не имеют явного
   lifecycle cleanup.
2. Некоторые large features многократно вызывают heightmap и `setBlock` на
   footprint, выходящем за 16×16. Placement происходит в worldgen region, но
   стоимость и chunk-boundary поведение отдельно не измерены.
3. `regional_landmark` и другие features повторяют heightmap/biome queries и не
   используют общий sampled context.
4. Нет decoration timers mean/p95/max, placement-failure counters по профилям и
   surface palette telemetry.
5. Глобальные feature counters в `WorldgenSurvey` требуют run/seed scoped
   snapshot/reset перед Stage 6 survey.

Положительные свойства:

- общий mutable `Random` не найден;
- `Future.get` в worldgen features не найден;
- `ServerLevel`/`ChunkAccess` в найденных caches не удерживаются;
- физическая hydrology заявляет analytical neighbour sampling, а не загрузку
  соседнего chunk;
- Stage 5 имеет отдельные seam/order/thread regression tests.

## 8. Классификация исходного состояния

### Почти не отличаются друг от друга

`plains`, `sunflower_plains`, `forest`, `flower_forest`, `birch_forest`,
`dark_forest`, `taiga`, `savanna`, `savanna_plateau`, `meadow`,
`cherry_grove`, а также большая часть обычных/deep ocean seabeds.

### Всё ещё в основном vanilla

Все биомы используют vanilla vegetation settings. Особенно это заметно для
лесов, taiga, savanna, meadow/cherry, wetlands, beaches и oceans. Отдельные
Nexus landmarks не образуют vegetation grammar.

### Риск перегрузки

`deep_dark`, `dripstone_caves`, `lush_caves`, mountain/windswept family,
badlands variants, jungle family, oceans с atoll + floating island и
`mushroom_fields`.

### Недостижимые или крайне редкие

Статически недостижимых biome holders не обнаружено. Фактически редкие или
недостаточно крупные биомы нельзя честно назвать до трёх runtime surveys.
Кандидаты на отдельную проверку: `mushroom_fields`, `deep_dark`,
`deep_lukewarm_ocean`, peak variants, `ice_spikes` и `bamboo_jungle`.

## 9. Решение аудита

Этап 6 пока не реализован. Перед biome-family работой необходимы:

1. актуальная матрица 53 профилей с implementation/test status;
2. единый immutable `SurfaceContext` и registry профилей;
3. единая vegetation grammar, использующая тот же context;
4. bounded seed/dimension-aware sampling cache либо доказанное отсутствие
   выгоды от cache;
5. feature arbitration для overlap;
6. machine-readable survey и runtime biome audit;
7. сохранение Stage 5 regression contract.
