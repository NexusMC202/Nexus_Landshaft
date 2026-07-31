# Stage 6 visual QA

Статус: **BLOCKED**.

Codex выполнил dedicated-server runtime generation, но в доступном workflow нет
управляемого Minecraft client window, поэтому shader-free F3 screenshots не
выдаются за выполненные. Диагностические PNG не являются игровыми
скриншотами.

## Запуск

```powershell
.\gradlew.bat runClient
```

Настройки:

- создать новый world type `nexus_landscape:nexus_v2`;
- shaders OFF;
- graphics Fast или Fancy, без Distant Horizons;
- F3 включён;
- render distance 12–16;
- spectator mode для cave/overview;
- проверять только новые chunks.

## Обязательные кадры

Для каждого кадра сохранить seed, XYZ, biome, surface profile, dominant zone и
границы chunk из F3+G.

| Scene | Seed / coordinates | Ожидаемая проверка |
|---|---|---|
| River top/bank | `240802`, `-16384 100 -32768` | channel sediment, свободный центр русла, без прямоугольной маски |
| Lake shore | `240802`, `-18024 90 -34970` | lake palette отличается от river, плавный shoreline |
| Wet bank | `240802`, `-13312 100 -30720` | влажный материал только возле воды |
| Coast | `240802`, `-25600 90 -32768` | морской берег около sea level, без inland coast |
| Highland/base | `918273645`, `18704 180 -6564` | исправленный highland без coast palette |
| Alpine | `240802`, `-10240 150 -32768` | высотная/ледниковая палитра и снижение деревьев |
| Mountain slope | `240802`, координата из `STAGE_6_ZONE_COVERAGE.md` | exposed rock без сплошной пестроты |
| Forest core | `240802`, `-18024 90 -34970` рядом с forest | кластерная, не сеточная плотность |
| Clearing | тот же forest region | видимая поляна внутри общего лесного региона |
| Dry biome | найти `/locate biome minecraft:desert` | dry palette и редкая vegetation |
| Snow biome | найти `/locate biome minecraft:snowy_slopes` | snow/alpine belts |
| Swamp | найти `/locate biome minecraft:swamp` | wet-lowland grammar без деревьев в воде |
| Profile boundary | lake target, plains/forest boundary | отсутствие идеально прямой границы |
| Chunk X seam | F3+G, пересечь local X 15/16 | отсутствие полосы материалов |
| Chunk Z seam | F3+G, пересечь local Z 15/16 | отсутствие полосы материалов |
| Lush caves | `/locate biome minecraft:lush_caves` | moss accents, vanilla cave не перекрыта |
| Dripstone caves | `/locate biome minecraft:dripstone_caves` | редкие stone accents |
| Deep dark | `/locate biome minecraft:deep_dark` | отсутствие перегрузки sculk |
| Unknown/modded fallback | мир с любым дополнительным biome mod | один fallback log, climate-appropriate surface |
| Nature’s Spirit | профиль с установленным Nature’s Spirit | запуск без registry/ClassNotFound ошибок |

## Что вернуть для анализа

Нужны PNG без shaders: общий вид, close-up материала, F3, F3+G chunk boundary и
по одному поперечному виду river/lake/coast. После получения изображений можно
перевести visual status из `BLOCKED` в `DONE` либо оформить конкретные defects.
