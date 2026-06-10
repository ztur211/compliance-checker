# Polish, Docs & Demo Implementation Plan (Plan 7)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the working app into a presentable portfolio piece: one-click **sample plans**, a **building-context form**, a results summary + **disclaimer**, a real **README + ARCHITECTURE** doc, a **production deploy with managed Postgres**, a captured **demo GIF**, and a release tag.

**Architecture:** Mostly polish and ops; no new backend domain logic. Samples are frontend constants the editor can load; the production deploy adds a Fly Postgres and secrets (the Plan 1 deploy was stateless).

**Prerequisite:** Plan 6 complete (full draw → check → review flow working locally).

---

## File map

```
frontend/src/editor/samples.ts
frontend/src/editor/EditorPage.tsx        # sample loader, building-context form, summary, disclaimer
README.md                                  # rewrite: features, run, screenshots, deploy
docs/ARCHITECTURE.md                       # distilled architecture
docs/demo.gif                              # captured manually
fly.toml                                   # (unchanged; deploy steps add Postgres + secrets)
```

---

## Task 1: One-click sample plans

**Files:**
- Create: `frontend/src/editor/samples.ts`
- Modify: `frontend/src/editor/EditorPage.tsx`

- [ ] **Step 1: Sample geometry documents** (compliant, single-exit, unreachable)

`frontend/src/editor/samples.ts`:
```ts
import type { GeometryDoc } from '../api/types'

export interface Sample { name: string; doc: GeometryDoc }

const office = (id: string, x0: number, x1: number) => ({
  id, name: id, occupancyType: 'WB',
  polygon: [{ x: x0, y: 0 }, { x: x1, y: 0 }, { x: x1, y: 10 }, { x: x0, y: 10 }],
})

export const SAMPLES: Sample[] = [
  {
    name: 'Compliant (2 exits)',
    doc: {
      schemaVersion: 1,
      spaces: [office('s1', 0, 10)],
      doors: [
        { id: 'e1', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true },
        { id: 'e2', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 10, y: 4 }, { x: 10, y: 6 }], clearWidthMillimetres: 1200, exit: true },
      ],
    },
  },
  {
    name: 'Single exit (too few escape routes)',
    doc: {
      schemaVersion: 1,
      spaces: [office('s1', 0, 10)],
      doors: [
        { id: 'e1', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true },
      ],
    },
  },
  {
    name: 'Isolated room (no means of escape)',
    doc: {
      schemaVersion: 1,
      spaces: [office('s1', 0, 10), { id: 's3', name: 'Store', occupancyType: 'WB',
        polygon: [{ x: 100, y: 100 }, { x: 110, y: 100 }, { x: 110, y: 110 }, { x: 100, y: 110 }] }],
      doors: [
        { id: 'e1', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true },
        { id: 'e2', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 10, y: 4 }, { x: 10, y: 6 }], clearWidthMillimetres: 1200, exit: true },
      ],
    },
  },
]
```

- [ ] **Step 2: Add a sample loader to `EditorPage`** — add near the top of the returned JSX (after the `<h1>`):
```tsx
      <label>
        Load sample:{' '}
        <select
          defaultValue=""
          onChange={(e) => {
            const s = SAMPLES.find((x) => x.name === e.target.value)
            if (s) fp.setDoc(s.doc)
          }}
        >
          <option value="" disabled>Choose…</option>
          {SAMPLES.map((s) => <option key={s.name} value={s.name}>{s.name}</option>)}
        </select>
      </label>
```
and add the import at the top of `EditorPage.tsx`:
```tsx
import { SAMPLES } from './samples'
```

- [ ] **Step 3: Frontend tests still pass**

Run: `cd /workspace/frontend && npm run test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git -C /workspace add frontend/src/editor/samples.ts frontend/src/editor/EditorPage.tsx
git -C /workspace commit -m "feat(frontend): one-click sample floor plans"
```

---

## Task 2: Building-context form, results summary, disclaimer

**Files:**
- Modify: `frontend/src/editor/EditorPage.tsx`

- [ ] **Step 1: Building-context state + form** — replace the hard-coded save payload with state. Add near the other `useState` calls:
```tsx
  const [riskGroup, setRiskGroup] = useState('WB')
  const [sprinklered, setSprinklered] = useState(true)
  const [escapeHeight, setEscapeHeight] = useState(3)
```
Change `save()` to use them:
```tsx
  async function save() {
    setSaving(true)
    try {
      await saveFloorPlan(floorPlanId, {
        name, riskGroup, sprinklered, escapeHeightMetres: escapeHeight, geometry: fp.doc,
      })
    } finally { setSaving(false) }
  }
```
Add the form to the JSX (after the Toolbar):
```tsx
      <fieldset style={{ marginTop: 8 }}>
        <legend>Building context</legend>
        <label>Risk group{' '}
          <select value={riskGroup} onChange={(e) => setRiskGroup(e.target.value)}>
            <option value="WB">WB — working/business</option>
            <option value="CA">CA — crowd activity</option>
          </select>
        </label>{' '}
        <label><input type="checkbox" checked={sprinklered} onChange={(e) => setSprinklered(e.target.checked)} /> Sprinklered</label>{' '}
        <label>Escape height (m){' '}
          <input type="number" value={escapeHeight} min={0} step={0.5}
                 onChange={(e) => setEscapeHeight(Number(e.target.value))} style={{ width: 64 }} />
        </label>
      </fieldset>
```

- [ ] **Step 2: Results summary + disclaimer** — replace the `<ResultsPanel .../>` line and trailing stats with:
```tsx
      {result && !result.blocked && (
        <p>
          {result.violations.length} violation(s) · {result.passed.length} passed · {result.notEvaluated.length} not evaluated
        </p>
      )}
      <ResultsPanel result={result} />
      <p>Spaces: {fp.doc.spaces.length} · Doors: {fp.doc.doors.length}</p>
      <footer style={{ marginTop: 16, fontSize: 12, color: '#777' }}>
        Design-aid / pre-check against the NZBC C/AS2 Acceptable Solution. Not a legal compliance sign-off.
      </footer>
```

- [ ] **Step 3: Build + test**

Run: `cd /workspace/frontend && npm run build && npm run test`
Expected: build OK; tests PASS.

- [ ] **Step 4: Commit**

```bash
git -C /workspace add frontend/src/editor/EditorPage.tsx
git -C /workspace commit -m "feat(frontend): building-context form, results summary, disclaimer"
```

---

## Task 3: Documentation

**Files:**
- Modify: `README.md`
- Create: `docs/ARCHITECTURE.md`

- [ ] **Step 1: Rewrite `README.md`**

```markdown
# compliance-checker

Automated **building-code compliance** checking. **v1** checks **New Zealand commercial fire egress**
(means of escape, NZBC C/AS2) on a 2D floor plan.

> Design-aid / pre-check against the C/AS2 Acceptable Solution — **not** a legal compliance sign-off.

![demo](docs/demo.gif)

## What it does
- Draw a floor plan (spaces, doors, exits) in the browser.
- A pure, deterministic **rules engine** computes occupant load, builds an **egress graph**, and runs
  **Dijkstra** to find each space's open-path travel distance to the nearest exit.
- Violations are **located** on the plan (offending space + egress path highlighted) and explained.
- Compliance rules are **codified from C/AS2 text by an LLM**, then **human-reviewed** before activation;
  checks run deterministically against the approved, versioned rule set.

## Architecture
Pure `engine` module (JTS + JGraphT, no Spring) ← `app` (Spring Boot, Postgres, JobRunr) ← React/TS frontend.
LLM (LangChain4j + Claude) runs only at **authoring time**, never in the check path. See `docs/ARCHITECTURE.md`
and the design spec in `docs/superpowers/specs/`.

## Run locally
```
docker compose up -d db
./mvnw -pl app spring-boot:run                 # backend :8080
cd frontend && npm install && npm run dev      # frontend :5173
```

## Test
```
./mvnw verify                                  # engine + app (Testcontainers)
cd frontend && npm run test
```

## Rule extraction (optional)
Set `ANTHROPIC_API_KEY`, then trigger extraction and approve candidates on the **Rule review** tab.
Without an active rule set, checks fall back to a built-in illustrative rule set.

## Deploy
Combined image (`Dockerfile`) to Fly.io with a managed Postgres — see `docs/superpowers/plans/…-07-…`.

## Status / roadmap
v1 = NZ commercial fire egress. Roadmap: accessibility (NZS 4121), more risk groups, multi-storey,
IFC import, structural (NZS 3604), other jurisdictions. See the spec.
```

- [ ] **Step 2: Add `docs/ARCHITECTURE.md`** (distilled from the spec)

```markdown
# Architecture

## Modules
- **engine/** — pure Java library (JTS geometry, JGraphT pathfinding). No Spring/DB/web/LLM.
  `ComplianceEngine.check(GeometryDoc, BuildingContext, RuleSet) → CheckResult`.
- **app/** — Spring Boot: REST API, Postgres (JPA + Flyway), JobRunr async checks, LLM rule codification.
- **frontend/** — React + TypeScript SVG editor/viewer + rule-review page.

## Check flow
draw → save geometry (jsonb) → POST check → JobRunr worker runs the pure engine
(resolve rules → facts → egress graph → Dijkstra → evaluate) → store located violations → frontend polls + highlights.

## Rule codification (authoring time)
C/AS2 text → PDFBox → clause chunks → LangChain4j/Claude (schema-constrained) → validate →
human review (approve/edit/reject, with provenance) → versioned active RuleSet → consumed by checks.
The LLM never runs during a check.

## Key decisions
Pure deterministic engine module · rules as data + parameter registry · LLM at authoring time +
mandatory human-in-the-loop · architect-for-many-jurisdictions-populate-one · centroid door-graph path
metric in v1 (navmesh on the roadmap). Full rationale: `docs/superpowers/specs/2026-06-09-…-design.md`.
```

- [ ] **Step 3: Commit**

```bash
git -C /workspace add README.md docs/ARCHITECTURE.md
git -C /workspace commit -m "docs: README + architecture overview"
```

---

## Task 4: Production deploy with managed Postgres

> Interactive — run the `fly` commands yourself (prefix with `!`). The Plan 1 deploy was stateless; the app now needs Postgres.

- [ ] **Step 1: Create + attach a Fly Postgres**

```bash
! fly postgres create --name compliance-checker-db --region syd --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 1
! fly postgres attach compliance-checker-db --app <your-app-name>
```
Note the credentials printed (host, db, user, password).

- [ ] **Step 2: Set datasource secrets** (the app reads `DB_URL`/`DB_USER`/`DB_PASSWORD`)

```bash
! fly secrets set --app <your-app-name> \
    DB_URL="jdbc:postgresql://compliance-checker-db.flycast:5432/<db>" \
    DB_USER="<user>" DB_PASSWORD="<password>"
# optional, only for in-prod extraction:
! fly secrets set --app <your-app-name> ANTHROPIC_API_KEY="sk-ant-…"
```

- [ ] **Step 3: Bump memory if needed and deploy**

In `fly.toml` ensure `[[vm]] memory = "1gb"` (Spring Boot + JobRunr). Then:
```bash
! fly deploy --app <your-app-name>
```

- [ ] **Step 4: Smoke-test the live deploy**

```bash
! curl -s https://<your-app-name>.fly.dev/api/health
```
Expected: `{"status":"ok","engine":"compliance-engine 0.1.0"}`. Open the URL: draw/load a sample, run a check, see located violations. Flyway runs V1–V3 automatically on boot.

- [ ] **Step 5: Commit any fly.toml change**

```bash
git -C /workspace add fly.toml
git -C /workspace commit -m "deploy: production memory sizing for app + jobrunr" || echo "no fly.toml change"
```

---

## Task 5: Demo capture + release

- [ ] **Step 1: Capture the demo GIF** (manual)

Run the app locally (or use the live URL). Record a ~15s screen capture of: load the *Single exit* sample → **Check compliance** → the violation list + the offending space outlined red. Save as `docs/demo.gif` (e.g. with [peek], [LICEcap], or `ffmpeg` from a screen recording). Keep it < 5 MB.

```bash
git -C /workspace add docs/demo.gif
git -C /workspace commit -m "docs: add demo gif"
```

- [ ] **Step 2: Final full verification**

```bash
cd /workspace && ./mvnw -B verify          # engine + app green
cd /workspace/frontend && npm ci && npm run test && npm run build
```
Expected: all green; frontend builds.

- [ ] **Step 3: Tag the release and push**

```bash
git -C /workspace tag v1.0.0-nz-egress
git -C /workspace push origin main --tags
```

- [ ] **Step 4: Confirm CI is green** on the pushed `main` (GitHub Actions), and the live URL works.

---

## Definition of done (Plan 7 — and v1)

- One-click samples, building-context form, results summary, and a disclaimer in the UI.
- README with demo GIF + `docs/ARCHITECTURE.md`.
- Live Fly.io deploy backed by managed Postgres; `/api/health` green; a check runs end-to-end on the live URL.
- `./mvnw verify` + frontend tests green; CI green on `main`; tagged `v1.0.0-nz-egress`.

## Self-review notes

- **Spec coverage:** sample plans + demo GIF + README + architecture doc + disclaimer (spec §13 maturity) ✓; live deploy with persistence (§13) ✓; building context surfaced so it visibly affects checks (§7) ✓. No new domain logic — purely the "ship it / present it" layer.
- **Placeholders:** none in code. `<your-app-name>` / DB credentials are necessarily user/environment-specific deploy values; `docs/demo.gif` is a manually captured asset (its capture is a documented step, not a code stub).
- **Type consistency:** samples conform to the `GeometryDoc`/`Space`/`Door` interfaces from Plan 2; the building-context form posts `riskGroup/sprinklered/escapeHeightMetres` matching `SaveFloorPlanRequest`. No signatures change.
```
