import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    android {
        namespace = "ink.x2.subnetdrop.network"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core"))
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.serializationJson)
            }
        }
        androidMain {
            kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
            dependencies {
                implementation(libs.ktor.clientCore)
                implementation(libs.ktor.clientCio)
                implementation(libs.ktor.clientWebsockets)
                implementation(libs.ktor.serverCore)
                implementation(libs.ktor.serverCio)
                implementation(libs.ktor.serverWebsockets)
                implementation(libs.tink.android)
            }
        }
        jvmMain {
            kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
            dependencies {
                implementation(libs.ktor.clientCore)
                implementation(libs.ktor.clientCio)
                implementation(libs.ktor.clientWebsockets)
                implementation(libs.ktor.serverCore)
                implementation(libs.ktor.serverCio)
                implementation(libs.ktor.serverWebsockets)
                implementation(libs.jmdns)
                implementation(libs.java.keyring)
                implementation(libs.tink)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
