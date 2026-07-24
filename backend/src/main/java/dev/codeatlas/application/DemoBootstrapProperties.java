package dev.codeatlas.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("code-atlas.demo")
public record DemoBootstrapProperties(
        boolean enabled,
        String displayName,
        String relativePath) {
}
