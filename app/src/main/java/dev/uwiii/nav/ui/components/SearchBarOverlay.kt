package dev.uwiii.nav.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uwiii.nav.favorites.FavoritesManager
import dev.uwiii.nav.favorites.PlaceType
import dev.uwiii.nav.geocoding.SearchResult
import dev.uwiii.nav.geocoding.SearchService
import dev.uwiii.nav.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarOverlay(
    userLocation: LatLng?,
    favoritesManager: FavoritesManager,
    onSelectDestination: (LatLng, String) -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSetDialogType by remember { mutableStateOf<PlaceType?>(null) }

    val scope = rememberCoroutineScope()
    val searchService = remember { SearchService() }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val executeSearch: (String) -> Unit = { q ->
        query = q
        searchJob?.cancel()
        if (q.trim().length >= 2) {
            searchJob = scope.launch {
                isSearching = true
                results = searchService.searchPlaces(q, userLocation)
                isSearching = false
            }
        } else {
            results = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface80)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // Suchfeld
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
            placeholder = { Text("Adresse oder Ort suchen…", color = TextMuted, fontSize = 14.5.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = CyanPrimary
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            results = emptyList()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Löschen",
                                tint = TextMuted
                            )
                        }
                    }
                    trailingActions?.invoke()
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                cursorColor = CyanPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Schnellwahl-Leiste & Favoriten (nur wenn noch keine aktive Textsuche läuft)
        if (query.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                // 1. Favoriten-Zeile (Zuhause & Arbeit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Zuhause Button
                    FavoriteChip(
                        title = "Zuhause",
                        subtitle = favoritesManager.homePlace?.address ?: "Festlegen",
                        icon = Icons.Default.Home,
                        isSet = favoritesManager.homePlace != null,
                        onClick = {
                            favoritesManager.homePlace?.let {
                                onSelectDestination(it.location, "Zuhause")
                            } ?: run {
                                showSetDialogType = PlaceType.HOME
                            }
                        },
                        onLongClick = { showSetDialogType = PlaceType.HOME },
                        modifier = Modifier.weight(1f)
                    )

                    // Arbeit Button
                    FavoriteChip(
                        title = "Arbeit",
                        subtitle = favoritesManager.workPlace?.address ?: "Festlegen",
                        icon = Icons.Default.Work,
                        isSet = favoritesManager.workPlace != null,
                        onClick = {
                            favoritesManager.workPlace?.let {
                                onSelectDestination(it.location, "Arbeit")
                            } ?: run {
                                showSetDialogType = PlaceType.WORK
                            }
                        },
                        onLongClick = { showSetDialogType = PlaceType.WORK },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Schnellwahl-Kategorien (Pills)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryPill(icon = Icons.Default.LocalGasStation, label = "Tankstellen") {
                        executeSearch("Tankstelle")
                    }
                    CategoryPill(icon = Icons.Default.EvStation, label = "Ladesäulen") {
                        executeSearch("Ladesäule")
                    }
                    CategoryPill(icon = Icons.Default.LocalParking, label = "Parken") {
                        executeSearch("Parkplatz")
                    }
                    CategoryPill(icon = Icons.Default.Restaurant, label = "Essen & Rast") {
                        executeSearch("Restaurant")
                    }
                }

                // 3. Verlauf der letzten Ziele (wenn vorhanden)
                if (favoritesManager.recentPlaces.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Letzte Ziele",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 4.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                    ) {
                        items(favoritesManager.recentPlaces) { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectDestination(place.location, place.title)
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = place.title,
                                            color = TextWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (place.address.isNotBlank()) {
                                            Text(
                                                text = place.address,
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { favoritesManager.removeRecent(place) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ladebalken während der Suche
        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = CyanPrimary,
                trackColor = DarkSurface80
            )
        }

        // Suchergebnisse
        AnimatedVisibility(visible = results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .padding(top = 4.dp, bottom = 4.dp)
            ) {
                items(results) { res ->
                    val distLabel = res.distanceKm?.let { km ->
                        if (km >= 1.0) {
                            val rounded = (km * 10.0).roundToInt() / 10.0
                            " • $rounded km"
                        } else {
                            val m = (km * 1000.0).roundToInt()
                            " • $m m"
                        }
                    } ?: ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                favoritesManager.addRecent(res.title, res.subtitle, res.location)
                                onSelectDestination(res.location, res.title)
                                query = ""
                                results = emptyList()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = res.title,
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (distLabel.isNotBlank()) {
                                    Text(
                                        text = distLabel,
                                        color = CyanPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (res.subtitle.isNotBlank()) {
                                Text(
                                    text = res.subtitle,
                                    color = TextMuted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }

    // Dialog zum Festlegen von Zuhause / Arbeit
    showSetDialogType?.let { type ->
        SetFavoriteDialog(
            type = type,
            favoritesManager = favoritesManager,
            userLocation = userLocation,
            onDismiss = { showSetDialogType = null }
        )
    }
}

@Composable
private fun FavoriteChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSet: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSet) CyanPrimary.copy(alpha = 0.14f) else DarkBackground)
            .border(
                1.dp,
                if (isSet) CyanPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSet) CyanPrimary else TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = if (isSet) TextWhite.copy(alpha = 0.8f) else CyanPrimary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkBackground)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
