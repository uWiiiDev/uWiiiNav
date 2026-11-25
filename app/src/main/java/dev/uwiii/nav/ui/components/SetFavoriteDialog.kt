package dev.uwiii.nav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import dev.uwiii.nav.favorites.FavoritesManager
import dev.uwiii.nav.favorites.PlaceType
import dev.uwiii.nav.favorites.SavedPlace
import dev.uwiii.nav.geocoding.SearchResult
import dev.uwiii.nav.geocoding.SearchService
import dev.uwiii.nav.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

@Composable
fun SetFavoriteDialog(
    type: PlaceType,
    favoritesManager: FavoritesManager,
    userLocation: LatLng?,
    onDismiss: () -> Unit
) {
    val titleLabel = if (type == PlaceType.HOME) "Zuhause-Adresse" else "Arbeitsadresse"
    val iconVector = if (type == PlaceType.HOME) Icons.Default.Home else Icons.Default.Work
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchService = remember { SearchService() }
    var searchJob by remember { mutableStateOf<Job?>(null) }

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
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = titleLabel,
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
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

                // Option 1: Aktuellen Standort verwenden
                if (userLocation != null) {
                    OutlinedButton(
                        onClick = {
                            if (type == PlaceType.HOME) {
                                favoritesManager.setHome(
                                    title = "Zuhause",
                                    address = "Aktueller Standort",
                                    location = userLocation
                                )
                            } else {
                                favoritesManager.setWork(
                                    title = "Arbeit",
                                    address = "Aktueller Standort",
                                    location = userLocation
                                )
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aktuellen Standort festlegen")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Option 2: Adresse suchen
                OutlinedTextField(
                    value = query,
                    onValueChange = { newQuery ->
                        query = newQuery
                        searchJob?.cancel()
                        if (newQuery.trim().length >= 2) {
                            searchJob = scope.launch {
                                delay(350)
                                isSearching = true
                                results = searchService.searchPlaces(newQuery, userLocation)
                                isSearching = false
                            }
                        } else {
                            results = emptyList()
                        }
                    },
                    placeholder = { Text("Straße & Hausnummer suchen…", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = CyanPrimary
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = CyanPrimary,
                        focusedIndicatorColor = CyanPrimary,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSearching) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = CyanPrimary,
                        trackColor = DarkSurface80
                    )
                }

                if (results.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(results) { res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (type == PlaceType.HOME) {
                                            favoritesManager.setHome(
                                                title = "Zuhause",
                                                address = res.title,
                                                location = res.location
                                            )
                                        } else {
                                            favoritesManager.setWork(
                                                title = "Arbeit",
                                                address = res.title,
                                                location = res.location
                                            )
                                        }
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = res.title,
                                        color = TextWhite,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (res.subtitle.isNotBlank()) {
                                        Text(
                                            text = res.subtitle,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        }
                    }
                }
            }
        }
    }
}
