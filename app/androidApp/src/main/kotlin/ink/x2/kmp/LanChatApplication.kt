package ink.x2.kmp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ink.x2.kmp.di.androidPlatformModule
import ink.x2.kmp.di.commonModules
import ink.x2.kmp.runtime.LanChatRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LanChatApplication : Application(), DefaultLifecycleObserver {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleMutex = Mutex()
    @Volatile
    private var isForeground = false
    private lateinit var runtime: LanChatRuntime

    override fun onCreate() {
        super<Application>.onCreate()
        val koinApplication = startKoin {
            androidContext(this@LanChatApplication)
            modules(commonModules() + androidPlatformModule)
        }
        runtime = koinApplication.koin.get()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
        reconcileRuntime()
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
        reconcileRuntime()
    }

    override fun onTerminate() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        runtime.close()
        super<Application>.onTerminate()
    }

    private fun reconcileRuntime() {
        applicationScope.launch {
            lifecycleMutex.withLock {
                if (isForeground) runtime.start() else runtime.stop()
            }
        }
    }
}
