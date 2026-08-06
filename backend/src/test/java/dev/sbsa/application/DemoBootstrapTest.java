package dev.sbsa.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static final String NAME = "Spring Boot Static Analysis";
    private static final String PATH = "spring-boot-static-analysis";

    private final RepositoryService repositories = org.mockito.Mockito.mock(RepositoryService.class);
    private final IndexingService indexing = org.mockito.Mockito.mock(IndexingService.class);

    @Test
    void registersAndFullyIndexesAnUnknownDemo() {
        RegisteredRepository repository = repository(null);
        when(repositories.list()).thenReturn(List.of());
        when(repositories.register(NAME, PATH)).thenReturn(repository);

        new DemoBootstrap(properties(true), repositories, indexing)
                .run(new DefaultApplicationArguments());

        verify(repositories).register(NAME, PATH);
        verify(indexing).start(repository.id(), IndexMode.FULL);
    }

    @Test
    void rescansAnAlreadyIndexedDemoOnStartup() {
        RegisteredRepository repository = repository(UUID.randomUUID());
        when(repositories.list()).thenReturn(List.of(repository));

        new DemoBootstrap(properties(true), repositories, indexing)
                .run(new DefaultApplicationArguments());

        // The mounted source can change between restarts, so the previous
        // container's graph must not be served as current.
        verify(repositories, never()).register(NAME, PATH);
        verify(indexing).start(repository.id(), IndexMode.INCREMENTAL);
    }

    @Test
    void leavesAnIndexedDemoUntouchedWhenStartupReindexIsDisabled() {
        RegisteredRepository repository = repository(UUID.randomUUID());
        when(repositories.list()).thenReturn(List.of(repository));

        new DemoBootstrap(properties(false), repositories, indexing)
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(indexing);
    }

    private DemoBootstrapProperties properties(boolean reindexOnStartup) {
        return new DemoBootstrapProperties(true, NAME, PATH, reindexOnStartup);
    }

    private RegisteredRepository repository(UUID activeRunId) {
        return new RegisteredRepository(
                UUID.randomUUID(),
                NAME,
                PATH,
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
