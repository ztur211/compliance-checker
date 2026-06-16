# Recording the demo

Reproducible full-tour recording (AI import → compliance check → rule review). The AI parts are
replayed deterministically by the `demo` Spring profile, so recording needs **no API key**.

## Prerequisites
- Docker (for Postgres), JDK 21, Node, and **ffmpeg** on PATH.

## One-time: capture real Claude fixtures (optional but recommended for authenticity)
The repo ships sample fixtures so the demo works immediately. To replace them with genuine Claude
outputs (a few cents):
```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw -pl app test -Dtest=CaptureDemoFixturesTest -DexcludedGroups=
git add app/src/main/resources/demo/*.json && git commit -m "chore(demo): capture real AI fixtures"
```
Skip this to use the committed sample candidates + the proportional fallback plan (honest as
"sample data"). The existing `app/src/test/resources/import-gold/wealthy-home-sample.gold.json` is a
no-cost, hand-verified alternative for the import fixture if you prefer not to call the vision model.

## Record
```bash
docker compose up -d db
./mvnw -pl app -am spring-boot:run -Dspring-boot.run.profiles=demo   # backend :8080 (demo)
cd frontend && npm install && npx playwright install chromium        # first time only
npm run dev                                                          # frontend :5173 (separate shell)
npm run demo:record                                                 # drives the tour -> demo-artifacts/*.webm
npm run demo:export                                                 # -> portfolio.mp4 + docs/demo.gif
```

## Use
- `frontend/portfolio.mp4` → upload to YouTube for the portfolio Work-tab card.
- `docs/demo.gif` → committed; shows in the README.

## Tuning
- Pacing: `slowMo` and `waitForTimeout` in `frontend/e2e/demo.spec.ts`.
- GIF size: fps/scale in `frontend/scripts/export-demo.mjs` (target < 5 MB).
- Guaranteed violation: see the notes in `demo.spec.ts` step 3 / `DemoVisionPlanExtractor.fallback`.
