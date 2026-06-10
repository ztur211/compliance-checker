package nz.compliance.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineInfoTest {

    @Test
    void describe_returnsNameAndVersion() {
        assertThat(EngineInfo.describe()).isEqualTo("compliance-engine 0.1.0");
    }
}
