plugins {
    id("phoneagent.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.phoneagent.tools"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core:domain")))
}
