plugins {
    id("phoneagent.android.library")
}

android {
    namespace = "com.example.phoneagent.tools"
}

dependencies {
    api(project(":core:domain"))
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core:domain")))
}
