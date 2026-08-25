plugins {
    `kotlin-dsl`
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    // The license plugin drags in an ancient spring-core (3.1.3.RELEASE), which shadows the newer spring-core
    // needed by the Spring Boot Gradle plugin's layered-jar tooling on the shared buildscript classpath,
    // breaking layered bootJar extraction with a NoSuchMethodError. Constraining spring-core through the Spring
    // Boot BOM keeps it in step with the springBoot version in gradle/libs.versions.toml automatically.
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.license.gradle.plugin)
}
