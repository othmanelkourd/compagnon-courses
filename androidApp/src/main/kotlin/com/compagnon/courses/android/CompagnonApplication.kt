package com.compagnon.courses.android

import android.app.Application
import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.shared.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CompagnonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@CompagnonApplication)
            modules(
                sharedModules(
                    NetworkConfig(
                        baseUrl = BuildConfig.API_BASE_URL,
                        platform = PLATFORM,
                        appVersion = BuildConfig.VERSION_NAME,
                        enableLogging = BuildConfig.DEBUG,
                    ),
                ),
            )
        }
    }

    private companion object {
        /** Value of the `X-Platform` header. */
        const val PLATFORM: String = "android"
    }
}
