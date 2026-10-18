package com.meshi.code.ui.screens

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.meshi.code.protocol.MeshContact
import com.meshi.code.repository.AdvertEvent
import com.meshi.code.viewmodel.ChatViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow

private data class MapNode(
    val label: String,
    val subLabel: String,
    val lat: Double,
    val lon: Double,
    val isSelf: Boolean = false,
    val isContact: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: ChatViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val contacts by viewModel.contacts.collectAsState()
    val adverts by viewModel.adverts.collectAsState()
    val selfInfo by viewModel.selfInfo.collectAsState()
    val nicknames by viewModel.nicknames.collectAsState()
    val isLocationSharing by viewModel.isLocationSharing.collectAsState()
    val context = LocalContext.current

    // Initialise OSMDroid config once
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
        }
    }

    // Nodes that have GPS — shown as map markers
    val nodesWithGps = remember(contacts, adverts, selfInfo, nicknames, isLocationSharing) {
        val result = mutableListOf<MapNode>()

        contacts.filter { it.lat != 0.0 || it.lon != 0.0 }.forEach { c ->
            val label = nicknames[c.displayId] ?: c.name.ifBlank { c.displayId.take(8) }
            result.add(MapNode(
                label = label,
                subLabel = c.displayId,
                lat = c.lat,
                lon = c.lon,
                isContact = true,
            ))
        }

        // Adverts not already covered by a saved contact
        val contactIds = contacts.map { it.displayId }.toSet()
        adverts
            .filter { it.lat != 0.0 || it.lon != 0.0 }
            .groupBy { it.senderPubKeyPrefix.toHexString() }
            .map { (id, events) -> id to events.last() }
            .filter { (id, _) -> id !in contactIds }
            .forEach { (id, adv) ->
                result.add(MapNode(
                    label = adv.name.ifBlank { id.take(8) },
                    subLabel = id,
                    lat = adv.lat,
                    lon = adv.lon,
                    isContact = false,
                ))
            }

        result
    }

    // Contacts WITHOUT GPS — shown in the bottom panel
    val nodesWithoutGps = remember(contacts, nicknames) {
        contacts.filter { it.lat == 0.0 && it.lon == 0.0 }
    }

    var showOfflinePanel by remember { mutableStateOf(true) }
    var selectedNode by remember { mutableStateOf<MapNode?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Node Map", fontWeight = FontWeight.Bold) },
                actions = {
                    if (nodesWithoutGps.isNotEmpty()) {
                        BadgedBox(badge = {
                            Badge { Text("${nodesWithoutGps.size}") }
                        }) {
                            IconButton(onClick = { showOfflinePanel = !showOfflinePanel }) {
                                Icon(
                                    Icons.Default.People,
                                    contentDescription = "Contacts without GPS",
                                    tint = if (showOfflinePanel) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding),
        ) {
            // ── OSM Map ──────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                val errorColor = MaterialTheme.colorScheme.error.toArgb()

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            minZoomLevel = 2.0
                            maxZoomLevel = 19.0
                            controller.setZoom(14.0)
                            // Default centre (will be overridden if nodes exist)
                            controller.setCenter(GeoPoint(0.0, 0.0))
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()

                        nodesWithGps.forEach { node ->
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(node.lat, node.lon)
                                title = node.label
                                snippet = "%.5f, %.5f".format(node.lat, node.lon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                infoWindow = BasicInfoWindow(
                                    org.osmdroid.library.R.layout.bonuspack_bubble,
                                    mapView,
                                )
                                // Tint the default marker icon
                                icon = tintedMarkerIcon(
                                    mapView.context,
                                    if (node.isSelf) errorColor else primaryColor,
                                )
                                setOnMarkerClickListener { m, _ ->
                                    selectedNode = node
                                    m.showInfoWindow()
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                        }

                        // Fit map to show all markers
                        if (nodesWithGps.size == 1) {
                            mapView.controller.animateTo(
                                GeoPoint(nodesWithGps[0].lat, nodesWithGps[0].lon),
                                15.0,
                                500L,
                            )
                        } else if (nodesWithGps.size > 1) {
                            val box = BoundingBox.fromGeoPoints(
                                nodesWithGps.map { GeoPoint(it.lat, it.lon) }
                            )
                            mapView.zoomToBoundingBox(box.increaseByScale(1.4f), true, 80)
                        }

                        mapView.invalidate()
                    },
                )

                // Overlay: node count badge
                if (nodesWithGps.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    ) {
                        Text(
                            "${nodesWithGps.size} node${if (nodesWithGps.size != 1) "s" else ""} on map",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Empty state
                if (nodesWithGps.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.LocationOff, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp))
                            Text(
                                "No GPS data yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Nodes appear here once they broadcast\nan advertisement with GPS coordinates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Contacts without GPS ──────────────────────────────────
            if (showOfflinePanel && nodesWithoutGps.isNotEmpty()) {
                HorizontalDivider()
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "No GPS position (${nodesWithoutGps.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(Icons.Default.ExpandMore, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                        }
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 160.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(nodesWithoutGps, key = { it.displayId }) { contact ->
                                val label = nicknames[contact.displayId]
                                    ?: contact.name.ifBlank { contact.displayId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.LocationOff, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium)
                                        Text(contact.displayId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("No GPS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/** Returns the osmdroid default marker drawable tinted with [colorArgb]. */
private fun tintedMarkerIcon(context: Context, colorArgb: Int): Drawable? {
    val d = ContextCompat.getDrawable(
        context,
        org.osmdroid.library.R.drawable.marker_default,
    )?.mutate() ?: return null
    d.setTint(colorArgb)
    return d
}

private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
