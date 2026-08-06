package dev.sbsa.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sbsa.shared.SbsaProperties;
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

    @Test
    void excludesSymlinkedSourcesThatEscapeTheRepository() throws Exception {
        Path real = source("service/src/main/java/demo/Service.java", "class Service {}\n");
        Path outside = Files.writeString(
                Files.createDirectory(root.resolve("outside")).resolve("Secret.java"),
                "class Secret {}\n");
        Path link = root.resolve("service/src/main/java/demo/Secret.java");
        Files.createSymbolicLink(link, outside);

        SourceFileDiscovery discovery = discovery(root.resolve("service"));

        // Files.walk does not follow symlinked directories, but Files.isRegularFile
        // follows links, so the escaping file would otherwise be read and parsed.
        assertThat(discovery.discover(root.resolve("service"))).containsExactly(real);
        assertThatThrownBy(() -> discovery.describe(root.resolve("service"), link))
                .hasMessageContaining("escapes the repository");
    }

    private Path source(String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private SourceFileDiscovery discovery() {
        return discovery(root);
    }

    private SourceFileDiscovery discovery(Path approvedRoot) {
        return new SourceFileDiscovery(new SbsaProperties(
                approvedRoot,
                1_048_576,
                10_000,
                false,
                new SbsaProperties.Indexing(1, 10),
                new SbsaProperties.Graph(100, 250)));
    }
}
