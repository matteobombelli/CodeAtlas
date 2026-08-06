package dev.sbsa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SbsaApplicationTest {

    @Test
    void applicationTypeIsAvailable() {
        assertThat(SbsaApplication.class).isNotNull();
    }
}
