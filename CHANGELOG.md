# Changelog

## 0.1.5

- added a remembered Wi-Fi network strip above the device cards, with the currently selected active network marked `LIVE`
- fixed the main inventory layout so the device-card RecyclerView reliably gets the remaining screen height under the toolbar and summary card
- kept the `UNAGI`-style device cards, but made them explicitly scoped to the selected live network

## 0.1.4

- changed the main screen to a drawer-backed device list layout modeled after `unagi`
- made the bounded TCP sweep publish incremental results so detected hosts appear while the scan is still running
- added sortable/filterable detected-device cards for responsive local hosts and basic service-role hints
- included the current app version in Diagnostics so staged APKs are easier to verify on-device

## 0.1.3

- added a local-only diagnostics screen with copyable report output
- included network identity inputs, sweep summaries, per-host probe outcomes, permission posture, and current sweep settings in diagnostics
- clearly labeled exported diagnostics as containing sensitive local-network values

## 0.1.2

- documented the discovery protocols and Android local-network constraints from primary sources
- fixed `NetworkMonitor` to use callback-supplied state inside `NetworkCallback` paths
- added bounded host planning from the active subnet
- added an automatic bounded TCP host sweep with session summaries and cancellation on network change

## 0.1.1

- added the first Room-backed per-network record flow
- derived and persisted a first-pass `networkKey` from stable Wi-Fi link inputs
- updated the main UI to show whether the current network is new or previously known
- added a unit test for `networkKey` derivation stability

## 0.1.0

- scaffolded the Android project with the same CLI-first Gradle setup used by `unagi`
- added APK staging and Android SDK setup scripts
- added a branded `MAKO` landing page and preview assets
- added a buildable Android app shell with current-network monitoring
- documented the initial architecture, privacy stance, and feature targets
