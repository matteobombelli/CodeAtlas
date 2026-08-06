package dev.codeatlas.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CodeSearchStoreTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CodeSearchStore search = new CodeSearchStore(jdbc);

    @Test
    void ignoresQueriesShorterThanTwoCharacters() {
        CodeSearchResponse response = search.search(UUID.randomUUID(), "a");

        assertThat(response.endpoints()).isEmpty();
        assertThat(response.methods()).isEmpty();
        assertThat(response.files()).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchesEachSupportedCategory() {
        CodeSearchResult result = new CodeSearchResult(
                UUID.randomUUID(),
                "IndexingService.java",
                "dev.codeatlas.indexing",
                "backend/src/main/java/dev/codeatlas/indexing/IndexingService.java",
                1,
                200,
                null);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of(result));

        CodeSearchResponse response = search.search(UUID.randomUUID(), " index ");

        assertThat(response.endpoints()).containsExactly(result);
        assertThat(response.methods()).containsExactly(result);
        assertThat(response.files()).containsExactly(result);
        verify(jdbc, times(3)).query(anyString(), anyMap(), any(RowMapper.class));
    }
}
