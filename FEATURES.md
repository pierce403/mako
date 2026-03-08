# Features

## MVP

- detect the active network context
- scope all observations to the current Wi-Fi network
- discover and list local devices
- keep per-network device memory across relaunches
- show a network timeline of meaningful changes
- keep everything local to the device

## UX expectations

- black-and-red shark presentation instead of `unagi` blue ninja styling
- clear distinction between observed facts and inferred classifications
- explicit indicators for discovery and active fingerprinting
- strong empty and unsupported states

## State design

- `new network`: blank-slate presentation with first-visit copy
- `known network`: historical context and prior counts visible early
- `live discovery`: explicit running indicator and protocol summary
- `active fingerprinting`: stronger warning/intent treatment than passive discovery
- `offline/unsupported`: crisp explanation instead of generic empty failure language
- `observed` vs `inferred` vs `user-labeled`: separate visual treatments and labels, never silently merged

## Post-MVP candidates

- per-device watch rules or alerts
- exportable diagnostics
- richer per-protocol fingerprint modules
- background-safe discovery modes
