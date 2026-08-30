# Avyra brand and interface system

Avyra is an artwork-led music player with its own **Orbit** visual language. It keeps familiar playback controls where listeners expect them, but its identity, composition, color, typography, navigation, and component geometry are original to Avyra.

## Identity

The mark is a rounded abstract **A** with a forward/play cutout. It is designed as one strong silhouette so the same geometry works as a full-color app icon, a monochrome notification icon, and a small in-app signature.

- Primary: `#7057FF` Avyra Violet
- Secondary: `#35E2C1` Avyra Aqua
- Highlight: `#FF7A90` Avyra Coral
- Dark canvas: `#080A12` Orbit Ink
- Production icon: `app/src/main/assets/Logo.svg`
- Transparent mark: `app/src/main/assets/LogoTransparent.svg`
- Generated source reference: `brand/source/AvyraLogoGeneratedReference.png`

Keep clear space around the mark equal to at least one quarter of its width. Do not add sound-wave bands, place it inside a circle, stretch it, or recolor the two-color app-icon background with another product's signature color.

## Interface principles

1. **Artwork is the atmosphere.** Album color can shape a page, but navigation and controls remain legible on their own surfaces.
2. **One obvious action.** Primary play controls use violet; secondary actions stay quiet until selected.
3. **Editorial before grid.** Home and discovery lead with a wide story card, followed by compact shelves.
4. **Soft geometry, clear hierarchy.** Large surfaces use 22–34 dp corners; controls use 12–18 dp corners; circles are reserved for people and truly radial controls.
5. **Familiar controls, Avyra composition.** Play, pause, seek, queue, volume, and routing retain standard meanings and accessible targets.

## Typography

Avyra uses the Android sans-serif family so builds do not depend on a platform-owner font license. Display text is wide and calm at weight 700; content uses 400–600. Avoid all-uppercase navigation and oversized heavy labels.

## Compatibility boundary

Visible product branding is Avyra. Kotlin namespaces, preference keys, URI schemes, and the legacy `Music/BitChord` import path remain unchanged where replacing them would break downloads, playback queues, or imported user data.

The distributable application ID is `com.avyra.music`; development builds use `com.avyra.music.dev`. Kotlin namespaces and selected legacy storage/protocol identifiers remain internal compatibility details.

Set `AVYRA_UPDATE_API_URL` in `local.properties` to your own GitHub `releases/latest` API endpoint when your release repository exists. It is blank by default.

Set `AVYRA_DISCORD_APPLICATION_ID` in `local.properties` to an Avyra-owned Discord application identifier. Rich Presence stays disabled when this value is blank, while Discord account connection remains available.
