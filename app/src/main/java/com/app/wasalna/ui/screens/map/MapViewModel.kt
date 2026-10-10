package com.app.wasalna.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.R
import com.app.wasalna.data.repository.NominatimRepository
import com.app.wasalna.data.repository.OSRMRepository
import com.app.wasalna.data.repository.OsrmRoute
import com.app.wasalna.data.repository.TransitRoutesRepository
import com.app.wasalna.domain.model.TransitPlan
import com.app.wasalna.data.repository.VelaSearchRepository
import com.app.wasalna.data.traffic.TrafficOverlayState
import com.app.wasalna.data.traffic.TrafficRepository
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.model.SearchResult
import com.app.wasalna.domain.model.TransitPlanner
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.utils.NetworkGuards
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Phase 10-Fix: 100% MapLibre — no engine toggle, no Google Places.
 * - Search: Photon keyless autocomplete (real places + coords, one step).
 * - Map taps: Nominatim reverse-geocode (free).
 * - Route: OSRM GeoJSON from the GPS fix to the confirmed destination.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val nominatim: NominatimRepository,
    private val vela: VelaSearchRepository,
    private val osrm: OSRMRepository,
    private val transit: TransitRoutesRepository,
    private val graphManager: com.app.wasalna.data.routing.RoutingGraphManager,
    private val ghEngine: com.app.wasalna.data.routing.GraphHopperRouteEngine,
    private val traffic: TrafficRepository,
) : ViewModel() {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Photon hits (real places with coordinates) — rendered directly.
    private val _predictions = MutableStateFlow<List<SearchResult>>(emptyList())
    val predictions: StateFlow<List<SearchResult>> = _predictions.asStateFlow()

    // REDESIGN 10 Oct 2026: search loading state for the clear
    // loading/empty/error search UX. Set around the debounced fetch.
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // OSRM route GeoJSON (FeatureCollection string) from the user's GPS
    // position to the confirmed destination. Null = none yet.
    private val _routeGeoJson = MutableStateFlow<String?>(null)
    val routeGeoJson: StateFlow<String?> = _routeGeoJson.asStateFlow()

    // Phase 10.4.0: multimodal ETA — user-selected transport mode +
    // live minutes estimate. Null = not computed yet.
    private val _transportMode = MutableStateFlow(TransportMode.CAR)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    // TRANSIT BYOK (Oct 2026): live Routes-API-v2 plan when the user
    // supplied a key. Null = no key / fetch failed → the OSRM reference
    // corridor below + APPROXIMATE guide stay (never a faked route).
    private val _transitPlan = MutableStateFlow<TransitPlan?>(null)
    val transitPlan: StateFlow<TransitPlan?> = _transitPlan.asStateFlow()

    // Static transit (METRO/MONORAIL/TRAIN, Oct 2026): one-line Arabic
    // summary of the TransitPlanner itinerary for the preview card.
    // Null = no static plan (other modes, cleared, or unroutable).
    private val _staticTransitText = MutableStateFlow<String?>(null)
    val staticTransitText: StateFlow<String?> = _staticTransitText.asStateFlow()

    // Map UX fixes: camera zoom level (3..19) bound to the MapLibre camera.
    private val _cameraZoom = MutableStateFlow(15f)
    val cameraZoom: StateFlow<Float> = _cameraZoom.asStateFlow()

    fun setZoom(zoom: Float) {
        _cameraZoom.value = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun zoomIn() = setZoom(_cameraZoom.value + 1f)
    fun zoomOut() = setZoom(_cameraZoom.value - 1f)

    // Phase 10.8.0g (traffic): Vela live-traffic raster ported 9 Oct 2026 per
    // Mohamed's order — Google's keyless `/maps/vt` traffic raster as a MapLibre
    // RasterSource (see TrafficOverlay + TrafficRepository for the Vela findings
    // summary). Viewport tiles reveal the viewed region to Google, so the toggle
    // defaults OFF with an Arabic disclosure. Rendering is raster (no parsed
    // segments — parsing a PNG would be invented data). Health probes run on
    // Dispatchers.IO; failure keeps old tiles + silent retry, never a crash.
    private val _isTrafficEnabled = MutableStateFlow(false)
    val isTrafficEnabled: StateFlow<Boolean> = _isTrafficEnabled.asStateFlow()

    val trafficState: StateFlow<TrafficOverlayState> = traffic.overlayState

    // True offline mode (GPS-chip only): persisted user flag — the SAME
    // DataStore key the header cloud button + Settings toggle write
    // (single source of truth, two-way synced). When true, EVERY network
    // call site in this ViewModel is bypassed — see OFFLINE BOUNDARY marks.
    val isOfflineMode: StateFlow<Boolean> = settingsRepository.isForceOffline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setForceOffline(enabled) }
                .onFailure { Log.e(TAG, "persist offline mode failed: ${it.message}") }
        }
    }

    fun setTrafficEnabled(enabled: Boolean) {
        // Offline keeps the persisted traffic preference untouched (it
        // restores on reconnect) — probes + raster rendering stay gated
        // by isOfflineMode, so enabling here only takes effect online.
        if (enabled && isOfflineMode.value) {
            viewModelScope.launch { _events.emit(MSG_TRAFFIC_OFFLINE) }
        }
        _isTrafficEnabled.value = enabled
        traffic.setEnabled(enabled)
        if (enabled) {
            probeTrafficNow()
            restartTrafficRefreshLoop()
        } else {
            trafficRefreshJob?.cancel()
            trafficRefreshJob = null
        }
        viewModelScope.launch {
            runCatching { settingsRepository.setTrafficEnabled(enabled) }
                .onFailure { Log.e(TAG, "persist traffic failed: ${it.message}") }
        }
    }

    // Last known viewport center for traffic health probes (set from MapScreen
    // on camera idle; null = Cairo fallback inside the repository).
    private var lastTrafficLat: Double? = null
    private var lastTrafficLng: Double? = null
    private var lastTrafficZoom: Float? = null
    private var trafficSettleJob: Job? = null
    private var trafficRefreshJob: Job? = null

    /** Camera settled (debounced ~1s in the caller contract) -> probe once. */
    fun onTrafficCameraSettled(lat: Double?, lng: Double?, zoom: Float?) {
        // ── OFFLINE BOUNDARY: no traffic health probe when offline.
        if (_isTrafficEnabled.value != true || isOfflineMode.value) return
        lastTrafficLat = lat
        lastTrafficLng = lng
        lastTrafficZoom = zoom
        trafficSettleJob?.cancel()
        trafficSettleJob = viewModelScope.launch {
            delay(TRAFFIC_SETTLE_DEBOUNCE_MS)
            probeTrafficNow()
        }
    }

    /** One health probe on Dispatchers.IO (inside the repository). Silent. */
    fun probeTrafficNow() {
        // ── OFFLINE BOUNDARY: no traffic health probe when offline.
        if (_isTrafficEnabled.value != true || isOfflineMode.value) return
        viewModelScope.launch {
            runCatching {
                traffic.probeViewport(lastTrafficLat, lastTrafficLng, lastTrafficZoom)
            }.onFailure { Log.w(TAG, "traffic probe failed: ${it.message}") }
        }
    }

    /** Periodic refresh while visible: re-probe every 60s (tiles refresh
     *  themselves on camera move via the engine; this only updates health). */
    private fun restartTrafficRefreshLoop() {
        trafficRefreshJob?.cancel()
        trafficRefreshJob = viewModelScope.launch {
            while (_isTrafficEnabled.value == true && !isOfflineMode.value) {
                delay(TRAFFIC_REFRESH_MS)
                // ── OFFLINE BOUNDARY: loop exits the moment offline turns on.
                if (_isTrafficEnabled.value != true || isOfflineMode.value) break
                runCatching {
                    traffic.probeViewport(lastTrafficLat, lastTrafficLng, lastTrafficZoom)
                }.onFailure { Log.w(TAG, "traffic refresh failed: ${it.message}") }
            }
        }
    }

    // Map UX fixes: premium gate for quick-trip interstitial ads.
    val isPremium: StateFlow<Boolean> = settingsRepository.isPremium
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Map UX fixes: Home & Work quick places (persisted in DataStore).
    val homeLat: StateFlow<Double?> = settingsRepository.homeLat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val homeLng: StateFlow<Double?> = settingsRepository.homeLng
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val homeAddress: StateFlow<String> = settingsRepository.homeAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val workLat: StateFlow<Double?> = settingsRepository.workLat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val workLng: StateFlow<Double?> = settingsRepository.workLng
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val workAddress: StateFlow<String> = settingsRepository.workAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setHome(lat: Double?, lng: Double?, address: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setHome(lat, lng, address) }
                .onFailure { Log.e(TAG, "persist home failed: ${it.message}") }
        }
    }

    fun setWork(lat: Double?, lng: Double?, address: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setWork(lat, lng, address) }
                .onFailure { Log.e(TAG, "persist work failed: ${it.message}") }
        }
    }

    /**
     * Map Home/Work from-map-point (elderly-friendly path): reverse-geocodes
     * the tapped/long-pressed point via Nominatim (Dispatchers.IO inside the
     * repository) and persists via SettingsRepository.setHome/setWork — the
     * SAME storage as the search-based path, no duplication. Emits an Arabic
     * confirmation; HomeWorkQuickRow reflects immediately because it collects
     * homeAddress/workAddress StateFlows. No `!!`, no new permissions.
     */
    fun saveMapPointAsHome(lat: Double, lng: Double) {
        viewModelScope.launch {
            // ── OFFLINE BOUNDARY: no Nominatim reverse-geocode when offline.
            val address = if (isOfflineMode.value) {
                _selectedAddress.value?.takeIf { it.isNotBlank() }
                    ?: fallbackName(lat, lng)
            } else {
                nominatim.reverseGeocode(lat, lng)
                    ?: _selectedAddress.value?.takeIf { it.isNotBlank() }
                    ?: fallbackName(lat, lng)
            }
            runCatching { settingsRepository.setHome(lat, lng, address) }
                .onSuccess { _events.emit(MSG_HOME_SAVED) }
                .onFailure {
                    Log.e(TAG, "save home from map failed: ${it.message}")
                    _events.emit(MSG_SAVE_FAILED)
                }
        }
    }

    fun saveMapPointAsWork(lat: Double, lng: Double) {
        viewModelScope.launch {
            // ── OFFLINE BOUNDARY: no Nominatim reverse-geocode when offline.
            val address = if (isOfflineMode.value) {
                _selectedAddress.value?.takeIf { it.isNotBlank() }
                    ?: fallbackName(lat, lng)
            } else {
                nominatim.reverseGeocode(lat, lng)
                    ?: _selectedAddress.value?.takeIf { it.isNotBlank() }
                    ?: fallbackName(lat, lng)
            }
            runCatching { settingsRepository.setWork(lat, lng, address) }
                .onSuccess { _events.emit(MSG_WORK_SAVED) }
                .onFailure {
                    Log.e(TAG, "save work from map failed: ${it.message}")
                    _events.emit(MSG_SAVE_FAILED)
                }
        }
    }

    /**
     * Map UX fixes: auto live-location pin — on entering the Map screen
     * (blank picker, no trip args) center the camera on the GPS fix,
     * drop a marker pin automatically, and surface the Save/Add button
     * with no manual tap needed. [selectedAddress] display is already
     * wired in MapScreen. Safe no-op without permission/fix or when a
     * destination/trip is already present. Idempotent per screen entry.
     */
    private var autoCenterDone = false
    fun autoCenterOnMyLocation(force: Boolean = false) {
        if (autoCenterDone && !force) return
        if (_selectedLatLng.value != null) return
        viewModelScope.launch {
            val fix = currentFix() ?: return@launch
            autoCenterDone = true
            val latLng = LatLng(fix.latitude, fix.longitude)
            _selectedLatLng.value = latLng
            _recenterTarget.value = latLng
            // ── OFFLINE BOUNDARY: no Nominatim reverse-geocode when offline.
            val address = if (isOfflineMode.value) {
                fallbackName(fix.latitude, fix.longitude)
            } else {
                nominatim.reverseGeocode(fix.latitude, fix.longitude)
                    ?: fallbackName(fix.latitude, fix.longitude)
            }
            _selectedAddress.value = address
            _selectedName.value = address
            _searchQuery.value = address
        }
    }

    private val _etaMinutes = MutableStateFlow<Int?>(null)
    val etaMinutes: StateFlow<Int?> = _etaMinutes.asStateFlow()

    // Phase 10.4.3.1: one-shot camera jump (My Location FAB). The map
    // consumes it and animates; never confused with a destination.
    private val _recenterTarget = MutableStateFlow<LatLng?>(null)
    val recenterTarget: StateFlow<LatLng?> = _recenterTarget.asStateFlow()

    fun recenterToMyLocation() {
        viewModelScope.launch {
            currentFix()?.let { fix ->
                _recenterTarget.value = LatLng(fix.latitude, fix.longitude)
            }
        }
    }

    /**
     * Phase 10.4.3.1: Home widget tap → Map opens on the live trip:
     * marker + mode + OSRM route follow automatically (reactive effect).
     */
    fun showTrip(name: String, lat: Double, lng: Double, modeName: String?) {
        setTransportMode(TransportMode.fromName(modeName))
        _selectedName.value = name
        _selectedAddress.value = name
        _selectedLatLng.value = LatLng(lat, lng)
        _predictions.value = emptyList()
        _searchQuery.value = name
    }

    private val _selectedName = MutableStateFlow<String?>(null)
    val selectedName: StateFlow<String?> = _selectedName.asStateFlow()

    private val _selectedLatLng = MutableStateFlow<LatLng?>(null)
    val selectedLatLng: StateFlow<LatLng?> = _selectedLatLng.asStateFlow()

    // Human-readable address for the tapped/searched point — shown above
    // the search bar and the Save FAB; used as the saved Location name.
    private val _selectedAddress = MutableStateFlow<String?>(null)
    val selectedAddress: StateFlow<String?> = _selectedAddress.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // One-shot UI messages.
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // Surfaced search error for UI (empty = no error).
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    init {
        // All MutableStateFlow/SharedFlow backing fields are declared ABOVE
        // this block (declarations before use). viewModelScope uses
        // Dispatchers.Main.immediate, so a launched coroutine may resume
        // before later property initializers run -> NPE on setValue.
        // Phase 10.4.2: restore the persisted mode (chosen on any screen).
        viewModelScope.launch {
            val saved = runCatching { settingsRepository.transportMode.first() }
                .getOrDefault("CAR")
            _transportMode.value = TransportMode.fromName(saved)
        }
        // True offline mode: react to the flag itself (collecting
        // isOfflineMode guarantees the guard reads below see the fresh
        // value). Offline ON stops all traffic network work; back online
        // with traffic enabled resumes probes — persisted traffic
        // preference is never clobbered by the mode switch.
        viewModelScope.launch {
            isOfflineMode.collect { offline ->
                trafficSettleJob?.cancel()
                if (offline) {
                    trafficRefreshJob?.cancel()
                    trafficRefreshJob = null
                } else if (_isTrafficEnabled.value) {
                    probeTrafficNow()
                    restartTrafficRefreshLoop()
                }
            }
        }
        // Map UX fixes: restore persisted traffic toggle (default OFF).
        viewModelScope.launch {
            val restored = runCatching {
                settingsRepository.isTrafficEnabled.first()
            }.getOrDefault(false)
            _isTrafficEnabled.value = restored
            traffic.setEnabled(restored)
            if (restored) {
                probeTrafficNow()
                restartTrafficRefreshLoop()
            }
        }
    }

    private var searchJob: Job? = null

    /**
     * Trigger logic — queries shorter than 2 chars never hit the API;
     * an emptied query clears predictions immediately.
     */
    fun onQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _predictions.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            return
        }
        if (query.trim().length < 2) {
            _predictions.value = emptyList()
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            searchPlaces(query)
        }
    }

    /**
     * Phase 10-Fix: Photon keyless autocomplete — one step, coords included.
     * Phase 10.4.0: localized — the current GPS fix biases results toward
     * the user's country/city; without a fix it falls back to global search.
     */
    fun searchPlaces(query: String) {
        viewModelScope.launch {
            // ── OFFLINE BOUNDARY: Photon search needs network — when
            // offline, keep the map usable and say so in Arabic.
            if (isOfflineMode.value) {
                _predictions.value = emptyList()
                _searchError.value = MSG_OFFLINE_SEARCH
                _isSearching.value = false
                return@launch
            }
            val fix = currentFix()
            runCatching {
                vela.suggest(query, lat = fix?.latitude, lon = fix?.longitude)
            }.onSuccess {
                _predictions.value = it
                // Phase 10.4.3.2: offline search — map renders, network doesn't.
                _searchError.value = if (it.isEmpty() && vela.lastWasOffline) {
                    NetworkGuards.OFFLINE_MESSAGE
                } else {
                    null
                }
            }.onFailure { e ->
                Log.e(TAG, "Photon search failed: ${e.message}")
                _predictions.value = emptyList()
                _searchError.value = "Search failed: check your connection"
            }
            _isSearching.value = false
        }
    }

    /**
     * TRIP SIMULATION: exposes one GPS fix so the simulator can use it
     * as the virtual origin. Null = no fix/permission. No behavior change
     * to the existing private path.
     */
    suspend fun peekCurrentFix(): android.location.Location? = currentFix()

    /** Last GPS fix, guarded by permission with a short timeout. Null = none. */
    private suspend fun currentFix(): android.location.Location? {
        if (!hasLocationPermission()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(LOC_TIMEOUT_MS) {
                runCatching { fusedClient.lastLocation.await() }.getOrNull()
            }
        }
    }

    /**
     * Phase 10.4.0: route + ETA refresh for the current destination under
     * the selected transport mode. Called on every confirmed destination
     * and on every mode change. Silent skip without a fix — never crashes.
     */
    private suspend fun refreshRouteAndEta(destLat: Double, destLng: Double) {
        val start = currentFix() ?: return
        // ── OFFLINE BOUNDARY: offline mode bypasses OSRM entirely — local
        // GraphHopper engine + GPS straight-line fallback only, no network.
        // Transit Routes API is NEVER called offline (stays local-only).
        if (isOfflineMode.value) {
            _transitPlan.value = null
            refreshOfflineRoute(start.latitude, start.longitude, destLat, destLng)
            return
        }
        // TRANSIT BYOK: with a user key, try the live v2 plan first
        // (Dispatchers.IO + 15 s timeouts inside the repository). No key
        // or any failure → corridor fallback below + Arabic notice.
        if (_transportMode.value == TransportMode.TRANSIT) {
            val key = runCatching { settingsRepository.routesApiKey.first() }
                .getOrDefault("").trim()
            if (key.isNotEmpty()) {
                val plan = runCatching {
                    transit.fetchPlan(
                        key,
                        start.latitude, start.longitude,
                        destLat, destLng,
                    )
                }.onFailure {
                    Log.e(TAG, "live transit failed: ${it.message}")
                }.getOrNull()
                if (plan != null) {
                    _transitPlan.value = plan
                    _routeGeoJson.value = plan.toGeoJson()
                    _etaMinutes.value = plan.durationSec
                        .takeIf { it >= 0 }
                        ?.let { kotlin.math.round(it / 60.0).toInt().coerceAtLeast(1) }
                    return
                }
                _transitPlan.value = null
                _events.emit(appContext.getString(R.string.transit_fetch_failed))
            } else {
                _transitPlan.value = null
            }
        } else {
            _transitPlan.value = null
        }
        // Static transit (METRO/MONORAIL/TRAIN): station-to-station plan
        // on embedded data (TransitPlanner) + OSRM foot for the walk legs
        // only — ride legs never touch OSRM/GraphHopper. Returns here.
        if (_transportMode.value.isTransit && _transportMode.value != TransportMode.TRANSIT) {
            _transitPlan.value = null
            refreshStaticTransit(start.latitude, start.longitude, destLat, destLng)
            return
        }
        _staticTransitText.value = null
        val profile = _transportMode.value.osrmProfile
        val route: OsrmRoute? = runCatching {
            osrm.fetchRoute(start.latitude, start.longitude, destLat, destLng, profile)
        }.onFailure {
            Log.e(TAG, "route fetch failed: ${it.message}")
        }.getOrNull()
        if (route != null) {
            _routeGeoJson.value = route.geoJson
            _etaMinutes.value = route.durationSec
                .takeIf { it >= 0 }
                ?.let { kotlin.math.round(it / 60.0).toInt().coerceAtLeast(1) }
            return
        }
        // Phase 10.4.3.2: offline routing — tell the user, keep the map.
        if (osrm.lastWasOffline) {
            _events.emit(NetworkGuards.OFFLINE_MESSAGE)
        }
        // Step 5: OSRM failed — try the local engine before giving up.
        refreshOfflineRoute(start.latitude, start.longitude, destLat, destLng)
    }

    /**
     * Phase 10.5.x Step 5 (upgraded Oct 2026): on-device route on
     * Dispatchers.IO (inside the engine) with per-region auto-select —
     * the INSTALLED graph covering origin→destination is loaded
     * (auto-switch when the route moves regions; engine holds one graph).
     * Success → road polyline + ETA. SpansUnstored → keep the map with
     * no polyline + emit the Arabic straight-line notice (never a faked
     * route; the alarm path always keeps its distanceTo() fallback in
     * TrackingService, so it never breaks).
     * ── OFFLINE BOUNDARY: engine + files only, zero network. ──
     */
    private suspend fun refreshOfflineRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
    ) {
        val routed = runCatching {
            com.app.wasalna.data.routing.routeOfflineWithAutoSelect(
                graphManager, ghEngine, startLat, startLng, destLat, destLng,
            )
        }.getOrNull()
        val local = routed?.second
        if (local != null) {
            _routeGeoJson.value = local.toGeoJson()
            _etaMinutes.value = local.etaMinutes
        } else {
            _routeGeoJson.value = null
            // ── OFFLINE BOUNDARY: string-only notice, no network. ──
            val known = withContext(Dispatchers.IO) {
                graphManager.knownRegionFor(startLat, startLng, destLat, destLng)
            }
            val notice = known?.let {
                appContext.getString(R.string.routing_span_suggest, it.name)
            } ?: appContext.getString(R.string.routing_span_unstored)
            _events.emit(notice)
        }
    }

    /**
     * Static transit preview (METRO/MONORAIL/TRAIN, Oct 2026):
     * TransitPlanner itinerary on embedded data + OSRM `foot` for the two
     * walk legs (origin→board, alight→destination). The ride polyline is
     * drawn ONLY through stations with real coords (metro); line-level
     * plans (monorail/LRT, no coords) show text + ETA with NO polyline —
     * never a faked route. ETA = planner door-to-door total. Offline:
     * straight walk lines + real ride coords (still no network).
     * ── HONESTY: every surface here pairs with the approximate label. ──
     */
    private suspend fun refreshStaticTransit(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double,
    ) {
        val mode = _transportMode.value
        val plan = withContext(Dispatchers.Default) {
            runCatching {
                TransitPlanner.plan(startLat, startLng, destLat, destLng, mode)
            }.getOrNull()
        }
        if (plan == null || plan.kind == "NONE") {
            _routeGeoJson.value = null
            _etaMinutes.value = null
            _staticTransitText.value = null
            val why = appContext.getString(R.string.transit_off_corridor)
            _events.emit(appContext.getString(R.string.transit_static_none, modeLabel(mode), why))
            return
        }
        _etaMinutes.value = plan.totalMin
        _staticTransitText.value = summarizeStatic(plan)
        // Non-null station coords (smart-cast via locals — no !!).
        val bLat = plan.board?.lat
        val bLng = plan.board?.lng
        val aLat = plan.alight?.lat
        val aLng = plan.alight?.lng
        if (bLat == null || bLng == null || aLat == null || aLng == null) {
            // Line-level (no station coords): text + ETA only, no polyline.
            _routeGeoJson.value = null
            return
        }
        val rideCoords = plan.ridePoints()
            .mapNotNull { st ->
                val la = st.lat
                val lo = st.lng
                if (la == null || lo == null) null else listOf(lo, la)
            }
        if (isOfflineMode.value) {
            // Offline: straight walk lines + real ride coords, no network.
            val walkA = listOf(listOf(startLng, startLat), listOf(bLng, bLat))
            val walkB = listOf(listOf(aLng, aLat), listOf(destLng, destLat))
            _routeGeoJson.value = lineStringGeoJson(walkA + rideCoords + walkB)
            return
        }
        val legA = runCatching {
            osrm.fetchRoute(startLat, startLng, bLat, bLng, "foot")
        }.getOrNull()
        val legB = runCatching {
            osrm.fetchRoute(aLat, aLng, destLat, destLng, "foot")
        }.onFailure {
            Log.e(TAG, "transit walk leg failed: ${it.message}")
        }.getOrNull()
        // JSON merge on Default (geometries can be large — never Main).
        val merged = withContext(Dispatchers.Default) {
            val coordsA = legA?.let { coordsOf(it.geoJson) } ?: emptyList()
            val coordsB = legB?.let { coordsOf(it.geoJson) } ?: emptyList()
            (coordsA + rideCoords + coordsB).distinct()
        }
        _routeGeoJson.value = if (merged.size >= 2) lineStringGeoJson(merged) else null
    }

    /** Arabic-first one-line summary of a static itinerary (preview card). */
    private fun summarizeStatic(plan: TransitPlanner.TransitItinerary): String {
        val stops = plan.segments.sumOf { it.stops.size }
        return if (plan.segments.size > 1) {
            val legs = plan.segments.joinToString(" + ") { it.line.nameAr }
            appContext.getString(R.string.transit_static_transfer, legs, plan.totalMin)
        } else {
            val line = plan.segments.firstOrNull()?.line?.nameAr
                ?: appContext.getString(R.string.vehicle_title)
            val from = plan.board?.displayName() ?: "—"
            val to = plan.alight?.displayName() ?: "—"
            val base = appContext.getString(R.string.transit_static_direct, line, from, to, stops, plan.totalMin)
            if (plan.reason == "line_level") {
                base + " " + appContext.getString(R.string.transit_line_level)
            } else {
                base
            }
        }
    }

    private fun modeLabel(mode: TransportMode): String = when (mode) {
        TransportMode.METRO -> appContext.getString(R.string.vehicle_metro)
        TransportMode.MONORAIL -> appContext.getString(R.string.vehicle_monorail)
        else -> appContext.getString(R.string.vehicle_train_lrt)
    }

    /** Extracts the [lng, lat] ring from an OSRM FeatureCollection string. */
    private fun coordsOf(geoJson: String): List<List<Double>> {
        return runCatching {
            val coords = org.json.JSONObject(geoJson)
                .optJSONArray("features")
                ?.optJSONObject(0)
                ?.optJSONObject("geometry")
                ?.optJSONArray("coordinates") ?: return emptyList()
            List(coords.length()) { i ->
                val pair = coords.optJSONArray(i) ?: return emptyList()
                listOf(pair.optDouble(0), pair.optDouble(1))
            }
        }.getOrDefault(emptyList())
    }

    /** Builds a FeatureCollection LineString from [lng, lat] pairs. */
    private fun lineStringGeoJson(pairs: List<List<Double>>): String {
        val coords = pairs.joinToString(",") { "[${it[0]},${it[1]}]" }
        return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\"," +
            "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coords]}," +
            "\"properties\":{}}]}"
    }

    /**
     * Phase 10.4.1: mode switch only flips state — the route/ETA refetch
     * is driven reactively by LaunchedEffect(selectedLatLng, transportMode)
     * in MapScreen, so EVERY mode change guarantees a fresh OSRM call.
     */
    fun setTransportMode(mode: TransportMode) {
        _transportMode.value = mode
        viewModelScope.launch {
            runCatching { settingsRepository.setTransportMode(mode.name) }
                .onFailure { Log.e(TAG, "persist mode failed: ${it.message}") }
        }
    }

    /**
     * Map UX fixes: quick-trip entry (Home/Work tap) — selects the saved
     * point as the destination; route/ETA follow reactively. No-op when
     * the place has no coordinates yet (caller prompts to set it first).
     */
    fun selectQuickPlace(lat: Double?, lng: Double?, address: String): Boolean {
        if (lat == null || lng == null) return false
        _selectedName.value = address.ifBlank { fallbackName(lat, lng) }
        _selectedAddress.value = address.ifBlank { fallbackName(lat, lng) }
        _selectedLatLng.value = LatLng(lat, lng)
        _predictions.value = emptyList()
        _searchError.value = null
        _searchQuery.value = address
        return true
    }

    /** Result click — Photon hits always carry lat/lng: select immediately. */
    fun fetchPlaceDetails(result: SearchResult) {
        val lat = result.lat ?: return
        val lng = result.lng ?: return
        _selectedName.value = result.name
        _selectedAddress.value = result.name
        _selectedLatLng.value = LatLng(lat, lng)
        _predictions.value = emptyList()
        _searchError.value = null
        _isSearching.value = false
        _searchQuery.value = result.name
        // Phase 10.4.1: route follows reactively via
        // LaunchedEffect(selectedLatLng, transportMode) in MapScreen.
    }

    /**
     * Phase 10.4.1: reactive route entry point — reads the current
     * selection + transport mode. Safe to call from LaunchedEffect on
     * every selection or mode change (idempotent, IO-bound).
     */
    fun refreshRouteAndEta() {
        val dest = _selectedLatLng.value ?: return
        viewModelScope.launch { refreshRouteAndEta(dest.latitude, dest.longitude) }
    }

    /**
     * Map tap entry point — marker/camera source of truth, then
     * Nominatim reverse-geocode (Dispatchers.IO, try-catch) into a
     * readable address used as the saved name.
     */
    fun onMapClicked(lat: Double, lng: Double) {
        _selectedLatLng.value = LatLng(lat, lng)
        _predictions.value = emptyList()
        viewModelScope.launch {
            // ── OFFLINE BOUNDARY: no Nominatim reverse-geocode when offline.
            val address = if (isOfflineMode.value) {
                fallbackName(lat, lng)
            } else {
                nominatim.reverseGeocode(lat, lng) ?: fallbackName(lat, lng)
            }
            _selectedAddress.value = address
            _selectedName.value = address
            _searchQuery.value = address
        }
    }

    private fun fallbackName(lat: Double, lng: Double): String =
        "Selected Location (${lat.toString().take(5)}, ${lng.toString().take(5)})"

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun clearSelection() {
        _selectedName.value = null
        _selectedAddress.value = null
        _selectedLatLng.value = null
        _routeGeoJson.value = null
        _etaMinutes.value = null
        _transitPlan.value = null
        _staticTransitText.value = null
    }

    /**
     * Persists the selected destination, then invokes [onSaved].
     * Phase 10.4.1: aborts with "already saved" on duplicates; on success
     * the selection is cleared BEFORE navigating so MapLibre never
     * recomposes wildly mid-save (no tile loss / camera jump).
     */
    fun saveDestination(onSaved: () -> Unit = {}) {
        val name = _selectedName.value ?: return
        val latLng = _selectedLatLng.value ?: return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val duplicate = runCatching {
                locationRepository.isDuplicate(name, latLng.latitude, latLng.longitude)
            }.getOrDefault(false)
            if (duplicate) {
                _isSaving.value = false
                _events.emit(MSG_ALREADY_SAVED)
                return@launch
            }
            runCatching {
                locationRepository.addLocation(
                    Location(
                        name = name,
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                    ),
                )
            }.onSuccess {
                clearSelection()
                onSaved()
            }.onFailure { Log.e(TAG, "saveDestination failed: ${it.message}") }
            _isSaving.value = false
        }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val TRAFFIC_SETTLE_DEBOUNCE_MS = 1_000L
        private const val TRAFFIC_REFRESH_MS = 60_000L
        private const val LOC_TIMEOUT_MS = 8_000L
        const val MIN_ZOOM = 3f
        const val MAX_ZOOM = 19f
        const val MSG_ALREADY_SAVED = "This place is already saved!"
        const val MSG_HOME_SAVED = "تم حفظ Home ✅"
        const val MSG_WORK_SAVED = "تم حفظ Work ✅"
        const val MSG_SAVE_FAILED = "تعذر الحفظ — تحقق من الاتصال وحاول مجددًا"
        const val FALLBACK_MSG = "Offline Routing failed, using GPS straight-line"
        /** Upgrade (Oct 2026): route spans unstored areas — names the graph to download. */
        const val SPAN_WITH_SUGGESTION = "Route is outside stored offline maps — download %1$s for road routing 🛰️"
        const val MSG_OFFLINE_SEARCH =
            "وضع أوفلاين: البحث يحتاج اتصال — اعتمد على الأماكن المحفوظة 🛰️"
        const val MSG_TRAFFIC_OFFLINE =
            "وضع أوفلاين: طبقة الازدحام متوقفة — تعود مع الاتصال 🛰️"
    }
}

// Phase 10.4.2: TransportMode lives in domain.model (shared with the
// tracking service); imported above.
