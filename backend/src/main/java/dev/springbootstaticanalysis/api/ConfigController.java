package dev.springbootstaticanalysis.api;

import dev.springbootstaticanalysis.shared.SpringBootStaticAnalysisProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes the deployment mode needed to hide controls the API will refuse. */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final SpringBootStaticAnalysisProperties properties;

    public ConfigController(SpringBootStaticAnalysisProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    ClientConfig get() {
        return new ClientConfig(properties.readOnly());
    }

    record ClientConfig(boolean readOnly) {
    }
}
