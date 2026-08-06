package dev.sbsa.application;

import dev.sbsa.indexing.IndexMode;
import dev.sbsa.indexing.IndexingService;
import dev.sbsa.repository.RegisteredRepository;
import dev.sbsa.repository.RepositoryService;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registers the read-only repository mounted by the local Compose environment.
 */
@Component
@ConditionalOnProperty(prefix = "sbsa.demo", name = "enabled", havingValue = "true")
public class DemoBootstrap implements ApplicationRunner {

    private final DemoBootstrapProperties properties;
    private final RepositoryService repositories;
    private final IndexingService indexing;

    public DemoBootstrap(
            DemoBootstrapProperties properties,
            RepositoryService repositories,
            IndexingService indexing) {
        this.properties = properties;
        this.repositories = repositories;
        this.indexing = indexing;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Optional<RegisteredRepository> existing = repositories.list().stream()
                .filter(repository -> repository.relativePath().equals(properties.relativePath()))
                .findFirst();
        RegisteredRepository repository = existing.orElseGet(() -> repositories.register(
                properties.displayName(), properties.relativePath()));
        if (repository.activeIndexRunId() == null) {
            indexing.start(repository.id(), IndexMode.FULL);
        } else if (properties.reindexOnStartup()) {
            // The mounted source can change between restarts, so refresh rather than
            // serving the graph the previous container left in the database.
            indexing.start(repository.id(), IndexMode.INCREMENTAL);
        }
    }
}
