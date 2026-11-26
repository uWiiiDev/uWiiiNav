package dev.uwiii.nav.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.Lane
import androidx.car.app.navigation.model.LaneDirection
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.uwiii.nav.state.NavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UWiiiCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        // Auf Zustandsänderungen (Route, Abbiegeschritt, Tunnel-Modus) lauschen und Display im Auto aktualisieren
        job = scope.launch {
            launch {
                NavigationState.currentStep.collectLatest {
                    invalidate()
                }
            }
            launch {
                NavigationState.activeRoute.collectLatest {
                    invalidate()
                }
            }
            launch {
                NavigationState.isTunnelMode.collectLatest {
                    invalidate()
                }
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        job?.cancel()
    }

    override fun onGetTemplate(): Template {
        return try {
            buildNavigationTemplate()
        } catch (e: Exception) {
            Log.e("UWiiiCarScreen", "Fehler beim Erstellen des Android Auto Templates", e)
            NavigationTemplate.Builder()
                .setNavigationInfo(
                    MessageInfo.Builder("uWiiiNav")
                        .setText("Fehler: ${e.message ?: "Unbekannter Fehler"}")
                        .build()
                )
                .setActionStrip(
                    ActionStrip.Builder()
                        .addAction(
                            Action.Builder()
                                .setTitle("Aktualisieren")
                                .setOnClickListener { invalidate() }
                                .build()
                        )
                        .build()
                )
                .build()
        }
    }

    private fun buildNavigationTemplate(): NavigationTemplate {
        val activeRoute = NavigationState.activeRoute.value
        val currentStep = NavigationState.currentStep.value
        val distanceMeters = NavigationState.distanceToNextStepMeters.value

        val templateBuilder = NavigationTemplate.Builder()
        templateBuilder.setBackgroundColor(CarColor.createCustom(0xFF0A0E17.toInt(), 0xFF0A0E17.toInt()))

        // 1. Manöver-Anweisungen (Abbiegekarte auf dem Auto-Display), wenn eine Route aktiv ist
        if (activeRoute != null && currentStep != null) {
            val maneuverType = mapManeuverType(currentStep.maneuverType, currentStep.maneuverModifier)
            val maneuver = Maneuver.Builder(maneuverType).build()

            val stepBuilder = Step.Builder(currentStep.instruction.ifBlank { "Dem Straßenverlauf folgen" })
                .setManeuver(maneuver)
                .apply {
                    if (currentStep.streetName.isNotBlank()) {
                        setRoad(currentStep.streetName)
                    }
                    if (NavigationState.isTunnelMode.value) {
                        setCue("Tunnel-Koppelnavigation aktiv")
                    }
                }

            if (currentStep.lanes.isNotEmpty()) {
                for (laneInfo in currentStep.lanes) {
                    val laneBuilder = Lane.Builder()
                    val indications = if (laneInfo.indications.isEmpty()) listOf("straight") else laneInfo.indications
                    for (ind in indications) {
                        val shape = when (ind) {
                            "left" -> LaneDirection.SHAPE_NORMAL_LEFT
                            "right" -> LaneDirection.SHAPE_NORMAL_RIGHT
                            "slight left" -> LaneDirection.SHAPE_SLIGHT_LEFT
                            "slight right" -> LaneDirection.SHAPE_SLIGHT_RIGHT
                            "sharp left" -> LaneDirection.SHAPE_SHARP_LEFT
                            "sharp right" -> LaneDirection.SHAPE_SHARP_RIGHT
                            "uturn" -> LaneDirection.SHAPE_U_TURN_LEFT
                            else -> LaneDirection.SHAPE_STRAIGHT
                        }
                        laneBuilder.addDirection(LaneDirection.create(shape, laneInfo.isValid))
                    }
                    stepBuilder.addLane(laneBuilder.build())
                }
            }

            val step = stepBuilder.build()

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
            // Keine aktive Route: Übersicht / Status anzeigen
            val messageInfo = MessageInfo.Builder("Bereit zur Navigation")
                .setText("Wähle ein Ziel auf dem Smartphone aus")
                .build()

            templateBuilder.setNavigationInfo(messageInfo)

            val actionStrip = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("uWiiiNav")
                        .setOnClickListener {
                            invalidate()
                        }
                        .build()
                )
                .build()

            templateBuilder.setActionStrip(actionStrip)
        }

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
}
