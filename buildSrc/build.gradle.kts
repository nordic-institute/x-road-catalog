plugins {
    `kotlin-dsl`
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.license.gradle.plugin)
}
