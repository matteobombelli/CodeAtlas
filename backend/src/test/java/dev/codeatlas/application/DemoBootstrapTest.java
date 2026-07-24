package dev.codeatlas.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codeatlas.indexing.IndexMode;
import dev.codeatlas.indexing.IndexingService;
import dev.codeatlas.repository.BuildSystem;
import dev.codeatlas.repository.RegisteredRepository;
import dev.codeatlas.repository.RepositoryService;
import dev.codeatlas.repository.RepositoryStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoBootstrapTest {

    private final RepositoryService repositories = org.mockito.Mockito.mock(RepositoryService.class);
    private final IndexingService indexing = org.mockito.Mockito.mock(IndexingService.class);
    private final DemoBootstrapProperties properties =
            new DemoBootstrapProperties(true, "Code Atlas", "code-atlas");

    @Test
    void registersAndIndexesTheConfiguredDemo() {
        RegisteredRepository repository = repository(null);
        when(repositories.list()).thenReturn(List.of());
        when(repositories.register("Code Atlas", "code-atlas")).thenReturn(repository);

        new DemoBootstrap(properties, repositories, indexing)
                .run(new DefaultApplicationArguments());

        verify(repositories).register("Code Atlas", "code-atlas");
        verify(indexing).start(repository.id(), IndexMode.FULL);
    }

    @Test
    void leavesAnIndexedDemoUntouched() {
        RegisteredRepository repository = repository(UUID.randomUUID());
        when(repositories.list()).thenReturn(List.of(repository));

        new DemoBootstrap(properties, repositories, indexing)
                .run(new DefaultApplicationArguments());

        verify(repositories, never()).register("Code Atlas", "code-atlas");
        verify(indexing, never()).start(repository.id(), IndexMode.FULL);
    }

    private RegisteredRepository repository(UUID activeRunId) {
        return new RegisteredRepository(
                UUID.randomUUID(),
                "Code Atlas",
                "code-atlas",
                "main",
                "abc123",
                false,
                BuildSystem.GRADLE,
                activeRunId == null ? RepositoryStatus.REGISTERED : RepositoryStatus.READY,
                activeRunId,
                Instant.EPOCH,
                activeRunId == null ? null : Instant.EPOCH,
                0);
    }
}
