package dev.springbootstaticanalysis.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.springbootstaticanalysis.TestProperties;
import jakarta.servlet.FilterChain;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WriteAccessFilterTest {

    private final WriteAccessFilter readOnlyFilter = filter(true);
    private final WriteAccessFilter localFilter = filter(false);

    @Test
    void rejectsMutatingApiRequestsFromThePublicEntrance() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        readOnlyFilter.doFilter(request("DELETE", "/api/repositories/abc"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(response.getHeader(HttpHeaders.ALLOW)).isEqualTo("GET, HEAD, OPTIONS");
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("read-only");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void acceptsMutatingApiRequestsFromTheLocalEntrance() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("POST", "/api/repositories");
        request.addHeader(WriteAccess.LOCAL_ENTRANCE_HEADER, "1");

        readOnlyFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void acceptsMutatingApiRequestsWhenTheDeploymentIsNotReadOnly() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        localFilter.doFilter(request("POST", "/api/repositories"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void allowsApiReads() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        readOnlyFilter.doFilter(request("GET", "/api/repositories"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void ignoresRequestsOutsideTheApi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        readOnlyFilter.doFilter(request("POST", "/actuator/health"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    private WriteAccessFilter filter(boolean readOnly) {
        return new WriteAccessFilter(
                new WriteAccess(TestProperties.properties(
                        Path.of("/workspace/repositories"),
                        readOnly,
                        TestProperties.GRAPH,
                        TestProperties.NO_DEMO)),
                new ObjectMapper());
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
