plugins {
    java
    `java-test-fixtures`
    jacoco
    id("catalogservice.java-checks")
}

group = "org.niis.xroad.catalog.persistence"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.jpa)
    implementation(libs.postgresql)
    implementation(libs.guava)
    implementation(libs.lombok)
    implementation(libs.lombok)
    implementation(libs.xmlunit.core)

    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.lombok)
    testImplementation(libs.h2.database)

    testRuntimeOnly(libs.junit.launcer)
    testRuntimeOnly(libs.liquibase)

    testAnnotationProcessor(libs.lombok)

    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.spring.boot.test)
}


tasks.test {
    systemProperty("user.timezone", "Europe/Helsinki")
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jar {
    enabled = true
    archiveClassifier = ""
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
    }
}
