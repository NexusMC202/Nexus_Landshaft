# Nexus Landscape — аудит генерации мира

Дата аудита: 2026-07-30  
Ветка: `agent/nexus-worldgen-foundation`  
Проверенный коммит до аудита: `71a932bf71a2776194733e087b7fe73e56a2c5f7`  
Версия мода: `0.8.0`  
Minecraft / NeoForge / Java: `1.21.1` / `21.1.235` / `21`

## 1. Результат аудита

Текущая версия является работающим техническим фундаментом, но не полной
переработкой Overworld из технического задания.

Подтверждено:

- отдельный world preset `nexus_landscape:nexus`;
- загрузка datapack-реестров и создание мира dedicated server;
- активный адаптированный terrain-граф Tectonic;
- три собственных климатических сигнала Nexus;
- доступность всех 53 ванильных Overworld-биомов;
- 13 зарегистрированных локальных features и 13 biome modifiers;
- базовые Tectonic-пещеры, подземные реки и лавовые тоннели;
- один успешный серверный прогон без строк `ERROR`, far-chunk warnings и
  обнаруженного watchdog/deadlock;
- сборка Java 21.

Не подтверждено и в основном не реализовано:

- отдельная художественная переработка каждого из 53 биомов;
- шесть семантических макрорегионов и geological province system;
- четыре явно различимых семейства гор;
- связная поверхностная гидрология с водоразделами, притоками, слияниями,
  поймами и дельтами;
- полноценные ледниковые и каньонные системы;
- шесть cave provinces, зависящих от поверхностной геологии;
- ecotones с собственными surface и vegetation;
- slope/province/moisture/exposure-aware surface material system;
- автоматическая проверка 20 seed;
- измерение времени генерации чанков, P95 и P99;
- игровые скриншоты без шейдеров и с шейдерами.

Итог: текущий мир корректно загружается и имеет Tectonic-макрорельеф с
локальными Nexus-декорациями. Называть его законченной комплексной
переработкой всего Overworld пока нельзя.

## 2. Область и метод аудита

Изучены:

- все 21 Java-файлы в `src/main/java`;
- `NexusLandscape`, регистрации features и клиентские события;
- все 13 реализации `Feature`;
- `WorldgenSurvey` и `RouteAuditCommand`;
- world preset и `noise_settings/nexus.json`;
- все 143 Nexus density functions и граф их достижимости;
- все Nexus noise definitions;
- 13 configured features, 13 placed features и 13 biome modifiers;
- 13 biome tags;
- `README.md`, `DESIGN.md`, `THIRD_PARTY_NOTICES.md`;
- Gradle-конфигурация и NeoForge metadata;
- последний `survey.txt`, `biome-coverage.txt`, `survey.png` и
  `run/logs/latest.log`.

Метод:

1. Построен граф ссылок от `noise_settings/nexus.json` к density functions.
2. Сопоставлены регистрации Java, configured/placed features, biome modifiers
   и tags.
3. Проверены алгоритмы размещения крупных объектов, радиусы, обращения к
   heightmap и одиночные `setBlock`.
4. Сопоставлены утверждения README/DESIGN с активным кодом и отчётами.
5. Выполнена сборка `.\gradlew.bat build`.

На этапе аудита исходный код генерации не изменялся.

## 3. Что реально реализовано

### 3.1 World preset

`worldgen/world_preset/nexus.json` создаёт:

- Overworld через `minecraft:noise`;
- ванильный `minecraft:multi_noise` biome source;
- Nexus noise settings `nexus_landscape:nexus`;
- ванильные Nether и End.

Положительное свойство: namespace и публичный идентификатор preset уже
стабильны. Недостаток: выбор биомов остаётся ванильным
`minecraft:overworld`; собственной системы geological provinces в biome
source нет.

### 3.2 Активный macro terrain

В проекте 143 density-function JSON:

| Группа | Всего | Достижимы из noise settings |
|---|---:|---:|
| `tectonic/*` | 122 | 119 |
| `climate/*` | 3 | 3 |
| `terrain/*` | 16 | 0 |
| `caves/*` | 2 | 0 |
| **Итого** | **143** | **122** |

Активный `final_density`, continentalness, depth, ridges и большая часть
erosion/terrain поступают из адаптированного Tectonic 3.0.26.

Неактивны:

- все 16 прежних `terrain/*`, включая Nexus river, mountain и archipelago
  functions;
- `caves/entrances.json`;
- `caves/large_chambers.json`;
- три заменённых Tectonic router-файла для temperature, vegetation и erosion.

Следствие: названия старых ресурсов Nexus не являются доказательством того,
что соответствующие системы участвуют в генерации.

### 3.3 Климат

Активны три Nexus-функции:

- `climate/temperature`: shifted noise, множитель удалённости от океанического
  continentalness и охлаждение от `Y=64` до `Y=224` на `0.38`;
- `climate/humidity`: shifted noise с коэффициентом `0.82`, осушение суши до
  `-0.22` и добавка до `0.16` возле нулей ridge-сигнала;
- `climate/erosion`: Tectonic erosion плюс shifted-noise variation с
  коэффициентом `0.5`.

Это реальные крупномасштабные климатические поля. Однако:

- расстояние до океана вычисляется косвенно через continentalness, а не через
  coast-distance field;
- «влажность речных коридоров» использует близость ridge-сигнала к нулю, а не
  расстояние до вычисленной реки или фактической воды;
- нет сезонности, prevailing winds, rain shadow, evaporation или climate
  simulation;
- климат не создаёт отдельные именованные регионы и не управляет геологией.

### 3.4 Биомы и поверхности

Подтверждено достижение 53/53 ванильных Overworld-биомов в
`biome-coverage.txt`.

Это тест доступности biome source, а не тест визуальной переработки.

В проекте нет собственных registry-biome JSON. Используются ванильные биомы,
изменённые общим terrain-графом, монолитным surface rule и отдельными biome
modifiers.

`noise_settings/nexus.json` содержит 2229 строк. В surface rule:

- 24 уникальных block state;
- 28 явно упомянутых биомов;
- 5 `steep` conditions;
- 33 `noise_threshold` conditions;
- 39 biome conditions.

Оставшиеся биомы получают общие или ванильноподобные правила. Отдельных
terrain/surface/vegetation signatures для каждого из 53 биомов нет.

Растительность большинства биомов остаётся ванильной. Nature's Spirit
используется только как необязательный runtime lookup трёх цветов:

- `lotus_flower`;
- `purple_wisteria`;
- `bleeding_heart`.

Полной Nature's Spirit vegetation palette, многоярусных лесов или
биом-специфичных tree pools нет.

### 3.5 Пещеры

Активный Tectonic cave graph включает:

- vanilla/Tectonic cheese caves;
- spaghetti caves;
- cave entrances;
- pillars;
- noodle caves;
- underground-river density;
- lava-tunnel density.

Существующие локальные cave features сохранены:

- Cave Sanctum;
- Spider Nest;
- Deep Dark Rift;
- Rare Flower Grotto.

Это локальные декорации внутри общего cave graph. В коде нет шести cave
provinces и нет выбора cave family по поверхностной geological province.

`caves/large_chambers.json` и `caves/entrances.json` из прежней Nexus-системы
не входят в активный граф.

### 3.6 Локальные features

Зарегистрированы и подключены через biome modifiers:

1. Hot Spring;
2. Cave Sanctum;
3. Floating Island;
4. River Bank;
5. Humid Karst Arch/Cluster;
6. Volcanic Caldera;
7. Coral Atoll;
8. Mycelial Grove;
9. Spider Nest;
10. Deep Dark Rift;
11. Rare Flower Grotto;
12. Mountain Arch;
13. Regional Landmark.

Диапазон rarity filter: от `1/5` чанков для Mycelial Grove до `1/900` для
Floating Island до дополнительных проверок размещения.

Только семь features вызывают `WorldgenSurvey.recordFeature`:

- hot spring;
- floating island;
- coral atoll;
- humid karst cluster;
- volcanic caldera;
- mountain arch;
- regional landmark.

Поэтому раздел `features.placed` не является полным распределением всех 13
features.

### 3.7 Клиентская атмосфера

`NexusClientVisuals` задаёт четыре fog-профиля:

- humid karst;
- volcanic;
- mycelial;
- Deep Dark.

Профиль выбирается по текущему biome tag без пространственного смешивания.
Sodium проверяется только по версии и не используется через его внутренние
API. Это безопасная renderer-independent интеграция, но не shader
integration и не климатическая система воздуха.

### 3.8 Диагностика

`WorldgenSurvey` умеет:

- генерировать полные чанки вокруг одной точки;
- выбирать ближайший целевой биом;
- экспортировать одну совмещённую biome/height PNG-карту;
- считать min/max/mean/standard deviation высоты;
- считать средний перепад на четыре блока;
- считать процент воды;
- перечислять биомы;
- опционально проверять доступность 53 биомов;
- считать семь инструментированных features.

`RouteAuditCommand` считает локально:

- долю рёбер с перепадом до 2 блоков на 4 м;
- долю рёбер с перепадом до 4 блоков на 4 м;
- долю перепадов от 8 блоков;
- средний перепад.

## 4. Что только заявлено или доказано недостаточно

| Заявление | Фактическое состояние |
|---|---|
| Connected trunk/tributary surface rivers | Старые Nexus river density functions неактивны. Поверхностного river graph, flow direction и confluence model нет. |
| Wetter river corridors | Humidity использует нули ridge-сигнала, не вычисленную реку. |
| Six recognizable region families | В DESIGN перечислены шесть художественных семейств, но нет шести семантических region masks и теста их покрытия. |
| Volcanic chains | Есть локальная кальдера и biome tag; отдельной вулканической macro-province density нет. |
| Humid eastern highlands | Есть jungle tag, fog и локальные karst towers; отдельного связного East-Asian macroregion нет. |
| Painted canyon/badlands system | Есть Tectonic/badlands surface terrain и hoodoo feature, но нет проверенной многоуровневой canyon province с river floor. |
| Glacial regions | Есть ванильные snow/ice surface rules, но нет accumulation zones, cirques, glacier tongues, moraines и glacial lakes как связанной системы. |
| Three cave scales / large landmark chambers | Общий Tectonic cave graph присутствует, но отдельная Nexus large-chamber function неактивна; масштабы не измерены. |
| Safe spider territories with Nexus Mobs | Генерируется паутина без спавнера. Вызовов API или registry Nexus Mobs нет. |
| Nature's Spirit integration | Реально разрешаются три optional flower ID. Остальная растительность не интегрирована. |
| Biome-aware landmarks make every region recognizable | Regional Landmark использует несколько широких tag-веток и ограниченный набор форм. Уникальности всех регионов не доказаны. |
| Traversable world | Есть один локальный slope report и команда; нет распределения по 20 seed и разным province families. |
| Coherent archipelagos | Tectonic island signals активны; связность, размеры и частота archipelago отдельно не измерены. |

## 5. Архитектурные ограничения

### 5.1 Нет семантического province layer

Tectonic использует внутренние region selectors (`club`, `diamond`, `heart`,
`spade`), но Nexus не преобразует их в устойчивые семантические провинции:
glacial, volcanic, karst, sedimentary, old mountains и young mountains.

Из-за этого terrain, caves, surfaces и vegetation не могут использовать общий
province ID/field.

### 5.2 Vanilla multi-noise biome source

Biome placement делегирован `minecraft:overworld`. Это хорошо для
совместимости 53 биомов, но ограничивает прямое управление:

- размерами конкретных регионов;
- ecotones;
- обязательным соседством биомов;
- согласованием biome family с geological province;
- исключением biome specks.

### 5.3 Монолитный surface rule

Surface rule хранится внутри одного JSON на 2229 строк. Ванильный формат
noise settings не предоставляет registry-ссылки на независимые surface-rule
файлы. Разделение потребует генератора ресурсов/шаблонов на этапе сборки либо
custom codec, а не простого перемещения JSON.

### 5.4 Features не конфигурируются данными

Все 13 Java features используют `NoneFeatureConfiguration`. Радиусы,
материалы, ограничения и варианты жёстко заданы в Java. Невозможно настроить
их по province или biome family через datapack без изменения кода.

### 5.5 Нет migration strategy

World preset сохраняет тот же ID при изменении density graph. Старые чанки
остаются прежними, новые получают новую форму; возможны резкие границы.
Versioned settings/preset и план миграции отсутствуют.

## 6. Проблемы качества процедурных форм

Требование отказаться от сфер, цилиндров, идеальных кругов и простых
radius/distance loops пока не выполнено.

Примеры:

- Coral Atoll: эллиптическое кольцо по `x²/rx² + z²/rz²`;
- Floating Island: эллипсоид с небольшими sinusoidal perturbations;
- Hot Spring: объединение двух эллипсов;
- Volcanic Caldera: радиальный конус и круглый кратер;
- Mountain Arch: две радиальные башни и параболический мост;
- Regional boulders: sphere/ellipsoid distance test;
- Mycelial Grove: круглая область радиуса 12;
- Cave Sanctum, Deep Dark Rift и Rare Flower Grotto: круговые маски;
- Karst towers: вертикальные эллиптические профили с небольшим warp.

Большинство объектов имеет только primary mass и случайное повреждение.
Полноценных directional fields, многомасштабной эрозии, terrain-conforming
secondary masses и debris transition нет.

Анализ окружения крупных landmarks ограничен:

- Hot Spring проверяет пять высот и вертикальный фундамент;
- Karst проверяет основания отдельных towers;
- Mountain Arch проверяет два основания;
- Atoll проверяет центр и heightmap каждой колонки;
- Caldera не анализирует региональную форму склона;
- Floating Island считает восемь удалённых heightmap-точек.

Следовательно, feature может быть формально размещён в допустимом биоме, но
композиционно не соответствовать окружающему рельефу.

## 7. Проблемы производительности

### 7.1 Неподтверждённая стоимость features

Нет счётчиков:

- проверенных и записанных блоков;
- отказов по каждой причине;
- времени каждого feature;
- P95/P99;
- количества heightmap lookups;
- распределения по seed.

Верхние границы циклов у крупных features существенны:

- Floating Island: до примерно 60 тысяч проверок voxel до внутренних
  отсечений;
- Coral Atoll: сотни колонок с отдельным heightmap lookup и заполнением от
  океанского дна до Y≈64;
- Karst Cluster: несколько towers по `13×13×height`;
- Mountain Arch и Caldera: тысячи одиночных `setBlock`.

Крупные формы строятся Java-циклами, а не density functions.

### 7.2 Риск обращения к далёким чанкам

`FloatingIslandFeature.nearArchipelagoEdge` запрашивает heightmap в восьми
точках на расстоянии 64 блоков, то есть до четырёх чанков от origin. Это
противоречит требованию не обращаться к незагруженным дальним чанкам и
является риском cascading worldgen.

Текущий единичный серверный лог не показал far-chunk warning, но этого
недостаточно для доказательства безопасности всех seed и всех placements.

### 7.3 Повторные вычисления

Atoll, Mycelial Grove, Regional Landmark и Caldera многократно вызывают
`getHeight` внутри циклов. Локального height cache/buffer нет.

### 7.4 Диагностический overhead и отсутствие профиля

`WorldgenSurvey` вызывает `getChunk` для каждой точки четырёхблочной сетки,
повторно обращаясь к одним чанкам. Последний combined run занял примерно
224 секунды от начала survey до завершения, но это время объединяет:

- генерацию обследуемой области;
- проход biome-coverage;
- запись отчётов.

Из него нельзя получить среднее время чанка, P95 или P99.

## 8. Отсутствующие системы

Полностью отсутствуют либо не сформированы как самостоятельная система:

1. geological province registry/field;
2. young alpine mountain family;
3. old eroded mountain family;
4. plateau/table mountain family как управляемая province;
5. volcanic mountain family как macro terrain;
6. glacial accumulation/cirque/tongue/moraine/lake system;
7. surface watershed and downhill flow;
8. tributary graph, confluences, deltas and floodplains;
9. canyon province с многоуровневыми стенами и river floor;
10. estuaries, dunes, lagoons и типология берегов;
11. wetland lowland system с озёрами и сложной shoreline;
12. шесть geological cave provinces;
13. связь cave province с surface province;
14. biome matrix для 53 биомов;
15. индивидуальные vegetation signatures 53 биомов;
16. ecotone fields и переходные surface/vegetation rules;
17. altitude vegetation belts;
18. template/variant system для крупных landmarks;
19. multi-seed test runner;
20. отдельные карты continentalness, erosion, climate, rivers, provinces,
    slopes и cave slices;
21. feature overlap/conflict analysis;
22. chunk-generation profiler;
23. автоматический visual regression report;
24. тесты с реальным Sodium/Iris/shader stack;
25. migration/versioning strategy для существующих миров.

## 9. Состояние диагностики и воспроизводимость

Последний сохранённый тест:

```text
level-seed=240802
level-type=nexus_landscape:nexus
survey target=minecraft:jagged_peaks
survey center=-50000,-50000
survey radius=16 chunks
biome audit range=65536
biome audit step=192
```

Результат survey:

```text
height.min=62
height.max=176
height.mean=75.72
height.standard_deviation=21.84
slope.mean_per_4_blocks=1.61
water.percent=66.53
biomes.unique=9
```

Biome audit:

```text
found=53 expected=53
```

Последний `latest.log`:

```text
ERROR lines=0
far-chunk warnings=0
detected deadlock/watchdog lines=0
WARN lines=11
```

Ограничения доказательства:

- это один seed;
- отчёт не записывает seed, center, radius и environment variables внутрь
  самого `survey.txt`;
- biome coverage проверяет noise-biome samples, а не полную генерацию каждого
  биома;
- не проверены landmarks всех типов;
- нет client screenshots;
- нет автоматического pass/fail threshold для склонов и specks.

Команда сборки:

```powershell
.\gradlew.bat build
```

Результат текущего аудита:

```text
BUILD SUCCESSFUL
test NO-SOURCE
```

`test NO-SOURCE` означает, что unit/integration test suite отсутствует.

## 10. Проверка критериев приёмки

| № | Критерий | Статус | Доказательство / причина |
|---:|---|---|---|
| 1 | 53 биома и документированная переработка | Частично | 53/53 доступны; документации и реализации для каждого биома нет. |
| 2 | Уникальные surface, vegetation, terrain signatures | Не выполнен | Отдельные surface conditions только для 28 биомов; vegetation в основном ванильная. |
| 3 | Минимум 6 макрорегионов | Не доказан | Есть художественный список в DESIGN, но нет семантических masks и coverage test. |
| 4 | Минимум 4 семейства гор | Не выполнен | Активен общий Tectonic terrain; четыре геологически именованных семейства не реализованы. |
| 5 | Хребты на сотни/тысячи блоков | Частично | Tectonic способен создавать длинные ridges; измерение непрерывности отсутствует. |
| 6 | Связные реки и притоки | Не выполнен | Surface river graph отсутствует; старые Nexus river functions неактивны. |
| 7 | Полноценные ледниковые регионы | Не выполнен | Есть snow/ice rules, но нет glacial system. |
| 8 | Полноценные каньонные регионы | Не выполнен | Отдельная canyon province и метрики отсутствуют. |
| 9 | Шесть cave types | Не выполнен | Есть общий Tectonic/vanilla cave graph, не шесть provinces. |
| 10 | Cave types связаны с surface geology | Не выполнен | Общего province field нет. |
| 11 | Нет массовых повторов landmarks | Не доказан | Rarity снижает частоту, но формы имеют мало вариантов; distribution test отсутствует. |
| 12 | Нет идеальных сфер/цилиндров/кругов | Не выполнен | Несколько features используют radius/distance masks. |
| 13 | Пространство для стройки и транспорта | Частично | Один survey даёт slope 1.61/4 блока; multi-seed coverage отсутствует. |
| 14 | 20 seed автоматически проверены | Не выполнен | Есть ручной single-seed survey. |
| 15 | Нет datapack/registry errors | Выполнен для одного прогона | Dedicated server загрузил мир, `ERROR=0`. |
| 16 | Нет cascading worldgen | Не доказан | Один лог чист; floating-island lookup на 64 блоках остаётся риском. |
| 17 | Нет deadlock | Выполнен только для одного прогона | Watchdog/deadlock не обнаружен; стресс-теста нет. |
| 18 | Сборка Java 21 | Выполнен | `BUILD SUCCESSFUL`. |
| 19 | Dedicated server создаёт/загружает мир | Выполнен для одного seed | Мир `nexus-v08-smoke` создан и сохранён. |
| 20 | Производительность измерена | Не выполнен | Нет per-chunk timings, P95/P99 и feature timings. |
| 21 | Реальные screenshots без/с shaders | Не выполнен | Есть диагностическая PNG-карта, не игровой screenshot. |
| 22 | Для референсов указаны реализованные принципы | Не выполнен | Reference-to-system matrix отсутствует. |
| 23 | README содержит только доказанные заявления | Не выполнен | Connected tributaries, region families и cave-scale claims доказаны недостаточно. |

Итого:

- выполнены полностью для ограниченного текущего прогона: 4 критерия;
- выполнены частично или только для одного прогона: 6 критериев;
- не выполнены или не доказаны: 13 критериев.

## 11. Технический долг

### P0 — до расширения генерации

1. Создать `BIOME_MATRIX.md` для всех 53 биомов.
2. Создать `WORLDGEN_ARCHITECTURE_V2.md` с единым signal graph.
3. Определить стабильный geological-province field и правила его использования
   terrain/caves/surfaces/vegetation.
4. Зафиксировать migration/versioning policy для существующих миров.
5. Устранить или ограничить 64-блочные heightmap lookups из feature stage.
6. Добавить воспроизводимые test metadata в каждый survey report.

### P1 — фундамент V2

1. Спроектировать surface hydrology до добавления river decorations.
2. Разделить mountain terrain на четыре измеримых family fields.
3. Спроектировать glacial и canyon macro systems.
4. Связать cave family с geological province.
5. Ввести data-driven configs вместо `NoneFeatureConfiguration`.
6. Определить способ поддерживаемой генерации монолитного surface-rule JSON.

### P2 — качество форм

1. Переписать крупные landmarks на warped masks и multi-mass composition.
2. Добавить terrain-fit scoring до размещения.
3. Добавить debris/erosion transition вокруг landmarks.
4. Увеличить число детерминированных template variants.
5. Добавить overlap budget и regional landmark density budget.

### P3 — тестирование и производительность

1. Автоматизировать минимум 20 seed.
2. Экспортировать требуемые десять карт/срезов.
3. Измерять chunk time, mean, P95, P99.
4. Инструментировать все 13 features.
5. Считать biome specks, biome area, ridge continuity и valley width.
6. Ввести pass/fail thresholds для travel surface.
7. Добавить dedicated-server regression и client screenshot workflow.
8. Тестировать vanilla, Sodium и Sodium+Iris/shader конфигурации отдельно.

## 12. Компромиссы и оставшиеся риски

Текущая архитектура хорошо сохраняет:

- совместимость ванильных биомов;
- data-driven macro density;
- стабильный preset ID;
- отсутствие обязательной зависимости от Tectonic/Lithostitched;
- необязательность Nature's Spirit, Nexus Mobs и Sodium.

Цена этих решений:

- Tectonic определяет большую часть художественного языка macro terrain;
- vanilla multi-noise ограничивает province-aware biome placement;
- features компенсируют отсутствующие системы локальными объектами;
- монолитный surface rule трудно развивать и ревьюить;
- старые Nexus density resources создают ложное впечатление активных систем;
- обновление графа может создавать швы со старыми чанками.

Главный риск следующего этапа — начать добавлять ещё больше локальных features
до появления общего province/hydrology/surface architecture. Это увеличит
стоимость переделки и не приблизит мир к требованиям на уровне регионов.

## 13. Решение по этапу 1

Этап 1 завершает только аудит. Он не подтверждает готовность проекта.

До массовой реализации следует выполнить этапы 2 и 3:

1. сформировать полную biome matrix;
2. зафиксировать V2-архитектуру сигналов и производительности;
3. только затем менять macro terrain, hydrology, surfaces и caves.

