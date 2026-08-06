package dev.sbsa.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sbsa.demo")
public record DemoBootstrapProperties(
        boolean enabled,
        String displayName,
        String relativePath) {
}
