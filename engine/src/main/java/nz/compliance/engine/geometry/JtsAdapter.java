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

    /** Polygon centroid (metres). */
    public static Point centroid(Space space) {
        Coordinate c = toPolygon(space).getCentroid().getCoordinate();
        return new Point(c.x, c.y);
    }
}
