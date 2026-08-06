package dev.springbootstaticanalysis.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import dev.springbootstaticanalysis.shared.InvalidRequestException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryPathGuardTest {

    @TempDir
    Path root;

    @Test
    void acceptsProjectDirectoryBelowApprovedRoot() throws Exception {
        Path repository = Files.createDirectories(root.resolve("demo"));
        RepositoryPathGuard guard = guard();

        assertThat(guard.resolve("demo")).isEqualTo(repository.toRealPath());
    }

    @Test
    void rejectsAbsoluteAndEscapingPaths() {
        RepositoryPathGuard guard = guard();

        assertThatThrownBy(() -> guard.resolve(root.toString()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> guard.resolve("../outside"))
                .isInstanceOf(InvalidRequestException.class);
    }

    private RepositoryPathGuard guard() {
        return new RepositoryPathGuard(new SpringBootStaticAnalysisProperties(
                root,
                1_048_576,
                10_000,
                new SpringBootStaticAnalysisProperties.Indexing(1, 10)));
    }
}
