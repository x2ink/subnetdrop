package ink.x2.kmp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import ink.x2.kmp.di.commonModules
import ink.x2.kmp.di.desktopPlatformModule
import ink.x2.kmp.runtime.LanChatRuntime
import java.awt.Dimension

fun main() {
    val koinApplication = startKoin {
        modules(commonModules() + desktopPlatformModule)
    }
    val runtime = koinApplication.koin.get<LanChatRuntime>()
    runBlocking { runtime.start() }
    launchApplication(runtime)
}

private fun launchApplication(runtime: LanChatRuntime) = application {
    val windowState = rememberWindowState(width = DEFAULT_WINDOW_WIDTH, height = DEFAULT_WINDOW_HEIGHT)
    Window(
        onCloseRequest = {
            runBlocking { runtime.stop() }
            runtime.close()
            exitApplication()
        },
        state = windowState,
        title = "局域网密聊",
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
        }
        App()
    }
}

private val DEFAULT_WINDOW_WIDTH = 1_180.dp
private val DEFAULT_WINDOW_HEIGHT = 760.dp
private const val MIN_WINDOW_WIDTH = 480
private const val MIN_WINDOW_HEIGHT = 420
