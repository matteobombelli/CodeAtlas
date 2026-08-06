package dev.springbootstaticanalysis.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring-boot-static-analysis.demo")
public record DemoBootstrapProperties(
        boolean enabled,
        String displayName,
        String relativePath) {
}
