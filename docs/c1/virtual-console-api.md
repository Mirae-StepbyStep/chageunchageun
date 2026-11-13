# Pleos Connect Virtual Console API — captured 2026-11-12

## 1. Summary

- **Transport:** plain HTTP. UI JavaScript uses `fetch`; writes are bodyless `PUT` requests with URL query parameters. State and catalog use `GET`. Browser liveness uses `POST`. No vehicle-control WebSocket or SSE was found.
- **Base URL:** `http://127.0.0.1:8088`, forwarded to emulator port 8088.
- **Can a plain script drive it? Partly verified.** Reads work without a browser, cookie, token, or session. The write protocol and browser-heartbeat protocol are reproducible with Python's standard library, but successful writes were **not verified**: all four authorized requests returned HTTP 503 `{"error":"emulator_inactive"}`.
- **End-state requirement was not achieved.** Final readback reports gear `Drive`, TPMS absent from `telltales.actual`, browser inactive, and DDS bridges unavailable. An absent TPMS entry is unknown, **not Off**. No successful control change was observed.
- The running page identifies itself as **Pleos Connect Virtual Console**. Emulator version v2.1.0 is supplied by the task; the page does not independently establish that version.
- The complete downloaded catalog contains **63 features**, including three hidden features, across seven visible domains plus hidden Misc. There are **37 telltale identifiers**.

Only four vehicle PUTs were sent, once each, with two-second spacing before readback. No heartbeat, browser launch, SDK/app test, package installation, port scan, or emulator-shell modification was performed. The app, Gradle files, and Manifest were not changed.

## 2. Connection

Setup observed:

```text
$ adb devices
List of devices attached
emulator-5554 device

$ adb forward --list
emulator-5554 tcp:8088 tcp:8088
```

The forward already existed, so it was not recreated. If missing in a future session:

```sh
adb -s emulator-5554 forward tcp:8088 tcp:8088
curl -i http://127.0.0.1:8088/
```

The root returned HTTP 200, `Content-Type: text/html; charset=utf-8`, and `Content-Length: 150815`. It includes inline JavaScript/CSS and local JS/CSS dependencies.

### Liveness is separate from authentication

`sendBrowserHeartbeat` creates a random browser client ID (`crypto.randomUUID`, with a timestamp/random fallback) and sends:

```http
POST /browser/session?client=<URL-encoded-client-id> HTTP/1.1
Host: 127.0.0.1:8088
Content-Length: 0
```

It sends immediately, then every **1000 ms**. On page exit it sends `POST /browser/session?client=<same-id>&closing=1`, normally through `navigator.sendBeacon`. The `keepalive:true` fetch option lets a request outlive page teardown; it is not a persistent socket requirement. Responses inspected here use `Connection: close`.

`GET /vehicle/state` advertises `browser.timeout_ms:3000`. Observed `browser.active:false`, `clients:0`, `emulator_active:false`, and `bridge_ready:false`, together with the four 503s, establish that the console was inactive. The heartbeat appears to activate/maintain its DDS publishers, but its actual activation behavior and side effects were **not tested**. No challenge/response handshake, cookie, authorization token, required Origin/Referer, custom header, or subscription request appears in the control code. The client ID is sent only to `/browser/session`, not attached to vehicle PUTs.

For a future independent client: start heartbeats, inspect state for active/ready bridges, then send sequential writes while maintaining heartbeats; send `closing=1` when finished. This ordering is inferred from page startup and observed inactivity rejection, not a successfully tested handshake. Exact activation delay, heartbeat response body, and effects of deactivation remain unknown. Sending heartbeats here would exceed the constraint allowing only four state-changing requests, so it was deliberately not tried.

A real browser is **not shown to be technically required**: its liveness call is ordinary HTTP, with a client-generated ID. Playwright is therefore not currently justified. If heartbeat emulation later fails, the simplest fallback is to keep the normal console page open and use the same direct PUT client; browser clicks could be a further fallback, but neither alternative was built or tested.

## 3. Commands

Requests below are relative to the base URL. Query values are case-sensitive; URL-encode them (especially `#` in colors). No JSON request body or `Content-Type` header is used by the page. Generic catalog booleans become the strings `true` and `false`; cockpit controls also use `1` and `0`.

“Verified live” means a successful state change, not merely an attempted request. None of the writes succeeded. Expected success behavior below comes from UI code (`response.ok` followed by state refresh); the exact success status/body is unknown.

| Action | Exact request or template | Expected response / readback | Verified live |
|---|---|---|---|
| TPMS On | `PUT /vehicle/telltales/simulator?color=Red&state=On&type=TPMS` | Success should update `telltales.actual.TPMS.state`; observed 503 `emulator_inactive` | No, rejected |
| TPMS Off | `PUT /vehicle/telltales/simulator?color=Red&state=Off&type=TPMS` | Same; observed 503 `emulator_inactive` | No, rejected |
| TPMS Blink | `PUT /vehicle/telltales/simulator?color=Red&state=Blink&type=TPMS` | Expected 2xx and state `Blink`; body unknown | No |
| Other telltale | `PUT /vehicle/telltales/simulator?color=Red&state={Off,On,Blink}&type={identifier}` | Expected 2xx; read `telltales.actual[identifier].state` | No |
| Gear D | `PUT /vehicle/gear?gear=Drive` | Expected `gear.actual.value=Drive`; observed 503 | No, rejected |
| Gear P | `PUT /vehicle/gear?gear=Park` | Expected `gear.actual.value=Park`; observed 503 | No, rejected |
| Gear R | `PUT /vehicle/gear?gear=Reverse` | Expected 2xx; read `gear.actual.value` | No |
| Gear N | `PUT /vehicle/gear?gear=Neutral` | Expected 2xx; read `gear.actual.value` | No |
| Speed 30 km/h | `PUT /vehicle/controls?speed=30` | Read `controls.speed_kph`; partial-field acceptance not live-tested | No |
| Cockpit motion snapshot | `PUT /vehicle/controls?speed=30&accelerator=0&brake=0&steering=0` | UI's combined format; read `controls` | No |
| Open front-left door | `PUT /vehicle/body?door_fl=true` | Read `body.doors.front_left=true`; 2xx expected | No |
| Close front-left door | `PUT /vehicle/body?door_fl=false` | Read `body.doors.front_left=false`; 2xx expected | No |

The four door fields are `door_fl`, `door_fr`, `door_rl`, `door_rr` (front left/right, rear left/right). Cockpit aliases use `door_fl=1` / `door_fl=0`, etc. Neither form was live-tested.

### Full telltale list

All identifiers below share Off/On/Blink. The catalog fixes `color=Red` for every telltale, including TPMS. State advertises colors `White`, `Green`, `Blue`, `Yellow`, `Red`, `Gray`; alternate colors were not tested. These are independent telltale publications: setting `DoorOpen` is not the same request as physically opening a door, and setting `TPMS` does not set tire pressure.

| Label | Exact identifier |
|---|---|
| Parking Brake | `ParkingBrake` |
| Electronic Parking Brake | `ElectronicParkingBrake` |
| Seat Belt | `SeatBelt` |
| Door Open | `DoorOpen` |
| ABS | `ABS` |
| MDPS | `MDPS` |
| TPMS | `TPMS` |
| Low Volt Battery | `LowVoltBattery` |
| ESC | `ESC` |
| ESC Off | `ESCOff` |
| Airbag | `Airbag` |
| AFS | `AFS` |
| Master Warning | `MasterWarning` |
| Auto Hold | `AutoHold` |
| Auto High Beam | `AutoHighBeam` |
| Headlamp Low | `HeadlampLow` |
| Headlamp High | `HeadlampHigh` |
| Position Lamp | `PositionLamp` |
| Front Fog Lamp | `FrontFogLamp` |
| Rear Fog Lamp | `RearFogLamp` |
| Engine Oil Pressure | `EngineOilPressure` |
| Engine Temperature | `EngineTemperature` |
| Emergency Call | `EmergencyCall` |
| EV System | `EVSystem` |
| Power Down | `PowerDown` |
| RBS | `RBS` |
| Immobilizer | `Immobilizer` |
| Engine | `Engine` |
| LED Headlamp | `LEDHeadlamp` |
| Preheat | `Preheat` |
| Particle Filter | `ParticleFilter` |
| H2 Sensor | `H2Sensor` |
| H2 Leak | `H2Leak` |
| Passenger Airbag Off | `PassengerAirbagOff` |
| Passenger Airbag On | `PassengerAirbagOn` |
| Turn Signal Left | `TurnSignalLeft` |
| Turn Signal Right | `TurnSignalRight` |

### Complete catalog by domain

Every catalog feature is listed below, including `visible:false` entries. `{value}` must be replaced by an allowed value; `{area}` by an exact listed area. For `{field_for_area}`, use the arrow mapping in the area column. Constant parameters are included in each request template. Range limits/steps are **UI-declared**, not independently validated server limits. All rows expect 2xx plus the indicated state-path update when active; exact response bodies are unknown. All are **verified live: no** (gear/telltales were attempted and rejected).

Readback paths refer to the JSON returned by `GET /vehicle/state`; `{area}` is substituted without changing its string/numeric spelling.

#### Vehicle

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `notification-1` | `PUT /vehicle/notifications/simulator?id=1&display={value}` | `false` (Hide); `true` (Show) | — | `notifications.actual.1.display` |

#### Motion

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `gear-selection` | `PUT /vehicle/gear?gear={value}` | `Park` (P); `Reverse` (R); `Neutral` (N); `Drive` (D) | — | `gear.actual.value` |
| `brake-pedal-status` | `PUT /vehicle/controls?brake={value}` | 0…100; step 1 % | — | `controls.brake_pct` |
| `perf-odometer` | `PUT /vehicle/odometer?traveled_distance_km={value}` | 0…1000000; step 1 km | — | `odometer.traveled_distance_km` |
| `trip-driving-time` **hidden** | `PUT /vehicle/odometer?usage_time_min={value}` | 0…525600; step 1 min | — | `odometer.usage_time_min` |
| `steering-angle` | `PUT /vehicle/controls?steering={value}` | -470…500; step 1 deg | — | `controls.steering_deg` |
| `steering-wheel-heat` | `PUT /vehicle/steering-wheel-heat?level={value}` | `0` (Off); `1` (Low); `2` (Medium); `3` (High) | — | `steering_wheel_heat.level` |

#### ADAS

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `cruise-control-state` | `PUT /vehicle/driving-mode?ad_mode_state={value}` | `not_ready` (Not ready); `ready` (Ready); `longitudinal` (Longitudinal); `lateral` (Lateral); `full_autonomous` (Full autonomous) | — | `driving_mode.ad_mode_state` |
| `cruise-control-target-speed` | `PUT /vehicle/driving-mode?target_speed_mps={value}` | 5.556…27.778; step 0.277775 m/s | — | `driving_mode.target_speed_mps` |

#### Convenience

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `etcs-activated` | `PUT /vehicle/etcs?activated={value}` | `false` (Off); `true` (On) | — | `etcs.activated` |
| `etcs-scenario` | `PUT /vehicle/etcs?scenario={value}` | `card-insert` (Card In); `card-remove` (Card Out); `tollgate-entrance` (Entrance); `tollgate-exit` (Exit) | — | `etcs.last_scenario` |
| `door-pos` | `PUT /vehicle/body?{field_for_area}={value}` | `false` (Close); `true` (Open) | `front_left` → `door_fl`, `front_right` → `door_fr`, `rear_left` → `door_rl`, `rear_right` → `door_rr` | `body.doors.{area}` |
| `door-lock` | `PUT /vehicle/body?doors_locked={value}` | `false` (Unlock); `true` (Lock) | — | `body.door_locks.all` |
| `trunk-position` | `PUT /vehicle/body?{field_for_area}={value}` | `false` (Close); `true` (Open) | `frunk` → `frunk`, `trunk` → `trunk` | `body.closures.{area}` |
| `cabin-lights-switch` | `PUT /vehicle/body?cabin_light={value}` | `false` (Off); `true` (On) | — | `body.cabin_light.on` |
| `reading-lights-switch` | `PUT /vehicle/reading-lights?value={value}&area={area}` | `false` (Off); `true` (On) | `front_left`, `front_right`, `rear_left`, `rear_right` | `body.reading_lights.{area}` |
| `ambient-light-switch` **hidden** | `PUT /vehicle/body?ambient_mode={value}` | `Off` (Off); `On` (On); `Auto` (Auto) | — | `body.ambient_light.mode` |
| `ambient-light-brightness` | `PUT /vehicle/body?ambient_brightness={value}` | 0…100; step 1 % | — | `body.ambient_light.brightness_pct` |
| `ambient-light-color` | `PUT /vehicle/body?ambient_color={value}` | `#00e5ff` (Cyan); `#2979ff` (Blue); `#aa00ff` (Purple); `#ffffff` (White) | — | `body.ambient_light.color` |
| `exterior-lights-mode` | `PUT /vehicle/lights?feature=headlamp_mode&value={value}` | `Off` (Off); `Auto` (Auto); `Position` (Tail lamp); `LowBeam` (Low beam) | — | `lights.headlamp_mode` |
| `high-beam-lights` | `PUT /vehicle/lights?feature=high_beam&value={value}` | `false` (Off); `true` (On) | — | `lights.high_beam_on` |
| `hazard-lights` | `PUT /vehicle/lights?feature=hazard&value={value}` | `false` (Off); `true` (On) | — | `lights.hazard_on` |
| `stop-lights` | `PUT /vehicle/lights?feature=stop_light&value={value}` | `false` (Off); `true` (On) | — | `lights.stop_light_on` |
| `door-lights` | `PUT /vehicle/lights?feature=door_light&value={value}&area={area}` | `false` (Off); `true` (On) | `frunk`, `trunk` | `lights.door_lights.{area}` |
| `escort-lights` **hidden** | `PUT /vehicle/lights?feature=escort_enabled&value={value}` | `false` (Off); `true` (On) | — | `lights.escort_enabled` |
| `turn-signal-state` | `PUT /vehicle/turn-signal?mode={value}` | `Off` (Off); `BlinkLeft` (Blink left); `BlinkRight` (Blink right); `FlashLeft` (Flash left); `FlashRight` (Flash right) | — | `lights.turn_signal_mode` |
| `window-position` | `PUT /vehicle/body?{field_for_area}={value}` | 0…100; step 1 % | `front_left` → `window_fl`, `front_right` → `window_fr`, `rear_left` → `window_rl`, `rear_right` → `window_rr` | `body.windows.{area}` |
| `window-lock` | `PUT /vehicle/window-lock?locked={value}&area={area}` | `false` (Unlock); `true` (Lock) | `all`, `rear_left`, `rear_right` | `window_lock.locked.{area}` |
| `seat-fore-aft-position` **hidden** | `PUT /vehicle/seat?kind=position&control=slide&value={value}&area={area}` | 0…100; step 1 % | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.positions.slide.{area}` |
| `seat-fore-aft-move` **hidden** | `PUT /vehicle/seat?kind=move&control=slide&value={value}&area={area}` | `-1` (Negative); `0` (Stop); `1` (Positive) | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.moves.slide.{area}` |
| `seat-backrest-position` **hidden** | `PUT /vehicle/seat?kind=position&control=recline&value={value}&area={area}` | 0…100; step 1 % | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.positions.recline.{area}` |
| `seat-backrest-move` **hidden** | `PUT /vehicle/seat?kind=move&control=recline&value={value}&area={area}` | `-1` (Negative); `0` (Stop); `1` (Positive) | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.moves.recline.{area}` |
| `seat-height-position` **hidden** | `PUT /vehicle/seat?kind=position&control=height&value={value}&area={area}` | 0…100; step 1 % | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.positions.height.{area}` |
| `seat-height-move` **hidden** | `PUT /vehicle/seat?kind=move&control=height&value={value}&area={area}` | `-1` (Negative); `0` (Stop); `1` (Positive) | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.moves.height.{area}` |
| `seat-tilt-position` **hidden** | `PUT /vehicle/seat?kind=position&control=tilt&value={value}&area={area}` | 0…100; step 1 % | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.positions.tilt.{area}` |
| `seat-tilt-move` **hidden** | `PUT /vehicle/seat?kind=move&control=tilt&value={value}&area={area}` | `-1` (Negative); `0` (Stop); `1` (Positive) | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.moves.tilt.{area}` |
| `seat-lumbar-position` **hidden** | `PUT /vehicle/seat?kind=position&control=lumbar&value={value}&area={area}` | 0…100; step 1 % | `driver`, `passenger` | `seat.positions.lumbar.{area}` |
| `seat-lumbar-move` **hidden** | `PUT /vehicle/seat?kind=move&control=lumbar&value={value}&area={area}` | `-1` (Negative); `0` (Stop); `1` (Positive) | `driver`, `passenger` | `seat.moves.lumbar.{area}` |
| `seat-occupancy` | `PUT /vehicle/seat?kind=occupied&value={value}&area={area}` | `false` (Empty); `true` (Occupied) | `driver`, `passenger`, `rear_left`, `rear_right` | `seat.occupied.{area}` |
| `windshield-wipers-switch` | `PUT /vehicle/body?{field_for_area}={value}` | `Off` (Off); `Auto` (Auto); `Low` (Low); `High` (High) | `front` → `front_wiper`, `rear` → `rear_wiper` | `body.wipers.{area}.mode` |
| `glove-box-door-pos` | `PUT /vehicle/glove-box?opened={value}` | `false` (Close); `true` (Open) | — | `glove_box.opened` |

#### Safety

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `telltales` | `PUT /vehicle/telltales/simulator?color=Red&state={value}&type={area}` | `Off` (Off); `On` (On); `Blink` (Blink) | `ParkingBrake`, `ElectronicParkingBrake`, `SeatBelt`, `DoorOpen`, `ABS`, `MDPS`, `TPMS`, `LowVoltBattery`, `ESC`, `ESCOff`, `Airbag`, `AFS`, `MasterWarning`, `AutoHold`, `AutoHighBeam`, `HeadlampLow`, `HeadlampHigh`, `PositionLamp`, `FrontFogLamp`, `RearFogLamp`, `EngineOilPressure`, `EngineTemperature`, `EmergencyCall`, `EVSystem`, `PowerDown`, `RBS`, `Immobilizer`, `Engine`, `LEDHeadlamp`, `Preheat`, `ParticleFilter`, `H2Sensor`, `H2Leak`, `PassengerAirbagOff`, `PassengerAirbagOn`, `TurnSignalLeft`, `TurnSignalRight` | `telltales.actual.{area}.state` |

#### Energy

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `power-system-actual` **hidden** | `PUT /vehicle/power-system/simulator?area_id=0&state={value}` | `Off` (Off); `Ready` (Ready); `On` (On); `Driving` (Driving) | — | `power_system.actual.0.state` |
| `range-remaining` | `PUT /vehicle/odometer?remaining_range_m={value}` | 0…1000000; step 1000 m | — | `odometer.remaining_range_m` |
| `ev-battery-level` | `PUT /vehicle/ev-battery?field=battery_level&value={value}` | 0…200; step 0.1 kWh | — | `ev_battery.current_energy_kwh` |
| `ev-battery-soc` | `PUT /vehicle/ev-battery?field=soc&value={value}` | 0…100; step 1 % | — | `ev_battery.state_of_charge` |
| `ev-battery-capacity` | `PUT /vehicle/ev-battery?field=capacity&value={value}` | 0…200; step 0.1 kWh | — | `ev_battery.maximum_capacity_kwh` |
| `ev-charge-current-limit` | `PUT /vehicle/ev-battery?field=current_limit&value={value}` | 10…48; step 0.5 A | — | `ev_battery.maximum_current_a` |
| `ev-charge-percent-limit` | `PUT /vehicle/ev-battery?field=percent_limit&value={value}` | 50…100; step 5 % | — | `ev_battery.charge_limit_pct` |
| `ev-charge-switch` | `PUT /vehicle/ev-battery?field=charge_switch&value={value}` | `false` (Off); `true` (On) | — | `ev_battery.charging_activated` |
| `ev-charge-port` | `PUT /vehicle/ev-battery?field=charge_port_open&value={value}` | `false` (Closed); `true` (Open) | — | `ev_battery.charging_door_opened` |

#### Climate

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `hvac-power` | `PUT /vehicle/hvac?power={value}` | `false` (Off); `true` (On) | — | `hvac.power_on` |
| `hvac-ac` | `PUT /vehicle/hvac?ac={value}` | `false` (Off); `true` (On) | — | `hvac.ac_on` |
| `hvac-auto` | `PUT /vehicle/hvac?auto={value}` | `false` (Off); `true` (On) | — | `hvac.auto_on` |
| `hvac-dual` | `PUT /vehicle/hvac?dual={value}` | `false` (Off); `true` (On) | — | `hvac.dual_on` |
| `hvac-recirc` | `PUT /vehicle/hvac?recirc={value}` | `false` (Fresh); `true` (Recirculate) | — | `hvac.recirc_on` |
| `hvac-fan-speed` | `PUT /vehicle/hvac?fan_speed={value}` | 0…8; step 1 | — | `hvac.fan_speed` |
| `hvac-fan-direction` | `PUT /vehicle/hvac?{field_for_area}={value}` | `1` (Face); `2` (Floor); `4` (Defrost) | `driver` → `driver_direction`, `passenger` → `passenger_direction` | `hvac.{area}.fan_direction` |
| `hvac-defroster` | `PUT /vehicle/hvac?{field_for_area}={value}` | `false` (Off); `true` (On) | `front_defroster_on` → `front_defroster`, `rear_defroster_on` → `rear_defroster` | `hvac.{area}` |
| `hvac-temperature-set` | `PUT /vehicle/hvac?{field_for_area}={value}` | 16…30; step 0.5 °C | `driver` → `driver_temp`, `passenger` → `passenger_temp` | `hvac.{area}.temp_c` |
| `hvac-seat-temperature` | `PUT /vehicle/hvac?{field_for_area}={value}` | `-3` (V3); `-2` (V2); `-1` (V1); `0` (Off); `1` (H1); `2` (H2); `3` (H3) | `driver` → `driver_seat`, `passenger` → `passenger_seat` | `hvac.{area}.seat_temp` |
| `hvac-air-cleaner` | `PUT /vehicle/hvac-air-cleaner?activated={value}` | `false` (Off); `true` (On) | — | `hvac_air_cleaner.activated` |

#### Misc (hidden domain)

| Feature ID | Request | Allowed values / range | Areas / field mapping | Readback path |
|---|---|---|---|---|
| `tire-pressure` **hidden** | `PUT /vehicle/tire-pressure?pressure_kpa={value}&area={area}` | 0…500; step 1 kPa | `1114113`, `1114191`, `1179649`, `1179727` | `tire_pressure.pressure_kpa.{area}` |

### Additional cockpit controls and state-advertised values

| Action | Request / fields | Values and limits | Expected response | Verified live |
|---|---|---|---|---|
| Vehicle speed | `PUT /vehicle/controls?speed={value}` | 0…240 km/h, cockpit rounds to integer | 2xx expected; inspect `controls.speed_kph` | No |
| Accelerator | `PUT /vehicle/controls?accelerator={value}` | 0…100 percent, integer cockpit values | 2xx expected; `controls.accelerator_pct` | No |
| Steering | `PUT /vehicle/controls?steering={value}` | Cockpit and state: −540…540 degrees; catalog: −470…500. Cockpit snaps absolute values ≤5 to zero | 2xx expected; `controls.steering_deg` | No |
| HVAC transport mode | `PUT /vehicle/hvac/mode?mode={value}` | `subscribe_hvac`, `subscribe_hvac_base` | 2xx expected; JSON body parsed by UI; error field on failure | No |
| Cockpit HVAC aliases | `PUT /vehicle/hvac?fan=3`; `PUT /vehicle/hvac?sync=1` | `fan` 0…8; `sync` 0/1; catalog uses `fan_speed` and `dual` instead | 2xx expected; JSON body parsed, shape unknown | No |
| Combined air distribution | `PUT /vehicle/hvac?driver_direction=3` (or `passenger_direction`) | Bitmask: Face 1, Floor 2, Defrost 4; cockpit combines 1…7. Base mode only 1,2,3,4,6 | 2xx expected; mode errors recognized by UI | No |
| Ambient brightness alias | `PUT /vehicle/body?ambient_brightness_pct=50` | 0…100; catalog uses `ambient_brightness` | 2xx expected; inspect `body` | No |
| Custom ambient color | `PUT /vehicle/body?ambient_color=%23ffab00` | HTML color picker supplies `#RRGGBB`; cockpit presets also include `#00e676`, `#ffab00`, `#ff4d8d` | 2xx expected; inspect `body` | No |
| Additional ETCS scenarios | `PUT /vehicle/etcs?scenario={value}` | State advertises `power-on`, `power-off`, `card-insert`, `card-remove`, `tollgate-entrance`, `tollgate-exit`, `tollgate-open`, `tollgate-failure` | 2xx expected; scenario effects untested | No |
| Notification identifiers | `PUT /vehicle/notifications/simulator?id={id}&display={true,false}` | State advertises IDs 0…286; catalog exposes only ID 1 | 2xx inferred beyond ID 1, not validated | No |
| Power system state | `PUT /vehicle/power-system/simulator?area_id=0&state=StandBy` | `StandBy` appears in state in addition to catalog Off/Ready/On/Driving | 2xx inferred, not validated | No |

Speed is a direct console input, not a requirement to simulate throttle. However, the running browser computes acceleration/deceleration each animation frame and commits changing motion every ~140 ms; it can overwrite externally supplied speed. The UI's **P button also sends speed/accelerator/brake zero before Park**. Direct `gear=Park` alone is a different operation; we did not send the extra controls write. A future demo should begin stationary if only gear/telltales may change.

### Exact live request and response record

No cookie jar, auth, session identifier, or browser was used. The client was Python `http.client.HTTPConnection`; each request used a fresh connection, an empty body, and the exact headers below. Every PUT was followed after two seconds by one `GET /vehicle/state` (HTTP 200). The four response bodies were identical; these are failures, not success examples.

#### Attempt 1

```http
PUT /vehicle/telltales/simulator?color=Red&state=On&type=TPMS HTTP/1.1
Host: 127.0.0.1:8088
Accept-Encoding: identity
Content-Length: 0

```

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Content-Length: 29
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, PUT, POST, OPTIONS
Access-Control-Allow-Headers: Content-Type
Connection: close

{"error":"emulator_inactive"}
```

Readback: `gear.actual.value=Drive`; `telltales.actual={}`; `browser.active=false`; `browser.clients=0`. Gear/telltale bridges remained false.

#### Attempt 2

```http
PUT /vehicle/telltales/simulator?color=Red&state=Off&type=TPMS HTTP/1.1
Host: 127.0.0.1:8088
Accept-Encoding: identity
Content-Length: 0

```

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Content-Length: 29
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, PUT, POST, OPTIONS
Access-Control-Allow-Headers: Content-Type
Connection: close

{"error":"emulator_inactive"}
```

Readback: `gear.actual.value=Drive`; `telltales.actual={}`; `browser.active=false`; `browser.clients=0`. Gear/telltale bridges remained false.

#### Attempt 3

```http
PUT /vehicle/gear?gear=Drive HTTP/1.1
Host: 127.0.0.1:8088
Accept-Encoding: identity
Content-Length: 0

```

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Content-Length: 29
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, PUT, POST, OPTIONS
Access-Control-Allow-Headers: Content-Type
Connection: close

{"error":"emulator_inactive"}
```

Readback: `gear.actual.value=Drive`; `telltales.actual={}`; `browser.active=false`; `browser.clients=0`. Gear/telltale bridges remained false.

#### Attempt 4

```http
PUT /vehicle/gear?gear=Park HTTP/1.1
Host: 127.0.0.1:8088
Accept-Encoding: identity
Content-Length: 0

```

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Content-Length: 29
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, PUT, POST, OPTIONS
Access-Control-Allow-Headers: Content-Type
Connection: close

{"error":"emulator_inactive"}
```

Readback: `gear.actual.value=Drive`; `telltales.actual={}`; `browser.active=false`; `browser.clients=0`. Gear/telltale bridges remained false.

## 4. Reading state

```sh
curl --fail-with-body http://127.0.0.1:8088/vehicle/state
curl --fail-with-body http://127.0.0.1:8088/vehicle/catalog
```

Both worked without auth or liveness activation. The browser uses `cache:"no-store"`, polls state every 900 ms, and fetches the catalog during startup. There is no discovered initial WebSocket message or subscription requirement. The state includes values, some timestamps/sources, bridge readiness, DDS topic names, and browser-liveness information. JSON paths:

| Value | JSON location |
|---|---|
| Gear | `gear.actual.value` |
| TPMS state | `telltales.actual.TPMS.state` (missing in this capture) |
| Other telltale | `telltales.actual[identifier].state` |
| Speed, pedals, steering | `controls.speed_kph`, `accelerator_pct`, `brake_pct`, `steering_deg` |
| Doors | `body.doors.front_left`, `front_right`, `rear_left`, `rear_right` |
| Browser presence | `browser.active`, `clients`, `timeout_ms` |
| DDS readiness | `gear.bridge_ready`, `telltales.bridge_ready`, `controls.dds_ready` |
| Other controls | Complete per-feature readback paths in section 3 |

The UI first selects `state[feature.state_key]`, then traverses `state_path` by dots, replacing `{area}`. Treat missing data as unknown. Cached gear `Drive` does not prove a currently active DDS publication, and a successful HTTP response alone would not establish delivery to the Android app. This investigation did not inspect or test app subscriptions.

## 5. Minimal Python example (standard library; not run)

**The write sequence cannot be certified working in this capture because activation was outside the allowed test scope.** The following 31-line example is usable once a normal console session is already active and maintaining its own one-second heartbeat. It checks readiness and fails before writes on the captured inactive setup. It performs exactly the requested D → wait 3 s → TPMS On → wait 8 s → P → wait 5 s → TPMS Off sequence and reads each change back. No dependency is needed. Keep the console page open throughout; do not manipulate its controls during the sequence. Activating that page may affect publisher state beyond these four controls and was not done here.

```python
import json
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8088"

def state():
    with urlopen(BASE + "/vehicle/state", timeout=5) as response:
        return json.load(response)

def put(path, **params):
    request = Request(BASE + path + "?" + urlencode(params), method="PUT")
    with urlopen(request, timeout=5) as response:
        response.read()

s = state()
if not (s["browser"]["active"] and s["gear"]["bridge_ready"]
        and s["telltales"]["bridge_ready"]):
    raise RuntimeError("Keep an active, DDS-ready console session open first")
for kind, value, delay in [("gear", "Drive", 3), ("tpms", "On", 8),
                           ("gear", "Park", 5), ("tpms", "Off", 0)]:
    if kind == "gear":
        put("/vehicle/gear", gear=value)
        actual = state()["gear"]["actual"]["value"]
    else:
        put("/vehicle/telltales/simulator", color="Red", state=value, type="TPMS")
        actual = state()["telltales"]["actual"].get("TPMS", {}).get("state")
    if actual != value:
        raise RuntimeError(f"{kind}: expected {value}, got {actual}")
    time.sleep(delay)
```

The code deliberately stops on the first error; it does not retry or send extra cleanup writes. If interrupted or rejected mid-sequence, it cannot guarantee Park/Off. Readback may also require a settling delay on an active backend; latency is unmeasured. To remove the open-page prerequisite, implement the plain-HTTP heartbeat from section 2 and verify its activation/cleanup effects in a separately authorized test. No browser-automation dependency is indicated by the recovered code.

## 6. Risks and limitations

- This is an internal, undocumented interface. Endpoint names, query aliases, enum spelling, ranges, catalog visibility, DDS topics, and session timeouts may change between emulator builds. Pin the asset/catalog hashes below rather than relying only on v2.1.0.
- All four live changes were rejected. **Final gear P and TPMS Off are unconfirmed and were not achieved in the console's readback.** Final cached gear was Drive; TPMS had no entry. No additional activation or repeat request was sent to force the result.
- Readback metadata changed independently during observation: multiple domain timestamps advanced and glove-box source metadata changed from `desired` to `web-activation`. Visible control values in those domains remained the same in the compared snapshots. The cause is unknown; this prevents claiming the backend was globally quiescent. This client sent no activation request.
- The HTTP service being up and the Android emulator being listed by adb do not mean its vehicle publisher is active. GET state can return cached values with every relevant bridge unavailable.
- Heartbeat activation/deactivation probably controls more than liveness alone (many state sections have `emulator_active`, source `web-activation` / `web-deactivation`). Those side effects are inferred and untested; starting a session cannot be assumed to preserve all other controls.
- Catalog ranges differ from cockpit/state limits (notably steering). HVAC `dual` versus `sync`, `fan_speed` versus `fan`, and ambient brightness aliases need active-backend verification. Some additional values appear only in state, not catalog options.
- Generic telltale requests always include Red from the catalog, regardless of the symbol's conventional color. Color omission/default behavior is unknown.
- Generic UI calls check HTTP success, while some cockpit writes ignore status. Do not copy their lack of error handling. Success response schemas and asynchronous DDS delivery timing were not established.
- The browser runs its own motion physics and has special Park sequencing. Concurrent UI and external writers can race; heartbeat traffic itself is not a control-write sequence.
- Feature inventory is complete for the captured catalog and inspected cockpit code, not proof of every hidden server endpoint. No undiscovered paths, other ports, or other hosts were probed. Three malformed double-slash asset requests returned 404 during download; corrected single-slash references were then retrieved successfully.
- Downloaded frontend assets were inspected statically, not executed. Three.js asset-loading fetch/XHR code is unrelated to vehicle transport. No source-map directive was found; no guessed `.map` URL was requested. 3D models/textures are not JS/CSS and were not downloaded.

## 7. Evidence

The local REA skill at `/Users/jaelee/claude-skills/rea/.agents/skills/reverse-engineer-anything/SKILL.md` and its JavaScript/recovery guides informed this workflow. The REA CLI was not on PATH, no local `dist` build was present, and no REA MCP tool was available. No package was acquired. Findings are ordinary shell/static-source and direct HTTP evidence, **not REA-generated Evidence IDs**.

All downloaded originals and live transcripts are under `/tmp/vconsole/`; this location is temporary. Inline scripts/styles were also extracted for inspection, but the original `index.html` line numbers below are canonical. The HTML has no separate application-control bundle: its controls are readable inline JS. The catalog is server JSON consumed by that JS, so exact feature IDs/ranges originate in the catalog, not hard-coded minified functions.

| Finding | Source and line range / selector |
|---|---|
| CSS/import map | `index.html:159–160` |
| Gear enum and client ID | `index.html:385–388` |
| Lazy highway dependency | `index.html:494–513`, `loadHighway` |
| Heartbeat and unload behavior | `index.html:515–524`, `sendBrowserHeartbeat` |
| Gear writes and special Park sequence | `index.html:526–574`, gear button callback |
| Steering clamp/snap | `index.html:601–611`, `setSteeringAngle` |
| Doors/windows/wipers/ambient input | `index.html:643–719`, `bindCabinControls` |
| HVAC mode, combined directions | `index.html:776–834`, `bindHvacModeControl`, `nextFanDirection` |
| ETCS scenarios | `index.html:885–898`, `bindEtcsControls` |
| Motion physics / speed clamp | `index.html:1140–1164`, `physicsTick` |
| Motion query format and rate limiting | `index.html:1166–1195`, `controlsSnapshot`, `commitControls` |
| HVAC query aliases | `index.html:1210–1288`, `hvacSnapshot`, `commitHvacControls` |
| Body query aliases | `index.html:1290–1343`, `bodySnapshot`, `commitBodyControls` |
| Catalog visibility and readback traversal | `index.html:1365–1384`, `apiVisibleFeatures`, `apiReadPath`, `apiFeatureValue` |
| Generic PUT construction, fixed parameters, area selection | `index.html:1386–1410`, `setApiFeature` |
| UI ranges/options and area handling | `index.html:1437–1477`, `renderApiBrowser` |
| Catalog GET | `index.html:1480–1492`, `loadApiCatalog` |
| State GET and displayed readiness | `index.html:1493–1524`, `refresh` |
| Startup, polling frequency, legacy JS assets | `index.html:1887–1895` |
| All 63 features, limits, endpoint/parameter schemas | `catalog.json:1`, select `features[id=…]`; readable copy `catalog.pretty.json` |
| All 37 telltales, fixed Red, Off/On/Blink | `catalog.json:1`, `features[id=telltales]` |
| Runtime colors, extra values, limits, inactive browser/bridges | `state-before.json:1` and `state-after-4.json:1` |
| Exact four requests/responses | `live-records.json`, entries `ordinal=1…4`; reproduced in section 3 |
| Readback after each PUT | `state-after-1.json` through `state-after-4.json` |
| Recursive ES-module dependency graph | `highway-view.js:1–5`; `highway-weather.js:1`; `highway-windshield.js:1–2`; `vendor/three/addons/loaders/GLTFLoader.js:1–69` |
| Graphics resource fetch/XHR only | `vendor/three.legacy.min.js:2` (FileLoader / ImageBitmapLoader); `vendor/three/three.core.min.js:6` (same loaders) |

### Downloaded HTML / JS / CSS inventory and identity

| Original URL path | Bytes | SHA-256 |
|---|---:|---|
| `/` | 150815 | `50e52e8a0b23f297775bcc84c35400ad0e8185c066facb003a1b22b62a60dfe3` |
| `/highway.css` | 4290 | `7398d20e00d5befab648ad0617f6260f0bbd26d3bb1b19abb2c0d72f1984eb9f` |
| `/highway-simulation.js` | 5358 | `c34245339f86fbc04453b371363b51713c6accdea7251374ba6c575574d11677` |
| `/highway-view.js` | 10714 | `f1c4d09912d0ac738068cff132930f2d11e99bc6ce697a83b8bbd3d20864da1d` |
| `/highway-weather.js` | 6099 | `e0c3cc9b578fa4b5295e6a99598c9d8fb0f84cc7b002274f2c7daae6ed31ef69` |
| `/highway-windshield.js` | 7052 | `2fb9050c16fff2f89ad93ef0db482f3c4e1e6e93a8faf75025b5ac74236d9c2a` |
| `/vendor/GLTFLoader.legacy.js` | 96723 | `63c8c57f637a7345187bfab9417c4d9ab6f64da1a8c674596912980ba0f1f5b8` |
| `/vendor/RGBELoader.legacy.js` | 12462 | `7212ce23ba1d9f45f9a4986beb779811eeafd7b0a6b5d897f4182a0edb6c1423` |
| `/vendor/three/addons/loaders/GLTFLoader.js` | 114959 | `97642d720f16cc9a0c9844934198e4d0c023bea8e89576d0f7545d03b2d103d2` |
| `/vendor/three/addons/utils/BufferGeometryUtils.js` | 35539 | `d700b4c4584b9ff53a04692624d2363019f6d1df2978a207389168c6ae647a65` |
| `/vendor/three/addons/utils/SkeletonUtils.js` | 11535 | `b1632a703206c3d830de9fcbe515696770d04b71a15ee6b50afa6d2c3298c86f` |
| `/vendor/three/three.core.min.js` | 384222 | `6486aa0d719cfa87ec88dc47223b59b1fb8417a1a407fc0e52467c943e2f8cc9` |
| `/vendor/three/three.module.min.js` | 364998 | `36a60b0120335f89a80a0dab70292292b0ec414b3d05e83cd09a3ea428c6712a` |
| `/vendor/three.legacy.min.js` | 653449 | `a8a966c3de0bac23630957e8b6ceb1330725bc2889a0b2dad952d710797ba179` |

Catalog SHA-256: `f5468e672f284b98f479d44588587ddce42166ca5b38cc89d7226acf308aa6c2`.

The original page has one inline style block, one import map, two inline executable scripts, three classic script sources, and a recursively loaded module graph. All referenced JS/CSS in that graph were saved; the inline pieces are also retained as `inline-*` extracts. No external host was contacted.
