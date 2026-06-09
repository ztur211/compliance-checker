# compliance-checker — Design Spec (v1: NZ Fire Egress)

- **Date:** 2026-06-09
- **Status:** Draft for review
- **Working title:** `compliance-checker` — building-code compliance checker; v1 covers NZ fire egress. Renameable.
- **Context:** Solo build, Java/Spring. Author has NZBC domain expertise (domain specifics to confirm — see §15). Designed to extend past v1 into a multi-domain product.

---

## 1. Summary

A web app where a user draws a commercial floor plan, sets the building context, clicks **Check**, and receives located **means-of-escape** violations evaluated against **LLM-codified, human-approved C/AS2 rules** — backed by a pure, deterministic rules engine.

Three technical pillars: (1) a geometry + graph-shortest-path engine over a spatial egress graph; (2) an LLM pipeline that codifies building-code text into structured rules with human review; (3) NZBC (New Zealand) as the target jurisdiction.

---

## 2. Goals / Non-goals

**Goals (v1):** a deployed, demoable compliance check for NZ commercial means of escape; a deterministic, pure rules engine with strong tests; an LLM rule-codification pipeline with human review, provenance, and an eval harness; CI, containerization, a live demo URL, docs; an architecture that supports future domains/jurisdictions as modules.

**Non-goals (v1):** a legal sign-off (this is a design-aid / pre-check against the prescriptive Acceptable Solution pathway); checking fire-engineered designs (Alternative Solutions / C/VM2); any non-spatial domain (structural, plumbing) or non-fire spatial domain; multi-tenancy, real users.

---

## 3. Scope — v1 boundary & roadmap

**✅ IN v1:**
- **Domain:** NZ · NZBC Protection from Fire (C clauses) · **C/AS2** · **means of escape only** · commercial risk groups (e.g. WB, CA) · single storey · 2D.
- **Editor:** draw spaces (polygon + use → occupant density), place doors/openings (on boundary + clear width), mark exits; set `BuildingContext` (risk group, sprinklered, escape height).
- **Engine checks:** occupant load · open-path travel distance (Dijkstra) · dead-end open-path length · escape-route count required vs provided · escape-route / exit width.
- **AI:** LLM extracts C/AS2 prose provisions → schema-constrained `Rule` candidates → human review queue → versioned RuleSet; key threshold tables hand-entered; ~10-provision gold-set eval.
- **Platform:** Spring Boot API · lightweight auth · Postgres · JobRunr async checks · pure `engine` module · multi-domain architecture present, only NZ-fire-egress populated.
- **Maturity:** golden + property + integration tests · GitHub Actions CI · Docker · live demo URL · README + architecture doc + demo GIF + sample plans.

**❌ OUT of v1:** IFC import · 3D · multi-storey · other jurisdictions · accessibility (NZS 4121) · structural (NZS 3604) · plumbing (AS/NZS 3500) · zoning / Unitary Plan · Alternative Solutions / C/VM2 · automatic (vision) table extraction · multi-user · real users.

**🗺️ Roadmap:**
1. Accessibility module (NZS 4121 / D1) — reuses the spatial engine; natural first extension.
2. Vision-based table extraction · more risk groups · multi-storey egress · IFC import · 3D viewer.
3. Structural module (NZS 3604) — separate framing/bracing model + calculation engine.
4. Building services (AS/NZS 3500) · zoning (Unitary Plan) · additional jurisdictions (AU NCC, UK, US IBC).
5. Stretch: multi-user · pilot with NZ practices.

---

## 4. Architecture

```
Browser  (React / TypeScript)
  ├─ Floor-plan editor  → draw spaces, doors, exits; set building context
  └─ 2D viewer          → renders located violations in place
        │  REST / JSON
        ▼
API  (Spring Boot) ──enqueue check──▶ Async worker  (JobRunr)
  │                                        │ runs
  │                                        ▼
  │                            RULES ENGINE  (pure, deterministic)
  │                               • geometry        (JTS)
  │                               • egress graph + pathfinding (JGraphT / Dijkstra)
  │                               • rule resolution + evaluation → located violations
  ▼
Postgres  (users · projects · floor plans · jurisdictions · rulesets · rules · check runs · violations)

Rule Codification  (offline: LLM + human review)
   C/AS2 text ─▶ structured Rule candidates ─▶ human approval ─▶ versioned RuleSet
```

**Central principle:** the engine is a pure library (its own build module) with no dependency on Spring, the database, the web, or the LLM. It takes geometry + resolved rules and returns violations — deterministic, fast, independently testable. The LLM stays at authoring time, never in the runtime check path.

---

## 5. Technology stack

| Concern | Choice |
|---|---|
| Backend | **Java + Spring Boot** (REST) |
| Persistence | **Spring Data JPA / Hibernate** + **Postgres** (geometry as typed jsonb; PostGIS later) |
| Geometry | **JTS (Java Topology Suite)** |
| Graph / pathfinding | **JGraphT** (Dijkstra) |
| Async jobs | **JobRunr** (DB-backed, retries, dashboard) |
| LLM (authoring-time) | **LangChain4j** (Claude), structured/JSON-schema output |
| PDF extraction | **Apache PDFBox** |
| Frontend | **React + TypeScript**, 2D canvas (SVG / Konva) |
| Build / test | Maven or Gradle · JUnit 5 · AssertJ · jqwik · Testcontainers |
| Deploy / CI | Docker · Fly.io / Render · GitHub Actions |

---

## 6. Domain model

Normalized in Postgres via JPA.

**Accounts & projects**
- **User** — owns projects.
- **Project** — `id, userId, name, createdAt`.
- **FloorPlan** — `id, projectId, name, level, geometryJson, schemaVersion`.

**Floor-plan geometry** (typed `geometryJson`, jsonb)
- **Space** — `polygon`, `use/occupancyType`, `name`.
- **Door / Opening** — boundary segment, `clearWidth`, connects two spaces or space ↔ exterior.
- **Exit** — a door marked as discharging to safety / final exit.

**Building context**
- **BuildingContext** (on Project/FloorPlan) — `location → Jurisdiction`, `riskGroup`, `sprinklered`, `escapeHeight`, per-space `use`.

**Jurisdiction & rules**
- **Jurisdiction** — region hierarchy (Country › Region/Council) + adopted/amended editions.
- **RuleSet** — named, **versioned, layered** (`base → amendments`); resolved set is the merge.
- **Rule** — `citation`, `title`, `parameter` (a known engine `ParameterKey`), `comparator` (≤ ≥ =), `thresholdTable` (keyed by context, not a scalar), `applicability` (predicate over `BuildingContext`), `status` (draft / human-reviewed / active), provenance (`sourceQuote`, `extractionConfidence`, `extractionModel`, `extractionRunId`).
- **RuleExtractionRun** (optional) — `documentRef, edition, timestamp, model, counts`; supports re-extraction + diffing.

**Checking**
- **CheckRun** — `id, floorPlanId, ruleSetId, geometryHash, status (queued/running/succeeded/failed), startedAt, finishedAt, summary`.
- **Violation** — `checkRunId, ruleId, citation, severity, message, computedValue, threshold, location`.

---

## 7. Jurisdiction & applicability (New Zealand)

> Working assumptions; domain specifics to be confirmed by the author (§15).

- **NZBC is performance-based** (Building Act 2004). Compliance is shown via a pathway — Acceptable Solution, Verification Method, or a bespoke Alternative Solution. Acceptable Solutions are prescriptive → mechanically checkable.
- v1 targets **C/AS2** (Acceptable Solution for commercial/multi-use; C/AS1 covers standalone dwellings, Risk Group SH). Alternative Solutions / C/VM2 are out of scope.
- **Means of escape** maps onto the engine: occupant load (occupant-density tables, m²/person by use), open-path travel distance + dead-end limits (Dijkstra), escape-route count, escape-route/exit widths; escape height as a context input.
- **`BuildingContext` is NZ-flavored:** `riskGroup` (e.g. WB, CA), `sprinklered`, `escapeHeight` drive which provisions apply and at what threshold.
- **Councils & layering:** the fire code is national, so fire-egress variation is building-characteristic-driven, not council-driven. Councils are the Building Consent Authorities that enforce; council variation lives in **zoning (District/Unitary Plan)** — a roadmap domain. Multi-jurisdiction layering is justified by future countries + that zoning layer.
- **Cited standards:** C/AS2 cites AS/NZS / NZS standards; those cited in compliance documents are free-to-read and codifiable for the invoked parts.

---

## 8. Data flow

1. User draws a plan → frontend serializes `geometryJson` → `POST` → validated, stored on the FloorPlan.
2. User picks a RuleSet + clicks **Check** → API creates a `CheckRun` (`queued`), enqueues a JobRunr job, returns the run id (async).
3. Worker loads geometry + resolved rules → invokes `ComplianceEngine`.
4. Engine: resolve rules → parse/validate geometry → compute facts → build egress graph → pathfinding → evaluate → emit located `Violations`.
5. Worker saves Violations + marks `CheckRun` `succeeded`.
6. Frontend polls/subscribes → renders each violation in place on the 2D plan.

---

## 9. Rules engine

**Contract** (pure module; depends only on JTS + JGraphT):

```java
public final class ComplianceEngine {
    CheckResult check(FloorPlan plan, BuildingContext ctx, RuleSet rules);
}
record CheckResult(List<Violation> violations, List<RuleOutcome> passed, List<RuleOutcome> notEvaluated);
```

**Pipeline:**
0. **Rule resolution** — merge layered RuleSet → filter by `applicability(ctx)` → bind each `thresholdTable` to a concrete value for `ctx` → produce the `{parameter, comparator, threshold}` list.
1. **Parse & validate geometry** — `geometryJson` → JTS `Polygon`s + door segments. Validate topology (`isValid()`, doors on boundaries, connectivity); `buffer(0)` cleanup where safe.
2. **Compute spatial facts (once):** area; occupant load = area ÷ occupant-density; door clear widths; required exit count & egress width.
3. **Build egress graph:** nodes = spaces + exit-discharge points; edges = doors weighted by travel distance (JGraphT).
4. **Analyses:** per space, Dijkstra shortest path to nearest exit (= open-path length); exits provided vs required; available vs required width; dead-end detection.
5. **Evaluate rules:** each rule is data; the evaluator selects applicable elements, reads the fact via the parameter registry, compares, emits a located `Violation` on failure. Per-rule isolation — one rule failing never aborts the run.
6. **Assemble violations:** attach geometry to highlight (room, door, or egress path polyline).

**Parameter registry** (bridge between engine and rules):

```java
Map<ParameterKey, FactExtractor> registry = Map.of(
    OPEN_PATH_LENGTH, egress::maxOpenPathLength,
    DEAD_END_LENGTH,  egress::deadEndLength,
    OCCUPANT_LOAD,    facts::occupantLoad,
    EXIT_WIDTH,       facts::availableExitWidth,
    EXIT_COUNT,       egress::exitCount);
```
Adding a checkable quantity = add one extractor; adding a rule = add data.

**Pathfinding fidelity:** v1 computes open-path length as straight-line segments along the door-graph route (remote point → door → … → exit). This approximates the true path of travel and may underestimate distance around obstructions; adequate for open-plan commercial and disclosed in output. A per-space visibility-graph / navmesh is a roadmap refinement.

**Module layout:** `geometry` · `facts` · `egress` · `rules` · `model`, behind `ComplianceEngine`. The `engine` build module declares no Spring/web/DB/LLM dependency.

---

## 10. AI rule-codification pipeline

Turns C/AS2 means-of-escape provisions into structured `Rule` objects. Offline, authoring-time.

1. **Ingest & chunk** — C/AS2 PDF → text (PDFBox), chunked by clause.
2. **Extract candidates** — each chunk → LLM constrained to the `Rule` JSON schema (LangChain4j → typed objects, retry on mismatch). Prompt includes the catalog of engine-supported parameters; the model maps provisions onto them and flags provisions it cannot map (→ backlog of new extractors).
3. **Validate (deterministic guardrail)** — known parameter? valid comparator/units? threshold parses? applicability references real fields? Malformed → flagged.
4. **Human review queue** — candidates land as `draft`; reviewer sees the structured rule beside its verbatim source quote + citation, then approves / edits / rejects. Only approved rules become `active`.
5. **Activate & version** — approved rules commit into a versioned RuleSet; re-extraction on a new edition supports diffing.

**Design points:**
- LLM at authoring time only → runtime checks stay deterministic, with no runtime LLM dependency.
- Human-in-the-loop is mandatory for life-safety code.
- Provenance on every rule.
- LLM is swappable/optional — rules can be hand-authored; early development isn't blocked on it.
- Gold-set eval: ~10 hand-codified provisions; measure precision/recall; a metric run manually/nightly, not a CI gate.

**Tables (v1):** occupant-density, open-path/dead-end, and width tables *are* the thresholds and are hand-entered by the author (fast, exact); the LLM drafts prose provisions, rule structure, and applicability. Vision-based table extraction is a roadmap item.

No model training or datasets — prompt + schema + validation + review UI + eval harness.

---

## 11. Error handling & edge cases

**Principle:** every rule yields one of ✅ **pass** · ❌ **violation** (located) · ⚠️ **not evaluated** (with reason). "Can't tell" is always surfaced, never a silent pass.

- **Geometry:** invalid/degenerate polygons → detect via JTS, report the space, offer `buffer(0)`. Door not on a boundary / not connecting → flagged. Unreachable space (no door) → ⚠️ not evaluated. No exits in the model → incomplete model, block the run. Exits exist but a space can't reach one → ❌ violation (no means of escape). Missing/ambiguous units → reject at input.
- **Rule resolution:** incomplete `BuildingContext` → ⚠️ needs input. Rule referencing an unimplemented parameter → ⚠️ not evaluated, logged to the gap backlog. No applicable rules → reported, not a silent pass.
- **Per-rule isolation:** a rule failure drops to `notEvaluated` and never aborts the run.
- **AI pipeline:** schema-invalid output → retry → else flag for manual entry. Hallucinated number/citation → caught at review + provenance mismatch. PDF garble → flag chunk. API errors → backoff retry; no runtime impact.
- **Async/runtime:** JobRunr retries with backoff; `CheckRun → failed` with a summary; results tied to a geometry hash (stale detection); idempotent re-runs.

---

## 12. Testing strategy

**Engine** (pure & deterministic, no mocking):
- Golden floor-plan fixtures with known answers: compliant; open-path violation; dead-end violation; under-width exit; too-few exits; unreachable space. TDD-able.
- Property-based tests (jqwik): adding an exit never increases travel distance; distances non-negative; convex room's distance = straight-line.
- Unit tests for geometry/facts and pathfinding (Dijkstra on tiny hand-computed graphs).

**AI codification:**
- Deterministic tests with cached LLM responses (no live API in CI) → parsing + validation guardrail.
- Gold-set eval (precision/recall vs ~10 hand-codified provisions) → a metric, manual/nightly, not a CI gate.

**Integration:**
- Testcontainers → real Postgres for repository/migration tests (no-Docker local fallback: `zonky embedded-postgres`).
- Full-pipeline test — POST plan → JobRunr inline mode → assert located violations.
- API contract tests — Spring `WebTestClient` / MockMvc.

**Frontend:** Vitest + React Testing Library for editor/viewer; one Playwright smoke E2E (draw → check → see violations).

**CI (GitHub Actions):** build → engine + backend tests → frontend tests → lint → coverage. Optional nightly gold-set eval.

---

## 13. Deployment & ops

- Docker images for API + frontend; `docker-compose` for local (API + Postgres).
- Deploy to Fly.io or Render with a live demo URL and seeded sample plans.
- GitHub Actions CI; build artifacts on green.
- Docs: README, a short architecture doc, a demo GIF, 2–3 sample plans (one compliant, ones per violation type).
- Visible disclaimer: design-aid / pre-check, not a legal sign-off.

---

## 14. Key design decisions

1. v1 scope = NZ commercial fire egress (C/AS2 means of escape); one domain, one jurisdiction.
2. Java + Spring Boot (author comfort; C# was equally viable).
3. Pure, deterministic engine as its own build module — testability; keeps the LLM out of the runtime path.
4. LLM at authoring time only; human-in-the-loop mandatory.
5. Rules as data + parameter registry + threshold tables + applicability predicates — lets rules be LLM-authored and new domains plug in.
6. Architect for many jurisdictions/domains; populate exactly one in v1.
7. Hand-enter key threshold tables in v1.
8. Simplified door-graph path metric in v1; navmesh on the roadmap (disclosed approximation).

---

## 15. Risks & open questions

**Risks (mitigations):** PDF table extraction is hard → hand-enter key tables. Messy geometry → JTS validation + `buffer(0)` + ⚠️ not-evaluated. Path-distance fidelity → disclosed; navmesh on roadmap. LLM extraction accuracy → human review + provenance + gold-set eval + deterministic guardrail. Scope creep → strict v1 boundary (§3).

**Open questions — domain (author to confirm):**
- The C/AS1 (SH) vs C/AS2 split and the exact risk groups for "commercial" (WB, CA, others?).
- Which means-of-escape numbers live in C/AS2 vs cited AS/NZS standards (and which to codify).
- Occupant-density values; open-path / dead-end limits per risk group, sprinklered vs unsprinklered.
- How escape height changes requirements in v1 scope.
- Escape-route width / capacity formulas (per-person width factors, minimum widths).

**Open questions — engineering:** Maven vs Gradle; jsonb vs PostGIS timing; SVG vs Konva for the editor.

---

## 16. Next step

On approval, proceed to an implementation plan (writing-plans), decomposed into vertical slices that keep a demoable system working at each step: skeleton + deploy → editor + persistence → occupant-load/width checks → egress graph + Dijkstra → rule resolution → AI codification + review UI → polish/docs/demo.
