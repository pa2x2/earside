# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.2] - 2026-10-08

### 🐛 Fixed

- The battery pill no longer covers fullscreen video and other apps that hide the status bar. It disappears while the status bar is hidden and comes back with it.

## [1.3.1] - 2026-10-08

### 🐛 Fixed

- The in-ear popup and battery pill now go away when the AirPods disconnect from the phone.

## [1.3.0] - 2026-10-07

Based on [CAPod v5.5.0-rc0](https://github.com/d4rken-org/capod/releases/tag/v5.5.0-rc0)

### ✨ Added

- A popup at the top of the screen when you put your AirPods in, showing their battery and listening mode. You can switch the listening mode from it, or tap the AirPods to open their settings. It only appears while they're connected to this phone. Turn it on under Reactions in the device's settings.
- The in-ear popup can shrink into a small battery pill around the front camera that stays until you take the AirPods out. Tap the pill to bring the popup back, swipe it up to hide it, or long-press it to open the device's settings. The pill needs Earside's accessibility service, which doesn't read your screen.
- A reminder to charge the case when you put the AirPods away and the case battery is at or below a level you pick, from 10% to 50%. It goes away once the case charges. Turn it on in the Battery section of the device's settings.
- The status bar can show the battery percentage in place of Earside's icon. For earbuds it shows the lower pod, skipping one that's charging. Turn it on in General settings.

### 🧩 Improved

- The info card in a device's settings shows the battery levels, the time left on each pod, and how long a charging pod needs to fill up. Below that it shows how much of the rated listening time each pod still gets.
- Lock screens that hide notification content now show the battery levels in Earside's notification.
- AirPods 4 and AirPods 5 show pictures of their own models. Before, AirPods 4 showed AirPods 3, and AirPods 4 with ANC and AirPods 5 showed AirPods Pro.

### 🐛 Fixed

- AirPods 5 set up as a different model now switch to AirPods 5 once Earside connects to them.
- AirPods 5 no longer show 1 January 1970 as the date each pod was first paired.

## [1.2.1] - 2026-10-05

### 🐛 Fixed

- A rule that changes an AirPods setting as they connect now waits until the AirPods report that setting. Before, the old value could come back and replace the one the rule set, and the rule had nothing to put back when its condition ended.
- AirPods taken out of the case sometimes connected without sending their battery levels and ear detection, and didn't send them for the rest of the connection. Earside now asks for them again when they don't arrive.

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

[1.3.2]: https://github.com/pa2x2/earside/releases/tag/v1.3.2
[1.3.1]: https://github.com/pa2x2/earside/releases/tag/v1.3.1
[1.3.0]: https://github.com/pa2x2/earside/releases/tag/v1.3.0
[1.2.1]: https://github.com/pa2x2/earside/releases/tag/v1.2.1
[1.2.0]: https://github.com/pa2x2/earside/releases/tag/v1.2.0
[1.1.0]: https://github.com/pa2x2/earside/releases/tag/v1.1.0
[1.0.0]: https://github.com/pa2x2/earside/releases/tag/v1.0.0
