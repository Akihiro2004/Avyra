# Third-party notices

Avyra is built with open-source projects and a few adapted source files. This list is meant to make the important boundaries easy to find. The dependency files and individual source headers are still the most exact record.

## Source included or adapted in this repository

### BitChord

Avyra is a fork of [BitChord](https://github.com/kushagrasinghx/BitChord) by Kushagra Singh, licensed under GPL-3.0. The majority of this repository originates there, including the Innertube client, the playback and download pipelines, the source and lyrics layers, and most of the interface.

Avyra changes the package name, the visual identity and the library layout, and adds in-app updates, onboarding and its own Android Auto browse tree. Those modifications are Avyra's; everything they were built on is BitChord's, and this project remains under GPL-3.0 as a result. Avyra tracks BitChord releases feature by feature — see `docs/UPSTREAM_V1_7_1_INTEGRATION.md` for the most recent one.

### Orchard

The Automix analysis code under `app/src/main/java/com/avyra/music/playback/smart` and `app/src/main/cpp` contains work adapted from [Orchard](https://github.com/SFG5453/Orchard).

Those files keep the Orchard author notices and remain licensed under the GNU Affero General Public License version 3 or later. A copy is in `LICENSES/AGPL-3.0-or-later.txt`. GPLv3 section 13 and AGPLv3 section 13 allow this combination, with the AGPL network-source requirements applying to the combined program.

### Kizzy

The Discord gateway and Rich Presence helper code under `app/src/main/java/com/my/kizzy` comes from [Kizzy](https://github.com/dead8309/Kizzy) and is licensed under GPL-3.0.

### Backdrop

The liquid-glass effect under `app/src/main/java/com/avyra/music/ui/components/backdrop` is vendored from [Kyant0/backdrop](https://github.com/Kyant0/backdrop) v2.0.0, Copyright 2025 Kyant0, licensed under Apache-2.0. The file headers keep that notice.

### FloatingTabBar

`app/src/main/java/com/avyra/music/ui/components/floatingtabbar` is vendored from [compose-floating-tab-bar](https://github.com/elyesmansour/compose-floating-tab-bar) v1.0.1 by Elyes Mansour, licensed under Apache-2.0.

### PoToken

The BotGuard PoToken helpers under `app/src/main/java/com/avyra/music/data/innertube/potoken` follow the WebView approach used by [NewPipe](https://github.com/TeamNewPipe/NewPipe) and [Metrolist](https://github.com/MetrolistGroup/Metrolist), both licensed under GPL-3.0.

### NewPipeExtractor patch

The project includes a patched NewPipeExtractor utility source and builds against [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), licensed under GPL-3.0.

## Models and major libraries

- [Beat This!](https://github.com/CPJKU/beat_this), including the beat model used by Automix, is MIT licensed. Its license is in `LICENSES/Beat-This-MIT.txt`.
- [Open-Unmix PyTorch](https://github.com/sigsep/open-unmix-pytorch), related to the vocal model used by Automix, is MIT licensed. Its license is in `LICENSES/Open-Unmix-MIT.txt`.
- AndroidX, Jetpack Compose, Media3, and the Android Gradle Plugin are provided under their published Android open-source licenses.
- Kotlin, kotlinx.coroutines, and kotlinx.serialization use Apache-2.0 licenses.
- Ktor uses Apache-2.0.
- Coil uses Apache-2.0.
- ONNX Runtime uses MIT.
- Rhino uses MPL-2.0.
- [InnerTubeX](https://github.com/MetrolistGroup/innertubex), which resolves YouTube streams, uses GPL-3.0.
- [SMBJ](https://github.com/hierynomus/smbj), used for SMB network shares, uses Apache-2.0.
- QuickJS-kt, used for module sources, uses Apache-2.0.
- jsoup uses MIT.

Transitive dependencies have their own licenses. If you redistribute an APK, review the resolved dependency graph for that exact release and include any notices required by those versions.

Third-party names, logos, services, album artwork, and music belong to their respective owners. Their appearance in the app does not mean they endorse Avyra.
