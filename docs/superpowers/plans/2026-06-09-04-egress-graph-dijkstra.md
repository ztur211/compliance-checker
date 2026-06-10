# Egress Graph + Dijkstra Implementation Plan (Plan 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the pure `engine`, build the egress graph (spaces + an `EXTERIOR` node; edges = doors weighted by travel distance) and run **Dijkstra (JGraphT)** to compute each space's **open-path travel distance to the nearest exit**, the escape path (for highlighting), and which spaces **cannot reach an exit**.

**Architecture:** v1 uses the centroid-based door-graph approximation from spec §9: a space is represented by its polygon centroid; an inter-space door contributes an edge weighted `dist(centroidA, doorMid) + dist(doorMid, centroidB)`; an **exit** door contributes an edge from its space to `EXTERIOR` weighted `dist(centroid, doorMid)`. `EgressAnalyzer.analyze(GeometryDoc)` returns an immutable `EgressResult`. A true most-remote-point / navmesh metric is roadmap. **Units: metres.**

**Tech Stack:** JGraphT `jgrapht-core`, JTS (centroids), JUnit 5 + AssertJ.

**Prerequisite:** Plan 3 complete (`JtsAdapter`, `FactsComputer`).

---

## File map

```
engine/pom.xml                                              # + jgrapht-core
engine/src/main/java/nz/compliance/engine/geometry/JtsAdapter.java   # + centroid()
engine/src/main/java/nz/compliance/engine/egress/SpaceEgress.java
engine/src/main/java/nz/compliance/engine/egress/EgressResult.java
engine/src/main/java/nz/compliance/engine/egress/EgressAnalyzer.java
engine/src/test/java/nz/compliance/engine/geometry/JtsAdapterCentroidTest.java
engine/src/test/java/nz/compliance/engine/egress/EgressAnalyzerTest.java
```

---

## Task 1: JGraphT dependency + centroid helper (TDD)

**Files:**
- Modify: `engine/pom.xml`
- Test: `engine/src/test/java/nz/compliance/engine/geometry/JtsAdapterCentroidTest.java`
- Modify: `engine/src/main/java/nz/compliance/engine/geometry/JtsAdapter.java`

- [ ] **Step 1: Add JGraphT to `engine/pom.xml`** (inside `<dependencies>`, after `jts-core`)

```xml
    <dependency>
      <groupId>org.jgrapht</groupId>
      <artifactId>jgrapht-core</artifactId>
      <version>1.5.2</version>
    </dependency>
```

- [ ] **Step 2: Write the failing test**

`engine/src/test/java/nz/compliance/engine/geometry/JtsAdapterCentroidTest.java`:
```java
package nz.compliance.engine.geometry;

import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JtsAdapterCentroidTest {

    @Test
    void centroid_ofSquareIsCentre() {
        Space s = new Space("s1", "s1", "WB", List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10)));
        Point c = JtsAdapter.centroid(s);
        assertThat(c.x()).isCloseTo(5.0, within(1e-9));
        assertThat(c.y()).isCloseTo(5.0, within(1e-9));
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=JtsAdapterCentroidTest`
Expected: FAIL — `centroid` not found.

- [ ] **Step 4: Add the `centroid` method to `JtsAdapter`** (add these imports + method to the existing class)

Add import:
```java
import org.locationtech.jts.geom.Coordinate;
```
(already present) and add the method inside the class:
```java
    /** Polygon centroid (metres). */
    public static Point centroid(Space space) {
        Coordinate c = toPolygon(space).getCentroid().getCoordinate();
        return new Point(c.x, c.y);
    }
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=JtsAdapterCentroidTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git -C /workspace add engine/pom.xml engine/src/main/java/nz/compliance/engine/geometry/JtsAdapter.java engine/src/test/java/nz/compliance/engine/geometry/JtsAdapterCentroidTest.java
git -C /workspace commit -m "feat(engine): add JGraphT and polygon centroid helper"
```

---

## Task 2: EgressAnalyzer — Dijkstra open-path distance (TDD)

**Files:**
- Test: `engine/src/test/java/nz/compliance/engine/egress/EgressAnalyzerTest.java`
- Create: `engine/src/main/java/nz/compliance/engine/egress/SpaceEgress.java`
- Create: `engine/src/main/java/nz/compliance/engine/egress/EgressResult.java`
- Create: `engine/src/main/java/nz/compliance/engine/egress/EgressAnalyzer.java`

- [ ] **Step 1: Write the failing test** (two rooms in a row; s1 has the exit; s3 is isolated)

`engine/src/test/java/nz/compliance/engine/egress/EgressAnalyzerTest.java`:
```java
package nz.compliance.engine.egress;

import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EgressAnalyzerTest {

    private static Space rect(String id, double x0, double y0, double x1, double y1) {
        return new Space(id, id, "WB", List.of(
                new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1)));
    }

    // s1: (0,0)-(10,10) centroid (5,5); s2: (10,0)-(20,10) centroid (15,5)
    // door d12 on x=10 mid (10,5); exit e1 on x=0 mid (0,5)
    private static GeometryDoc twoRooms() {
        return new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s2", 10, 0, 20, 10)),
                List.of(
                        new Door("d12", "s1", "s2", List.of(new Point(10, 4), new Point(10, 6)), 1000, false),
                        new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
    }

    @Test
    void openPathLength_s1_is5() {
        EgressResult r = EgressAnalyzer.analyze(twoRooms());
        SpaceEgress s1 = r.bySpace().get("s1");
        assertThat(s1.reachesExit()).isTrue();
        assertThat(s1.openPathLengthMetres()).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isCloseTo(5.0, within(1e-9));
    }

    @Test
    void openPathLength_s2_routesThroughS1_is15() {
        EgressResult r = EgressAnalyzer.analyze(twoRooms());
        SpaceEgress s2 = r.bySpace().get("s2");
        // dist(s2c->d12mid)=5, dist(d12mid->s1c)=5, dist(s1c->e1mid)=5 => 15
        assertThat(s2.openPathLengthMetres()).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isCloseTo(15.0, within(1e-9));
        assertThat(s2.pathNodeIds()).containsExactly("s2", "s1", "EXTERIOR");
    }

    @Test
    void worstOpenPath_isS2() {
        EgressResult r = EgressAnalyzer.analyze(twoRooms());
        assertThat(r.worstOpenPath()).get()
                .extracting(SpaceEgress::spaceId).isEqualTo("s2");
    }

    @Test
    void isolatedSpace_isUnreachable() {
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s3", 100, 100, 110, 110)),
                List.of(new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        EgressResult r = EgressAnalyzer.analyze(doc);
        assertThat(r.bySpace().get("s3").reachesExit()).isFalse();
        assertThat(r.unreachable()).extracting(SpaceEgress::spaceId).containsExactly("s3");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=EgressAnalyzerTest`
Expected: FAIL — egress classes not found.

- [ ] **Step 3: Implement the result records**

`engine/src/main/java/nz/compliance/engine/egress/SpaceEgress.java`:
```java
package nz.compliance.engine.egress;

import java.util.List;
import java.util.Optional;

/** Egress outcome for a single space. */
public record SpaceEgress(String spaceId, Optional<Double> openPathLengthMetres,
                          boolean reachesExit, List<String> pathNodeIds) {
    public SpaceEgress {
        pathNodeIds = List.copyOf(pathNodeIds);
    }
}
```

`engine/src/main/java/nz/compliance/engine/egress/EgressResult.java`:
```java
package nz.compliance.engine.egress;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Egress analysis for a whole plan, keyed by space id. */
public record EgressResult(Map<String, SpaceEgress> bySpace) {

    public EgressResult {
        bySpace = Map.copyOf(bySpace);
    }

    /** The space with the longest open path that still reaches an exit. */
    public Optional<SpaceEgress> worstOpenPath() {
        return bySpace.values().stream()
                .filter(e -> e.openPathLengthMetres().isPresent())
                .max(Comparator.comparingDouble(e -> e.openPathLengthMetres().orElseThrow()));
    }

    /** Spaces with no path to any exit. */
    public List<SpaceEgress> unreachable() {
        return bySpace.values().stream().filter(e -> !e.reachesExit()).toList();
    }
}
```

- [ ] **Step 4: Implement the analyzer**

`engine/src/main/java/nz/compliance/engine/egress/EgressAnalyzer.java`:
```java
package nz.compliance.engine.egress;

import nz.compliance.engine.geometry.JtsAdapter;
import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the egress graph and computes open-path travel distance to the nearest
 * exit for every space (v1 centroid-based door-graph approximation; see spec §9).
 */
public final class EgressAnalyzer {

    public static final String EXTERIOR = "EXTERIOR";

    private EgressAnalyzer() {
    }

    public static EgressResult analyze(GeometryDoc doc) {
        SimpleWeightedGraph<String, DefaultWeightedEdge> graph =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        graph.addVertex(EXTERIOR);

        Map<String, Point> centroids = new HashMap<>();
        for (Space s : doc.spaces()) {
            graph.addVertex(s.id());
            centroids.put(s.id(), JtsAdapter.centroid(s));
        }

        for (Door d : doc.doors()) {
            if (d.position().size() != 2) {
                continue;
            }
            Point from = centroids.get(d.fromSpaceId());
            if (from == null) {
                continue;
            }
            Point mid = midpoint(d);
            if (d.exit() && d.toSpaceId() == null) {
                addOrMinEdge(graph, d.fromSpaceId(), EXTERIOR, dist(from, mid));
            } else if (d.toSpaceId() != null) {
                Point to = centroids.get(d.toSpaceId());
                if (to == null) {
                    continue;
                }
                addOrMinEdge(graph, d.fromSpaceId(), d.toSpaceId(), dist(from, mid) + dist(mid, to));
            }
            // non-exit doors to the exterior are not escape routes; ignored
        }

        DijkstraShortestPath<String, DefaultWeightedEdge> dijkstra = new DijkstraShortestPath<>(graph);
        Map<String, SpaceEgress> bySpace = new LinkedHashMap<>();
        for (Space s : doc.spaces()) {
            GraphPath<String, DefaultWeightedEdge> path = dijkstra.getPath(s.id(), EXTERIOR);
            if (path == null) {
                bySpace.put(s.id(), new SpaceEgress(s.id(), Optional.empty(), false, List.of()));
            } else {
                bySpace.put(s.id(), new SpaceEgress(s.id(), Optional.of(path.getWeight()),
                        true, path.getVertexList()));
            }
        }
        return new EgressResult(bySpace);
    }

    private static void addOrMinEdge(SimpleWeightedGraph<String, DefaultWeightedEdge> g,
                                     String a, String b, double weight) {
        DefaultWeightedEdge e = g.getEdge(a, b);
        if (e == null) {
            e = g.addEdge(a, b);
            g.setEdgeWeight(e, weight);
        } else if (weight < g.getEdgeWeight(e)) {
            g.setEdgeWeight(e, weight);
        }
    }

    private static Point midpoint(Door d) {
        Point a = d.position().get(0);
        Point b = d.position().get(1);
        return new Point((a.x() + b.x()) / 2.0, (a.y() + b.y()) / 2.0);
    }

    private static double dist(Point a, Point b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd /workspace && ./mvnw -q -B -pl engine test -Dtest=EgressAnalyzerTest`
Expected: PASS — 4 green.

- [ ] **Step 6: Full engine test run**

Run: `cd /workspace && ./mvnw -q -B -pl engine test`
Expected: PASS — all engine tests green.

- [ ] **Step 7: Commit**

```bash
git -C /workspace add engine/src/main/java/nz/compliance/engine/egress engine/src/test/java/nz/compliance/engine/egress
git -C /workspace commit -m "feat(engine): egress graph + Dijkstra open-path distance analyzer"
```

---

## Definition of done (Plan 4)

- `./mvnw -pl engine test` green.
- `EgressAnalyzer.analyze(doc)` returns, per space: open-path distance to the nearest exit, the path node list (for highlighting), and a reachable/unreachable flag; plus `worstOpenPath()` and `unreachable()` helpers.

## Self-review notes

- **Spec coverage:** egress graph build + Dijkstra shortest path (spec §9 steps 3–4) ✓; "exits exist but a space can't reach one" surfaced as `reachesExit=false` for Plan 5 to turn into a violation (§11) ✓. Dead-end/common-path nuances and the navmesh metric remain roadmap (disclosed in spec §9).
- **Placeholders:** none. The centroid-based metric is the explicit v1 approximation, not a stub.
- **Type consistency:** consumes `GeometryDoc`/`Space`/`Door`/`Point` unchanged; `SpaceEgress.openPathLengthMetres` is `Optional<Double>`; the `EXTERIOR` sentinel string is shared. Plan 5 reads `EgressResult` via the parameter registry (`OPEN_PATH_LENGTH`, `EXIT_COUNT`).
```
