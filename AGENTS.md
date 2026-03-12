# AGENTS

## Mission

Build the MVP: identify network -> inventory devices -> fingerprint -> timeline.

`MAKO` is a local-only Android situational-awareness app for the Wi-Fi network the device is currently on. The product should feel like the shark-themed sibling of `unagi`: black and red visual language, fast local awareness, no cloud dependency, and a clear separation between what was observed and what is only inferred.

## Hard constraints

- No Android Studio usage (everything must work via CLI + VS Code)
- Reproducible environment setup (script + docs)
- No cloud; no remote logging by default
- Scope discovery to the network the phone is currently on; do not turn MVP work into a generic internet scanner
- Keep passive network context gathering separate from active fingerprinting
- Treat network identity, device identity, DNS names, and service banners as best-effort evidence, not ground truth

## Toolchain rules

- Use Android SDK Command-line Tools (`sdkmanager`, `adb`, etc.)
- Require JDK 17 unless the Android toolchain changes and docs are updated accordingly
- Pin AGP/Gradle versions using the official compatibility matrix; avoid dynamic versions

## Definition of Done (MVP)

- On a physical Android device connected to Wi-Fi: the app can identify the current network, discover local devices, persist those results locally, and show them again after relaunch
- Returning to a previously seen network restores its device inventory and timeline instead of starting from scratch
- Connecting to a different network creates a blank slate for that network without polluting other network records
- A network detail view exposes a scrollable timeline showing device arrivals, departures, and meaningful fingerprint/name/service changes over time
- Permission handling and unsupported-state UX are robust (Wi-Fi off, not on Wi-Fi, discovery blocked, partial probe failures, DNS failures)
- No crashes when active fingerprinting is disabled, unavailable, or only partially successful

## Implementation guidance

- The primary storage boundary is the network record. Do not build MVP around a single global device table that silently merges data across unrelated networks.
- Same SSID does not imply the same real network. Network grouping should use multiple inputs with stored confidence and raw evidence.
- Preserve raw observations so identity/fingerprinting logic can be improved later without losing history.
- Keep passive discovery results separate from active fingerprinting results. Active probes should enrich history, not overwrite the raw observation record.
- DNS-derived names are hints. Reverse lookups, mDNS, NetBIOS, LLMNR, DHCP hostnames, HTTP titles, TLS certs, and SSDP descriptors can disagree or go stale.
- Device identity inside one network is still approximate. IPs can churn, hostnames can be reused, and MAC visibility may be limited.
- Timeline events should be derived from meaningful state transitions: first seen, returned, absent, renamed, newly fingerprinted, fingerprint changed, service set changed.
- Optimize for battery and network civility: bounded sweeps, protocol-specific rate limits, concurrency caps, and cancellation on network change.
- Make it obvious when discovery or fingerprinting is active, what protocols are being used, and why a device classification was assigned.
- Local-only remains the default posture. If export or sharing is added later, it must be explicit and user-controlled.

## PR discipline

- Small PRs, each linked to a `TODO.md` item
- Add or update docs with every new requirement
- Commit and push after every task; if a push is blocked, surface the blocker immediately
- Track active multi-step work in `TODO.md` and update the checklist before or during implementation as scope changes
- Keep `TODO.md` as an active checklist; the current collaborator preference is to visibly check items off as they are completed
- For long-running work, land completed sub-tasks incrementally instead of batching unrelated changes together

## Recursive learning

- Update `AGENTS.md` whenever you learn anything important about the project, workflow, or collaborator preferences
- Capture both wins and misses: what to repeat, what to avoid, and any blocker that slowed delivery
- Keep notes concrete and reusable: build/test commands, deployment steps, project structure, coding conventions, pitfalls, and formatting preferences
- Prefer small, timely updates in the same task that revealed the learning, and replace stale guidance when it is superseded

## Agent memory checklist

- Repo status: `/home/pierce/projects/mako` now has an Android app scaffold, landing page, initialized git repo, and `origin` set to `git@github.com:pierce403/mako.git`
- Collaborator preference: keep completed items visibly checked in `TODO.md` instead of pruning them immediately
- Brand text in the app should use `MAKO`, not lowercase `mako`, unless design copy deliberately calls for it
- Preserve the shark/Wi-Fi identity: black + red theme, local-network awareness, and per-network history are core product traits, not optional polish
- Build/test/stage commands: `./gradlew assembleDebug`, `./gradlew installDebug`, and `scripts/stage-apk`
- Local Android SDK on this workstation is at `/home/pierce/Android/Sdk`; `local.properties` currently points there for local builds
- App/package identity is now `ninja.mako`
- `scripts/stage-apk` stages `downloads/mako-v<version>-debug.apk` and rewrites download links in `index.html`
- The current app shell already monitors active link properties through `ConnectivityManager` and `LinkProperties`; discovery/fingerprinting layers should extend that instead of replacing it
- `NetworkMonitor` should use callback-supplied `NetworkCapabilities` and `LinkProperties` state inside `NetworkCallback` paths; only the initial snapshot should use synchronous getters
- First-pass `networkKey` derivation now hashes stable Wi-Fi link inputs from `NetworkIdentityFactory`: transport, subnet CIDR, gateway, sorted DNS set, search domains, and private DNS name
- Current `networkKey` intentionally excludes ephemeral local host address and still excludes SSID/BSSID until the Android permission posture is documented and validated
- Network records now persist raw identity inputs alongside the derived key in Room so grouping logic can be revised later without losing the original evidence
- `HostCandidatePlanner` now derives a bounded IPv4 host plan from the active subnet, prioritizing gateway, in-subnet DNS servers, and neighbors near the local address before filling the rest of the sweep budget
- `TcpHostSweepRunner` now runs the first bounded active discovery pass using TCP connect probes on ports `53, 80, 443, 445, 631`, with concurrency capped at 12 hosts and a 250 ms per-connect timeout
- `TcpHostSweepRunner` now emits incremental session progress, so the main device list can populate while a sweep is still in flight instead of only after completion
- The current sweep controller starts automatically for a new active Wi-Fi discovery plan and cancels when the network key or active subnet plan changes or Wi-Fi drops
- `DiagnosticsActivity` now accepts an explicit local-only report payload from `MainActivity`, exposes a copy-to-clipboard action, and labels that the exported text contains sensitive local-network values
- The main screen now follows the `unagi` interaction pattern more closely: a left filter drawer, a compact network summary card, and a RecyclerView-backed detected-device list
- The main screen now leads with the live local Wi-Fi device inventory, keeps current-network context in a tighter summary card, and includes this phone in the visible device list
- The main screen now follows `unagi` more closely: a compact status strip, a toolbar overflow menu for diagnostics/rescan actions, and a full-height device list; manual rescans advance across additional bounded subnet slices and accumulate responsive hosts for the current network
- The main overflow menu now owns `Start scan`, `Stop scan`, `Rescan subnet`, and `Continuous scanning`; the foreground scan-enabled state is persisted so notification actions, lifecycle handoff, and the main screen stay in sync
- Continuous background scanning is now an explicit foreground-service mode with a 30 second cadence; it posts an ongoing notification and may alert on newly seen interesting hosts that are not the phone, gateway, or configured DNS resolvers
- Current permission posture: manifest includes `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and `POST_NOTIFICATIONS`; runtime prompting currently only applies to `POST_NOTIFICATIONS` on Android 13+ for user-visible continuous-scan notifications
- Live inventory cards now support best-effort host enrichment: reverse DNS hostname lookup, neighbor-table MAC capture when Android exposes it, IEEE OUI vendor lookup from a bundled asset, and heuristic category classification
- Peer MAC and vendor data are not guaranteed on Android; treat them as best-effort hints derived from the local neighbor cache, not ground truth
- Device cards are now tappable and open a dedicated device-detail screen with the current evidence report for that host
- Record SSID/BSSID, multicast discovery, and future Android local-network permission posture here as those discovery paths are implemented and validated
- Record probe defaults and rate limits here once active fingerprinting exists
- If the app targets SDK 35 or newer, top-level screens need explicit system-bar inset handling to avoid toolbar overlap on Android 15+
- When shipping user-visible APK changes, bump `versionCode` and `versionName` and keep the installed version visible somewhere obvious such as Diagnostics/about
