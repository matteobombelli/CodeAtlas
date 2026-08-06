package dev.sbsa.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ReadOnlyModeFilterTest {

    private final ReadOnlyModeFilter filter = new ReadOnlyModeFilter(new ObjectMapper());

    @Test
    void rejectsMutatingApiRequests() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("DELETE", "/api/repositories/abc"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(response.getHeader(HttpHeaders.ALLOW)).isEqualTo("GET, HEAD, OPTIONS");
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("read-only");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsReads() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("GET", "/api/repositories"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresRequestsOutsideTheApi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Actuator and static assets are not the filter's business.
        filter.doFilter(request("POST", "/actuator/health"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
