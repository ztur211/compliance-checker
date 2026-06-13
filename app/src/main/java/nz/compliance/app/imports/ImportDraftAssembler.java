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
