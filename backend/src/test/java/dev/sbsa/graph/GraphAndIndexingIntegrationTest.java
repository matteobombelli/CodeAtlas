package dev.sbsa.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sbsa.analysis.EndpointStore;
import dev.sbsa.analysis.HttpEndpointView;
import dev.sbsa.indexing.IndexMode;
import dev.sbsa.indexing.IndexRun;
import dev.sbsa.indexing.IndexStatus;
import dev.sbsa.indexing.IndexStore;
import dev.sbsa.indexing.IndexingService;
import dev.sbsa.repository.RegisteredRepository;
import dev.sbsa.repository.RepositoryService;
import dev.sbsa.shared.SbsaProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Indexes a small Spring-shaped repository end to end, then exercises the graph
 * bounds and the single-active-run guarantee against real data.
 */
@Testcontainers
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphAndIndexingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    // Created eagerly so @DynamicPropertySource can see it regardless of the
    // order JUnit and Spring resolve their own fixtures in.
    private static final Path ROOT = createTempRoot();

    @Autowired
    private RepositoryService repositories;

    @Autowired
    private IndexingService indexing;

    @Autowired
    private IndexStore indexStore;

    @Autowired
    private EndpointStore endpoints;

    @Autowired
    private GraphStore graphs;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private RegisteredRepository repository;

    @DynamicPropertySource
    static void repositoriesRoot(DynamicPropertyRegistry registry) {
        registry.add("sbsa.repositories-root", ROOT::toString);
    }

    @BeforeAll
    void indexSampleRepository() throws Exception {
        writeSampleRepository();
        repository = repositories.register("Sample", "sample");
        IndexRun run = indexing.start(repository.id(), IndexMode.FULL);
        awaitCompletion(run.id());
        repository = repositories.get(repository.id());
    }

    @Test
    void endpointGraphNeverRepeatsAnEdgeId() {
        HttpEndpointView endpoint = firstEndpoint();

        ExecutionGraph graph = graphs.endpointGraph(
                repository.id(), endpoint.id(), 4, true, true);

        assertThat(graph.nodes()).isNotEmpty();
        assertThat(graph.edges()).isNotEmpty();
        assertThat(graph.edges()).extracting(GraphEdge::id).doesNotHaveDuplicates();
    }

    @Test
    void blastRadiusNeverRepeatsAnEdgeId() {
        HttpEndpointView endpoint = firstEndpoint();

        ExecutionGraph graph = graphs.blastRadius(
                repository.id(), endpoint.controllerMethodId(), 4, true);

        assertThat(graph.edges()).extracting(GraphEdge::id).doesNotHaveDuplicates();
    }

    @Test
    void endpointGraphHonoursTheConfiguredEdgeLimit() {
        HttpEndpointView endpoint = firstEndpoint();
        // One edge is below what the sample produces, so the traversal has to stop
        // and say so rather than returning a partial graph as if it were complete.
        GraphStore bounded = new GraphStore(jdbc, properties(1, 1));

        ExecutionGraph graph = bounded.endpointGraph(
                repository.id(), endpoint.id(), 4, true, true);

        assertThat(graph.edges()).hasSizeLessThanOrEqualTo(1);
        assertThat(graph.truncated()).isTrue();
        assertThat(graph.warnings()).extracting("type").contains("TRUNCATED");
    }

    @Test
    void interleavedIndexRunInsertsCannotBothWin() throws Exception {
        assertThat(activeRunCount()).isZero();

        try (Connection first = dataSource.getConnection();
                Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);

            // Both transactions run IndexStore.create's guard query before either
            // inserts, which is exactly the interleaving READ COMMITTED permits and
            // the guard alone cannot rule out.
            assertThat(activeRuns(first)).isZero();
            assertThat(activeRuns(second)).isZero();

            insertQueuedRun(first);
            first.commit();

            assertThatThrownBy(() -> insertQueuedRun(second))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("index_runs_single_active_idx");
            second.rollback();
        }

        assertThat(activeRunCount()).isEqualTo(1);
    }

    private int activeRuns(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT count(*) FROM index_runs
                WHERE repository_id = ? AND status IN ('QUEUED', 'RUNNING')
                """)) {
            statement.setObject(1, repository.id());
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private void insertQueuedRun(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO index_runs (id, repository_id, mode, status, phase, started_at)
                VALUES (?, ?, 'INCREMENTAL', 'QUEUED', 'QUEUED', now())
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, repository.id());
            statement.executeUpdate();
        }
    }

    private HttpEndpointView firstEndpoint() {
        List<HttpEndpointView> found = endpoints.list(repository.id(), "");
        assertThat(found).isNotEmpty();
        return found.stream()
                .sorted(Comparator.comparing(HttpEndpointView::path))
                .findFirst()
                .orElseThrow();
    }

    private int activeRunCount() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM index_runs
                WHERE repository_id = :repositoryId AND status IN ('QUEUED', 'RUNNING')
                """, java.util.Map.of("repositoryId", repository.id()), Integer.class);
        return count == null ? 0 : count;
    }

    private void awaitCompletion(UUID runId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            IndexRun current = indexStore.get(runId);
            if (current.status() == IndexStatus.COMPLETE) {
                return;
            }
            if (current.status() == IndexStatus.FAILED) {
                throw new IllegalStateException("Indexing failed: " + current.errorSummary());
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Indexing did not finish in time");
    }

    private SbsaProperties properties(int maxNodes, int maxEdges) {
        return new SbsaProperties(
                ROOT, 1_048_576, 10_000, false,
                new SbsaProperties.Indexing(1, 10),
                new SbsaProperties.Graph(maxNodes, maxEdges));
    }

    private static void writeSampleRepository() throws Exception {
        Path repository = Files.createDirectories(ROOT.resolve("sample"));
        Path source = repository.resolve("src/main/java/demo/ProjectFlow.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                @RestController
                class ProjectController {
                    private final ProjectService service;
                    ProjectController(ProjectService service) { this.service = service; }
                    @PostMapping("/projects")
                    ProjectEntity create() { return service.create(); }
                    @GetMapping("/projects")
                    ProjectEntity find() { return service.find(); }
                }

                @Entity class ProjectEntity {}

                interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {}

                @Service
                class ProjectService {
                    private final ProjectRepository repository;
                    ProjectService(ProjectRepository repository) { this.repository = repository; }
                    ProjectEntity create() { return repository.save(new ProjectEntity()); }
                    ProjectEntity find() { return repository.findById(1L).orElseThrow(); }
                }
                """);
        Files.writeString(repository.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"");
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("initial")
                    .setAuthor("Test Author", "test@example.test")
                    .setCommitter("Test Author", "test@example.test")
                    .call();
        }
    }

    private static Path createTempRoot() {
        try {
            Path root = Files.createTempDirectory("sbsa-graph-it");
            root.toFile().deleteOnExit();
            return root.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
