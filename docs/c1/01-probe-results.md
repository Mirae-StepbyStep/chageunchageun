# C1 probe results (Step 1)

Run 2026-11-12 on the Pleos Connect emulator v2.1.0 (Android 14, `crp_avd_arm64`), Pleos Vehicle SDK 2.3.2, branch `jaegyun/c1-appgraph-start`.

- Probe: `app/src/androidTest/.../c1/C1SignalProbe.kt`, logcat tag `C1Probe`. It is an instrumented test, so it is in no app build.
- Console input: `tools/console/console.py`, scenario `scenarios/c1_probe_all.json`, plus `gear D` / `gear P` and one `TPMS Blink`.
- SDK signatures and permission annotations were read from `Vehicle-2.3.2.aar` (`api.Safety`), not guessed.

## Answers

| Question | Answer |
|---|---|
| Is `PRIVATE_CAR_SAFETY` granted to our package? | Yes. `dumpsys package` shows `granted=true`. The permission is defined by `ai.pleos.playground.service.vehicle` with protection level `normal`, so declaring it is enough on this emulator. |
| (a) Does `registerTelltales` deliver TPMS on / blink / off? | Yes, all three, about 10 ms after the console write. |
| (b) Does `getTelltalesInfo` return the current state? | Yes. Polling once a second saw every change 0.1 to 0.8 s after the write. It is not needed, because the listener works. |
| (c) Do the dedicated APIs follow the console telltales? | No. `hasPowerDownWarningIndicator` and `hasSeatAirbagEnabled` stayed `false` while the matching telltale was On. The others are not supported on this emulator. |
| Do gear and `uxRestricted` follow the console gear? | Yes, through the existing `CarDriveStateSource`. |

## Telltales

Console write times are host clock. App times are logcat times plus 0.572 s, the measured device-to-host clock offset. The offset measurement is only good to a few tens of milliseconds, so read the delays as "well under 0.1 s".

| Console telltale | Pleos API | WarningType | Works | Delay, listener | Delay, 1 s poll | Evidence (app log) |
|---|---|---|---|---|---|---|
| `TPMS` On | `registerTelltales` → `TelltaleType.TPMS` | TIRE_PRESSURE | yes | 9 ms | 0.29 s | `registerTelltales CALLBACK size=1 active=TPMS=ON(color=4,prio=-1,count=0)` |
| `TPMS` Off | same | TIRE_PRESSURE | yes | 8 ms | 0.27 s | `registerTelltales CALLBACK size=0 active=none` |
| `TPMS` Blink | same | TIRE_PRESSURE | yes | not timed | not timed | `active=TPMS=BLINK(color=4,prio=-1,count=0)` |
| `SeatBelt` | `TelltaleType.SEAT_BELT` | SEAT_BELT | yes | 7 ms | 0.26 s | `active=SEAT_BELT=ON(…)` |
| `Airbag` | `TelltaleType.AIRBAG` | AIRBAG | yes | 7 ms | 0.23 s | `active=AIRBAG=ON(…)` |
| `Engine` | `TelltaleType.ENGINE` | ENGINE | yes | 8 ms | 0.20 s | `active=ENGINE=ON(…)` |
| `PowerDown` | `TelltaleType.POWER_DOWN` | ENGINE (alternative) | yes | 7 ms | 0.18 s | `active=POWER_DOWN=ON(…)` |
| `EngineOilPressure` | `TelltaleType.ENGINE_OIL_PRESSURE` | ENGINE_OIL (meaning differs) | yes | 10 ms | 0.15 s | `active=ENGINE_OIL_PRESSURE=ON(…)` |
| none | none | BRAKE_FLUID | no console control | | | |
| none | none | WASHER_FLUID | no console control | | | |

Payload facts the Step 5 adapter must handle:

- The callback and `getTelltalesInfo` carry only the telltales that are not Off. Turning a telltale Off removes its entry; with nothing on, the list is empty (`size=0`). Absence means off.
- `checkTelltalesCapabilities` returned `CapabilityConfig(accessType=READ)`.
- On registration the listener fires once with the current list.
- Only one telltale was on at a time in this run. Two on together was not tested.

## Dedicated Safety APIs

| API (permission `CAR_SAFETY`) | WarningType | Result on the emulator |
|---|---|---|
| `checkPowerDownWarningIndicatorCapabilities`, `hasPowerDownWarningIndicator`, `registerPowerDownWarningIndicator` | ENGINE | Supported (READ), value `false`. Did not change when the `PowerDown` telltale was On; no callback. |
| `checkAirbagCapabilities`, `hasSeatAirbagEnabled`, `registerSeatAirbagEnabled` | AIRBAG | Supported for ROW_1_LEFT and ROW_1_RIGHT, value `false`. Did not change when the `Airbag` telltale was On. |
| `checkAirbagDeployedCapabilities` | AIRBAG | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkSeatNotBuckledWarningCapabilities` | SEAT_BELT | `ERROR_NOT_SUPPORTED_PROPERTY` |
| `checkEngineOilLevelStateCapabilities`, `getEngineOilLevelState` | ENGINE_OIL | `ERROR_NOT_SUPPORTED_PROPERTY`; getter: "property ID is not in carPropertyConfig list … ENGINE_OIL_LEVEL" |
| `checkBrakeFluidLevelStateCapabilities`, `getBrakeFluidLevelState` | BRAKE_FLUID | `ERROR_NOT_SUPPORTED_PROPERTY`; getter fails the same way (0x21400730) |
| `checkLowWasherFluidWarningStateCapabilities`, `hasLowWasherFluidWarning` | WASHER_FLUID | `ERROR_NOT_SUPPORTED_PROPERTY`; getter fails the same way (0x2120071e) |

## Drive state

`DRIVE_STATE` set to REAL, console gear changed with the car at 0 km/h.

| Console | App log |
|---|---|
| start, gear P | `gear Value(P, AAOS)`, `uxRestricted Value(false, AAOS)`, `canShowDetail true` |
| `gear D` | `gear Value(D, AAOS)`, `canShowDetail false`, then `uxRestricted Value(true, AAOS)` about 60 ms later |
| `gear P` | `gear Value(P, AAOS)`, `uxRestricted Value(false, AAOS)`, `canShowDetail true` |

For the adapter's owner: it works as written. Its header still says "에뮬레이터 미시험", which can now be removed. The restriction turns on with gear D alone, before the car moves.

## Console motion commands

- `gear P`: readback gear Park, speed 0.
- `gear D` then `speed 30`: the write is accepted (readback 30), then the page's motion model coasts the car down by about 2.3 km/h each second (30, 28, 26, 23, 21, 19 over 5 s) because the accelerator is 0. A scenario that needs a steady speed has to re-send it, or accept the decay.

## What the emulator shows by itself

Screenshots were taken one second after each telltale turned On.

- For all six telltales the emulator's own cluster panel (left third of the screen) shows a small red telltale icon in its top-left corner.
- No alert card, banner or pop-up appeared for any of them, over our app or elsewhere. Nothing like the red "도어 열림" card.
- With `TPMS` on Blink the icon blinks; the screenshot caught it in the off phase.

So an in-app warning card does not duplicate a system alert for these telltales.

## Heads-up notification

Not tested. `android.permission.POST_NOTIFICATIONS` is not declared in the Manifest (`granted=false`), and the Manifest is shared. Proposed diff, not applied:

```diff
+    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## Recommendation

Drive from the Virtual Console in the demo, through `registerTelltales`:

| WarningType | Console telltale | Note |
|---|---|---|
| TIRE_PRESSURE | `TPMS` | Treat On and Blink as active. |
| SEAT_BELT | `SeatBelt` | |
| AIRBAG | `Airbag` | |
| ENGINE | `Engine` | `PowerDown` also arrives; mapping it to ENGINE as well is a team decision. |
| ENGINE_OIL | `EngineOilPressure` | This is an oil pressure lamp. The catalog copy says the oil may be low; check the wording before mapping it. |

Keep on the debug panel: BRAKE_FLUID and WASHER_FLUID. The console has no control for them and their SDK APIs are not supported on this emulator.

The dedicated `hasXxx` / `getXxx` Safety APIs add nothing here; the adapter needs only the Telltales API.
