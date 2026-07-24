package dev.codeatlas.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.codeatlas.shared.CodeAtlasProperties;
import dev.codeatlas.shared.InvalidRequestException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryPathGuardTest {

    @TempDir
    Path root;

    @Test
    void acceptsGitRepositoryBelowApprovedRoot() throws Exception {
        Path repository = Files.createDirectories(root.resolve("demo"));
        Files.createDirectory(repository.resolve(".git"));
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
        return new RepositoryPathGuard(new CodeAtlasProperties(
                root,
                1_048_576,
                10_000,
                new CodeAtlasProperties.Indexing(1, 10)));
    }
}
