package nz.compliance.app.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the hand-authored gold files in import-gold/ so a malformed gold fails CI, not the live eval. */
class GoldPlanParseTest {

    @Test
    void everyGoldFileParsesIntoAUsableGoldPlan() throws Exception {
        Path dir = Path.of("src/test/resources/import-gold");
        ObjectMapper mapper = new ObjectMapper();
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> golds = files.filter(p -> p.toString().endsWith(".gold.json")).sorted().toList();
            assertThat(golds).as("at least one *.gold.json fixture").isNotEmpty();

            for (Path g : golds) {
                GoldPlan plan = mapper.readValue(Files.readAllBytes(g), GoldPlan.class);
                String name = g.getFileName().toString();

                boolean scoresScale = plan.scoreScale() == null || plan.scoreScale();
                boolean useful = !plan.rooms().isEmpty() || !plan.doors().isEmpty() || scoresScale;
                assertThat(useful).as(name + " scores nothing").isTrue();

                for (GoldPlan.GoldRoom room : plan.rooms()) {
                    assertThat(room.polygonPx().size())
                            .as(name + " room \"" + room.label() + "\" needs >=3 points")
                            .isGreaterThanOrEqualTo(3);
                }
                for (GoldPlan.GoldDoor door : plan.doors()) {
                    assertThat(door.positionPx().size()).as(name + " door needs exactly 2 points").isEqualTo(2);
                }
            }
        }
    }
}
