package dev.codeatlas.repository;

import dev.codeatlas.shared.CodeAtlasProperties;
import dev.codeatlas.shared.InvalidRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class RepositoryPathGuard {

    private final CodeAtlasProperties properties;

    public RepositoryPathGuard(CodeAtlasProperties properties) {
        this.properties = properties;
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new InvalidRequestException("relativePath is required");
        }

        Path requested = Path.of(relativePath);
        if (requested.isAbsolute()) {
            throw new InvalidRequestException("Repository path must be relative to the approved root");
        }

        try {
            Path approvedRoot = properties.repositoriesRoot().toRealPath();
            Path resolved = approvedRoot.resolve(requested).normalize().toRealPath();
            if (!resolved.startsWith(approvedRoot)) {
                throw new InvalidRequestException("Repository path escapes the approved root");
            }
            if (!Files.isDirectory(resolved) || !Files.exists(resolved.resolve(".git"))) {
                throw new InvalidRequestException("Path is not a readable Git repository");
            }
            return resolved;
        } catch (IOException exception) {
            throw new InvalidRequestException("Repository path cannot be resolved under the approved root", exception);
        }
    }
}
