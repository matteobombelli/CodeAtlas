package dev.springbootstaticanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Local-first entry point for the Spring Boot Static Analysis modular monolith.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringBootStaticAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootStaticAnalysisApplication.class, args);
    }
}
