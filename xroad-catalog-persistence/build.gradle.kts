plugins {
    java
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
    testRuntimeOnly(libs.junit.launcer)
    testImplementation(libs.lombok)
    testImplementation(libs.h2.database)
    testAnnotationProcessor(libs.lombok)
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
