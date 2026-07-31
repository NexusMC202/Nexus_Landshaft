# Stage 6 runtime survey

Дата: 2026-07-31  
Среда: Minecraft 1.21.1, NeoForge, Java 21, dedicated test server, shaders off.

Все перечисленные level names были новыми. Старые chunks для проверки не
использовались. Dedicated server подтвердил фактическую генерацию, но не может
создать F3 screenshots; визуальная приёмка поэтому остаётся заблокированной.

## Fresh-world runs

| Seed | Target / coordinates | Observed biome / terrain | Surface / vegetation | Influences / slope | Runtime result | Defects / status |
|---:|---|---|---|---|---|---|
| `240802` | open lake, `X=-18024 Z=-34970`, radius 4 chunks | `plains` 572, `river` 230, `forest` 222; `sedimentary_lowland`; Y 63–85 | lake-shore/wet-bank physical surface; terrestrial lowland grammar | water 2.44%, mean slope 1.42 per 4 blocks | `Done` 47.820 s; 43,264 surface columns; 208,689 changed blocks; 1,317 ground accents; 18 tree attempts | canonical `river.samples=0` despite river biome; needs survey alignment check |
| `-41027` | targeted region near Stage 5 negative-seed cases | initially surface lookup reported `lush_caves`/`dark_forest`; terrain Y 79–94 | runtime surface and vegetation passes executed | mixed lowland context | `Done` 51.704 s; targeted survey completed | exposed a 3D cave-biome lookup leak into surface profile; fixed by filtering `SUBTERRANEAN` and climate fallback |
| `918273645` | highland survey target | `birch_forest`, `grove`; old highland; Y 117–132 | base highland surface and terrestrial vegetation | no channel/lake/coast in small crop | `Done` 105.720 s; 36,864 surface columns; 110,636 changed blocks; 439 ground accents; 59 tree attempts | runtime correct, but startup time fails performance acceptance; `dripstone_caves` fallback logged once |

## Final seed 240802 counters

```text
surface.columns=43264
surface.blocks_changed=208689
surface.zone.channel=0
surface.zone.lake_shore=34949
surface.zone.wet_bank=867
surface.zone.coast=0
surface.zone.volcanic=0
surface.zone.alpine=0
surface.zone.exposed_slope=0
surface.zone.base=7448
vegetation.chunks=121
vegetation.ground_blocks=1317
vegetation.rock_blocks=9
vegetation.tree_attempts=18
vegetation.cave_accents=0
```

No crash, out-of-bounds exception, cascading generation warning or neighbour
chunk read was observed. Telemetry is reset at survey start and belongs to that
`RandomState`, so counters from separate worlds are not mixed.

## Coverage status

Runtime-confirmed:

- ordinary/forest lowland;
- open-lake shoreline and wet-bank surface;
- negative seed world;
- large long seed;
- highland/grove region;
- biome boundaries in targeted crops;
- optional-mod-absent environment.

Not fully runtime-confirmed in this Stage 6 iteration:

- coast palette with non-zero coast-zone telemetry;
- dry and snowy target scenes in all three seeds;
- non-zero cave accents;
- non-zero channel-zone telemetry in the final lake run;
- all 53 biome profiles in actual generated terrain;
- client-side appearance and F3 screenshots.

## Manual shader-free screenshot checklist

Launch:

```powershell
.\gradlew.bat runClient
```

Create a new `nexus_v2` world for each seed, disable shaders, set render distance
high enough to show the transition, then capture F3:

1. seed `240802`, `-18024 90 -34970`: lake overview, shoreline cross-section,
   wet bank and forest transition;
2. seed `240802`, `-21095 100 -36500`: ocean coast and estuary;
3. seed `240802`, `-27204 160 -524`: mountain source, exposed slope and tree
   line;
4. seed `-41027`, `-30224 100 -48783`: confluence and river exclusion;
5. seed `-41027`, `-28675 100 -36556`: river mouth/coast;
6. seed `918273645`, `18704 170 -6564`: highland profile and sparse vegetation;
7. seed `918273645`, `-4134 100 -35697`: closed basin shoreline.

For every scene capture overview, ground close-up, boundary between profiles,
and F3 with seed/coordinates. These images are still required before visual
acceptance.

## 2026-07-31 performance and zone follow-up

Все записи ниже получены на физически созданных chunks, а не только pure
resolver tests.

| Seed | Coordinates | Biome / terrain | Dominant runtime zone | Surface / vegetation counters | Screenshot | Result / defects |
|---:|---|---|---|---|---|---|
| `918273645` | `18704,-6564` | birch/grove, old highland | base `36,452`; wet bank `3,484` | 39,936 columns; 139,215 blocks; 8/146 trees | BLOCKED | исправлен ложный coast `36,452→0`; `Done=62.002s` |
| `240802` | `-16384,-32768` | jagged/frozen peaks, river corridor | channel `30,157`, base `6,707` | 36,864 columns; 77,878 blocks; river tree rejects 119 | BLOCKED | channel envelope был слишком широк; dominant channel переведён на canonical mask |
| `240802` | `-13312,-30720` | meadow/forest lowland | wet bank `5,849`; slope `134`; base `30,881` | 115,922 blocks; 4/133 trees; 40 cave blocks | BLOCKED | dripstone profile 4,359; impossible counters 0 |
| `240802` | `-25600,-32768` | snowy slopes near coast | coast `36,653`; wet bank `211` | 116,657 blocks; 0/123 trees | BLOCKED | coast runtime confirmed; client view required |
| `240802` | `-10240,-32768` | glacial massif | alpine `36,864` | 105,794 blocks; 5/122 trees | BLOCKED | alpine runtime confirmed; visual treeline pending |
| `-41027` | `46080,37376` | taiga, volcanic influence 0.52 | volcanic `28,111`; base `8,753` | 118,348 blocks; 30/125 trees; 1 cave block | BLOCKED | volcanic normalization fixed and confirmed |
| `240802` | `-18024,-34970` | plains/river/forest lake | lake `34,982`; wet bank `893`; base `7,389` | 43,264 columns; 208,721 blocks; 6/164 trees | BLOCKED | lake final run, all impossible counters 0 |

Агрегированное покрытие:

```text
surface.zone.river > 0
surface.zone.lake = 34982
surface.zone.wet_bank = 5849 (отдельный target)
surface.zone.coast = 36653
surface.zone.volcanic = 28111
surface.zone.alpine = 36864
surface.zone.slope = 134
surface.zone.base = 36452 (highland target)
```

Суммировать эти значения как один run нельзя: telemetry намеренно
snapshot/reset между target worlds.

## Vegetation explanation

Старая метрика `tree_attempts=18` обозначала только дошедшие до placement
попытки и была неоднозначной. Новая telemetry считает полный pipeline:

- lake: 164 candidates, 6 placed, 6 water rejects, 13 river rejects, 124
  density rejects, 15 other;
- wet-bank/slope: 133 candidates, 4 placed, 125 density rejects;
- volcanic: 125 candidates, 30 placed, 77 density rejects, 18 other;
- river target: 124 candidates, 119 river rejects, 0 placed.

Также отдельно считаются ground attempts/placed и province membership:
dense forest, woodland, clearing, open valley, wet lowland, rocky slope,
alpine и coastal.

## Cave runtime

Физически встречены:

- lush profile: 430 probes на volcanic target;
- dripstone profile: 4,359 probes и до 40 changed blocks на wet-bank target;
- deep dark: 9,413 probes на river target;
- generic modded underground fallback: 13,061 probes и 3 changed blocks с
  Nature’s Spirit.

Наземная grammar не применяется к cave profiles. Cave candidate X/Z и
вертикальный offset теперь seed-jittered, чтобы не образовывать регулярную
chunk grid. Визуальный cave QA остаётся `BLOCKED`.

## Nature’s Spirit runtime

Проверена версия `2.2.5-1.21.1` вместе с TerraBlender `4.1.0.8`,
Architectury `13.0.8` и Cloth Config `15.0.140`.

- dedicated server и свежий мир дошли до `Done (30.684s)`;
- ClassNotFoundException и registry errors отсутствуют;
- biome-source scan нашёл 48 `natures_spirit:*` keys;
- физически сгенерированы `coniferous_covert` (750 samples),
  `alpine_clearings` (149) и `boreal_taiga` (71);
- one-time fallback logs также подтверждены для `maple_woodlands` и
  `aspen_forest`;
- выбор профиля: generic climate-aware fallback;
- physical result: 36,864 columns, 110,250 changed surface blocks, 88 ground
  accents, 13,061 generic cave probes.

Nature’s Spirit остаётся необязательным: предыдущие vanilla-only fresh worlds
запускались без этих JAR.
