package dev.springbootstaticanalysis.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFileDiscoveryTest {

    @TempDir
    Path root;

    @Test
    void discoversConventionalMainAndTestSourcesAndExcludesBuildOutput() throws Exception {
        Path main = source("service/src/main/java/demo/Service.java", "class Service {}\n");
        Path test = source("service/src/test/java/demo/ServiceTest.java", "class ServiceTest {}\n");
        source("service/build/generated/src/main/java/demo/Generated.java", "class Generated {}\n");
        source("other/Random.java", "class Random {}\n");

        SourceFileDiscovery discovery = discovery();

        assertThat(discovery.discover(root)).containsExactly(main, test);
        DiscoveredSourceFile described = discovery.describe(root, main);
        assertThat(described.moduleName()).isEqualTo("service");
        assertThat(described.sourceSet()).isEqualTo("MAIN");
        assertThat(described.contentHash()).hasSize(64);
    }

    private Path source(String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private SourceFileDiscovery discovery() {
        return new SourceFileDiscovery(new SpringBootStaticAnalysisProperties(
                root,
                1_048_576,
                10_000,
                new SpringBootStaticAnalysisProperties.Indexing(1, 10)));
    }
}
