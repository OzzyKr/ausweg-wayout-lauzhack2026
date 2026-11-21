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

kotlin {
    // kotlin.time.Instant is still experimental in Kotlin 2.2; it is what kotlinx-datetime 0.7 uses.
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }
}

// Run from the repo root so relative paths like data/seed/events.json work.
tasks.named<JavaExec>("run") { workingDir = rootDir }

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)
    // OJP calls (the key stays on the server); both already in the catalog for shared.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.ktor.server.test.host)
}
