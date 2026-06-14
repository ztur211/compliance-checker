package nz.compliance.app.imports;

import nz.compliance.engine.model.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores a predicted {@link PlanExtraction} against hand-authored {@link GoldPlan} ground truth:
 * rooms by greedy IoU matching (with label accuracy on matched pairs), doors by midpoint proximity,
 * and scale by relative error. Pure and deterministic — the live eval harness runs the model, then
 * this turns its output into an accuracy scorecard.
 */
public class ExtractionScorer {

    static final double ROOM_IOU_THRESHOLD = 0.5;
    static final double DOOR_TOLERANCE_PX = 40.0;
    static final double SCALE_REL_TOLERANCE = 0.15;

    private static final GeometryFactory GF = new GeometryFactory();

    public ScoreReport score(PlanExtraction pred, GoldPlan gold) {
        RoomScore rooms = scoreRooms(pred.rooms(), gold.rooms());
        DoorScore doors = scoreDoors(pred.doors(), gold.doors());
        ScaleScore scale = scoreScale(pred.scaleGuess(), gold);
        int predExits = (int) pred.doors().stream().filter(ExtractedDoor::exitGuess).count();
        int goldExits = (int) gold.doors().stream().filter(GoldPlan.GoldDoor::exit).count();
        return new ScoreReport(
                gold.rooms().size(), pred.rooms().size(), rooms.matched(),
                rooms.recall(), rooms.precision(), rooms.meanIoU(), rooms.labelAccuracy(),
                gold.doors().size(), pred.doors().size(), doors.matched(), doors.recall(), doors.precision(),
                goldExits, predExits,
                scale.scored(), scale.ok(), scale.relError());
    }

    // ---- rooms: greedy one-to-one IoU matching ----

    private record RoomScore(int matched, double recall, double precision, double meanIoU, double labelAccuracy) {}

    private record Pair(int goldIdx, int predIdx, double iou) {}

    private RoomScore scoreRooms(List<ExtractedRoom> pred, List<GoldPlan.GoldRoom> gold) {
        List<Polygon> predPolys = pred.stream().map(r -> polygon(r.polygonPx())).toList();
        List<Polygon> goldPolys = gold.stream().map(r -> polygon(r.polygonPx())).toList();

        List<Pair> candidates = new ArrayList<>();
        for (int gi = 0; gi < goldPolys.size(); gi++) {
            for (int pi = 0; pi < predPolys.size(); pi++) {
                double iou = iou(goldPolys.get(gi), predPolys.get(pi));
                if (iou >= ROOM_IOU_THRESHOLD) {
                    candidates.add(new Pair(gi, pi, iou));
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(b.iou(), a.iou()));   // best overlaps first

        boolean[] goldUsed = new boolean[gold.size()];
        boolean[] predUsed = new boolean[pred.size()];
        int matched = 0, correctLabel = 0;
        double iouSum = 0;
        for (Pair p : candidates) {
            if (goldUsed[p.goldIdx()] || predUsed[p.predIdx()]) {
                continue;
            }
            goldUsed[p.goldIdx()] = true;
            predUsed[p.predIdx()] = true;
            matched++;
            iouSum += p.iou();
            if (labelsMatch(gold.get(p.goldIdx()).label(), pred.get(p.predIdx()).label())) {
                correctLabel++;
            }
        }
        double recall = gold.isEmpty() ? 0.0 : (double) matched / gold.size();
        double precision = pred.isEmpty() ? 0.0 : (double) matched / pred.size();
        double meanIoU = matched == 0 ? 0.0 : iouSum / matched;
        double labelAccuracy = matched == 0 ? 0.0 : (double) correctLabel / matched;
        return new RoomScore(matched, recall, precision, meanIoU, labelAccuracy);
    }

    private static boolean labelsMatch(String a, String b) {
        return norm(a).equals(norm(b)) && !norm(a).isEmpty();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // ---- doors: greedy nearest-midpoint within tolerance ----

    private record DoorScore(int matched, double recall, double precision) {}

    private DoorScore scoreDoors(List<ExtractedDoor> pred, List<GoldPlan.GoldDoor> gold) {
        boolean[] predUsed = new boolean[pred.size()];
        int matched = 0;
        for (GoldPlan.GoldDoor gd : gold) {
            Point gm = midpoint(gd.positionPx());
            if (gm == null) {
                continue;
            }
            int best = -1;
            double bestDist = DOOR_TOLERANCE_PX;
            for (int pi = 0; pi < pred.size(); pi++) {
                if (predUsed[pi]) {
                    continue;
                }
                Point pm = midpoint(pred.get(pi).positionPx());
                if (pm == null) {
                    continue;
                }
                double dist = Math.hypot(pm.x() - gm.x(), pm.y() - gm.y());
                if (dist <= bestDist) {
                    bestDist = dist;
                    best = pi;
                }
            }
            if (best >= 0) {
                predUsed[best] = true;
                matched++;
            }
        }
        double recall = gold.isEmpty() ? 0.0 : (double) matched / gold.size();
        double precision = pred.isEmpty() ? 0.0 : (double) matched / pred.size();
        return new DoorScore(matched, recall, precision);
    }

    // ---- scale ----

    private record ScaleScore(boolean scored, boolean ok, Double relError) {}

    private static ScaleScore scoreScale(ScaleGuess pred, GoldPlan gold) {
        boolean scoreIt = gold.scoreScale() == null || gold.scoreScale();
        if (!scoreIt) {
            return new ScaleScore(false, true, null);
        }
        Double expected = gold.scaleMetresPerPixel();
        if (expected == null) {
            return new ScaleScore(true, pred == null, null);          // expect a null scale
        }
        if (pred == null) {
            return new ScaleScore(true, false, null);                 // expected a value, got null
        }
        double rel = Math.abs(pred.metresPerPixel() - expected) / expected;
        return new ScaleScore(true, rel <= SCALE_REL_TOLERANCE, rel);
    }

    // ---- geometry helpers ----

    private static double iou(Polygon a, Polygon b) {
        if (a == null || b == null) {
            return 0.0;
        }
        Geometry ga = a.isValid() ? a : a.buffer(0);   // repair self-intersecting rings
        Geometry gb = b.isValid() ? b : b.buffer(0);
        double union = ga.union(gb).getArea();
        return union <= 0 ? 0.0 : ga.intersection(gb).getArea() / union;
    }

    private static Polygon polygon(List<Point> pts) {
        if (pts == null || pts.size() < 3) {
            return null;
        }
        Coordinate[] ring = new Coordinate[pts.size() + 1];
        for (int i = 0; i < pts.size(); i++) {
            ring[i] = new Coordinate(pts.get(i).x(), pts.get(i).y());
        }
        ring[pts.size()] = new Coordinate(pts.get(0).x(), pts.get(0).y());   // close the ring
        return GF.createPolygon(ring);
    }

    private static Point midpoint(List<Point> seg) {
        if (seg == null || seg.size() != 2) {
            return null;
        }
        return new Point((seg.get(0).x() + seg.get(1).x()) / 2.0, (seg.get(0).y() + seg.get(1).y()) / 2.0);
    }

    /** Per-image accuracy scorecard. Rates are 0..1; {@code scaleRelError} is null when not applicable. */
    public record ScoreReport(
            int goldRooms, int predRooms, int matchedRooms,
            double roomRecall, double roomPrecision, double meanIoU, double labelAccuracy,
            int goldDoors, int predDoors, int matchedDoors, double doorRecall, double doorPrecision,
            int goldExits, int predExits,
            boolean scaleScored, boolean scaleOk, Double scaleRelError) {

        public String summary() {
            String scale;
            if (!scaleScored) {
                scale = "scale n/a";
            } else if (scaleRelError == null) {
                scale = scaleOk ? "scale OK(null)" : "scale WRONG(expected null)";
            } else {
                scale = String.format(Locale.ROOT, "scale %s(%.1f%%)", scaleOk ? "OK" : "WRONG", scaleRelError * 100);
            }
            return String.format(Locale.ROOT,
                    "rooms %d/%d (recall %.2f prec %.2f IoU %.2f label %.2f) | doors %d/%d (recall %.2f prec %.2f) "
                            + "| exits pred %d/gold %d | %s",
                    matchedRooms, goldRooms, roomRecall, roomPrecision, meanIoU, labelAccuracy,
                    matchedDoors, goldDoors, doorRecall, doorPrecision, predExits, goldExits, scale);
        }
    }
}
