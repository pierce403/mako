# Android Local-Network Notes

This document collects Android platform guidance that directly affects `MAKO`.

## Current app-relevant APIs

### `ConnectivityManager` and `LinkProperties`

Use these to obtain:

- active network handle
- transport type
- routes
- local addresses
- DNS servers
- captive-portal and validation state

Android's connectivity guide explicitly documents `LinkProperties` as the source for DNS servers, local IP addresses, and routes.

## Important callback rule

Android's `ConnectivityManager.NetworkCallback` reference says not to call synchronous `ConnectivityManager.getNetworkCapabilities()` or `getLinkProperties()` inside callback methods because that is prone to race conditions and can return stale or null objects.

Implication for `MAKO`:

- network monitoring should rely on callback-supplied state or defer synchronous reads outside the callback edge
- discovery start/stop logic should avoid building identity from stale callback-time reads

## Local-network access roadmap

Android's official local-network permission guidance now makes future-proofing mandatory:

- Android 16 / API 36: local-network restrictions are opt-in for testing
- Android 17 / API 37 and higher: broad local-network access is blocked by default for apps targeting that SDK until the app adopts the new permission path

The documented impacted traffic includes:

- outgoing TCP to LAN addresses
- incoming TCP from LAN addresses
- UDP unicast / multicast / broadcast to or from LAN addresses
- `.local` service resolution
- framework helpers such as `NsdManager`

For Android 16 opt-in testing, the same guidance says local-network access can be restricted with:

```bash
adb shell am compat enable RESTRICT_LOCAL_NETWORK <package_name>
```

The page also says that during the Android 16 opt-in phase, framework operations outside the app process such as `NsdManager` are not affected in the same way as raw socket access.

## Permissions posture for `MAKO`

What we know now:

- reading connectivity state with `NetworkCallback` does not by itself require a permission
- LAN traffic itself is the area that will be permission-gated on newer Android releases
- multicast receive paths require careful use of `WifiManager.MulticastLock`
- continuous background scanning on current Android releases is practical via a foreground service, not via a hidden/background worker

What this means for the project:

- passive network-fact capture can stay on the connectivity stack
- broad discovery and active fingerprinting need an explicit permission strategy for Android 16/17+
- picker-mediated mDNS discovery may become the fallback or privacy-preserving mode on future Android versions
- current runtime prompting should stay narrow: `POST_NOTIFICATIONS` on Android 13+ for visible continuous-scan notifications and device alerts

Current shipped `MAKO` behavior:

- manifest permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`
- runtime prompt today: `POST_NOTIFICATIONS` on Android 13+ when the user enables continuous scanning
- no runtime request is currently made for SSID/BSSID access because `MAKO` is not yet collecting them
- multicast is not yet actively used, so `CHANGE_WIFI_MULTICAST_STATE` is present for future mDNS/SSDP work but does not currently trigger any user-facing flow
- peer MAC visibility is best-effort only: current host enrichment tries the local neighbor cache (`/proc/net/arp`) and may return nothing for many peers or Android builds
- vendor naming is derived locally from a bundled IEEE OUI table only when a peer MAC address is actually visible

## Multicast lock posture

Android's `WifiManager.MulticastLock` reference says:

- Wi-Fi multicast is normally filtered
- holding a multicast lock allows multicast packets through
- doing so has a noticeable battery cost if held longer than needed

Project implication:

- only hold multicast during an active mDNS or SSDP sweep
- release it immediately after the sweep or listener session ends
- show users that discovery is active when this path is in use

## Official references

- https://developer.android.com/develop/connectivity/network-ops/reading-network-state
- https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- https://developer.android.com/reference/android/net/nsd/NsdManager
- https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock
- https://developer.android.com/privacy-and-security/local-network-permission
