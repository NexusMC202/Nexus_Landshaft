# Stage 6 zone coverage

Статус: **DONE для runtime counters**, визуальный статус `BLOCKED`. Synthetic
resolver tests не считаются runtime evidence.

## Diagnostic coordinates

Seed `240802`, analytical zone scan:

| Zone | Candidate coordinates | Diagnostic evidence | Runtime status |
|---|---|---|---|
| river/channel | `-41027`, `-30224, -48783` | final run: 23,757 channel + 1,759 wet-bank columns; tree river rejects 107 | DONE; canonical active channel has priority |
| lake shore | `240802`, `-18024, -34970` | 34,982 lake + 893 wet-bank columns | DONE |
| wet bank | `240802`, `-13312, -30720` | 5,849 columns | DONE |
| coast | `240802`, `-25600, -32768` | 36,653 columns | DONE |
| volcanic | `-41027`, `46080, 37376` | 28,111 volcanic + 8,753 base columns | DONE |
| alpine | `240802`, `-10240, -32768` | 36,864 columns | DONE |
| exposed slope | `240802`, `-13312, -30720` | 134 columns рядом с wet-bank/base | DONE |
| base | `918273645`, `18704, -6564` | 36,452 columns после coast fix | DONE |

## Runtime assertions

Final seed `918273645` highland run:

```text
surface.invalid.river_coast_dominance=0
surface.invalid.alpine_low_altitude=0
surface.invalid.wet_bank_far_from_water=0
surface.invalid.lake_shore_on_channel=0
surface.invalid.processing_below_minimum=0
```

Final confluence run, seed `-41027`, `X=-30224 Z=-48783`:

```text
surface.columns=36864
surface.zone.channel=23757
surface.zone.wet_bank=1759
surface.zone.coast=4341
surface.zone.base=7007
surface.invalid.river_coast_dominance=0
```

Dominant zone является enum и поэтому river/coast не могут одновременно стать
двумя dominant results. Отдельные influence fields могут пересекаться в
эстуарии, но resolver применяет зафиксированный priority.

## Known defects

- Исправлено: broad `coastWeight` окрашивал высокогорье в coast palette.
- Исправлено: exposed-slope threshold не соответствовал runtime normalization
  и делал зону практически недостижимой.
- Исправлено: volcanic field достигал около 0.52, но resolver envelope требовал
  фактически недостижимое значение.
- Исправлено: широкий river influence ошибочно считался центром канала;
  dominant channel теперь использует canonical river mask.
- Не принято визуально: salt-and-pepper, rectangular transitions и chunk seams
  требуют клиентских кадров из `STAGE_6_VISUAL_QA.md`.
