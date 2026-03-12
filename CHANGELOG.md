# Changelog

## 0.1.9

- added live host enrichment on the main inventory: reverse-DNS hostnames, best-effort MAC capture from the neighbor cache, IEEE OUI vendor lookup, and heuristic device classification
- made device cards tappable and added a full device-detail screen that shows the current evidence report for each observed host
- shipped the IEEE OUI bundle with the app and documented that MAC/vendor enrichment remains best-effort on Android because neighbor-table visibility is not guaranteed for every peer

## 0.1.8

- added explicit `Start scan` / `Stop scan` controls to the main overflow menu and fixed manual rescans so they always force a fresh sweep, even when the bounded host slice does not change
- added an opt-in continuous background scan mode backed by a foreground service, with ongoing status notifications and alerts for newly seen interesting hosts
- added runtime notification permission prompting for Android 13+ when continuous scanning is enabled and kept scan-enabled state persisted across the app, menu, and background notification action

## 0.1.7

- rebuilt the landing screen around a full-height local device list with a compact status strip instead of a large summary card
- moved diagnostics actions into a toolbar overflow menu and added a manual subnet rescan action there
- changed rescans to advance across additional bounded host slices and keep discovered devices accumulated on the current network instead of replacing the list each sweep

## 0.1.6

- reworked the main landing screen so the live local Wi-Fi device inventory stays primary instead of being pushed down by remembered-network chrome
- tightened the current-network summary card and moved its detail into compact inventory/status copy plus Diagnostics
- added the current phone to the visible local inventory list so the landing page reflects the device that is actually running `MAKO`

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
