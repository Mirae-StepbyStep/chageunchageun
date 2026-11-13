# Virtual Console remote

`console.py` drives the Pleos Connect Virtual Console over HTTP so the C1 probe and demo can replay exact sequences. It uses only the Python standard library and is not part of the Android build.

This uses an **undocumented internal API**, captured from emulator v2.1.0 in `docs/c1/virtual-console-api.md`. It may break on any other emulator build. Catalog SHA-256 at capture time:

```
f5468e672f284b98f479d44588587ddce42166ca5b38cc89d7226acf308aa6c2
```

## Prerequisites

1. The Pleos Connect emulator v2.1.0 is running.
2. Port 8088 is forwarded: `adb forward tcp:8088 tcp:8088`
3. `http://127.0.0.1:8088` is open in a browser tab and stays open. The page sends the heartbeat that enables writes; the script does not. Without it every write is refused and the script exits before sending anything.

Do not use the page's own controls while a scenario runs. The page has its own motion model and can overwrite speed.

## Commands

Run from this folder.

| Command | What it does |
|---|---|
| `python3 console.py state` | Prints gear, speed, active telltales, browser session and bridge readiness |
| `python3 console.py telltale <Type> <Off\|On\|Blink>` | Sets one telltale, for example `telltale TPMS On` |
| `python3 console.py gear <P\|R\|N\|D>` | Sets the gear. `P` first sends speed 0, accelerator 0, brake 0, as the page's P button does |
| `python3 console.py speed <kph>` | Sets the speed |
| `python3 console.py run <scenario.json>` | Replays a scenario |

Every write is sent once, then the state is read back and printed as `expected … actual … -> OK` or `MISMATCH`. Writes are never retried. The exit code is 0 when everything matched, 1 on a mismatch or error.

Telltale names are the console's identifiers (`TPMS`, `SeatBelt`, `Airbag`, `Engine`, `PowerDown`, `EngineOilPressure`, …). An unknown name prints the full list.

## Scenarios

A scenario is a JSON list of steps, one key each:

```json
[
  {"note": "free text, only printed"},
  {"gear": "D"},
  {"speed": 30},
  {"wait": 3},
  {"telltale": "TPMS", "state": "On"}
]
```

The whole file is validated before the first write. Each step is printed with a timestamp. A mismatch is reported and the run continues. If a write fails or you press Ctrl-C, the script tries one cleanup: speed 0, gear P, and Off for every telltale the scenario turned on.

| File | Sequence |
|---|---|
| `scenarios/c1_tpms.json` | D, 30 km/h, wait 3 s, TPMS On, wait 10 s, speed 0, P, wait 8 s, TPMS Off |
| `scenarios/c1_probe_all.json` | TPMS, SeatBelt, Airbag, Engine, PowerDown, EngineOilPressure in turn: On, wait 4 s, Off, wait 2 s. Does not touch the gear; put the car in P first |

## Check without an emulator

```
python3 -m unittest
```
