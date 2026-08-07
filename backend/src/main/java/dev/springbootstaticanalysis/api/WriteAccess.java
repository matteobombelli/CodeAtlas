package dev.springbootstaticanalysis.api;

import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Decides whether a request may change server state.
 *
 * <p>A local stack is writable for everyone. A public deployment sets
 * {@code read-only}, which keeps the published URL read-only but still admits
 * requests that arrive through the frontend's loopback-only entrance. That
 * entrance is the only one whose Nginx server block sets
 * {@value #LOCAL_ENTRANCE_HEADER}; the public server block always clears the
 * header, so a browser cannot forge it.
 */
@Component
public class WriteAccess {

    static final String LOCAL_ENTRANCE_HEADER = "X-Local-Entrance";

    private final boolean readOnly;

    public WriteAccess(SpringBootStaticAnalysisProperties properties) {
        this.readOnly = properties.readOnly();
    }

    public boolean allows(HttpServletRequest request) {
        return !readOnly || request.getHeader(LOCAL_ENTRANCE_HEADER) != null;
    }
}
