package dev.uwiii.nav

import android.app.Application
import org.maplibre.android.MapLibre

class UWiiiNavApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre initialisieren (Open Source Karten-Engine)
        MapLibre.getInstance(this)
    }
}
