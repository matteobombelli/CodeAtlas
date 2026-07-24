package dev.codeatlas.repository;

import dev.codeatlas.shared.InvalidRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {

    private final RepositoryPathGuard pathGuard;
    private final RepositoryStore store;

    public RepositoryService(RepositoryPathGuard pathGuard, RepositoryStore store) {
        this.pathGuard = pathGuard;
        this.store = store;
    }

    public RegisteredRepository register(String displayName, String relativePath) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 200) {
            throw new InvalidRequestException("displayName must contain between 1 and 200 characters");
        }
        Path path = pathGuard.resolve(relativePath);
        try (org.eclipse.jgit.lib.Repository gitRepository =
                     new RepositoryBuilder()
                             .setWorkTree(path.toFile())
                             .readEnvironment()
                             .findGitDir()
                             .build();
             Git git = new Git(gitRepository)) {
            String branch = gitRepository.getBranch();
            var head = gitRepository.resolve("HEAD");
            boolean dirty = !git.status().call().isClean();
            return store.create(
                    UUID.randomUUID(),
                    displayName.strip(),
                    Path.of(relativePath).normalize().toString().replace('\\', '/'),
                    path,
                    branch,
                    head == null ? null : head.name(),
                    dirty,
                    detectBuildSystem(path));
        } catch (IOException | GitAPIException exception) {
            throw new InvalidRequestException("Git metadata could not be read", exception);
        }
    }

    public List<RegisteredRepository> list() {
        return store.list();
    }

    public RegisteredRepository get(UUID id) {
        return store.get(id);
    }

    public void delete(UUID id) {
        store.delete(id);
    }

    private BuildSystem detectBuildSystem(Path path) {
        boolean gradle = Files.exists(path.resolve("settings.gradle"))
                || Files.exists(path.resolve("settings.gradle.kts"))
                || Files.exists(path.resolve("build.gradle"))
                || Files.exists(path.resolve("build.gradle.kts"));
        boolean maven = Files.exists(path.resolve("pom.xml"));
        if (gradle && maven) {
            return BuildSystem.GRADLE_AND_MAVEN;
        }
        if (gradle) {
            return BuildSystem.GRADLE;
        }
        if (maven) {
            return BuildSystem.MAVEN;
        }
        return BuildSystem.UNKNOWN;
    }
}
