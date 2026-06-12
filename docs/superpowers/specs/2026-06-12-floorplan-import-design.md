# compliance-checker — Design Spec: Floor-Plan Import (vision-assisted)

- **Date:** 2026-06-12
- **Status:** Draft for review
- **Depends on:** v1 (NZ fire egress). The engine, 2D editor, async check flow, and the LLM rule-codification pipeline are built, tested, and green (`v1.0.0-nz-egress`).
- **Context:** v1 only lets a user **draw** a plan in the browser. This spec adds an **ingestion layer** that turns an uploaded plan image into the engine's existing `GeometryDoc`, using multimodal Claude for perception plus a mandatory human-confirmation step. The LLM stays **out of** the deterministic check path.

---

## 1. Summary

A user uploads a **PDF or image** of a single-storey floor plan. The app renders it to an image; **multimodal Claude** extracts a best-guess plan — rooms, doors, exit/occupancy guesses, and a scale guess — in **pixel** coordinates. The user lands in the **existing editor** with the uploaded image pinned as a **backdrop** and the draft overlaid, corrects it against the real drawing, sets/verifies **scale**, and confirms. The result is a normal `GeometryDoc` that flows through the **existing** save → check path, unchanged.

This is the **second application** of the project's core pattern: as with rule codification, the **LLM drafts**, a **human approves**, and the **deterministic engine** runs only on the confirmed result.

---

## 2. Goals / Non-goals

**Goals**
- Turn an uploaded PDF/image of a plan into the engine's `GeometryDoc` with far less effort than drawing from scratch.
- A genuine "wow" first impression: drop a picture, watch it become a recognised plan.
- **Separation of input from analysis** — users (and Claude) supply *building geometry only* (rooms, doors, and which doors are final exits); the egress route and its compliance are *always computed by the engine* (graph + Dijkstra), never drawn or annotated by the user. Import must never degrade into manual egress-route annotation.
- Keep the LLM in the **ingestion** path only; the check stays pure and deterministic.
- Reuse existing patterns: the extractor **interface seam**, the lazily-built Claude client, the `validationErrors()` gate, the `@Tag("eval")` harness, and the editor canvas.
- **Degrade gracefully** to "trace over a backdrop" when the AI is unavailable or unsure.

**Non-goals**
- Fully-automatic, no-review checking (unsafe for a life-safety tool).
- Bespoke computer vision (Claude does the perception).
- Native DWG / DXF / IFC **vector** parsing (separate roadmap; DWG users export to PDF).
- Multi-page / multi-storey plans.

---

## 3. Scope

**IN (v1 of import):**
- Single page / **single storey** (matches the engine's v1 scope).
- **PDF (first page) + PNG / JPG.**
- **Claude vision** extraction (reuse the Claude / LangChain4j stack).
- **Backdrop-assisted** correction in the existing editor.
- **Auto-scale-detect, else manual calibration.**
- Graceful degradation to manual tracing over the backdrop.
- An optional vision **eval harness** (metric, not a CI gate).

**OUT (roadmap, named to prevent scope creep):**
- Native **DWG / DXF / IFC vector** ingestion — a *separate* pipeline (exact geometry + real units, higher fidelity for CAD users, but a different build). DWG users **export to PDF** in the meantime, which import already handles.
- Multi-page / multi-storey.
- Batch import.
- Fully-automatic, no-review checking.
- Persisting the source image + extraction as plan **provenance** (optional nicety; see §12).
- Any model training / fine-tuning.

---

## 4. Architecture

Import is an **additive ingestion layer in front of** the engine. It does not modify the engine, the rules, or the check.

```
NEW ingestion layer
  upload (PDF / PNG / JPG)
    → render to image            (PDFBox — already a dependency)
    → Claude vision extraction   (draft rooms + doors + exit/occupancy guesses + scale guess)
    → ImportDraft in PIXELS + scale guess
    → editor: image backdrop + draft overlay + calibrate
    → user confirms / corrects
  ─────────────────────────────────────────────
    → GeometryDoc (in METRES)
    → EXISTING saveFloorPlan → validationErrors() → async check   (unchanged)
```

**Central principle (preserve and state in the build):** the LLM lives in the **ingestion** path, *never* the **check** path. It drafts; a human confirms; the deterministic engine runs only on the **confirmed** geometry — the identical human-in-the-loop pattern already used for rule codification. The runtime check remains pure, reproducible, and LLM-free.

---

## 5. Components

Each unit is small, single-purpose, and mirrors an existing pattern.

**Backend (`app`)**
- **`ImportController`** — `POST /api/imports` (multipart file) → returns an `ImportDraft`. Orchestrates render → extract → assemble. Does **not** persist a `FloorPlan`; nothing is saved until the user confirms via the existing save endpoint.
- **`PlanImageRenderer`** — renders a PDF's first page to PNG via **PDFBox `PDFRenderer`** (PDFBox 3.x already in deps); passes PNG/JPG through. Returns the normalised image bytes + pixel dimensions.
- **`VisionPlanExtractor`** — **interface seam**, exactly like `RuleExtractor`. Real impl **`ClaudeVisionPlanExtractor`** behind `@Profile("!test")`, **lazily built** (the app boots with no `ANTHROPIC_API_KEY`), **stubbed in tests**. Sends the image to Claude via **LangChain4j** with a structured-output schema and returns a `PlanExtraction`.
- **`ImportDraftAssembler`** — converts the pixel-space `PlanExtraction` into an `ImportDraft` (draft geometry kept in **pixels**, plus scale guess, confidences, warnings). Pure and deterministic → unit-testable.

**Frontend**
- **Import entry point** — an upload control (PDF/PNG/JPG) that calls `POST /api/imports`.
- **Import-review mode in the editor** — the existing `EditorCanvas` gains an optional **backdrop image layer** and a **calibration tool**; the draft is loaded as editable geometry on top.
- On **Confirm** — convert pixel coords → metres via the scale, build a `GeometryDoc`, call the existing `saveFloorPlan`, then the existing Check flow.

**Reused as-is:** the editor/canvas, `saveFloorPlan`, the whole check flow, and **`GeometryDoc.validationErrors()`** as the final safety gate (reserved `EXTERIOR` id, ≥3 points per space, doors reference real spaces, 2-point door positions) — so a bad import can never corrupt the engine.

---

## 6. Data model (DTOs)

**`PlanExtraction`** (vision output; pixel space)
- `rooms`: list of `ExtractedRoom { label, occupancyTypeGuess, polygonPx: Point[], confidence }`
- `doors`: list of `ExtractedDoor { positionPx: Point[2], toRoomLabel?, isExitGuess, clearWidthMmGuess?, confidence }`
- `scaleGuess`: `{ metresPerPixel, source: "scale-bar" | "dimension" | "other", confidence }` | `null`
- `warnings`: string[]

**`ImportDraft`** (returned to the frontend)
- `backdrop`: the rendered image (returned to the client for display; not persisted in v1)
- `imageWidthPx`, `imageHeightPx`
- `draftGeometryPx`: a `GeometryDoc`-shaped object whose coordinates are still **pixels**
- `scaleGuess?`, `warnings`

**`ScaleCalibration`** (frontend-only, during review)
- two points + `knownMetres` → `metresPerPixel = knownMetres / pixelDistance(points)`

Occupancy guesses are mapped onto the engine's known vocabulary (e.g. `WB`, `CA`); an unmapped guess is surfaced for the user to set (never silently defaulted).

---

## 7. Data flow

1. User uploads PDF/PNG/JPG → `POST /api/imports`.
2. **`PlanImageRenderer`** → normalised image + pixel dimensions (PDF first page rendered; image passed through).
3. **`VisionPlanExtractor`** → `PlanExtraction` (pixel coords; occupancy/exit/width guesses; scale guess or null + confidence).
4. **`ImportDraftAssembler`** → `ImportDraft`; controller returns it.
5. Frontend opens the editor in **import-review mode**: backdrop image behind, draft overlaid.
6. **Scale:** if `scaleGuess` present → pre-fill and ask the user to eyeball it; else → **calibrate** (click two points on a known length, type the metres).
7. User **corrects** against the drawing: nudge rooms, set occupancy types, mark which doors are exits, fix door positions/widths.
8. **Confirm** → convert every pixel coord → **metres** via the scale → assemble a `GeometryDoc` → existing `saveFloorPlan` → `validationErrors()` → async Check.

---

## 8. Scale handling

The engine computes travel distance in **metres**; an uploaded image is in **pixels**. A pixels→metres scale is therefore mandatory before a check can run.

- **Auto-detect:** Claude attempts to read a scale bar or dimension annotation and returns `metresPerPixel` + a `source` + confidence, or `null`.
- **Manual fallback (always available):** the user clicks two points of known real length and types the metres; `metresPerPixel = knownMetres / pixelDistance`.
- **Conversion (at Confirm):** `metres = pixels × metresPerPixel`. Door clear widths are user-set/verified (vision width guesses are advisory only).
- **Check is blocked** (with a "calibrate first" prompt) until a scale is set — metres are otherwise undefined. Editing is **not** blocked.

---

## 9. Error handling & graceful degradation

Applying the project's "surface it, never a silent pass" rule (spec §11 of the v1 design) to ingestion:

- **Bad / corrupt / oversized file** → rejected up front with a clear message, *before* any LLM call.
- **Vision fails / low confidence / no API key / timeout** → **graceful degradation**: still return the **backdrop** with an empty-or-partial draft so the user can **trace over the image** from scratch. The feature never hard-fails; worst case is "manual tracing with a backdrop." (Same *"LLM is optional / swappable"* principle as the rule pipeline.)
- **No scale yet** (auto failed *and* not calibrated) → **block Check**, not editing.
- **Malformed / partial LLM output** → validate; keep usable elements, flag the rest. **Per-element isolation** — one bad room doesn't sink the import (mirrors per-rule isolation).
- **Confirmed `GeometryDoc`** → still passes through `validationErrors()` before save. The engine stays protected regardless of what the vision model produced.

---

## 10. Testing strategy

Mirrors the existing strategy; **no live API in CI.**
- **`PlanImageRenderer`** — deterministic unit test: fixture PDF → image, assert dimensions / non-empty.
- **`VisionPlanExtractor`** — the **seam is stubbed in tests** (like `RuleExtractor`); no Claude call in CI.
- **`ImportDraftAssembler` + pixel→metre conversion** — pure unit tests on a canned `PlanExtraction` (assembly + calibration math).
- **Optional vision eval** — `@Tag("eval")`, **excluded from CI**, needs an API key: run real Claude vision on a few gold floor-plan images, measure **room IoU / door recall** as a *metric*. The mirror of the rule gold-set eval.
- **Frontend** — component tests for the backdrop layer, the calibration tool, and the import-review flow.
- The **confirmed `GeometryDoc`** flows into the existing check tests unchanged.

---

## 11. Key design decisions

1. **Separation of input from analysis** — users/Claude supply building geometry only (rooms, doors, final-exit flags); the engine *always* computes the egress route + compliance (graph + Dijkstra). The user never draws or annotates the escape route; import must not degrade into manual egress-route annotation.
2. Import is an **additive ingestion layer**; the engine and check are untouched.
3. **LLM in ingestion, not the check path** — preserves determinism; parallels rule codification.
4. **Draft-then-confirm** (mandatory human review) — required for a life-safety tool.
5. **Claude vision over bespoke CV** — reuse the stack, ship fast, avoid a research project.
6. **Backdrop-assisted correction** — accuracy without trusting imprecise vision coordinates.
7. **Auto-scale-or-calibrate** — robustness; metres are mandatory, so calibration is the guaranteed floor.
8. **Reuse the extractor-seam + lazy-client + validation-gate + eval patterns** for consistency with the existing codebase.
9. **DWG / DXF / IFC deferred** — a different (vector) pipeline; DWG→PDF export bridges the gap now.

---

## 12. Risks & open questions

**Risks (mitigations):**
- Vision coordinate fidelity → backdrop correction + mandatory human confirm.
- Scale ambiguity → mandatory calibration before any check.
- Cost / latency of a vision call per upload → it's a per-upload authoring action, **not** in the check path; acceptable; can cache by image hash.
- Vision hallucination → human review + the `validationErrors()` gate.

**Open questions:**
- Persist the source image + extraction as plan **provenance** (mirrors rule provenance)? Deferred; revisit after MVP.
- Door clear-width guesses — trust vision, or always human-set? Current lean: **human-set/verify**, vision advisory.
- Occupancy-type vocabulary — Claude maps to the engine's known set (`WB`/`CA`/…) or flags unknown for the user.

---

## 13. Next step

On approval, proceed to an implementation plan (writing-plans), decomposed into vertical slices that keep a demoable system at each step:

1. Backend `PlanImageRenderer` + `VisionPlanExtractor` **seam** (stubbed) + `ImportDraftAssembler` + DTOs (TDD).
2. `ImportController` + `POST /api/imports` returning a stubbed draft.
3. Frontend upload → editor **backdrop layer** + draft overlay.
4. **Calibration** tool + pixel→metre conversion → Confirm → `GeometryDoc` → existing save → check.
5. Real **`ClaudeVisionPlanExtractor`** (LangChain4j, lazy, `@Profile("!test")`).
6. Optional vision **eval harness** (`@Tag("eval")`).
