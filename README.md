# Wasalna — وصلنا 🚏

> **Wake up before your stop.** A native Android app that tracks your trip in the
> background and fires a full-screen alarm before your destination — so you can
> sleep tight on the bus, train, or with a driver.

**Package:** `com.app.wasalna` · **Language:** Kotlin · **UI:** Jetpack Compose (Material 3)
**License:** Apache-2.0 (see `LICENSE`)

## Features

- 📍 Save favourite places, start tracking with one tap
- ⏰ Full-screen alarm (loud or silent/earphones) before arrival
- 🗺️ Free offline-capable maps (MapLibre + OpenFreeMap, no API key needed)
- 🧭 On-device offline routing (GraphHopper) + online OSRM fallback
- 🔋 Foreground tracking service with battery-optimization guidance
- 🌙 Elderly mode (larger text & touch targets)
- 🧳 Trip history (current / future / past) + impact stats

## Tech stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose + Material 3, Navigation-Compose |
| DI | Hilt (KSP) |
| Local DB | Room (`AppDatabase`) |
| Prefs | DataStore Preferences |
| Maps | MapLibre SDK (primary, OpenFreeMap tiles) + Google Maps Compose (optional fallback) |
| Search | Photon API (keyless) |
| Routing | OSRM online + on-device GraphHopper (offline `.ghz` graphs) |
| Location | Play Services `FusedLocationProviderClient` |
| Background | Foreground `TrackingService` (type `location`) |
| Monetization | Play Billing + AdMob (test IDs by default) |
| Network | OkHttp |

All default services are free / keyless: OpenFreeMap tiles, Photon, OSRM.
Google Maps engine is an **optional fallback** and needs a `MAPS_API_KEY` (see below).

## Getting started

Requirements: JDK 17+, Android SDK (see `app/build.gradle.kts` for `compileSdk`/`minSdk`).

```bash
git clone <your-repo-url> wasalna
cd wasalna
cp local.properties.example local.properties
# Optional: put your Google Maps key in local.properties (fallback engine only).
# The default OSM/MapLibre engine works without any key.
./gradlew assembleDebug
```

| Command | Notes |
|---|---|
| `./gradlew assembleDebug --offline` | default when dependencies are cached |
| `./gradlew assembleDebug` (online) | when adding/updating a dependency |

## Project structure

```
app/src/main/java/com/app/wasalna/
├── data/       # Room, DataStore, repositories, TrackingService, routing
├── di/         # Hilt modules
├── domain/     # models + repository interfaces
├── ui/         # navigation, theme, screens (home, map, trips, settings, …)
└── utils/      # AppLinks (fork config), helpers
```

**Layer rule:** UI → `domain/repository` interfaces → `data/repository` impls.

## Configuration for forks

- `utils/AppLinks.kt` — **change these first**: `SHARE_URL` and `GRAPH_BASE_URL`
  point at the original author's hosting. Point them at your own.
- `local.properties` (gitignored) — optional `MAPS_API_KEY` for the Google Maps
  fallback engine; placeholder `YOUR_API_KEY_HERE` runs in degraded mode.
- AdMob uses Google's public **test** IDs — replace with your own before release.
- Offline routing graphs (`.ghz`) are downloaded on demand, never bundled —
  host your own and update `GRAPH_BASE_URL`.

## Permissions (why each)

| Permission | Why |
|---|---|
| Fine/Coarse + Background location | trip tracking while the screen is off |
| Foreground service (location) | continuous tracking engine |
| Notifications | live trip notification (stop / pause / silent) |
| Battery-optimization exemption | prevents the OS from killing tracking |
| WakeLock | survives screen-off sleep |

## Contributing

Contributor/agent workflow notes live in `AGENTS.md`.

## License

Apache License 2.0 — see `LICENSE`.
