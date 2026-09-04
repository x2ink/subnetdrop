import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.sqldelight) apply false
}

plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().downloadBaseUrl.set("https://mirrors.aliyun.com/nodejs-release")
}

plugins.withType<WasmNodeJsPlugin> {
    the<WasmNodeJsEnvSpec>().downloadBaseUrl.set("https://mirrors.aliyun.com/nodejs-release")
}

plugins.withType<YarnPlugin> {
    the<YarnRootEnvSpec>().downloadBaseUrl.set("https://repo.huaweicloud.com/yarn")
}

plugins.withType<WasmYarnPlugin> {
    the<WasmYarnRootEnvSpec>().downloadBaseUrl.set("https://repo.huaweicloud.com/yarn")
}
