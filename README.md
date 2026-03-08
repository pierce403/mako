# mako

Android app for local-first Wi-Fi situational awareness.

`MAKO` is the shark-themed sibling to `unagi`: black-and-red presentation, CLI-only Android workflow, local-only storage, and a focus on the network the device is currently on. The MVP is scoped to:

- identify the current network
- discover local devices on that network
- keep per-network device memory
- show a timeline of arrivals, departures, and meaningful fingerprint changes

## Current status

The repo now has the same basic operating shape as `unagi`:

- Gradle wrapper and pinned Android/Kotlin plugin versions
- `app/` Android module
- CLI SDK bootstrap script
- APK staging script for `downloads/`
- landing page at `index.html`

The app shell currently renders current-link context from Android connectivity APIs and is ready for the real network discovery and storage layers.

## Build

- `./gradlew assembleDebug`
- `./gradlew installDebug`
- `scripts/stage-apk`

Android SDK setup instructions live in [docs/SETUP_ANDROID.md](/home/pierce/projects/mako/docs/SETUP_ANDROID.md).

## Near-term work

- prove the feasible Android local-network discovery stack
- define the Room schema around per-network history
- add passive naming and active fingerprinting modules
- build network detail, device detail, and timeline screens
