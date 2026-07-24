package dev.codeatlas.git;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codeatlas.indexing.DiscoveredSourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHistoryAnalyzerTest {

    @TempDir
    Path root;

    @Test
    void aggregatesFileHistoryInOneRepositoryWalk() throws Exception {
        Path source = root.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Sample {}\n");
        try (Git git = Git.init().setDirectory(root.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("add sample")
                    .setAuthor("Atlas", "atlas@example.test")
                    .setCommitter("Atlas", "atlas@example.test")
                    .call();
        }
        DiscoveredSourceFile file = new DiscoveredSourceFile(
                UUID.randomUUID(),
                "src/main/java/demo/Sample.java",
                "MAIN",
                ".",
                "0".repeat(64),
                1,
                Files.size(source));

        List<GitFileStat> result = new GitHistoryAnalyzer().analyze(root, List.of(file));

        assertThat(result).singleElement().satisfies(stat -> {
            assertThat(stat.totalCommits()).isEqualTo(1);
            assertThat(stat.lastAuthorName()).isEqualTo("Atlas");
            assertThat(stat.recentSubjects()).containsExactly("add sample");
        });
    }
}
