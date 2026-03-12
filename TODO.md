# TODO

## How to use this file

- Keep only active, incomplete, or next-up work here
- Add tasks before starting multi-step work
- Keep completed items visibly checked so progress stays easy to audit
- Record completed work in `README.md`, `CHANGELOG.md`, `ARCHITECTURE.md`, or `AGENTS.md` when the result should be remembered
- Prefer small, actionable items that map cleanly to the MVP

## Current focus

- [ ] Prove the network-discovery stack that is actually feasible on modern Android while connected to Wi-Fi
- [ ] Define the storage model for networks, devices, observations, fingerprints, and timeline events before building UI-heavy features
- [x] Turn the current network-monitor shell into the first real per-network record flow

## Product framing

- [x] Write a short product brief for `MAKO`: what it is, who it is for, and what the MVP excludes
- [x] Define the MVP flow in one line: connect -> identify network -> discover devices -> inspect details -> review timeline
- [x] Decide the user-facing terminology for `network`, `device`, `observation`, `fingerprint`, `event`, `session`, and `timeline`
- [x] Define what counts as `passive` versus `active` behavior in the app
- [x] Decide whether active fingerprinting is opt-in per app, per network, per session, or per device
- [x] Write down non-goals to prevent scope creep into generic vulnerability scanning

## Visual direction

- [x] Establish the shark theme: black surfaces, red highlights, aggressive but readable typography, and non-gimmicky motion
- [x] Create an icon/logo direction for `MAKO`
- [x] Define color, spacing, and typography tokens before the first UI pass
- [x] Design visual states for `new network`, `known network`, `live discovery`, `active fingerprinting`, and `offline/unsupported`
- [x] Decide how the network summary should look at the top of the main screen
- [x] Decide how to visually distinguish `observed`, `inferred`, and `user-labeled` data

## App scaffold

- [x] Initialize the Android app structure with Gradle wrapper, app module, and package namespace
- [x] Decide `applicationId`, namespace, min SDK, target SDK, and Kotlin/JVM settings
- [x] Add core dependencies: Room, coroutines, lifecycle/viewmodel, RecyclerView or Compose stack, DataStore, test libs
- [x] Add a CLI-first setup path for Android SDK/JDK installation and verification
- [x] Add formatting, lint, and test commands to the docs as soon as the scaffold exists
- [x] Decide whether the app architecture stays close to `unagi` or shifts to a more modular network/discovery pipeline

## Network identity

- [ ] Enumerate all Android-accessible signals for the current Wi-Fi network: SSID, BSSID, gateway IP, subnet, DNS servers, DHCP domain/search suffixes, captive-portal hints, transport info, Wi-Fi standard, meter state
- [x] Define a `networkKey` strategy that can handle same-SSID-different-place collisions
- [x] Decide which network fields are treated as stable identifiers versus ephemeral session metadata
- [x] Store raw network identity inputs so grouping logic can be revisited later
- [ ] Decide how to merge or split network records when the router changes but the site is probably the same place
- [ ] Handle redacted or unavailable SSID/BSSID cases cleanly
- [ ] Detect network changes promptly and end any in-flight discovery/fingerprinting tied to the old network
- [ ] Decide whether users can rename or annotate a network record locally
- [ ] Add a `forget this network history` path

## Discovery feasibility research

- [ ] Validate what Android actually allows for local subnet discovery without root
- [ ] Validate whether ARP/neighbor-cache scraping is usable or too unreliable on target API levels
- [ ] Validate multicast discovery requirements for mDNS/Bonjour, SSDP/UPnP, and any needed multicast locks
- [ ] Validate which DNS APIs/libraries are practical on-device for custom PTR/SRV/TXT lookups
- [ ] Validate whether ICMP is usable or whether TCP/UDP reachability probes are more realistic
- [ ] Validate the impact of Private DNS, VPNs, captive portals, and randomized MAC behavior on the discovery model
- [ ] Capture these findings in docs before discovery implementation hardens

## Discovery pipeline

- [x] Build a controller that starts and stops discovery based on the currently connected network
- [x] Derive the active subnet and candidate host ranges to inspect
- [x] Implement a bounded host sweep with concurrency limits and cancellation support
- [x] Track each sweep as a first-class session with start time, end time, status, and summary stats
- [x] Capture probe-level outcomes for diagnostics instead of only aggregate counters
- [x] Stop or pause discovery immediately when Wi-Fi disconnects or the network key changes

## DNS and local naming tricks

- [ ] Reverse-resolve local IPs using PTR lookups against the network's configured resolvers
- [ ] Query local search-domain variants when reverse DNS is absent and that behavior is justified
- [ ] Explore mDNS service browsing and hostname resolution for `.local` peers
- [ ] Explore LLMNR and NetBIOS name resolution where it is practical and worth the complexity
- [ ] Capture resolver identity details that are useful for diagnostics without turning this into a WAN fingerprinting feature
- [ ] Decide whether SRV/TXT record collection is part of MVP or only used for specific service types
- [ ] Normalize and score conflicting names from PTR, mDNS, NetBIOS, HTTP, TLS, and user labels
- [ ] Persist the source and confidence for every discovered name

## Service discovery and reachability

- [ ] Implement mDNS/Bonjour discovery for common home/office device categories
- [ ] Implement SSDP/UPnP discovery and descriptor parsing
- [ ] Detect common printer, media, smart-home, and developer-device services without over-claiming identity
- [ ] Decide whether DHCP-derived hints are available/usable on Android or need to be excluded
- [ ] Capture open-port evidence in a way that is useful for later fingerprinting without bloating storage
- [ ] Decide which protocols are worth probing in MVP versus post-MVP

## Active fingerprinting

- [ ] Define the safe default fingerprint set for MVP
- [ ] Add explicit user controls and disclosures for active fingerprinting
- [ ] Build a rate-limited per-host fingerprint runner with concurrency caps and backoff
- [ ] Probe common local-service banners where available: HTTP, HTTPS/TLS certificate metadata, SSH banner, SMB/NetBIOS, printer protocols, Chromecast/mDNS, SSDP descriptors
- [ ] Parse and store raw fingerprint evidence separately from derived labels
- [ ] Assign device-type/category confidence from evidence rather than from a single guessed label
- [ ] Track fingerprint changes over time and emit timeline events when they change meaningfully
- [ ] Distinguish `unreachable`, `timeout`, `filtered`, `service present`, and `service fingerprinted` states
- [ ] Ensure probes stay on the local network and never expand into internet-target behavior
- [ ] Make the fingerprint pipeline pluggable so new protocol modules can be added without rewriting storage

## Data model

- [ ] Design the Room schema around per-network storage
- [ ] Define entities for `Network`, `NetworkSession`, `Device`, `Observation`, `FingerprintSnapshot`, `TimelineEvent`, `NameRecord`, and `ServiceRecord`
- [ ] Decide whether device identity is keyed by IP, MAC, hostname, a derived fingerprint key, or a layered combination with confidence
- [ ] Keep raw observation payloads so grouping logic can evolve
- [ ] Store `firstSeen`, `lastSeen`, `timesSeen`, `lastReachable`, and `lastFingerprintAt` per device within a network
- [ ] Represent device absence/departure as events rather than just missing rows
- [ ] Store both the current derived device summary and the historical snapshots that produced it
- [ ] Plan schema migrations early because discovery logic is likely to evolve
- [ ] Decide retention rules for raw probe logs versus durable timeline history

## Timeline model

- [ ] Define event types for network join/leave, sweep start/end, device discovered, device returned, device absent, name changed, service added/removed, fingerprint changed, and user annotation
- [ ] Implement diffing between consecutive sweeps to generate timeline events
- [ ] Decide how long a device must be absent before it counts as `gone`
- [ ] Decide how to represent noisy devices that flap in and out during a single session
- [ ] Make the timeline scrollable and filterable by device, event type, and time range
- [ ] Support a `what changed since last time on this network` summary
- [ ] Support drill-down from a timeline event into the raw evidence behind that event

## Main UI

- [x] Build the main screen around the current network summary plus the discovered-device list
- [x] Rework the landing page so the live local Wi-Fi inventory stays above the fold and includes this phone in the visible device list
- [x] Move diagnostics and manual rescan controls into a toolbar overflow menu so the inventory list can own the landing screen
- [x] Add explicit start/stop scan controls plus an opt-in continuous background scan mode from the toolbar menu
- [x] Add best-effort hostname, vendor, and device-classification enrichment to the live inventory cards
- [x] Show whether the current network is new or previously known
- [ ] Show discovery state, last sweep time, active fingerprinting state, and any notable failures
- [ ] Add sorting and filtering for live status, hostname, IP, confidence, last seen, and likely device category
- [ ] Add strong empty states for `not on Wi-Fi`, `new network with no discoveries yet`, and `permissions missing`
- [x] Make it obvious that data is scoped to the current network record

## Device detail UI

- [x] Add a tap-through device detail screen with a full live evidence report for each discovered host
- [ ] Build a detail screen showing current derived identity, all observed names, reachable addresses, services, and fingerprint evidence
- [ ] Show what is known versus inferred with explicit evidence labels
- [ ] Show the device's history within the current network only
- [ ] Add manual fingerprint refresh controls if active fingerprinting is enabled
- [ ] Add local notes/tags for a device
- [ ] Add a raw-data section for debugging and future reclassification work

## Network detail UI

- [ ] Build a dedicated network detail screen for metadata, device totals, session history, and the full timeline
- [ ] Show first seen, last seen, last connected, subnet, DNS servers, and any local annotations
- [ ] Show aggregate counts: known devices, currently reachable devices, newly seen devices, changed devices
- [ ] Add a prominent timeline entry point from the main screen
- [ ] Decide whether timeline lives on the network detail screen or in a dedicated full-screen view

## Diagnostics and transparency

- [x] Create a diagnostics screen/report similar in spirit to `unagi`
- [x] Include network identity inputs, sweep summaries, protocol successes/failures, permission state, and current settings
- [ ] Surface why a device was labeled the way it was
- [ ] Surface why two observations were merged into one device or kept separate
- [x] Add a copy/export path for diagnostics that is explicit and local-only
- [x] Redact or clearly label sensitive values in exported diagnostics

## Permissions and platform behavior

- [ ] Research and document the Android permission posture for Wi-Fi identity and local discovery across supported API levels
- [ ] Confirm whether location permission is needed for SSID/BSSID access on each target API level
- [ ] Confirm any multicast lock requirements for mDNS and SSDP
- [ ] Handle Android 10 through Android 15 behavior differences
- [ ] Handle `Wi-Fi off`, `not connected`, `captive portal`, `VPN active`, `Private DNS active`, and `metered network` states cleanly
- [x] Decide whether long-running discovery needs a foreground service
- [x] If background behavior exists at all, keep it explicit and platform-friendly

## Performance and civility

- [ ] Put hard caps on sweep size, concurrency, and probe budgets
- [ ] Ensure large `/24` networks remain usable without freezing the UI
- [ ] Avoid rescanning the full network on every trivial app lifecycle event
- [ ] Cache stable results where it reduces churn without hiding real changes
- [ ] Throttle expensive history maintenance work off the main thread
- [ ] Measure battery cost of repeated discovery on home and office Wi-Fi

## Privacy and trust

- [ ] Write the privacy stance early and keep it local-first
- [ ] Explain exactly what the app sends on the network during passive discovery and active fingerprinting
- [ ] Decide whether any protocol should be disabled by default for safety or UX reasons
- [ ] Add a clear `discovery is active` indicator in the UI
- [ ] Add a clear `active fingerprinting is active` indicator in the UI
- [ ] Provide per-network controls for forgetting local history

## Testing

- [ ] Add unit tests for `networkKey` derivation and merge/split behavior
- [ ] Add unit tests for device grouping inside a network
- [ ] Add unit tests for sweep diffing and timeline event generation
- [ ] Add unit tests for name normalization and confidence scoring
- [ ] Add unit tests for fingerprint parser modules
- [ ] Add instrumentation tests for the main, network-detail, and device-detail screens
- [ ] Build a manual QA matrix covering home Wi-Fi, hotspot, enterprise/captive portal, and router/DNS changes
- [ ] Test network switching while discovery is running
- [ ] Test reconnecting to a previously known network after app relaunch
- [ ] Test behavior when probes partially fail or hang
- [ ] Test performance on noisy networks with many peers

## Documentation

- [x] Write `README.md`
- [x] Write `ARCHITECTURE.md`
- [x] Write `FEATURES.md`
- [x] Write `PRIVACY.md`
- [x] Document the discovery stack, trust model, and active fingerprinting safeguards
- [x] Document exact build/test/install commands once the Android project exists
- [x] Document any protocol exclusions or platform limitations discovered during implementation

## Release and project hygiene

- [x] Initialize git for the repo if that is still missing when implementation starts
- [x] Add `.gitignore`, editor settings, and basic repo hygiene files once the scaffold exists
- [x] Decide whether the project gets a landing page and versioned APK download flow like `unagi`
- [x] If a landing page exists, keep app branding, screenshots, and APK links aligned with releases
- [x] Add a changelog once real implementation work starts landing
