# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-10-03

### ✨ Added

- Device rules change your AirPods' settings when something happens. You set them up for each device under Reactions in its settings. If the AirPods aren't connected when a rule fires, it applies once they connect.
- A rule can fire when the phone joins or leaves a Wi-Fi network, during set hours on the days you pick, while Do Not Disturb is on, during a call, when the AirPods connect, when they go in or out of your ears, or when their battery runs low.
- A rule can set the listening mode, Conversation Awareness, Volume Swipe, Adaptive Audio Noise, Chime Volume, Personalized Volume, ANC with one pod, Microphone, Sleep Detection, and the phone's media volume while the AirPods play it. One rule can change several of these.
- A rule can put the previous setting back when its condition ends, unless you changed that setting yourself in the meantime. Apply now runs a rule right away if its condition already holds, and Earside can notify you each time a rule changes a setting.
- Rules that react to Wi-Fi need location access set to "Allow all the time", because Android only tells apps the Wi-Fi network name when they have it. Earside compares the name with your rules and stores no location.

## [1.1.0] - 2026-10-02

### ✨ Added

- The expanded device notification has buttons to switch listening modes and to turn Conversation Awareness on or off. They show up for headphones that support these settings once Earside has connected to them.
- Earside can update itself from its GitHub releases. When the app opens, it checks for a new version, shows what changed, and downloads and installs it for you. On Android 12 and later, updates after the first one install without asking.
- Check for updates from Settings. In General settings you can turn off the check on launch or switch to the pre-release channel. If you skip a version, the launch check won't offer it again.

### 🔄 Changed

- The Transparency icon in the widget and the Quick Settings tile now matches the one in the app.

## [1.0.0] - 2026-10-01

Based on [CAPod v5.4.0-rc0](https://github.com/d4rken-org/capod/releases/tag/v5.4.0-rc0)

### ✨ Added

- Tapping a device's notification opens the settings for that device.

### 🔄 Changed

- Earside installs as its own app, `app.pa2x2.earside`, next to CAPod. It doesn't replace or update CAPod.
- Settings and device profiles don't carry over from CAPod. Set up your devices again, including their identity and encryption keys.
- Uninstall or disable CAPod after you switch. Your headphones accept advanced settings from one app at a time, and with both installed every popup and notification shows up twice.
- Every feature is unlocked. There is nothing to upgrade.
- The app is in English only.
- New app icon, logo and splash screen. Launchers that tint icons get a themed version.
- The app uses your device's Material You colors by default.
- The changelog link in settings opens Earside's releases on GitHub.

### 🗑️ Removed

- CAPod's contact form, Discord, issue tracker, wiki, translation project, privacy policy and sponsor links.

[1.2.0]: https://github.com/pa2x2/earside/releases/tag/v1.2.0
[1.1.0]: https://github.com/pa2x2/earside/releases/tag/v1.1.0
[1.0.0]: https://github.com/pa2x2/earside/releases/tag/v1.0.0
