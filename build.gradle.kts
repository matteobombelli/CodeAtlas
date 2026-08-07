plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "dev.springbootstaticanalysis"
    version = "0.1.0"

    // Temporary security overrides until the Spring Boot 3.5 BOM advances.
    extra["jackson-bom.version"] = "2.21.5"
    extra["postgresql.version"] = "42.7.12"

    repositories {
        mavenCentral()
    }
}
