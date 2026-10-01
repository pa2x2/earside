# Earside

Android app that detects and monitors AirPods and Beats via Bluetooth LE: battery levels, popups when
the case opens, reactions such as auto-play/pause, and home screen widgets.

## Fork

Earside is a fork of [CAPod](https://github.com/d4rken-org/capod), forked at `v5.4.0-rc0`. Upstream is
merged regularly with the `merge-upstream` skill, so keep the fork's divergence small:

- Kotlin packages and the namespace stay `eu.darken.capod`. Only the application ID differs:
  `app.pa2x2.earside`, with a `.dev` suffix for debug builds.
- Change an upstream file only when the fork needs it to behave differently. Leave dormant upstream
  code alone rather than tidying it.
- These are removed on purpose. An upstream merge can bring them back, so delete them again:
  `app/src/gplay/`, `app/src/testGplay/`, `app-e2e/`, `tools/`, every `values-<lang>/` translation
  directory (not `values-night/`), `fastlane/`, `crowdin*`, the Pages site (`_config.yml`, `_layouts/`,
  `CNAME`, `PRIVACY_POLICY.md`), `.assets/`, `.pi/`, `.github/FUNDING.yml`, `.github/release.yml`,
  `.github/actions/`, the workflows `code-checks.yml`, `emulator.yml`, `pages.yml`,
  `release-prepare.yml`, `release-tag.yml` and `thumbnail-images.yml`, `version.properties`, `VERSION`,
  and the support contact form (`main/ui/settings/support/contactform/`, `SupportLinks.kt`).
- CAPod's icons, logos, mascots, marketing assets, animations, documentation, store texts and
  translations are not under the GPL. Don't copy them into the fork.

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

### Pro is always unlocked

`UpgradeRepoFoss` reports `isPro = true` whether or not a sponsor record exists. Upstream's Pro gating
code stays in place and must keep working with that value; don't remove the gates themselves.

### Navigation is mid-migration

Navigation3 (`addNavigation3()`) drives current Compose routing, but legacy `androidx.navigation`
helpers still exist (`NavDirectionsExtensions`, `ViewModel3`). Don't assume SafeArgs is fully gone.

## Toolchain

- JDK 21 (`.github/.java-version`). `JAVA_HOME` and `ANDROID_HOME` are set in the user's `~/.zshrc`,
  which non-interactive shells don't load. Export them yourself:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=$HOME/Android/Sdk`.
- Don't run `./gradlew check`. Its full `lint` task fails on findings inherited from upstream that CI
  doesn't look at. CI gates on `lintVitalFossRelease`.

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

## Releases

- The version lives in `app/build.gradle.kts`: `versionName = "X.Y.Z"` and an integer `versionCode`
  that goes up by one for every release.
- `CHANGELOG.md` follows Keep a Changelog. The `release-notes` skill writes it.
- A push to `main` whose `versionName` has a `## [X.Y.Z]` section in `CHANGELOG.md` and no `vX.Y.Z` tag
  yet makes `release.yml` build the signed FOSS APK (`earside-vX.Y.Z.apk`), create a draft GitHub
  release with those notes, and tag the commit. Publishing the draft is a manual step.
- Release signing in CI comes from the repository secrets `SIGNING_KEY` (base64 keystore),
  `KEY_STORE_PASSWORD`, `ALIAS` and `KEY_PASSWORD`. Locally, set `STORE_PATH`, `STORE_PASSWORD`,
  `KEY_ALIAS` and `KEY_PASSWORD`, or put `release.storePath`, `release.storePassword`,
  `release.keyAlias` and `release.keyPassword` in
  `~/.config/projects/app.pa2x2.earside/signing-foss.properties`.
- Never create `v*` tags by hand. `release.yml` creates them, and a stray tag blocks that version's
  release.

## Commits

Single-line messages in upstream's style, `<prefix>(<optional scope>): <Summary>`, under 72
characters, imperative mood, no period, no body, no `Co-authored-by` trailers.

| Prefix | For |
|---|---|
| `fix` | Bug fixes |
| `feat` | New features |
| `refactor` | Restructuring without behavior change |
| `ui` | Visual or layout-only tweaks |
| `chore` | Maintenance, dependency updates, build config, CI |
| `docs` | Documentation |

Example scopes: `fix(widget): ...`, `feat(monitor): ...`, `refactor(popup): ...`.

## Pull requests

Work happens on `upcoming`; `main` is what gets released.

- Title: `<Category>: <Short user-facing summary>`, where the category is `Widget`, `Reaction`,
  `Device`, `General` or `Fix`. Write it for users: no class or library names
- Description: `## What changed` (from the user's perspective, or "No user-facing behavior change" plus
  a short internal description), then `## Technical Context` (one bullet per point: why this approach,
  the root cause for fixes, non-obvious side effects), then optionally `## Review checklist`

## Working as an agent

- Delegate only large, genuinely independent work that parallelizes. Don't spawn a sub-agent to
  verify your own work. Sub-agents don't inherit your context: state the task, paths and whether you
  want research only or implementation too
- Change only what the task needs. Fix root causes rather than working around them
- Don't refactor surrounding code while fixing a bug, and don't add comments or docs to code you didn't
  change
- Don't guess at file paths; search for them
- Put plans, repro screenshots, throwaway scripts and captured logs in `.claude/tmp/` (gitignored),
  not `/tmp`
