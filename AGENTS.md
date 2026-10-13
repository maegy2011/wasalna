# AGENTS.md — Wasalna contributor notes (internal)

This file holds the maintainer workflow. Public overview lives in `README.md`.

## Environment & build

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export ANDROID_SDK_ROOT=/opt/android-sdk ANDROID_HOME=/opt/android-sdk
```

| Command | Notes |
|---|---|
| `./gradlew assembleDebug --offline` | **default** — deps are cached; offline is faster & reliable |
| `./gradlew assembleDebug` (online) | only when adding a dependency |
| `./gradlew :app:hiltJavaCompileDebug --offline` | fast check for Dagger/Hilt errors |

- `local.properties` holds `MAPS_API_KEY` (gitignored, **never commit**).
- Google Maps engine is a fallback; the default engine is OSM/MapLibre (keyless).
- Min SDK / target per `app/build.gradle.kts`.

### Release (ONLY on explicit order)

- Release signing/deploy steps are maintainer-run and never part of a public PR.
- `assembleRelease` + publish flow needs an **explicit order every time**.
- R8/minify note (Oct 2026): release with `isMinifyEnabled=true` produced a
  crashing APK (R8 stripped runtime classes). Minify stays OFF until proven rules exist.

## Navigation

`Screen.kt` owns all routes. `MainContainerScreen` hosts the inner `NavHost`
(Home / My Places / Map / Settings / Paywall / TripsHistory / OfflineMaps),
the bottom bar (Home · My Places · First Class), and the drawer
(Settings · **Offline Maps** · Trip History · Share · Rate · Contact · **Your Impact** stats).

Conventions:

- Cross-tab moves use `navigateTab(route)` =
  `popUpTo(startDestination){saveState} + launchSingleTop + restoreState`.
- My Places back arrow → Home tab (drawer not offered there).
- Home widget tap → `map?lat=&lng=&mode=&name=` → `MapViewModel.showTrip(...)`
  (marker + mode + OSRM route follow automatically via the reactive effect).
- `MapScreen` has `BackHandler(onBack)` — hardware back never traps the user.

## Key systems (read before modifying)

**Design system (Oct 2026).** `ui/theme/Color.kt` holds a full M3 token
palette (light/dark + elderly high-contrast) — coolors navy set
(#00171F / #003459 / #007EA7 / #00A8E8 + white); elderly high-contrast
schemes untouched. `Theme.kt` sets app-wide `WasalnaShapes` (16 controls /
20 cards / 24 sheets / 28 large) — rely on theme defaults instead of local
shapes. `AppChrome.kt` is the single source for TopBar (optional `colorful`
gradient mode) / BottomBar (**icons-only**, start-trip = 44dp pulsing disc
inside the center slot) / drawer items ( 24dp pill,
animated icon tint) / brand header (tri-gradient + animated plan pill).

**Tracking (`TrackingService`).** Foreground + `location` type, `START_STICKY`,
12-hour `PARTIAL_WAKE_LOCK` (released in `onDestroy`). Rich notification:
`Heading to: X` + live `Dist | Speed | ETA`, actions Stop / Pause-Resume /
Silent-Ring. ETA = local estimate while moving (>5 km/h), OSRM refresh ≤ every
2 min while still. Destination reached (≤2 km) → broadcast + alarm.
Battery gate: UI checks `isIgnoringBatteryOptimizations` before
`startForegroundService`; if restricted → blocking dialog → system settings.

**Places & duplicates.** `LocationDao.findByName` (case/space-insensitive) +
`findNear` (~55 m). `saveDestination` checks `isDuplicate` → Snackbar,
else saves, **clears selection first**, then navigates (prevents map-tile-loss glitch).

**ETA reactivity.** `setTransportMode` only flips state; `LaunchedEffect(selectedLatLng,
transportMode)` in `MapScreen` drives every OSRM refetch — never call the fetcher
directly from setters.

**Trial & paywall.** Mock purchase → `startTrial(3)` (premium + `trialEndTime`).
Drawer + paywall show a live countdown (`TrialLabels.planLabel`, 1-minute ticker).
`enforceTrialExpiry()` runs on app start + paywall open.

**Trips.** Service opens an `ACTIVE` row on START (closes stale ones), updates ETA
live, finishes on destroy. Home widget + Current tab read `activeTrip()`; Past
tab + drawer stats derive from `PAST` rows.

**Map trip sheet.** Picking a destination shows a primary "Start trip"
CTA in the sheet that funnels through `TripStarter.startRealTrip`
(pre-flight gate). The bottom-bar play action still just opens the map.

**Air quality (WAQI BYOK).** Token validates live on save
(`api.waqi.info/feed/here` → status ok/error/null=offline) — Settings
toasts the verdict. Tile layer: `AqiOverlay.kt` mirrors the traffic
overlay pattern (raster below symbols).

**Offline maps (premium).** `OfflineMapsViewModel` wraps `OfflineManager` with
`OfflineTilePyramidRegionDefinition(style, bounds, z10–16, density)`.
Photon/OSRM failures set `lastWasOffline` → UI shows offline message
(map keeps rendering from downloaded tiles).

**Ringtone (premium).** `customRingtoneUri` in DataStore; `AlarmActivity.resolveAlarmUri`
verifies readability, else system default.

## Workflow rules

1. Code + local `git commit` are free; release/APK/deploy/any push need an
   **explicit order every time**.
2. Versioning: `versionName` = phase, `versionCode` +1 per published APK.
3. Builds: always `--offline` unless adding a dependency.
4. Every new `@Inject` repository needs a `@Binds` in `RepositoryModule` —
   the classic `Dagger/MissingBinding` failure.
5. DataStore one-shot reads use `.first()` — never `collect{}` + early return.
6. MapLibre APIs were verified with `javap -cp classes.jar`, don't guess:
   `mapboxTileCountLimitExceeded` (not `on…`), callback arrays are
   `Array<OfflineRegion>` (no `out` projection).
7. Nullable `Uri` across lambdas → use `?.let { picked -> … }`.
8. Enum edits: last entry ends with `;` when members follow.

## Troubleshooting

| Symptom | Cause → Fix |
|---|---|
| `Dagger/MissingBinding` | missing `@Binds` in `RepositoryModule` |
| `Expecting ';'` in enum | trailing `,` instead of `;` |
| `Argument type mismatch: Uri?` | smart-cast lost in lambda → `?.let` |
| `does not implement abstract member 'onList'` | signature must be `Array<OfflineRegion>?` |
| `Unresolved 'mapboxTileCount…'` | real name is `mapboxTileCountLimitExceeded` |
| Suspend hang on prefs read | `collect{}` without termination → use `.first()` |
| Map tiles lost / camera jump after save | selection must be cleared **before** navigating |
| ETA stale after mode switch | route must be effect-driven, not setter-driven |
| Stale ACTIVE trip after kill | `closeOpenTrips()` on START + Home init |

## Version history (recent)

- **10.4.3.9** — on-device GraphHopper routing (CH-only car profile, on-demand `.ghz`)
- **10.4.3.8** — live trip route map (breadcrumbs polyline + destination pin)
- **10.4.3.7** — bulletproof offline tracking (GPS fallback, `[Offline]` tag)
- **10.4.3.6** — strict-OEM AutoStart guidance
- **10.4.3.4** — proactive silent heads-up (~3 min before alarm)
- **10.4.3.3** — adaptive trigger distance + Wear OS actions + vibration
- **10.4.3.2** — offline tile downloads + offline search/routing fallback
- **10.4.3.1** — live blue dot, trip-to-map view, premium ringtone
- **10.4.3** — trips DB, Home widget, history tabs, drawer stats

Full log: `git log --oneline`.
