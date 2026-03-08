# Changelog

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
