plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "ch.ausweg"
version = "0.1.0"

application {
    mainClass.set("ch.ausweg.server.ApplicationKt")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.ktor.server.test.host)
}
