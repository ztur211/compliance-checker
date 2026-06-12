# Floor-Plan Import (vision-assisted) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user upload a PDF/image of a single-storey floor plan, have multimodal Claude extract a best-guess plan, correct it against the image as a backdrop, set scale, and confirm — producing the engine's existing `GeometryDoc`, which flows through the unchanged save → check path.

**Architecture:** An additive **ingestion layer** in front of the engine: `upload → PlanImageRenderer (PDFBox) → VisionPlanExtractor (seam; Claude) → ImportDraftAssembler → ImportDraft (pixels) → frontend backdrop-assisted review + calibration → GeometryDoc (metres) → existing save/check`. The LLM lives only in the ingestion path; the check stays pure and deterministic. **Separation of input from analysis:** users/Claude supply building geometry only (rooms, doors, final-exit flags); the engine always computes the egress route + compliance. The user never draws the escape route.

**Tech Stack:** Java 21 / Spring Boot, PDFBox 3.0.3 (already a dep — `PDFRenderer`), LangChain4j 1.16.2 (`AnthropicChatModel`, multimodal), Jackson, JUnit 5 + AssertJ, Spring `@WebMvcTest`; React 19 + TypeScript, SVG, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-06-12-floorplan-import-design.md`.

**Toolchain notes:**
- `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10` before any Maven command.
- **No Docker locally.** Every test in this plan is a **unit test or `@WebMvcTest`** (no Postgres) — they all run locally with `./mvnw -pl app test`. There are no new Testcontainers `*IT` tests here.
- Frontend: `cd frontend && npm run test` (Vitest) and `npm run build`.
- Commit plan work **directly to `main`** (solo portfolio repo). End commit messages with the `Co-Authored-By` trailer.
- Package: `nz.compliance.app.imports` (`import` is a Java keyword; `imports` is fine). Pixel coordinates reuse the engine's `nz.compliance.engine.model.Point` record (documented as pixels until scaled).

---

## File map

```
app/src/main/java/nz/compliance/app/imports/
  ScaleGuess.java            # record: metresPerPixel, source, confidence
  ExtractedRoom.java         # record: label, occupancyTypeGuess, polygonPx, confidence
  ExtractedDoor.java         # record: positionPx(2), connectsRoomLabels, exitGuess, clearWidthMmGuess, confidence
  PlanExtraction.java        # record: rooms, doors, scaleGuess?, warnings   (vision output, PIXELS)
  RenderedImage.java         # record: pngBytes, widthPx, heightPx
  ImportDraft.java           # record: backdropPngBase64, imageWidthPx, imageHeightPx, draftGeometryPx, scaleGuess?, warnings
  PlanImageRenderer.java     # PDF/image bytes -> RenderedImage (PDFBox PDFRenderer / ImageIO)
  VisionPlanExtractor.java   # interface seam: PlanExtraction extract(RenderedImage)
  StubVisionPlanExtractor.java   # @Profile("!claude") default: minimal heuristic draft, no API needed
  ClaudeVisionPlanExtractor.java # @Profile("claude") real Claude (Task 8)
  ImportDraftAssembler.java  # pure: (RenderedImage, PlanExtraction) -> ImportDraft (pixels, synthesized ids)
  ImportService.java         # orchestrates render -> extract -> assemble
  ImportController.java      # POST /api/imports (multipart) -> ImportDraft

app/src/test/java/nz/compliance/app/imports/
  PlanImageRendererTest.java
  ImportDraftAssemblerTest.java
  ImportControllerTest.java          # @WebMvcTest
  VisionPlanExtractorEvalTest.java   # @Tag("eval") (Task 9)

frontend/src/api/imports.ts          # ImportDraft type + uploadImport(file)
frontend/src/editor/importConvert.ts # pure: metresPerPixel(), pxGeometryToMetres()
frontend/src/editor/ImportReviewCanvas.tsx # backdrop + draft + calibration
frontend/src/editor/EditorPage.tsx   # MODIFY: import entry + review mode
frontend/src/editor/importConvert.test.ts
frontend/src/editor/ImportReviewCanvas.test.tsx
```

---

## Task 1: DTOs (records)

**Files:**
- Create: `app/src/main/java/nz/compliance/app/imports/ScaleGuess.java`
- Create: `app/src/main/java/nz/compliance/app/imports/ExtractedRoom.java`
- Create: `app/src/main/java/nz/compliance/app/imports/ExtractedDoor.java`
- Create: `app/src/main/java/nz/compliance/app/imports/PlanExtraction.java`
- Create: `app/src/main/java/nz/compliance/app/imports/RenderedImage.java`
- Create: `app/src/main/java/nz/compliance/app/imports/ImportDraft.java`

These are plain data carriers consumed by later tasks; they have no behaviour, so they are created together and exercised by Task 4's tests.

- [ ] **Step 1: Create the records**

`ScaleGuess.java`:
```java
package nz.compliance.app.imports;

/** A pixels->metres scale guess. {@code source} is e.g. "scale-bar", "dimension", "other". */
public record ScaleGuess(double metresPerPixel, String source, double confidence) {}
```

`ExtractedRoom.java`:
```java
package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import java.util.List;

/** A room the vision model found. {@code polygonPx} is in IMAGE PIXELS. */
public record ExtractedRoom(String label, String occupancyTypeGuess, List<Point> polygonPx, double confidence) {
    public ExtractedRoom {
        polygonPx = polygonPx == null ? List.of() : List.copyOf(polygonPx);
    }
}
```

`ExtractedDoor.java`:
```java
package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import java.util.List;

/** A door/opening the vision model found. {@code positionPx} is the 2-point segment in IMAGE PIXELS. */
public record ExtractedDoor(List<Point> positionPx, List<String> connectsRoomLabels,
                            boolean exitGuess, Double clearWidthMmGuess, double confidence) {
    public ExtractedDoor {
        positionPx = positionPx == null ? List.of() : List.copyOf(positionPx);
        connectsRoomLabels = connectsRoomLabels == null ? List.of() : List.copyOf(connectsRoomLabels);
    }
}
```

`PlanExtraction.java`:
```java
package nz.compliance.app.imports;

import java.util.List;

/** Whole-plan vision output, in IMAGE PIXELS. {@code scaleGuess} may be null. */
public record PlanExtraction(List<ExtractedRoom> rooms, List<ExtractedDoor> doors,
                             ScaleGuess scaleGuess, List<String> warnings) {
    public PlanExtraction {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        doors = doors == null ? List.of() : List.copyOf(doors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
```

`RenderedImage.java`:
```java
package nz.compliance.app.imports;

/** A normalised PNG render of an uploaded plan, plus its pixel dimensions. */
public record RenderedImage(byte[] pngBytes, int widthPx, int heightPx) {}
```

`ImportDraft.java`:
```java
package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import java.util.List;

/**
 * What the import endpoint returns to the frontend. {@code draftGeometryPx} is a GeometryDoc whose
 * coordinates are IMAGE PIXELS (converted to metres only at user Confirm, using the chosen scale).
 */
public record ImportDraft(String backdropPngBase64, int imageWidthPx, int imageHeightPx,
                          GeometryDoc draftGeometryPx, ScaleGuess scaleGuess, List<String> warnings) {
    public ImportDraft {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app -am test-compile -q`
Expected: BUILD SUCCESS (no tests yet).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nz/compliance/app/imports
git commit -m "feat(import): DTO records for plan extraction + import draft"
```

---

## Task 2: PlanImageRenderer (PDF/image → PNG) — TDD

**Files:**
- Test: `app/src/test/java/nz/compliance/app/imports/PlanImageRendererTest.java`
- Create: `app/src/main/java/nz/compliance/app/imports/PlanImageRenderer.java`

- [ ] **Step 1: Write the failing test**

`PlanImageRendererTest.java`:
```java
package nz.compliance.app.imports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PlanImageRendererTest {

    private final PlanImageRenderer renderer = new PlanImageRenderer();

    @Test
    void rendersAnImagePassthroughKeepingDimensions() throws Exception {
        BufferedImage bi = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);

        RenderedImage out = renderer.render(bos.toByteArray());

        assertThat(out.widthPx()).isEqualTo(120);
        assertThat(out.heightPx()).isEqualTo(90);
        assertThat(out.pngBytes()).isNotEmpty();
    }

    @Test
    void rendersFirstPageOfAPdf() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage(PDRectangle.A4));
            pdf.save(bos);
        }

        RenderedImage out = renderer.render(bos.toByteArray());

        assertThat(out.widthPx()).isGreaterThan(0);
        assertThat(out.heightPx()).isGreaterThan(0);
        assertThat(out.pngBytes()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -Dtest=PlanImageRendererTest -q`
Expected: FAIL — `PlanImageRenderer` not found.

- [ ] **Step 3: Implement the renderer**

`PlanImageRenderer.java`:
```java
package nz.compliance.app.imports;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Renders an uploaded PDF (first page) or raster image into a normalised PNG. */
@Component
public class PlanImageRenderer {

    private static final float PDF_DPI = 150f;

    public RenderedImage render(byte[] bytes) {
        try {
            BufferedImage image = isPdf(bytes) ? renderPdfFirstPage(bytes) : readImage(bytes);
            return new RenderedImage(toPng(image), image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the uploaded plan", e);
        }
    }

    private static boolean isPdf(byte[] b) {
        return b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    private static BufferedImage renderPdfFirstPage(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFRenderer(doc).renderImageWithDPI(0, PDF_DPI, ImageType.RGB);
        }
    }

    private static BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
        if (bi == null) {
            throw new IOException("unsupported image format");
        }
        return bi;
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return bos.toByteArray();
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -Dtest=PlanImageRendererTest -q`
Expected: PASS — 2 green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nz/compliance/app/imports/PlanImageRenderer.java app/src/test/java/nz/compliance/app/imports/PlanImageRendererTest.java
git commit -m "feat(import): PlanImageRenderer (PDF first page / image -> PNG)"
```

---

## Task 3: VisionPlanExtractor seam + stub

**Files:**
- Create: `app/src/main/java/nz/compliance/app/imports/VisionPlanExtractor.java`
- Create: `app/src/main/java/nz/compliance/app/imports/StubVisionPlanExtractor.java`

The seam mirrors `nz.compliance.app.rules.RuleExtractor` so the pipeline runs end-to-end (and demos) with **no API key** until Task 8 adds the real Claude impl. The stub returns a single full-image room and no doors/scale — enough to prove the backdrop + calibration + manual door placement, the genuine graceful-degradation floor.

- [ ] **Step 1: Create the interface**

`VisionPlanExtractor.java`:
```java
package nz.compliance.app.imports;

/** Seam over the vision LLM so the app runs without an API key and tests can stub it. */
public interface VisionPlanExtractor {
    PlanExtraction extract(RenderedImage image);
}
```

- [ ] **Step 2: Create the stub**

`StubVisionPlanExtractor.java`:
```java
package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default extractor (active unless the "claude" profile is on). Produces a single room covering the
 * image and nothing else, so the user always lands on the backdrop with something to calibrate and
 * correct — the graceful-degradation floor when no vision model is configured.
 */
@Component
@Profile("!claude")
public class StubVisionPlanExtractor implements VisionPlanExtractor {

    @Override
    public PlanExtraction extract(RenderedImage image) {
        double w = image.widthPx();
        double h = image.heightPx();
        ExtractedRoom whole = new ExtractedRoom("Room", "", List.of(
                new Point(0, 0), new Point(w, 0), new Point(w, h), new Point(0, h)), 0.1);
        return new PlanExtraction(List.of(whole), List.of(), null,
                List.of("No vision model configured — trace your plan over the backdrop."));
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app -am test-compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nz/compliance/app/imports/VisionPlanExtractor.java app/src/main/java/nz/compliance/app/imports/StubVisionPlanExtractor.java
git commit -m "feat(import): VisionPlanExtractor seam + no-API stub"
```

---

## Task 4: ImportDraftAssembler (pixels → draft) — TDD

**Files:**
- Test: `app/src/test/java/nz/compliance/app/imports/ImportDraftAssemblerTest.java`
- Create: `app/src/main/java/nz/compliance/app/imports/ImportDraftAssembler.java`

Pure, deterministic mapping from a pixel-space `PlanExtraction` to an `ImportDraft`: synthesize ids (`room-1`, `door-1`…), map door labels to room ids, fall back to the **nearest room centroid** when a door names no room, and drop/flag malformed elements (rooms with <3 points, doors without exactly 2 points). No silent defaults for safety numbers — missing widths become `0` for the user to set.

- [ ] **Step 1: Write the failing test**

`ImportDraftAssemblerTest.java`:
```java
package nz.compliance.app.imports;

import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportDraftAssemblerTest {

    private final ImportDraftAssembler assembler = new ImportDraftAssembler();
    private final RenderedImage image = new RenderedImage(new byte[]{1, 2, 3}, 200, 100);

    private static ExtractedRoom room(String label, double x0, double y0, double x1, double y1) {
        return new ExtractedRoom(label, "WB", List.of(
                new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1)), 0.9);
    }

    @Test
    void synthesizesIdsAndMapsAnExitDoorToTheNamedRoom() {
        PlanExtraction ex = new PlanExtraction(
                List.of(room("Office", 0, 0, 100, 100), room("Lobby", 100, 0, 200, 100)),
                List.of(new ExtractedDoor(List.of(new Point(0, 40), new Point(0, 60)),
                        List.of("Office"), true, 1200.0, 0.8)),
                new ScaleGuess(0.05, "scale-bar", 0.7), List.of());

        ImportDraft draft = assembler.assemble(image, ex);
        GeometryDoc g = draft.draftGeometryPx();

        assertThat(g.spaces()).extracting(Space::id).containsExactly("room-1", "room-2");
        assertThat(g.spaces()).extracting(Space::name).containsExactly("Office", "Lobby");
        assertThat(g.doors()).hasSize(1);
        Door d = g.doors().get(0);
        assertThat(d.id()).isEqualTo("door-1");
        assertThat(d.fromSpaceId()).isEqualTo("room-1");
        assertThat(d.toSpaceId()).isNull();           // exit -> discharges outside
        assertThat(d.exit()).isTrue();
        assertThat(d.clearWidthMillimetres()).isEqualTo(1200.0);
        assertThat(draft.scaleGuess().metresPerPixel()).isEqualTo(0.05);
        assertThat(draft.imageWidthPx()).isEqualTo(200);
        assertThat(draft.backdropPngBase64()).isNotBlank();
    }

    @Test
    void unlabeledDoorAttachesToNearestRoomCentroid() {
        PlanExtraction ex = new PlanExtraction(
                List.of(room("Office", 0, 0, 100, 100), room("Lobby", 100, 0, 200, 100)),
                // door midpoint x=150 is nearer Lobby's centroid (150,50) than Office's (50,50)
                List.of(new ExtractedDoor(List.of(new Point(150, 40), new Point(150, 60)),
                        List.of(), false, null, 0.5)),
                null, List.of());

        Door d = assembler.assemble(image, ex).draftGeometryPx().doors().get(0);

        assertThat(d.fromSpaceId()).isEqualTo("room-2");   // Lobby
        assertThat(d.clearWidthMillimetres()).isEqualTo(0.0); // unknown -> 0, user sets it
    }

    @Test
    void dropsDegenerateRoomsAndDoorsWithAWarning() {
        PlanExtraction ex = new PlanExtraction(
                List.of(new ExtractedRoom("Bad", "WB", List.of(new Point(0, 0), new Point(1, 1)), 0.2),
                        room("Office", 0, 0, 100, 100)),
                List.of(new ExtractedDoor(List.of(new Point(0, 40)), List.of("Office"), false, null, 0.3)),
                null, List.of());

        ImportDraft draft = assembler.assemble(image, ex);

        assertThat(draft.draftGeometryPx().spaces()).extracting(Space::name).containsExactly("Office");
        assertThat(draft.draftGeometryPx().doors()).isEmpty();
        assertThat(draft.warnings()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -Dtest=ImportDraftAssemblerTest -q`
Expected: FAIL — `ImportDraftAssembler` not found.

- [ ] **Step 3: Implement the assembler**

`ImportDraftAssembler.java`:
```java
package nz.compliance.app.imports;

import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure mapping: a pixel-space {@link PlanExtraction} -> an {@link ImportDraft} with synthesized ids. */
@Component
public class ImportDraftAssembler {

    public ImportDraft assemble(RenderedImage image, PlanExtraction ex) {
        List<String> warnings = new ArrayList<>(ex.warnings());
        List<Space> spaces = new ArrayList<>();
        Map<String, String> labelToId = new HashMap<>();   // first room with a given label wins
        Map<String, Point> centroidById = new HashMap<>();

        int n = 1;
        for (ExtractedRoom r : ex.rooms()) {
            if (r.polygonPx().size() < 3) {
                warnings.add("Dropped a room with fewer than 3 points (\"" + r.label() + "\").");
                continue;
            }
            String id = "room-" + n++;
            String name = (r.label() == null || r.label().isBlank()) ? id : r.label();
            String occ = r.occupancyTypeGuess() == null ? "" : r.occupancyTypeGuess();
            spaces.add(new Space(id, name, occ, r.polygonPx()));
            labelToId.putIfAbsent(r.label(), id);
            centroidById.put(id, centroid(r.polygonPx()));
        }

        List<Door> doors = new ArrayList<>();
        int d = 1;
        for (ExtractedDoor ed : ex.doors()) {
            if (ed.positionPx().size() != 2) {
                warnings.add("Dropped a door without exactly 2 points.");
                continue;
            }
            String fromId = resolveFrom(ed, labelToId, centroidById, midpoint(ed.positionPx()));
            if (fromId == null) {
                warnings.add("Dropped a door: no room to attach it to.");
                continue;
            }
            String toId = ed.exitGuess() ? null : resolveTo(ed, labelToId, fromId);
            double width = ed.clearWidthMmGuess() == null ? 0.0 : ed.clearWidthMmGuess();
            doors.add(new Door("door-" + d++, fromId, toId, ed.positionPx(), width, ed.exitGuess()));
        }

        GeometryDoc geo = new GeometryDoc(1, spaces, doors);
        String backdrop = Base64.getEncoder().encodeToString(image.pngBytes());
        return new ImportDraft(backdrop, image.widthPx(), image.heightPx(), geo, ex.scaleGuess(), warnings);
    }

    private static String resolveFrom(ExtractedDoor ed, Map<String, String> labelToId,
                                      Map<String, Point> centroidById, Point mid) {
        for (String label : ed.connectsRoomLabels()) {
            String id = labelToId.get(label);
            if (id != null) {
                return id;
            }
        }
        return nearest(centroidById, mid);
    }

    private static String resolveTo(ExtractedDoor ed, Map<String, String> labelToId, String fromId) {
        for (String label : ed.connectsRoomLabels()) {
            String id = labelToId.get(label);
            if (id != null && !id.equals(fromId)) {
                return id;
            }
        }
        return null;
    }

    private static String nearest(Map<String, Point> centroidById, Point p) {
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<String, Point> e : centroidById.entrySet()) {
            double dist = Math.hypot(e.getValue().x() - p.x(), e.getValue().y() - p.y());
            if (dist < bestD) {
                bestD = dist;
                best = e.getKey();
            }
        }
        return best;
    }

    private static Point centroid(List<Point> pts) {
        double x = 0, y = 0;
        for (Point p : pts) {
            x += p.x();
            y += p.y();
        }
        return new Point(x / pts.size(), y / pts.size());
    }

    private static Point midpoint(List<Point> seg) {
        return new Point((seg.get(0).x() + seg.get(1).x()) / 2.0, (seg.get(0).y() + seg.get(1).y()) / 2.0);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -Dtest=ImportDraftAssemblerTest -q`
Expected: PASS — 3 green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nz/compliance/app/imports/ImportDraftAssembler.java app/src/test/java/nz/compliance/app/imports/ImportDraftAssemblerTest.java
git commit -m "feat(import): ImportDraftAssembler (pixel extraction -> draft, synthesized ids)"
```

---

## Task 5: ImportService + ImportController (POST /api/imports) — TDD

**Files:**
- Create: `app/src/main/java/nz/compliance/app/imports/ImportService.java`
- Create: `app/src/main/java/nz/compliance/app/imports/ImportController.java`
- Test: `app/src/test/java/nz/compliance/app/imports/ImportControllerTest.java`

`ImportService` wires render → extract → assemble. The controller accepts a multipart upload and returns the `ImportDraft` as JSON. The test is a `@WebMvcTest` (web layer only — **no Postgres/Docker**) with a mocked `ImportService`.

- [ ] **Step 1: Create the service**

`ImportService.java`:
```java
package nz.compliance.app.imports;

import org.springframework.stereotype.Service;

/** Orchestrates an upload into an {@link ImportDraft}: render -> vision extract -> assemble. */
@Service
public class ImportService {

    private final PlanImageRenderer renderer;
    private final VisionPlanExtractor extractor;
    private final ImportDraftAssembler assembler;

    public ImportService(PlanImageRenderer renderer, VisionPlanExtractor extractor,
                         ImportDraftAssembler assembler) {
        this.renderer = renderer;
        this.extractor = extractor;
        this.assembler = assembler;
    }

    public ImportDraft importFrom(byte[] fileBytes) {
        RenderedImage image = renderer.render(fileBytes);
        PlanExtraction extraction = extractor.extract(image);
        return assembler.assemble(image, extraction);
    }
}
```

- [ ] **Step 2: Create the controller**

`ImportController.java`:
```java
package nz.compliance.app.imports;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api")
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/imports")
    public ImportDraft create(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no file uploaded");
        }
        try {
            return imports.importFrom(file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (UncheckedIOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not read the uploaded plan");
        }
    }
}
```

- [ ] **Step 3: Write the failing test**

`ImportControllerTest.java`:
```java
package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
class ImportControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ImportService imports;

    @Test
    void returnsDraftForAnUploadedFile() throws Exception {
        ImportDraft draft = new ImportDraft("base64png", 200, 100,
                new GeometryDoc(1, List.of(), List.of()), null, List.of());
        when(imports.importFrom(any())).thenReturn(draft);

        MockMultipartFile file = new MockMultipartFile("file", "plan.png", "image/png", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/imports").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageWidthPx").value(200))
                .andExpect(jsonPath("$.backdropPngBase64").value("base64png"));
    }

    @Test
    void rejectsAnEmptyUpload() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[]{});
        mvc.perform(multipart("/api/imports").file(empty))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 4: Run to verify it fails, then passes**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -Dtest=ImportControllerTest -q`
Expected: with the controller/service in place from Steps 1–2, this should PASS — 2 green. (If you wrote the test first, it fails to compile until the controller exists.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nz/compliance/app/imports/ImportService.java app/src/main/java/nz/compliance/app/imports/ImportController.java app/src/test/java/nz/compliance/app/imports/ImportControllerTest.java
git commit -m "feat(import): POST /api/imports endpoint + ImportService orchestration"
```

- [ ] **Step 6: Full app test run (local, no Docker)**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -q`
Expected: all existing unit + `@WebMvcTest` tests plus the new import tests PASS. (Postgres `*IT` tests are skipped locally and run in CI.)

---

## Task 6: Frontend — API client, conversion helpers, review canvas

**Files:**
- Create: `frontend/src/api/imports.ts`
- Create: `frontend/src/editor/importConvert.ts`
- Test: `frontend/src/editor/importConvert.test.ts`
- Create: `frontend/src/editor/ImportReviewCanvas.tsx`
- Test: `frontend/src/editor/ImportReviewCanvas.test.tsx`

- [ ] **Step 1: Create the API client**

`frontend/src/api/imports.ts`:
```ts
import type { GeometryDoc } from './types'

export interface ScaleGuess { metresPerPixel: number; source: string; confidence: number }

export interface ImportDraft {
  backdropPngBase64: string
  imageWidthPx: number
  imageHeightPx: number
  draftGeometryPx: GeometryDoc   // coordinates are IMAGE PIXELS until Confirm
  scaleGuess: ScaleGuess | null
  warnings: string[]
}

/** Upload a PDF/image; the browser sets the multipart boundary, so do NOT set Content-Type. */
export async function uploadImport(file: File): Promise<ImportDraft> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch('/api/imports', { method: 'POST', body: form })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`)
  return res.json() as Promise<ImportDraft>
}
```

- [ ] **Step 2: Write the failing conversion test**

`frontend/src/editor/importConvert.test.ts`:
```ts
import { describe, it, expect } from 'vitest'
import { metresPerPixel, pxGeometryToMetres } from './importConvert'
import type { GeometryDoc } from '../api/types'

describe('importConvert', () => {
  it('computes metres per pixel from a known length', () => {
    expect(metresPerPixel({ x: 0, y: 0 }, { x: 200, y: 0 }, 10)).toBeCloseTo(0.05)
  })

  it('throws when calibration points coincide', () => {
    expect(() => metresPerPixel({ x: 5, y: 5 }, { x: 5, y: 5 }, 10)).toThrow()
  })

  it('scales pixel coords to metres but leaves door widths alone', () => {
    const px: GeometryDoc = {
      schemaVersion: 1,
      spaces: [{ id: 'room-1', name: 'Office', occupancyType: 'WB',
        polygon: [{ x: 0, y: 0 }, { x: 200, y: 0 }, { x: 200, y: 200 }, { x: 0, y: 200 }] }],
      doors: [{ id: 'door-1', fromSpaceId: 'room-1', toSpaceId: null,
        position: [{ x: 0, y: 80 }, { x: 0, y: 120 }], clearWidthMillimetres: 1200, exit: true }],
    }
    const m = pxGeometryToMetres(px, 0.05)
    expect(m.spaces[0].polygon[1]).toEqual({ x: 10, y: 0 })
    expect(m.doors[0].position[1]).toEqual({ x: 0, y: 6 })
    expect(m.doors[0].clearWidthMillimetres).toBe(1200) // mm, unchanged
  })
})
```

Run: `cd frontend && npm run test -- importConvert` → Expected: FAIL (module not found).

- [ ] **Step 3: Implement the conversion helpers**

`frontend/src/editor/importConvert.ts`:
```ts
import type { GeometryDoc, Point } from '../api/types'

/** Real-world metres per image pixel, from two points and the known real length between them. */
export function metresPerPixel(a: Point, b: Point, knownMetres: number): number {
  const px = Math.hypot(a.x - b.x, a.y - b.y)
  if (px === 0) throw new Error('calibration points must differ')
  return knownMetres / px
}

/** Scale a pixel-space GeometryDoc into metres. Door clear widths (mm) are left untouched. */
export function pxGeometryToMetres(geo: GeometryDoc, mpp: number): GeometryDoc {
  const s = (p: Point): Point => ({ x: p.x * mpp, y: p.y * mpp })
  return {
    schemaVersion: geo.schemaVersion,
    spaces: geo.spaces.map((sp) => ({ ...sp, polygon: sp.polygon.map(s) })),
    doors: geo.doors.map((d) => ({ ...d, position: d.position.map(s) })),
  }
}
```

Run: `cd frontend && npm run test -- importConvert` → Expected: PASS (3 green).

- [ ] **Step 4: Write the failing review-canvas test**

`frontend/src/editor/ImportReviewCanvas.test.tsx`:
```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import ImportReviewCanvas from './ImportReviewCanvas'
import type { ImportDraft, ScaleGuess } from '../api/imports'

const makeDraft = (scaleGuess: ScaleGuess | null): ImportDraft => ({
  backdropPngBase64: 'AAAA',
  imageWidthPx: 200,
  imageHeightPx: 100,
  draftGeometryPx: {
    schemaVersion: 1,
    spaces: [{ id: 'room-1', name: 'Office', occupancyType: 'WB',
      polygon: [{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 100, y: 100 }, { x: 0, y: 100 }] }],
    doors: [{ id: 'door-1', fromSpaceId: 'room-1', toSpaceId: null,
      position: [{ x: 0, y: 40 }, { x: 0, y: 60 }], clearWidthMillimetres: 1200, exit: true }],
  },
  scaleGuess,
  warnings: [],
})

describe('ImportReviewCanvas', () => {
  it('renders the backdrop and draft; Confirm disabled with no scale', () => {
    render(<ImportReviewCanvas draft={makeDraft(null)} onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(document.querySelector('image')).not.toBeNull()
    expect(document.querySelectorAll('polygon')).toHaveLength(1)
    expect(screen.getByRole('button', { name: /confirm/i })).toBeDisabled()
  })

  it('confirms geometry in metres when a scale is known', () => {
    const onConfirm = vi.fn()
    render(<ImportReviewCanvas draft={makeDraft({ metresPerPixel: 0.05, source: 'scale-bar', confidence: 0.8 })}
                               onConfirm={onConfirm} onCancel={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: /confirm/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onConfirm.mock.calls[0][0].spaces[0].polygon[1]).toEqual({ x: 5, y: 0 }) // 100px * 0.05
  })
})
```

Run: `cd frontend && npm run test -- ImportReviewCanvas` → Expected: FAIL (module not found).

- [ ] **Step 5: Implement the review canvas**

`frontend/src/editor/ImportReviewCanvas.tsx`:
```tsx
import { useState } from 'react'
import type { GeometryDoc, Point } from '../api/types'
import type { ImportDraft } from '../api/imports'
import { metresPerPixel, pxGeometryToMetres } from './importConvert'

interface Props {
  draft: ImportDraft
  onConfirm: (geometryMetres: GeometryDoc) => void
  onCancel: () => void
}

export default function ImportReviewCanvas({ draft, onConfirm, onCancel }: Props) {
  const [geo, setGeo] = useState<GeometryDoc>(draft.draftGeometryPx)
  const [mpp, setMpp] = useState<number | null>(draft.scaleGuess?.metresPerPixel ?? null)
  const [calPts, setCalPts] = useState<Point[]>([])
  const [knownMetres, setKnownMetres] = useState(1)

  const W = draft.imageWidthPx
  const H = draft.imageHeightPx
  const poly = (pts: Point[]) => pts.map((p) => `${p.x},${p.y}`).join(' ')

  function onSvgClick(e: React.MouseEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    const x = ((e.clientX - rect.left) / rect.width) * W
    const y = ((e.clientY - rect.top) / rect.height) * H
    setCalPts((p) => (p.length >= 2 ? [{ x, y }] : [...p, { x, y }]))
  }
  function applyCalibration() {
    if (calPts.length === 2) setMpp(metresPerPixel(calPts[0], calPts[1], knownMetres))
  }
  function setOccupancy(id: string, occ: string) {
    setGeo((g) => ({ ...g, spaces: g.spaces.map((s) => (s.id === id ? { ...s, occupancyType: occ } : s)) }))
  }
  function setDoor(id: string, patch: Partial<{ exit: boolean; clearWidthMillimetres: number }>) {
    setGeo((g) => ({ ...g, doors: g.doors.map((d) => (d.id === id ? { ...d, ...patch } : d)) }))
  }
  function confirm() { if (mpp != null) onConfirm(pxGeometryToMetres(geo, mpp)) }

  return (
    <section style={{ border: '2px solid #3367d6', padding: 8, marginBottom: 12 }}>
      <h2>Review imported plan</h2>
      {draft.warnings.map((w, i) => <p key={i} style={{ color: '#b06000' }}>⚠️ {w}</p>)}
      <svg viewBox={`0 0 ${W} ${H}`} width={800} style={{ border: '1px solid #ccc', maxWidth: '100%' }}
           onClick={onSvgClick}>
        <image href={`data:image/png;base64,${draft.backdropPngBase64}`} x={0} y={0} width={W} height={H} />
        {geo.spaces.map((s) => (
          <polygon key={s.id} points={poly(s.polygon)} fill="rgba(51,103,214,0.15)" stroke="#3367d6" />
        ))}
        {geo.doors.map((d) => (
          <line key={d.id} x1={d.position[0].x} y1={d.position[0].y} x2={d.position[1].x} y2={d.position[1].y}
                stroke={d.exit ? '#0b8043' : '#999'} strokeWidth={3} />
        ))}
        {calPts.length > 0 && (
          <polyline points={poly(calPts)} fill="none" stroke="#ff6d00" strokeWidth={2} strokeDasharray="4" />
        )}
      </svg>

      <fieldset>
        <legend>Scale</legend>
        <p>{mpp == null ? 'Not set — calibrate before checking.' : `${mpp.toFixed(4)} m / pixel`}</p>
        <p style={{ fontSize: 12 }}>Click two points of a known length on the plan, enter its real length, then Apply.</p>
        <label>Known length (m){' '}
          <input type="number" value={knownMetres} min={0} step={0.1}
                 onChange={(e) => setKnownMetres(Number(e.target.value))} style={{ width: 72 }} />
        </label>{' '}
        <button onClick={applyCalibration} disabled={calPts.length !== 2}>Apply calibration</button>
      </fieldset>

      <fieldset>
        <legend>Rooms</legend>
        {geo.spaces.map((s) => (
          <div key={s.id}>
            {s.name}{' '}
            <select value={s.occupancyType} onChange={(e) => setOccupancy(s.id, e.target.value)}>
              <option value="">use…</option>
              <option value="WB">WB — working/business</option>
              <option value="CA">CA — crowd activity</option>
            </select>
          </div>
        ))}
      </fieldset>

      <fieldset>
        <legend>Doors</legend>
        {geo.doors.map((d) => (
          <div key={d.id}>
            {d.id}{' '}
            <label><input type="checkbox" checked={d.exit}
                          onChange={(e) => setDoor(d.id, { exit: e.target.checked })} /> exit</label>{' '}
            <label>width (mm){' '}
              <input type="number" value={d.clearWidthMillimetres} min={0} step={50}
                     onChange={(e) => setDoor(d.id, { clearWidthMillimetres: Number(e.target.value) })}
                     style={{ width: 80 }} />
            </label>
          </div>
        ))}
      </fieldset>

      <button onClick={confirm} disabled={mpp == null}>Confirm &amp; load into editor</button>{' '}
      <button onClick={onCancel}>Cancel</button>
    </section>
  )
}
```

Run: `cd frontend && npm run test -- ImportReviewCanvas` → Expected: PASS (2 green).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/imports.ts frontend/src/editor/importConvert.ts frontend/src/editor/importConvert.test.ts frontend/src/editor/ImportReviewCanvas.tsx frontend/src/editor/ImportReviewCanvas.test.tsx
git commit -m "feat(import): frontend api + px->metre conversion + backdrop review canvas"
```

> **Scope note (YAGNI):** the review canvas confirms the semantically-critical fields (scale, exits, occupancy, widths) and drops bad elements. **Vertex-level dragging** of room corners over the backdrop is a fast-follow — after Confirm the geometry loads into the existing metres editor, where the user already has space/door tools to fine-tune.

---

## Task 7: Frontend — wire import into the editor

**Files:**
- Modify: `frontend/src/editor/EditorPage.tsx`

- [ ] **Step 1: Add imports** (top of `EditorPage.tsx`, with the other imports)

```tsx
import { uploadImport, type ImportDraft } from '../api/imports'
import ImportReviewCanvas from './ImportReviewCanvas'
```

- [ ] **Step 2: Add state** (with the other `useState` calls, e.g. after the `error` state)

```tsx
const [importDraft, setImportDraft] = useState<ImportDraft | null>(null)
```

- [ ] **Step 3: Add the upload handler** (next to `save`/`runCheck`)

```tsx
async function onImportFile(e: React.ChangeEvent<HTMLInputElement>) {
  const file = e.target.files?.[0]
  if (!file) return
  setError(null)
  try { setImportDraft(await uploadImport(file)) }
  catch (err) { setError(String(err)) }
  finally { e.target.value = '' }   // allow re-selecting the same file
}
```

- [ ] **Step 4: Add the upload control + review panel to the JSX.** Immediately after the `<h1>compliance-checker — editor</h1>` line, insert:

```tsx
      <label style={{ display: 'block', margin: '8px 0' }}>
        Import plan (PDF/image):{' '}
        <input type="file" accept=".pdf,image/*" onChange={onImportFile} />
      </label>
      {importDraft && (
        <ImportReviewCanvas
          draft={importDraft}
          onConfirm={(geo) => { fp.setDoc(geo); setImportDraft(null) }}
          onCancel={() => setImportDraft(null)}
        />
      )}
```

When `onConfirm` fires, `fp.setDoc(geo)` loads the metres geometry into the existing editor; the user then sets building context and clicks **Check** exactly as before — the engine computes the egress, the user never drew it.

- [ ] **Step 5: Type-check, test, and build the frontend**

Run:
```bash
cd frontend && npm run test && npm run build
```
Expected: all Vitest suites PASS (including the new `importConvert` and `ImportReviewCanvas` suites); `tsc -b && vite build` succeeds.

- [ ] **Step 6: Manual demo checkpoint** (on a machine with Docker/Postgres — the backend needs the DB to persist/check; the editor runs in the sandbox, the backend on your machine)

```bash
# backend host:
docker compose up -d db
export JAVA_HOME=/home/node/.local/jdk-21.0.11+10
./mvnw -pl app -am spring-boot:run            # :8080  (stub extractor; no API key needed)
# frontend:
cd frontend && npm run dev                    # :5173
```
In the browser: **Import plan → pick a PDF/PNG → land on the backdrop → calibrate (click two points of a known length, enter metres, Apply) → set occupancy + exits → Confirm → set building context → Check.** Confirm violations are computed and located.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/editor/EditorPage.tsx
git commit -m "feat(import): upload entry + review mode wired into the editor"
```

---

## Task 8: Real Claude vision extractor — TDD (parsing)

**Files:**
- Create: `app/src/main/java/nz/compliance/app/imports/ClaudeVisionPlanExtractor.java`
- Test: `app/src/test/java/nz/compliance/app/imports/ClaudeVisionPlanExtractorParseTest.java`

Active only under the **`claude`** profile (so the default/test/CI path keeps using the stub — no API key). The `ChatModel` is built lazily, like `LangChain4jRuleExtractor`. We TDD the **JSON parsing** (`parse()`), which needs no API; the live call is exercised by the Task 9 eval harness.

> Jackson deserializes the records by constructor parameter name; the parent POM already compiles with `-parameters` (added for Spring MVC), which makes this work for `PlanExtraction`/`ExtractedRoom`/`ExtractedDoor`/`ScaleGuess`/`Point`.

- [ ] **Step 1: Write the failing parse test**

`ClaudeVisionPlanExtractorParseTest.java`:
```java
package nz.compliance.app.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeVisionPlanExtractorParseTest {

    private final ClaudeVisionPlanExtractor extractor = new ClaudeVisionPlanExtractor("");

    @Test
    void parsesJsonIntoAnExtraction() {
        String json = """
            {"rooms":[{"label":"Office","occupancyTypeGuess":"WB",
                       "polygonPx":[{"x":0,"y":0},{"x":10,"y":0},{"x":10,"y":10}],"confidence":0.9}],
             "doors":[{"positionPx":[{"x":0,"y":4},{"x":0,"y":6}],"connectsRoomLabels":["Office"],
                       "exitGuess":true,"clearWidthMmGuess":1200,"confidence":0.8}],
             "scaleGuess":{"metresPerPixel":0.05,"source":"scale-bar","confidence":0.7},
             "warnings":[]}""";

        PlanExtraction ex = extractor.parse(json);

        assertThat(ex.rooms()).hasSize(1);
        assertThat(ex.rooms().get(0).label()).isEqualTo("Office");
        assertThat(ex.doors().get(0).exitGuess()).isTrue();
        assertThat(ex.scaleGuess().metresPerPixel()).isEqualTo(0.05);
    }

    @Test
    void stripsMarkdownFences() {
        String fenced = "```json\n{\"rooms\":[],\"doors\":[],\"scaleGuess\":null,\"warnings\":[]}\n```";
        assertThat(extractor.parse(fenced).rooms()).isEmpty();
    }

    @Test
    void malformedOutputDegradesToAWarning() {
        PlanExtraction ex = extractor.parse("sorry, I cannot read this");
        assertThat(ex.rooms()).isEmpty();
        assertThat(ex.warnings()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -Dtest=ClaudeVisionPlanExtractorParseTest -q`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement the extractor**

`ClaudeVisionPlanExtractor.java`:
```java
package nz.compliance.app.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/** Real vision extractor (active under the "claude" profile). Lazily built so the app boots without a key. */
@Component
@Profile("claude")
public class ClaudeVisionPlanExtractor implements VisionPlanExtractor {

    private static final String SYSTEM = """
        You extract a building floor plan from an image for a fire-egress tool.
        Return ONLY JSON (no prose, no markdown fences) of the form:
        {"rooms":[{"label":string,"occupancyTypeGuess":"WB"|"CA"|"","polygonPx":[{"x":number,"y":number}],"confidence":number}],
         "doors":[{"positionPx":[{"x":number,"y":number},{"x":number,"y":number}],"connectsRoomLabels":[string],
                   "exitGuess":boolean,"clearWidthMmGuess":number|null,"confidence":number}],
         "scaleGuess":{"metresPerPixel":number,"source":string,"confidence":number}|null,
         "warnings":[string]}
        Coordinates are IMAGE PIXELS, origin top-left. A door's positionPx is its 2-point opening.
        Mark exitGuess=true only if a door clearly discharges outside / to a final exit.
        If you cannot read a scale bar or dimension, set scaleGuess to null. Never invent numbers.""";

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile ChatModel model;

    public ClaudeVisionPlanExtractor(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public PlanExtraction extract(RenderedImage image) {
        String base64 = Base64.getEncoder().encodeToString(image.pngBytes());
        UserMessage user = UserMessage.from(
                ImageContent.from(base64, "image/png"),
                TextContent.from("Extract the floor plan. Image is "
                        + image.widthPx() + "x" + image.heightPx() + " px."));
        String json = model().chat(SystemMessage.from(SYSTEM), user).aiMessage().text();
        return parse(json);
    }

    /** Parse the model's text into a PlanExtraction; malformed output degrades to a warning-only result. */
    PlanExtraction parse(String raw) {
        try {
            return mapper.readValue(stripFences(raw), PlanExtraction.class);
        } catch (Exception e) {
            return new PlanExtraction(List.of(), List.of(), null,
                    List.of("Could not parse the vision output — trace over the backdrop instead."));
        }
    }

    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("(?s)^```[a-zA-Z]*\\n", "").replaceFirst("(?s)\\n```\\s*$", "");
        }
        return t;
    }

    private ChatModel model() {
        ChatModel local = model;
        if (local == null) {
            synchronized (this) {
                local = model;
                if (local == null) {
                    model = local = AnthropicChatModel.builder()
                            .apiKey(apiKey).modelName("claude-sonnet-4-6").maxTokens(4096).build();
                }
            }
        }
        return local;
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -Dtest=ClaudeVisionPlanExtractorParseTest -q`
Expected: PASS — 3 green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/nz/compliance/app/imports/ClaudeVisionPlanExtractor.java app/src/test/java/nz/compliance/app/imports/ClaudeVisionPlanExtractorParseTest.java
git commit -m "feat(import): real Claude vision extractor (claude profile, lazy, parse-tested)"
```

> **To use Claude at runtime:** start the app with `-Dspring.profiles.active=claude` and `ANTHROPIC_API_KEY` set. The `claude` profile activates `ClaudeVisionPlanExtractor` and deactivates `StubVisionPlanExtractor` (`@Profile("!claude")`), so exactly one `VisionPlanExtractor` bean is ever present.

---

## Task 9 (optional): Vision eval harness

**Files:**
- Create: `app/src/test/java/nz/compliance/app/imports/VisionPlanExtractorEvalTest.java`

A `@Tag("eval")` metric run — **excluded from CI** by the existing surefire `excludedGroups=eval`. It needs `ANTHROPIC_API_KEY` and real plan images dropped in `app/src/test/resources/import-gold/`; it logs room/door/scale counts and never fails. The mirror of the rule gold-set eval.

- [ ] **Step 1: Create the harness**

`VisionPlanExtractorEvalTest.java`:
```java
package nz.compliance.app.imports;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Tag("eval")
class VisionPlanExtractorEvalTest {

    @Test
    void reportsExtractionMetricsOnGoldImages() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "no ANTHROPIC_API_KEY; skipping eval");
        Path dir = Path.of("src/test/resources/import-gold");
        Assumptions.assumeTrue(Files.isDirectory(dir), "no import-gold images; skipping eval");

        ClaudeVisionPlanExtractor extractor = new ClaudeVisionPlanExtractor(key);
        PlanImageRenderer renderer = new PlanImageRenderer();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path img : files.filter(p -> p.toString().matches(".*\\.(png|jpg|jpeg|pdf)")).toList()) {
                RenderedImage rendered = renderer.render(Files.readAllBytes(img));
                PlanExtraction ex = extractor.extract(rendered);
                System.out.printf("[eval] %s -> rooms=%d doors=%d scale=%s%n",
                        img.getFileName(), ex.rooms().size(), ex.doors().size(),
                        ex.scaleGuess() == null ? "null" : ex.scaleGuess().metresPerPixel());
            }
        }
    }
}
```

- [ ] **Step 2: Verify it is excluded from the normal run**

Run: `export JAVA_HOME=/home/node/.local/jdk-21.0.11+10 && ./mvnw -pl app test -q`
Expected: PASS; the eval test does **not** run (no `[eval]` output) because `excludedGroups=eval` is configured in `app/pom.xml`.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/nz/compliance/app/imports/VisionPlanExtractorEvalTest.java
git commit -m "test(import): vision eval harness (@Tag eval, excluded from CI)"
```

---

## Definition of done

- `./mvnw -pl app test` green locally (renderer, assembler, `@WebMvcTest` controller, Claude parse) — no Docker needed.
- `cd frontend && npm run test && npm run build` green (conversion + review-canvas suites).
- Manual demo: upload a PDF/image → backdrop + draft → calibrate → confirm → existing Check computes located violations.
- Push to `main`; **CI green** (CI also runs the existing Postgres `*IT` suites, which this feature does not touch).
- The engine, rules, and check path are **unchanged**; the LLM is only in the ingestion path; the user supplies geometry, the engine computes egress.

## Self-review notes

- **Spec coverage:** §4 architecture → Tasks 1–7 (ingestion layer; engine untouched). §5 components → `PlanImageRenderer` (T2), `VisionPlanExtractor` seam (T3), `ImportDraftAssembler` (T4), `ImportController`/`ImportService` (T5). §6 DTOs → T1. §7 data flow → T5 (backend) + T6–T7 (frontend). §8 scale → `importConvert` + calibration UI (T6) + `scaleGuess` plumbed end-to-end. §9 error handling/degradation → empty-upload 400 (T5), unreadable file → 400, stub/parse fallback warnings (T3/T8), Confirm blocked until scale set (T6). §10 testing → unit + `@WebMvcTest` + eval (T9). §11 #1 separation-of-input-from-analysis → the engine/check are never modified; the review UI edits geometry only.
- **Deliberate deviation:** the spec said "EditorCanvas gains a backdrop layer"; the plan uses a dedicated `ImportReviewCanvas` (pixel space, calibration) and reuses the existing metres editor *after* Confirm. Cleaner decomposition; the spec's intent (backdrop-assisted correction) is preserved.
- **Type consistency:** record names/fields (`PlanExtraction`, `ExtractedRoom.polygonPx`, `ExtractedDoor.positionPx/connectsRoomLabels/exitGuess/clearWidthMmGuess`, `ScaleGuess.metresPerPixel`, `ImportDraft.draftGeometryPx`) are used identically across Tasks 1/4/5/8; the TS `ImportDraft`/`ScaleGuess` mirror them. `uploadImport`→`ImportReviewCanvas`→`onConfirm(GeometryDoc)`→`fp.setDoc` chain is consistent.
- **Toolchain caveats:** `@MockBean` is used in T5; on Spring Boot ≥3.4 it is deprecated — if a warning bothers you, swap to `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`). PDFBox rendering is headless-safe. LangChain4j `ChatModel.chat(ChatMessage...)`→`ChatResponse.aiMessage().text()` per 1.16.2.

