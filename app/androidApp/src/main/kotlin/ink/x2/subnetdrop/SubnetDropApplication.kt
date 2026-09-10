package ink.x2.subnetdrop

import android.app.Application
import ink.x2.subnetdrop.di.androidPlatformModule
import ink.x2.subnetdrop.di.commonModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SubnetDropApplication : Application() {
    override fun onCreate() {
        super<Application>.onCreate()
        startKoin {
            androidContext(this@SubnetDropApplication)
            modules(commonModules() + androidPlatformModule)
        }
    }
}
