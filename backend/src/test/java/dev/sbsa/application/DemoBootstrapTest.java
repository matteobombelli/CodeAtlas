package dev.sbsa.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sbsa.indexing.IndexMode;
import dev.sbsa.indexing.IndexingService;
import dev.sbsa.repository.BuildSystem;
import dev.sbsa.repository.RegisteredRepository;
import dev.sbsa.repository.RepositoryService;
import dev.sbsa.repository.RepositoryStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoBootstrapTest {

    private final RepositoryService repositories = org.mockito.Mockito.mock(RepositoryService.class);
    private final IndexingService indexing = org.mockito.Mockito.mock(IndexingService.class);
    private final DemoBootstrapProperties properties =
            new DemoBootstrapProperties(true, "Spring Boot Static Analysis", "sbsa");

    @Test
    void registersAndIndexesTheConfiguredDemo() {
        RegisteredRepository repository = repository(null);
        when(repositories.list()).thenReturn(List.of());
        when(repositories.register("Spring Boot Static Analysis", "sbsa")).thenReturn(repository);

        new DemoBootstrap(properties, repositories, indexing)
                .run(new DefaultApplicationArguments());

        verify(repositories).register("Spring Boot Static Analysis", "sbsa");
        verify(indexing).start(repository.id(), IndexMode.FULL);
    }

    @Test
    void leavesAnIndexedDemoUntouched() {
        RegisteredRepository repository = repository(UUID.randomUUID());
        when(repositories.list()).thenReturn(List.of(repository));

        new DemoBootstrap(properties, repositories, indexing)
                .run(new DefaultApplicationArguments());

        verify(repositories, never()).register("Spring Boot Static Analysis", "sbsa");
        verify(indexing, never()).start(repository.id(), IndexMode.FULL);
    }

    private RegisteredRepository repository(UUID activeRunId) {
        return new RegisteredRepository(
                UUID.randomUUID(),
                "Spring Boot Static Analysis",
                "sbsa",
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
