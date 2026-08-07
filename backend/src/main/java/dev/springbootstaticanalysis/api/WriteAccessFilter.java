package dev.springbootstaticanalysis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects every mutating API request that {@link WriteAccess} does not admit. */
@Component
public class WriteAccessFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String ALLOWED_METHODS = "GET, HEAD, OPTIONS";

    private final WriteAccess writeAccess;
    private final ObjectMapper objectMapper;

    public WriteAccessFilter(WriteAccess writeAccess, ObjectMapper objectMapper) {
        this.writeAccess = writeAccess;
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
        if (SAFE_METHODS.contains(request.getMethod()) || writeAccess.allows(request)) {
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
