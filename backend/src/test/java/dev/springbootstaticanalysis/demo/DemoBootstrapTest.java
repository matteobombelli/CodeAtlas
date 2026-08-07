package dev.springbootstaticanalysis.demo;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.springbootstaticanalysis.indexing.IndexMode;
import dev.springbootstaticanalysis.indexing.IndexingService;
import dev.springbootstaticanalysis.repository.BuildSystem;
import dev.springbootstaticanalysis.repository.RegisteredRepository;
import dev.springbootstaticanalysis.repository.RepositoryService;
import dev.springbootstaticanalysis.repository.RepositoryStatus;
import dev.springbootstaticanalysis.TestProperties;
import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoBootstrapTest {

    private final RepositoryService repositories = org.mockito.Mockito.mock(RepositoryService.class);
    private final IndexingService indexing = org.mockito.Mockito.mock(IndexingService.class);
    private final SpringBootStaticAnalysisProperties properties =
            TestProperties.properties(
                    Path.of("/workspace/repositories"),
                    false,
                    TestProperties.GRAPH,
                    new SpringBootStaticAnalysisProperties.Demo(
                            true,
                            "Spring Boot Static Analysis",
                            "spring-boot-static-analysis",
                            true));

    @Test
    void registersAndIndexesTheConfiguredDemo() {
        RegisteredRepository repository = repository(null);
        when(repositories.list()).thenReturn(List.of());
        when(repositories.register("Spring Boot Static Analysis", "spring-boot-static-analysis")).thenReturn(repository);

        new DemoBootstrap(properties, repositories, indexing)
                .run(new DefaultApplicationArguments());

        verify(repositories).register("Spring Boot Static Analysis", "spring-boot-static-analysis");
        verify(indexing).start(repository.id(), IndexMode.FULL);
    }

    @Test
    void refreshesAnIndexedDemoIncrementally() {
        RegisteredRepository repository = repository(UUID.randomUUID());
        when(repositories.list()).thenReturn(List.of(repository));

        new DemoBootstrap(properties, repositories, indexing)
                .run(new DefaultApplicationArguments());

        verify(repositories, never()).register("Spring Boot Static Analysis", "spring-boot-static-analysis");
        verify(indexing).start(repository.id(), IndexMode.INCREMENTAL);
    }

    private RegisteredRepository repository(UUID activeRunId) {
        return new RegisteredRepository(
                UUID.randomUUID(),
                "Spring Boot Static Analysis",
                "spring-boot-static-analysis",
                BuildSystem.GRADLE,
                activeRunId == null ? RepositoryStatus.REGISTERED : RepositoryStatus.READY,
                activeRunId,
                Instant.EPOCH,
                activeRunId == null ? null : Instant.EPOCH,
                0);
    }
}
