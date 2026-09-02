# Changelog

This file records user-facing changes. Development details still live in the Git history.

## Unreleased

## 1.0.5 - 2026-08-31

### Fixed

- Songs played the wrong recording. JioSaavn shipped ranked above YouTube, which meant every track you tapped was offered to it first and whatever it returned was played instead — and a match is made on title, artist and runtime alone, which a cover, a re-recording or an unrelated song of the same name all satisfy. The row went on showing the right title, artist, artwork, runtime and lyrics while the audio was somebody else's. JioSaavn is now tried only when YouTube cannot serve a track, so nothing stands in for the track you chose. Sources you add yourself are unaffected and still take priority.

- A song that plays a different recording than the one on its row is now caught while it is playing, whatever caused it. The row states how long the track runs and the player knows how long what it is playing runs; two recordings of one song are almost never the same length, so when those disagree the audio is not what was asked for. The saved copy is thrown away and the track is fetched again from the top. Once per track, so a row whose own stated length is simply wrong cannot put it in a loop.

### Changed

- Settings now slides in from the edge and back out the way it came, instead of fading. A fade has no direction, so opening Settings and closing it looked the same; the page underneath now shifts and dims as the new one covers it, and returns as it uncovers. The screens inside Settings push the same way.
- Closing the app from the recent apps list now stops playback. It always could, but the switch for it was off by default and easy never to find; it is on by default now and still in Settings for anyone who wants music to carry on. Playback in a car is unaffected — a swipe on the phone is ignored while anything in a car is listening, and the queue is saved before stopping so the car picks up where it left off.

### Fixed

- Some downloaded songs played a different recording — the right title, artist and album on the row, someone else's audio every time. A download is saved as `Artist - Title`, and a file already in Music under that name was taken to be the same track, so a remaster, a live take, or the same song from another album quietly adopted the first one's audio and recorded itself against it. A file is now only reused when its length agrees with the track's as well, and a name already taken by a different recording is left alone rather than written over.
- A streamed track could play a different recording than the one on the row, on and off rather than every time. Audio is cached per track, and the entry it goes in was chosen from whether the settings ranked another source above YouTube — a fact about the settings, not about the track. A play that ended up on YouTube after all wrote its audio into the entry holding a substituted copy, so the next play of either got whichever had been written last.
- Downloads already recorded against the wrong file are checked once on this update and forgotten, so the affected songs play correctly again after being downloaded a second time.

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
