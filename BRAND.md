# Avyra brand and interface system

Avyra is an artwork-led music player with its own **Orbit** visual language. It keeps familiar playback controls where listeners expect them, but its identity, composition, color, typography, navigation, and component geometry are original to Avyra.

## Identity

The mark is a rounded abstract **A** with a forward/play cutout. It is designed as one strong silhouette so the same geometry works as a full-color app icon, a monochrome notification icon, and a small in-app signature.

- Primary: `#0A84FF` Avyra Blue (light theme: `#007AFF`)
- Secondary: `#5AC8FA` Avyra Cyan
- Highlight: `#FF6482` Avyra Pink
- Dark canvas: `#05070B` Orbit Ink
- Hairline: `#23262E` — list separators, at 0.5 dp

A single vivid blue carries the interface. It is the only saturated thing on
screen that is not album art, which is what lets a tapped control read as tapped
without any surface needing to be tinted. Cyan and pink are close relatives
rather than contrasts; they exist for the few places Material insists on a
secondary and a tertiary, and the palette is at its best when neither is
especially visible.

The neutrals are mixed *towards* the accent rather than being pure grey — a cool
cast far too slight to read as blue on its own, but enough that the accent looks
like it belongs to the surface instead of being dropped onto it.
- Production icon: `app/src/main/assets/Logo.svg`
- Transparent mark: `app/src/main/assets/LogoTransparent.svg`
- Generated source reference: `brand/source/AvyraLogoGeneratedReference.png`

Keep clear space around the mark equal to at least one quarter of its width. Do not add sound-wave bands, place it inside a circle, stretch it, or recolor the two-color app-icon background with another product's signature color.

## Interface principles

1. **Artwork is the atmosphere.** Album color can shape a page, but navigation and controls remain legible on their own surfaces.
2. **One obvious action.** Primary play controls use violet; secondary actions stay quiet until selected.
3. **Editorial before grid.** Home and discovery lead with a wide story card, followed by compact shelves.
4. **Soft geometry, clear hierarchy.** Artwork is rounded to roughly 7% of its own width — 12 dp on a shelf card, 16 dp on a hero card, 10 dp on a list row — so a cover reads as a printed square with its corners taken off rather than as an app icon. Sheets and controls keep 12–18 dp. Circles are reserved for people and truly radial controls.
5. **Carousels to browse, lists to retrieve.** A horizontal shelf trades completeness for size and is right where the listener has chosen none of it — Home and Discover. A library is the opposite question: it is scanned vertically, everything visible at once, artwork small enough that a screenful is a dozen rows.
5. **Familiar controls, Avyra composition.** Play, pause, seek, queue, volume, and routing retain standard meanings and accessible targets.

## Typography

Avyra uses the Android sans-serif family so builds do not depend on a platform-owner font license. Display text is wide and calm at weight 700; content uses 400–600. Avoid all-uppercase navigation and oversized heavy labels.

Note that `app/src/main/res/font/` currently carries five SF Pro Display `.otf` files (11.5 MB) that **nothing references** — the theme has always resolved to `FontFamily.SansSerif`, and there is no `R.font` lookup anywhere in the source. They are inert: they inflate the APK and change nothing on screen.

Before wiring them into the theme, note that SF Pro is licensed for designing and developing for Apple platforms, not for redistribution inside an Android APK. If Avyra wants a voice of its own, an open family (Inter, Manrope, Figtree) gets most of the same feel without the licensing question.

## Compatibility boundary

Visible product branding is Avyra. Kotlin namespaces, preference keys, URI schemes, and the legacy `Music/BitChord` import path remain unchanged where replacing them would break downloads, playback queues, or imported user data.

The distributable application ID is `com.avyra.music`; development builds use `com.avyra.music.dev`. Kotlin namespaces and selected legacy storage/protocol identifiers remain internal compatibility details.

Set `AVYRA_UPDATE_API_URL` in `local.properties` to your own GitHub `releases/latest` API endpoint when your release repository exists. It is blank by default.

Set `AVYRA_DISCORD_APPLICATION_ID` in `local.properties` to an Avyra-owned Discord application identifier. Rich Presence stays disabled when this value is blank, while Discord account connection remains available.
