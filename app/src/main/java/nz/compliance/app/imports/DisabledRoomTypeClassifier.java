package nz.compliance.app.imports;

import nz.compliance.engine.model.GeometryDoc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * The default: no classifier configured, so no opinion. Whatever occupancy type the vision
 * extractor guessed stands, and the reviewer edits it.
 *
 * <p>This exists so that the ML service is genuinely optional. The product must build, test, and
 * run with no Python process anywhere in sight - the same way it runs without an ANTHROPIC_API_KEY.
 */
@Configuration
public class DisabledRoomTypeClassifier {

    @Bean
    @ConditionalOnMissingBean(RoomTypeClassifier.class)
    RoomTypeClassifier noOpRoomTypeClassifier() {
        return (GeometryDoc geometryPx, double metresPerPixel) -> Map.of();
    }
}
