# Nexus Landscape — Stage 6 progress

Последнее обновление: 2026-07-31
Этап: 6 — Surface and Vegetation Provinces
Статус этапа: **в работе, не завершён**.

## 1. Исходное состояние

Перед изменениями сохранён Stage 5 regression contract:

- sink-star: 0;
- rectangular junctions: 0;
- trunk continuity: 100%;
- downhill centerline: около 99.99%;
- river mask: около 8.25%;
- clean build и GitHub Actions проходили.

Фактическое исходное состояние surface/vegetation описано в
`STAGE_6_AUDIT.md`. Все biome holders присутствуют в vanilla Overworld
multi-noise source, но runtime coverage на трёх seed ещё не доказан.

## 2. Implemented

### Audit и design contract

- добавлен `STAGE_6_AUDIT.md`;
- проверены noise settings, generator chain, biome tags, biome modifiers и
  rarity всех Nexus placed features;
- аудит содержит 53/53 Overworld biome IDs;
- `BIOME_MATRIX.md` расширен обязательными climate, elevation, slope,
  affinity, wetness, optional integration, distinction, implementation и test
  status полями;
- в Matrix D автоматически подтверждены 53 уникальные строки.

### Surface profile architecture

Добавлены immutable, не удерживающие мир объекты:

- `SurfaceProfile`;
- `SurfaceContext`;
- `SurfaceSelection`;
- `SurfaceProfileResolver`;
- `SurfaceProfileCatalog`.

Catalog содержит 53/53 профиля. Каждый профиль имеет:

- уникальный biome/profile ID;
- macro climate и terrain family;
- elevation/slope band;
- top, soil, transition, exposed rock, wet, sediment, coast и alpine palettes;
- вертикальную глубину soil;
- минимум три visual traits.

Resolver использует единый порядок:

`channel → lake shore → wet bank → coast → volcanic → alpine → exposed slope → base`.

Он является чистой функцией и не читает level, chunk или соседние chunks.

## 3. Synthetic verified

Новый Gradle task `stage6SurfaceTest`, подключённый к `check`, проверяет:

- точное множество 53 vanilla Overworld biome IDs;
- наличие structured surface profile для каждого биома;
- уникальность profile IDs;
- отсутствие `vanilla` sentinel вместо профиля;
- минимум три visual traits;
- заполненность всех вертикальных palettes;
- приоритет river/lake/coast/volcanic/alpine/slope зон;
- независимость от порядка запросов;
- многопоточное чтение immutable catalog/resolver.

Последний результат:

```text
SurfaceProfileSelfTest: PASS profiles=53
RegionalFieldMathSelfTest: PASS
BUILD SUCCESSFUL
```

Stage 5 synthetic tests в том же `check`:

```text
hydrology seams=6110 downhillChecks=4211 convergences=156 terminals=2 lakes=2
hydrology physicalBasins openLakes=1 overflowChannels=34
active river network routing=4211 active=2779 share=0.660
terminalMax=3 sinuosity=1.0609 junctionP90=17.03 downhill=PASS
hydrology request-order/thread samples=664
basin seams samples=656 targeted=6 continuity=17 cold/warm/reverse/thread=PASS
```

## 4. Runtime verified

Пока не выполнено для Stage 6:

- profile catalog ещё не подключён к физической записи surface blocks;
- vegetation grammar ещё не реализована;
- `/nexuslandscape biome_audit <radius>` ещё не добавлена;
- `/nexuslandscape survey <radius>` ещё не добавлена;
- coverage/surface/vegetation survey на трёх seed не выполнен;
- decoration mean/p95/max не измерены.

## 5. Visually accepted

Нет. Shader-free F3 screenshots этапа 6 ещё не сняты. Автоматические atlas
maps не будут выдаваться за игровые screenshots.

## 6. Failed / blockers

- Performance contract пока нарушен двумя неограниченными maps в
  `NexusV2HydrologySampler`; исправление обязательно до performance acceptance.
- Nature’s Spirit optional resolver отсутствует.
- Nexus feature overlays не имеют общего arbitration/exclusion field.
- Все 53 biome vegetation profiles пока отсутствуют.
- Реальные surface identities пока не применяются к chunks.

## 7. Следующий последовательный шаг

1. Реализовать immutable vegetation profiles и grammar.
2. Подключить surface context к V2 runtime без чтения соседних chunks.
3. Сохранить приоритет физического `RiverWaterPass` и lake/overflow.
4. После biome-family implementation повторить Stage 5 regressions.

## 8. Изменённые файлы текущей итерации

- `STAGE_6_AUDIT.md`;
- `BIOME_MATRIX.md`;
- `STAGE_6_PROGRESS.md`;
- `build.gradle`;
- `src/main/java/dev/nexusmc/landscape/worldgen/v2/surface/SurfaceProfile.java`;
- `src/main/java/dev/nexusmc/landscape/worldgen/v2/surface/SurfaceContext.java`;
- `src/main/java/dev/nexusmc/landscape/worldgen/v2/surface/SurfaceSelection.java`;
- `src/main/java/dev/nexusmc/landscape/worldgen/v2/surface/SurfaceProfileResolver.java`;
- `src/main/java/dev/nexusmc/landscape/worldgen/v2/surface/SurfaceProfileCatalog.java`;
- `src/test/java/dev/nexusmc/landscape/worldgen/v2/surface/SurfaceProfileSelfTest.java`.

## 9. Commit

Итоговый SHA этапа 6: отсутствует — этап не завершён.
SHA текущей итерации: будет записан после commit.
