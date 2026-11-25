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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SystemUpdate
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
import dev.uwiii.nav.location.NavLocation
import dev.uwiii.nav.offline.OfflineMapManager
import dev.uwiii.nav.routing.RouteData
import dev.uwiii.nav.routing.RoutingService
import dev.uwiii.nav.ui.components.NavigationBottomBar
import dev.uwiii.nav.ui.components.NavigationTopBanner
import dev.uwiii.nav.ui.components.OfflineMapsDialog
import dev.uwiii.nav.ui.components.SearchBarOverlay
import dev.uwiii.nav.ui.components.SpeedCameraAlertBanner
import dev.uwiii.nav.ui.components.SpeedometerOverlay
import dev.uwiii.nav.ui.theme.CyanPrimary
import dev.uwiii.nav.ui.theme.DarkSurface
import dev.uwiii.nav.ui.theme.DarkSurface80
import dev.uwiii.nav.ui.theme.SpeedGreen
import dev.uwiii.nav.ui.theme.SpeedRed
import dev.uwiii.nav.ui.theme.TextMuted
import dev.uwiii.nav.ui.theme.TextWhite
import dev.uwiii.nav.updater.GitHubUpdateChecker
import dev.uwiii.nav.updater.UpdateInfo
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.*

@SuppressLint("MissingPermission")
@Composable
fun NavigationScreen(
    currentLocation: NavLocation?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var is3DMode by remember { mutableStateOf(true) }
    var followUserLocation by remember { mutableStateOf(true) }

    // Routing & Navigation State
    val routingService = remember { RoutingService() }
    val voiceNavigator = remember { VoiceNavigator(context) }
    val offlineMapManager = remember { OfflineMapManager(context) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var activeRoute by remember { mutableStateOf<RouteData?>(null) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var distanceToNextStep by remember { mutableStateOf(0.0) }
    var activePolyline by remember { mutableStateOf<Polyline?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Gefahrenstellen- & Blitzerwarner State
    val cameraService = remember { SpeedCameraService() }
    var isCameraAlertEnabled by remember { mutableStateOf(true) }
    var currentAlertCamera by remember { mutableStateOf<SpeedCamera?>(null) }
    var currentAlertDistance by remember { mutableStateOf(0.0) }

    // GitHub Update Checker State
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateChecker = remember { GitHubUpdateChecker(context) }

    // Lifecycle Cleanup für Audio & Map
    DisposableEffect(lifecycleOwner, mapViewInstance) {
        val mapView = mapViewInstance ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    voiceNavigator.shutdown()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceNavigator.shutdown()
        }
    }

    // Beim Start einmal im Hintergrund nach Updates schauen
    LaunchedEffect(Unit) {
        val info = updateChecker.checkForUpdates("1.5.2")
        if (info.hasUpdate) {
            updateInfo = info
            showUpdateDialog = true
        }
    }

    // Kamera aktualisieren, wenn sich der GPS-Standort ändert
    LaunchedEffect(currentLocation, followUserLocation, is3DMode) {
        val loc = currentLocation ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        if (followUserLocation) {
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(loc.latitude, loc.longitude))
                .zoom(if (activeRoute != null) 17.5 else 16.5)
                .tilt(if (is3DMode) 55.0 else 0.0) // 3D-Kippung für Cockpit-Gefühl
                .bearing(loc.bearing.toDouble())   // Karte dreht sich in Fahrtrichtung
                .build()

            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 700)
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
                    voiceNavigator.speak("Achtung, Geschwindigkeitskontrolle in $distText$limitText", force = true)
                }
            } else {
                currentAlertCamera = null
            }
        } else {
            currentAlertCamera = null
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
            voiceNavigator.speak(nextStep.instruction, force = true)
        } else if (dist in 150.0..300.0) {
            val roundedDist = ((dist / 50).roundToInt() * 50).coerceAtLeast(50)
            voiceNavigator.speak("In $roundedDist Metern ${currentStep.instruction.lowercase()}")
        } else if (dist < 40.0 && currentStepIndex == route.steps.size - 1) {
            voiceNavigator.speak("Sie haben Ihr Ziel erreicht!")
        }
    }

    // Hilfsfunktion: Navigation zu Ziel starten
    fun startNavigation(destination: LatLng, name: String) {
        val loc = currentLocation ?: return
        val map = mapInstance ?: return
        scope.launch {
            isCalculatingRoute = true
            val start = LatLng(loc.latitude, loc.longitude)
            val route = routingService.calculateRoute(start, destination)
            isCalculatingRoute = false

            if (route != null) {
                // Altes Zeichnen entfernen
                activePolyline?.let { map.removePolyline(it) }
                destinationMarker?.let { map.removeMarker(it) }

                // Neue Route auf Karte zeichnen (Cyan Leuchtlinie)
                val polyline = map.addPolyline(
                    PolylineOptions()
                        .addAll(route.points)
                        .color(android.graphics.Color.parseColor("#00E5FF"))
                        .width(7.5f)
                )
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(destination)
                        .title(name)
                )

                activePolyline = polyline
                destinationMarker = marker
                activeRoute = route
                currentStepIndex = 0
                followUserLocation = true

                dev.uwiii.nav.state.NavigationState.setRoute(route, name)
                scope.launch { cameraService.loadCamerasForRoute(route.points) }
                voiceNavigator.speak("Route nach $name berechnet. ${route.distanceKm}, Fahrzeit etwa ${route.durationMin}. Gute Fahrt!")
            }
        }
    }

    // Hilfsfunktion: Navigation beenden
    fun cancelNavigation() {
        val map = mapInstance
        activePolyline?.let { map?.removePolyline(it) }
        destinationMarker?.let { map?.removeMarker(it) }
        activePolyline = null
        destinationMarker = null
        activeRoute = null
        currentStepIndex = 0
        dev.uwiii.nav.state.NavigationState.clearRoute()
        voiceNavigator.speak("Navigation beendet.")
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

                        // Weltweiter OpenStreetMap Vektor-Style mit vollen Straßendetails
                        val styleUrl = "https://tiles.openfreemap.org/styles/liberty"
                        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
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
                            try {
                                val hasFine = ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasFine || hasCoarse) {
                                    val locationComponent = map.locationComponent
                                    val options = LocationComponentActivationOptions.builder(ctx, style)
                                        .useDefaultLocationEngine(false)
                                        .build()
                                    locationComponent.activateLocationComponent(options)
                                    locationComponent.isLocationComponentEnabled = true
                                    locationComponent.renderMode = RenderMode.COMPASS
                                    locationComponent.cameraMode = CameraMode.TRACKING_COMPASS
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

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
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SearchBarOverlay(
                            onSelectDestination = { dest, title ->
                                startNavigation(dest, title)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = !showOverflowMenu },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkSurface80)
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
                                text = { Text("Sprachansage testen", color = TextWhite) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = SpeedGreen
                                    )
                                },
                                onClick = {
                                    voiceNavigator.testVoice()
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
                                        val info = updateChecker.checkForUpdates("1.5.2")
                                        updateInfo = info
                                        showUpdateDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

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
            // Tacho und Schnellwahltasten
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Tacho (km/h) links unten
                SpeedometerOverlay(
                    speedKmh = currentLocation?.speedKmh ?: 0f
                )

                // Schnellwahltasten rechts unten (3D/2D umschalten & Zentrieren)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // 2D / 3D Perspektive umschalten
                    FloatingActionButton(
                        onClick = { is3DMode = !is3DMode },
                        containerColor = DarkSurface80,
                        contentColor = if (is3DMode) CyanPrimary else TextWhite,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "3D Ansicht umschalten"
                        )
                    }

                    // Auf Standort zentrieren
                    FloatingActionButton(
                        onClick = {
                            followUserLocation = true
                            currentLocation?.let { loc ->
                                val cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(loc.latitude, loc.longitude))
                                    .zoom(if (activeRoute != null) 17.5 else 16.5)
                                    .tilt(if (is3DMode) 55.0 else 0.0)
                                    .bearing(loc.bearing.toDouble())
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

            // ETA-Leiste (wenn eine Route aktiv ist)
            AnimatedVisibility(
                visible = activeRoute != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeRoute?.let { route ->
                    NavigationBottomBar(
                        routeData = route,
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
                        Text("Du verwendest die aktuellste Version von uWiiiNav (v1.0.0).")
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
