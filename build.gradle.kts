buildscript {
    configurations.configureEach {
        resolutionStrategy {
            activateDependencyLocking()
            force(libs.plexus.utils)
            force(libs.commons.compress)
        }
    }
}

plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.spring.boot) apply false
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

sonar {
    properties {
        property("sonar.projectKey", "nordic-institute_x-road-catalog")
        property("sonar.organization", "nordic-institute")
        property("sonar.test.exclusions", "**/src/test/**")
    }
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
