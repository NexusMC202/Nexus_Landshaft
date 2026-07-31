# Stage 6 performance baseline

Дата измерения: 2026-07-31  
Среда: Windows, Java 21.0.11 HotSpot, NeoForge 21.1.235, Minecraft 1.21.1,
dev dedicated-server без debugger, view distance 2, simulation distance 2,
structures disabled. Profiler включался через `NEXUS_LANDSCAPE_PROFILE=1`.

## Метод

`Stage6Profiler` собирает агрегированные измерения без логирования на колонку:

- calls, total, average, p50 bucket, p95 bucket и maximum;
- vanilla surface, Nexus surface и RiverWaterPass;
- vanilla placed features и Nexus vegetation;
- noise sampling, biome lookup, context preparation и block replacement;
- vegetation sampling/placement и cave accents.

Счётчики привязаны к одному process run и явно сбрасываются перед targeted
survey. P50/P95 являются верхними границами логарифмических buckets, а не
полным allocation profiler.

Сценарий: seed `918273645`, новый мир, targeted region около
`X=18704 Z=-6564`, radius 3 chunks. В измеренный Stage 6 snapshot вошли 156
surface chunks, 110 decorated chunks и 39,936 surface columns.

## До оптимизации

Cold fresh-world:

- JVM/Gradle launch до `No existing world data`: примерно 26.0 s;
- datapack/recipe/world setup до `Preparing level`: 2.2 s;
- `Preparing level` до `Preparing start region`: 64.8 s;
- spawn preparation: 57.883 s;
- Minecraft `Done`: 122.743 s;
- targeted удалённая генерация/survey после `Done`: 65.853 s;
- save overworld: около 4.1 s.

Измеренные generation phases:

| Phase | Calls | Total | Average |
|---|---:|---:|---:|
| Nexus surface | 156 | 42.331 s | 271.351 ms/chunk |
| surface context preparation | 39,936 | 39.975 s | 1.001 ms/column |
| surface noise grid | 156 | 2.081 s | 13.340 ms/chunk |
| surface biome lookup | 39,936 | 0.079 s | 0.002 ms/column |
| surface block replacement | 39,936 | 0.147 s | 0.0037 ms/column |
| RiverWaterPass | 156 | 4.037 s | 25.880 ms/chunk |
| Nexus vegetation | 110 | 2.471 s | 22.463 ms/chunk |
| vanilla surface | 156 | 1.896 s | 12.152 ms/chunk |
| vanilla placed features | 110 | 1.639 s | 14.897 ms/chunk |

Подтверждённое узкое место: два полных `HydrologyMath` запроса (`sample` и
`basinSample`) для каждой из 256 колонок surface chunk. Biome lookup, registry
lookup и физическая запись блоков bottleneck не являлись.

## Исправление

`HydrologyChunkGrid` вычисляет для Stage 6 surface pass сетку 6×6 с шагом 4
блока и билинейно интерполирует только непрерывные influence-поля. Discrete
order/IDs/reason берутся из ближайшей canonical sample. Физический
`RiverWaterPass` не изменён и продолжает использовать точную Stage 5
гидрологию.

Отдельный тяжёлый confluence case (`seed=-41027`, `X=-30224 Z=-48783`) после
исправления приоритета русла показал 54.986 s для 144 вызовов
`RiverWaterPass` (381.850 ms/chunk). Это не регрессия оптимизированной Stage 6
surface grid: точный физический проход Stage 5 намеренно не был заменён
интерполяцией. Случай зафиксирован как оставшийся performance risk.

Grid не применяется в vegetation pass: там точечных выборок мало, и
предварительное заполнение сетки оказалось дороже.

Дополнительно исправлен ложный coast material на высокогорье: broad regional
coast field теперь ограничен sea-level envelope.

## После оптимизации

Повторный fresh-world того же seed и target:

- `Preparing level` → `Preparing start region`: 31.629 s;
- spawn preparation: 30.344 s;
- Minecraft `Done`: **62.002 s**;
- targeted удалённая генерация/survey: 29.099 s;
- save overworld: около 1.4 s.

| Phase | Calls | Total | Average | Max |
|---|---:|---:|---:|---:|
| Nexus surface | 156 | 7.611 s | 48.790 ms | 84.394 ms |
| surface context preparation | 39,936 | 0.409 s | 0.0102 ms | 4.876 ms |
| surface noise grid | 156 | 1.965 s | 12.594 ms | 27.415 ms |
| surface biome lookup | 39,936 | 0.020 s | 0.0005 ms | 1.306 ms |
| surface block replacement | 39,936 | 0.033 s | 0.0008 ms | 5.706 ms |
| RiverWaterPass | 156 | 3.663 s | 23.483 ms | 36.999 ms |
| Nexus vegetation | 110 | 2.124 s | 19.313 ms | 32.439 ms |
| vanilla surface | 156 | 1.889 s | 12.107 ms | 25.696 ms |
| vanilla placed features | 110 | 1.493 s | 13.570 ms | 72.433 ms |

Nexus surface уменьшился на 82.0%, context preparation — на 99.0%, а `Done`
для сопоставимых fresh worlds — на 49.5%.

Среди перечисленных измеренных generation phases Nexus surface занимает около
45.4%, Nexus vegetation — 12.7%. Эти доли нельзя приравнивать к проценту всего
Minecraft wall-clock: density generation, structures/status pipeline и spawn
поиск находятся вне переопределённых hooks.

## Allocation estimate

JFR/async-profiler в этой среде не запускался, поэтому точного bytes/column нет.
Статический аудит подтверждает:

- один immutable `SurfaceContext` и один `SurfaceSelection` на surface column;
- один biome lookup на surface column;
- registry lookup отсутствует во внутреннем цикле (`MATERIALS` immutable);
- block loop использует один `MutableBlockPos`;
- 18×18 climate/height arrays создаются один раз на chunk;
- 6×6 hydrology grid создаётся один раз на surface chunk;
- строки и collections во внутреннем block replacement loop не создаются.

Оставшийся основной Stage 6 cost — подготовка grid/noise на chunk, а не
перебор заменяемых блоков.

## Статус

`PARTIAL`: серьёзный подтверждённый bottleneck исправлен и повторно измерен.
Для release JAR без Gradle dev launcher и точного allocation/JFR профиля нужны
дополнительные измерения. Также требуется отдельная оптимизация точного
`RiverWaterPass` на тяжёлых слияниях без ухудшения Stage 5 seam/physical-water
инвариантов.
