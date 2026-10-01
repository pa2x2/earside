# Earside

This is a fork of [CAPod](https://github.com/d4rken-org/capod) to apply changes for my liking. It was
forked from `d4rken-org/capod` at `v5.4.0-rc0` (September 2026), and every change since then is in
this repository's history.

How it differs from CAPod:

* Every feature is available. There is no paid tier.
* English only.
* Released only as APKs on [GitHub](https://github.com/pa2x2/earside/releases). It is not published to
  any app store.
* Installs as `app.pa2x2.earside`, separately from CAPod. Don't run both at once: AirPods accept only
  one app connection, and the two would compete for it.

## Building

```bash
./gradlew assembleFossDebug
```

## Credits

All the hard work is by [Matthias Urhahn (d4rken)](https://github.com/d4rken) and the CAPod
contributors. Please support [the original project](https://github.com/d4rken-org/capod) and the
projects it credits.

## License

The code is licensed under the GNU General Public License v3, see [LICENSE](LICENSE).

CAPod excludes its icons, logos, mascots, marketing assets, animations, documentation, store listings and
translations from that license. Earside replaces or removes them.
