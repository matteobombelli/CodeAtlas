package dev.codeatlas.indexing;

import dev.codeatlas.shared.CodeAtlasProperties;
import dev.codeatlas.shared.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SourceFileDiscovery {

    private static final Set<String> EXCLUDED_SEGMENTS = Set.of(
            ".git", ".gradle", ".idea", "build", "target", "out",
            "generated", "node_modules");

    private final CodeAtlasProperties properties;

    public SourceFileDiscovery(CodeAtlasProperties properties) {
        this.properties = properties;
    }

    public List<Path> discover(Path repositoryRoot) {
        List<Path> results = new ArrayList<>();
        try (var paths = Files.walk(repositoryRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !isExcluded(repositoryRoot.relativize(path)))
                    .filter(path -> isConventionalSource(repositoryRoot.relativize(path)))
                    .forEach(results::add);
        } catch (IOException exception) {
            throw new InvalidRequestException("Java source discovery failed", exception);
        }
        if (results.size() > properties.maxSourceFiles()) {
            throw new InvalidRequestException(
                    "Repository exceeds the configured source-file limit of "
                            + properties.maxSourceFiles());
        }
        return results.stream().sorted().toList();
    }

    public DiscoveredSourceFile describe(Path root, Path file) {
        try {
            long size = Files.size(file);
            if (size > properties.maxSourceFileBytes()) {
                throw new InvalidRequestException(
                        "Source file exceeds the configured size limit: " + root.relativize(file));
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String relative = root.relativize(file).toString().replace('\\', '/');
            return new DiscoveredSourceFile(
                    UUID.randomUUID(),
                    relative,
                    relative.contains("/src/test/java/") || relative.startsWith("src/test/java/")
                            ? "TEST" : "MAIN",
                    moduleName(relative),
                    sha256(file),
                    content.isEmpty() ? 0 : content.lines().toList().size(),
                    size);
        } catch (IOException exception) {
            throw new InvalidRequestException("Source file could not be read: " + file, exception);
        }
    }

    private boolean isExcluded(Path relative) {
        for (Path segment : relative) {
            if (EXCLUDED_SEGMENTS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isConventionalSource(Path relative) {
        String normalized = relative.toString().replace('\\', '/');
        return normalized.startsWith("src/main/java/")
                || normalized.startsWith("src/test/java/")
                || normalized.contains("/src/main/java/")
                || normalized.contains("/src/test/java/");
    }

    private String moduleName(String relative) {
        int source = relative.indexOf("/src/");
        return source < 0 ? "." : relative.substring(0, source);
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
