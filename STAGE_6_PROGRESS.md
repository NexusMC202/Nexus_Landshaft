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
| Unknown/modded biome fallback | DONE | surface/vegetation catalogs, survey scanner | unknown/no-mod PASS | Nature’s Spirit 2.2.5: 48 keys найдены, 3 биома физически сгенерированы | используется climate fallback, не explicit per-biome mapping |
| Vegetation provinces | DONE | `vegetation/*`, `NexusV2ChunkGenerator` | 53/53, density, exclusions PASS | выполняются после vanilla decoration | итоговая смесь vanilla+Nexus визуально не принята |
| River/slope/treeline exclusions | DONE | `VegetationResolver` | river/slope/height PASS | Nexus trees подавляются масками | vanilla placed features остаются отдельной системой |
| Cave vegetation accents | PARTIAL | `VegetationProvincePass` | наземная растительность под землёй запрещена | lush/dripstone/deep-dark/generic profiles встречены; blocks changed > 0 | shader-free visual QA отсутствует; Stage 7 не начиналась |
| Runtime telemetry | DONE | `SurfaceProvincePass`, `VegetationProvincePass`, `WorldgenSurvey` | snapshot/reset + cache tests PASS | counters привязаны к `RandomState` и сбрасываются на survey | telemetry покрывает Nexus pass, не vanilla decoration |
| Runtime zone coverage | DONE | `STAGE_6_ZONE_COVERAGE.md` | resolver assertions PASS | все 8 зон имеют ненулевые counters на новых chunks | visual acceptance BLOCKED |
| Diagnostic commands | DONE | `Stage6Command` | compile/check PASS | read-only `biome_audit`; CSV export dev-gated | команды требуют запущенный мир с правами |
| Performance investigation | DONE | `Stage6Profiler`, `HydrologyChunkGrid`, performance baseline | regression PASS | bottleneck подтверждён; `Done` 122.743→62.002 s в instrumented comparison | release-JAR/JFR allocations не измерены |
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

Три исходных и дополнительные свежие миры NeoForge 1.21.1 успешно дошли до
`Done`. Сопоставимый instrumented seed `918273645` после исправления:

- `240802`: 47.820 s, targeted open-lake survey;
- `-41027`: 51.704 s, targeted survey;
- `918273645`: `122.743 → 62.002 s`; Nexus surface `42.331 → 7.611 s`.

Финальный confluence case (`-41027`, `-30224,-48783`) подтвердил 23,757
channel columns и нулевой `surface.invalid.river_coast_dominance`. Точный
`RiverWaterPass` на этом экстремальном участке занял 54.986 s и остаётся
зафиксированным performance risk.

На финальном lake run seed `240802`:

```text
surface.columns=43264
surface.blocks_changed=208721
surface.zone.lake_shore=34982
surface.zone.wet_bank=893
vegetation.tree_attempts=164
vegetation.tree_placed=6
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
- Shader-free visual QA и проверка видимых seams остаются `BLOCKED`.
- Cave accents имеют ненулевой runtime, но не проверены визуально.
- Точное allocation/JFR и release-JAR baseline остаются `PARTIAL`.
- Общий arbitration для старых Nexus landmark features не входит в эту
  итерацию; Stage 7 не начинался.
- Все 53 строки BIOME_MATRIX синтетически подключены, но ни одна не переводится
  в visually accepted без трёх игровых наблюдений и screenshots.

## Commits

- `95e20bc` — surface profile architecture;
- `3774bc1` — runtime surface profiles;
- `edff130` — runtime vegetation grammar;
- `92bf3d8` — bounded caches and runtime telemetry.
- `e87d4b7` — Stage 6 audit, documentation and diagnostic commands;
- `cab5f92` — Stage 6 profiler and surface hydrology-grid optimization.

Финальный SHA команды/документации будет добавлен после regression run.
