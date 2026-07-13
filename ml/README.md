# roomtype

Predicts a room's **occupancy type** (the code `engine/facts/OccupantDensity.java` turns into
m²/person) from its label and geometry. Runs at **import time only**, never in the check path: it
pre-fills a field a human then reviews, exactly like the LLM rule codification.

Optional. The Java app builds, tests, and runs with no Python process anywhere: without
`roomtype.url` set, `DisabledRoomTypeClassifier` supplies a no-op and imports proceed unchanged.

## Run

```bash
cd ml
uv sync --extra dev
uv run pytest                 # tests
uv run ruff check .           # lint
uv run uvicorn roomtype.service:app --port 8000
```

Then point the Java app at it:

```bash
ROOMTYPE_URL=http://localhost:8000 ./mvnw -pl app -am spring-boot:run
```

`GET /health` reports `model_loaded: false` until you train one. That is a healthy state, not a
failure: the service falls back to the keyword baseline in `baseline.py`.

## Train

```bash
uv run roomtype-seed          # extract rooms from app/src/test/resources/import-gold into data/rooms.jsonl
# ... now label the occupancy_type column by hand ...
uv run roomtype-train         # baseline vs model scorecard, writes artifacts/roomtype.joblib
```

The seeder writes `occupancy_type: null` for every row and refuses to guess. Labels are ground
truth, and inventing ground truth is the one thing a ground-truth file must never contain.

## State of play

**This is a scaffold with the machine learning deliberately left out.** Two functions raise
`NotImplementedError` on purpose:

- `features.featurise` - which numbers describe a room
- `train.build_model` - what gets fitted to them

They are the exercise. See [`docs/tutorials/02-machine-learning.md`](../docs/tutorials/02-machine-learning.md).

And be aware of the honest state of the data: seeding yields **7 rooms from one residential plan**,
in **pixel** coordinates. That is not enough to train anything, it is the wrong building type for a
commercial-egress product, and pixel areas are not comparable across plans. Section 2 of the
tutorial is about what to do with that, and it is the most important section.

## Layout

| File | What it is |
|---|---|
| `labels.py` | The label space. Changes in lockstep with `OccupantDensity.java`. |
| `schema.py` | Wire contract with the Java side. |
| `geometry.py` | Polygon primitives (area, aspect ratio, compactness). Plumbing, given to you. |
| `baseline.py` | Keyword matcher. The number your model has to beat. |
| `features.py` | **Yours.** |
| `train.py` | Training + the evaluation harness (baseline comparison, confusion matrix, macro-F1). `build_model` is **yours**. |
| `predict.py` | Serving: model if present, baseline otherwise, abstain below a confidence threshold. |
| `service.py` | FastAPI. `POST /classify`, `GET /health`. |
| `seed.py` | Extracts rooms from the import-gold fixtures into an unlabelled dataset. |
