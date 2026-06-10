# Engine Core — Geometry Facts Implementation Plan (Plan 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the pure `engine` module, turn a `GeometryDoc` into JTS geometry and compute the **spatial facts** every rule will read: space area (m²), occupant load (from occupant density), and exit-door count + total exit width. No thresholds here — those are rule-driven (Plan 5); this plan computes raw, deterministic facts only.

**Architecture:** A thin JTS adapter converts a `Space` polygon to a `org.locationtech.jts.geom.Polygon`; an `OccupantDensity` table maps occupancy type → m²/person; `FactsComputer.compute(GeometryDoc)` returns an immutable `PlanFacts`. Everything is static/pure and unit-tested without Spring, DB, or web. **Units: metres, m², millimetres, m²/person.**

**Tech Stack:** JTS (Java Topology Suite) `jts-core`, JUnit 5 + AssertJ.

**Prerequisite:** Plan 2 complete (the `nz.compliance.engine.model` geometry types exist).

---

## File map

```
engine/pom.xml                                              # + jts-core
engine/src/main/java/nz/compliance/engine/geometry/JtsAdapter.java
engine/src/main/java/nz/compliance/engine/facts/OccupantDensity.java
engine/src/main/java/nz/compliance/engine/facts/SpaceFacts.java
engine/src/main/java/nz/compliance/engine/facts/PlanFacts.java
engine/src/main/java/nz/compliance/engine/facts/FactsComputer.java
engine/src/test/java/nz/compliance/engine/geometry/JtsAdapterTest.java
engine/src/test/java/nz/compliance/engine/facts/OccupantDensityTest.java
engine/src/test/java/nz/compliance/engine/facts/FactsComputerTest.java
```

---

## Task 1: JTS adapter — polygon, area, validity (TDD)

**Files:**
- Modify: `engine/pom.xml`
- Test: `engine/src/test/java/nz/compliance/engine/geometry/JtsAdapterTest.java`
- Create: `engine/src/main/java/nz/compliance/engine/geometry/JtsAdapter.java`

- [ ] **Step 1: Add JTS to `engine/pom.xml`** (inside `<dependencies>`, before the test deps)

```xml
    <dependency>
      <groupId>org.locationtech.jts</groupId>
      <artifactId>jts-core</artifactId>
      <version>1.20.0</version>
    </dependency>
```

- [ ] **Step 2: Write the failing test**

`engine/src/test/java/nz/compliance/engine/geometry/JtsAdapterTest.java`:
```java
package nz.compliance.engine.geometry;

import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JtsAdapterTest {

    private static Space space(String id, List<Point> pts) {
        return new Space(id, id, "WB", pts);
    }

    @Test
    void area_ofTenByTenSquare_is100() {
        Space s = space("s1", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
        assertThat(JtsAdapter.areaSquareMetres(s)).isEqualTo(100.0);
    }

    @Test
    void isValid_trueForSimpleSquare() {
        Space s = space("s1", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
        assertThat(JtsAdapter.isValid(s)).isTrue();
    }

    @Test
    void isValid_falseForSelfIntersectingBowtie() {
        // bow-tie: edges cross -> invalid polygon
        Space s = space("s1", List.of(
                new Point(0, 0), new Point(10, 10), new Point(10, 0), new Point(0, 10)));
        assertThat(JtsAdapter.isValid(s)).isFalse();
    }

    @Test
    void isValid_falseForFewerThanThreePoints() {
        Space s = space("s1", List.of(new Point(0, 0), new Point(1, 1)));
        assertThat(JtsAdapter.isValid(s)).isFalse();
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=JtsAdapterTest`
Expected: FAIL — `JtsAdapter` not found.

- [ ] **Step 4: Implement the adapter**

`engine/src/main/java/nz/compliance/engine/geometry/JtsAdapter.java`:
```java
package nz.compliance.engine.geometry;

import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

/** Converts the engine's geometry model into JTS geometry (all coordinates in metres). */
public final class JtsAdapter {

    private static final GeometryFactory GF = new GeometryFactory();

    private JtsAdapter() {
    }

    /** Builds a closed JTS polygon from a space's vertices. */
    public static Polygon toPolygon(Space space) {
        List<Point> pts = space.polygon();
        Coordinate[] coords = new Coordinate[pts.size() + 1];
        for (int i = 0; i < pts.size(); i++) {
            coords[i] = new Coordinate(pts.get(i).x(), pts.get(i).y());
        }
        coords[pts.size()] = coords[0]; // close the ring
        LinearRing ring = GF.createLinearRing(coords);
        return GF.createPolygon(ring);
    }

    public static double areaSquareMetres(Space space) {
        return toPolygon(space).getArea();
    }

    /** True if the space forms a valid simple polygon (≥3 points, non-self-intersecting). */
    public static boolean isValid(Space space) {
        if (space.polygon().size() < 3) {
            return false;
        }
        try {
            return toPolygon(space).isValid();
        } catch (IllegalArgumentException e) {
            return false; // degenerate ring
        }
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=JtsAdapterTest`
Expected: PASS — 4 green.

- [ ] **Step 6: Commit**

```bash
git -C /workspace add engine/pom.xml engine/src/main/java/nz/compliance/engine/geometry engine/src/test/java/nz/compliance/engine/geometry
git -C /workspace commit -m "feat(engine): JTS adapter for polygon area and validity"
```

---

## Task 2: Occupant-density table (TDD)

> ⚠️ The numbers here are **illustrative placeholders**. Replace with the authoritative NZBC C/AS2 occupant-density values during domain validation (spec §15). The lookup contract stays the same.

**Files:**
- Test: `engine/src/test/java/nz/compliance/engine/facts/OccupantDensityTest.java`
- Create: `engine/src/main/java/nz/compliance/engine/facts/OccupantDensity.java`

- [ ] **Step 1: Write the failing test**

`engine/src/test/java/nz/compliance/engine/facts/OccupantDensityTest.java`:
```java
package nz.compliance.engine.facts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OccupantDensityTest {

    @Test
    void knownTypes_returnConfiguredDensity() {
        assertThat(OccupantDensity.squareMetresPerPerson("WB")).isEqualTo(10.0);
        assertThat(OccupantDensity.squareMetresPerPerson("CA")).isEqualTo(1.0);
    }

    @Test
    void unknownType_fallsBackToDefault() {
        assertThat(OccupantDensity.squareMetresPerPerson("ZZ")).isEqualTo(10.0);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=OccupantDensityTest`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement the table**

`engine/src/main/java/nz/compliance/engine/facts/OccupantDensity.java`:
```java
package nz.compliance.engine.facts;

import java.util.Map;

/**
 * Floor area per person (m²/person) keyed by occupancy/use type.
 * v1 values are ILLUSTRATIVE placeholders pending NZBC C/AS2 confirmation.
 */
public final class OccupantDensity {

    private static final Map<String, Double> SQM_PER_PERSON = Map.of(
            "WB", 10.0,  // working / business (illustrative)
            "CA", 1.0    // crowd activity (illustrative)
    );

    private static final double DEFAULT_SQM_PER_PERSON = 10.0;

    private OccupantDensity() {
    }

    public static double squareMetresPerPerson(String occupancyType) {
        return SQM_PER_PERSON.getOrDefault(occupancyType, DEFAULT_SQM_PER_PERSON);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=OccupantDensityTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git -C /workspace add engine/src/main/java/nz/compliance/engine/facts/OccupantDensity.java engine/src/test/java/nz/compliance/engine/facts/OccupantDensityTest.java
git -C /workspace commit -m "feat(engine): occupant-density lookup table"
```

---

## Task 3: FactsComputer + PlanFacts (TDD)

**Files:**
- Test: `engine/src/test/java/nz/compliance/engine/facts/FactsComputerTest.java`
- Create: `engine/src/main/java/nz/compliance/engine/facts/SpaceFacts.java`
- Create: `engine/src/main/java/nz/compliance/engine/facts/PlanFacts.java`
- Create: `engine/src/main/java/nz/compliance/engine/facts/FactsComputer.java`

- [ ] **Step 1: Write the failing test**

`engine/src/test/java/nz/compliance/engine/facts/FactsComputerTest.java`:
```java
package nz.compliance.engine.facts;

import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FactsComputerTest {

    private static Space square(String id, String type, double side) {
        return new Space(id, id, type, List.of(
                new Point(0, 0), new Point(side, 0), new Point(side, side), new Point(0, side)));
    }

    @Test
    void computesAreaAndOccupantLoadPerSpace() {
        // 10x10 WB space = 100 m², density 10 m²/person => 10 occupants
        GeometryDoc doc = new GeometryDoc(1, List.of(square("s1", "WB", 10)), List.of());

        PlanFacts facts = FactsComputer.compute(doc);

        assertThat(facts.spaces()).hasSize(1);
        assertThat(facts.spaces().get(0).areaSquareMetres()).isEqualTo(100.0);
        assertThat(facts.spaces().get(0).occupantLoad()).isCloseTo(10.0, within(1e-9));
        assertThat(facts.totalOccupantLoad()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void countsExitDoorsAndSumsExitWidth() {
        GeometryDoc doc = new GeometryDoc(1,
                List.of(square("s1", "WB", 10)),
                List.of(
                        new Door("d1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true),
                        new Door("d2", "s1", null, List.of(new Point(10, 4), new Point(10, 6)), 900, true),
                        new Door("d3", "s1", null, List.of(new Point(4, 0), new Point(6, 0)), 800, false)));

        PlanFacts facts = FactsComputer.compute(doc);

        assertThat(facts.exitDoorCount()).isEqualTo(2);
        assertThat(facts.totalExitWidthMillimetres()).isEqualTo(2100.0);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=FactsComputerTest`
Expected: FAIL — classes not found.

- [ ] **Step 3: Implement the records and computer**

`engine/src/main/java/nz/compliance/engine/facts/SpaceFacts.java`:
```java
package nz.compliance.engine.facts;

/** Per-space computed facts. occupantLoad is raw (un-rounded); rounding is a rule concern. */
public record SpaceFacts(String spaceId, double areaSquareMetres, double occupantLoad) {
}
```

`engine/src/main/java/nz/compliance/engine/facts/PlanFacts.java`:
```java
package nz.compliance.engine.facts;

import java.util.List;
import java.util.Optional;

/** Immutable bundle of spatial facts for a whole floor plan. */
public record PlanFacts(List<SpaceFacts> spaces, double totalOccupantLoad,
                        int exitDoorCount, double totalExitWidthMillimetres) {

    public PlanFacts {
        spaces = List.copyOf(spaces);
    }

    public Optional<SpaceFacts> space(String id) {
        return spaces.stream().filter(s -> s.spaceId().equals(id)).findFirst();
    }
}
```

`engine/src/main/java/nz/compliance/engine/facts/FactsComputer.java`:
```java
package nz.compliance.engine.facts;

import nz.compliance.engine.geometry.JtsAdapter;
import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Space;

import java.util.ArrayList;
import java.util.List;

/** Computes raw spatial facts from a geometry document. Pure and deterministic. */
public final class FactsComputer {

    private FactsComputer() {
    }

    public static PlanFacts compute(GeometryDoc doc) {
        List<SpaceFacts> spaceFacts = new ArrayList<>();
        double totalOccupants = 0;
        for (Space s : doc.spaces()) {
            double area = JtsAdapter.areaSquareMetres(s);
            double occupants = area / OccupantDensity.squareMetresPerPerson(s.occupancyType());
            spaceFacts.add(new SpaceFacts(s.id(), area, occupants));
            totalOccupants += occupants;
        }

        int exitCount = 0;
        double exitWidth = 0;
        for (Door d : doc.doors()) {
            if (d.exit()) {
                exitCount++;
                exitWidth += d.clearWidthMillimetres();
            }
        }

        return new PlanFacts(spaceFacts, totalOccupants, exitCount, exitWidth);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=FactsComputerTest`
Expected: PASS — 2 green.

- [ ] **Step 5: Full engine test run**

Run: `cd /workspace && ./mvnw -q -B -pl engine test`
Expected: PASS — model, JtsAdapter, OccupantDensity, FactsComputer tests all green.

- [ ] **Step 6: Commit**

```bash
git -C /workspace add engine/src/main/java/nz/compliance/engine/facts engine/src/test/java/nz/compliance/engine/facts/FactsComputerTest.java
git -C /workspace commit -m "feat(engine): FactsComputer producing PlanFacts (area, occupant load, exit width)"
```

---

## Definition of done (Plan 3)

- `./mvnw -pl engine test` green.
- `FactsComputer.compute(doc)` returns per-space area + occupant load, total occupant load, and exit-door count + total exit width — all in metric units, deterministically.

## Self-review notes

- **Spec coverage:** geometry parse/validate via JTS (spec §9 step 1) ✓; facts: area, occupant load, widths (§9 step 2) ✓. Egress graph/Dijkstra is Plan 4; thresholds/rounding are Plan 5 (kept out of facts deliberately).
- **Placeholders:** none in code. Occupant-density *values* are flagged illustrative (a domain-data fill-in, not a code placeholder) — the lookup contract is final.
- **Type consistency:** consumes `Space`/`Door`/`GeometryDoc` from Plan 2 unchanged; produces `SpaceFacts`/`PlanFacts` consumed by Plan 4 (egress) and Plan 5 (rule evaluation). `occupantLoad` is `double` (raw) everywhere.
```
