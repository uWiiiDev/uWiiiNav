package dev.uwiii.nav.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.uwiii.nav.audio.VoiceNavigator
import dev.uwiii.nav.camera.SpeedCamera
import dev.uwiii.nav.camera.SpeedCameraService
import dev.uwiii.nav.discovery.DiscoveryItem
import dev.uwiii.nav.discovery.DiscoveryService
import dev.uwiii.nav.favorites.FavoritesManager
import dev.uwiii.nav.fuel.GasPriceService
import dev.uwiii.nav.location.NavLocation
import dev.uwiii.nav.offline.OfflineMapManager
import dev.uwiii.nav.routing.RouteData
import dev.uwiii.nav.routing.RoutingService
import dev.uwiii.nav.speed.SpeedLimitService
import dev.uwiii.nav.traffic.IncidentType
import dev.uwiii.nav.traffic.TrafficIncident
import dev.uwiii.nav.traffic.TrafficService
import dev.uwiii.nav.trips.TripLogger
import dev.uwiii.nav.trips.TripRecord
import dev.uwiii.nav.ui.components.DiscoveryCardBanner
import dev.uwiii.nav.ui.components.DiscoverySettingsDialog
import dev.uwiii.nav.ui.components.GasPriceDialog
import dev.uwiii.nav.ui.components.NavigationBottomBar
import dev.uwiii.nav.ui.components.NavigationTopBanner
import dev.uwiii.nav.ui.components.OfflineMapsDialog
import dev.uwiii.nav.ui.components.SearchBarOverlay
import dev.uwiii.nav.ui.components.SpeedCameraAlertBanner
import dev.uwiii.nav.ui.components.SpeedometerOverlay
import dev.uwiii.nav.ui.components.TrafficAlertBanner
import dev.uwiii.nav.ui.components.TripHistoryDialog
import dev.uwiii.nav.ui.components.TripSummaryDialog
import dev.uwiii.nav.ui.components.VoiceSettingsDialog
import dev.uwiii.nav.ui.theme.CyanPrimary
import dev.uwiii.nav.ui.theme.DarkSurface
import dev.uwiii.nav.ui.theme.DarkSurface80
import dev.uwiii.nav.ui.theme.SpeedGreen
import dev.uwiii.nav.ui.theme.SpeedRed
import dev.uwiii.nav.ui.theme.SpeedYellow
import dev.uwiii.nav.ui.components.RouteSelectionBar
import dev.uwiii.nav.ui.components.TunnelAlertBanner
import dev.uwiii.nav.ui.theme.TextMuted
import dev.uwiii.nav.ui.theme.TextWhite
import dev.uwiii.nav.updater.GitHubUpdateChecker
import dev.uwiii.nav.updater.UpdateInfo
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Calendar
import kotlin.math.*

enum class CameraPerspective {
    VIEW_2D,        // Draufsicht von oben (0° Neigung, genordet)
    VIEW_3D,        // Klassische 3D-Verfolgerkamera von schräg oben (55° Neigung)
    VIEW_COCKPIT    // Echte Fahrersicht (75° Neigung, tief über dem Asphalt mit Weitblick zum Horizont)
}

@SuppressLint("MissingPermission")
@Composable
fun NavigationScreen(
    currentLocation: NavLocation?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val currentVersionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.7.1"
        } catch (e: Exception) {
            "1.7.1"
        }
    }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var perspective by remember { mutableStateOf(CameraPerspective.VIEW_COCKPIT) }
    val is3DMode = perspective != CameraPerspective.VIEW_2D
    var followUserLocation by remember { mutableStateOf(true) }

    // Routing & Navigation State
    val routingService = remember { RoutingService() }
    val voiceNavigator = remember { VoiceNavigator(context) }
    val offlineMapManager = remember { OfflineMapManager(context) }
    val favoritesManager = remember { FavoritesManager(context) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var activeRoute by remember { mutableStateOf<RouteData?>(null) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var distanceToNextStep by remember { mutableStateOf(0.0) }
    var activePolylineCasing by remember { mutableStateOf<Polyline?>(null) }
    var activePolylineCore by remember { mutableStateOf<Polyline?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Alternativrouten State
    var candidateRoutes by remember { mutableStateOf<List<RouteData>>(emptyList()) }
    var selectedRouteIndex by remember { mutableStateOf(0) }
    var destinationPendingTarget by remember { mutableStateOf<LatLng?>(null) }
    var destinationPendingName by remember { mutableStateOf("") }
    var alternativePolylines by remember { mutableStateOf<List<Polyline>>(emptyList()) }

    // Tunnel-Modus & Dead Reckoning (Koppelnavigation) State
    var isTunnelMode by remember { mutableStateOf(false) }
    var lastGpsTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastKnownSpeedKmh by remember { mutableStateOf(0f) }
    var deadReckoningPointIndex by remember { mutableStateOf(0) }
    var deadReckoningLocation by remember { mutableStateOf<LatLng?>(null) }

    // Kartenstil-Einstellung (Standard: Liberty Hell & Kontrastreich, da OpenFreeMap Dark zu dunkel ist)
    val prefs = remember { context.getSharedPreferences("uWiiiNav_prefs", android.content.Context.MODE_PRIVATE) }
    var mapThemeMode by remember { mutableStateOf(prefs.getString("map_theme_mode", "liberty") ?: "liberty") }

    val isSystemDark = isSystemInDarkTheme()
    val isNightMode = remember(isSystemDark, isTunnelMode) {
        if (isTunnelMode) {
            true
        } else {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            isSystemDark || hour < 7 || hour >= 20
        }
    }
    val targetStyleUrl = when (mapThemeMode) {
        "liberty" -> "https://tiles.openfreemap.org/styles/liberty"
        "dark" -> "https://tiles.openfreemap.org/styles/dark"
        else -> if (isNightMode) "https://tiles.openfreemap.org/styles/dark" else "https://tiles.openfreemap.org/styles/liberty"
    }

    // Gefahrenstellen- & Blitzerwarner State
    val cameraService = remember { SpeedCameraService() }
    var isCameraAlertEnabled by remember { mutableStateOf(true) }
    var currentAlertCamera by remember { mutableStateOf<SpeedCamera?>(null) }
    var currentAlertDistance by remember { mutableStateOf(0.0) }

    // Entdecker-Modus (Kultur & Wissenswertes in der Umgebung)
    val discoveryService = remember { DiscoveryService(context) }
    var currentDiscoveryItem by remember { mutableStateOf<DiscoveryItem?>(null) }
    var showDiscoveryDialog by remember { mutableStateOf(false) }

    // Autobahn Live-Verkehr & Stau-Service
    val trafficService = remember { TrafficService() }
    var currentTrafficIncident by remember { mutableStateOf<TrafficIncident?>(null) }
    var currentTrafficDistance by remember { mutableStateOf(0.0) }
    var isTrafficAlertEnabled by remember { mutableStateOf(true) }

    // Tempolimit-Erkennung (OSM MaxSpeed)
    val speedLimitService = remember { SpeedLimitService(context) }

    // Fahrtenbuch & Trip-Logger
    val tripLogger = remember { TripLogger(context) }
    var completedTripRecord by remember { mutableStateOf<TripRecord?>(null) }
    var showTripHistoryDialog by remember { mutableStateOf(false) }

    // Spritpreis-Radar (Tankerkönig API)
    val gasPriceService = remember { GasPriceService(context) }
    var showGasPriceDialog by remember { mutableStateOf(false) }

    // GitHub Update Checker State
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateChecker = remember { GitHubUpdateChecker(context) }

    // MapView Lifecycle
    DisposableEffect(lifecycleOwner, mapViewInstance) {
        val mapView = mapViewInstance ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // VoiceNavigator Lifecycle (nur einmalig beim Verlassen des Screens beenden)
    DisposableEffect(Unit) {
        onDispose {
            voiceNavigator.shutdown()
        }
    }

    // Beim Start einmal im Hintergrund nach Updates schauen
    LaunchedEffect(Unit) {
        val info = updateChecker.checkForUpdates(currentVersionName)
        if (info.hasUpdate) {
            updateInfo = info
            showUpdateDialog = true
        }
    }

    // Kamera & 3D-Fahrzeugposition aktualisieren, wenn sich der GPS-Standort ändert
    LaunchedEffect(currentLocation, followUserLocation, perspective) {
        val loc = currentLocation ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect

        // GPS-Aktualisierungszeitpunkt & Koppelnavigations-Basis sichern
        lastGpsTimestamp = System.currentTimeMillis()
        lastKnownSpeedKmh = loc.speedKmh
        deadReckoningLocation = LatLng(loc.latitude, loc.longitude)

        if (isTunnelMode) {
            isTunnelMode = false
            dev.uwiii.nav.state.NavigationState.setTunnelMode(false)
            voiceNavigator.speak("GPS-Signal wiederhergestellt.")
        }

        // 1. 3D-Navigations-Pfeil (Chevron) mit realem Standort & Richtung füttern
        try {
            if (map.locationComponent.isLocationComponentActivated) {
                val androidLoc = android.location.Location("fused").apply {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    bearing = loc.bearing
                    speed = loc.speedKmh / 3.6f
                    accuracy = loc.accuracy
                    time = System.currentTimeMillis()
                }
                map.locationComponent.forceLocationUpdate(androidLoc)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Cockpit-Kameraführung mit freiem Blick nach vorn zum Horizont
        if (followUserLocation) {
            val metrics = context.resources.displayMetrics
            // Tiefen-Padding für Cockpit: Fahrzeug ganz nach unten setzen (85% freie Sicht voraus)
            val bottomPadding = when (perspective) {
                CameraPerspective.VIEW_2D -> 0
                CameraPerspective.VIEW_3D -> (metrics.heightPixels * 0.30).toInt()
                CameraPerspective.VIEW_COCKPIT -> (metrics.heightPixels * 0.44).toInt()
            }
            map.setPadding(0, 0, 0, bottomPadding)

            // Dynamischer Zoom & Neigung je nach Perspektive:
            val targetZoom = when (perspective) {
                CameraPerspective.VIEW_2D -> if (activeRoute != null) 16.5 else 16.0
                CameraPerspective.VIEW_3D -> if (loc.speedKmh > 75f) 16.2 else 17.4
                CameraPerspective.VIEW_COCKPIT -> if (loc.speedKmh > 75f) 17.2 else 18.2
            }
            val targetTilt = when (perspective) {
                CameraPerspective.VIEW_2D -> 0.0
                CameraPerspective.VIEW_3D -> if (loc.speedKmh > 75f) 52.0 else 58.0
                CameraPerspective.VIEW_COCKPIT -> if (loc.speedKmh > 75f) 72.0 else 76.0 // Echte Fahrersicht flach über Asphalt
            }
            val targetBearing = when (perspective) {
                CameraPerspective.VIEW_2D -> 0.0
                else -> loc.bearing.toDouble()
            }

            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(loc.latitude, loc.longitude))
                .zoom(targetZoom)
                .tilt(targetTilt)
                .bearing(targetBearing)
                .build()

            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 650)
        }
    }

    // Dead Reckoning (Koppelnavigation bei Tunnelfahrt / GPS-Verlust)
    LaunchedEffect(activeRoute) {
        val route = activeRoute ?: return@LaunchedEffect
        while (true) {
            delay(500L)
            if (activeRoute == null) break

            val now = System.currentTimeMillis()
            val timeSinceGps = now - lastGpsTimestamp

            // Wenn seit mehr als 2.5 Sekunden kein GPS-Signal ankam und wir vor dem Verlust fuhren:
            if (timeSinceGps > 2500L && lastKnownSpeedKmh > 10f) {
                if (!isTunnelMode) {
                    isTunnelMode = true
                    dev.uwiii.nav.state.NavigationState.setTunnelMode(true)
                    voiceNavigator.speak("GPS-Signal unterbrochen. Tunnel-Modus aktiv.")
                }

                // Koppelnavigation: Berechne gefahrene Distanz in 0.5 Sekunden
                val speedMs = (lastKnownSpeedKmh.coerceAtLeast(20f) / 3.6f)
                val distMoved = speedMs * 0.5

                val (nextPoint, nextIdx) = advanceAlongRoute(
                    points = route.points,
                    startIndex = deadReckoningPointIndex,
                    distanceMeters = distMoved.toDouble()
                )
                deadReckoningPointIndex = nextIdx
                deadReckoningLocation = nextPoint

                val bearing = if (nextIdx < route.points.size - 1) {
                    calculateBearing(route.points[nextIdx], route.points[nextIdx + 1])
                } else {
                    currentLocation?.bearing ?: 0f
                }

                // Virtuellen Standort an MapLibre-Chevron & Kamera übergeben
                mapInstance?.let { map ->
                    try {
                        if (map.locationComponent.isLocationComponentActivated) {
                            val virtualLoc = android.location.Location("dead_reckoning").apply {
                                latitude = nextPoint.latitude
                                longitude = nextPoint.longitude
                                this.bearing = bearing
                                speed = speedMs
                                accuracy = 15f
                                time = now
                            }
                            map.locationComponent.forceLocationUpdate(virtualLoc)
                        }

                        if (followUserLocation) {
                            val tilt = when (perspective) {
                                CameraPerspective.VIEW_2D -> 0.0
                                CameraPerspective.VIEW_3D -> 56.0
                                CameraPerspective.VIEW_COCKPIT -> 75.0
                            }
                            val zoom = when (perspective) {
                                CameraPerspective.VIEW_2D -> 16.0
                                CameraPerspective.VIEW_3D -> 17.2
                                CameraPerspective.VIEW_COCKPIT -> 18.0
                            }
                            val camBearing = when (perspective) {
                                CameraPerspective.VIEW_2D -> 0.0
                                else -> bearing.toDouble()
                            }
                            val cameraPosition = CameraPosition.Builder()
                                .target(nextPoint)
                                .zoom(zoom)
                                .tilt(tilt)
                                .bearing(camBearing)
                                .build()
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 450)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Schritt-Distanz im Tunnel aktualisieren
                if (currentStepIndex < route.steps.size) {
                    val currentStep = route.steps[currentStepIndex]
                    val dist = calculateDistanceMeters(
                        nextPoint.latitude, nextPoint.longitude,
                        currentStep.location.latitude, currentStep.location.longitude
                    )
                    distanceToNextStep = dist
                    dev.uwiii.nav.state.NavigationState.updateStep(currentStep, dist)

                    // Nächster Schritt erreicht?
                    if (dist < 35.0 && currentStepIndex < route.steps.size - 1) {
                        currentStepIndex++
                        val nextStep = route.steps[currentStepIndex]
                        dev.uwiii.nav.state.NavigationState.updateStep(nextStep, nextStep.distanceMeters)
                        voiceNavigator.speak(nextStep.instruction)
                    }
                }
            }
        }
    }

    // Beim ersten GPS-Empfang Blitzer im Umkreis laden
    LaunchedEffect(currentLocation != null) {
        currentLocation?.let { loc ->
            cameraService.loadCamerasAround(loc.latitude, loc.longitude, 12.0)
        }
    }

    // Blitzer-Prüfung bei jeder GPS-Aktualisierung
    LaunchedEffect(currentLocation, isCameraAlertEnabled) {
        val loc = currentLocation ?: return@LaunchedEffect
        if (isCameraAlertEnabled) {
            val alert = cameraService.findCameraAhead(loc, maxDistanceMeters = 700.0)
            if (alert != null) {
                val (cam, dist) = alert
                currentAlertCamera = cam
                currentAlertDistance = dist

                if (cameraService.shouldTriggerVoiceAlert(cam.id)) {
                    val limitText = if (cam.maxSpeed != null) " bei ${cam.maxSpeed} km/h" else ""
                    val distText = if (dist >= 100) "${(dist / 50).roundToInt() * 50} Metern" else "Kürze"
                    voiceNavigator.speak("Achtung, Geschwindigkeitskontrolle in $distText$limitText")
                }
            } else {
                currentAlertCamera = null
            }
        } else {
            currentAlertCamera = null
        }
    }

    // Entdecker-Modus: Sehenswürdigkeiten & Wissenswertes in der Umgebung suchen
    LaunchedEffect(currentLocation, discoveryService.isEnabled) {
        val loc = currentLocation ?: return@LaunchedEffect
        if (!discoveryService.isEnabled) {
            currentDiscoveryItem = null
            return@LaunchedEffect
        }
        val sight = discoveryService.checkNearbySights(loc.latitude, loc.longitude)
        if (sight != null) {
            currentDiscoveryItem = sight
            if (discoveryService.isAudioEnabled && !voiceNavigator.isMuted) {
                voiceNavigator.speak("Wissenswertes in der Nähe: ${sight.title}. ${sight.extract}")
            }
        }
    }

    // Autobahn Live-Stauprüfung bei GPS-Aktualisierung
    LaunchedEffect(currentLocation, activeRoute, isTrafficAlertEnabled) {
        val loc = currentLocation ?: return@LaunchedEffect
        if (!isTrafficAlertEnabled) {
            currentTrafficIncident = null
            return@LaunchedEffect
        }
        val alert = trafficService.findIncidentAhead(loc, maxDistanceMeters = 15000.0)
        if (alert != null) {
            val (incident, dist) = alert
            currentTrafficIncident = incident
            currentTrafficDistance = dist

            if (trafficService.shouldTriggerVoiceAlert(incident.id)) {
                val distText = if (dist >= 1000) "${(dist / 1000.0).roundToInt()} Kilometern" else "${(dist / 100).roundToInt() * 100} Metern"
                val typeName = when (incident.type) {
                    IncidentType.CLOSURE -> "Sperrung"
                    IncidentType.ACCIDENT -> "Unfall"
                    IncidentType.TRAFFIC_JAM -> "Stau"
                    IncidentType.ROADWORKS -> "Baustelle"
                    IncidentType.HAZARD -> "Gefahr"
                }
                voiceNavigator.speak("Achtung, $typeName auf der ${incident.road} in $distText")
            }
        } else {
            currentTrafficIncident = null
        }
    }

    // Tempolimit & Trip-Logger Aktualisierung bei GPS-Änderung
    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
        speedLimitService.checkSpeedLimit(loc.latitude, loc.longitude)
        if (tripLogger.isTracking) {
            tripLogger.recordLocation(loc.speedKmh, loc.latitude, loc.longitude)
        }
    }

    // Turn-by-Turn Navigation & Sprachansagen während der Fahrt
    LaunchedEffect(currentLocation, activeRoute) {
        val loc = currentLocation ?: return@LaunchedEffect
        val route = activeRoute ?: return@LaunchedEffect
        if (route.steps.isEmpty() || currentStepIndex >= route.steps.size) return@LaunchedEffect

        val currentStep = route.steps[currentStepIndex]
        val dist = calculateDistanceMeters(
            loc.latitude, loc.longitude,
            currentStep.location.latitude, currentStep.location.longitude
        )
        distanceToNextStep = dist
        dev.uwiii.nav.state.NavigationState.updateStep(currentStep, dist)

        // Nächster Schritt erreicht?
        if (dist < 35.0 && currentStepIndex < route.steps.size - 1) {
            currentStepIndex++
            val nextStep = route.steps[currentStepIndex]
            dev.uwiii.nav.state.NavigationState.updateStep(nextStep, nextStep.distanceMeters)
            voiceNavigator.speak(nextStep.instruction)
        } else if (dist in 150.0..300.0) {
            val roundedDist = ((dist / 50).roundToInt() * 50).coerceAtLeast(50)
            val laneHint = currentStep.getLaneVoiceHint()
            val speechText = if (laneHint != null) {
                "In $roundedDist Metern ${currentStep.instruction.lowercase()}. $laneHint."
            } else {
                "In $roundedDist Metern ${currentStep.instruction.lowercase()}"
            }
            voiceNavigator.speak(speechText)
        } else if (dist < 40.0 && currentStepIndex == route.steps.size - 1) {
            voiceNavigator.speak("Sie haben Ihr Ziel erreicht!")
            val finished = tripLogger.finishTrip()
            if (finished != null) {
                completedTripRecord = finished
            }
        }
    }

    // Standort-Komponente aktivieren & 3D-Chevron konfigurieren
    val activateLocationComponent: (android.content.Context, MapLibreMap, Style) -> Unit = { c, m, s ->
        try {
            val hasFine = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val lc = m.locationComponent
                val opts = LocationComponentActivationOptions.builder(c, s)
                    .useDefaultLocationEngine(false)
                    .build()
                lc.activateLocationComponent(opts)
                lc.isLocationComponentEnabled = true
                lc.renderMode = RenderMode.GPS
                lc.cameraMode = CameraMode.NONE

                val metrics = c.resources.displayMetrics
                val bottomPadding = when (perspective) {
                    CameraPerspective.VIEW_2D -> 0
                    CameraPerspective.VIEW_3D -> (metrics.heightPixels * 0.30).toInt()
                    CameraPerspective.VIEW_COCKPIT -> (metrics.heightPixels * 0.44).toInt()
                }
                m.setPadding(0, 0, 0, bottomPadding)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Hilfsfunktion: Alternativrouten auf der Karte zeichnen
    fun redrawCandidateRoutes(
        map: MapLibreMap,
        routes: List<RouteData>,
        selectedIndex: Int,
        destination: LatLng,
        name: String
    ) {
        activePolylineCasing?.let { map.removePolyline(it) }
        activePolylineCore?.let { map.removePolyline(it) }
        alternativePolylines.forEach { map.removePolyline(it) }
        destinationMarker?.let { map.removeMarker(it) }

        val newAlternatives = ArrayList<Polyline>()

        // 1. Zuerst unselektierte Routen als dezent grau-blaue Bänder zeichnen
        routes.forEachIndexed { idx, r ->
            if (idx != selectedIndex) {
                val poly = map.addPolyline(
                    PolylineOptions()
                        .addAll(r.points)
                        .color(android.graphics.Color.parseColor("#778899"))
                        .width(6f)
                )
                newAlternatives.add(poly)
            }
        }
        alternativePolylines = newAlternatives

        // 2. Ausgewählte Route mit 3D-Cyanband hervorheben
        val selected = routes[selectedIndex]
        activePolylineCasing = map.addPolyline(
            PolylineOptions()
                .addAll(selected.points)
                .color(android.graphics.Color.parseColor("#003344"))
                .width(13.5f)
        )
        activePolylineCore = map.addPolyline(
            PolylineOptions()
                .addAll(selected.points)
                .color(android.graphics.Color.parseColor("#00E5FF"))
                .width(7.5f)
        )
        destinationMarker = map.addMarker(
            MarkerOptions()
                .position(destination)
                .title(name)
        )
    }

    // Hilfsfunktion: Aktive Einzelroute zeichnen
    fun redrawActiveRoute(
        map: MapLibreMap,
        route: RouteData,
        destination: LatLng,
        name: String
    ) {
        activePolylineCasing?.let { map.removePolyline(it) }
        activePolylineCore?.let { map.removePolyline(it) }
        alternativePolylines.forEach { map.removePolyline(it) }
        destinationMarker?.let { map.removeMarker(it) }
        alternativePolylines = emptyList()

        activePolylineCasing = map.addPolyline(
            PolylineOptions()
                .addAll(route.points)
                .color(android.graphics.Color.parseColor("#003344"))
                .width(13.5f)
        )
        activePolylineCore = map.addPolyline(
            PolylineOptions()
                .addAll(route.points)
                .color(android.graphics.Color.parseColor("#00E5FF"))
                .width(7.5f)
        )
        destinationMarker = map.addMarker(
            MarkerOptions()
                .position(destination)
                .title(name)
        )
    }

    // Hilfsfunktion: Aktive Navigation mit ausgewählter Route starten
    fun startActiveNavigation(route: RouteData, destination: LatLng, name: String) {
        val loc = currentLocation ?: return
        val map = mapInstance ?: return

        activeRoute = route
        currentStepIndex = 0
        followUserLocation = true
        deadReckoningPointIndex = 0
        candidateRoutes = emptyList()

        redrawActiveRoute(map, route, destination, name)

        tripLogger.startTrip(name, loc.latitude, loc.longitude)
        dev.uwiii.nav.state.NavigationState.setRoute(route, name)
        favoritesManager.addRecent(name, "", destination)
        scope.launch { cameraService.loadCamerasForRoute(route.points) }
        scope.launch { trafficService.loadTrafficForRoute(route) }
        voiceNavigator.speak("Navigation gestartet. ${route.summary}. ${route.distanceKm}, etwa ${route.durationMin}. Gute Fahrt!")
    }

    // Hilfsfunktion: Route(n) berechnen & Auswahl oder Sofortstart
    fun startNavigation(destination: LatLng, name: String) {
        val loc = currentLocation ?: return
        val map = mapInstance ?: return

        destinationPendingTarget = destination
        destinationPendingName = name

        scope.launch {
            isCalculatingRoute = true
            val start = LatLng(loc.latitude, loc.longitude)
            val routes = routingService.calculateRoutes(start, destination)
            isCalculatingRoute = false

            if (routes.isNotEmpty()) {
                if (routes.size > 1) {
                    // Alternativrouten zur Auswahl anbieten
                    candidateRoutes = routes
                    selectedRouteIndex = 0
                    redrawCandidateRoutes(map, routes, 0, destination, name)

                    // Kamera auf Gesamtrouten-Übersicht zoomen
                    try {
                        val boundsBuilder = LatLngBounds.Builder()
                        boundsBuilder.include(start)
                        boundsBuilder.include(destination)
                        routes.first().points.forEach { boundsBuilder.include(it) }
                        val bounds = boundsBuilder.build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 130), 800)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    voiceNavigator.speak("${routes.size} Routen nach $name gefunden. Bitte wählen Sie Ihre gewünschte Strecke.")
                } else {
                    // Einzelne Route sofort starten
                    startActiveNavigation(routes[0], destination, name)
                }
            }
        }
    }

    // Hilfsfunktion: Navigation beenden
    fun cancelNavigation() {
        val map = mapInstance
        activePolylineCasing?.let { map?.removePolyline(it) }
        activePolylineCore?.let { map?.removePolyline(it) }
        alternativePolylines.forEach { map?.removePolyline(it) }
        destinationMarker?.let { map?.removeMarker(it) }
        activePolylineCasing = null
        activePolylineCore = null
        alternativePolylines = emptyList()
        destinationMarker = null
        activeRoute = null
        candidateRoutes = emptyList()
        destinationPendingTarget = null
        currentStepIndex = 0
        deadReckoningPointIndex = 0
        isTunnelMode = false
        currentAlertCamera = null
        currentTrafficIncident = null
        currentDiscoveryItem = null
        tripLogger.cancelTrip()
        dev.uwiii.nav.state.NavigationState.clearRoute()
        voiceNavigator.speak("Navigation beendet.")
    }

    // Tag-/Nacht-Kartenstil dynamisch umschalten (Solar/Tunnel/System)
    LaunchedEffect(targetStyleUrl, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        if (map.style?.uri != targetStyleUrl) {
            map.setStyle(Style.Builder().fromUri(targetStyleUrl)) { style ->
                activateLocationComponent(context, map, style)
                destinationPendingTarget?.let { dest ->
                    if (activeRoute != null) {
                        redrawActiveRoute(map, activeRoute!!, dest, destinationPendingName)
                    } else if (candidateRoutes.isNotEmpty()) {
                        redrawCandidateRoutes(map, candidateRoutes, selectedRouteIndex, dest, destinationPendingName)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Die Kartenansicht (MapLibre OpenStreetMap)
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    mapViewInstance = this
                    getMapAsync { map ->
                        mapInstance = map
                        map.setMaxPitchPreference(82.0)
                        map.setMinPitchPreference(0.0)

                        // Dynamischer Vektor-Style (Liberty für Tag, Dark für Nacht/Tunnel)
                        map.setStyle(Style.Builder().fromUri(targetStyleUrl)) { style ->
                            // Nutzerinteraktion: Wenn der Nutzer selbst wischt/zoomt, nicht sofort zurückspringen
                            map.addOnCameraMoveStartedListener { reason ->
                                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                    followUserLocation = false
                                }
                            }

                            // Langklick auf beliebigen Punkt auf der Karte startet Navigation dorthin!
                            map.addOnMapLongClickListener { point ->
                                startNavigation(point, "Gewählter Ort")
                                true
                            }

                            // Standort-Komponente auf der Karte aktivieren
                            activateLocationComponent(ctx, map, style)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Wiederverwendbares 3-Punkte-Menü für Suchleiste und untere Navigationsleiste
        val renderOverflowMenu: @Composable () -> Unit = {
            Box {
                IconButton(
                    onClick = { showOverflowMenu = !showOverflowMenu }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menü",
                        tint = TextWhite
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (voiceNavigator.isMuted) "Sprachansagen: Stumm" else "Sprachansagen: Aktiv",
                                color = if (voiceNavigator.isMuted) SpeedYellow else SpeedGreen
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (voiceNavigator.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = if (voiceNavigator.isMuted) SpeedYellow else SpeedGreen
                            )
                        },
                        onClick = {
                            voiceNavigator.toggleMute()
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isCameraAlertEnabled) "Gefahrenstellen: An" else "Gefahrenstellen: Aus",
                                color = if (isCameraAlertEnabled) SpeedRed else TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (isCameraAlertEnabled) SpeedRed else TextMuted
                            )
                        },
                        onClick = {
                            isCameraAlertEnabled = !isCameraAlertEnabled
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isTrafficAlertEnabled) "Live-Verkehr & Stau: An" else "Live-Verkehr & Stau: Aus",
                                color = if (isTrafficAlertEnabled) SpeedGreen else TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Traffic,
                                contentDescription = null,
                                tint = if (isTrafficAlertEnabled) SpeedGreen else TextMuted
                            )
                        },
                        onClick = {
                            isTrafficAlertEnabled = !isTrafficAlertEnabled
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (discoveryService.isEnabled) "Entdecker-Modus: An" else "Entdecker-Modus: Aus",
                                color = if (discoveryService.isEnabled) CyanPrimary else TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = if (discoveryService.isEnabled) CyanPrimary else TextMuted
                            )
                        },
                        trailingIcon = {
                            Text(
                                text = "${discoveryService.displayDurationSeconds}s",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        },
                        onClick = {
                            showDiscoveryDialog = true
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Stimme auswählen",
                                color = TextWhite
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = CyanPrimary
                            )
                        },
                        onClick = {
                            showVoiceDialog = true
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Offline-Karten", color = TextWhite) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = CyanPrimary
                            )
                        },
                        onClick = {
                            showOfflineDialog = true
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Spritpreis-Radar", color = TextWhite) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = CyanPrimary
                            )
                        },
                        onClick = {
                            showGasPriceDialog = true
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Fahrtenbuch", color = TextWhite) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = CyanPrimary
                            )
                        },
                        trailingIcon = {
                            if (tripLogger.tripHistory.isNotEmpty()) {
                                Text(
                                    text = "${tripLogger.tripHistory.size}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        onClick = {
                            showTripHistoryDialog = true
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            val themeLabel = when (mapThemeMode) {
                                "liberty" -> "Hell (Klar & Kontrastreich)"
                                "dark" -> "Dunkel (Nachtmodus)"
                                else -> "Automatisch (Tag/Nacht)"
                            }
                            Text("Kartenstil: $themeLabel", color = TextWhite)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = CyanPrimary
                            )
                        },
                        onClick = {
                            val nextMode = when (mapThemeMode) {
                                "liberty" -> "auto"
                                "auto" -> "dark"
                                else -> "liberty"
                            }
                            mapThemeMode = nextMode
                            prefs.edit().putString("map_theme_mode", nextMode).apply()
                            showOverflowMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nach Updates suchen", color = TextWhite) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = CyanPrimary
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            scope.launch {
                                val info = updateChecker.checkForUpdates(currentVersionName)
                                updateInfo = info
                                showUpdateDialog = true
                            }
                        }
                    )

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "uWiiiNav v$currentVersionName",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Kopfbereich: Entweder Navigations-Hinweis ODER Suchleiste
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (activeRoute != null && activeRoute!!.steps.isNotEmpty()) {
                val step = activeRoute!!.steps.getOrNull(currentStepIndex)
                NavigationTopBanner(
                    step = step,
                    distanceToStepMeters = distanceToNextStep
                )
            } else if (candidateRoutes.isEmpty()) {
                SearchBarOverlay(
                    userLocation = currentLocation?.let { LatLng(it.latitude, it.longitude) },
                    favoritesManager = favoritesManager,
                    onSelectDestination = { dest, title ->
                        startNavigation(dest, title)
                    },
                    onOpenGasRadar = {
                        showGasPriceDialog = true
                    },
                    trailingActions = {
                        renderOverflowMenu()
                    }
                )
            }

            // Tunnel-Modus & Koppelnavigation Warnbanner
            TunnelAlertBanner(
                isTunnelMode = isTunnelMode,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (isCalculatingRoute) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = CyanPrimary,
                    trackColor = DarkSurface80
                )
            }

            // Gefahrenstellen- & Blitzer-Warnbanner
            SpeedCameraAlertBanner(
                camera = currentAlertCamera,
                distanceMeters = currentAlertDistance,
                currentSpeedKmh = currentLocation?.speedKmh ?: 0f,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Live-Verkehr & Autobahn-Stauwarnung
            TrafficAlertBanner(
                incident = currentTrafficIncident,
                distanceMeters = currentTrafficDistance,
                onDismiss = { currentTrafficIncident = null },
                modifier = Modifier.padding(top = 8.dp)
            )

            // Entdecker-Modus: Kultur & Sehenswürdigkeiten-Einblendung
            DiscoveryCardBanner(
                item = currentDiscoveryItem,
                displayDurationSeconds = discoveryService.displayDurationSeconds,
                onDismiss = { currentDiscoveryItem = null },
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 3. Cockpit-Steuerelemente am unteren Bildschirmrand
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dynamische Restfahrzeit & Ankunftszeit berechnen
            val remainingTime = remember(activeRoute, currentStepIndex, distanceToNextStep) {
                val route = activeRoute ?: return@remember null
                if (route.steps.isEmpty() || currentStepIndex >= route.steps.size) {
                    route.durationMin
                } else {
                    val remainingStepsSec = route.steps.drop(currentStepIndex + 1).sumOf { it.durationSeconds }
                    val currentStep = route.steps[currentStepIndex]
                    val ratio = (distanceToNextStep / currentStep.distanceMeters.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                    val currentStepSec = currentStep.durationSeconds * ratio
                    val totalSec = remainingStepsSec + currentStepSec
                    val mins = (totalSec / 60.0).roundToInt().coerceAtLeast(1)
                    if (mins >= 60) {
                        val hours = mins / 60
                        val remMins = mins % 60
                        "${hours}h ${remMins}m"
                    } else {
                        "$mins Min."
                    }
                }
            }

            val etaTime = remember(activeRoute, currentStepIndex, distanceToNextStep) {
                val route = activeRoute ?: return@remember null
                val now = java.util.Calendar.getInstance()
                val mins = if (currentStepIndex < route.steps.size) {
                    val remainingStepsSec = route.steps.drop(currentStepIndex + 1).sumOf { it.durationSeconds }
                    val currentStep = route.steps[currentStepIndex]
                    val ratio = (distanceToNextStep / currentStep.distanceMeters.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                    ((remainingStepsSec + currentStep.durationSeconds * ratio) / 60.0).roundToInt().coerceAtLeast(1)
                } else {
                    (route.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
                }
                now.add(java.util.Calendar.MINUTE, mins)
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMAN)
                sdf.format(now.time)
            }

            // Tacho und Schnellwahltasten
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Tacho links unten: Nur anzeigen, wenn KEINE Navigation aktiv ist!
                // Bei aktiver Navigation wandert der Tacho direkt in die untere Navigationsleiste.
                if (activeRoute == null) {
                    SpeedometerOverlay(
                        speedKmh = currentLocation?.speedKmh ?: 0f,
                        speedLimit = speedLimitService.currentSpeedLimit
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Schnellwahltasten rechts unten (3D/2D umschalten & Zentrieren)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Perspektive umschalten: Fahrersicht -> 3D -> 2D -> Fahrersicht
                    FloatingActionButton(
                        onClick = {
                            val nextPerspective = when (perspective) {
                                CameraPerspective.VIEW_COCKPIT -> CameraPerspective.VIEW_3D
                                CameraPerspective.VIEW_3D -> CameraPerspective.VIEW_2D
                                CameraPerspective.VIEW_2D -> CameraPerspective.VIEW_COCKPIT
                            }
                            perspective = nextPerspective
                            followUserLocation = true

                            val metrics = context.resources.displayMetrics
                            val bottomPadding = when (nextPerspective) {
                                CameraPerspective.VIEW_2D -> 0
                                CameraPerspective.VIEW_3D -> (metrics.heightPixels * 0.30).toInt()
                                CameraPerspective.VIEW_COCKPIT -> (metrics.heightPixels * 0.44).toInt()
                            }
                            mapInstance?.setPadding(0, 0, 0, bottomPadding)

                            currentLocation?.let { loc ->
                                val targetZoom = when (nextPerspective) {
                                    CameraPerspective.VIEW_2D -> 16.0
                                    CameraPerspective.VIEW_3D -> 17.2
                                    CameraPerspective.VIEW_COCKPIT -> 18.2
                                }
                                val targetTilt = when (nextPerspective) {
                                    CameraPerspective.VIEW_2D -> 0.0
                                    CameraPerspective.VIEW_3D -> 56.0
                                    CameraPerspective.VIEW_COCKPIT -> 76.0
                                }
                                val targetBearing = when (nextPerspective) {
                                    CameraPerspective.VIEW_2D -> 0.0
                                    else -> loc.bearing.toDouble()
                                }
                                val cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(loc.latitude, loc.longitude))
                                    .zoom(targetZoom)
                                    .tilt(targetTilt)
                                    .bearing(targetBearing)
                                    .build()
                                mapInstance?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 600)
                            }

                            val voiceHint = when (nextPerspective) {
                                CameraPerspective.VIEW_COCKPIT -> "Cockpit Fahrersicht aktiviert"
                                CameraPerspective.VIEW_3D -> "3D Ansicht aktiviert"
                                CameraPerspective.VIEW_2D -> "2D Draufsicht aktiviert"
                            }
                            voiceNavigator.speak(voiceHint)
                        },
                        containerColor = DarkSurface80,
                        contentColor = when (perspective) {
                            CameraPerspective.VIEW_COCKPIT -> SpeedGreen
                            CameraPerspective.VIEW_3D -> CyanPrimary
                            CameraPerspective.VIEW_2D -> TextWhite
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = when (perspective) {
                                CameraPerspective.VIEW_COCKPIT -> Icons.Default.DirectionsCar
                                CameraPerspective.VIEW_3D -> Icons.Default.Explore
                                CameraPerspective.VIEW_2D -> Icons.Default.Navigation
                            },
                            contentDescription = "Perspektive umschalten"
                        )
                    }

                    // Auf Standort zentrieren
                    FloatingActionButton(
                        onClick = {
                            followUserLocation = true
                            currentLocation?.let { loc ->
                                val metrics = context.resources.displayMetrics
                                val bottomPadding = when (perspective) {
                                    CameraPerspective.VIEW_2D -> 0
                                    CameraPerspective.VIEW_3D -> (metrics.heightPixels * 0.30).toInt()
                                    CameraPerspective.VIEW_COCKPIT -> (metrics.heightPixels * 0.44).toInt()
                                }
                                mapInstance?.setPadding(0, 0, 0, bottomPadding)
                                val targetZoom = when (perspective) {
                                    CameraPerspective.VIEW_2D -> if (activeRoute != null) 16.5 else 16.0
                                    CameraPerspective.VIEW_3D -> if (loc.speedKmh > 75f) 16.2 else 17.4
                                    CameraPerspective.VIEW_COCKPIT -> if (loc.speedKmh > 75f) 17.2 else 18.2
                                }
                                val targetTilt = when (perspective) {
                                    CameraPerspective.VIEW_2D -> 0.0
                                    CameraPerspective.VIEW_3D -> if (loc.speedKmh > 75f) 52.0 else 58.0
                                    CameraPerspective.VIEW_COCKPIT -> if (loc.speedKmh > 75f) 72.0 else 76.0
                                }
                                val targetBearing = when (perspective) {
                                    CameraPerspective.VIEW_2D -> 0.0
                                    else -> loc.bearing.toDouble()
                                }
                                val cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(loc.latitude, loc.longitude))
                                    .zoom(targetZoom)
                                    .tilt(targetTilt)
                                    .bearing(targetBearing)
                                    .build()
                                mapInstance?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 600)
                            }
                        },
                        containerColor = CyanPrimary,
                        contentColor = Color.Black,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Zentrieren"
                        )
                    }
                }
            }

            // Alternativrouten-Auswahlleiste (vor dem Start der Fahrt)
            AnimatedVisibility(
                visible = candidateRoutes.isNotEmpty() && activeRoute == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                RouteSelectionBar(
                    routes = candidateRoutes,
                    selectedIndex = selectedRouteIndex,
                    onSelectRoute = { idx ->
                        selectedRouteIndex = idx
                        destinationPendingTarget?.let { dest ->
                            mapInstance?.let { map ->
                                redrawCandidateRoutes(map, candidateRoutes, idx, dest, destinationPendingName)
                            }
                        }
                    },
                    onStartNavigation = {
                        val chosen = candidateRoutes.getOrNull(selectedRouteIndex) ?: candidateRoutes.first()
                        destinationPendingTarget?.let { dest ->
                            startActiveNavigation(chosen, dest, destinationPendingName)
                        }
                    },
                    onDismiss = {
                        cancelNavigation()
                    }
                )
            }

            // ETA-Leiste (wenn eine Route aktiv ist)
            AnimatedVisibility(
                visible = activeRoute != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeRoute?.let { route ->
                    NavigationBottomBar(
                        routeData = route,
                        currentSpeedKmh = if (isTunnelMode) lastKnownSpeedKmh else (currentLocation?.speedKmh ?: 0f),
                        speedLimit = speedLimitService.currentSpeedLimit,
                        remainingTime = remainingTime,
                        etaTime = etaTime,
                        isTunnelMode = isTunnelMode,
                        overflowMenu = { renderOverflowMenu() },
                        onCancelRoute = { cancelNavigation() }
                    )
                }
            }
        }

        // 4. Update-Dialog (wenn auf GitHub ein Release existiert)
        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = {
                    Text(text = if (updateInfo?.hasUpdate == true) "Neues Update verfügbar!" else "App ist aktuell")
                },
                text = {
                    if (updateInfo?.hasUpdate == true) {
                        Column {
                            Text("Version ${updateInfo?.latestVersion} steht auf GitHub bereit.")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Änderungen:\n${updateInfo?.releaseNotes}")
                        }
                    } else {
                        Text("Du verwendest die aktuellste Version von uWiiiNav (v$currentVersionName).")
                    }
                },
                confirmButton = {
                    if (updateInfo?.hasUpdate == true && updateInfo?.apkDownloadUrl != null) {
                        Button(
                            onClick = {
                                updateInfo?.apkDownloadUrl?.let { updateChecker.openBrowserDownload(it) }
                                showUpdateDialog = false
                            }
                        ) {
                            Text("Jetzt herunterladen")
                        }
                    } else {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("OK")
                        }
                    }
                },
                dismissButton = {
                    if (updateInfo?.hasUpdate == true) {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Später")
                        }
                    }
                }
            )
        }

        // 5. Offline-Karten-Manager Dialog
        if (showOfflineDialog) {
            OfflineMapsDialog(
                offlineManager = offlineMapManager,
                currentLocation = currentLocation?.let { LatLng(it.latitude, it.longitude) },
                onDismiss = { showOfflineDialog = false }
            )
        }

        // 6. Stimmen- und Sprachausgabe-Dialog
        if (showVoiceDialog) {
            VoiceSettingsDialog(
                voiceNavigator = voiceNavigator,
                onDismiss = { showVoiceDialog = false }
            )
        }

        // 7. Entdecker-Modus Einstellungen & Vorschau
        if (showDiscoveryDialog) {
            DiscoverySettingsDialog(
                discoveryService = discoveryService,
                onTestItem = { item ->
                    currentDiscoveryItem = item
                    if (discoveryService.isAudioEnabled && !voiceNavigator.isMuted) {
                        voiceNavigator.speak("Wissenswertes in der Nähe: ${item.title}. ${item.extract}")
                    }
                },
                onDismiss = { showDiscoveryDialog = false }
            )
        }

        // 8. Trip-Zusammenfassung nach Zielankunft
        completedTripRecord?.let { trip ->
            TripSummaryDialog(
                trip = trip,
                onDismiss = { completedTripRecord = null }
            )
        }

        // 9. Fahrtenbuch-Historie Dialog
        if (showTripHistoryDialog) {
            TripHistoryDialog(
                tripLogger = tripLogger,
                onDismiss = { showTripHistoryDialog = false }
            )
        }

        // 10. Spritpreis-Radar Dialog
        if (showGasPriceDialog) {
            GasPriceDialog(
                fuelService = gasPriceService,
                userLocation = currentLocation?.let { LatLng(it.latitude, it.longitude) },
                onSelectStation = { loc, brand ->
                    startNavigation(loc, brand)
                },
                onDismiss = { showGasPriceDialog = false }
            )
        }
    }
}

// Haversine-Distanzberechnung in Metern
private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Erdradius in Metern
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

// Dead Reckoning: Position entlang der Route vorausberechnen
private fun advanceAlongRoute(
    points: List<LatLng>,
    startIndex: Int,
    distanceMeters: Double
): Pair<LatLng, Int> {
    if (points.isEmpty()) return Pair(LatLng(0.0, 0.0), 0)
    var remaining = distanceMeters
    var idx = startIndex.coerceIn(0, points.size - 1)

    while (idx < points.size - 1 && remaining > 0) {
        val p1 = points[idx]
        val p2 = points[idx + 1]
        val d = calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        if (remaining <= d) {
            val fraction = if (d > 0) remaining / d else 0.0
            val lat = p1.latitude + fraction * (p2.latitude - p1.latitude)
            val lng = p1.longitude + fraction * (p2.longitude - p1.longitude)
            return Pair(LatLng(lat, lng), idx)
        } else {
            remaining -= d
            idx++
        }
    }
    return Pair(points.last(), points.size - 1)
}

// Richtungswinkel (Bearing) zwischen zwei Koordinaten berechnen
private fun calculateBearing(from: LatLng, to: LatLng): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lon1 = Math.toRadians(from.longitude)
    val lat2 = Math.toRadians(to.latitude)
    val lon2 = Math.toRadians(to.longitude)
    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    val brng = Math.toDegrees(Math.atan2(y, x))
    return ((brng + 360) % 360).toFloat()
}
