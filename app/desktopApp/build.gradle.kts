import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.koin.core)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "ink.x2.kmp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ink.x2.kmp"
            packageVersion = "1.0.0"
            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.sql",
                "jdk.security.auth",
                "jdk.unsupported",
            )
        }
    }
}
