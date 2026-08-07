package dev.springbootstaticanalysis.api;

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

/** Rejects every mutating API request when a deployment is public and read-only. */
@Component
@ConditionalOnProperty(
        prefix = "spring-boot-static-analysis",
        name = "read-only",
        havingValue = "true")
public class ReadOnlyModeFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String ALLOWED_METHODS = "GET, HEAD, OPTIONS";

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
                "urn:spring-boot-static-analysis:problem:read-only-deployment"));

        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        response.setHeader(HttpHeaders.ALLOW, ALLOWED_METHODS);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
