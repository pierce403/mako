# mako

Android app for local-first Wi-Fi situational awareness.

`MAKO` is the shark-themed sibling to `unagi`: black-and-red presentation, CLI-only Android workflow, local-only storage, and a focus on the network the device is currently on. The MVP is scoped to:

- identify the current network
- discover local devices on that network
- keep per-network device memory
- show a timeline of arrivals, departures, and meaningful fingerprint changes

## Product brief

`MAKO` is for people who want fast local situational awareness about the Wi-Fi environment they are standing in: operators, researchers, defenders, and curious users who care about what is on the current network without shipping that visibility to a cloud service.

MVP flow: `connect -> identify network -> discover devices -> inspect details -> review timeline`

Passive behavior means low-impact network-context capture and local discovery work tied to the current Wi-Fi link. Active behavior means explicit fingerprinting probes that go beyond basic discovery and try to learn more from a host or service banner.

Active fingerprinting is currently defined as opt-in per network, with manual per-device actions living inside an enabled network context.

## Non-goals

- generic internet scanning outside the current local network
- exploitation, brute forcing, or vulnerability verification workflows
- cloud sync, remote logging, or silent background collection by default
- pretending DNS names, banners, or service descriptors are stable identity truth

## Terminology

- `network`: the current Wi-Fi environment, represented by a derived `networkKey`
- `device`: a best-effort per-network host record, not a global identity claim
- `observation`: raw evidence captured during discovery or probing
- `fingerprint`: structured evidence and derived classification from active probes
- `event`: a meaningful state transition written to the timeline
- `session`: one bounded discovery or interaction run tied to a network
- `timeline`: the ordered change history for a network and the devices remembered within it

## Visual direction

- shark theme: black surfaces with red accents and a hard-edged, tactical presentation
- typography: condensed uppercase display treatment for headings, calmer body copy underneath
- tokens: central color tokens live in [colors.xml](/home/pierce/projects/mako/app/src/main/res/values/colors.xml) and website variables live in [index.html](/home/pierce/projects/mako/index.html)
- landing priority: the main screen should open on the live local Wi-Fi device inventory, with current-network context kept compact above it
- state treatment: `new network`, `known network`, `live discovery`, `active fingerprinting`, and `offline/unsupported` should each read as distinct UI states
- trust treatment: `observed`, `inferred`, and `user-labeled` data should remain visually distinct rather than being blended into one confidence-free label

## Current status

The repo now has the same basic operating shape as `unagi`:

- Gradle wrapper and pinned Android/Kotlin plugin versions
- `app/` Android module
- CLI SDK bootstrap script
- APK staging script for `downloads/`
- landing page at `index.html`

The app shell now leads with the live local Wi-Fi inventory list, backed by the current-link context from Android connectivity APIs and the first bounded subnet sweep.

Current architecture direction: stay operationally similar to `unagi`, but use a more modular network/discovery/data pipeline instead of mirroring the Bluetooth-specific internals directly.

## Build

- `./gradlew assembleDebug`
- `./gradlew installDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `scripts/stage-apk`

Android SDK setup instructions live in [docs/SETUP_ANDROID.md](/home/pierce/projects/mako/docs/SETUP_ANDROID.md).

Protocol and platform notes live in:

- [docs/DISCOVERY_PROTOCOLS.md](/home/pierce/projects/mako/docs/DISCOVERY_PROTOCOLS.md)
- [docs/ANDROID_LOCAL_NETWORK.md](/home/pierce/projects/mako/docs/ANDROID_LOCAL_NETWORK.md)

## Near-term work

- prove the feasible Android local-network discovery stack
- define the Room schema around per-network history
- add passive naming and active fingerprinting modules
- build network detail, device detail, and timeline screens
