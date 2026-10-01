# Earside

Android app that detects and monitors AirPods and Beats via Bluetooth LE: battery levels, popups when
the case opens, reactions such as auto-play/pause, and home screen widgets.

## Project structure

One app module, `app/`, with a single `foss` flavor. Source sets: `main`, `foss`, `debug`, `test`,
`testFoss`, `screenshotTest`.

| Path | Contains |
|------|----------|
| `app/src/main/java/` | Compose screens, services, receivers, monitor, bluetooth, models |
| `app/src/foss/java/` | Flavor code: the upgrade flow, which always reports Pro |
| `app/src/main/res/` | Layouts, drawables, strings (English only) |
| `app/src/test/`, `app/src/testFoss/` | Unit tests |
| `app/build.gradle.kts` | App build config, version, signing, dependencies |
| `buildSrc/` | `ProjectConfig` (namespace, application ID, SDK levels) and shared Gradle helpers |
| `app/src/debug/java/.../screenshots/` | Screenshot preview content |

## Architecture

Invariants worth knowing before you touch device state, the AAP stack, or the upgrade flow.

### BLE vs AAP

Two independent data paths. Which one a feature can use decides whether it is even possible.

| | BLE (advertisements) | AAP (L2CAP session) |
|---|---|---|
| Direction | Read-only, passive | Bidirectional commands + events |
| Prerequisite | `BLUETOOTH_SCAN` on Android 12+, Bluetooth/location permissions below | Bonded + `BLUETOOTH_CONNECT` + active L2CAP socket |
| Data | Battery, case open, in-ear, pod model | Settings, ANC control, press controls, stem events, device info |
| Availability | Any pod in range | Only your own paired pods |

A figure BLE never advertises cannot be obtained without a bonded AAP session, and anything requiring
a write is AAP-only.

### `DeviceMonitor` is the state merge boundary

`DeviceMonitor` (singleton) `combine`s four live sources — `BlePodMonitor.devices`,
`AapConnectionManager.allStates`, `BluetoothManager2.connectedDevices` (supplies `isSystemConnected`),
and `DeviceProfilesRepo.profiles` — then merges `DeviceStateCache` on top, deliberately after the
combine so cache writes don't feed back into it.

- Unified device state comes from `DeviceMonitor.devices`. Don't assemble your own from `BlePodMonitor`
- Commands go through `AapConnectionManager.sendCommand(...)`. ViewModels legitimately inject it
  (`OverviewViewModel`, `DeviceSettingsViewModel`, `PressControlsViewModel` all do)
- Nothing outside the AAP engine touches `AapConnection` (the L2CAP socket wrapper) directly
- `TroubleShooterViewModel` reaching into `BlePodMonitor` for raw diagnostic scans is an intentional
  exception, not a pattern to copy

Because the cache is merged in, a `PodDevice` may carry data while the device is out of range.
Presence in the flow does not imply a live connection.

### `AapConnectionManager` owns sessions

It holds every open AAP session keyed by `BluetoothAddress`. Consumers call `sendCommand(...)` and
observe `allStates`. The stack under `pods/core/apple/aap/` splits into `protocol/` (pure data) and
`engine/` (per-connection state machine). The glue in `monitor/core/aap/` wires it into the
foreground service and persists learned settings and session keys across restarts.

The pods accept one AAP session at a time, so Earside and CAPod running together compete for it.

### Navigation is mid-migration

Navigation3 (`addNavigation3()`) drives current Compose routing, but legacy `androidx.navigation`
helpers still exist (`NavDirectionsExtensions`, `ViewModel3`). Don't assume SafeArgs is fully gone.

## Toolchain

- JDK 21 (`.github/.java-version`). `JAVA_HOME` and `ANDROID_HOME` are set in the user's `~/.zshrc`,
  which non-interactive shells don't load. Export them yourself:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/Sdk`.

## Validation

Run before calling a change done:

```bash
./gradlew assembleFossDebug testFossDebugUnitTest
```

`build.yml` runs on every push to `upcoming` and on pull requests that change more than Markdown: `testFossDebugUnitTest`,
`lintVitalFossRelease` and `assembleFossDebug`.

## Conventions

- MVVM + Hilt + Coroutines. Follow the patterns in the file you're in
- Use string resources for user-facing text. The app is English only: add strings to
  `values/strings.xml` and nowhere else
- Unit tests use JUnit 5, kotest assertions and mockk, and extend `testhelpers.BaseTest`, not the
  Android defaults
