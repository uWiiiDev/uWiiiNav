package dev.uwiii.nav.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.uwiii.nav.fuel.FuelType
import dev.uwiii.nav.fuel.GasPriceService
import dev.uwiii.nav.fuel.GasStation
import dev.uwiii.nav.ui.theme.*
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.util.Locale

@Composable
fun GasPriceDialog(
    fuelService: GasPriceService,
    userLocation: LatLng?,
    onSelectStation: (LatLng, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(fuelService.preferredFuelType) }
    var stations by remember { mutableStateOf<List<GasStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showKeyDialog by remember { mutableStateOf(false) }

    fun refreshStations() {
        val loc = userLocation ?: return
        scope.launch {
            isLoading = true
            stations = fuelService.getNearbyStations(loc.latitude, loc.longitude, radiusKm = 7.0)
            isLoading = false
        }
    }

    LaunchedEffect(userLocation, fuelService.apiKey) {
        refreshStations()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (fuelService.isDemoKey) SpeedYellow.copy(alpha = 0.2f) else CyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = if (fuelService.isDemoKey) SpeedYellow else CyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Spritpreis-Radar",
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (fuelService.isDemoKey) "⚠️ Demo-Modus (Testdaten 1,009 €)" else "🟢 Live-Preise (MTS-K)",
                                color = if (fuelService.isDemoKey) SpeedYellow else SpeedGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Key-Button
                        IconButton(
                            onClick = { showKeyDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (fuelService.isDemoKey) SpeedYellow.copy(alpha = 0.2f) else DarkSurface80)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "API-Key konfigurieren",
                                tint = if (fuelService.isDemoKey) SpeedYellow else CyanPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Refresh-Button
                        IconButton(
                            onClick = { refreshStations() },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurface80)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Neu laden",
                                tint = TextWhite,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Schließen-Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkSurface80)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hinweis-Banner, wenn Demo-Key aktiv ist
                if (fuelService.isDemoKey) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpeedYellow.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpeedYellow.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = SpeedYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Demo-Schlüssel aktiv",
                                    color = SpeedYellow,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tankerkönig liefert im Demo-Modus feste 1,009 €. Eigenen kostenlosen Key eintragen für echte Preise.",
                                    color = TextWhite.copy(alpha = 0.85f),
                                    fontSize = 10.5.sp,
                                    lineHeight = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { showKeyDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SpeedYellow, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Key eingeben", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (fuelService.lastErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpeedRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpeedRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SpeedRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fehler: ${fuelService.lastErrorMessage}",
                                color = SpeedRed,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showKeyDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Prüfen", color = CyanPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Kraftstoff-Typ Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBackground)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        FuelType.DIESEL to "Diesel",
                        FuelType.E10 to "Super E10",
                        FuelType.E5 to "Super E5"
                    ).forEach { (type, label) ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CyanPrimary else Color.Transparent)
                                .clickable {
                                    selectedType = type
                                    fuelService.updatePreferredFuelType(type)
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanPrimary)
                    }
                } else {
                    // Sortiere Stationen nach dem Preis des gewählten Typs (günstigste zuerst)
                    val sortedStations = remember(stations, selectedType) {
                        stations.filter {
                            val price = when (selectedType) {
                                FuelType.DIESEL -> it.diesel
                                FuelType.E10 -> it.e10
                                FuelType.E5 -> it.e5
                            }
                            price != null && price > 0.5
                        }.sortedBy {
                            when (selectedType) {
                                FuelType.DIESEL -> it.diesel
                                FuelType.E10 -> it.e10
                                FuelType.E5 -> it.e5
                            }
                        }
                    }

                    if (sortedStations.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (userLocation == null) "GPS-Standort wird gesucht…" else "Keine Tankstellen mit aktuellen Preisen im 7 km Umkreis gefunden.",
                                color = TextMuted,
                                fontSize = 12.5.sp
                            )
                        }
                    } else {
                        val minPrice = sortedStations.firstOrNull()?.let {
                            when (selectedType) {
                                FuelType.DIESEL -> it.diesel
                                FuelType.E10 -> it.e10
                                FuelType.E5 -> it.e5
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sortedStations) { station ->
                                val price = when (selectedType) {
                                    FuelType.DIESEL -> station.diesel
                                    FuelType.E10 -> station.e10
                                    FuelType.E5 -> station.e5
                                }
                                val isBestPrice = price != null && price == minPrice

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isBestPrice) SpeedGreen.copy(alpha = 0.12f) else DarkBackground)
                                        .border(
                                            1.dp,
                                            if (isBestPrice) SpeedGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            onSelectStation(station.location, station.brand.ifBlank { station.name })
                                            onDismiss()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = station.brand.ifBlank { station.name },
                                                color = TextWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• ${station.distanceKm} km",
                                                color = CyanPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = "${station.street}, ${station.place}".trim().removePrefix(",").trim(),
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Preis-Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isBestPrice) SpeedGreen else DarkSurface80)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = price?.let { String.format(Locale.GERMANY, "%.3f €", it) } ?: "--",
                                            color = if (isBestPrice) Color.Black else TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog zur API-Key Konfiguration
    if (showKeyDialog) {
        var tempKey by remember { mutableStateOf(if (fuelService.isDemoKey) "" else fuelService.apiKey) }

        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tankerkönig API-Key", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Für amtliche Echtzeit-Preise der Markttransparenzstelle (Bundeskartellamt) wird ein kostenloser persönlicher API-Key benötigt.",
                        color = TextMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    )

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.tankerkoenig.de/"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kostenlosen Key holen (Webseite)", fontSize = 12.sp)
                    }

                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("API-Schlüssel (UUID)") },
                        placeholder = { Text("z.B. 12345678-1234-1234-1234-123456789abc") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedLabelColor = CyanPrimary,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!fuelService.isDemoKey) {
                        TextButton(
                            onClick = {
                                fuelService.resetToDemoKey()
                                showKeyDialog = false
                                refreshStations()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Auf Demo-Schlüssel zurücksetzen", color = SpeedYellow, fontSize = 11.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val keyToSave = tempKey.trim()
                        if (keyToSave.isNotBlank()) {
                            fuelService.updateApiKey(keyToSave)
                        } else {
                            fuelService.resetToDemoKey()
                        }
                        showKeyDialog = false
                        refreshStations()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Text("Speichern & Laden", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) {
                    Text("Abbrechen", color = TextMuted)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
