import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    android {
        namespace = "ink.x2.subnetdrop.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.multiplatform.settings)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.jdbcDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

sqldelight {
    databases {
        create("ChatDatabase") {
            packageName.set("ink.x2.subnetdrop.data.db")
        }
    }
}
