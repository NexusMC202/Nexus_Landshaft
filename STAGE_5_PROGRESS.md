# Stage 5 progress — landforms and hydrology

Status: **foundation integrated, surface-water pass not accepted yet**.

## Implemented

- Four distinct relief families are now derived from the V2 regional fields:
  young fold mountains, old eroded highlands, dry plateaus, and volcanic belts.
- Glacier mass, canyon incision, coast weight, and a bounded composite
  `landform_offset` are available from the same deterministic field math.
- `landform_offset` is connected to both V2 terrain density paths.
- A custom `nexus_landscape:hydrology_field` density function and three
  seed-dependent hydrology noises are registered.
- The drainage prototype uses canonical jittered watershed nodes. Each node
  selects one strictly lower neighbour, so edges cannot flow uphill and
  multiple tributaries can converge on the same sink or trunk.
- River masks are chunk-order independent and are cached in 2D before the
  vertical density gradient is applied.
- Diagnostics now export landform, glacier, canyon, river mask, river order,
  and river water-level atlases.

## Verification

- Synthetic regional coverage: 9 provinces, 8 moods, 6 active landform
  channels.
- Dramatic rhythm share: 11.37%, below the 35% composition ceiling.
- Hydrology: 6,110 chunk-boundary continuity checks, 4,063 strictly downhill
  drainage edges, and 201 convergent watershed nodes.
- JSON validation: all resource JSON files parse.
- A fresh NeoForge world `nexus-v2-stage5-smoke-c` reached `Done` in 25.057 s,
  completed the survey, saved all dimensions, and emitted no registry,
  datapack, cascading-worldgen, exception, or deadlock errors.
- The 49,152 × 49,152 block atlas reports a river-mask coverage of 5.81%.

## Not accepted as complete

The smoke-test center is a high mountain area (surface Y 196–261) and contains
no surface water. The graph is deterministic and connected locally, but its
water-level model is not yet coupled to the actual terrain envelope. Therefore
Stage 5 must not be called complete until:

1. river elevation follows sampled macro terrain while remaining downhill;
2. sinks become bounded lakes or receive a deterministic overflow outlet;
3. a chunk-local water/sediment pass fills accepted channels without neighbour
   reads or floating water;
4. several fresh seeds show visible rivers in lowlands and sensible headwaters
   in mountains;
5. the watershed atlas loses remaining lattice-like short segments.

The current code is a working, crash-free foundation for that calibration, not
the final visual result.
