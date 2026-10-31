"""Akses dari luar rumah tanpa memindahkan engine keluar PC.

Kenapa begini? TikTok memblokir IP datacenter (Render/VPS). Yang aman:
koneksi TikTok tetap keluar dari IP rumah Anda, sedangkan HP masuk lewat
"terowongan" (tunnel) HTTPS.

Didukung (otomatis dipakai bila tersedia):
  1. cloudflared  (https://xxx.trycloudflare.com)  - gratis, tanpa akun
  2. ngrok        (https://xxx.ngrok-free.app)     - gratis, butuh token
  3. playit.gg    (https://xxx.playit.gg)          - gratis, butuh akun

Prioritas dapat diatur lewat server/tunnel.json:
    {"provider": "auto", "cloudflared": "", "ngrok_token": "", "playit_dir": ""}
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import threading
import time
from pathlib import Path
from typing import Optional

SERVER_DIR = Path(__file__).resolve().parent
CONFIG_FILE = SERVER_DIR / "tunnel.json"

URL_PATTERNS = [
    re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com"),
    re.compile(r"https://[a-z0-9-]+\.ngrok-free\.app"),
    re.compile(r"https://[a-z0-9-]+\.ngrok\.io"),
    re.compile(r"https://[a-z0-9-]+\.playit\.gg"),
]


def _cfg() -> dict:
    cfg = {"provider": "auto", "cloudflared": "", "ngrok_token": "", "playit_dir": ""}
    try:
        cfg.update(json.loads(CONFIG_FILE.read_text(encoding="utf-8")))
    except Exception:
        pass
    return cfg


def _which(name: str, extra: str = "") -> Optional[str]:
    if extra and Path(extra).exists():
        return extra
    local = SERVER_DIR / (name + (".exe" if name != "playit" else ""))
    if local.exists():
        return str(local)
    return shutil.which(name)


def providers_status() -> dict:
    cfg = _cfg()
    return {
        "cloudflared": bool(_which("cloudflared", cfg.get("cloudflared", ""))),
        "ngrok": bool(_which("ngrok")),
        "playit": bool(_which("playit", cfg.get("playit_dir", ""))),
        "preferred": cfg.get("provider", "auto"),
    }


class Tunnel:
    """Menjalankan satu tunnel dan membaca URL publik dari outputnya."""

    def __init__(self, port: int):
        self.port = port
        self.url: Optional[str] = None
        self.provider: Optional[str] = None
        self.log: list[str] = []
        self._proc: Optional[subprocess.Popen] = None
        self._stop = False

    # ------------------------------------------------------------- jalankan
    def start(self) -> bool:
        cfg = _cfg()
        order = ["cloudflared", "ngrok", "playit"]
        pref = cfg.get("provider", "auto")
        if pref in order:
            order.remove(pref)
            order.insert(0, pref)

        for name in order:
            if self._spawn(name, cfg):
                return True
        self.log.append("Tidak ada tunnel yang tersedia (cloudflared/ngrok/playit).")
        return False

    def _spawn(self, name: str, cfg: dict) -> bool:
        try:
            if name == "cloudflared":
                exe = _which("cloudflared", cfg.get("cloudflared", ""))
                if not exe:
                    return False
                cmd = [exe, "tunnel", "--no-autoupdate", "--url",
                       f"http://127.0.0.1:{self.port}"]
            elif name == "ngrok":
                exe = _which("ngrok")
                token = cfg.get("ngrok_token", "")
                if not exe:
                    return False
                if token:
                    subprocess.run([exe, "config", "add-authtoken", token],
                                   capture_output=True, timeout=30)
                cmd = [exe, "http", str(self.port), "--log=stdout"]
            else:  # playit
                exe = _which("playit", cfg.get("playit_dir", ""))
                if not exe:
                    return False
                cmd = [exe, "--auto-tunnel", f"tcp:127.0.0.1:{self.port}"]
        except Exception as e:
            self.log.append(f"{name}: {e}")
            return False

        try:
            self._proc = subprocess.Popen(
                cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding="utf-8", errors="replace",
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
        except Exception as e:
            self.log.append(f"{name}: {e}")
            return False

        self.provider = name
        threading.Thread(target=self._reader, daemon=True).start()
        # tunggu URL muncul (maks 30 detik)
        for _ in range(60):
            if self.url or self._stop or (self._proc and self._proc.poll() is not None):
                break
            time.sleep(0.5)
        return bool(self.url)

    def _reader(self):
        proc = self._proc
        if proc is None or proc.stdout is None:
            return
        for line in proc.stdout:
            line = line.strip()
            if line:
                self.log.append(line[-200:])
                del self.log[:-60]
                if not self.url:
                    for pat in URL_PATTERNS:
                        m = pat.search(line)
                        if m:
                            self.url = m.group(0)
                            break

    # ---------------------------------------------------------------- info
    def status(self) -> dict:
        alive = bool(self._proc and self._proc.poll() is None)
        return {
            "provider": self.provider,
            "url": self.url,
            "running": alive,
            "tail": self.log[-6:],
        }

    def stop(self):
        self._stop = True
        if self._proc and self._proc.poll() is None:
            try:
                self._proc.terminate()
            except Exception:
                pass
