"""Offline check of console.py against a fake console. Run: python3 -m unittest -v (in this folder)."""
import json
import unittest
from urllib.parse import parse_qs, urlsplit

from console import Console, ConsoleError


class FakeServer:
    def __init__(self, active=True, fail_on=None):
        self.active, self.fail_on, self.puts = active, fail_on, []
        self.gear, self.speed, self.telltales = "Park", 0, {}

    def request(self, method, path):
        if method == "GET":
            return 200, json.dumps({
                "browser": {"active": self.active, "clients": int(self.active)},
                "gear": {"bridge_ready": self.active, "actual": {"value": self.gear}},
                "telltales": {
                    "bridge_ready": self.active,
                    "types": ["TPMS", "Engine"],
                    "actual": {k: {"state": v} for k, v in self.telltales.items()},
                },
                "controls": {"speed_kph": self.speed, "dds_ready": {"all": self.active}},
            })
        url = urlsplit(path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        self.puts.append((url.path, q))
        if self.fail_on and self.fail_on.items() <= q.items():
            return 503, '{"error":"emulator_inactive"}'
        if url.path == "/vehicle/gear":
            self.gear = q["gear"]
        elif url.path == "/vehicle/controls":
            self.speed = int(q["speed"])
        else:
            self.telltales[q["type"]] = q["state"]
        return 200, "{}"


def console(server):
    return Console(request=server.request, sleep=lambda s: None, out=lambda line: None)


class ConsoleTest(unittest.TestCase):
    def test_inactive_console_blocks_writes(self):
        server = FakeServer(active=False)
        with self.assertRaises(ConsoleError) as e:
            console(server).telltale("TPMS", "On")
        self.assertIn("Open http://127.0.0.1:8088", str(e.exception))
        self.assertEqual(server.puts, [])

    def test_park_stops_motion_first(self):
        server = FakeServer()
        console(server).gear("P")
        self.assertEqual(server.puts, [
            ("/vehicle/controls", {"speed": "0", "accelerator": "0", "brake": "0"}),
            ("/vehicle/gear", {"gear": "Park"}),
        ])

    def test_failed_step_cleans_up_once(self):
        server = FakeServer(fail_on={"speed": "30"})
        steps = [{"gear": "D"}, {"telltale": "TPMS", "state": "On"}, {"speed": 30}, {"telltale": "Engine", "state": "On"}]
        with self.assertRaises(ConsoleError):
            console(server).run(steps)
        self.assertEqual((server.gear, server.speed, server.telltales), ("Park", 0, {"TPMS": "Off"}))
        self.assertEqual(len(server.puts), 6)  # D, TPMS On, speed 30 (503), speed 0, Park, TPMS Off

    def test_invalid_scenario_sends_nothing(self):
        server = FakeServer()
        with self.assertRaises(ConsoleError):
            console(server).run([{"gear": "D"}, {"telltail": "TPMS", "state": "On"}])
        self.assertEqual(server.puts, [])

    def test_mismatch_is_counted(self):
        server = FakeServer()
        c = console(server)
        c.speed(30)
        server.speed = 12  # the page's motion model overwrote it
        self.assertEqual(c.mismatches, 0)
        c._write("controls", "/vehicle/x", {"type": "TPMS", "state": "On"}, "speed_kph", 99,
                 lambda s: s["controls"]["speed_kph"])
        self.assertEqual(c.mismatches, 1)


if __name__ == "__main__":
    unittest.main()
