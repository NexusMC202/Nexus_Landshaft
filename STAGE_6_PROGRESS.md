# Nexus Landscape — Stage 6 progress

Последнее обновление: 2026-07-31
Этап: 6 — Surface and Vegetation Provinces
Статус этапа: **PARTIAL — runtime подключён, визуальная приёмка не выполнена**.

## Status table

| Задача | Статус | Файлы | Тесты | Runtime status | Известные ограничения |
|---|---|---|---|---|---|
| Audit 53 biomes | DONE | `STAGE_6_AUDIT.md`, `BIOME_MATRIX.md` | 53/53 catalog coverage | все профили доступны resolver | не все 53 встречены в runtime survey |
| Runtime surface resolver | DONE | `surface/*`, `NexusV2ChunkGenerator` | determinism, priority, seams, threads PASS | физически меняет новые chunks до `RiverWaterPass` | визуально не принят |
| Smooth material transitions | DONE | `SurfaceNoise`, `SurfaceProfileResolver` | boundary/seed/order PASS | coordinate noise, masks и dither работают | atlas не заменяет игровой осмотр |
| Unknown/modded biome fallback | PARTIAL | surface/vegetation catalogs | unknown/no-mod PASS | climate-aware fallback логируется один раз | Nature’s Spirit не установлен в runtime |
| Vegetation provinces | DONE | `vegetation/*`, `NexusV2ChunkGenerator` | 53/53, density, exclusions PASS | выполняются после vanilla decoration | итоговая смесь vanilla+Nexus визуально не принята |
| River/slope/treeline exclusions | DONE | `VegetationResolver` | river/slope/height PASS | Nexus trees подавляются масками | vanilla placed features остаются отдельной системой |
| Cave vegetation accents | PARTIAL | `VegetationProvincePass` | наземная растительность под землёй запрещена | код подключён, fresh runs без ненулевых cave accents | Stage 7 cave geometry не начиналась |
| Runtime telemetry | DONE | `SurfaceProvincePass`, `VegetationProvincePass`, `WorldgenSurvey` | snapshot/reset + cache tests PASS | counters привязаны к `RandomState` и сбрасываются на survey | telemetry покрывает Nexus pass, не vanilla decoration |
| Three fresh seeds | PARTIAL | `STAGE_6_RUNTIME_SURVEY.md` | server startup/survey PASS | `240802`, `-41027`, `918273645` созданы заново | полный набор из 10 сцен на каждом seed не покрыт |
| Diagnostic commands | DONE | `Stage6Command` | compile/check PASS | read-only `biome_audit`; CSV export dev-gated | команды требуют запущенный мир с правами |
| Performance acceptance | PARTIAL | bounded cache + architecture docs | cache concurrency/capacity PASS | 47.8–105.7 s до `Done` в измеренных fresh worlds | большой seed показал неприемлемые 105.7 s; профилирование JVM не выполнено |
| Shader-free F3 screenshots | BLOCKED | — | — | dedicated server не имеет UI | нужен `runClient` и ручной захват |

## Implemented

- `SurfaceProvincePass` вызывается после vanilla/Tectonic surface rules и до
  `RiverWaterPass`. Поэтому Stage 5 остаётся последним владельцем русел, озёр и
  overflow.
- Immutable `SurfaceContext` объединяет seed, абсолютные координаты, biome,
  climate router, province, height/slope, river/lake/coast и специальные
  regional influences.
- 53 surface profiles и 53 vegetation profiles реально участвуют в выборе
  материалов и растительности новых chunks.
- `VegetationProvincePass` вызывается после vanilla biome decoration и
  добавляет детерминированные ground accents, камни, деревья и ограниченные
  cave accents.
- Hydrology caches имеют строгую вместимость; survey counters можно явно
  snapshot/reset для конкретного generation run.
- Добавлены `/nexuslandscape biome_audit <radius>` и отключённый по умолчанию
  `/nexuslandscape survey <radius>`. CSV export включается только переменной
  `NEXUS_LANDSCAPE_DEV_COMMANDS=1`.

## Synthetic verified

`check` включает Stage 4/5 regression и Stage 6 surface/vegetation self-tests:

- 53/53 профиля, отсутствие `null`;
- unknown biome и запуск без Nature’s Spirit;
- seed determinism, different-seed variation, reverse query order и parallel;
- x/z 15/16 seam probes;
- приоритет channel/lake/coast/volcanic/alpine/slope/base;
- распределение материалов и допустимая vegetation density;
- запрет terrestrial vegetation в cave context;
- снижение деревьев на склонах, выше tree line и в центре рек;
- bounded-cache capacity, clear и concurrency.

## Runtime verified

Три свежих мира NeoForge 1.21.1 успешно дошли до `Done`:

- `240802`: 47.820 s, targeted open-lake survey;
- `-41027`: 51.704 s, targeted survey;
- `918273645`: 105.720 s, targeted highland survey.

На финальном lake run seed `240802`:

```text
surface.columns=43264
surface.blocks_changed=208689
surface.zone.lake_shore=34949
surface.zone.wet_bank=867
vegetation.chunks=121
vegetation.ground_blocks=1317
vegetation.rock_blocks=9
vegetation.tree_attempts=18
```

`neighbour reads`, cascading/far-chunk warnings и worldgen crash в логе не
обнаружены. Подробности и координаты находятся в
`STAGE_6_RUNTIME_SURVEY.md`.

## Visually accepted

Нет. Игровые shader-free F3 screenshots не созданы: доступный dedicated-server
workflow не имеет клиентского окна. PNG atlas являются только диагностикой.

## Failed / open

- В targeted lake crop биомы `plains`, `river`, `forest` найдены, но canonical
  `river.samples=0`; согласование hydrology survey с реальным river biome
  требует отдельной проверки Stage 5/6.
- Cave accents остались нулевыми в измеренных runtime regions.
- Время создания spawn area на seed `918273645` — 105.720 s; performance
  acceptance не пройдена.
- Общий arbitration для старых Nexus landmark features не входит в эту
  итерацию; Stage 7 не начинался.
- Все 53 строки BIOME_MATRIX синтетически подключены, но ни одна не переводится
  в visually accepted без трёх игровых наблюдений и screenshots.

## Commits

- `95e20bc` — surface profile architecture;
- `3774bc1` — runtime surface profiles;
- `edff130` — runtime vegetation grammar;
- `92bf3d8` — bounded caches and runtime telemetry.

Финальный SHA команды/документации будет добавлен после regression run.
