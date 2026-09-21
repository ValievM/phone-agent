plugins {
    id("phoneagent.android.library")
}

android {
    namespace = "com.example.phoneagent.data.auth"
}

dependencies {
    api(project(":core:domain"))
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core:domain")))
}
