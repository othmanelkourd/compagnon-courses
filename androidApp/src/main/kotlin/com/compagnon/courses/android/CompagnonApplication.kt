package com.compagnon.courses.android

import android.app.Application
import android.content.res.Configuration
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.CatalogUpdater
import com.compagnon.courses.core.network.NetworkConfig
import com.compagnon.courses.shared.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.Locale

class CompagnonApplication : Application() {

    private lateinit var catalogUpdater: CatalogUpdater

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
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
                    deviceCatalogScope(),
                ),
            )
        }.koin
        // The bundled catalog is installed, then (debug builds, which have a server) the server's latest release is
        // checked, in the background.
        catalogUpdater = koin.get()
        catalogUpdater.start()
    }

    // Android keeps the process when the phone language changes: onCreate does not run again.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        catalogUpdater.switchTo(deviceCatalogScope())
    }

    /** French devices get the regional French catalog, every other language the English one. */
    private fun deviceCatalogScope() = CatalogScope.forDevice(Locale.getDefault().toLanguageTag())

    private companion object {
        /** Value of the `X-Platform` header. */
        const val PLATFORM: String = "android"
    }
}
