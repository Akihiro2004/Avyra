# BitChord v1.5.1 compatibility review

Upstream reviewed: `kushagrasinghx/BitChord`, branch `v1.5.1`, head `5c9ab95`.

Avyra and BitChord no longer share a mergeable Git history. This update is
therefore a feature-by-feature port, not a branch merge. The compatibility
rules are:

- keep `com.avyra.music`, Avyra labels, links, update channel and signing;
- keep `Music/Avyra` plus the legacy BitChord folder reader;
- keep Avyra's YouTube-canonical identity, source ordering and wrong-recording
  duration checks;
- keep the dual-player Automix pipeline, spatial processor, equalizer, Canvas,
  Android Auto and Avyra Replay behavior;
- never replace a user-visible external download with private app storage by
  default.

## Ported or already covered

| Upstream change | Avyra decision |
| --- | --- |
| Paged search results | Ported as first-page-fast search with lazy continuation loading. Added All and Videos filters while retaining Avyra's Songs default and automatic video-to-audio preference. |
| Paged library feeds and playlist picker | Ported with bounded continuation traversal and identity deduplication. |
| Better quality-upgrade selection | Ported. Patient background upgrades wait for all sources and choose the best; first-note playback still takes the first acceptable stream. |
| HLS playback detection | Ported for resolved replacement/audition streams by explicitly selecting Media3's HLS source type. |
| Download fixes | Adapted. Direct source files are probed so an HLS manifest mislabeled as FLAC falls back to YouTube. Dolby containers are storable. Existing Avyra naming, duration audit, external folder and Wi-Fi/quality behavior remain. |
| Dolby Atmos format support | Adapted into Avyra's stream metadata and stats label. Existing Android system-panel guidance remains. |
| Persistent shuffle and repeat | Ported through Avyra's visible-queue shuffle implementation and both crossfade players. |
| Long-session autoplay handoff | Already covered by Avyra's handoff listener, seed reset and current-track reload path. |
| JioSaavn wrong-song protection | Already superseded by Avyra's YouTube-canonical identity, strict duration matching and runtime mismatch rejection. |
| Safer Automix Opus analysis | Adapted. YouTube-backed analysis now ignores substitute/upgrade cache keys so a different rendition cannot train transition timing for the canonical track. |
| Primary-artist scrobbling | Ported as separate opt-in switches for Last.fm and ListenBrainz. Joint credits remain the default. |
| Lyrics focus blur | Ported as an opt-in-compatible setting that also respects Reduce dynamic blur, reduced animation and manual lyric browsing. |
| Local non-music filtering | Adapted. Short clips, alarms, ringtones, recordings and voice-note folders are hidden by default; unlike upstream, long WAV music remains supported. |
| Replay light theme | Ported using the active Material color scheme while retaining Avyra's Replay content and sharing flow. |
| Explore moods, genres and charts | Already covered by Avyra's combined `FEmusic_explore` and `FEmusic_charts` feed. The upstream replacement screen would remove Avyra's chart shelves, so it was not substituted. |

## Deliberately not ported

| Upstream change | Reason |
| --- | --- |
| Remove spatial audio | Rejected. Spatial widening is an intentional Avyra feature and is isolated in its own audio processor. |
| Disable automatic video-to-audio conversion | Rejected. Avyra exposes this as a user preference and has stricter wrong-recording safeguards than the upstream manual-only behavior. |
| Private-storage downloads and offline HLS packages | Rejected as the default because it would hide existing downloads from `Music/Avyra` and change backup/file-manager behavior. HLS sources safely fall back to Avyra's existing direct-file download path instead. |
| Multi-account cookie/session vault and brand-channel selector | Not ported in this update. Avyra already scopes authenticated requests to the active cookie, auth user, data-sync id and page id. Replacing its encrypted `AuthStore` and playback-history ownership in the same update would put library writes and history attribution at unacceptable risk. This should be a separate migration with account-switch integration tests. |
| Experimental Automix ONNX ranker/model | Not ported. Avyra's transition planner and native analyzers have diverged substantially, and the upstream model is an optional electronic-music experiment. Canonical-analysis safety was ported independently. |
| 32-bit float / preferred USB DAC route | Not ported yet. Android still owns the mixer and route, so upstream's switch is a float-output request plus preferred-device hint, not direct or bit-perfect DAC access. Avyra's two simultaneous players and DSP chain require device testing before changing both renderers. |
| Forced high-refresh/performance mode and battery-settings shortcut | Not ported. It increases battery/thermal cost and does not improve audio correctness. Avyra already exposes Reduce animation and keeps analysis off unless Automix is enabled. |
| Global iOS-style overscroll | Not ported because it would wrap every Avyra screen, including custom nested scrolling in lyrics, queue and player sheets. It needs gesture regression testing as a standalone UI change. |
| Local-library grid/list rewrite | Not copied wholesale. It replaces Avyra's downloaded-release/playlist grouping and long-press actions. A future Avyra-native grid can reuse those models without discarding them. |
| Hebrew/Polish and bulk translation replacement | Not copied wholesale. Upstream resources contain BitChord product text and a different string catalogue. They need an Avyra translation pass rather than a resource-folder overwrite. |
| Upstream CI, debug signing, funding, README and release-channel files | Rejected as repository-specific infrastructure and branding. |

## Verification contract

The integration must pass the production unit-test variant plus the dev debug
application and instrumentation APK builds. New regression coverage exercises
search/library continuation parsing, Automix canonical-rendition selection,
primary-artist splitting and Avyra's adapted local-media filter.
