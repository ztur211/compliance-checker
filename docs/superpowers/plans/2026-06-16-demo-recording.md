# Demo Recording — Reproducible Full-Tour Video/GIF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a committed, re-runnable pipeline that records the app's full tour (AI import → compliance check → rule review) to a video, exported to `portfolio.mp4` and `docs/demo.gif`, with the AI parts replayed deterministically (no API calls at record time).

**Architecture:** A `demo` Spring profile replays a one-time-captured real Claude vision extraction (or a proportional fallback plan) and seeds real AI-codified rule candidates, so the two AI segments have good, deterministic data for free. A Playwright "demo-driver" with an injected animated cursor + step captions drives the tour against the running app and records straight to webm; an ffmpeg script exports MP4 + GIF. A light, reversible UI polish pass makes it portfolio-grade.

**Tech Stack:** Java 21 / Spring Boot (app module, JPA, JobRunr), the pure `engine` module, React 19 + TypeScript + Vite (frontend), Playwright, ffmpeg.

---

## Environment & execution note

This dev sandbox has **no Docker, no Postgres, no browser, no ffmpeg**. Therefore:

- **Runs anywhere (incl. this sandbox):** the pure JUnit unit tests added below (no Spring context, no DB) and the frontend Vitest suite (jsdom). Java/TS compile checks.
- **Runs on the owner's machine or CI only:** booting the backend (needs Postgres via `docker compose up -d db`), the one-time capture (needs `ANTHROPIC_API_KEY`), the Playwright recording (needs a browser + the running app), and the ffmpeg export. These steps are marked **[owner/CI]** below.

Execute backend tasks 1–3 and frontend tasks 4–5 with full unit verification anywhere; tasks 6–10 are authored here and verified by the owner on a machine that can run the app.

---

## File structure

**Backend (`app` module)**
- `app/src/main/java/nz/compliance/app/imports/DemoVisionPlanExtractor.java` — new; replays captured extraction or builds a proportional fallback plan. Responsibility: deterministic vision output under the `demo` profile.
- `app/src/main/java/nz/compliance/app/imports/StubVisionPlanExtractor.java` — modify one annotation so the demo bean wins under `demo`.
- `app/src/main/java/nz/compliance/app/rules/DemoRuleSeeder.java` — new; seeds DRAFT rule candidates on boot under `demo`.
- `app/src/main/resources/demo/rules.json` — new; committed sample candidates (overwritten by capture).
- `app/src/main/resources/demo/extraction.json` — **not committed initially**; written by the capture harness.
- `app/src/test/java/nz/compliance/app/imports/DemoVisionPlanExtractorTest.java` — new unit test (no DB).
- `app/src/test/java/nz/compliance/app/rules/DemoRuleSeederTest.java` — new unit test (no DB).
- `app/src/test/java/nz/compliance/app/demo/CaptureDemoFixturesTest.java` — new `@Tag("capture")` harness.
- `app/pom.xml` — modify: exclude the `capture` tag from CI.

**Frontend**
- `frontend/src/styles.css` — new; light global styles + legend/cursor classes.
- `frontend/src/main.tsx` — modify: import the stylesheet.
- `frontend/index.html` — modify: page title.
- `frontend/src/App.tsx` — modify: header + nav styling.
- `frontend/src/editor/EditorPage.tsx` — modify: legend.
- `frontend/src/editor/EditorCanvas.tsx` — modify: crisper violation styling.
- `frontend/playwright.config.ts` — new.
- `frontend/e2e/overlay.ts` — new; injected cursor + caption helpers.
- `frontend/e2e/demo.spec.ts` — new; the demo-driver.
- `frontend/e2e/assets/plan.jpg` — new; copy of the demo image to upload.
- `frontend/scripts/export-demo.mjs` — new; ffmpeg export.
- `frontend/package.json` — modify: add `@playwright/test` + `demo:record`/`demo:export` scripts.

**Docs / repo**
- `.gitignore` — modify: ignore recording artifacts (keep `docs/demo.gif`).
- `README.md` — modify: embed `docs/demo.gif`.
- `docs/DEMO.md` — new; the re-record runbook.
- `docs/demo.gif` — generated artifact, committed by the owner.

---

## Phase A — Backend `demo` profile (replay) + capture

### Task 1: `DemoVisionPlanExtractor` + stub profile change

**Files:**
- Create: `app/src/main/java/nz/compliance/app/imports/DemoVisionPlanExtractor.java`
- Modify: `app/src/main/java/nz/compliance/app/imports/StubVisionPlanExtractor.java:15`
- Test: `app/src/test/java/nz/compliance/app/imports/DemoVisionPlanExtractorTest.java`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/nz/compliance/app/imports/DemoVisionPlanExtractorTest.java`:
```java
package nz.compliance.app.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoVisionPlanExtractorTest {

    private final DemoVisionPlanExtractor extractor = new DemoVisionPlanExtractor();
    private final RenderedImage image = new RenderedImage(new byte[0], 1600, 1000);

    @Test
    void fallbackPlanHasThreeRoomsAndExactlyOneExitWithinBounds() {
        PlanExtraction ex = extractor.fallback(image);
        assertThat(ex.rooms()).hasSize(3);
        long exits = ex.doors().stream().filter(ExtractedDoor::exitGuess).count();
        assertThat(exits).isEqualTo(1);                 // single exit -> guaranteed escape-routes violation
        assertThat(ex.scaleGuess()).isNotNull();
        assertThat(ex.scaleGuess().metresPerPixel()).isGreaterThan(0);
        ex.rooms().forEach(r -> r.polygonPx().forEach(p -> {
            assertThat(p.x()).isBetween(0.0, 1600.0);
            assertThat(p.y()).isBetween(0.0, 1000.0);
        }));
    }

    @Test
    void extractUsesFallbackWhenNoCaptureFixtureOnClasspath() {
        PlanExtraction ex = extractor.extract(image);   // no demo/extraction.json in test classpath
        assertThat(ex.rooms()).hasSize(3);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl app test -Dtest=DemoVisionPlanExtractorTest`
Expected: FAIL — compilation error, `DemoVisionPlanExtractor` does not exist.

- [ ] **Step 3: Create the extractor**

`app/src/main/java/nz/compliance/app/imports/DemoVisionPlanExtractor.java`:
```java
package nz.compliance.app.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.compliance.engine.model.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Deterministic, free vision extractor (active under the "demo" profile). Returns a previously
 * captured REAL Claude extraction from classpath {@code demo/extraction.json} when present;
 * otherwise a proportional canned plan sized to the uploaded image, so the backdrop always lines up
 * and the resulting check produces a clear, located violation. No API calls, no key.
 */
@Component
@Profile("demo")
public class DemoVisionPlanExtractor implements VisionPlanExtractor {

    static final String FIXTURE = "demo/extraction.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public PlanExtraction extract(RenderedImage image) {
        PlanExtraction captured = loadCaptured();
        return captured != null ? captured : fallback(image);
    }

    /** The committed real capture, or null if none has been captured yet. */
    PlanExtraction loadCaptured() {
        ClassPathResource res = new ClassPathResource(FIXTURE);
        if (!res.exists()) {
            return null;
        }
        try (InputStream in = res.getInputStream()) {
            return mapper.readValue(in, PlanExtraction.class);
        } catch (Exception e) {
            return null;   // fall back rather than break the demo
        }
    }

    /**
     * Three rooms across the building's width with a SINGLE exit at the far-left wall, sized so the
     * deepest room's open path to that exit far exceeds the 18 m illustrative limit — a guaranteed,
     * located violation for the demo. Coordinates are image pixels; the scale maps the image width to
     * ~55 m so the metre dimensions are realistic. Door labels match room labels so the assembler
     * wires them without proximity guesses.
     */
    PlanExtraction fallback(RenderedImage image) {
        double w = image.widthPx();
        double h = image.heightPx();
        double mpp = 55.0 / w;                 // pixels -> metres (image width ~= 55 m)
        double y0 = h * 0.30, y1 = h * 0.70, ymid = h * 0.5;

        ExtractedRoom lobby = new ExtractedRoom("Lobby", "WB", List.of(
                new Point(w * 0.02, y0), new Point(w * 0.34, y0),
                new Point(w * 0.34, y1), new Point(w * 0.02, y1)), 0.9);
        ExtractedRoom office = new ExtractedRoom("Open office", "WB", List.of(
                new Point(w * 0.34, y0), new Point(w * 0.66, y0),
                new Point(w * 0.66, y1), new Point(w * 0.34, y1)), 0.9);
        ExtractedRoom store = new ExtractedRoom("Back store", "WB", List.of(
                new Point(w * 0.66, y0), new Point(w * 0.98, y0),
                new Point(w * 0.98, y1), new Point(w * 0.66, y1)), 0.9);

        ExtractedDoor exit = new ExtractedDoor(List.of(
                new Point(w * 0.02, ymid - h * 0.06), new Point(w * 0.02, ymid + h * 0.06)),
                List.of("Lobby"), true, 1200.0, 0.9);
        ExtractedDoor d1 = new ExtractedDoor(List.of(
                new Point(w * 0.34, ymid - h * 0.05), new Point(w * 0.34, ymid + h * 0.05)),
                List.of("Lobby", "Open office"), false, 900.0, 0.9);
        ExtractedDoor d2 = new ExtractedDoor(List.of(
                new Point(w * 0.66, ymid - h * 0.05), new Point(w * 0.66, ymid + h * 0.05)),
                List.of("Open office", "Back store"), false, 900.0, 0.9);

        return new PlanExtraction(List.of(lobby, office, store), List.of(exit, d1, d2),
                new ScaleGuess(mpp, "demo", 0.9), List.of("Demo plan (no live vision call)."));
    }
}
```

- [ ] **Step 4: Change the stub's profile so exactly one extractor is active under `demo`**

In `app/src/main/java/nz/compliance/app/imports/StubVisionPlanExtractor.java`, change line 15:
```java
@Profile("!claude")
```
to:
```java
@Profile("!claude & !demo")
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -pl app test -Dtest=DemoVisionPlanExtractorTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nz/compliance/app/imports/DemoVisionPlanExtractor.java \
        app/src/main/java/nz/compliance/app/imports/StubVisionPlanExtractor.java \
        app/src/test/java/nz/compliance/app/imports/DemoVisionPlanExtractorTest.java
git commit -m "feat(demo): demo-profile vision extractor (replay capture or proportional fallback)"
```

---

### Task 2: `DemoRuleSeeder` + committed sample candidates

**Files:**
- Create: `app/src/main/resources/demo/rules.json`
- Create: `app/src/main/java/nz/compliance/app/rules/DemoRuleSeeder.java`
- Test: `app/src/test/java/nz/compliance/app/rules/DemoRuleSeederTest.java`

- [ ] **Step 1: Create the sample candidates fixture**

`app/src/main/resources/demo/rules.json` (illustrative C/AS2 paraphrases; engine-valid `parameter`/`comparator`; overwritten by the live capture in Task 3):
```json
[
  {
    "citation": "C/AS2 3.3.2",
    "title": "Maximum open path length",
    "parameter": "OPEN_PATH_LENGTH",
    "comparator": "LTE",
    "threshold": 18.0,
    "riskGroups": ["WB"],
    "sourceQuote": "The maximum length of an open path shall not exceed 18 m.",
    "confidence": 0.93
  },
  {
    "citation": "C/AS2 3.4.1",
    "title": "Minimum number of escape routes",
    "parameter": "EXIT_COUNT",
    "comparator": "GTE",
    "threshold": 2.0,
    "riskGroups": ["WB", "CA"],
    "sourceQuote": "Every firecell shall be provided with not less than two escape routes where the occupant load exceeds 50.",
    "confidence": 0.90
  },
  {
    "citation": "C/AS2 3.15.1",
    "title": "Minimum clear width of an escape route",
    "parameter": "EXIT_WIDTH",
    "comparator": "GTE",
    "threshold": 850.0,
    "riskGroups": ["WB", "CA"],
    "sourceQuote": "The minimum clear width of any escape route shall be 850 mm.",
    "confidence": 0.88
  }
]
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/nz/compliance/app/rules/DemoRuleSeederTest.java`:
```java
package nz.compliance.app.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRuleSeederTest {

    // repos are unused by loadCandidates(); null is safe for this pure-loader test
    private final DemoRuleSeeder seeder = new DemoRuleSeeder(null, null);

    @Test
    void loadsValidDemoCandidatesFromClasspath() {
        var candidates = seeder.loadCandidates();
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allSatisfy(c -> {
            assertThat(c.citation()).isNotBlank();
            assertThat(c.parameter()).isNotBlank();
            assertThat(c.comparator()).isNotBlank();
            assertThat(c.threshold()).isGreaterThan(0);
        });
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw -pl app test -Dtest=DemoRuleSeederTest`
Expected: FAIL — `DemoRuleSeeder` does not exist.

- [ ] **Step 4: Create the seeder**

`app/src/main/java/nz/compliance/app/rules/DemoRuleSeeder.java`:
```java
package nz.compliance.app.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Seeds DRAFT rule candidates on boot (under the "demo" profile) so the "Rule review" tab has real,
 * AI-codified C/AS2 cards to demonstrate the human-in-the-loop approval flow. Idempotent: skips if
 * the demo rule set already exists. The set is left INACTIVE so checks keep using the engine's
 * fallback rule set unchanged. Candidates come from classpath {@code demo/rules.json}.
 */
@Component
@Profile("demo")
public class DemoRuleSeeder implements ApplicationRunner {

    static final String DEMO_SET_NAME = "NZBC C/AS2 — Means of Escape (AI-codified, demo)";
    static final String DEMO_SET_VERSION = "demo-1";
    static final String FIXTURE = "demo/rules.json";

    private final RuleSetRepository ruleSets;
    private final RuleRepository rules;
    private final ObjectMapper mapper = new ObjectMapper();

    public DemoRuleSeeder(RuleSetRepository ruleSets, RuleRepository rules) {
        this.ruleSets = ruleSets;
        this.rules = rules;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = ruleSets.findAll().stream().anyMatch(
                rs -> DEMO_SET_NAME.equals(rs.getName()) && DEMO_SET_VERSION.equals(rs.getVersion()));
        if (exists) {
            return;
        }
        List<RuleCandidate> candidates = loadCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        RuleSetEntity rs = ruleSets.save(new RuleSetEntity(DEMO_SET_NAME, DEMO_SET_VERSION));
        for (RuleCandidate c : candidates) {
            rules.save(new RuleEntity(rs.getId(), c));   // RuleEntity defaults status to DRAFT
        }
    }

    /** Load demo candidates from classpath; empty list if the fixture is missing/unreadable. */
    List<RuleCandidate> loadCandidates() {
        ClassPathResource res = new ClassPathResource(FIXTURE);
        if (!res.exists()) {
            return List.of();
        }
        try (InputStream in = res.getInputStream()) {
            return mapper.readValue(in, new TypeReference<List<RuleCandidate>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -pl app test -Dtest=DemoRuleSeederTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/nz/compliance/app/rules/DemoRuleSeeder.java \
        app/src/main/resources/demo/rules.json \
        app/src/test/java/nz/compliance/app/rules/DemoRuleSeederTest.java
git commit -m "feat(demo): seed DRAFT rule candidates for the Rule-review tab under demo profile"
```

---

### Task 3: One-time capture harness (real Claude → fixtures)  **[owner/CI]**

**Files:**
- Create: `app/src/test/java/nz/compliance/app/demo/CaptureDemoFixturesTest.java`
- Modify: `app/pom.xml:92`

- [ ] **Step 1: Exclude the `capture` tag from CI**

In `app/pom.xml`, change line 92:
```xml
<excludedGroups>eval</excludedGroups>
```
to:
```xml
<excludedGroups>eval,capture</excludedGroups>
```

- [ ] **Step 2: Create the capture harness**

`app/src/test/java/nz/compliance/app/demo/CaptureDemoFixturesTest.java`:
```java
package nz.compliance.app.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.compliance.app.imports.ClaudeVisionPlanExtractor;
import nz.compliance.app.imports.PlanExtraction;
import nz.compliance.app.imports.PlanImageRenderer;
import nz.compliance.app.rules.LangChain4jRuleExtractor;
import nz.compliance.app.rules.RuleCandidate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time, owner-run capture of REAL Claude outputs into the committed demo fixtures the "demo"
 * profile replays. Needs ANTHROPIC_API_KEY; {@code @Tag("capture")} keeps it out of CI. The tests
 * instantiate the extractors directly (no Spring context, no DB).
 *
 * Run:  ANTHROPIC_API_KEY=sk-ant-... ./mvnw -pl app test -Dtest=CaptureDemoFixturesTest -DexcludedGroups=
 */
@Tag("capture")
class CaptureDemoFixturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path DEMO_DIR = Path.of("src/main/resources/demo");
    private static final String DEMO_IMAGE = "src/test/resources/import-gold/wealthy-home-sample.jpg";

    // Representative C/AS2 clause texts. Replace with exact wording you are licensed to use.
    private static final List<String> CLAUSES = List.of(
            "The maximum length of an open path shall not exceed 18 m.",
            "Every firecell shall be provided with not less than two escape routes where the occupant load exceeds 50.",
            "The minimum clear width of any escape route shall be 850 mm.");

    @Test
    void captureImportExtraction() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "no ANTHROPIC_API_KEY; skipping capture");
        PlanExtraction ex = new ClaudeVisionPlanExtractor(key)
                .extract(new PlanImageRenderer().render(Files.readAllBytes(Path.of(DEMO_IMAGE))));
        Files.createDirectories(DEMO_DIR);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(DEMO_DIR.resolve("extraction.json").toFile(), ex);
        System.out.println("[capture] demo/extraction.json: rooms=" + ex.rooms().size()
                + " doors=" + ex.doors().size() + " scale=" + ex.scaleGuess());
    }

    @Test
    void captureRuleCandidates() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "no ANTHROPIC_API_KEY; skipping capture");
        LangChain4jRuleExtractor extractor = new LangChain4jRuleExtractor(key);
        List<RuleCandidate> all = new ArrayList<>();
        for (String clause : CLAUSES) {
            all.addAll(extractor.extract(clause));
        }
        Assumptions.assumeTrue(!all.isEmpty(), "extractor returned no candidates");
        Files.createDirectories(DEMO_DIR);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(DEMO_DIR.resolve("rules.json").toFile(), all);
        System.out.println("[capture] demo/rules.json: " + all.size() + " candidates");
    }
}
```

- [ ] **Step 3: Verify it compiles and is skipped without a key**

Run: `./mvnw -pl app test -Dtest=CaptureDemoFixturesTest -DexcludedGroups=`
Expected: compiles; both tests **skipped** (no `ANTHROPIC_API_KEY`) — `Tests run: 2, Skipped: 2` (assumption-aborted).

- [ ] **Step 4: (Owner, optional now) actually capture real fixtures** **[owner/CI]**

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw -pl app test -Dtest=CaptureDemoFixturesTest -DexcludedGroups=
```
Expected: writes `app/src/main/resources/demo/extraction.json` and overwrites `demo/rules.json` with genuine Claude outputs. **Eyeball `extraction.json`**: it should have several rooms and ≥1 exit door. If it has no exit door (so the check wouldn't show an escape-routes violation), either keep the fallback (delete `extraction.json`) or mark one door `"exitGuess": true` — your call on authenticity; the spec's preference order (Task 8) keeps the demo honest.

- [ ] **Step 5: Commit**

```bash
git add app/pom.xml app/src/test/java/nz/compliance/app/demo/CaptureDemoFixturesTest.java
# include fixtures only if you ran the real capture in Step 4:
git add app/src/main/resources/demo/extraction.json 2>/dev/null || true
git commit -m "test(demo): @Tag(capture) harness writing real Claude demo fixtures"
```

- [ ] **Step 6: Backend integration smoke** **[owner/CI]**

```bash
docker compose up -d db
./mvnw -pl app -am spring-boot:run -Dspring-boot.run.profiles=demo &
sleep 25
curl -s -X POST -F "file=@app/src/test/resources/import-gold/wealthy-home-sample.jpg" \
     http://localhost:8080/api/imports | head -c 300         # expect rooms/doors JSON, not "No vision model configured"
curl -s http://localhost:8080/api/admin/rules | head -c 300  # expect a non-empty DRAFT array
```
Expected: import returns a multi-room draft; `/api/admin/rules` returns the seeded DRAFT candidates. Stop the server when done.

---

## Phase B — Light UI polish (reversible)

### Task 4: Global styles, page title, header + nav

**Files:**
- Create: `frontend/src/styles.css`
- Modify: `frontend/src/main.tsx`, `frontend/index.html`, `frontend/src/App.tsx`

- [ ] **Step 1: Create the stylesheet**

`frontend/src/styles.css`:
```css
:root { --ink: #1f2933; --line: #d6dbe0; --brand: #3367d6; --bad: #c5221f; --ok: #0b8043; }
* { box-sizing: border-box; }
body { margin: 0; font: 15px/1.5 system-ui, -apple-system, Segoe UI, Roboto, sans-serif; color: var(--ink); background: #f6f8fb; }
.app-header { display: flex; align-items: baseline; gap: 12px; padding: 14px 20px; background: #fff; border-bottom: 1px solid var(--line); }
.app-header h1 { font-size: 18px; margin: 0; }
.app-header .tag { font-size: 12px; color: #66707a; }
.tabs { display: flex; gap: 8px; padding: 10px 20px; }
.tabs button { padding: 6px 14px; border: 1px solid var(--line); background: #fff; border-radius: 6px; cursor: pointer; }
.tabs button[aria-pressed="true"] { background: var(--brand); border-color: var(--brand); color: #fff; }
main { padding: 0 20px 32px; max-width: 1040px; }
button { font: inherit; }
.legend { display: flex; gap: 16px; font-size: 12px; color: #66707a; margin: 6px 0; }
.legend span::before { content: ""; display: inline-block; width: 14px; height: 0; vertical-align: middle; margin-right: 6px; }
.legend .vio::before { border-top: 10px solid #fbd5d0; box-shadow: inset 0 0 0 1px var(--bad); height: 10px; }
.legend .path::before { border-top: 2px dashed var(--bad); }
.cursor-demo { position: fixed; z-index: 9999; width: 22px; height: 22px; margin: -11px 0 0 -11px; border-radius: 50%; border: 2px solid rgba(0,0,0,.55); background: rgba(255,255,255,.35); pointer-events: none; transition: transform .05s linear; }
.cursor-ripple { position: fixed; z-index: 9998; width: 10px; height: 10px; margin: -5px 0 0 -5px; border-radius: 50%; background: var(--brand); pointer-events: none; animation: ripple .5s ease-out forwards; }
@keyframes ripple { to { transform: scale(5); opacity: 0; } }
.caption-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 9997; padding: 14px 20px; font-size: 20px; font-weight: 600; color: #fff; background: linear-gradient(transparent, rgba(0,0,0,.78)); pointer-events: none; opacity: 0; transition: opacity .25s; }
.caption-bar.show { opacity: 1; }
```

- [ ] **Step 2: Import the stylesheet** — add to the top of `frontend/src/main.tsx`:
```tsx
import './styles.css'
```

- [ ] **Step 3: Set the page title** — in `frontend/index.html` change `<title>frontend</title>` to:
```html
<title>NZ Fire Egress Compliance Checker</title>
```

- [ ] **Step 4: Header + nav styling** — replace the contents of `frontend/src/App.tsx`'s `return (...)` with:
```tsx
  return (
    <>
      <header className="app-header">
        <h1>NZ Fire Egress Compliance Checker</h1>
        <span className="tag">NZBC C/AS2 means of escape · design-aid pre-check</span>
      </header>
      <nav className="tabs">
        <button aria-pressed={tab === 'editor'} onClick={() => setTab('editor')}>Editor</button>
        <button aria-pressed={tab === 'review'} onClick={() => setTab('review')}>Rule review</button>
      </nav>
      {tab === 'review' ? <ReviewPage /> : floorPlanId ? <EditorPage floorPlanId={floorPlanId} /> : <p style={{ padding: '0 20px' }}>Starting…</p>}
    </>
  )
```

- [ ] **Step 5: Run frontend tests**

Run: `cd frontend && npm run test`
Expected: PASS. If a snapshot of `App` exists and legitimately changed, update it: `npm run test -- -u`, then re-run.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/styles.css frontend/src/main.tsx frontend/index.html frontend/src/App.tsx
git commit -m "feat(frontend): light demo polish — global styles, header, tabs"
```

---

### Task 5: Editor legend + crisper violation styling

**Files:**
- Modify: `frontend/src/editor/EditorPage.tsx`, `frontend/src/editor/EditorCanvas.tsx`

- [ ] **Step 1: Add a legend above the canvas** — in `frontend/src/editor/EditorPage.tsx`, replace the `<EditorCanvas .../>` block (lines ~122–123) with:
```tsx
      <div className="legend">
        <span className="vio">offending space (violation)</span>
        <span className="path">computed egress path</span>
      </div>
      <EditorCanvas doc={fp.doc} draft={draft} onCanvasClick={onCanvasClick}
                    violationSpaceIds={violationSpaceIds} pathNodeIds={pathNodeIds} />
```

- [ ] **Step 2: Crisper violation styling** — in `frontend/src/editor/EditorCanvas.tsx`, replace the spaces `<polygon>` block (lines ~30–34) with:
```tsx
      {doc.spaces.map((s) => {
        const bad = violationSpaceIds.includes(s.id)
        return (
          <polygon key={s.id} points={poly(s.polygon)}
                   fill={bad ? '#fbd5d0' : '#e8f0fe'}
                   stroke={bad ? '#c5221f' : '#3367d6'} strokeWidth={bad ? 3 : 1.5} />
        )
      })}
```
and make the egress path bolder — replace the `<polyline ... strokeDasharray="6" ...>` (lines ~41–44) with:
```tsx
      {pathNodeIds.filter((id) => byId[id]).length > 1 && (
        <polyline fill="none" stroke="#c5221f" strokeWidth={3} strokeDasharray="7 5"
                  points={poly(pathNodeIds.filter((id) => byId[id]).map((id) => centroid(byId[id].polygon)))} />
      )}
```

- [ ] **Step 3: Run frontend tests**

Run: `cd frontend && npm run test`
Expected: PASS. `EditorCanvas.test.tsx` asserts violation highlighting — confirm it still matches the new `fill`/`stroke`; if it pins the exact old fill `#fce8e6`, update it to `#fbd5d0` (or, to avoid churn, keep `#fce8e6` and the matching legend swatch). Re-run until green.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/editor/EditorPage.tsx frontend/src/editor/EditorCanvas.tsx
git commit -m "feat(frontend): editor legend + crisper violation/egress styling"
```

---

## Phase C — Playwright demo-driver

### Task 6: Playwright setup + upload asset + gitignore  **[owner/CI for run]**

**Files:**
- Modify: `frontend/package.json`, `.gitignore`
- Create: `frontend/playwright.config.ts`, `frontend/e2e/assets/plan.jpg`

- [ ] **Step 1: Add the dependency and scripts** — in `frontend/package.json`, add to `devDependencies`:
```json
    "@playwright/test": "^1.50.0",
```
and add to `scripts`:
```json
    "demo:record": "playwright test",
    "demo:export": "node scripts/export-demo.mjs",
```

- [ ] **Step 2: Install** **[owner/CI]**

Run: `cd frontend && npm install && npx playwright install chromium`
Expected: dependency resolved; Chromium downloaded.

- [ ] **Step 3: Create the Playwright config**

`frontend/playwright.config.ts`:
```ts
import { defineConfig } from '@playwright/test'

// Records the full-tour demo to webm. Headed + slowMo for natural pacing; a fixed viewport so the
// frame is stable for export. Assumes the app is already running: frontend :5173 (vite, proxying
// /api -> :8080) and backend :8080 under the `demo` Spring profile.
export default defineConfig({
  testDir: './e2e',
  outputDir: './demo-artifacts',
  timeout: 120_000,
  retries: 0,
  workers: 1,
  use: {
    baseURL: 'http://localhost:5173',
    headless: false,
    viewport: { width: 1280, height: 800 },
    video: { mode: 'on', size: { width: 1280, height: 800 } },
    launchOptions: { slowMo: 120 },
  },
})
```

- [ ] **Step 4: Add the upload asset**

Run: `cp app/src/test/resources/import-gold/wealthy-home-sample.jpg frontend/e2e/assets/plan.jpg`
(Create `frontend/e2e/assets/` if needed: `mkdir -p frontend/e2e/assets` first.)

- [ ] **Step 5: Ignore recording artifacts (keep `docs/demo.gif`)** — append to `.gitignore`:
```
# demo recording artifacts (docs/demo.gif is committed)
frontend/demo-artifacts/
frontend/test-results/
frontend/portfolio.mp4
```

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/playwright.config.ts \
        frontend/e2e/assets/plan.jpg .gitignore
git commit -m "chore(demo): playwright setup, upload asset, ignore recording artifacts"
```

---

### Task 7: Injected cursor + caption overlay

**Files:**
- Create: `frontend/e2e/overlay.ts`

- [ ] **Step 1: Create the overlay helpers**

`frontend/e2e/overlay.ts`:
```ts
import type { Page, Locator } from '@playwright/test'

// Injected into the page so the recording shows a moving cursor, click ripples, and step captions —
// raw Playwright video renders none of these and looks broken. The init script tracks the synthetic
// mouse Playwright dispatches; helpers drive smooth motion and set captions.
const INIT = `
(() => {
  const cur = document.createElement('div'); cur.className = 'cursor-demo'; cur.style.left='-50px'; cur.style.top='-50px';
  const cap = document.createElement('div'); cap.className = 'caption-bar';
  const add = () => { document.body.appendChild(cur); document.body.appendChild(cap); };
  if (document.body) add(); else addEventListener('DOMContentLoaded', add);
  addEventListener('mousemove', (e) => { cur.style.left = e.clientX + 'px'; cur.style.top = e.clientY + 'px'; }, true);
  addEventListener('mousedown', (e) => {
    const r = document.createElement('div'); r.className = 'cursor-ripple';
    r.style.left = e.clientX + 'px'; r.style.top = e.clientY + 'px'; document.body.appendChild(r);
    setTimeout(() => r.remove(), 600);
  }, true);
  window.__caption = (t) => { cap.textContent = t; cap.classList.toggle('show', !!t); };
})();
`

export async function installOverlay(page: Page): Promise<void> {
  await page.addInitScript(INIT)
}

export async function caption(page: Page, text: string): Promise<void> {
  await page.evaluate((t) => (window as unknown as { __caption: (s: string) => void }).__caption(t), text)
}

/** Smoothly move the synthetic cursor to the centre of a locator (so the overlay animates), then pause. */
export async function glideTo(page: Page, target: Locator, pauseMs = 350): Promise<void> {
  await target.scrollIntoViewIfNeeded()
  const box = await target.boundingBox()
  if (box) {
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 28 })
  }
  await page.waitForTimeout(pauseMs)
}

/** Glide to a control and click it. */
export async function glideClick(page: Page, target: Locator): Promise<void> {
  await glideTo(page, target)
  await target.click()
}
```

- [ ] **Step 2: Type-check**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.app.json`
Expected: no errors from `e2e/overlay.ts`. (If `e2e/` is outside the tsconfig include, this is a no-op for the file — Playwright type-checks it at run time in Task 8; that's acceptable.)

- [ ] **Step 3: Commit**

```bash
git add frontend/e2e/overlay.ts
git commit -m "feat(demo): injected cursor + caption overlay for the recording"
```

---

### Task 8: The demo-driver spec (full tour)  **[owner/CI for run]**

**Files:**
- Create: `frontend/e2e/demo.spec.ts`

- [ ] **Step 1: Create the demo-driver**

`frontend/e2e/demo.spec.ts`:
```ts
import { test, expect } from '@playwright/test'
import { installOverlay, caption, glideTo, glideClick } from './overlay'

// Full-tour demo: AI import -> compliance check -> rule review. Records to demo-artifacts/*.webm.
// Requires the app running: frontend :5173 + backend :8080 under the `demo` Spring profile.
test('compliance-checker full tour', async ({ page }) => {
  await installOverlay(page)
  await page.goto('/')
  await expect(page.getByRole('button', { name: 'Editor' })).toBeVisible()
  await page.waitForTimeout(800)

  // 1) AI import
  await caption(page, '1 · Upload a floor plan — AI extracts spaces, doors & exits')
  await glideTo(page, page.locator('input[type="file"]'))
  await page.setInputFiles('input[type="file"]', 'e2e/assets/plan.jpg')
  const confirm = page.getByRole('button', { name: 'Confirm & load into editor' })
  await expect(confirm).toBeEnabled({ timeout: 20_000 })   // scaleGuess pre-fills -> enabled
  await page.waitForTimeout(1400)                          // let the reviewer see the extracted plan
  await glideClick(page, confirm)

  // 2) Check compliance
  await caption(page, '2 · One click → located NZBC C/AS2 egress violations')
  const check = page.getByRole('button', { name: 'Check compliance' })
  await glideClick(page, check)
  await expect(page.getByText(/violation\(s\)/)).toBeVisible({ timeout: 25_000 })
  await page.waitForTimeout(1800)                          // hold on the red space + dashed path

  // 3) Rule review
  await caption(page, '3 · Rules are AI-codified from C/AS2 text, then human-approved')
  await glideClick(page, page.getByRole('button', { name: 'Rule review' }))
  await expect(page.getByRole('heading', { name: 'Rule review' })).toBeVisible()
  const approve = page.getByRole('button', { name: 'Approve' }).first()
  await expect(approve).toBeVisible({ timeout: 10_000 })
  await page.waitForTimeout(1200)
  await glideClick(page, approve)
  await page.waitForTimeout(1500)

  await caption(page, '')
})
```

- [ ] **Step 2: Record** **[owner/CI]**

Prereqs running: `docker compose up -d db`; backend `./mvnw -pl app -am spring-boot:run -Dspring-boot.run.profiles=demo`; frontend `cd frontend && npm run dev`.
Run: `cd frontend && npm run demo:record`
Expected: test passes; a `*.webm` appears under `frontend/demo-artifacts/`.

- [ ] **Step 3: Verify the take** **[owner/CI]** — open the webm and confirm: the cursor is visible and moves; captions show for each step; **step 2 shows a space outlined red with a dashed egress path** and a violation count > 0.
  - If **no** located violation appears (engine path metric differs from the assumption), deepen the fallback plan: in `DemoVisionPlanExtractor.fallback`, raise the scale (e.g. `75.0 / w`) and re-run, **or** (honest fallback per the spec) drive a sample for step 2 by replacing the import block's check target — load the "Single exit (too few escape routes)" sample via the "Load sample" `<select>` before clicking Check.
  - If captions/cursor don't render, confirm `styles.css` is imported (Task 4) and `installOverlay` runs before `goto`.

- [ ] **Step 4: Commit**

```bash
git add frontend/e2e/demo.spec.ts
git commit -m "feat(demo): playwright full-tour demo-driver (import -> check -> rule review)"
```

---

## Phase D — Export + docs

### Task 9: ffmpeg export pipeline  **[owner/CI for run]**

**Files:**
- Create: `frontend/scripts/export-demo.mjs`

- [ ] **Step 1: Create the export script**

`frontend/scripts/export-demo.mjs`:
```js
// Exports the newest Playwright recording to a portfolio MP4 and a README GIF.
// Usage: node scripts/export-demo.mjs    (run from the frontend/ directory; needs ffmpeg on PATH)
import { execFileSync } from 'node:child_process'
import { readdirSync, statSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..')
const artifacts = join(frontend, 'demo-artifacts')
const docs = join(frontend, '..', 'docs')

function newestWebm(dir) {
  let best = null
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    const s = statSync(p)
    if (s.isDirectory()) { const c = newestWebm(p); if (c && (!best || c.mtime > best.mtime)) best = c }
    else if (name.endsWith('.webm') && (!best || s.mtimeMs > best.mtime)) best = { path: p, mtime: s.mtimeMs }
  }
  return best
}

const src = newestWebm(artifacts)
if (!src) { console.error('No .webm under demo-artifacts/ — run `npm run demo:record` first.'); process.exit(1) }
mkdirSync(docs, { recursive: true })
const mp4 = join(frontend, 'portfolio.mp4')
const palette = join(artifacts, 'palette.png')
const gif = join(docs, 'demo.gif')
const ff = (args) => execFileSync('ffmpeg', ['-y', ...args], { stdio: 'inherit' })

console.log('Source:', src.path)
ff(['-i', src.path, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', '-movflags', '+faststart', mp4])
ff(['-i', src.path, '-vf', 'fps=12,scale=900:-1:flags=lanczos,palettegen', palette])
ff(['-i', src.path, '-i', palette, '-lavfi', 'fps=12,scale=900:-1:flags=lanczos,paletteuse', gif])
console.log('\nWrote:\n  ' + mp4 + '  (portfolio)\n  ' + gif + '  (README)')
console.log('If demo.gif > 5 MB, lower fps (e.g. fps=10) or scale (e.g. scale=720) and re-run.')
```

- [ ] **Step 2: Export** **[owner/CI]**

Run: `cd frontend && npm run demo:export`
Expected: prints the source webm; writes `frontend/portfolio.mp4` and `docs/demo.gif`. Confirm `docs/demo.gif` is < 5 MB (`ls -lh docs/demo.gif`); if larger, lower fps/scale in the script and re-run.

- [ ] **Step 3: Commit the script**

```bash
git add frontend/scripts/export-demo.mjs
git commit -m "chore(demo): ffmpeg export -> portfolio.mp4 + docs/demo.gif"
```

---

### Task 10: README embed + runbook + final verification

**Files:**
- Modify: `README.md`
- Create: `docs/DEMO.md`
- Add (owner): `docs/demo.gif`

- [ ] **Step 1: Embed the GIF in the README** — in `README.md`, immediately after the blockquote line (`> Design-aid / pre-check …`), insert:
```markdown

![Demo: AI import → compliance check → rule review](docs/demo.gif)
```

- [ ] **Step 2: Write the runbook**

`docs/DEMO.md`:
```markdown
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
```

- [ ] **Step 3: Final verification**

Run (anywhere): `cd frontend && npm run test`
Expected: PASS.
Run (anywhere): `./mvnw -pl app test -Dtest=DemoVisionPlanExtractorTest,DemoRuleSeederTest`
Expected: PASS.
Run **[owner/CI]**: `./mvnw -B verify` — Expected: green (Testcontainers/Postgres). Confirm `docs/demo.gif` renders in a Markdown preview of the README.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/DEMO.md
# owner adds the generated gif after recording:
git add docs/demo.gif 2>/dev/null || true
git commit -m "docs(demo): embed demo gif in README + recording runbook"
```

---

## Definition of done

- App boots under `-Dspring-boot.run.profiles=demo` and serves a replayed AI import (multi-room draft, not the stub box) and seeded DRAFT rule candidates on the Rule-review tab.
- `npm run demo:record` drives the full tour and emits a webm with a visible cursor, captions, and a located violation; `npm run demo:export` produces `portfolio.mp4` + `docs/demo.gif` (< 5 MB).
- `docs/demo.gif` embedded in the README; `docs/DEMO.md` lets anyone re-record in one pass.
- Unit tests (`DemoVisionPlanExtractorTest`, `DemoRuleSeederTest`) pass; frontend Vitest passes; `./mvnw -B verify` green; the `capture` tag is excluded from CI; no API calls at record time.

## Self-review

**Spec coverage:** demo profile replay (DemoVisionPlanExtractor + DemoRuleSeeder) ✓ Task 1–2; capture-once harness ✓ Task 3; Playwright driver + injected cursor/captions ✓ Task 6–8; ffmpeg export to mp4+gif ✓ Task 9; light reversible UI polish ✓ Task 4–5; README gif + runbook ✓ Task 10; honesty guard on forcing a violation ✓ Task 8 step 3 + fallback notes; profile exclusivity (`!claude & !demo`) ✓ Task 1 step 4. The gold-JSON no-cost fallback is documented in DEMO.md.

**Placeholder scan:** none. `demo/extraction.json` is produced by the documented capture step (and the extractor falls back without it); `demo/rules.json` ships as committed sample data; `sk-ant-...` is a necessarily user-supplied secret.

**Type/name consistency:** `VisionPlanExtractor.extract(RenderedImage)`, `PlanExtraction(rooms,doors,scaleGuess,warnings)`, `ExtractedRoom(label,occupancyTypeGuess,polygonPx,confidence)`, `ExtractedDoor(positionPx,connectsRoomLabels,exitGuess,clearWidthMmGuess,confidence)`, `ScaleGuess(metresPerPixel,source,confidence)`, `Point(x,y)`, `RuleCandidate(citation,title,parameter,comparator,threshold,riskGroups,sourceQuote,confidence)`, `new RuleEntity(UUID, RuleCandidate)` (defaults DRAFT), `RuleRepository.findByStatus(DRAFT)`, engine-valid `ParameterKey`/`Comparator` strings — all match the read source. Frontend selectors (`input[type="file"]`, "Confirm & load into editor", "Check compliance", "Rule review", "Approve", `/violation\(s\)/`) match `EditorPage.tsx`/`ImportReviewCanvas.tsx`/`ReviewPage.tsx`/`App.tsx`.
