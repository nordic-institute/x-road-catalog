buildscript {
    /*dependencies {
        classpath(libs.licenseGradlePlugin) {
            exclude(group = "org.springframework", module = "spring-core")
        }
    }*/

    configurations.configureEach {
        resolutionStrategy {
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

val project_version by extra("4.0.0")

sonar {
    properties {
        property("sonar.projectKey", "nordic-institute_x-road-catalog")
        property("sonar.organization", "nordic-institute")
        property("sonar.exclusions", "**/src/test/**")
    }
}

subprojects {
    version = project_version
}
