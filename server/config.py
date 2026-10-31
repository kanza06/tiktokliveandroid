"""Penyimpanan pengaturan server (settings_server.json).

Semua setting di sini adalah "setting aplikasi" yang juga dipakai client Android:
client membacanya lewat GET /api/settings dan mengubahnya lewat POST /api/settings.
"""
import json
import threading
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parent
ROOT = SERVER_DIR.parent
SETTINGS_FILE = SERVER_DIR / "settings_server.json"

# Sama dengan DEFAULTS di main.py (aplikasi desktop lama)
DEFAULTS = {
    "username": "",
    "opacity": 90,
    "font_size": 14,
    "font_family": "Segoe UI",
    "theme": "dark",
    "speed_unit": "kbps",
}

BOOL_KEYS = {
    "auto_connect": "Sambungkan otomatis saat server dijalankan",
    "auto_reconnect": "Coba sambung ulang otomatis saat terputus",
    "reconnect_interval": "Jeda sambung ulang (detik)",
}

VALID_SPEED_UNITS = ("kbps", "mbps", "MBps")
VALID_THEMES = ("dark", "light")

_lock = threading.Lock()


def default_config() -> dict:
    cfg = dict(DEFAULTS)
    cfg.update({
        "auto_connect": True,
        "auto_reconnect": True,
        "reconnect_interval": 15,
    })
    return cfg


def _clean(cfg: dict) -> dict:
    """Validasi & normalisasi nilai supaya client tidak pernah menerima data rusak."""
    out = default_config()
    out.update({k: v for k, v in cfg.items() if k in out})

    out["username"] = str(out.get("username") or "").strip().lstrip("@")
    try:
        out["opacity"] = min(100, max(30, int(out["opacity"])))
    except (TypeError, ValueError):
        out["opacity"] = DEFAULTS["opacity"]
    try:
        out["font_size"] = min(24, max(10, int(out["font_size"])))
    except (TypeError, ValueError):
        out["font_size"] = DEFAULTS["font_size"]
    out["font_family"] = str(out.get("font_family") or DEFAULTS["font_family"])
    if out["theme"] not in VALID_THEMES:
        out["theme"] = "dark"
    if out["speed_unit"] not in VALID_SPEED_UNITS:
        out["speed_unit"] = "kbps"
    try:
        out["reconnect_interval"] = min(300, max(5, int(out["reconnect_interval"])))
    except (TypeError, ValueError):
        out["reconnect_interval"] = 15
    out["auto_connect"] = bool(out["auto_connect"])
    out["auto_reconnect"] = bool(out["auto_reconnect"])
    return out


def load_settings() -> dict:
    cfg = default_config()
    try:
        cfg.update(json.loads(SETTINGS_FILE.read_text(encoding="utf-8")))
    except Exception:
        pass
    return _clean(cfg)


def save_settings(cfg: dict) -> dict:
    cfg = _clean(cfg)
    with _lock:
        try:
            SERVER_DIR.mkdir(parents=True, exist_ok=True)
            SETTINGS_FILE.write_text(json.dumps(cfg, indent=2), encoding="utf-8")
        except Exception:
            pass
    return cfg


def update_settings(patch: dict, base: dict | None = None) -> dict:
    """Gabungkan patch ke pengaturan lalu simpan. Semua kunci divalidasi."""
    cfg = dict(base if base is not None else load_settings())
    for k, v in (patch or {}).items():
        if k in cfg:
            cfg[k] = v
    return save_settings(cfg)
