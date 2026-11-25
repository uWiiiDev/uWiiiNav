package dev.uwiii.nav.state

import dev.uwiii.nav.location.NavLocation
import dev.uwiii.nav.routing.RouteData
import dev.uwiii.nav.routing.RouteStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng

object NavigationState {
    private val _currentLocation = MutableStateFlow<NavLocation?>(null)
    val currentLocation: StateFlow<NavLocation?> = _currentLocation.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteData?>(null)
    val activeRoute: StateFlow<RouteData?> = _activeRoute.asStateFlow()

    private val _currentStep = MutableStateFlow<RouteStep?>(null)
    val currentStep: StateFlow<RouteStep?> = _currentStep.asStateFlow()

    private val _distanceToNextStepMeters = MutableStateFlow<Double>(0.0)
    val distanceToNextStepMeters: StateFlow<Double> = _distanceToNextStepMeters.asStateFlow()

    private val _destinationName = MutableStateFlow<String>("")
    val destinationName: StateFlow<String> = _destinationName.asStateFlow()

    fun updateLocation(location: NavLocation) {
        _currentLocation.value = location
    }

    fun setRoute(route: RouteData?, destinationName: String = "") {
        _activeRoute.value = route
        _destinationName.value = destinationName
        if (route != null && route.steps.isNotEmpty()) {
            _currentStep.value = route.steps[0]
            _distanceToNextStepMeters.value = route.steps[0].distanceMeters
        } else {
            _currentStep.value = null
            _distanceToNextStepMeters.value = 0.0
        }
    }

    fun updateStep(step: RouteStep, distanceMeters: Double) {
        _currentStep.value = step
        _distanceToNextStepMeters.value = distanceMeters
    }

    fun clearRoute() {
        _activeRoute.value = null
        _currentStep.value = null
        _distanceToNextStepMeters.value = 0.0
        _destinationName.value = ""
    }
}
