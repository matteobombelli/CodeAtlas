package dev.springbootstaticanalysis.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springbootstaticanalysis.TestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryServiceTest {

    @TempDir
    Path root;

    @Test
    void registersAProjectDirectoryAndDetectsItsBuildSystem() throws Exception {
        Path project = Files.createDirectory(root.resolve("sample"));
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"");

        RepositoryStore store = mock(RepositoryStore.class);
        UUID id = UUID.randomUUID();
        when(store.create(
                any(), eq("Sample"), eq("sample"), eq(project.toRealPath()),
                eq(BuildSystem.GRADLE)))
                .thenReturn(new RegisteredRepository(
                        id, "Sample", "sample", BuildSystem.GRADLE,
                        RepositoryStatus.REGISTERED, null, Instant.now(), null, 0));

        RegisteredRepository registered =
                new RepositoryService(guard(), store).register("Sample", "sample");

        assertThat(registered.buildSystem()).isEqualTo(BuildSystem.GRADLE);
    }

    private RepositoryPathGuard guard() {
        return new RepositoryPathGuard(TestProperties.properties(root));
    }
}
