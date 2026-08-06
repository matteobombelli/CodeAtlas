pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "spring-boot-static-analysis"

include("backend")
include("demo-app")
