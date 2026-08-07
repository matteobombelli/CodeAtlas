package dev.springbootstaticanalysis.demo;

import dev.springbootstaticanalysis.indexing.IndexMode;
import dev.springbootstaticanalysis.indexing.IndexingService;
import dev.springbootstaticanalysis.repository.RegisteredRepository;
import dev.springbootstaticanalysis.repository.RepositoryService;
import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Registers and indexes the read-only project that Compose mounts. */
@Component
@ConditionalOnProperty(
        prefix = "spring-boot-static-analysis.demo",
        name = "enabled",
        havingValue = "true")
public class DemoBootstrap implements ApplicationRunner {

    private final SpringBootStaticAnalysisProperties.Demo demo;
    private final RepositoryService repositories;
    private final IndexingService indexing;

    public DemoBootstrap(
            SpringBootStaticAnalysisProperties properties,
            RepositoryService repositories,
            IndexingService indexing) {
        this.demo = properties.demo();
        this.repositories = repositories;
        this.indexing = indexing;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Optional<RegisteredRepository> existing = repositories.list().stream()
                .filter(repository -> repository.relativePath().equals(demo.relativePath()))
                .findFirst();
        RegisteredRepository repository = existing.orElseGet(
                () -> repositories.register(demo.displayName(), demo.relativePath()));
        if (repository.activeIndexRunId() == null) {
            indexing.start(repository.id(), IndexMode.FULL);
        } else if (demo.reindexOnStartup()) {
            // The mounted source can change between deployments. Refresh the saved
            // graph rather than serving the snapshot left by the previous container.
            indexing.start(repository.id(), IndexMode.INCREMENTAL);
        }
    }
}
