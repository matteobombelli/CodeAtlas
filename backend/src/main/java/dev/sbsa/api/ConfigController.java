package dev.sbsa.api;

import dev.sbsa.shared.SbsaProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the handful of settings the frontend needs, so the UI can hide
 * controls the deployment will refuse.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final SbsaProperties properties;

    public ConfigController(SbsaProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    ClientConfig get() {
        return new ClientConfig(properties.readOnly());
    }

    record ClientConfig(boolean readOnly) {
    }
}
