package dev.codeatlas.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.codeatlas.shared.CodeAtlasProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryServiceTest {

    @TempDir
    Path root;

    @Test
    void registersGitWorkingTreeAndReadsMetadata() throws Exception {
        Path repository = Files.createDirectory(root.resolve("sample"));
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            Files.writeString(repository.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("initial")
                    .setAuthor("Code Atlas", "atlas@example.test")
                    .setCommitter("Code Atlas", "atlas@example.test")
                    .call();
        }

        RepositoryStore store = mock(RepositoryStore.class);
        UUID id = UUID.randomUUID();
        when(store.create(
                any(), eq("Sample"), eq("sample"), eq(repository.toRealPath()),
                anyString(), anyString(), anyBoolean(), eq(BuildSystem.GRADLE)))
                .thenReturn(new RegisteredRepository(
                        id, "Sample", "sample", "master", "abc", false,
                        BuildSystem.GRADLE, RepositoryStatus.REGISTERED,
                        null, Instant.now(), null, 0));

        RepositoryService service = new RepositoryService(guard(), store);

        RegisteredRepository registered = service.register("Sample", "sample");

        assertThat(registered.buildSystem()).isEqualTo(BuildSystem.GRADLE);
    }

    @Test
    void refreshesBranchCommitAndDirtyState() throws Exception {
        Path repository = Files.createDirectory(root.resolve("sample"));
        String head;
        String branch;
        try (Git git = Git.init().setDirectory(repository.toFile()).call()) {
            Files.writeString(repository.resolve("pom.xml"), "<project/>");
            git.add().addFilepattern(".").call();
            head = git.commit()
                    .setMessage("initial")
                    .setAuthor("Code Atlas", "atlas@example.test")
                    .setCommitter("Code Atlas", "atlas@example.test")
                    .call()
                    .getName();
            branch = git.getRepository().getBranch();
            Files.writeString(repository.resolve("pom.xml"), "<project><!-- dirty --></project>");
        }
        UUID id = UUID.randomUUID();
        RepositoryStore store = mock(RepositoryStore.class);
        when(store.canonicalPath(id)).thenReturn(repository.toRealPath());
        RegisteredRepository refreshed = new RegisteredRepository(
                id, "Sample", "sample", branch, head, true,
                BuildSystem.MAVEN, RepositoryStatus.READY,
                UUID.randomUUID(), Instant.now(), Instant.now(), 0);
        when(store.get(id)).thenReturn(refreshed);

        RegisteredRepository result = new RepositoryService(guard(), store).refreshGitState(id);

        verify(store).updateGitState(id, branch, head, true);
        assertThat(result.dirty()).isTrue();
    }

    private RepositoryPathGuard guard() {
        return new RepositoryPathGuard(new CodeAtlasProperties(
                root,
                1_048_576,
                10_000,
                new CodeAtlasProperties.Indexing(1, 10)));
    }
}
