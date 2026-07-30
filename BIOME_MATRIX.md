# Nexus Landscape — матрица 53 Overworld-биомов

Статус документа: целевой контракт этапа 6, не описание уже реализованного мира.
Minecraft: `1.21.1`  
Основание: `STAGE_6_AUDIT.md` и проверенные resources `nexus_v2`.

## 1. Назначение

Матрица задаёт отличимый целевой профиль каждого из 53 ванильных
Overworld-биомов. Строка считается реализованной только тогда, когда
подтверждены одновременно:

1. macro terrain и micro terrain;
2. surface/stone signature;
3. vegetation signature;
4. hydrology/coast signature;
5. ecotones;
6. связанная cave family;
7. диапазон размеров региона;
8. визуальный и многосидовый тест.

Наличие биома в `minecraft:overworld` multi-noise source не означает
выполнение строки.

Требуемые поля распределены между таблицами без неявных `vanilla`-строк:

- Matrix A: terrain family, surface/subsurface palette, exposed rock и erosion;
- Matrix B: river/coast affinity, water/groundwater response и cave relation;
- Matrix C: vegetation structure, dominant tree shape, undergrowth, landmark,
  transition и визуальные признаки;
- Matrix D: macro climate, preferred elevation/slope, explicit affinities,
  optional integration, implementation и test status.

Порядок будущей генерации:

```text
continent
  -> geological province
  -> regional landform and hydrology
  -> climate
  -> vanilla biome selection
  -> biome surface/vegetation signature
  -> ecotone
  -> rare landmark
```

Geological province должен выбираться раньше биома. Биом изменяет выражение
провинции, но не создаёт геологию локальным объектом.

## 2. Общие профили

### 2.1 Macro terrain families

| Код | Семейство | Основная форма |
|---|---|---|
| M1 | Осадочные низменности | Равнины, широкие долины, террасы, озёрные котловины |
| M2 | Влажные низины и карст | Болота, затопленные леса, известняковые холмы и башни |
| M3 | Старые эродированные возвышенности | Округлые хребты, седловины, лесные речные долины |
| M4 | Молодые альпийские и ледниковые горы | Длинные хребты, цирки, U-долины, морены |
| M5 | Сухие плато и каньоны | Столовые уровни, уступы, badlands, арройо |
| M6 | Вулканический пояс | Щитовые массивы, стратовулканы, кальдеры, лавовые долины |
| M7 | Океанический шельф и бассейн | Шельф, склон, абиссаль, банки, архипелаги |
| M8 | Мицелиальная океаническая провинция | Крупные древние острова, эродированные чаши, грибные плато |
| XR | Сквозной речной коридор | Наследует провинцию водосбора; меняет долину и наносы |
| XC | Сквозной береговой коридор | Наследует сушу и морской бассейн; формирует берег |
| XU | Подземная провинция | Наследует surface province и добавляет 3D cave field |

### 2.2 Soil profiles

| Код | Материалы |
|---|---|
| P1 | Плодородный суглинок: grass block, dirt, локальная глина |
| P2 | Кислый лесной грунт: podzol, coarse dirt, moss |
| P3 | Торфяно-болотный: mud, clay, rooted dirt, тёмный ил |
| P4 | Эоловый/пляжный: sand, sandstone, gravel |
| P5 | Мёрзлый: snow, powder snow, frozen dirt, gravel |
| P6 | Тонкий горный: grass, coarse dirt, exposed stone, scree |
| P7 | Сухой осадочный: red sand, terracotta, sandstone |
| P8 | Мицелиальный: mycelium, podzol, moss, coarse dirt |
| P9 | Подводный: sand, gravel, clay, mud |

### 2.3 Stone profiles

| Код | Материалы и слоистость |
|---|---|
| S1 | Аллювиальный фундамент: stone, tuff, gravel, clay lenses |
| S2 | Старый силикатный массив: stone, andesite, diorite, granite |
| S3 | Альпийский кристаллический: stone, andesite, calcite veins |
| S4 | Карбонатный карст: calcite, dripstone, tuff, stone |
| S5 | Аридный осадочный: sandstone, red sandstone, terracotta bands |
| S6 | Вулканический: basalt, smooth basalt, blackstone, tuff |
| S7 | Морской: stone, gravel, clay, sandstone/calcarenite |
| S8 | Мицелиальный древний: stone, tuff, calcite, weathered andesite |

### 2.4 Erosion profiles

| Код | Правило |
|---|---|
| E0 | Аккумуляция: уклон мал, наносы заполняют впадины |
| E1 | Мягкая эрозия: широкие округлые холмы и неглубокие овраги |
| E2 | Речное расчленение: долины, боковые отроги, террасы |
| E3 | Горная эрозия: осыпи, кулуары, оголённые гребни |
| E4 | Каньонная/аридная: отвесные уступы, mesas, talus aprons |
| E5 | Морская/ледовая: клифы, абразионные полки, фьорды, морены |

### 2.5 Coast profiles

| Код | Берег |
|---|---|
| C0 | Нет собственного берега; наследует соседний |
| C1 | Песчаный пляж и низкие дюны |
| C2 | Галечный/гравийный берег |
| C3 | Скальный клиф и абразионная полка |
| C4 | Марш, эстуарий, илистая дельта |
| C5 | Мангровая лагуна и tidal channels |
| C6 | Снежный берег, pack ice и ледовая полка |
| C7 | Коралловая банка, риф, лагуна и карбонатный остров |
| C8 | Мицелиальный клиф, gravel pocket и грибная бухта |

### 2.6 River profiles

| Код | Река |
|---|---|
| R0 | Отсутствует либо только подземный дренаж |
| R1 | Меандрирующая низинная река, старицы и пойма |
| R2 | Гравийная braided river с островами |
| R3 | Горная река, каскады, водопады, hanging tributaries |
| R4 | Сезонный arroyo/wadi, редкие постоянные pools |
| R5 | Болотные distributaries, протоки и затопленные старицы |
| R6 | Карстовый stream: исчезающие участки, springs и ущелья |
| R7 | Ледниковая braided river, meltwater и frozen reaches |
| R8 | Estuary/delta reach с tidal influence |

### 2.7 Cave families

Минимальный обязательный набор V2:

| Код | Cave family | Связь с поверхностью |
|---|---|---|
| Q1 | Limestone karst | M2/S4; камеры, колодцы, мосты, underground rivers |
| Q2 | Volcanic lava tubes | M6/S6; трубы, обрушения, lava pockets |
| Q3 | Glacial caves | M4/P5; ice caves, meltwater, subglacial lakes |
| Q4 | Deep crystalline / Deep Dark | Глубокие M3/M4; rifts, sculk transition, huge chambers |
| Q5 | Wet aquifer / lush caves | M1/M2; lakes, clay, moss, roots, waterfalls |
| Q6 | Dry sedimentary caves | M5/S5; cracks, dry chambers, sand/terracotta strata |

### 2.8 Ecology and debris codes

| Код | Значение |
|---|---|
| V0 | Деревья отсутствуют; растительность менее 5% |
| V1 | Редкая: 5–20% canopy/cover |
| V2 | Открытая: 20–45% |
| V3 | Лесная: 45–70% |
| V4 | Очень плотная: 70–95%, несколько ярусов |
| V5 | Водная/подводная: cover зависит от глубины и света |
| L0/L1/L2/L3 | Нет / редкие / обычные / многочисленные fallen logs |
| D0/D1/D2/D3/D4 | Нет / галька / валуны / talus / лёд или coral rubble |

Размер региона ниже — целевой характерный размер связного пятна в блоках, а
не жёсткий квадрат. Для линейных биомов указаны ширина и длина.

## 3. Matrix A — terrain, soil, stone и erosion

| Биом | Macro family | Micro terrain | Soil | Stone | Erosion |
|---|---|---|---|---|---|
| `minecraft:plains` | M1 | Пойменные уровни, пологие гривы, неглубокие балки | P1 | S1 | E0/E1 |
| `minecraft:sunflower_plains` | M1 | Тёплые приподнятые лёссовые террасы | P1 | S1 | E1 |
| `minecraft:snowy_plains` | M1/M4 | Мёрзлые котловины, снежные гривы, термокарст | P5 | S1/S3 | E0/E1 |
| `minecraft:ice_spikes` | M4 | Ледниковая чаша, моренные гряды, связные ледяные поля | P5 | S3 | E3/E5 |
| `minecraft:desert` | M5 | Erg, дюны, каменистые hamada и сухие русла | P4/P7 | S5 | E1/E4 |
| `minecraft:swamp` | M2 | Затопленная низина, островки, старицы, сложная shoreline | P3 | S1/S4 | E0 |
| `minecraft:mangrove_swamp` | M2 | Tidal flat, лагуны, протоки, грязевые островки | P3 | S7/S4 | E0/E5 |
| `minecraft:forest` | M1/M3 | Волнистые междуречья и широкие ручьевые долины | P1/P2 | S1/S2 | E1/E2 |
| `minecraft:flower_forest` | M1/M3 | Солнечные террасы, поляны, неглубокие карстовые чаши | P1 | S1/S4 | E1 |
| `minecraft:birch_forest` | M1/M3 | Светлые пологие склоны, камовые холмы | P1 | S2 | E1 |
| `minecraft:dark_forest` | M3 | Влажные закрытые ложбины, старые округлые гряды | P2 | S2 | E1/E2 |
| `minecraft:old_growth_birch_forest` | M3 | Высокие древние террасы, ravines и boulder fields | P1/P2 | S2 | E2 |
| `minecraft:old_growth_pine_taiga` | M3 | Широкие седловины, моренные холмы, глубокие долины | P2 | S2 | E2 |
| `minecraft:old_growth_spruce_taiga` | M3/M4 | Влажные крутые склоны, валунные поля, речные ступени | P2 | S2/S3 | E2/E3 |
| `minecraft:taiga` | M3 | Продольные лесные хребты и гравийные долины | P2 | S2 | E1/E2 |
| `minecraft:snowy_taiga` | M3/M4 | Заснеженные моренные холмы и замёрзшие ложбины | P5/P2 | S2/S3 | E2/E5 |
| `minecraft:savanna` | M1/M5 | Сухие rolling uplands, широкие сезонные долины | P1/P7 | S1/S5 | E1/E2 |
| `minecraft:savanna_plateau` | M5 | Ступенчатое плато, mesas и пологие ramps | P7 | S5 | E2/E4 |
| `minecraft:windswept_hills` | M3 | Старые округлые хребты, седловины и ветровые уступы | P6 | S2 | E2/E3 |
| `minecraft:windswept_gravelly_hills` | M3 | Гравийные гребни, осыпи и оголённые gullies | P6 | S2 | E3 |
| `minecraft:windswept_forest` | M3 | Лесные ridge shoulders и защищённые долины | P2/P6 | S2 | E2/E3 |
| `minecraft:windswept_savanna` | M5/M6 | Сухие разломанные плато, volcanic necks и cliffs | P7/P6 | S5/S6 | E3/E4 |
| `minecraft:jungle` | M2 | Карстовые котловины, tower valleys, streams и cliffs | P1/P3 | S4 | E2/E3 |
| `minecraft:sparse_jungle` | M2/M1 | Карстовые предгорья, открытые террасы и corridors | P1 | S4/S1 | E1/E2 |
| `minecraft:bamboo_jungle` | M2 | Влажные аллювиальные чаши, sinkholes и речные островки | P1/P3 | S4 | E1/E2 |
| `minecraft:badlands` | M5 | Многоуровневые mesas, benches и canyon floor | P7 | S5 | E4 |
| `minecraft:eroded_badlands` | M5 | Узкие hoodoo ridges, amphitheaters и dissected mesas | P7 | S5 | E4 |
| `minecraft:wooded_badlands` | M5 | Лесистые plateau tops, talus slopes и canyon tributaries | P7/P2 | S5 | E2/E4 |
| `minecraft:meadow` | M4/M3 | Альпийские террасы, hanging valleys и мягкие saddles | P6/P1 | S3 | E1/E3 |
| `minecraft:cherry_grove` | M3/M4 | Защищённые высокие чаши и ступенчатые террасы | P1/P6 | S2/S3 | E1/E2 |
| `minecraft:grove` | M4 | Субальпийские лесные чаши, морены и avalanche clearings | P5/P2 | S3 | E2/E3 |
| `minecraft:snowy_slopes` | M4 | U-shaped valley walls, snowfields, gullies и moraines | P5/P6 | S3 | E3/E5 |
| `minecraft:frozen_peaks` | M4 | Ледниковые цирки, broad summit ice и hanging valleys | P5 | S3 | E3/E5 |
| `minecraft:jagged_peaks` | M4 | Острые главные хребты, отроги, кулуары и ареты | P6/P5 | S3 | E3 |
| `minecraft:stony_peaks` | M4/M6 | Тёплые скальные гребни, limestone/volcanic variants | P6 | S3/S6 | E3 |
| `minecraft:river` | XR | Врезанное русло, point bars, levees, terraces | P3/P1 | Наследует + S1 | E0/E2 |
| `minecraft:frozen_river` | XR/M4 | Meltwater channel, braided bars, ледовые заторы | P5 | Наследует + S3 | E0/E5 |
| `minecraft:beach` | XC | Berm, dune ridge, washover и lagoon margin | P4 | S7 | E5 |
| `minecraft:snowy_beach` | XC/M4 | Снежный berm, gravel pocket и pressure-ice margin | P5/P4 | S7/S3 | E5 |
| `minecraft:stony_shore` | XC | Клиф, wave-cut platform, cobble fan | P6/P4 | Наследует + S7 | E5 |
| `minecraft:warm_ocean` | M7 | Мелкий карбонатный шельф, рифовые террасы и банки | P9 | S7 | E5 |
| `minecraft:lukewarm_ocean` | M7 | Шельф, sand waves, seagrass basins и island banks | P9 | S7 | E5 |
| `minecraft:deep_lukewarm_ocean` | M7 | Шельфовый склон, каньоны и isolated banks | P9 | S7 | E5 |
| `minecraft:ocean` | M7 | Волнистый шельф, sediment basins и rocky rises | P9 | S7 | E5 |
| `minecraft:deep_ocean` | M7 | Abyssal plain, continental slope и submarine canyons | P9 | S7 | E5 |
| `minecraft:cold_ocean` | M7/M4 | Glacial shelf, gravel banks и drowned moraines | P9/P5 | S7/S3 | E5 |
| `minecraft:deep_cold_ocean` | M7/M4 | Глубокий glacial basin, troughs и steep slope | P9 | S7/S3 | E5 |
| `minecraft:frozen_ocean` | M7/M4 | Pack-ice shelf, leads, pressure ridges и shoals | P5/P9 | S7/S3 | E5 |
| `minecraft:deep_frozen_ocean` | M7/M4 | Deep polar basin, ice shelves и submarine troughs | P9/P5 | S7/S3 | E5 |
| `minecraft:mushroom_fields` | M8 | Крупный эродированный остров, плато, чаши и бухты | P8 | S8 | E1/E2/E5 |
| `minecraft:dripstone_caves` | XU | Вертикальные карстовые камеры, колодцы и лабиринты | P3/P6 | S4 | E2/E3 |
| `minecraft:lush_caves` | XU | Влажные камеры, terraces, lakes и root shafts | P3/P1 | S1/S4 | E0/E2 |
| `minecraft:deep_dark` | XU | Огромные глубокие rifts, chambers и collapsed shelves | P6 | S2/S3 | E3 |

## 4. Matrix B — coast, rivers, water, caves и размеры

| Биом | Coast | River | Water features | Cave family | Размер региона |
|---|---|---|---|---|---|
| `minecraft:plains` | C1/C4 | R1 | Пруды, старицы, seasonal wetlands | Q5 | 768–4096 |
| `minecraft:sunflower_plains` | C1 | R1 | Редкие spring-fed ponds | Q5 | 256–1536 |
| `minecraft:snowy_plains` | C6 | R7 | Замёрзшие озёра, thermokarst ponds | Q3/Q5 | 768–4096 |
| `minecraft:ice_spikes` | C6 | R7 | Frozen tarns, meltwater cracks | Q3 | 256–1280 |
| `minecraft:desert` | C1/C3 | R4 | Oasis spring, playa, flash-flood pool | Q6 | 1024–6144 |
| `minecraft:swamp` | C4 | R5 | Старицы, протоки, shallow lakes | Q5/Q1 | 512–3072 |
| `minecraft:mangrove_swamp` | C5 | R5/R8 | Tidal creeks, лагуны, mud pools | Q5/Q1 | 384–2048 |
| `minecraft:forest` | C1/C2 | R1/R2 | Лесные ручьи и beaver-like ponds | Q5 | 768–4096 |
| `minecraft:flower_forest` | C1 | R1 | Springs и маленькие clear-water ponds | Q5/Q1 | 256–1536 |
| `minecraft:birch_forest` | C1/C2 | R1/R2 | Kettle ponds и ручьи | Q5 | 512–3072 |
| `minecraft:dark_forest` | C2/C4 | R1 | Тёмные oxbows, saturated hollows | Q5 | 512–3072 |
| `minecraft:old_growth_birch_forest` | C2/C3 | R2/R3 | Ravine streams и cascade pools | Q5/Q4 | 384–2048 |
| `minecraft:old_growth_pine_taiga` | C2/C3 | R2/R3 | Moraine lakes, gravel streams | Q4/Q5 | 768–4096 |
| `minecraft:old_growth_spruce_taiga` | C2/C3 | R2/R3 | Deep forest lakes и waterfalls | Q4/Q5 | 768–4096 |
| `minecraft:taiga` | C2/C3 | R2/R3 | Elongated lakes и tributary brooks | Q4/Q5 | 768–4096 |
| `minecraft:snowy_taiga` | C6/C2 | R7/R3 | Frozen lakes, meltwater channels | Q3/Q4 | 768–4096 |
| `minecraft:savanna` | C1/C3 | R4/R1 | Seasonal pans и permanent river pools | Q6/Q5 | 768–4096 |
| `minecraft:savanna_plateau` | C3 | R4/R3 | Escarpment falls и plateau springs | Q6 | 512–3072 |
| `minecraft:windswept_hills` | C3/C2 | R3/R2 | Headwater tarns и cascades | Q4 | 512–4096 |
| `minecraft:windswept_gravelly_hills` | C3/C2 | R3/R2 | Gravel seep, temporary pools | Q4 | 256–2048 |
| `minecraft:windswept_forest` | C3/C2 | R3 | Hanging streams и waterfalls | Q4/Q5 | 384–3072 |
| `minecraft:windswept_savanna` | C3 | R4/R3 | Canyon pools, volcanic springs | Q6/Q2 | 384–3072 |
| `minecraft:jungle` | C3/C5 | R6/R3 | Karst springs, waterfalls, blue pools | Q1/Q5 | 1024–6144 |
| `minecraft:sparse_jungle` | C1/C3 | R6/R1 | Open streams, seasonal ponds | Q1/Q5 | 384–3072 |
| `minecraft:bamboo_jungle` | C4/C5 | R6/R5 | Flooded groves, springs, sinkhole lakes | Q1/Q5 | 384–2048 |
| `minecraft:badlands` | C3 | R4 | Canyon river, playa и flash pools | Q6 | 768–4096 |
| `minecraft:eroded_badlands` | C3 | R4 | Narrow slot streams и plunge pools | Q6 | 256–2048 |
| `minecraft:wooded_badlands` | C3 | R4/R3 | Plateau springs и perennial canyon reach | Q6/Q4 | 384–3072 |
| `minecraft:meadow` | C3 | R3 | Alpine tarn, spring и braided headwater | Q4/Q3 | 384–3072 |
| `minecraft:cherry_grove` | C2/C3 | R3/R1 | Terrace ponds и clear springs | Q4/Q5 | 256–2048 |
| `minecraft:grove` | C6/C3 | R3/R7 | Moraine lake и snowmelt brook | Q3/Q4 | 384–3072 |
| `minecraft:snowy_slopes` | C6/C3 | R7/R3 | Hanging lake, snowmelt waterfall | Q3 | 256–2048 вдоль хребта |
| `minecraft:frozen_peaks` | C6/C3 | R7 | Cirque lakes, glacier tongues | Q3/Q4 | 256–1536; цепь 1–8 км |
| `minecraft:jagged_peaks` | C3 | R3/R7 | High tarns, couloir waterfalls | Q4/Q3 | 256–1536; цепь 1–8 км |
| `minecraft:stony_peaks` | C3 | R3/R6 | Springs, karst/volcanic crater lake | Q1/Q2/Q4 | 256–1536; цепь 1–6 км |
| `minecraft:river` | C4 у устья | R1/R2/R3/R4/R6 | Пойма, bars, oxbows, confluences | Наследует watershed | 12–96 шир.; 512–8192 дл. |
| `minecraft:frozen_river` | C6/R8 at mouth | R7 | Leads, ice jams, meltwater bars | Q3 below glacial reaches | 12–80 шир.; 512–8192 дл. |
| `minecraft:beach` | C1 | R8 | Lagoon mouth, tidal pool, washover | Q5/Q6 по провинции | 16–128 шир.; 256–4096 дл. |
| `minecraft:snowy_beach` | C6 | R8/R7 | Ice leads, frozen tidal pools | Q3 | 16–96 шир.; 256–4096 дл. |
| `minecraft:stony_shore` | C2/C3 | R8/R3 | Rock pools, sea caves, waterfalls | Q4/Q1/Q2 по провинции | 16–160 шир.; 256–4096 дл. |
| `minecraft:warm_ocean` | C7 | R8 | Reef lagoon, atoll, seagrass shallows | Q5 | 1024–8192 |
| `minecraft:lukewarm_ocean` | C7/C1 | R8 | Island banks, lagoons, seagrass bays | Q5 | 2048–12288 |
| `minecraft:deep_lukewarm_ocean` | C7/C3 | R8 | Submarine springs и canyon currents | Q5/Q4 | 2048–12288 |
| `minecraft:ocean` | C1/C2/C3 | R8 | Banks, islands, kelp margins | Q5/Q4 | 2048–16384 |
| `minecraft:deep_ocean` | C3 | R8 | Редкие cold seeps и hydrothermal vents | Q4/Q5 | 4096–20000 |
| `minecraft:cold_ocean` | C2/C6 | R8/R7 | Kelp banks, drowned moraines | Q3/Q5 | 2048–12288 |
| `minecraft:deep_cold_ocean` | C3/C6 | R8/R7 | Glacial troughs и submarine springs | Q3/Q4 | 4096–20000 |
| `minecraft:frozen_ocean` | C6 | R7/R8 | Leads, pressure ridges, icebergs | Q3 | 2048–12288 |
| `minecraft:deep_frozen_ocean` | C6/C3 | R7/R8 | Ice shelf, deep trough, iceberg field | Q3/Q4 | 4096–20000 |
| `minecraft:mushroom_fields` | C8 | R1/R6 | Luminous ponds, springs, sheltered bays | Q5/Q1 | 768–4096 |
| `minecraft:dripstone_caves` | C0 | R6 underground | Underground river, sump, drip pools | Q1 | 128–1536 в 3D |
| `minecraft:lush_caves` | C0 | R5/R6 underground | Lakes, waterfalls, clay pools | Q5 | 128–1536 в 3D |
| `minecraft:deep_dark` | C0 | R0/редкий deep river | Black lakes и flooded rifts редко | Q4 | 256–2048 в 3D |

## 5. Matrix C — vegetation, belts, debris, landmarks и ecotones

| Биом | Плотность | Высотные пояса | Деревья | Подлесок | Logs | Debris | Редкий landmark | Основные ecotones |
|---|---|---|---|---|---|---|---|---|
| `minecraft:plains` | V1 | floodplain→dry terrace | Редкие oak-группы у воды | Трава, цветы, кусты по влаге | L1 | D1 | Крупный spring-fed pond | forest edge, meadow foothill, wet floodplain |
| `minecraft:sunflower_plains` | V1 | terrace→warm ridge | Одинокий oak, fruit-tree fallback | Подсолнуховые поля с разрывами | L0/L1 | D1 | Панорамная цветочная гряда | plains, savanna, flower forest |
| `minecraft:snowy_plains` | V1 | frozen basin→wind ridge | Редкие spruce shelters | Низкая трава, dry shrub, snow drifts | L1 | D1/D4 | Замёрзшее kettle lake | snowy taiga, grove, ice-spike moraine |
| `minecraft:ice_spikes` | V0/V1 | moraine→spike field→ice rim | Spruce только по краю | Лишайник/редкая трава на моренах | L0 | D4 | Связный ледниковый амфитеатр | snowy plains, frozen peaks, frozen ocean |
| `minecraft:desert` | V0/V1 | playa→dune→rock plateau | Редкие oasis palm/acacia fallback | Dry grass, cactus clusters | L0 | D1/D2 | Oasis у выхода aquifer | savanna scrub, badlands bench, dune beach |
| `minecraft:swamp` | V4 | open water→island→wet forest | Swamp oak, willow-like optional palette | Камыш, папоротник, мох, aquatic layers | L3 | D1 | Затопленный древний лес | wet forest, mangrove tidal zone, meadow levee |
| `minecraft:mangrove_swamp` | V4 | subtidal→mudflat→canopy | Mangrove с разной высотой корней | Propagules, mud plants, reeds | L3 | D1 | Большая tidal lagoon | swamp, warm coast, jungle estuary |
| `minecraft:forest` | V3 | valley→slope→open ridge | Mixed oak/birch, редкий old tree | Fern, shrub, grass, shade gaps | L2 | D1/D2 | Fallen-tree clearing | plains meadow, birch belt, dark hollow |
| `minecraft:flower_forest` | V2/V3 | wet hollow→flower terrace | Open oak/birch groups | Несколько цветочных сообществ по влаге | L1 | D1 | Цветочная карстовая чаша | forest, meadow, plains |
| `minecraft:birch_forest` | V3 | low slope→bright ridge | Birch разного возраста | Fern, grass, sparse shrub | L2 | D1/D2 | Белая boulder grove | plains, old birch, mixed forest |
| `minecraft:dark_forest` | V4 | wet hollow→closed canopy ridge | Dark oak с canopy gaps | Moss, mushroom, fern, deadwood | L3 | D2 | Древняя storm-fall поляна | forest buffer, swamp hollow, old upland |
| `minecraft:old_growth_birch_forest` | V3/V4 | terrace→ravine→high bench | Высокая birch, смешанные старые группы | Fern layers, shrubs, moss | L3 | D2 | Древняя берёзовая ravine | birch forest, meadow, old hills |
| `minecraft:old_growth_pine_taiga` | V3 | valley bog→pine slope→ridge | Giant pine clusters | Berry, fern, podzol openings | L3 | D2 | Fallen giant over stream | taiga, spruce hollow, windswept saddle |
| `minecraft:old_growth_spruce_taiga` | V4 | wet valley→spruce slope→snow rim | Giant spruce, redwood-like optional accent | Moss, fern, berry, coarse logs | L3 | D2/D3 | Mossy boulder ravine | pine taiga, snowy taiga, grove |
| `minecraft:taiga` | V3 | river terrace→forest slope→saddle | Spruce/pine mixture | Berry, fern, grass, moss | L2 | D2 | Gravel stream confluence | forest, old-growth pockets, snowy belt |
| `minecraft:snowy_taiga` | V3 | frozen valley→spruce slope→snowline | Snowy spruce groups | Berry, fern shelters, snow drifts | L2/L3 | D2/D4 | Frozen forest lake | taiga, grove, snowy plains |
| `minecraft:savanna` | V1/V2 | seasonal valley→grass upland | Acacia groves along drainage | Tall grass, dry shrub, flowers after rain | L1 | D1/D2 | Baobab-like optional giant tree | plains, sparse jungle, dry plateau |
| `minecraft:savanna_plateau` | V1 | canyon floor→bench→plateau top | Wind-shaped acacia | Dry grass pockets, cliff shrubs | L1 | D2/D3 | Escarpment waterfall | savanna ramp, badlands bench, volcanic ridge |
| `minecraft:windswept_hills` | V1/V2 | valley forest→heath→bare ridge | Wind-shaped oak/spruce pockets | Heath, grass, exposed moss | L1 | D2/D3 | Long natural saddle | forest, meadow, gravelly crest |
| `minecraft:windswept_gravelly_hills` | V0/V1 | gravel fan→scree→bare crest | Единичные twisted trees | Sparse grass in protected pockets | L0/L1 | D3 | Огромный talus fan | windswept hills, stony shore, peak apron |
| `minecraft:windswept_forest` | V2/V3 | protected valley→forest shoulder→open crest | Wind-shaped oak/spruce | Fern, shrub, heath transition | L2 | D2/D3 | Forested rock gate | forest, windswept hills, meadow |
| `minecraft:windswept_savanna` | V1 | arroyo→dry bench→volcanic/cliff crest | Twisted acacia | Dry grass, thorn shrub | L1 | D2/D3 | Broken volcanic neck | savanna plateau, badlands, volcanic belt |
| `minecraft:jungle` | V4 | river floor→canopy→karst cliff→cloud rim | Jungle giants, canopy tiers | Fern, vines, bamboo pockets, flowers | L3 | D2 | Карстовая долина с водопадом | sparse foothill, bamboo basin, mangrove estuary |
| `minecraft:sparse_jungle` | V2 | open terrace→karst foot→forest wall | Open jungle/acacia mixture | Tall grass, fern islands, shrubs | L2 | D1/D2 | Natural karst pass | savanna, jungle wall, plains terrace |
| `minecraft:bamboo_jungle` | V4 | flooded hollow→bamboo bench→jungle rim | Jungle trees над bamboo layers | Bamboo mosaics, fern, wet flowers | L2/L3 | D1 | Sinkhole lake in bamboo | jungle, swamp pocket, sparse terrace |
| `minecraft:badlands` | V0/V1 | canyon floor→bench→mesa top | Редкие dry oak у постоянной воды | Dry shrub, grass on deposits | L0/L1 | D2/D3 | Большой river amphitheater | desert, wooded plateau, savanna margin |
| `minecraft:eroded_badlands` | V0 | slot floor→hoodoo wall→razor ridge | Только редкие protected shrubs/trees | Minimal dry plants | L0 | D3 | Hoodoo cathedral без симметрии | badlands benches, desert apron |
| `minecraft:wooded_badlands` | V2 | canyon floor→talus→wooded plateau | Oak/pine на устойчивых tops | Dry grass, shrub, podzol pockets | L1/L2 | D2/D3 | Plateau spring grove | badlands wall, forested upland, savanna |
| `minecraft:meadow` | V1/V2 | valley floor→flower terrace→alpine heath | Редкие spruce/cherry groups ниже snowline | Цветочные мозаики, grass height by moisture | L1 | D1/D2 | Alpine tarn meadow | forest foothill, grove, snowy slope |
| `minecraft:cherry_grove` | V3 | sheltered basin→terrace→rock rim | Cherry clusters с полянами | Petal ground, flowers, fern near streams | L2 | D1/D2 | Крупная cherry terrace village site | meadow, forest, snowy grove |
| `minecraft:grove` | V3 | moraine forest→avalanche gap→snowline | Spruce clusters, krummholz at rim | Berry, fern, snow pockets | L2/L3 | D2/D4 | Moraine lake in spruce bowl | taiga, meadow, snowy slopes |
| `minecraft:snowy_slopes` | V0/V1 | subalpine edge→snowfield→ice apron | Krummholz spruce только внизу | Alpine grass islands, snow drift | L0/L1 | D3/D4 | Hanging glacial valley | grove, meadow moraine, peak cirque |
| `minecraft:frozen_peaks` | V0 | cirque→glacier→summit ice | Нет; rare krummholz below biome edge | Lichen only on exposed rock | L0 | D4/D3 | Glacier tongue and blue-ice cave | snowy slope, grove moraine, frozen valley |
| `minecraft:jagged_peaks` | V0 | talus→arete→summit | Нет; tree line outside core | Alpine grass only on shelves | L0 | D3 | Multi-ridge alpine massif | meadow saddle, snowy slope, stony warm face |
| `minecraft:stony_peaks` | V0/V1 | warm foot→rock shelf→bare summit | Sparse oak/jungle by climate at base | Dry alpine grass, moss in springs | L0/L1 | D3 | Karst arch or volcanic crater variant | jungle karst, meadow, jagged ridge |
| `minecraft:river` | V2 along banks | channel→bar→levee→floodplain | Riparian willow/oak-like strips | Reed, grass, wet flower, gravel pioneer | L2 | D1/D2 | Major confluence/delta head | Parent biome через 32–128 block riparian ecotone |
| `minecraft:frozen_river` | V1 | channel ice→bar→frozen floodplain | Spruce/willow-like shelter strips | Dry grass, reed remnants, snow | L1 | D1/D4 | Ice-jam gorge | snowy plains, taiga, glacial outwash |
| `minecraft:beach` | V0/V1 | swash→berm→dune→backshore | Нет; rare coastal tree above storm line | Beach grass and sparse shrub | L1 driftwood | D1 | Lagoon breach or dune field | ocean shallows, dune, inland vegetation |
| `minecraft:snowy_beach` | V0 | ice margin→gravel berm→snow drift | Нет | Sparse dry grass above ice | L1 driftwood | D1/D4 | Pressure-ice cove | frozen ocean, snowy plain/taiga |
| `minecraft:stony_shore` | V0/V1 | rock pool→platform→cliff→clifftop | Cliff-top trees по parent biome | Moss/fern in wet cracks | L1 driftwood | D2/D3 | Sea arch только как редкий eroded mass | ocean platform, parent cliff, beach pocket |
| `minecraft:warm_ocean` | V5 | lagoon→reef crest→outer slope | Mangrove/palm only on emerged islands | Seagrass, coral zonation | L0/L1 | D4 | Large irregular atoll | warm reef, mangrove lagoon, lukewarm shelf |
| `minecraft:lukewarm_ocean` | V5 | seagrass flat→bank→shelf edge | Coastal trees only on islands | Seagrass, kelp transition, coral patches | L0/L1 | D4/D1 | Mountain/flat island chain | warm reef, temperate shelf, sandy coast |
| `minecraft:deep_lukewarm_ocean` | V5 | upper slope→canyon→deep terrace | Нет | Sparse kelp on lit banks | L0 | D1/D2 | Submarine canyon and isolated bank | lukewarm shelf, deep ocean |
| `minecraft:ocean` | V5 | nearshore→shelf→rocky bank | Island trees by climate | Kelp/seagrass mosaics | L0/L1 | D1/D2 | Coherent rocky archipelago | beach/shore, cold/lukewarm current fronts |
| `minecraft:deep_ocean` | V0/V5 | slope→abyss→seamount | Нет | Sparse deep flora | L0 | D2 | Seamount chain or trench | ocean shelf, deep cold/lukewarm basins |
| `minecraft:cold_ocean` | V5 | gravel shelf→kelp bank→moraine | Conifers only on islands | Dense kelp belts, cold seagrass | L0/L1 | D2/D4 | Drowned moraine archipelago | snowy/stony coast, ocean current front |
| `minecraft:deep_cold_ocean` | V0/V5 | glacial trough→deep terrace | Нет | Sparse kelp on banks | L0 | D2/D4 | Deep glacial trough | cold shelf, deep frozen basin |
| `minecraft:frozen_ocean` | V0/V5 | lead→pack ice→pressure ridge | Нет | Подводная sparse flora under leads | L0 | D4 | Irregular iceberg field | snowy beach, cold ocean ice margin |
| `minecraft:deep_frozen_ocean` | V0 | ice shelf→deep trough→abyss | Нет | Minimal | L0 | D4/D2 | Giant tabular iceberg and ice cave | frozen shelf, deep cold basin |
| `minecraft:mushroom_fields` | V3/V4 | sheltered bay→mycelial forest→plateau | Giant mushrooms нескольких silhouettes | Small fungi, moss, luminous pockets | L1/L2 | D2 | Огромная mushroom hollow/island crown | ocean cliff, mycelial beach, cave opening |
| `minecraft:dripstone_caves` | V0/V1 | lower dry floor→wet channel→shaft | Нет | Dripstone clusters by moisture/flow | L0 | D3 | Karst bridge over underground river | Q1 dry chamber↔Q5 wet passage↔surface karst |
| `minecraft:lush_caves` | V4 | lake floor→moss terrace→root ceiling | Azalea/root columns, rare cave tree | Moss, clay, vines, flowers by light | L2 | D1/D2 | Daylight shaft with large tree | Q5 aquifer↔Q1 spring cave↔forest/swamp surface |
| `minecraft:deep_dark` | V0/V1 | lower rift→sculk shelf→huge chamber | Нет | Sculk density grows inward, not random spots | L0 | D2/D3 | Ancient-scale asymmetric abyss | Q4 crystalline cave↔narrow dark threshold |

## 6. Обязательные правила ecotones

Граница не создаётся отдельным случайным feature. Она должна вычисляться из
расстояния до соседнего climate/biome/province field.

Минимальные переходы:

| Переход | Ширина | Surface/vegetation signature |
|---|---:|---|
| forest → plains/meadow | 32–128 | Canopy gaps, shrubs, отдельные деревья, постепенное уменьшение logs |
| taiga → grove → snowy slopes | 64–192 | Podzol, krummholz, snow pockets, moraine debris |
| desert → savanna/badlands | 64–256 | Dry grass, sandstone/terracotta lenses, wadis |
| swamp → wet forest | 48–192 | Mud hollows, levees, willow-like trees, fern layers |
| plains → foothills/meadow | 64–256 | Rolling terraces, boulders, increasing exposed stone |
| jungle → sparse jungle → karst | 64–256 | Canopy thinning, limestone exposure, springs, tower foot fans |
| ocean → beach/shore → dunes/cliff | 32–192 | Bathymetry, sediment size, storm berm, backshore plants |
| glacier → moraine → alpine meadow | 64–256 | Blue/packed ice, till, kettle ponds, pioneer vegetation |
| river → parent biome | 32–128 | Channel bar, levee, floodplain, riparian trees |

## 7. Region-size constraints

1. Размеры в матрице являются target envelopes, а не гарантированным размером
   каждого пятна.
2. Main biome speck меньше 96 блоков считается ошибкой, кроме:
   - линейного river/beach ecotone;
   - cave transition;
   - осмысленного островка в wetland/archipelago.
3. Mountain biome может быть узким поперёк хребта, но должен сохранять
   непрерывность family field на 1–8 км.
4. Ocean temperature transition не должен создавать одиночные чанки.
5. Underground biome оценивается в 3D connected volume, а не только по
   горизонтальной площади.
6. Rare landmark не может использоваться для достижения минимального размера
   региона.

## 8. Matrix D — climate, диапазоны и проверяемый статус

Обозначения elevation: `L` до 80, `M` 70–128, `H` 112–192, `A` выше 176,
`U` underground, `S` sea floor. Диапазоны пересекаются намеренно и должны
смешиваться плавно. Slope — подъём на 4 горизонтальных блока:
`flat` 0–2, `gentle` 0–5, `rolling` 2–10, `steep` 8–24, `cliff` 20+.

`NS optional` означает безопасный climate-aware fallback без обязательной
зависимости от Nature’s Spirit; `vanilla fallback` обязателен. Runtime surface
и vegetation resolvers подключены для всех строк, но значения `planned` ниже
сохраняют смысл **визуально не принятой строки**, а не отсутствующего кода.

| Biome ID | Macro climate | Elevation | Slope | River affinity | Coast affinity | Groundwater/wetness | Optional integration | Distinction contract | Implementation | Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| `minecraft:plains` | temperate continental | L–M | flat/gentle | high floodplain | medium low coast | medium; wet pockets | NS shrubs, vanilla fallback | levees + oak groups + loam palette | planned | not run |
| `minecraft:sunflower_plains` | warm temperate | L–M | flat/gentle | medium | low | low/medium | NS meadow plants, fallback | loess terrace + flower mosaics + open canopy | planned | not run |
| `minecraft:snowy_plains` | subpolar continental | L–M | flat/rolling | medium/frozen | medium polar | low; thermokarst pockets | NS cold shrubs, fallback | snow drifts + frozen loam + shelter trees | planned | not run |
| `minecraft:ice_spikes` | polar dry | L–H | rolling/steep | low/frozen | medium polar | frozen pockets | none required | moraine + coherent ice field + bare cover | planned | not run |
| `minecraft:desert` | hot arid | L–H | flat/rolling | seasonal/wadi | medium arid | very low; oasis high | NS arid plants/palm, fallback | dune/hamada + sandstone exposure + clustered scrub | planned | not run |
| `minecraft:swamp` | temperate humid | L | flat | very high/distributary | high estuary | saturated | NS willow/reeds, fallback | mud islands + open water network + layered wet canopy | planned | not run |
| `minecraft:mangrove_swamp` | tropical coastal humid | L | flat | very high/tidal | very high | tidal/saturated | NS coastal plants, fallback | tidal creeks + root-height zones + mudflat | planned | not run |
| `minecraft:forest` | temperate humid | L–M | gentle/rolling | medium/high | low/medium | medium | NS deciduous accents, fallback | mixed canopy clusters + fern edge + loam/rock gaps | planned | not run |
| `minecraft:flower_forest` | temperate humid | L–H | gentle/rolling | medium | low | medium/high pockets | NS flowers, fallback | moisture flower mosaics + open canopy + karst bowls | planned | not run |
| `minecraft:birch_forest` | cool temperate | L–M | gentle/rolling | medium | low | medium | NS birch companions, fallback | bright irregular canopy + камовые hills + pale debris | planned | not run |
| `minecraft:dark_forest` | temperate wet | L–M | gentle/rolling | high | low | high hollows | NS shade plants, fallback | closed canopy + storm gaps + moss/deadwood | planned | not run |
| `minecraft:old_growth_birch_forest` | cool temperate wet | M–H | rolling/steep | medium | low/cliff | medium | NS old trees, fallback | tall silhouettes + ravines + boulder fields | planned | not run |
| `minecraft:old_growth_pine_taiga` | boreal continental | M–H | rolling/steep | high gravel | low/cliff | medium | NS conifers, fallback | giant pine clusters + podzol openings + moraine | planned | not run |
| `minecraft:old_growth_spruce_taiga` | boreal humid | M–H | rolling/steep | high mountain | low/cliff | high valleys | NS redwood/spruce, fallback | tiered giant spruce + moss ravines + talus | planned | not run |
| `minecraft:taiga` | boreal | L–H | gentle/rolling | high gravel | low/medium | medium | NS conifers/shrubs, fallback | longitudinal groves + berry floor + gravel streams | planned | not run |
| `minecraft:snowy_taiga` | cold boreal | L–H | rolling | high/frozen | medium polar | frozen/medium | NS cold conifers, fallback | snow shelter clusters + frozen lakes + podzol gaps | planned | not run |
| `minecraft:savanna` | warm seasonal dry | L–M | gentle/rolling | medium/seasonal | medium | low; river high | NS dry woodland, fallback | drainage acacia groves + grass mosaics + dry rock | planned | not run |
| `minecraft:savanna_plateau` | warm arid highland | M–H | rolling/steep | low/seasonal | low/cliff | low | NS dry shrubs, fallback | plateau steps + wind acacia + escarpment rock | planned | not run |
| `minecraft:windswept_hills` | cool windy | M–H | steep/cliff | high headwater | cliff | medium valleys | NS heath, fallback | bare ridges + sheltered tree pockets + saddles | planned | not run |
| `minecraft:windswept_gravelly_hills` | cool windy dry | M–H | steep/cliff | medium gravel | cliff | low | NS scree plants, fallback | gravel crest + talus fan + sparse twisted trees | planned | not run |
| `minecraft:windswept_forest` | cool windy humid | M–H | rolling/steep | high headwater | cliff | medium | NS wind trees, fallback | asymmetric canopy + open crest + exposed rock gate | planned | not run |
| `minecraft:windswept_savanna` | warm windy arid | M–H | steep/cliff | seasonal canyon | cliff | low | NS dry/volcanic pioneers, fallback | broken plateau + volcanic variant + twisted acacia | planned | not run |
| `minecraft:jungle` | tropical perhumid | L–H | rolling/steep | very high/karst | high humid cliff | high | NS tropical plants/trees, fallback | canopy tiers + limestone towers + river gaps | planned | not run |
| `minecraft:sparse_jungle` | tropical seasonal | L–M | gentle/rolling | medium/high | medium | medium | NS tropical shrubs, fallback | canopy edge + open terraces + limestone exposure | planned | not run |
| `minecraft:bamboo_jungle` | tropical wet basin | L–M | flat/rolling | very high | medium estuary | high/saturated pockets | NS bamboo companions, fallback | bamboo mosaics + sinkholes + emergent trees | planned | not run |
| `minecraft:badlands` | hot arid continental | L–H | rolling/cliff | seasonal canyon | cliff | very low | NS arid plants, fallback | painted benches + canyon sediment + sparse drainage scrub | planned | not run |
| `minecraft:eroded_badlands` | hot hyper-arid | M–H | steep/cliff | low/flash | cliff | very low | NS sparse arid plants, fallback | hoodoo rhythm + bare slots + talus aprons | planned | not run |
| `minecraft:wooded_badlands` | warm semi-arid upland | M–H | rolling/steep | medium canyon | cliff | low/medium springs | NS plateau trees, fallback | wooded tops + painted walls + spring groves | planned | not run |
| `minecraft:meadow` | cool humid highland | M–H | gentle/rolling | high headwater | low/cliff | medium | NS alpine flowers, fallback | flower mosaics + treeline transition + alpine tarn | planned | not run |
| `minecraft:cherry_grove` | mild humid highland | M–H | gentle/rolling | medium/high | low/cliff | medium | NS understory, fallback | clustered cherry terraces + petal gaps + rock rim | planned | not run |
| `minecraft:grove` | subalpine humid | H–A | rolling/steep | high snowmelt | polar cliff | frozen/medium | NS alpine conifers, fallback | spruce bowls + avalanche gaps + moraine | planned | not run |
| `minecraft:snowy_slopes` | alpine cold | H–A | steep/cliff | high snowmelt | polar cliff | frozen | NS alpine pioneers, fallback | continuous snow belts + gullies + sparse lower krummholz | planned | not run |
| `minecraft:frozen_peaks` | glacial alpine | A | steep/cliff | glacial headwater | polar cliff | frozen | none required | glacier mass + moraine + exposed crystalline rock | planned | not run |
| `minecraft:jagged_peaks` | alpine continental | H–A | cliff | high headwater | cliff | low/frozen pockets | none required | aretes + couloirs + height-thinned cover | planned | not run |
| `minecraft:stony_peaks` | warm alpine | H–A | steep/cliff | karst/volcanic headwater | cliff | low; spring pockets | NS alpine plants, fallback | calcite/volcanic variants + bare shelves + springs | planned | not run |
| `minecraft:river` | inherited watershed | L–H corridor | flat–steep by family | defining | mouth high | high in corridor only | NS riparian plants, fallback | channel order profile + point bars + narrow riparian grammar | partial: Stage 5 physical pass | Stage 5 synthetic passed; Stage 6 not run |
| `minecraft:frozen_river` | cold inherited | L–H corridor | flat–steep by family | defining/frozen | polar mouth high | frozen/high | NS cold riparian, fallback | ice/bar zones + snowmelt banks + shelter strip | partial: Stage 5 physical pass | Stage 5 synthetic passed; Stage 6 not run |
| `minecraft:beach` | inherited temperate/warm | sea level | flat/gentle | mouth high | defining | saline/medium backshore | NS coastal grass, fallback | swash/berm/dune + driftwood + inland ecotone | planned | not run |
| `minecraft:snowy_beach` | polar coast | sea level | flat/gentle | frozen mouth high | defining | frozen/saline | NS cold coast plants, fallback | gravel-snow berm + pressure ice + sparse cover | planned | not run |
| `minecraft:stony_shore` | inherited rocky coast | sea level–M | steep/cliff | waterfall/mouth | defining | wet cracks | NS cliff plants, fallback | wave platform + cobble fan + cliff-top transition | planned | not run |
| `minecraft:warm_ocean` | tropical marine | S | shelf | river mouth | reef coast | saline | NS island vegetation, fallback | reef zonation + carbonate bed + atoll grammar | partial: atoll feature | not run |
| `minecraft:lukewarm_ocean` | subtropical marine | S | shelf | river mouth | sandy/reef | saline | NS island vegetation, fallback | seagrass banks + patch reef + island vegetation | partial: atoll feature | not run |
| `minecraft:deep_lukewarm_ocean` | subtropical deep marine | S deep | shelf/cliff | submarine mouth | shelf edge | saline | none required | deep terrace + canyon + sparse lit-bank flora | planned | not run |
| `minecraft:ocean` | temperate marine | S | shelf | river mouth | mixed | saline | NS island vegetation, fallback | rocky banks + kelp mosaic + coherent islands | planned | not run |
| `minecraft:deep_ocean` | temperate deep marine | S deep | gentle/cliff | submarine mouth | shelf edge | saline | none required | abyssal sediment + seamount + sparse flora | planned | not run |
| `minecraft:cold_ocean` | cold marine | S | shelf | glacial mouth | gravel/polar | saline/cold | NS cold coast plants, fallback | drowned moraine + kelp belts + gravel banks | planned | not run |
| `minecraft:deep_cold_ocean` | cold deep marine | S deep | trough/cliff | glacial mouth | shelf edge | saline/cold | none required | glacial trough + deep terrace + cold sparse flora | planned | not run |
| `minecraft:frozen_ocean` | polar marine | S/sea ice | shelf | frozen mouth | ice defining | frozen/saline | none required | leads + pressure ridges + shoals | planned | not run |
| `minecraft:deep_frozen_ocean` | polar deep marine | S deep/ice | trough/cliff | frozen mouth | ice shelf | frozen/saline | none required | tabular ice + submarine trough + abyss | planned | not run |
| `minecraft:mushroom_fields` | mild oceanic mycelial | L–H island | rolling/steep coast | medium | mycelial defining | medium/high pockets | NS fungi optional, fallback | three mushroom silhouettes + mycelial rock + luminous wet pockets | partial: mycelial grove | not run |
| `minecraft:dripstone_caves` | underground arid/karst | U | chamber/cliff | underground high | none | low–high by channel | NS cave plants optional, fallback | carbonate layers + drip gradients + underground river | partial: decorative overlays | not run |
| `minecraft:lush_caves` | underground humid | U | chamber/terrace | underground high | none | high/aquifer | NS cave flora optional, fallback | moss terraces + root shafts + lake-light gradient | partial: decorative overlays | not run |
| `minecraft:deep_dark` | deep underground | U deep | rift/cliff | very low/controlled | none | low; rare black lake | none required | sculk inward gradient + asymmetric rift + bare threshold | partial: decorative overlays | not run |

## 9. Implementation status

Текущее проверенное состояние:

- статическое присутствие в vanilla Overworld biome source: `53/53`;
- fresh-world runtime выполнен на трёх seed, но не покрывает все 53 биома;
- строк с подключёнными surface profiles: `53/53`;
- строк с подключёнными vegetation profiles: `53/53`;
- визуально принятых строк матрицы: `0/53`;
- строк с отдельным явным surface biome condition: `28/53`;
- строк с отдельным полным vegetation profile: `53/53`;
- строк с измеренным target region size: `0/53`;
- ecotone inputs доступны resolver: river/lake/wet bank/coast/alpine/slope/base;
- реализованных geological cave-family связей: `0/6`.

Эти значения должны обновляться только после тестов и визуального отчёта.

## 10. Acceptance для отдельной строки

Строка переводится из `SPEC` в `IMPLEMENTED`, только если:

1. terrain family определяется общим province/landform field;
2. micro terrain измерен height/slope map;
3. surface palette видна минимум в трёх независимых seed;
4. vegetation density попадает в заданный диапазон;
5. coast/river profile согласован с общей hydrology;
6. ecotone не имеет границы в один блок/чанк;
7. cave family наследует surface province;
8. размер региона попадает в envelope без массовых specks;
9. landmark density не заменяет основную форму биома;
10. сохранены маршруты и площадки для строительства;
11. dedicated server не сообщает far-chunk/cascading errors;
12. приложены скриншоты без шейдеров и с целевым shader stack.
