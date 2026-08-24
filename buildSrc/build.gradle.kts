plugins {
    `kotlin-dsl`
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.license.gradle.plugin)
}

configurations.configureEach {
    resolutionStrategy {
        // The license plugin drags in an ancient spring-core (3.1.3.RELEASE), which shadows the newer
        // spring-core needed by the Spring Boot Gradle plugin's layered-jar tooling on the shared
        // buildscript classpath, breaking layered bootJar extraction with a NoSuchMethodError.
        // Keep this in step with the spring-core version the Spring Boot Gradle plugin (see springBoot
        // in gradle/libs.versions.toml) resolves on the buildscript classpath.
        force("org.springframework:spring-core:6.2.17")
    }
}
