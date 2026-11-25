package dev.uwiii.nav.car

import android.graphics.Rect
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.uwiii.nav.R
import dev.uwiii.nav.state.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UWiiiCarScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    init {
        lifecycle.addObserver(this)
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        // Auf Zustandsänderungen (Route, Abbiegeschritt, Distanz) lauschen und Display im Auto aktualisieren
        job = scope.launch {
            NavigationState.currentStep.collectLatest {
                invalidate()
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        job?.cancel()
    }

    override fun onGetTemplate(): Template {
        val activeRoute = NavigationState.activeRoute.value
        val currentStep = NavigationState.currentStep.value
        val distanceMeters = NavigationState.distanceToNextStepMeters.value

        val templateBuilder = NavigationTemplate.Builder()
        templateBuilder.setBackgroundColor(CarColor.createCustom(0xFF0A0E17.toInt(), 0xFF0A0E17.toInt()))

        // 1. Manöver-Anweisungen (Abbiegekarte auf dem Auto-Display)
        if (activeRoute != null && currentStep != null) {
            val maneuverType = mapManeuverType(currentStep.maneuverType, currentStep.maneuverModifier)
            val maneuver = Maneuver.Builder(maneuverType).build()

            val step = Step.Builder(currentStep.instruction)
                .setManeuver(maneuver)
                .apply {
                    if (currentStep.streetName.isNotBlank()) {
                        setRoad(currentStep.streetName)
                    }
                }
                .build()

            val dist = if (distanceMeters >= 1000) {
                Distance.create(distanceMeters / 1000.0, Distance.UNIT_KILOMETERS)
            } else {
                Distance.create(distanceMeters.coerceAtLeast(10.0), Distance.UNIT_METERS)
            }

            val routingInfo = RoutingInfo.Builder()
                .setCurrentStep(step, dist)
                .build()

            templateBuilder.setNavigationInfo(routingInfo)

            // Aktionsleiste: Navigation beenden
            val actionStrip = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Beenden")
                        .setOnClickListener {
                            NavigationState.clearRoute()
                            invalidate()
                        }
                        .build()
                )
                .build()

            templateBuilder.setActionStrip(actionStrip)
        } else {
            // Keine aktive Route: Übersicht anzeigen
            val actionStrip = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("uWiiiNav")
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, R.mipmap.ic_launcher)
                            ).build()
                        )
                        .setOnClickListener {}
                        .build()
                )
                .build()

            templateBuilder.setActionStrip(actionStrip)
        }

        // Karten-Steuerleiste (z. B. Zentrieren)
        val mapActionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Zentrieren")
                    .setOnClickListener {
                        // Kamera auf aktuellen Standort zurückführen
                        invalidate()
                    }
                    .build()
            )
            .build()

        templateBuilder.setMapActionStrip(mapActionStrip)

        return templateBuilder.build()
    }

    private fun mapManeuverType(type: String, modifier: String): Int {
        return when (type) {
            "arrive" -> Maneuver.TYPE_DESTINATION
            "depart" -> Maneuver.TYPE_DEPART
            "roundabout" -> Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
            "turn" -> when (modifier) {
                "right" -> Maneuver.TYPE_TURN_NORMAL_RIGHT
                "left" -> Maneuver.TYPE_TURN_NORMAL_LEFT
                "slight right" -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
                "slight left" -> Maneuver.TYPE_TURN_SLIGHT_LEFT
                "sharp right" -> Maneuver.TYPE_TURN_SHARP_RIGHT
                "sharp left" -> Maneuver.TYPE_TURN_SHARP_LEFT
                "uturn" -> Maneuver.TYPE_U_TURN_LEFT
                else -> Maneuver.TYPE_STRAIGHT
            }
            "merge" -> Maneuver.TYPE_MERGE_RIGHT
            "ramp" -> if (modifier.contains("left")) Maneuver.TYPE_OFF_RAMP_SLIGHT_LEFT else Maneuver.TYPE_OFF_RAMP_SLIGHT_RIGHT
            "fork" -> if (modifier.contains("left")) Maneuver.TYPE_FORK_LEFT else Maneuver.TYPE_FORK_RIGHT
            else -> Maneuver.TYPE_STRAIGHT
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        // Auto-Bildschirm Oberfläche verfügbar
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {}

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {}
}
