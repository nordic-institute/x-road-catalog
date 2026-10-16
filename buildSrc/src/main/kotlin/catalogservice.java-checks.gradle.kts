import org.gradle.api.plugins.quality.Checkstyle

plugins {
    id("com.github.hierynomus.license")
    checkstyle
    pmd
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

license {
    header = rootProject.file("LICENSE.txt")
    include("**/*.java")
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseCheck>("licenseMain") {
    source = fileTree("src/main")
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseCheck>("licenseTest") {
    source = fileTree("src/test")
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseFormat>("licenseFormatMain") {
    source = fileTree("src/main")
}

tasks.named<com.hierynomus.gradle.license.tasks.LicenseFormat>("licenseFormatTest") {
    source = fileTree("src/test")
}

checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().toString()
    configDirectory = file("${project.rootDir}/config/checkstyle")
    isIgnoreFailures = false
    isShowViolations = false
    enableExternalDtdLoad = true
}

tasks.named<Checkstyle>("checkstyleMain") {
    source = fileTree("src/main/java")
    configFile = file("${project.rootDir}/config/checkstyle/checkstyle.xml")
}

tasks.named<Checkstyle>("checkstyleTest") {
    source = fileTree("src/test/java")
    configFile = file("${project.rootDir}/config/checkstyle/checkstyle.xml")
}

pmd {
    isConsoleOutput = true
    toolVersion = libs.findVersion("pmd").get().toString()
    rulesMinimumPriority = 5
    ruleSets = listOf("${project.rootDir}/config/pmd/custom-ruleset.xml")
}

configurations.checkstyle {
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.collections:google-collections") {
        select("com.google.guava:guava:0")
    }
}
