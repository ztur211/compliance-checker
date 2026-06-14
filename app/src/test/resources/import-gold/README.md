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
| `griswold-house-sketch-1862.jpg` | **messy** hand-drawn sketch | graceful degradation: low confidence, many warnings; faint freehand walls + barely-legible labels |

## Provenance & licences

All from Wikimedia Commons and **public domain**. The two large drawings
(Independence Hall, Griswold) were downscaled to ≤2200 px max width (re-encoded
JPEG); originals are at the source URLs.

- **wealthy-home-sample.jpg** — [Sample Floorplan](https://commons.wikimedia.org/wiki/File:Sample_Floorplan.jpg) by Boereck. **Public domain.**
- **independence-hall-habs.jpg** — [HABS measured drawing, first floor of Independence Hall](https://commons.wikimedia.org/wiki/File:HABS_measured_drawing_of_the_first_floor_of_Independence_Hall.jpg), NPS / Historic American Buildings Survey. **Public domain** (US federal work).
- **schenley-high-school-1916.png** — [Schenley High School, 1916, First Floor Plan](https://commons.wikimedia.org/wiki/File:Schenley_High_School,_1916,_First_Floor_Plan.png), Edward Stotz (1916). **Public domain** (pre-1929 US).
- **griswold-house-sketch-1862.jpg** — [J. N. A. Griswold House, first-floor plan sketch](https://commons.wikimedia.org/wiki/File:John_N._A._Griswold_house_(now_Newport_Art_Museum),_Newport,_Rhode_Island._First_floor_plan._Sketch_LCCN2013648686.jpg), Richard Morris Hunt (1862), Library of Congress (LCCN 2013648686). **Public domain** (PD-old-100-expired — published pre-1931, architect d. 1895).
