plugins {
    id("phoneagent.kotlin.jvm")
}

dependencies {
    api(project(":core:domain"))
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core:domain")))
}
