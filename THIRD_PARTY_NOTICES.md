# Third-party notices

Avyra is built with open-source projects and a few adapted source files. This list is meant to make the important boundaries easy to find. The dependency files and individual source headers are still the most exact record.

## Source included or adapted in this repository

### Orchard

The Automix analysis code under `app/src/main/java/com/avyra/music/playback/smart` and `app/src/main/cpp` contains work adapted from [Orchard](https://github.com/SFG5453/Orchard).

Those files keep the Orchard author notices and remain licensed under the GNU Affero General Public License version 3 or later. A copy is in `LICENSES/AGPL-3.0-or-later.txt`. GPLv3 section 13 and AGPLv3 section 13 allow this combination, with the AGPL network-source requirements applying to the combined program.

### Kizzy

The Discord gateway and Rich Presence helper code under `app/src/main/java/com/my/kizzy` comes from [Kizzy](https://github.com/dead8309/Kizzy) and is licensed under GPL-3.0.

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
- jsoup uses MIT.

Transitive dependencies have their own licenses. If you redistribute an APK, review the resolved dependency graph for that exact release and include any notices required by those versions.

Third-party names, logos, services, album artwork, and music belong to their respective owners. Their appearance in the app does not mean they endorse Avyra.
