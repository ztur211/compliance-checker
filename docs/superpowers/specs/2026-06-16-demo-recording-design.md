# Demo Recording — Reproducible Full-Tour Video/GIF (Design)

> A tooling/ops spec, not a product feature. Goal: produce a polished, **reproducible** screen
> recording of the compliance-checker's full flow — for the owner's portfolio (MP4) and the repo
> README (`docs/demo.gif`, finishing Plan 7's one unfinished task).

## Goal

One clean, scripted run of the app's **full tour** — AI floor-plan import → compliance check →
rule review — captured to video, exported to:

- **`portfolio.mp4`** — for the portfolio site's Work-tab video card (YouTube-hosted).
- **`docs/demo.gif`** — short (<5 MB), silent loop embedded in the README.

The recording must be **deterministic and free to re-run**: no live API calls at record time, no
manual screen-grab, no flaky waits. The owner runs a script; the script *is* the recording.

## Non-goals

- No new product/domain logic. The engine, check path, and rule model are unchanged.
- No heavier UI redesign (a *light*, reversible polish pass only — see below).
- Not a CI gate. The demo-driver and capture harness run on demand, not in CI.
- Not a hosted/live-deploy task. Records against a local run.

## Background & constraints (from recon)

| Tour segment | Default (no API key) behaviour | Implication |
|---|---|---|
| **Check compliance** | `samples.ts` exists (3 samples); engine runs deterministically on a built-in fallback rule set (`DefaultNzEgressRuleSet`: open path ≤ 18 m, exits ≥ 2). Violations are located on the SVG canvas (offending space `fill #fce8e6 / stroke #c5221f`; egress path = red dashed polyline). | ✅ Solid, free, deterministic. |
| **AI import** | `StubVisionPlanExtractor` (`@Profile("!claude")`) returns a *single box covering the whole image* + warning *"No vision model configured…"*. Real extraction needs `ClaudeVisionPlanExtractor` (`@Profile("claude")`) + `ANTHROPIC_API_KEY` and is **nondeterministic**. | ❌ Stub looks bad; live is non-reproducible. → **replay a captured real extraction.** |
| **Rule review** | Candidates come **only** from a live `LangChain4jRuleExtractor` (`@Profile("!test")`) run; no seed/Flyway data. Fresh DB → `GET /api/admin/rules` is empty → page shows *"No draft rules to review."* | ❌ Empty screen. → **seed captured real candidates.** |

Other facts: backend **requires Postgres** (no H2 profile); check is **async via JobRunr** (frontend
polls `GET /api/checks/{runId}`, completes <1 s for small plans); demo images live in
`app/src/test/resources/import-gold/` (`wealthy-home-sample.jpg` etc.), and hand-authored **gold**
extractions exist beside them (`wealthy-home-sample.gold.json`).

This sandbox cannot run the demo (no Docker/Postgres/browser/ffmpeg). **The owner runs capture +
recording on his own machine**; this repo ships the tooling.

## Approach — "capture the AI once, replay deterministically"

Two phases:

1. **Capture (one-time, owner, costs a few cents).** With `ANTHROPIC_API_KEY` set, a `@Tag("capture")`
   harness calls the *real* Claude paths and writes two committed fixtures of the genuine outputs.
2. **Replay (every run, free, deterministic).** A `demo` Spring profile serves those fixtures with
   zero API calls. The Playwright demo-driver records the tour against the `demo`-profile app.

> **No-cost alternative (noted, not chosen):** the existing `wealthy-home-sample.gold.json` is an
> accurate, hand-verified plan and can stand in for the import fixture honestly ("ground-truth
> sample"), reducing the one-time capture to just the rule candidates. Default plan: capture the real
> Claude extraction; fall back to gold only if the owner prefers to skip the vision call.

## Components

### Backend — the `demo` profile (replay)

- **`DemoVisionPlanExtractor`** (`imports`, `@Profile("demo")`, implements `VisionPlanExtractor`):
  returns the captured `PlanExtraction` deserialized from `app/src/main/resources/demo/extraction.json`,
  keyed to the demo image. Pre-fills `scaleGuess` and door/exit metadata so the import-review canvas
  comes up **mostly complete** (clean, low-click take).
- **Stub profile change:** `StubVisionPlanExtractor` `@Profile("!claude")` → **`@Profile("!claude & !demo")`**
  so exactly one `VisionPlanExtractor` bean is active. (`demo` and `claude` are mutually exclusive; do
  not enable both.)
- **`DemoRuleSeeder`** (`rules`, `@Profile("demo")`, `ApplicationRunner`): on boot, **idempotently**
  inserts one demo `RuleSetEntity` + N **DRAFT** `RuleEntity` rows from `app/src/main/resources/demo/rules.json`
  (skip if a rule set with the known demo name+version already exists). DRAFT status makes them appear
  in `GET /api/admin/rules` for the review tab.

### Backend — capture harness (one-time)

`@Tag("capture")` tests (excluded from CI, mirroring the existing `@Tag` eval harnesses
`VisionPlanExtractorEvalTest` / `GoldSetEvalTest`), run under the `claude` profile with a key:

- **Import:** render `wealthy-home-sample.jpg` via `PlanImageRenderer` → `ClaudeVisionPlanExtractor.extract()`
  → serialize `PlanExtraction` to `app/src/main/resources/demo/extraction.json`.
- **Rules:** feed a small set of real **C/AS2 clause texts** to the live rule extractor
  (`LangChain4jRuleExtractor` / `ExtractionService`) → serialize validated candidates to
  `app/src/main/resources/demo/rules.json`.

The harness writes to `src/main/resources/demo/` so the `demo` profile reads them at runtime. Fixtures
are committed (they are genuine, reproducible-once outputs).

### Frontend — Playwright demo-driver (record)

- New dev dependency `@playwright/test`; `frontend/playwright.config.ts` (headed, fixed viewport e.g.
  1280×800, `video: 'on'`, output to `frontend/demo-artifacts/`).
- **`frontend/e2e/demo.spec.ts`** drives the storyboard with deliberate pacing
  (`page.mouse.move(x, y, { steps: 25 })`, explicit `expect`/wait-for-state, short pauses).
- **`frontend/e2e/overlay.ts`** (injected via `page.addInitScript`): renders an **animated cursor**
  that follows synthetic `mousemove` events, **click ripples** on `mousedown`, and a **caption banner**
  whose text the spec updates per step. *Rationale:* raw Playwright video shows no cursor and reads as
  broken — the injected cursor + captions make it look like a real product walkthrough. Highest-risk
  visual element; validate first.
- Doubles as an end-to-end smoke test of the full tour.

### Export — webm → mp4 + gif

- **`frontend/scripts/export-demo.mjs`** (+ `package.json` scripts `demo:record`, `demo:export`):
  - MP4: `ffmpeg -i in.webm -c:v libx264 -pix_fmt yuv420p -crf 20 portfolio.mp4`.
  - GIF (palette, 2-pass): `fps≈12`, `scale≈900:-1`, tuned to keep `docs/demo.gif` < 5 MB; trim to the
    punchiest ~12–15 s window for the README loop.
- **ffmpeg is a host prerequisite** (documented in the runbook).

### Light UI polish (reversible, no redesign)

CSS-level + minor JSX only; no behaviour change:

- A max-width container, system font stack, consistent spacing; a **title header**
  ("NZ Fire Egress Compliance Checker") + the existing disclaimer line.
- Bigger editor canvas; crisper violation styling (thicker red stroke, subtle fill); a small **legend**
  ("red = violation · dashed = egress path").
- Basic styled buttons + active-tab styling.
- Keep the diff small; update frontend snapshot tests as needed (`npm run test` stays green).

## Storyboard (detailed shot list)

Silent, captioned, ~35–45 s. Booted under the `demo` profile.

1. **AI import** — click the file input ("Import plan (PDF/image):") → select `wealthy-home-sample.jpg`
   → `ImportReviewCanvas` appears with the **replayed real** extraction (rooms/doors/exits + scale
   pre-filled) → click **"Confirm & load into editor"**. Caption: *"Upload a plan → AI extracts spaces,
   doors & exits."*
2. **Check compliance** — set building context so the result is a clear, **honest** fail → click
   **"Check compliance"** → poll resolves → offending space outlines red, red dashed egress path, and
   the violation list renders. Caption: *"One click → located NZBC C/AS2 egress violations."*
3. **Rule review** — click the **"Rule review"** nav button → seeded **real Claude** C/AS2 candidates
   render (parameter · comparator · threshold · citation · source quote) → click **"Approve"** on one.
   Caption: *"Rules are AI-codified from C/AS2 text, then human-approved."*

### Guaranteeing a violation — honestly

Preference order so step 2 always shows a dramatic, located violation **without faking**:

1. Choose building context that *legitimately* trips the fallback rules on the imported plan (e.g.,
   not sprinklered / low escape height, or the plan's open path > 18 m, or < 2 exits).
2. If the captured plan is unavoidably compliant, curate the demo image so its honest extraction
   violates.
3. Last resort: drive a clearly-labelled **sample** ("Single exit" / "Unreachable wing") for step 2.

## Owner runbook

```bash
# 0. prerequisites: Docker, ffmpeg, Node, JDK
docker compose up -d db

# 1. one-time capture (real Claude; a few cents). writes app/src/main/resources/demo/*.json
ANTHROPIC_API_KEY=sk-ant-… ./mvnw -pl app test -Dgroups=capture -Dspring.profiles.active=claude
git add app/src/main/resources/demo/*.json && git commit -m "chore(demo): capture real AI fixtures"

# 2. record (free, deterministic)
./mvnw -pl app -am spring-boot:run -Dspring-boot.run.profiles=demo   # backend :8080 (demo)
cd frontend && npm install && npm run dev                            # frontend :5173
npm run demo:record      # Playwright drives the tour, emits webm
npm run demo:export      # ffmpeg → portfolio.mp4 + docs/demo.gif
```

## File map

**New**
- `app/src/main/java/nz/compliance/app/imports/DemoVisionPlanExtractor.java`
- `app/src/main/java/nz/compliance/app/rules/DemoRuleSeeder.java`
- `app/src/main/resources/demo/extraction.json`, `app/src/main/resources/demo/rules.json` (captured)
- `app/src/test/java/nz/compliance/app/demo/CaptureDemoFixturesTest.java` (`@Tag("capture")`)
- `frontend/playwright.config.ts`, `frontend/e2e/demo.spec.ts`, `frontend/e2e/overlay.ts`
- `frontend/scripts/export-demo.mjs`
- `docs/DEMO.md` (runbook) · `docs/demo.gif` (generated artifact)

**Modified**
- `app/src/main/java/nz/compliance/app/imports/StubVisionPlanExtractor.java` (profile → `!claude & !demo`)
- `frontend/package.json` (`@playwright/test`, `demo:record`, `demo:export`)
- `frontend/src/editor/EditorPage.tsx`, `EditorCanvas.tsx`, + a small styles file (light polish)
- `README.md` (embed `docs/demo.gif`)

## Testing / verification

- `cd frontend && npm run test` green after polish (update snapshots if needed).
- `./mvnw verify` green — demo beans are profile-gated (inactive by default); the capture test is
  `@Tag`-excluded from CI like the eval harnesses.
- `npm run demo:record` produces a non-empty webm; `npm run demo:export` produces `portfolio.mp4` and
  `docs/demo.gif` (< 5 MB); a human eyeballs the result (cursor visible, captions legible, violation
  obvious).

## Definition of done

- `demo` profile replays real captured AI for import + rule review; check is the deterministic engine.
- Re-runnable pipeline committed; `docs/demo.gif` embedded in the README; `portfolio.mp4` produced.
- `docs/DEMO.md` runbook lets the owner re-record in one pass.
- Frontend tests + `./mvnw verify` + CI green; no API calls at record time.

## Risks / open questions

- **Cursor/caption overlay is the riskiest visual piece** — validate the injected-cursor approach in a
  Playwright spike before building the full tour.
- **ffmpeg** must be present on the host (documented).
- **One-time capture** needs key + spend; the gold-JSON fallback removes the vision call if desired.
- **Honesty guard:** force the violation via legitimate context/geometry, never by faking output.
- **Profile exclusivity:** never enable `demo` and `claude` together (would double-bind the extractor).

## Self-review notes

- **Placeholders:** none. `app/src/main/resources/demo/*.json` are generated by the documented capture
  step (an artifact, not a stub); `<key>`/credentials are necessarily environment-specific.
- **Consistency:** profile wiring (`!claude & !demo` / `demo` / `claude`) yields exactly one extractor
  bean per profile set; seeder + demo extractor both gate on `demo`. Storyboard selectors match the
  recon (file input `.pdf,image/*`, "Confirm & load into editor", "Check compliance", "Rule review",
  "Approve").
- **Scope:** single implementation plan — backend replay + capture, frontend driver + overlay + export,
  light polish, README/runbook. No domain logic.
- **Ambiguity:** "make a violation" resolved to the honest preference-order above.
