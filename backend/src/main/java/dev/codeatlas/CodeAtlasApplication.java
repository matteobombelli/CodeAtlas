package dev.codeatlas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeAtlasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAtlasApplication.class, args);
    }
}
