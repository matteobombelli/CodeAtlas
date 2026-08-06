package dev.sbsa.source;

import dev.sbsa.repository.RepositoryStore;
import dev.sbsa.shared.ConflictException;
import dev.sbsa.shared.InvalidRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SourceService {

    private static final int MAX_EXCERPT_LINES = 200;

    private final RepositoryStore repositoryStore;
    private final SourceStore sourceStore;

    public SourceService(RepositoryStore repositoryStore, SourceStore sourceStore) {
        this.repositoryStore = repositoryStore;
        this.sourceStore = sourceStore;
    }

    public SourceExcerpt excerpt(
            UUID repositoryId,
            String relativePath,
            int requestedStart,
            int requestedEnd) {
        if (relativePath == null || relativePath.isBlank() || Path.of(relativePath).isAbsolute()) {
            throw new InvalidRequestException("Source path must be a non-empty relative path");
        }
        String normalized = Path.of(relativePath).normalize().toString().replace('\\', '/');
        if (normalized.startsWith("../") || normalized.equals("..")) {
            throw new InvalidRequestException("Source path escapes the repository");
        }
        IndexedSourceFile indexed = sourceStore.get(repositoryId, normalized);
        if (requestedStart < 1
                || requestedEnd < requestedStart
                || requestedEnd > indexed.lineCount()
                || requestedEnd - requestedStart + 1 > MAX_EXCERPT_LINES) {
            throw new InvalidRequestException(
                    "Source range must be within the indexed file and at most "
                            + MAX_EXCERPT_LINES + " lines");
        }

        Path root = repositoryStore.canonicalPath(repositoryId);
        try {
            Path file = root.resolve(normalized).normalize().toRealPath();
            if (!file.startsWith(root.toRealPath()) || !Files.isRegularFile(file)) {
                throw new InvalidRequestException("Source path escapes the repository");
            }
            byte[] bytes = Files.readAllBytes(file);
            String currentHash = hash(bytes);
            if (!currentHash.equals(indexed.contentHash())) {
                throw new ConflictException(
                        "Source changed after the active index; rescan before opening it");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String content = String.join(
                    "\n", lines.subList(requestedStart - 1, requestedEnd));
            return new SourceExcerpt(
                    normalized,
                    requestedStart,
                    requestedEnd,
                    "java",
                    content,
                    currentHash);
        } catch (IOException exception) {
            throw new InvalidRequestException("Source file could not be read safely", exception);
        }
    }

    private String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
