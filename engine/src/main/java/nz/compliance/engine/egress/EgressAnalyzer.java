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
 *
 * <p>Topologically-invalid spaces (where {@link JtsAdapter#isValid} is false) are
 * excluded from the graph: a self-intersecting/degenerate polygon has no trustworthy
 * centroid, so it gets {@code reachesExit=false} / empty path rather than a fabricated
 * route, and cannot corrupt other spaces' routes. The rules layer (Plan 5) maps such
 * spaces to "not evaluated" via {@code SpaceFacts.valid}; a space that is valid but
 * still cannot reach an exit is the genuine "no means of escape" case.
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
            if (!JtsAdapter.isValid(s)) {
                continue; // degenerate polygon: no trustworthy centroid (see class doc)
            }
            graph.addVertex(s.id());
            centroids.put(s.id(), JtsAdapter.centroid(s));
        }

        for (Door d : doc.doors()) {
            if (d.position().size() != 2) {
                continue;
            }
            Point from = centroids.get(d.fromSpaceId());
            if (from == null) {
                continue; // door on an unknown or invalid (excluded) space
            }
            Point mid = midpoint(d);
            if (d.exit()) {
                // Any door marked a final exit discharges to the exterior, regardless
                // of toSpaceId. This matches FactsComputer's exit predicate (d.exit()
                // alone), so exit count and the egress graph agree about the same door.
                addOrMinEdge(graph, d.fromSpaceId(), EXTERIOR, dist(from, mid));
            } else if (d.toSpaceId() != null) {
                if (d.fromSpaceId().equals(d.toSpaceId())) {
                    continue; // a door cannot connect a space to itself
                }
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
            if (!graph.containsVertex(s.id())) {
                // excluded as topologically invalid — no trustworthy route
                bySpace.put(s.id(), new SpaceEgress(s.id(), Optional.empty(), false, List.of()));
                continue;
            }
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
