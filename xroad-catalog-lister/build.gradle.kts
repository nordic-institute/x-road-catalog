import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.spring.boot)
    java
    jacoco
    id("catalogservice.java-checks")
}

group = "org.niis.xroad.catalog.lister"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://artifactory.niis.org/artifactory/xroad-maven-snapshots/")
    }
    maven {
        url = uri("https://artifactory.niis.org/artifactory/xroad-maven-releases/")
    }
}

tasks.bootJar {
    enabled = true
    layered {
        enabled = false
    }
    includeTools = false
}

tasks.jar {
    enabled = false
}

dependencies {
    implementation(project(":xroad-catalog-persistence"))
    implementation(libs.jakarta)
    implementation(libs.spring.doc)
    implementation(libs.commons.csv)
    implementation(libs.spring.boot.jpa)
    implementation(libs.spring.boot.web)
    implementation(libs.jackson)
    implementation(libs.guava)
    implementation(libs.lombok)
    implementation(libs.xmlunit.core)
    implementation(libs.xroad.configuration.client)
    implementation(libs.xrd4j.server)

    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.mockito)
    testImplementation(libs.lombok)
    testImplementation(libs.h2.database)

    testRuntimeOnly(libs.junit.launcer)

    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    systemProperty("user.timezone", "Europe/Helsinki")
    useJUnitPlatform()
    testLogging {
        //events "passed", "skipped", "failed", "standardOut", "standardError"
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
    }
    finalizedBy(tasks.jacocoTestReport)
}

springBoot {
    mainClass.set("org.niis.xroad.catalog.lister.ListerApplication")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
    }
}
