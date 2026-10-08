plugins {
    id("phoneagent.kotlin.jvm")
}

kotlin.sourceSets.main {
    kotlin.srcDir("../../data/llm/src/main/kotlin")
    kotlin.include("com/example/phoneagent/data/llm/dto/**", "com/example/phoneagent/data/llm/sse/**")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation("io.ktor:ktor-client-core:3.3.0")
    implementation("io.ktor:ktor-client-cio:3.3.0")
    testImplementation("io.ktor:ktor-client-mock:3.3.0")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
