# Avyra brand notes

This file is a practical reference for anyone working on the app. It is not meant to sound like a big company brand book. The goal is just to stop the interface from slowly becoming inconsistent.

## Identity

The Avyra mark is a rounded letter A with a play-shaped cutout. It should still be easy to recognize as a small notification icon, not only as a large logo.

- Icon plum: `#5B173F`
- Icon raspberry: `#C93672`
- Icon coral: `#FF7A45`
- Primary blue: `#0A84FF` in dark mode and `#007AFF` in light mode
- Cyan: `#5AC8FA`
- Pink: `#FF6482`
- Dark background: `#05070B`
- Divider color: `#23262E`

The main source assets are:

- `app/src/main/assets/Logo.svg`
- `app/src/main/assets/LogoTransparent.svg`
- `brand/source/AvyraLogoGeneratedReference.png`

Give the logo some space. Do not stretch it, put random effects around it, or recolor it to look like another music service.

## Interface rules

1. Album artwork can influence the background, but controls still need enough contrast without depending on the artwork.
2. Each screen should have one action that feels most important. Secondary actions can stay quieter.
3. Home and discovery can use horizontal shelves. Library screens should be easier to scan as lists.
4. Use soft corners for artwork and sheets, but do not turn every element into a rounded card.
5. Keep familiar playback symbols and tap targets. A custom look should not make play, pause, queue, volume, or casting harder to understand.
6. Prefer the Android sans-serif font family. The project should not depend on a font that cannot be redistributed in an Android app.

## App identity

Production builds use `com.avyra.music`. Development builds use `com.avyra.music.dev`. Kotlin source also uses the `com.avyra.music` namespace.

Old storage names only appear inside migration code so existing users can keep settings, backups, lyrics, and downloads created by earlier builds. New data uses the Avyra identity.

Optional update and Discord settings are kept in local configuration:

- `AVYRA_UPDATE_API_URL`
- `AVYRA_DISCORD_APPLICATION_ID`

Do not commit real account tokens, signing credentials, or private service keys.
