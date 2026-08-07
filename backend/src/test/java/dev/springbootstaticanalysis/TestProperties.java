package dev.springbootstaticanalysis;

import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties.Demo;
import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties.Graph;
import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties.Indexing;
import java.nio.file.Path;

/** Backend settings for tests, matching the defaults in {@code application.yml}. */
public final class TestProperties {

    public static final Graph GRAPH = new Graph(100, 250);
    public static final Demo NO_DEMO = new Demo(false, "", "", false);

    public static SpringBootStaticAnalysisProperties properties(Path repositoriesRoot) {
        return properties(repositoriesRoot, false, GRAPH, NO_DEMO);
    }

    public static SpringBootStaticAnalysisProperties properties(
            Path repositoriesRoot, boolean readOnly, Graph graph, Demo demo) {
        return new SpringBootStaticAnalysisProperties(
                repositoriesRoot,
                1_048_576,
                10_000,
                readOnly,
                new Indexing(1, 10),
                graph,
                demo);
    }

    private TestProperties() {
    }
}
