# Wasalna — وصلنا 🚏

> **Wake up before your stop.** A native Android app that tracks your trip in the
> background and fires a full-screen alarm ~2 km before your destination — so you
> can sleep tight on the bus, train, or with a driver.

**Package:** `com.app.wasalna` · **Language:** Kotlin · **UI:** Jetpack Compose (Material 3)
**Current:** `versionName = "10.4.3.9"` · `versionCode = 21` (see `app/build.gradle.kts`)

This README is written **for `opencode` agents** working in this repo. Read it before
touching code. It complements the root `AGENTS.md` (opencode model rules).

---

## 1. Tech stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose + Material 3, Navigation-Compose 2.7.7 |
| DI | Hilt 2.51.1 (KSP) |
| Local DB | Room 2.6.1 (`AppDatabase`, v2) |
| Prefs | DataStore Preferences (`SettingsDataStore`) |
| Maps | **MapLibre SDK 11.1.0** (primary, OpenFreeMap tiles) + Google Maps Compose (fallback engine) |
| Search | Photon API (keyless) via `VelaSearchRepository` |
| Routing | OSRM (`OSRMRepository`, profiles `driving`/`foot`) |
| Location | Play Services `FusedLocationProviderClient` |
| Background | `TrackingService` (foreground, type `location`) + `PowerManager.PARTIAL_WAKE_LOCK` |
| Monetization | Play Billing 6.2.1 (mock) + AdMob banner/interstitial (gated) |
| Network | OkHttp 4.12.0 |

Free / keyless services only: OpenFreeMap tiles, Photon, OSRM. Google Maps engine
needs `MAPS_API_KEY` (see §3).

## 2. Project structure

```
app/src/main/java/com/app/wasalna/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            # Room DB v2: locations + trips
│   │   ├── dao/                      # LocationDao, TripDao
│   │   ├── datastore/SettingsDataStore.kt  # all prefs (see §6)
│   │   └── entity/                   # LocationEntity, TripHistoryEntity
│   ├── mapper/                       # entity ↔ domain
│   ├── repository/                   # Location, Settings, Trip, OSRM, Vela, Nominatim
│   └── service/TrackingService.kt    # FG location engine + rich notification
├── di/                               # DatabaseModule, RepositoryModule, *EntryPoint
├── domain/
│   ├── model/                        # Location, SearchResult, TransportMode, TripHistory
│   └── repository/                   # interfaces (Hilt @Binds in RepositoryModule)
├── ui/
│   ├── navigation/Screen.kt          # sealed routes (single source of truth)
│   ├── theme/                        # Wasalna green #0D7C66 / gold #F2A007
│   └── screens/
│       ├── splash/ · permissions/    # entry flow: Splash → Permissions → Main
│       ├── main/MainContainerScreen.kt  # drawer + bottom bar + inner NavHost
│       ├── home/                     # landing + active-trip widget
│       ├── myplaces/                 # saved places CRUD + tracking start
│       ├── map/                      # MapLibre picker, route, ETA, save
│       ├── trips/                    # history tabs: Current / Future / Past
│       ├── offline/                  # premium offline tile downloads
│       ├── settings/                 # prefs, ringtone, offline entry
│       ├── paywall/                  # First Class (mock purchase → 3-day trial)
│       └── alarm/AlarmActivity.kt    # full-screen alarm + playback
└── utils/                            # TrialLabels, NetworkGuards, Logging
```

**Layer rule:** UI → `domain/repository` interfaces → `data/repository` impls.
Never let a `ViewModel` touch a DAO or `SettingsDataStore` directly.
The one exception: `TrackingService` (plain `Service`, no `@Inject`) reaches
`TripRepository` via `TripRepositoryEntryPoint` (`EntryPointAccessors`).

## 3. Environment & build

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export ANDROID_SDK_ROOT=/opt/android-sdk ANDROID_HOME=/opt/android-sdk
```

| Command | Notes |
|---|---|
| `./gradlew assembleDebug --offline` | **default** — deps are cached; offline is faster & reliable |
| `./gradlew assembleDebug` (online) | only when adding a dependency |
| `./gradlew :app:hiltJavaCompileDebug --offline` | fast check for Dagger/Hilt errors |

- `local.properties` holds `MAPS_API_KEY` (tracked locally, **never push**).
- Google Maps engine is a fallback; the default engine is OSM/MapLibre (keyless).
- Min SDK / target per `app/build.gradle.kts`. `compileSdk 36` (suppress-warning flag
  is already in `gradle.properties`).

### Release (ONLY on explicit order)

`assembleRelease` + zipalign/apksigner with the local keystore, then copy to
`/var/www/guest-downloads/Wasalna-v<version>.apk` (`chmod a+r`) and verify
`https://mohamedadel.cfd/downloads/Wasalna-v<version>.apk` returns **HTTP 200**.
Keystore / `google-services.json` / `.env` files are tracked locally on purpose —
**never push this repo anywhere.**
- **Signing (verified 7 Oct 2026):** no dedicated release keystore exists;
  every published Wasalna APK is signed with the standard Android **debug** key
  (`~/.android/debug.keystore`, `pass:android`) — keep it for update continuity.
  A future dedicated keystore would break updates for existing installs.
- **R8/minify (7 Oct 2026 — hotfix):** release with `isMinifyEnabled=true` +
  `isShrinkResources=true` produced a 47MB APK that **crashed on launch**
  (R8 stripped runtime classes). Minify is OFF until proven rules exist —
  release APKs are ~60MB and match the working debug builds.

## 4. Navigation

`Screen.kt` owns all routes. `MainContainerScreen` hosts the inner `NavHost`
(Home / My Places / Map / Settings / Paywall / TripsHistory / OfflineMaps),
the bottom bar (Home · My Places · First Class), and the drawer
(Settings · Trip History · Share · Rate · Contact · **Your Impact** stats).

Conventions:

- Cross-tab moves use `navigateTab(route)` =
  `popUpTo(startDestination){saveState} + launchSingleTop + restoreState`.
- My Places back arrow → Home tab (drawer not offered there).
- Home widget tap → `map?lat=&lng=&mode=&name=` → `MapViewModel.showTrip(...)`
  (marker + mode + OSRM route follow automatically via the reactive effect).
- `MapScreen` has `BackHandler(onBack)` — hardware back never traps the user.

## 5. Key systems (read before modifying)

**Tracking (`TrackingService`).** Foreground + `location` type (manifest declares
`FOREGROUND_SERVICE_LOCATION`), `START_STICKY`, 12-hour `PARTIAL_WAKE_LOCK`
(released in `onDestroy` — every stop path ends there). Rich notification:
brand color, `Heading to: X` + live `Dist | Speed | ETA 🚗`, actions
Stop / Pause-Resume / Silent-Ring, content tap opens the app. ETA = local
estimate while moving (>5 km/h), OSRM refresh ≤ every 2 min while still.
Destination reached (≤2 km) → `ACTION_DESTINATION_REACHED` broadcast + alarm.
Battery gate: UI checks `isIgnoringBatteryOptimizations` before
`startForegroundService`; if restricted → blocking dialog → system settings.

**Places & duplicates.** `LocationDao.findByName` (case/space-insensitive) +
`findNear` (~55 m). `saveDestination` checks `isDuplicate` → Snackbar
`"This place is already saved!"`, else saves, **clears selection first**, then
navigates (prevents the map-tile-loss glitch).

**ETA reactivity.** `setTransportMode` only flips state; persistence to DataStore
included. `LaunchedEffect(selectedLatLng, transportMode)` in `MapScreen` drives
every OSRM refetch — never call the fetcher directly from setters.

**Trial & paywall.** Mock purchase → `startTrial(3)` (premium + `trialEndTime`).
Drawer + paywall show a live countdown (`TrialLabels.planLabel`,
1-minute ticker). `enforceTrialExpiry()` runs on app start + paywall open.

**Trips.** Service opens an `ACTIVE` row on START (closes stale ones), updates ETA
live, finishes on destroy. Home widget + Current tab read `activeTrip()`; Past
tab + drawer stats (`pastCount`, `totalEtaMinutes`) derive from `PAST` rows.

**Offline maps (premium).** `OfflineMapsViewModel` wraps `OfflineManager`:
list/delete + `OfflineTilePyramidRegionDefinition(style, bounds, z10–16, density)`
with `OfflineRegionObserver` progress. Picker dialog hosts its own `MapView`
(Cairo default) and downloads `visibleRegion.latLngBounds`. Observer detached in
`finishDownload`/`onCleared`; `MapView` fully torn down on dismiss.
Photon/OSRM failures set `lastWasOffline` → UI shows `NetworkGuards.OFFLINE_MESSAGE`
(map keeps rendering from downloaded tiles).

**Ringtone (premium).** `customRingtoneUri` in DataStore; Settings row opens
`ACTION_RINGTONE_PICKER` (persistable URI permission, all in try-catch + toasts).
`AlarmActivity.resolveAlarmUri(premium)` verifies readability, else system default
— used by both loud and earphone paths.

## 6. DataStore keys (`SettingsDataStore`)

`onboardingCompleted · elderlyMode · alarmLeadTimeMinutes · isPremium ·
trialEndTime · silentAlarmEnabled · preferredMapEngine (OSM/GOOGLE) ·
transportMode (WALK/CAR/TRAIN) · customRingtoneUri · isAdaptiveAlarmEnabled ·
isProactiveAlertEnabled · hasSeenAutoStartGuidance`

New key = add `*PreferencesKey` + Flow + setter → expose in `SettingsRepository`
(interface + impl) → consume in a `ViewModel` as `StateFlow`.

## 7. opencode workflow rules (repo-specific)

1. **Models:** all agents on `opencode-go/muse-spark-1.3-contributor`.
   **Moonshot/Kimi models are banned** until further notice.
2. **Review chain:** implement → Oracle review → fix → Momus verification → PASS.
3. **Builds:** code + local `git commit` are free; `assembleRelease`/APK/deploy/
   any push need an **explicit order every time**.
4. **Versioning:** `versionName` = phase (`10.4.3.2`), `versionCode` +1 per published APK;
   file `Wasalna-v<version>.apk`.
5. **Builds:** always `--offline` unless adding a dependency.
6. **Every new `@Inject` repository needs a `@Binds` in `RepositoryModule`** —
   the classic `Dagger/MissingBinding` failure.
7. **DataStore one-shot reads use `.first()`** — never `collect{}` + early return
   (it hangs forever).
8. **Check the AAR, don't guess:** MapLibre APIs (`LocationComponent`,
   `OfflineManager`, observer method names) were verified with
   `javap -cp classes.jar`. Notably: `mapboxTileCountLimitExceeded` (not `on…`),
   and callback arrays are `Array<OfflineRegion>` (no `out` projection).
9. **Smart-cast traps:** nullable `Uri` across lambdas → use `?.let { picked -> … }`.
10. **Enum edits:** last entry ends with `;` when members follow — a stray comma
    breaks KSP compilation.

## 8. Troubleshooting

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

## 9. Version history (recent)

- **10.4.3.9** — on-device GraphHopper routing (Janino-free KmhTimeWeighting,
  CH-only car profile, on-demand `.ghz` download — never bundled)
- **10.4.3.8** — live trip route map (breadcrumbs polyline + destination pin,
  widget + history entry points)
- **10.4.3.7** — bulletproof offline tracking (GPS fallback distance/ETA,
  60s OSRM probe throttle, `[Offline 🛰️]` notification tag)
- **10.4.3.6** — strict-OEM AutoStart guidance (entry flow + settings revisit)
- **10.4.3.4** — proactive silent heads-up (~3 min before alarm, auto-dismissed
  on arrival)
- **10.4.3.3** — adaptive trigger distance (Walk 500m / Car 2km / Train 4km +
  speed stretch) + Wear OS alarm actions + aggressive vibration
- **10.4.3.2** — offline tile downloads + offline search/routing fallback
- **10.4.3.1** — live blue dot, My-Location FAB, trip-to-map view, premium ringtone
- **10.4.3** — trips DB (ACTIVE/PAST), Home widget, history tabs, drawer impact stats
- **10.4.2.1** — WakeLock anti-kill, START_STICKY, battery-optimization gate
- **10.4.2** — rich tracking notification (Stop/Pause/Silent), nav fixes
- **10.4.1** — 3-day trial system, duplicate guard, save-glitch fix, ETA reactivity
- **10.4.0** — transport modes (Walk/Car/Train) + Vela-style ETA + OSRM route line
- **10.3.x** — splash/permissions/onboarding polish, paywall, settings, ads gating

Full log: `git log --oneline`.
