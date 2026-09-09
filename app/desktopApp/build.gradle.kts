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
    implementation(libs.filekit.core)
    implementation(libs.logback)
    implementation(libs.compose.components.resources)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "ink.x2.subnetdrop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SubnetDrop"
            packageVersion = "1.0.0"
            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.sql",
                "jdk.security.auth",
                "jdk.unsupported",
            )
            macOS {
                iconFile.set(project.file("src/main/resources/icons/subnetdrop.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/subnetdrop.ico"))
            }
            linux {
                iconFile.set(rootProject.file("app/shared/src/commonMain/composeResources/drawable/subnetdrop_app_icon.png"))
            }
        }
    }
}
