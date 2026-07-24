package dev.codeatlas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodeAtlasApplicationTest {

    @Test
    void applicationTypeIsAvailable() {
        assertThat(CodeAtlasApplication.class).isNotNull();
    }
}
