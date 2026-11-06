# Pyng

A personal messaging app for Android that links to Google Messages, so RCS and
SMS keep flowing through your phone's own Messages app while Pyng gives you a
cleaner place to read and reply.

## How it works

Pyng pairs with Google Messages the same way Messages for Web does. Sign in with
the Google account that Google Messages uses, then confirm the emoji that shows
up on your phone. Google Messages stays your default SMS app.

## Building

Builds run on GitHub Actions. Every push to `main` produces a test APK under
**Releases**. The engine in `engine/` is Go, compiled to an Android library with
gomobile. The app in `app/` is Kotlin and Jetpack Compose.

## License note

The engine uses [libgm](https://github.com/mautrix/gmessages) from
mautrix-gmessages, which is licensed under the GNU AGPL v3. Any build that is
distributed to other people must follow that license.
