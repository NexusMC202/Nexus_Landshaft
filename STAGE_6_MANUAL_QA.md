# Nexus Landscape — Stage 6 manual QA

For the current five-seed, long-distance and profiler workflow, use
[`LIVE_QA_MULTI_SEED.md`](LIVE_QA_MULTI_SEED.md). The targeted coordinates
below remain the regression scene catalog.

Статус: **PARTIAL** до получения и проверки пользовательских screenshots.
Stage 7 не входит в этот документ.

## Быстрая установка

1. Установить Minecraft `1.21.1`, NeoForge `21.1.235` или более новый
   совместимый `21.1.x`, Java 21.
2. Удалить старый Nexus Landscape JAR из `<instance>/mods`, затем положить туда
   `nexus_landscape-0.8.0.jar`. Одновременно две версии мода не держать.
3. Обязательных сторонних модов нет. Tectonic, TerraBlender и внешний worldgen
   datapack для обычного запуска не нужны; данные Nexus V2 находятся внутри JAR.
4. Nature’s Spirit необязателен. Проверенная связка: Nature’s Spirit
   `2.2.5-1.21.1`, TerraBlender `4.1.0.8`, Architectury `13.0.8`, Cloth Config
   `15.0.140`. Без неё используются vanilla biomes и встроенные профили.
5. Старые config-файлы удалять не требуется. Для проверки создать **новый** мир
   типа `nexus_landscape:nexus_v2`: ранее созданные чанки не перегенерируются.
6. Разрешить cheats либо открыть мир для LAN с cheats; debug-команды требуют
   permission level 2. Обычный игрок доступа не имеет.

Настройки: shaders OFF, vanilla resource pack, Fancy либо Fast без визуальных
worldgen-модов, render distance 16–24, simulation distance 8–12, clear weather,
noon. `F3` открывает debug screen, `F3+G` включает границы chunks, `F2` сохраняет
screenshot. Шейдеры и нестандартные resource packs выключены, чтобы материал,
освещение и швы оценивались без маскировки.

```mcfunction
/gamemode creative
/gamemode spectator
/time set noon
/weather clear
/gamerule doDaylightCycle false
```

Для каждой точки сначала создать отдельный свежий мир с указанным seed. После
телепортации выполнить `/nexuslandscape debug position`, а после кадров —
`/nexuslandscape debug export`.

## Подтверждённые QA-точки

Координаты surface/vegetation/cave взяты из `*.first` runtime telemetry свежих
физически созданных chunks. Nature’s Spirit координаты получены server-side
biome-source scan с реально загруженной версией 2.2.5; эти три biome keys также
были ранее подтверждены physical targeted generation. Dimension везде
`minecraft:overworld`.

| ID | Категория | Seed | Координаты / chunk | Biome | Surface profile | Vegetation profile | Zone | Что проверить | Фактический результат | PASS/FAIL | Скриншот |
|----|-----------|------|--------------------|-------|-----------------|--------------------|------|---------------|-----------------------|-----------|----------|
| S01 | River/channel | -41027 | `-30320 66 -48848`; `-1895,-3053` | forest | surface/forest | vegetation/forest | channel | Читаемое непрямое русло, sediment в пределах influence, без деревьев в центре и seam | | | |
| S02 | Lake shore | 240802 | `-18113 65 -35030`; `-1133,-2190` | plains | surface/plains | vegetation/plains | lake_shore | Берег повторяет basin, не образует одноцветное кольцо и не заменяет воду | | | |
| S03 | Wet bank | 240802 | `-13313 78 -30781`; `-833,-1924` | meadow | surface/meadow | vegetation/meadow | wet_bank | Влажные материалы и более густой ground cover только рядом с водой | | | |
| S04 | Ocean coast/estuary | -41027 | `-30320 66 -48839`; `-1895,-3053` | forest | surface/forest | vegetation/forest | coast | Coast не конфликтует с соседним channel и не поднимается в гору | | | |
| S05 | Volcanic | -41027 | `45999 103 37320`; `2874,2332` | taiga | surface/taiga | vegetation/taiga | volcanic | Связная вулканическая палитра без одиночной пиксельной россыпи | | | |
| S06 | Alpine surface | 240802 | `-10336 106 -32832`; `-646,-2052` | plains | surface/plains | vegetation/plains | alpine | Высотная палитра и читаемая treeline, без alpine в низине | | | |
| S07 | Exposed slope | 240802 | `-13217 71 -30713`; `-827,-1920` | forest | surface/forest | vegetation/forest | exposed_slope | Камень следует склону, нет прямоугольника или вертикальной chunk-полосы | | | |
| S08 | Base | 240802 | `-13408 78 -30800`; `-838,-1925` | snowy_slopes | surface/snowy_slopes | vegetation/snowy_slopes | base | Обычный профиль без ложных coast/alpine/channel материалов | | | |
| V01 | Dense forest | 240802 | `-13392 79 -30784`; `-837,-1924` | meadow | surface/meadow | vegetation/meadow | dense_forest | Плотные, но кластерные деревья; остаются проходы | | | |
| V02 | Woodland | 240802 | `-13262 76 -30783`; `-829,-1924` | forest | surface/forest | vegetation/forest | woodland | Средняя плотность, заметно свободнее dense forest | | | |
| V03 | Clearing | 240802 | `-13354 79 -30799`; `-835,-1925` | meadow | surface/meadow | vegetation/meadow | clearing | Читаемая поляна без ровной окружности/прямоугольника | | | |
| V04 | Open valley | 240802 | `-13234 76 -30751`; `-828,-1922` | meadow | surface/meadow | vegetation/meadow | open_valley | Низкая древесная плотность, проходимая поверхность | | | |
| V05 | Wet lowland | 240802 | `-13314 79 -30779`; `-833,-1924` | meadow | surface/meadow | vegetation/meadow | wet_lowland | Густой ground cover, деревья не стоят в воде | | | |
| V06 | Rocky slope | 240802 | `-13251 70 -30666`; `-829,-1917` | forest | surface/forest | vegetation/forest | rocky_slope | Редкие деревья и rock accents, без висящих блоков | | | |
| V07 | Alpine vegetation | 240802 | `-10319 109 -32816`; `-645,-2051` | plains | surface/plains | vegetation/plains | alpine | Снижение дерева/кустарника выше treeline | | | |
| V08 | Coastal vegetation | -41027 | `-30303 67 -48830`; `-1894,-3052` | forest | surface/forest | vegetation/forest | coastal | Береговой ground cover без деревьев в море/русле | | | |
| C01 | Lush cave | -41027 | `46012 42 37341`; `2875,2333` | lush_caves | surface/dripstone_caves | vegetation/lush_caves | cave/lush | Редкие moss accents; surface vegetation под землёй отсутствует | | | |
| C02 | Dripstone cave | 240802 | `-13382 23 -30773`; `-837,-1924` | dripstone_caves | surface/lush_caves | vegetation/dripstone_caves | cave/dripstone | Редкие допустимые accents, vanilla formations не перекрыты | | | |
| C03 | Deep dark | 240802 | `-13382 -53 -30773`; `-837,-1924` | deep_dark | surface/lush_caves | vegetation/deep_dark | cave/deep_dark | Нет перегрузки sculk и наземных деревьев/цветов | | | |
| C04 | Generic cave fallback | 240802 | `-19271 -53 -32809`; `-1205,-2051` | natures_spirit:coniferous_covert | surface/lush_caves fallback | vegetation/lush_caves fallback | cave/generic | Без наземной grammar; climate fallback подтверждён export | | | |
| N01 | Nature’s Spirit | 240802 | `-19388 80 -33024`; `-1212,-2064` | natures_spirit:coniferous_covert | climate fallback | climate fallback | debug result | Boreal forest appearance; fallback должен соответствовать cold/wet climate | | | |
| N02 | Nature’s Spirit | 240802 | `-19416 80 -32812`; `-1214,-2051` | natures_spirit:alpine_clearings | climate fallback | climate fallback | debug result | Редкая alpine vegetation и открытая поверхность | | | |
| N03 | Nature’s Spirit | 240802 | `-18984 80 -32992`; `-1187,-2062` | natures_spirit:boreal_taiga | climate fallback | climate fallback | debug result | Boreal palette, кластерные хвойные деревья | | | |
| T01 | Surface-profile transition | 240802 | `-13262 76 -30783` → `-13234 76 -30751` | forest → meadow | surface/forest → surface/meadow | vegetation/forest → vegetation/meadow | transition | Естественный переход, без идеально прямой границы | | | |
| X01 | Chunk seam X | 240802 | `-18113/-18112 65 -35030`; `-1133/-1132,-2190` | plains | surface/plains | vegetation/plains | lake edge | F3+G: сравнить обе стороны X mod 16 = 15/0 | | | |
| Z01 | Chunk seam Z | 240802 | `-13392 79 -30785/-30784`; `-837,-1925/-1924` | meadow | surface/meadow | vegetation/meadow | vegetation boundary | F3+G: сравнить обе стороны Z mod 16 = 15/0 | | | |

Всего: **27 QA-сцен**. Для каждой команды телепортации имеют форму
`/tp @s X Y Z`; значения X/Y/Z взяты непосредственно из таблицы.

## Сценарии визуальной приёмки

### River/channel

```mcfunction
/tp @s -30320 66 -48848
```

Русло должно читаться непрерывно; берег не идеально прямой; деревья отсутствуют
в центре; sediment не выходит далеко за influence; соединения не
прямоугольные; на F3+G нет шва.

### Lake shore и wet bank

```mcfunction
/tp @s -18113 65 -35030
/tp @s -13313 78 -30781
```

Lake shore следует форме воды, не является огромным кольцом одного материала и
не выглядит как channel. Wet bank остаётся около воды, имеет умеренно более
густую растительность и плавно растворяется в base.

### Coast, volcanic, alpine и exposed slope

```mcfunction
/tp @s -30320 66 -48839
/tp @s 45999 103 37320
/tp @s -10336 106 -32832
/tp @s -13217 71 -30713
```

Coast должен отличаться от моря/river и не подниматься в высокогорье. Volcanic
палитра связная, без одиночных блоков. Alpine зависит от высоты и разрежает
растительность. Exposed rock следует геометрии склона, а грунт не висит на
отвесной стене. Допустима seed-зависимая мелкая вариативность материалов;
недопустимы прямоугольники, полосы и массовый salt-and-pepper.

### Vegetation provinces

Последовательно посетить V01–V08. Dense forest, woodland, clearing, open
valley, wet lowland, rocky slope, alpine и coastal должны визуально различаться.
Критические дефекты: одинаковая плотность везде, chunk-grid, деревья в воде,
русле или на отвесной скале, висящие trunks/leaves.

### Caves

Перейти в spectator и посетить C01–C04. Lush/dripstone/deep-dark accents должны
быть редкими и соответствовать biome; generic fallback не должен создавать
наземные деревья/цветы. Допустимо отсутствие accent непосредственно в центре
кадра; профиль проверяется debug-командой. Недопустима замена глубоких слоёв или
массовая растительность.

### Nature’s Spirit

Установить всю проверенную optional-связку, создать новый seed `240802`, затем
посетить N01–N03. Все три используют `climate_fallback`, а не фиктивные explicit
mapping. Команда `debug position` должна показать соответствующий biome key и
`natures_spirit=climate_fallback`.

## Chunk seams

```mcfunction
/tp @s -18113 65 -35030
/tp @s -13392 79 -30784
```

Включить `F3+G`. Для X01 соседние chunks `-1133,-2190` и `-1132,-2190`; для
Z01 — `-837,-1925` и `-837,-1924`. Сделать overview вдоль линии и close-up
поперёк неё. FAIL: прямая линия материала, прямоугольное изменение vegetation,
резкая смена zone, повтор рисунка каждый chunk, пропажа/удвоение decoration.

## Determinism

1. Создать новый `nexus_v2` world seed `240802`.
2. Перейти к S02, снять F3 и overview.
3. Удалить мир, создать новый с тем же seed и повторить.
4. Сравнить terrain, water, surface materials, Nexus trees и accents.

При одинаковых версиях, модах и настройках весь результат должен совпасть.
Различие vanilla features не считается допустимым, если состав модов и порядок
datapacks не менялись.

## Debug-команды

- `/nexuslandscape debug position` — локальные профили, zone и influences;
- `/nexuslandscape debug chunk` — координаты текущего chunk и накопленная
  session telemetry без повторного сканирования;
- `/nexuslandscape debug counters` — read-only глобальная telemetry сессии;
- `/nexuslandscape debug reset` — сброс telemetry текущего RandomState;
- `/nexuslandscape debug export` — один ограниченный JSON в
  `<instance>/nexuslandscape-debug/`.

Команды ручные, operator-only, не запускаются каждый tick и не изменяют мир.
В Nether/End position/export безопасно сообщают фактическое dimension; surface
поля являются диагностической аналитикой Overworld generator и не означают, что
Stage 6 применяется к иной dimension.

## Критические условия FAIL

Stage 6 не принимается при crash, registry/ClassNotFound/missing dependency или
datapack error, невозможности создать/повторно открыть мир, неразумном времени
нового chunk/TPS regression, видимом seam, прямоугольных zones, идеально ровных
границах, массовом salt-and-pepper, береговом материале далеко от воды, river на
морском coast, inland/highland coast, low-altitude alpine, деревьях в воде,
channel или на отвесных скалах, наземной/висящей cave vegetation, замене глубоких
слоёв, одинаковом виде большинства biomes/provinces, недетерминированном seed
либо постоянном log spam.

## Что вернуть

Для каждой проверенной сцены заполнить последние три столбца таблицы. Нужны PNG:
overview, close-up материалов, F3, а для X01/Z01 — F3+G. Приложить JSON из
`nexuslandscape-debug` и `latest.log`, если есть FAIL.
