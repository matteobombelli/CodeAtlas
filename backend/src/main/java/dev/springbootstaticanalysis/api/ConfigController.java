package dev.springbootstaticanalysis.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the frontend whether this entrance may use the mutating endpoints. */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final WriteAccess writeAccess;

    public ConfigController(WriteAccess writeAccess) {
        this.writeAccess = writeAccess;
    }

    @GetMapping
    ClientConfig get(HttpServletRequest request) {
        return new ClientConfig(!writeAccess.allows(request));
    }

    record ClientConfig(boolean readOnly) {
    }
}
