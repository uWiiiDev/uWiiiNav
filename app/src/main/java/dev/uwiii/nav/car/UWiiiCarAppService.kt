package dev.uwiii.nav.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class UWiiiCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Entwicklermodus & Desktop Head Unit & echte Autos ohne Einschränkung zulassen
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return UWiiiCarSession()
    }
}
