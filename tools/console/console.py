#!/usr/bin/env python3
"""Remote control for the Pleos Connect Virtual Console (emulator v2.1.0).

Uses the console's undocumented internal HTTP API; see docs/c1/virtual-console-api.md.
The console page must stay open in a browser: it sends the heartbeat that keeps
writes enabled. This script never sends that heartbeat itself.

Usage:
  console.py state
  console.py telltale <Type> <Off|On|Blink>
  console.py gear <P|R|N|D>
  console.py speed <kph>
  console.py run <scenario.json>
"""
import http.client
import json
import sys
import time
from urllib.parse import urlencode

HOST, PORT = "127.0.0.1", 8088
NOT_READY = "Open http://127.0.0.1:8088 in a browser and keep it open"
GEARS = {"P": "Park", "R": "Reverse", "N": "Neutral", "D": "Drive"}
TELLTALE_STATES = {"off": "Off", "on": "On", "blink": "Blink"}
SETTLE_S = 2.0  # how long a readback may take to show a write
STEP_KEYS = {"gear", "telltale", "speed", "wait", "note"}


class ConsoleError(Exception):
    pass


def http_request(method, path):
    """One request on a fresh connection. Returns (status, body text)."""
    conn = http.client.HTTPConnection(HOST, PORT, timeout=5)
    try:
        conn.request(method, path)  # bodyless; http.client adds Content-Length: 0 for PUT
        response = conn.getresponse()
        return response.status, response.read().decode("utf-8", "replace")
    except OSError as e:
        raise ConsoleError(
            f"cannot reach http://{HOST}:{PORT} ({e}). Is the emulator running and is "
            f"'adb forward tcp:{PORT} tcp:{PORT}' set?"
        ) from e
    finally:
        conn.close()


class Console:
    def __init__(self, request=http_request, sleep=time.sleep, out=print):
        self.request, self.sleep, self.out = request, sleep, out
        self.mismatches = 0

    # --- reads

    def state(self):
        status, body = self.request("GET", "/vehicle/state")
        if status != 200:
            raise ConsoleError(f"GET /vehicle/state returned {status}: {body}")
        return json.loads(body)

    @staticmethod
    def readiness(s):
        return {
            "browser": bool(s.get("browser", {}).get("active")),
            "gear": bool(s.get("gear", {}).get("bridge_ready")),
            "telltales": bool(s.get("telltales", {}).get("bridge_ready")),
            "controls": bool(s.get("controls", {}).get("dds_ready", {}).get("all")),
        }

    @staticmethod
    def telltale_state(s, kind):
        # A missing entry means unknown, not Off.
        return s.get("telltales", {}).get("actual", {}).get(kind, {}).get("state")

    def print_state(self):
        s = self.state()
        ready = self.readiness(s)
        active = {
            k: v.get("state")
            for k, v in s.get("telltales", {}).get("actual", {}).items()
            if v.get("state") != "Off"
        }
        self.out(f"gear:            {s.get('gear', {}).get('actual', {}).get('value')}")
        self.out(f"speed_kph:       {s.get('controls', {}).get('speed_kph')}")
        self.out(f"telltales on:    {active or 'none reported'}")
        self.out(f"browser active:  {ready['browser']} (clients={s.get('browser', {}).get('clients')})")
        self.out(
            f"bridges ready:   gear={ready['gear']} telltales={ready['telltales']} "
            f"controls={ready['controls']}"
        )

    # --- writes

    def _write(self, bridge, path, params, label, expected, read):
        """Check readiness, send one PUT, read back, print expected vs actual. No retry."""
        ready = self.readiness(self.state())
        if not (ready["browser"] and ready[bridge]):
            raise ConsoleError(f"{NOT_READY} (browser={ready['browser']}, {bridge} bridge={ready[bridge]})")
        status, body = self.request("PUT", path + "?" + urlencode(params))
        if not 200 <= status < 300:
            raise ConsoleError(f"PUT {path}?{urlencode(params)} returned {status}: {body}")
        # Only the read is repeated while the value settles; the write is sent once.
        deadline = time.monotonic() + SETTLE_S
        while True:
            actual = read(self.state())
            if actual == expected or time.monotonic() >= deadline:
                break
            self.sleep(0.2)
        ok = actual == expected
        self.mismatches += not ok
        self.out(f"{label}: expected {expected}, actual {actual} -> {'OK' if ok else 'MISMATCH'}")
        return ok

    def telltale(self, kind, state):
        state = TELLTALE_STATES.get(state.lower())
        if state is None:
            raise ConsoleError("telltale state must be Off, On or Blink")
        types = self.state().get("telltales", {}).get("types", [])
        if kind not in types:
            raise ConsoleError(f"unknown telltale '{kind}'. Known: {', '.join(types)}")
        return self._write(
            "telltales",
            "/vehicle/telltales/simulator",
            {"color": "Red", "state": state, "type": kind},
            f"telltale {kind}",
            state,
            lambda s: self.telltale_state(s, kind),
        )

    def speed(self, kph):
        return self._write(
            "controls", "/vehicle/controls", {"speed": kph}, "speed_kph", kph,
            lambda s: s.get("controls", {}).get("speed_kph"),
        )

    def gear(self, letter):
        name = GEARS.get(letter.upper())
        if name is None:
            raise ConsoleError("gear must be P, R, N or D")
        if name == "Park":
            # Same as the page's P button: stop the motion model before Park.
            self._write(
                "controls", "/vehicle/controls", {"speed": 0, "accelerator": 0, "brake": 0},
                "speed_kph", 0, lambda s: s.get("controls", {}).get("speed_kph"),
            )
        return self._write(
            "gear", "/vehicle/gear", {"gear": name}, "gear", name,
            lambda s: s.get("gear", {}).get("actual", {}).get("value"),
        )

    # --- scenarios

    def run(self, steps):
        for i, step in enumerate(steps):
            if not isinstance(step, dict) or len(step.keys() & (STEP_KEYS - {"state"})) != 1 or step.keys() - STEP_KEYS - {"state"}:
                raise ConsoleError(f"step {i + 1} is not a valid step: {json.dumps(step, ensure_ascii=False)}")
            if "telltale" in step and "state" not in step:
                raise ConsoleError(f"step {i + 1}: telltale needs a state")
        turned_on = []
        try:
            for step in steps:
                self.out(f"[{time.strftime('%H:%M:%S')}] {json.dumps(step, ensure_ascii=False)}")
                if "gear" in step:
                    self.gear(step["gear"])
                elif "telltale" in step:
                    self.telltale(step["telltale"], step["state"])
                    if step["state"].lower() != "off" and step["telltale"] not in turned_on:
                        turned_on.append(step["telltale"])
                elif "speed" in step:
                    self.speed(step["speed"])
                elif "wait" in step:
                    self.sleep(step["wait"])
        except (ConsoleError, KeyboardInterrupt) as e:
            self.out(f"[{time.strftime('%H:%M:%S')}] stopped: {e or 'interrupted'}. Cleaning up once.")
            for action in [lambda: self.gear("P")] + [lambda k=k: self.telltale(k, "Off") for k in turned_on]:
                try:
                    action()  # gear("P") sends speed 0 first
                except ConsoleError as cleanup_error:
                    self.out(f"cleanup step failed: {cleanup_error}")
            raise


def main(argv):
    console = Console()
    try:
        if argv == ["state"]:
            console.print_state()
        elif len(argv) == 3 and argv[0] == "telltale":
            console.telltale(argv[1], argv[2])
        elif len(argv) == 2 and argv[0] == "gear":
            console.gear(argv[1])
        elif len(argv) == 2 and argv[0] == "speed" and argv[1].isdigit():
            console.speed(int(argv[1]))
        elif len(argv) == 2 and argv[0] == "run":
            with open(argv[1], encoding="utf-8") as f:
                console.run(json.load(f))
        else:
            print(__doc__)
            return 2
    except ConsoleError as e:
        print(e)
        return 1
    except KeyboardInterrupt:
        return 130
    return 1 if console.mismatches else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
