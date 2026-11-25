package dev.uwiii.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.uwiii.nav.location.LocationClient
import dev.uwiii.nav.location.NavLocation
import dev.uwiii.nav.ui.screens.NavigationScreen
import dev.uwiii.nav.ui.theme.CyanPrimary
import dev.uwiii.nav.ui.theme.DarkBackground
import dev.uwiii.nav.ui.theme.TextMuted
import dev.uwiii.nav.ui.theme.TextWhite
import dev.uwiii.nav.ui.theme.UWiiiNavTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verhindern, dass der Bildschirm während der Navigation im Auto ausgeht
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            UWiiiNavTheme {
                val context = LocalContext.current
                val locationClient = remember { LocationClient(context) }

                var hasLocationPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                }

                var currentLocation by remember { mutableStateOf<NavLocation?>(null) }

                LaunchedEffect(hasLocationPermission) {
                    if (hasLocationPermission) {
                        locationClient.getLocationUpdates().collect { loc ->
                            currentLocation = loc
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    if (hasLocationPermission) {
                        NavigationScreen(currentLocation = currentLocation)
                    } else {
                        PermissionScreen(
                            onRequestPermission = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Standortberechtigung erforderlich",
                style = MaterialTheme.typography.headlineSmall,
                color = TextWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "uWiiiNav benötigt Zugriff auf deinen GPS-Standort, um deine Position auf der OpenStreetMap-Karte anzuzeigen und deine Geschwindigkeit zu berechnen.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
            ) {
                Text(
                    text = "Berechtigung erteilen",
                    color = DarkBackground
                )
            }
        }
    }
}
