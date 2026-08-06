package dev.springbootstaticanalysis.repository;

import dev.springbootstaticanalysis.shared.InvalidRequestException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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
        return store.create(
                UUID.randomUUID(),
                displayName.strip(),
                Path.of(relativePath).normalize().toString().replace('\\', '/'),
                path,
                detectBuildSystem(path));
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
