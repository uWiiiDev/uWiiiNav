package dev.uwiii.nav.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class UWiiiCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return UWiiiCarScreen(carContext)
    }
}
