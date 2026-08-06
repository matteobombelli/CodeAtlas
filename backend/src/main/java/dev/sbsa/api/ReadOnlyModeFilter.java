package dev.sbsa.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects mutating API requests when the deployment is read-only.
 *
 * <p>Enforcing this at the web layer keeps internal callers such as
 * {@code DemoBootstrap} free to index on startup, and covers every current and
 * future mutating route without scattering checks through the services.
 */
@Component
@ConditionalOnProperty(prefix = "sbsa", name = "read-only", havingValue = "true")
public class ReadOnlyModeFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String ALLOWED = "GET, HEAD, OPTIONS";

    private final ObjectMapper objectMapper;

    public ReadOnlyModeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "This deployment is read-only; " + request.getMethod()
                        + " requests are not accepted.");
        problem.setTitle("Read-only deployment");
        problem.setType(URI.create(
                "https://github.com/matteobombelli/spring-boot-static-analysis/problems/read-only"));

        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        response.setHeader(HttpHeaders.ALLOW, ALLOWED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
