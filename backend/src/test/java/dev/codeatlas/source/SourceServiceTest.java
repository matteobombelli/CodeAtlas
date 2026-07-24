package dev.codeatlas.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.codeatlas.repository.RepositoryStore;
import dev.codeatlas.shared.ConflictException;
import dev.codeatlas.shared.InvalidRequestException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceServiceTest {

    @TempDir
    Path root;

    @Test
    void returnsOnlyIndexedBoundedSourceAndDetectsStaleContent() throws Exception {
        UUID repositoryId = UUID.randomUUID();
        String relative = "src/main/java/demo/Sample.java";
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        byte[] content = "line one\nline two\nline three\n".getBytes();
        Files.write(file, content);

        RepositoryStore repositories = mock(RepositoryStore.class);
        SourceStore sources = mock(SourceStore.class);
        when(repositories.canonicalPath(repositoryId)).thenReturn(root);
        when(sources.get(repositoryId, relative)).thenReturn(
                new IndexedSourceFile(relative, hash(content), 3, content.length));
        SourceService service = new SourceService(repositories, sources);

        SourceExcerpt excerpt = service.excerpt(repositoryId, relative, 2, 3);

        assertThat(excerpt.content()).isEqualTo("line two\nline three");
        assertThatThrownBy(() -> service.excerpt(repositoryId, "../secret", 1, 1))
                .isInstanceOf(InvalidRequestException.class);

        Files.writeString(file, "changed\n");
        assertThatThrownBy(() -> service.excerpt(repositoryId, relative, 1, 1))
                .isInstanceOf(ConflictException.class);
    }

    private String hash(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
