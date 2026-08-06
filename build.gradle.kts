plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "dev.springbootstaticanalysis"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
