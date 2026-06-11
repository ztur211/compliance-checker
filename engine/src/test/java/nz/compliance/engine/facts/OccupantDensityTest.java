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

    @Test
    void nullType_fallsBackToDefault() {
        // a Space deserialized without an occupancyType must not crash the facts run
        assertThat(OccupantDensity.squareMetresPerPerson(null)).isEqualTo(10.0);
    }
}
