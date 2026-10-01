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

## Comments

A comment earns its place by telling the reader something the code can't: why it's done this way, a
constraint or platform quirk it works around, a bug it prevents, or what a non-obvious value means.
If deleting the comment loses nothing a careful reader couldn't get from the code, delete it.

- **Don't restate the code.** No `/** Sends a command to the pod. */` over `sendCommand`, no
  `// Reconnect when Bluetooth turns on` over a `BroadcastReceiver`, no
  `/** Last error, as a user-facing message. */` over `error: String?`.
- **Don't narrate names or types.** If a property, parameter or function name plus its type already
  says it, leave it bare. Document a property only when its meaning isn't obvious: units, what `null`
  stands for, who sets it, what it must never be.
- **Don't write file headers that only name the file.** "Tests for the profiles repo" or "AAP
  connection handling" add nothing. A header is worth it when it explains a design: how the parts fit,
  what the class owns, a contract callers rely on.
- **Keep comments true.** When you change code, update or delete the comments it touches. A stale
  comment is worse than none.

These apply to code you write or change. Leave the comments in upstream code you aren't touching alone.

## Writing tests

A change does not come with tests by default. Most features and fixes need none: compilation, lint,
and trying the change on a phone with real AirPods catch most mistakes. Tests written just because a
change was made grow the suite without catching anything.

These rules are for tests you add. The existing tests come from upstream CAPod; leave them alone
unless your change breaks one.

Tests live in `app/src/test/` (or `app/src/testFoss/` for flavor code), mirroring the production
package.

### When to write one

Write a test only when all three hold:

1. **The bug would go unnoticed.** Compilation, lint and using the feature once on a device wouldn't
   catch it. Think races and reconnects of the AAP session, what survives a service or app restart
   (`DeviceStateCache`, learned settings, session keys), BLE advertisements and AAP payloads from real
   devices, pod models you don't own, and error paths you can't easily trigger by hand.
2. **The bug is likely.** Either it has happened before, or the logic is subtle enough that a
   reasonable edit would break it. "What if someone deletes this line" doesn't count.
3. **Nothing else catches it.** No existing test, type or lint check already covers it.

Name the bug before you write the test. "Checks that device caching works" is not a bug. "A cached pod
snapshot stays on the old profile after its address moves to another identity" is. If you can only
describe what the code does, not how it would go wrong, don't write the test.

### How to write one

- **One test per bug, not per branch.** Don't list every pod model, enum value or AAP message type.
  Cover only the cases that differ from the obvious.
- **Assert related things together.** One test can check that a dropped AAP session keeps the cached
  battery levels *and* reports the device as disconnected. Don't split those into separate tests with
  the same setup.
- **Test where the logic lives.** Call the parser, repo or engine that owns it, not a ViewModel or
  composable that uses it.
- **No new test infrastructure.** If a test needs a new base class, fake repository or
  Robolectric/Compose harness, it is probably the wrong test. Move the logic into a plain function and
  test that. Prefer real objects and the existing helpers (`FakeDataStoreValue`, `BaseBlePodsTest`,
  `BaseAapSessionTest`) over mocks.
- **Fakes keep the real guarantees.** A mock or fake must only produce what the real collaborator can.
  Don't manufacture impossible inputs to exercise a consumer's normalization; test that invariant
  where it is owned.
- **Re-read before finishing.** Delete any test the rest of the change made redundant.

### Don't write these

- **Library and platform behaviour.** Don't test that kotlinx.serialization round-trips a class, that a
  `StateFlow` holds what was emitted, that a Compose `onClick` fires, or that DataStore stores a value.
  Those libraries have their own tests.
- **Setter round-trips.** `setting.value(true)` followed by `setting.value() shouldBe true` only
  restates the setter. Test the logic instead: ordering, dedupe, merging, error handling, what
  persists.
- **Constants and static config.** Asserting default setting values, a pod model's fields, enum
  entries, or a Hilt module's bindings just copies the source. Test the behaviour that uses the value.
- **Tautologies.** If the expected value is computed the same way the implementation computes it, such
  as building the expected AAP bytes with the same encoder, the test can't fail. Compare against an
  independent source: a literal, or a payload captured from a real device.
- **Tests of a mock.** Calling a function and then `coVerify`-ing that it forwarded to a mocked
  `AapConnectionManager.sendCommand` only checks the mock. Pass-through wrappers and adapters don't
  need tests. Assert call order only when ordering is itself the behaviour.
- **UI render tests.** Compose and Robolectric tests need harnesses, break on every layout change, and
  miss what actually goes wrong on a device. If a composable has logic worth testing (enabled rules,
  filtering, which option is selected), move it into a plain function beside it and test that. Check
  the UI itself on a device.
- **Implementation snapshots.** Tests that pin intermediate state, private control flow, or exact
  strings and serialized layouts that nothing depends on. Pin a format only when it is a compatibility
  requirement: the BLE and AAP wire formats, or stored data read back after an update.
- **Duplicates.** If a mapping is covered by a unit test, one wiring test at the next layer is enough.
  Don't re-test every branch there.
