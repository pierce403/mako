# Discovery Protocols

This document captures the first-pass protocol choices for `MAKO`, the reasons they matter, and the current implementation posture.

The goal is not to support every local-network protocol at once. The goal is to choose a stack that is useful on modern Android, local-only by default, and explicit about what is observed versus inferred.

## Protocol set

### Current network facts

- Source of truth: `ConnectivityManager`, `NetworkCapabilities`, and `LinkProperties`
- Why it matters: this is how `MAKO` learns the active transport, local addresses, routes, gateway, DNS servers, and metered/captive-portal state
- MVP posture: in scope and already implemented as the root of the network record flow

Android's connectivity guide says the `LinkProperties` object contains link information such as DNS servers, local IP addresses, and routes, and that using `NetworkCallback` for connectivity state does not require any particular permission by itself. It also warns that synchronous `ConnectivityManager` getters should not be called from inside callback methods because of race conditions.

### Unicast DNS

- Relevant records: PTR, SRV, TXT, plus ordinary A/AAAA lookups
- Why it matters: reverse lookup and service naming can produce low-cost hostname or service hints without full banner grabbing
- MVP posture: in scope and first-pass reverse DNS is now implemented as a best-effort enrichment step for responsive hosts

`RFC 1035` defines PTR and TXT resource records. PTR is the basis for reverse name mapping. `RFC 2782` defines SRV records for locating a service endpoint by service, protocol, and domain. `RFC 6763` layers DNS-Based Service Discovery on top of DNS naming conventions and notes that DNS-SD can work over ordinary unicast DNS as well as multicast DNS.

Practical implication for `MAKO`: unicast DNS is appropriate for:

- reverse lookups against configured resolvers
- SRV/TXT lookups for service-specific enrichment
- low-cost follow-up queries after a host is already known

Practical implication for `MAKO`: unicast DNS is not evidence of device identity by itself. DNS data can be stale, shared, or centrally managed.

Current shipped posture:

- `MAKO` now performs best-effort reverse DNS for responsive IPv4 hosts after they are observed in the bounded TCP sweep
- current hostname enrichment uses the platform resolver path and is intentionally treated as hint-level evidence in the UI
- negative or missing reverse DNS results do not remove a host from inventory and are shown as unavailable rather than as proof that no hostname exists

### mDNS / DNS-SD

- Standards: `RFC 6762` and `RFC 6763`
- Why it matters: this is the most important local-service discovery path for printers, media devices, Apple devices, developer gear, and many smart-home products
- MVP posture: in scope and likely the primary passive discovery protocol

`RFC 6762` describes Multicast DNS on `224.0.0.251:5353` for IPv4 and `[FF02::FB]:5353` for IPv6. `RFC 6763` defines how service instances, SRV records, and TXT records are used for service discovery. The Android `NsdManager` API states that Android's framework NSD support is currently DNS-based service discovery limited to the local network over Multicast DNS.

Practical implication for `MAKO`:

- prefer Android's `NsdManager` where it is sufficient
- expect service-type browsing to be local-link scoped
- treat TXT and service-instance names as evidence, not identity truth

### SSDP / UPnP

- Reference: UPnP Device Architecture
- Why it matters: common on TVs, streamers, speakers, routers, printers, and smart-home bridges
- MVP posture: in scope after the first mDNS pass

The UPnP Device Architecture defines SSDP discovery as UDP-based messages using a subset of HTTP-like header formatting rather than full HTTP semantics. Discovery traffic is sent to `239.255.255.250:1900`, and active search uses `M-SEARCH * HTTP/1.1` with `MAN: "ssdp:discover"`.

Practical implication for `MAKO`:

- SSDP is valuable for device category hints and descriptor URLs
- SSDP should be bounded and rate-limited because it is multicast and chatty
- descriptor parsing should be treated as descriptive metadata, not stable identity

### LLMNR

- Standard: `RFC 4795`
- Why it matters: some Windows-oriented environments still expose names this way
- MVP posture: deferred / optional

`RFC 4795` defines LLMNR as link-local multicast name resolution, explicitly operating only on the local link and not serving as a DNS replacement. `RFC 6763` further notes that LLMNR explicitly excluded service discovery, which is why it is not a replacement for DNS-SD.

Practical implication for `MAKO`:

- useful only for host-name resolution, not service browsing
- should stay behind protocol-specific gating because it adds noise and uneven value
- not worth making the first discovery path

### NetBIOS over TCP/IP / NBNS

- Standard family: `RFC 1001` and `RFC 1002`
- Why it matters: can still expose useful names and roles on Windows-heavy or legacy networks
- MVP posture: deferred / optional

`RFC 1002` defines packet formats and protocol behavior for NetBIOS over TCP/UDP. It remains useful as a compatibility and enrichment source, but it is legacy, noisy, and less universal than mDNS or SSDP.

Practical implication for `MAKO`:

- treat NetBIOS names as one hint among many
- do not let NetBIOS dominate identity grouping
- postpone until the core discovery pipeline is stable

### Reachability probes and banner grabs

- Primitive: explicit TCP connect probes to selected local ports
- Why it matters: a bounded connect attempt is more predictable than pretending ICMP-style reachability is trustworthy from app space
- MVP posture: in scope for later active fingerprinting

Oracle's `InetAddress.isReachable()` documentation says a typical implementation uses ICMP if privileges can be obtained, otherwise it tries TCP port 7. That makes it a poor fit for modern local-network fingerprinting because port 7 is rarely available and negative results are ambiguous.

Practical implication for `MAKO`:

- do not rely on `InetAddress.isReachable()` as the core host-sweep primitive
- prefer explicit TCP connect probes to a bounded set of known ports
- classify results as timeout, refused, reachable-on-port, or responded-with-banner rather than "host exists" vs "host absent"

### Neighbor cache and OUI lookup

- Primitive: best-effort reads from the local ARP / neighbor cache plus offline IEEE OUI lookup
- Why it matters: if Android exposes a peer MAC address at all, the prefix can provide a manufacturer hint that improves device classification and detail screens
- MVP posture: in scope and first-pass support is now implemented

Practical implication for `MAKO`:

- MAC visibility is not guaranteed on modern Android, so this path is a hint source rather than a required part of discovery
- manufacturer names are only shown when both the peer MAC is visible and the prefix exists in the bundled IEEE OUI table
- UI copy should keep vendor and category labels clearly separated from observed reachability facts

## Android-specific constraints

### Multicast receive path

Android's `WifiManager.MulticastLock` documentation says Wi-Fi multicast packets are normally filtered and that holding a multicast lock allows the app to receive multicast traffic, with a noticeable battery cost if left on unnecessarily.

Implication for `MAKO`:

- mDNS and SSDP receive paths should acquire multicast only while actively discovering
- multicast discovery should be session-bounded and visible in the UI

### Local-network permission changes

Android's local network permission guidance now matters for this project:

- Android 16 guidance: local-network restrictions can be opt-in tested
- Android 17 guidance: local network protection becomes mandatory for apps targeting API 37+
- raw-socket traffic to local addresses, multicast traffic such as mDNS and SSDP, and framework helpers such as `NsdManager` are all part of the affected surface

The same Android page also documents a more privacy-preserving path: system-mediated `NsdManager` discovery with `DiscoveryRequest.FLAG_SHOW_PICKER`, which can provide user-selected mDNS targets without broad local-network access.

Implication for `MAKO`:

- `MAKO` should keep a broad-discovery mode for its core use case, but it should architect for future permission gating
- the app should be able to degrade to picker-mediated or narrower discovery paths on future Android releases

## Current protocol order

The current preferred implementation order is:

1. `ConnectivityManager` / `LinkProperties` for network facts
2. bounded host-range derivation from the active subnet
3. mDNS / DNS-SD using `NsdManager`
4. unicast PTR/SRV/TXT lookups against configured resolvers
5. SSDP / UPnP search and descriptor parsing
6. explicit active fingerprinting by selected TCP protocols
7. optional LLMNR and NetBIOS enrichment

## Explicit exclusions for now

- no dynamic DNS updates
- no internet-wide scanning
- no vulnerability exploitation or credential guessing
- no assumption that one protocol's name equals true device identity
- no dependence on `InetAddress.isReachable()` as the primary discovery signal

## Primary references

- Android Developers: Connectivity state and `LinkProperties`
  - https://developer.android.com/develop/connectivity/network-ops/reading-network-state
- Android Developers: `ConnectivityManager.NetworkCallback`
  - https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- Android Developers: `NsdManager`
  - https://developer.android.com/reference/android/net/nsd/NsdManager
- Android Developers: `WifiManager.MulticastLock`
  - https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock
- Android Developers: Local network permission
  - https://developer.android.com/privacy-and-security/local-network-permission
- IETF `RFC 1035`: DNS implementation and specification
  - https://datatracker.ietf.org/doc/html/rfc1035
- IETF `RFC 2782`: DNS SRV
  - https://datatracker.ietf.org/doc/html/rfc2782
- IETF `RFC 4795`: LLMNR
  - https://datatracker.ietf.org/doc/html/rfc4795
- IETF `RFC 6762`: Multicast DNS
  - https://datatracker.ietf.org/doc/html/rfc6762
- IETF `RFC 6763`: DNS-Based Service Discovery
  - https://datatracker.ietf.org/doc/html/rfc6763
- IETF `RFC 1002`: NetBIOS over TCP/UDP details
  - https://datatracker.ietf.org/doc/rfc1002/
- Open Connectivity Foundation: UPnP Device Architecture 1.1
  - https://openconnectivity.org/upnp-specs/UPnP-arch-DeviceArchitecture-v1.1.pdf
- Oracle Java docs: `InetAddress.isReachable()`
  - https://docs.oracle.com/javase/9/docs/api/java/net/InetAddress.html
