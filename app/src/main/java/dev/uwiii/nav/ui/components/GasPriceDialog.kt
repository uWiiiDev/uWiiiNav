package dev.uwiii.nav.ui.components

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
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var selectedType by remember { mutableStateOf(fuelService.preferredFuelType) }
    var stations by remember { mutableStateOf<List<GasStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userLocation) {
        val loc = userLocation ?: return@LaunchedEffect
        isLoading = true
        stations = fuelService.getNearbyStations(loc.latitude, loc.longitude, radiusKm = 7.0)
        isLoading = false
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
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
                                text = "Echtzeit-Preise der Markttransparenzstelle",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

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
                            tint = TextWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
}
