package dev.uwiii.nav.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.uwiii.nav.location.NavLocation
import dev.uwiii.nav.ui.components.SpeedometerOverlay
import dev.uwiii.nav.ui.theme.CyanPrimary
import dev.uwiii.nav.ui.theme.DarkSurface80
import dev.uwiii.nav.ui.theme.TextMuted
import dev.uwiii.nav.ui.theme.TextWhite
import dev.uwiii.nav.updater.GitHubUpdateChecker
import dev.uwiii.nav.updater.UpdateInfo
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    currentLocation: NavLocation?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var is3DMode by remember { mutableStateOf(true) }
    var followUserLocation by remember { mutableStateOf(true) }

    // GitHub Update Checker State
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateChecker = remember { GitHubUpdateChecker(context) }

    // Beim Start einmal im Hintergrund nach Updates schauen
    LaunchedEffect(Unit) {
        val info = updateChecker.checkForUpdates("1.0.0")
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
                .zoom(17.0)
                .tilt(if (is3DMode) 55.0 else 0.0) // 3D-Kippung für Cockpit-Gefühl
                .bearing(loc.bearing.toDouble())   // Karte dreht sich in Fahrtrichtung
                .build()

            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 900)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Die Kartenansicht (MapLibre OpenStreetMap)
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    getMapAsync { map ->
                        mapInstance = map

                        // 100% freier OpenStreetMap Vektor-Karten-Style
                        val styleUrl = "https://demotiles.maplibre.org/style.json"
                        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                            // Standort-Komponente auf der Karte aktivieren
                            val locationComponent = map.locationComponent
                            val options = LocationComponentActivationOptions.builder(ctx, style)
                                .useDefaultLocationEngine(false)
                                .build()
                            locationComponent.activateLocationComponent(options)
                            locationComponent.isLocationComponentEnabled = true
                            locationComponent.renderMode = RenderMode.COMPASS
                            locationComponent.cameraMode = CameraMode.TRACKING_COMPASS
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                // Lifecycle Events verwalten
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
            }
        )

        // 2. Kopfzeile: Statusanzeige & Update-Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface80)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "uWiiiNav",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Update-Button (GitHub-Symbol / Update)
            IconButton(
                onClick = {
                    scope.launch {
                        val info = updateChecker.checkForUpdates("1.0.0")
                        updateInfo = info
                        showUpdateDialog = true
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurface80)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Nach Updates suchen",
                    tint = if (updateInfo?.hasUpdate == true) CyanPrimary else TextMuted
                )
            }
        }

        // 3. Cockpit-Steuerelemente am unteren Bildschirmrand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
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
                        imageVector = Icons.Default.ThreeDRotation,
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
                                .zoom(17.0)
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
    }
}
