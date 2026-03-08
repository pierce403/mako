# Architecture

## Modules

- `ui`: activities, view models, and presentation
- `network`: active-network monitoring and current-link identity capture
- `discovery`: subnet sweeps, passive service discovery, and active fingerprinting
- `data`: Room entities, DAOs, repositories, and timeline generation

## Architecture decision

`MAKO` should work like `unagi` operationally, but not as a line-for-line structural clone. The chosen direction is a more modular pipeline where:

- `network` owns current-link state and network identity inputs
- `discovery` owns local discovery and active probing controllers
- `data` owns per-network persistence and timeline derivation
- `ui` consumes derived state rather than orchestrating protocol work directly

## Current network key strategy

The first-pass `networkKey` is derived from the most stable Wi-Fi link properties currently available without extra identity permissions:

- stable inputs currently in the key: transport, subnet CIDR, gateway, DNS server set, search domains, and private DNS name
- ephemeral inputs intentionally excluded from the key: local host address and other session-only status fields
- raw identity inputs are still stored on the persisted network record so the grouping strategy can be revised later

Current limitation: SSID/BSSID are not part of the key yet. They will be reconsidered after the Android permission posture is documented and validated.

## MVP storage shape

### Network

- derived `networkKey`
- raw identity inputs used to derive that key
- first seen / last seen / last connected
- user label or notes

### Device

- scoped to one network record
- derived device identity summary with confidence
- latest names, addresses, service hints, and fingerprint summary

### Observation

- raw evidence captured during a sweep or passive discovery pass
- protocol/source attribution
- timestamps and reachability outcomes

### TimelineEvent

- network-connected / network-left
- device-discovered / device-returned / device-absent
- name-changed / service-changed / fingerprint-changed

## Early implementation note

The initial scaffold only implements active-link monitoring through `ConnectivityManager` and `LinkProperties`. Discovery and persistence are intentionally not faked; they will be added once the Android feasibility work is documented.
