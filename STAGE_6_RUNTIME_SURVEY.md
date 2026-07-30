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
