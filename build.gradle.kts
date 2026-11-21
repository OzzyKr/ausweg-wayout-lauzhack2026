plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    // Skip code generated into build/ (e.g. Compose resource accessors).
    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
        exclude { it.file.path.contains("${File.separator}build${File.separator}") }
    }
}

// One detekt run over every module's sources: `./gradlew detekt`.
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom("config/detekt/detekt.yml")
    source.setFrom("shared/src", "composeApp/src", "server/src")
}

// detekt 1.23.8 is built against Kotlin 2.0.21; keep its own classpath on that version.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}
