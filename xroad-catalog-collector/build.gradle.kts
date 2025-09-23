
plugins {
    alias(libs.plugins.spring.boot)
    java
    jacoco
    id("catalogservice.java-checks")
}

group = "org.niis.xroad.catalog.collector"

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

tasks.withType<Jar>().configureEach {
    enabled = false
    duplicatesStrategy = DuplicatesStrategy.WARN
}

springBoot {
    mainClass = "org.niis.xroad.catalog.collector.CollectorApplication"
}

dependencies {
    implementation(project(":xroad-catalog-persistence"))
    implementation(libs.jakarta.annotation)
    implementation(libs.guava)
    implementation(libs.http.client)
    implementation(libs.json)
    implementation(libs.spring.boot.jpa)
    implementation(libs.spring.boot.web)
    implementation(libs.jackson)
    implementation(libs.xrd4j.client)
    implementation(libs.lombok)
    implementation(libs.liquibase)
    implementation(libs.xmlunit.core)
    annotationProcessor(libs.lombok)
    compileOnly(libs.liquibase.hibernate6)
    testImplementation(libs.spring.boot.test)
    testRuntimeOnly(libs.junit.launcer)
    testImplementation(libs.mockito)
    testAnnotationProcessor(libs.lombok)
    testImplementation(libs.awaitility)
    testImplementation(libs.h2.database)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
    }
}
