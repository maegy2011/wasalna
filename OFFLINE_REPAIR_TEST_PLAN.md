# Repair & Test Plan: Wasalna Offline Mode (Global)

## Executive Summary

The offline mode spans **four interconnected subsystems** with distinct data sources and failure modes:
- **Routing** — On-device GraphHopper (CH car profile), per-region graphs
- **Maps** — MapLibre vector tiles (OpenFreeMap), zoom 10–14, tile cap 50K
- **Air Quality** — WAQI raster tiles (BYOK token), **online-only** with 6h offline cache
- **Street Names & Places** — OfflineGazetteer (per-region JSON.gz, Arabic-normalized) + saved places

The **offline flag** (`isForceOffline` in DataStore) gates all network calls in `MapViewModel`, `TrackingService`, and repositories. Current implementation has solid foundations but is **Egypt-hardcoded**; needs globalization plus gaps in **coverage detection, error surfaces, cross-subsystem consistency, and test coverage**.

---

## 1. Routing (GraphHopper) — Globalize Regions

### Current State
- `GraphHopperRouteEngine` — singleton, CH-only `car` profile, `ensureLoadedFor(regionId, dir)` with RAM fallback
- `RoutingGraphManager` — **6 Egypt-only regions** (Cairo+Giza, Alexandria, Delta, Canal, Upper, Egypt Full), `.ghz` downloads, per-region `StateFlow<OfflineGraphState>`
- `OfflineRouteSelector.decideOfflineRoute` — pure logic: `Routable(region)` | `SpansUnstored(knownRegion?)`
- `TrackingService.runLocalRoute` — auto-selects covering graph, falls back to GPS line, emits fallback notice
- `MapViewModel.refreshOfflineRoute` — same path for Map UI, emits Arabic notice on `SpansUnstored`

### Gaps / Risks
| Area | Issue | Severity |
|------|-------|----------|
| **Egypt-only regions** | `REGIONS` list hardcoded to Egypt metros; global users get `NoCoverage` | Critical |
| **Graph coverage** | `coversRoute` uses bbox only — roads near border may route outside graph | High |
| **Multi-region trips** | `coveringRegionForAll` requires ALL points in ONE graph — via routes spanning regions silently fall back | High |
| **Engine reload race** | `ensureLoadedFor` closes + reloads on region switch; concurrent calls may race | Medium |
| **Corrupt graph handling** | `properties` file check only — no checksum; bad download → silent failure until retry | Medium |
| **No graph expiry** | Stale graphs never auto-refresh; server-side rebake not detected | Low |

### Repair Tasks
1. **R1** — Replace static `REGIONS` with **dynamic region manifest** (server JSON: id, name, url, sizeMb, bbox). App fetches manifest on first offline-screen open; caches 7 days.
2. **R2** — Add road-network-aware coverage check (sample 3–5 points along straight line, verify `engine.calculateRoute` succeeds) in `decideOfflineRoute`
3. **R3** — For multi-region via routes: split at region boundaries, chain sub-routes (engine reload per segment) — or emit explicit "multi-region not supported" notice
4. **R4** — Serialize `ensureLoadedFor` with existing `mutex` (already present); verify no double-close
5. **R5** — Add SHA-256 checksum in `.ghz` metadata; validate on extract; on mismatch → `Error("corrupt graph")` + auto-delete
6. **R6** — Add `graphVersion` in metadata; on app start, compare with server manifest (HEAD request) → badge "update available"

### Test Plan
| Test | Type | Scenario |
|------|------|----------|
| `routeOfflineWithAutoSelect_coversRoute` | Unit | Origin+dest in same region graph → returns `Routable(region)` |
| `routeOfflineWithAutoSelect_spansUnstored` | Unit | Origin in region A, dest in region B → returns `SpansUnstored(regionC)` |
| `routeOfflineWithAutoSelect_noCoverage` | Unit | Origin in covered area, dest in uncovered (ocean) → returns `SpansUnstored(null)` |
| `engine_loadCorruptGraph_throwsError` | Unit | ZIP with missing `properties` → `Error("no prepared graph")` |
| `engine_regionSwitch_reloads` | Integration | Load region A, then region B → engine reloads, old graph released |
| `trackingService_offlineRoute_fallbackNotice` | Integration | No graph installed → notification shows `[Offline 🛰️]` + fallback toast |
| `mapViewModel_offlineReadiness_needDownload` | UI | Destination in known-but-missing region → chip shows "Download routing data (XX MB)" |
| `graphManager_manifestFetch_parsesGlobalRegions` | Integration | Server manifest → REGIONS list populated for multiple countries |

---

## 2. Maps (MapLibre Offline Tiles) — Already Global

### Current State
- `OfflineMapsViewModel` — `OfflineTilePyramidRegionDefinition` (OpenFreeMap style, zoom 10–14), 50K tile cap, pre-download estimate
- Download flow: estimate → create region → observer with `done` guard → verify in listing → `packDatabase()` (VACUUM) on delete
- `ensureGraphForBounds` — after tile verify, fetches overlapping routing graph (now uses dynamic manifest)
- Presets: Cairo, Giza, Cairo+Giza metro boxes (should become user-defined)

### Gaps / Risks
| Area | Issue | Severity |
|------|-------|----------|
| **Egypt-only presets** | Hardcoded Cairo/Giza bounds; global users need custom bbox picker | Critical |
| **Style mismatch** | Offline tiles use `OPENFREEMAP_STYLE_URL`; online map may use different style → visual jump | High |
| **Tile expiry** | No TTL; stale tiles never refreshed | Medium |
| **Partial download resume** | Cancel → delete partial; no resume support (MapLibre limitation) | Medium |
| **Storage accounting** | `queryOfflineDbSize` scans `filesDir` for `offline`/`mbgl` — may miss external storage | Low |
| **No progress persistence** | Rotation during download loses progress UI (observer detached) | Low |

### Repair Tasks
1. **M1** — Pin exact style URL in `OfflineStyle.kt`; ensure online `MapScreen` uses identical style source
2. **M2** — Replace presets with **custom bbox picker** (map long-press → adjust rectangle → name → download)
3. **M3** — Add `downloadedAt` timestamp in region metadata; on map open, if > 30 days → show "Tiles may be stale" banner
4. **M4** — Persist download progress to DataStore (`downloadProgress_<regionId>`); restore observer on recreate
5. **M5** — Use `context.getExternalFilesDir(null)` for DB size scan (matches graph store)

### Test Plan
| Test | Type | Scenario |
|------|------|----------|
| `offlineMaps_download_verifyIntegrity` | Integration | Download custom bbox → region appears in list with correct name |
| `offlineMaps_oversizeRejected_beforeDownload` | Unit | Bounds > 50K tiles → error "Area too large (X > 50,000)" |
| `offlineMaps_cancel_cleansUp` | Integration | Start download → cancel → no orphan tiles, DB size unchanged |
| `offlineMaps_delete_packsDatabase` | Integration | Delete region → `packDatabase` called, storage reclaimed |
| `offlineMaps_styleParity_onlineVsOffline` | Visual | Same bbox online vs offline → identical rendering (snapshot test) |
| `offlineMaps_customBboxPicker_works` | UI | Long-press map → adjust rect → name → download starts |

---

## 3. Air Quality (WAQI) — 6h Offline Cache

### Current State
- `AqiOverlay.ensureAqiLayer` — `RasterSource` + `RasterLayer` at 0.55 opacity, below symbols
- Tile template: `https://tiles.aqicn.org/tiles/usepa-aqi/{z}/{x}/{y}.png?token=$token`
- **Online-only** — gated by `isOfflineMode` in `MapScreen` (lines 657, 659) and `MapViewModel` (line 728)
- `AqiRepository.fetchAqi` — station feed for destination chip (numeric AQI)
- Settings: BYOK token validation on save (`api.waqi.info/feed/here`)

### Gaps / Risks
| Area | Issue | Severity |
|------|-------|----------|
| **No offline cache** | AQI layer completely unavailable offline — no stale tiles | High |
| **Token validation** | Only on save; expired token → silent blank layer (no error UI) | Medium |
| **No fallback** | When token invalid/missing, layer disappears without notice | Medium |
| **Tile attribution** | WAQI attribution not shown in layer legend | Low |

### Repair Tasks
1. **A1** — Implement **offline AQI cache** (Room): on successful tile fetch, store tile URL + AQI value per tile (key: `z/x/y`), TTL 6h. In offline mode, render cached tiles via custom `TileLoader` → `RasterSource` with local bitmap decode.
2. **A2** — Periodic token re-validation (daily); on 401/403 → clear token, show Settings toast "WAQI token expired"
3. **A3** — When AQI layer toggled ON but token missing/offline → show inline banner "Air quality requires network + WAQI token"
4. **A4** — Add WAQI attribution in `AqiLegendCard` (required by license)

### Test Plan
| Test | Type | Scenario |
|------|------|----------|
| `aqiOverlay_online_tokenValid_showsLayer` | Integration | Valid token + online → raster layer renders |
| `aqiOverlay_offline_noLayer` | Unit | `isOfflineMode=true` → `ensureAqiLayer` returns early, no layer added |
| `aqiOverlay_tokenExpired_silentFailure` | Integration | Expired token → layer blank, no crash (current behavior) |
| `aqiRepository_fetchAqi_cachesResult` | Unit | Successful fetch → Room cache populated per tile |
| `aqiOverlay_offline_usesCache` | Integration | Offline + cache hit (within 6h) → tiles render from cache |
| `aqiOverlay_cacheExpiry_after6h` | Unit | Cache entry > 6h → treated as miss, layer hidden offline |

---

## 4. Street Names & Places (OfflineGazetteer) — Globalize Regions

### Current State
- `OfflineGazetteer` — per-region `json.gz` (~1–3 MB), Arabic normalization (diacritics, alef/taa unification)
- `ensureRegion(region)` — downloads on demand (any network), loads into memory
- `search(query)` — prefix/word-prefix/substring ranking, POI kind boost
- `MapViewModel.searchOffline` — triggered when `isOfflineMode || !NetworkGuards.isOnline`
- Falls back to saved places (Room) + gazetteer hits

### Gaps / Risks
| Area | Issue | Severity |
|------|-------|----------|
| **Egypt-only regions** | Gazetteer regions tied to `RoutingGraphManager.REGIONS` (Egypt only) | Critical |
| **Index not region-tagged** | `delete(regionId)` clears **entire** in-memory index → other regions lost | High |
| **No coverage map** | User doesn't know which areas have gazetteer data | Medium |
| **Arabic normalization gaps** | `normalize` handles common forms but misses some ligatures (e.g., `لا` → `لا` not `ال + ا`) | Medium |
| **Photon dedup coordinate quantization** | 100m cell may merge distinct POIs in dense areas | Low |
| **No background preload** | `preloadInstalled` only called manually; not auto-triggered on graph install | Low |

### Repair Tasks
1. **P1** — Tag `GazEntry` with `regionId`; on `delete(regionId)`, filter index instead of full clear
2. **P2** — Gazetteer regions **mirror routing manifest** (same dynamic region list); `gazetteerBaseUrl` per region
3. **P3** — Add `gazetteerCoverage: Set<String>` in `OfflineRoutingViewModel` (mirrors `installedRegionIds`)
4. **P4** — Extend `normalize`: add `لا` → `ال + ا` decomposition, handle `آ` → `ا`, `ؤ` → `و`, `ئ` → `ي`
5. **P5** — Auto-call `gazetteer.ensureRegion(region)` after `graphManager.download(region)` succeeds (in `OfflineMapsViewModel.finishDownload`)
6. **P6** — Increase dedup precision to 50m (`la * 2000`)

### Test Plan
| Test | Type | Scenario |
|------|------|----------|
| `gazetteer_arabicNormalize_comprehensive` | Unit | "شَارِع التَّحْرِير" → "شارع التحرير"; "ميدان التحرير" → "ميدان التحرير"; "لا إله إلا الله" → "لا اله الا الله" |
| `gazetteer_search_prefixRanking` | Unit | "تحر" → "ميدان التحرير" (prefix) before "شارع التحرير" (substring) |
| `gazetteer_deleteRegion_preservesOthers` | Unit | Load region A + B → delete A → B entries remain |
| `gazetteer_offlineSearch_works` | Integration | Offline mode + region gazetteer loaded → search local name returns hits |
| `mapViewModel_searchOffline_fallsBackToSavedPlaces` | UI | No gazetteer loaded → saved place "Home" appears in results |
| `gazetteer_globalRegions_fromManifest` | Integration | Server manifest → gazetteer regions match routing regions |

---

## 5. Cross-Subsystem Consistency

### Critical Integration Points
| Point | Current | Risk |
|-------|---------|------|
| **Offline flag propagation** | `SettingsDataStore.isForceOffline` → `MapViewModel.isOfflineMode` → `TrackingService.forceOffline` (re-read per fix) | ✅ Consistent |
| **Graph + tile co-download** | `OfflineMapsViewModel.finishDownload` → `ensureGraphForBounds` | ✅ Works but no UI for graph progress |
| **Offline readiness** | `MapViewModel.computeOfflineReadiness` uses `coveringRegionForAll` | ✅ Matches routing decision |
| **AQI + traffic gating** | Both gated by `isOfflineMode` in `MapScreen` | ✅ Consistent |
| **Search fallback** | `MapViewModel.searchOffline` → gazetteer + saved places | ✅ Works |

### Missing Integration
1. **C1** — **Unified "Offline Ready" badge**: Combine map tiles + routing graph + gazetteer for a destination → single "Ready / Partial / Unavailable" chip
2. **C2** — **Offline trip simulation**: `TripSimulationController` should work fully offline (already uses `TrackingService` path)
3. **C3** — **Settings → Offline dashboard**: Single screen showing tile regions, graph regions, gazetteer regions, storage used

---

## 6. Test Infrastructure

### Current State
- No test directory found (`**/test/**/*.kt` empty)
- Dependencies: JUnit, Truth, Turbine, Compose Test, Robolectric (via AndroidX Test)
- Build: `./gradlew assembleDebug --offline` (default)

### Required Test Setup
```kotlin
// app/build.gradle.kts additions
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.espresso.core)
}
```

### Test Categories
| Category | Target | Tools |
|----------|--------|-------|
| **Unit** | `OfflineRouteSelector`, `Gazetteer.normalize`, `matchScore`, `RoutingRegion.contains` | JUnit + Truth |
| **Integration** | `RoutingGraphManager.download`, `GraphHopperRouteEngine.ensureLoaded`, `OfflineGazetteer.fetchRegion` | Robolectric + TempDir |
| **UI** | `OfflineMapsScreen`, `OfflineRoutingScreen`, `MapScreen` offline states | Compose Test + Turbine |
| **E2E** | Full offline trip: download region → start trip → simulate GPS → verify alarm | Instrumented (emulator + GPS mock) |

### CI/CD
- **Nightly only** for instrumented/E2E tests (needs emulator + network control)
- Unit + integration tests on every PR (fast, no emulator)

---

## 7. Prioritized Execution Order

| Phase | Tasks | Est. Effort | Dependencies |
|-------|-------|-------------|--------------|
| **P0 (Critical - Globalization)** | R1, M2, P2 | 3–4 days | None |
| **P1 (High - Core Fixes)** | R2, R3, M1, A1, A2, P1, P4, P5, C1 | 5–7 days | P0 |
| **P2 (Medium - Polish)** | R4, R5, R6, M3, M4, M5, A3, A4, P3, P6, C2, C3 | 3–5 days | P1 |
| **Test Infra** | Test deps, base test classes, CI config (nightly) | 1–2 days | Parallel |

---

## 8. Acceptance Criteria (Definition of Done)

| Subsystem | Criteria |
|-----------|----------|
| **Routing** | Any same-region trip routes offline with road polyline + ETA; cross-region trip shows "Download Region X (XX MB)" notice; global manifest loads on first use |
| **Maps** | Custom bbox download renders identically to online; cancel/delete reclaims storage; stale banner after 30 days; no Egypt presets |
| **Air Quality** | Offline + cached tiles (≤6h) → AQI layer renders; expired token → clear error in Settings; attribution visible |
| **Places** | Offline search returns local names + saved places; delete region A gazetteer keeps region B; global regions from manifest |
| **Cross** | Single "Offline Ready" chip on Map destination: ✅ Map / ✅ Routing / ⚠️ Gazetteer / ❌ AQI (with reason) |

---

## 9. Clarifications Applied
1. ✅ App is **global**, not Egypt-only — routing/gazetteer regions now dynamic from server manifest
2. ✅ AQI cache **6h TTL** confirmed
3. ✅ No bundled gazetteer — all regions downloadable
4. ✅ No specific API level / device matrix — standard emulator matrix
5. ✅ **Nightly CI only** for offline E2E tests; unit/integration on PR

---

*Plan based on codebase analysis as of Oct 2026 (version 10.29.0). All file references use `file_path:line_number` format for traceability.*