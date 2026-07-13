package nz.compliance.app.imports;

import com.fasterxml.jackson.annotation.JsonProperty;
import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the Python roomtype service (see {@code ml/}). Enabled only when {@code roomtype.url} is set.
 *
 * <p>Geometry is converted to METRES here, because the model's area features are meaningless in
 * pixels: a pixel area means something different on every plan, depending on the image's resolution.
 * The caller guarantees a resolved, positive scale before we are invoked.
 */
@Component
@ConditionalOnProperty("roomtype.url")
public class HttpRoomTypeClassifier implements RoomTypeClassifier {

    private static final Logger log = LoggerFactory.getLogger(HttpRoomTypeClassifier.class);

    /** Anything the service is unsure about comes back as this, and we simply omit it. */
    private static final String ABSTAIN = "UNKNOWN";

    private final RestClient http;

    public HttpRoomTypeClassifier(RestClient.Builder builder,
                                  @org.springframework.beans.factory.annotation.Value("${roomtype.url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Map<String, String> classify(GeometryDoc geometryPx, double metresPerPixel) {
        if (metresPerPixel <= 0) {
            throw new IllegalArgumentException("metresPerPixel must be positive, got " + metresPerPixel);
        }
        List<RoomIn> rooms = toRequest(geometryPx, metresPerPixel);
        if (rooms.isEmpty()) {
            return Map.of();
        }
        try {
            ClassifyResponse response = http.post()
                    .uri("/classify")
                    .body(new ClassifyRequest(rooms))
                    .retrieve()
                    .body(ClassifyResponse.class);
            if (response == null || response.predictions() == null) {
                return Map.of();
            }
            Map<String, String> bySpaceId = new LinkedHashMap<>();
            for (Prediction p : response.predictions()) {
                if (p.occupancyType() != null && !ABSTAIN.equals(p.occupancyType())) {
                    bySpaceId.put(p.id(), p.occupancyType());
                }
            }
            return bySpaceId;
        } catch (RuntimeException e) {
            // Fail soft: an unreachable or broken classifier must not fail the import. The rooms
            // simply arrive without a suggested type and the reviewer fills them in.
            log.warn("roomtype service unavailable, importing without occupancy suggestions: {}",
                    e.toString());
            return Map.of();
        }
    }

    private static List<RoomIn> toRequest(GeometryDoc geometryPx, double metresPerPixel) {
        Map<String, Integer> doorCount = new HashMap<>();
        Map<String, Integer> neighbourCount = new HashMap<>();
        Map<String, Boolean> hasExit = new HashMap<>();
        for (Door d : geometryPx.doors()) {
            doorCount.merge(d.fromSpaceId(), 1, Integer::sum);
            if (d.exit()) {
                hasExit.put(d.fromSpaceId(), true);
            } else if (d.toSpaceId() != null) {
                doorCount.merge(d.toSpaceId(), 1, Integer::sum);
                neighbourCount.merge(d.fromSpaceId(), 1, Integer::sum);
                neighbourCount.merge(d.toSpaceId(), 1, Integer::sum);
            }
        }

        List<RoomIn> rooms = new ArrayList<>();
        for (Space s : geometryPx.spaces()) {
            List<PointDto> polygon = new ArrayList<>();
            for (Point p : s.polygon()) {
                polygon.add(new PointDto(p.x() * metresPerPixel, p.y() * metresPerPixel));
            }
            rooms.add(new RoomIn(
                    s.id(),
                    s.name() == null ? "" : s.name(),
                    polygon,
                    doorCount.getOrDefault(s.id(), 0),
                    neighbourCount.getOrDefault(s.id(), 0),
                    hasExit.getOrDefault(s.id(), false)));
        }
        return rooms;
    }

    // Wire contract with ml/src/roomtype/schema.py. Field names are snake_case to match it.
    record PointDto(double x, double y) {
    }

    record RoomIn(String id, String label, List<PointDto> polygon,
                  @JsonProperty("door_count") int doorCount,
                  @JsonProperty("connected_room_count") int connectedRoomCount,
                  @JsonProperty("has_exit_door") boolean hasExitDoor) {
    }

    record ClassifyRequest(List<RoomIn> rooms) {
    }

    record Prediction(String id,
                      @JsonProperty("occupancy_type") String occupancyType,
                      double confidence,
                      String source) {
    }

    record ClassifyResponse(List<Prediction> predictions) {
    }
}
