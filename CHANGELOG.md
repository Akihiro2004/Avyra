# Changelog

This file records user-facing changes. Development details still live in the Git history.

## Unreleased

## 1.0.4 - 2026-08-31

### Added

- Android Auto. Avyra now appears in the car and is browsed there directly: Downloads first, so it works with no signal, then Recent, and — when signed in — Library and Quick picks. Tapping a song plays the list it came from rather than that song alone, voice search reaches downloads before the network, and the steering-wheel play button resumes the last queue even when the app was never opened on the phone.

## 1.0.3 - 2026-08-31

### Changed

- Redesigned the update screen. It was built as a system alert — the narrow, two-button kind meant for a single sentence — and was being asked to show a whole release changelog, so the notes came out cramped and their headings were set larger than the screen's own title. It is now a full-screen page in the shape of iOS Software Update: the version and its state at the top, the release notes in a panel sized to be read, and the actions along the bottom.
- A download now shows its percentage as well as its progress bar.

## 1.0.2 - 2026-08-31

### Fixed

- In-app updates failed partway through the download, usually reporting "software caused connection abort". The download ran on the screen that started it, so anything that rebuilt that screen — leaving the app during a download long enough for Android to reclaim the activity, or a rotation — cancelled the transfer and closed the connection out from under it.
- Asking for an update from Settings started one download and opened a dialog offering a second; taking the dialog up on it deleted the first download's half-finished file while it was still being written.
- An interrupted update download now resumes from where it stopped and retries a few times, rather than failing outright. A hundred-megabyte download over mobile data rarely survives being restarted from zero on every dropped connection.
- An update whose download ended early is now reported as a failed download instead of being handed to the installer as a corrupt package.

## 1.0.1 - 2026-08-31

### Fixed

- Some songs played a completely different recording than the one on the row, every time. A track offered to a higher-ranked source was matched on title and artist alone, so another recording of the same song under the same name could be served in its place — and once its audio was cached, every later play repeated it. A replacement now has to agree on runtime as well, and a row that carries no runtime is left with the source it came from.
- The equalizer's fader handle and its fill were invisible as soon as a band left the centre.
- Moving one equalizer band silently reset the other nine, so a curve could never be built up across several bands.
- Equalizer presets took back exactly as much level as they added, which made every one of them a net cut — "Bass" left the bass where it was and turned everything else down. Presets now keep half their boost.

### Changed

- Redesigned the settings screen: grouped panels with coloured icon tiles, quieter section headings, and rows aligned to a single text column.
- Finished the active Avyra package, source namespace, native library, resources, and internal identity migration.
- Added compatibility readers for settings, source configuration, account storage, backups, embedded lyrics, and downloads made by earlier builds.
- Reworked the public README and added contribution, security, support, conduct, trademark, and third-party documentation.
- Added Android CI, issue forms, pull request guidance, and dependency update configuration.

### Removed

- Removed a stale maintainer funding link.
- Removed an unused hidden screenshot left from the old project identity.

## 1.0.0 - 2026-08-30

- First Avyra release.
- Introduced the independent `com.avyra.music` application ID and Avyra release signing identity.
- Added the Avyra visual system and the initial public release build.
