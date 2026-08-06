package dev.springbootstaticanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringBootStaticAnalysisApplicationTest {

    @Test
    void applicationTypeIsAvailable() {
        assertThat(SpringBootStaticAnalysisApplication.class).isNotNull();
    }
}
