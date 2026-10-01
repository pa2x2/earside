<div align="center">

<img src="./.github/assets/earside.svg" alt="Earside logo" width="104" />

# Earside

### Your AirPods, at home on Android.

Battery levels, a popup when you open the case, and the controls Android leaves out.

[Download](https://github.com/pa2x2/earside/releases/latest)

[![Latest release](https://img.shields.io/github/v/release/pa2x2/earside?display_name=tag&sort=semver&label=release)](https://github.com/pa2x2/earside/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white)
[![License](https://img.shields.io/github/license/pa2x2/earside)](./LICENSE)

</div>

Android connects to AirPods like any other headphones and stops there. Earside reads what they broadcast and talks to the pair you own, so you get what an iPhone would show you, and a few things it wouldn't.

It works with AirPods, AirPods Pro, AirPods Max and most Beats headphones.

## Built on CAPod

Earside would not exist without [CAPod](https://github.com/d4rken-org/capod) by Matthias Urhahn (d4rken) and the people who contribute to it. This is an independent fork, made to apply changes for my liking. It is not an official CAPod project, and problems with it belong here, not with CAPod.

It was forked from `d4rken-org/capod` at `v5.4.0-rc0` (September 2026), and every change since then is in this repository's history.

## What you get

**Battery at a glance.** Each pod and the case show their own level and charging state, in the app, in a notification and on a home screen widget. A notification tells you when charging is done.

**A popup when you open the case.** Flip the lid and the battery levels pop up, the way they do on an iPhone.

**Music that follows your ears.** Take a pod out and playback pauses. Put it back and it resumes, but only if Earside paused it. It can pause when you fall asleep, too.

**Connect without the settings menu.** Earside can ask Android to connect when your AirPods come into range, when you open the case, or when you put them in.

**The controls Android leaves out.** For your own paired AirPods, switch noise control modes from the app or a widget, and change what a press on the stem does.

**Everything, free.** There is no paid tier. Every feature is available from the start.

## Getting started

1. Install `earside-<version>.apk` from the [releases page](https://github.com/pa2x2/earside/releases).
2. Open Earside and grant the permissions the dashboard asks for.
3. In the device profile Earside created, select your paired AirPods. That turns on background monitoring, reactions, and the settings that need a direct connection.

Earside and CAPod can be installed side by side, but don't run both at once. AirPods accept only one app connection, and the two would compete for it.

## Privacy

Earside has no internet permission, so it cannot send anything anywhere. It reads what your headphones broadcast over Bluetooth and keeps its settings on your phone. A debug log stays on the phone unless you share it yourself.

## Disclaimer

Earside is not affiliated with or endorsed by Apple. AirPods and Beats are trademarks of Apple Inc.

## License

GPL-3.0. See [LICENSE](LICENSE).

CAPod's own icons, logos, mascots, marketing assets, animations, documentation, store listings and translations are excluded from that license, and Earside does not use them.
