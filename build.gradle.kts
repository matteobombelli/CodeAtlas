plugins {
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "dev.sbsa"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
