# Gold fixtures for the vision-extraction eval

These floor plans feed `VisionPlanExtractorEvalTest` (`@Tag("eval")`, excluded
from CI via `excludedGroups`). Run them against the live model with:

```bash
ANTHROPIC_API_KEY=… ./mvnw -pl app test \
  -Dtest=VisionPlanExtractorEvalTest -Dgroups=eval -Dspring.profiles.active=claude
```

The harness currently **prints metrics only** (rooms/doors/scale counts) — it
does not yet score against ground truth. Adding labelled expectations + an
IoU/recall check is a follow-up (see the "shape ≠ accuracy" note in the import
backlog). Each plan below was chosen to stress a different failure mode; the
"expected" column is what an Opus-4.8 proxy produced (no key) and is a guide,
not an assertion.

| File | Stresses | Expected (proxy) |
|------|----------|------------------|
| `wealthy-home-sample.jpg` | easy baseline (orthogonal house, no scale bar) | ~7 rooms, scale **null**, windows ≠ doors |
| `independence-hall-habs.jpg` | a **graphic scale bar** present | scale **derived** (`source:"scale-bar"`, ~0.0162 m/px), not null |
| `schenley-high-school-1916.png` | **dense** institutional plan (~24 rooms) | coherent at scale; ~2k output tokens — under the cap, but the closest to the truncation regression |
| `bedroom-hand-drawn.jpg` | **messy** hand-drawn sketch | graceful degradation: low confidence, many warnings, furniture **not** emitted as rooms |

## Provenance & licences

All from Wikimedia Commons. The two large drawings were downscaled to 2000 px
max width (re-encoded JPEG); originals are at the source URLs.

- **wealthy-home-sample.jpg** — [Sample Floorplan](https://commons.wikimedia.org/wiki/File:Sample_Floorplan.jpg) by Boereck. **Public domain.**
- **independence-hall-habs.jpg** — [HABS measured drawing, first floor of Independence Hall](https://commons.wikimedia.org/wiki/File:HABS_measured_drawing_of_the_first_floor_of_Independence_Hall.jpg), NPS / Historic American Buildings Survey. **Public domain** (US federal work).
- **schenley-high-school-1916.png** — [Schenley High School, 1916, First Floor Plan](https://commons.wikimedia.org/wiki/File:Schenley_High_School,_1916,_First_Floor_Plan.png), Edward Stotz (1916). **Public domain** (pre-1929 US).
- **bedroom-hand-drawn.jpg** — [BedroomHandDrawnPlan](https://commons.wikimedia.org/wiki/File:BedroomHandDrawnPlan.jpg) by **CyberThing**. **CC BY-SA 3.0** / GFDL 1.2+.
  ⚠️ This is the **only non-public-domain** fixture. Share-alike: keeping it
  requires crediting CyberThing (done here). If you'd rather the fixture set be
  uniformly PD, swap it for a public-domain hand-drawn/sketch plan.
