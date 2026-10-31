"""Uji cepat engine tanpa perlu koneksi TikTok (mode demo)."""
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi.testclient import TestClient  # noqa: E402

from server.app import app  # noqa: E402


def main():
    with TestClient(app) as c:
        print("health :", json.dumps(c.get("/api/health").json(), ensure_ascii=False))
        print("demo   :", c.get("/api/demo?on=1").json())
        time.sleep(5.5)
        for i in range(1, 6):
            c.get(f"/api/demo?on=1&step={i}")
            time.sleep(0.2)
        d = c.get("/api/status?since=0").json()
        print("status :", d["status"], "| active", d["active"], "| visitors", d["visitors"],
              "| gifters", d["gifters"], "| likes", d["likes"])
        print("comments:", len(d["comments"]), "gifts:", len(d["gifts"]),
              "joins:", len(d["joins"]), "events:", len(d["events"]),
              "seq:", d["seq"])
        print("settings:", json.dumps(d["settings"], ensure_ascii=False))
        r = c.post("/api/settings", json={"font_size": 50, "theme": "light",
                                          "speed_unit": "xx", "username": "@abc"})
        print("patch  :", json.dumps(r.json(), ensure_ascii=False))
        c.post("/api/settings", json={"font_size": 14, "theme": "dark",
                                      "username": "lucas.vic"})
        bad = c.get("/api/status?since=999999").json()
        print("since  :", len(bad["events"]), "events (harus 0)")
        print("demo off:", c.get("/api/demo?on=0").json())
        print("after  :", c.get("/api/status").json()["status"])
        idx = c.get("/")
        print("index  :", idx.status_code, len(idx.text), "bytes")


if __name__ == "__main__":
    main()
