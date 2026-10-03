<div align="center">

<img src="./.github/assets/earside.svg" alt="Earside logo" width="104" />

# Earside

[Download](https://github.com/pa2x2/earside/releases/latest)

[![Latest release](https://img.shields.io/github/v/release/pa2x2/earside?display_name=tag&sort=semver&label=release)](https://github.com/pa2x2/earside/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white)
[![License](https://img.shields.io/github/license/pa2x2/earside)](./LICENSE)

</div>

## Built on CAPod

Earside would not exist without [CAPod](https://github.com/d4rken-org/capod) by Matthias Urhahn (d4rken) and the people who contribute to it. This is an independent fork, made to apply changes for my liking. It is not an official CAPod project, and problems with it belong here, not with CAPod.

It was forked from `d4rken-org/capod` at `v5.4.0-rc0` (September 2026), and every change since then is in this repository's history.

## Privacy

Earside reads what your headphones broadcast over Bluetooth and keeps its settings on your phone. A debug log stays on the phone unless you share it yourself.

Device rules that react to Wi-Fi need location access, because Android only tells apps the name of the Wi-Fi network you're on when they have it, and "Allow all the time" so the rules work in the background. Earside only compares the network name with your rules. It doesn't track your location or keep a list of the networks you join, though network names can appear in a debug log.

The internet is used only for app updates: Earside asks GitHub for the latest releases of this repository and downloads the update you choose to install. It checks when the app opens, which you can turn off in the settings. The request names the Earside version and nothing about you or your headphones.

## Disclaimer

Earside is not affiliated with or endorsed by Apple. AirPods and Beats are trademarks of Apple Inc.

## License

GPL-3.0. See [LICENSE](LICENSE).

CAPod's own icons, logos, mascots, marketing assets, animations, documentation, store listings and translations are excluded from that license, and Earside does not use them.
