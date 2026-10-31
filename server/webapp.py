"""Aplikasi web RINGAN untuk Fly.io (mode TTL_WEB_ONLY=1).

Modul ini sengaja tidak meng-import `server.app`, `server.live_engine`, atau
paket `TikTokLive`. Tujuannya: container harus bisa hidup dan menjawab health
check `/api/ping` walau engine/TikTokLive gagal start.

Kontrak endpoint disamakan dengan `server/app.py` supaya klien Android tetap
kenal: /api/ping, /api/health, /api/discover, /api/status.
"""
from __future__ import annotations

import os
import socket
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

APP_NAME = "TikTok Live Reader Engine"
VERSION = "1.1.0"
PORT = int(os.environ.get("TTL_PORT", "8080"))
API_KEY = os.environ.get("TTL_API_KEY", "").strip()
DISCOVERY_PORT = 47653

# Harus sama dengan DEFAULTS di server/config.py agar APK menerima bentuk yang dikenal.
DEFAULT_SETTINGS = {
    "username": "",
    "opacity": 90,
    "font_size": 14,
    "font_family": "Segoe UI",
    "theme": "dark",
    "speed_unit": "kbps",
}


def lan_ip() -> str:
    """IP keluar container; di Fly nilainya IP privat mesin."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


@asynccontextmanager
async def lifespan(_app: FastAPI):
    print(f"[WEB] {APP_NAME} v{VERSION} siap di port {PORT}", flush=True)
    yield
    print("[WEB] berhenti", flush=True)


app = FastAPI(title=APP_NAME, version=VERSION, lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/ping")
def ping():
    """Health check Fly + pencarian server dari HP."""
    return {"ok": True, "app": APP_NAME, "version": VERSION, "port": PORT,
            "ip": lan_ip(), "needs_key": bool(API_KEY)}


@app.get("/api/health")
def health():
    return {
        "app": APP_NAME,
        "version": VERSION,
        "ip": lan_ip(),
        "port": PORT,
        "mode": "web",
        "error": "",
        "tunnel": None,
        "needs_key": bool(API_KEY),
        "discovery_port": DISCOVERY_PORT,
    }


@app.get("/api/discover")
def discover():
    """Dipanggil APK Android v2.6 untuk menemukan alamat server."""
    return {"ttl": 1, "app": APP_NAME, "version": VERSION, "port": PORT}


@app.get("/api/status")
def status(since: int = 0, meta: int = 0):  # noqa: ARG001
    """Snapshot kosong dengan bentuk yang dibaca APK Android (JsonParse.snapshot).

    Mode web belum menyalakan engine, jadi tidak ada event; tapi semua kunci
    harus ada supaya APK tidak menampilkan "Server tidak terjangkau".
    """
    return {
        "ok": True,
        "username": "",
        "status": "Terhubung ke server cloud (Fly)",
        "mode": "web",
        "error": "",
        "seq": 0,
        "live_seconds": 0,
        "active": 0,
        "visitors": 0,
        "gifters": 0,
        "likes": 0,
        "follows": 0,
        "shares": 0,
        "comments": [],
        "gifts": [],
        "joins": [],
        "active_list": [],
        "visitors_list": [],
        "gifters_list": [],
        "events": [],
        "settings": DEFAULT_SETTINGS,
    }


@app.get("/api/settings")
def get_settings():
    return dict(DEFAULT_SETTINGS)


@app.post("/api/settings")
def post_settings(_patch: dict | None = None):
    """Mode web tidak menyimpan pengaturan; kembalikan nilai default."""
    return dict(DEFAULT_SETTINGS)


@app.get("/")
def index():
    return {
        "app": APP_NAME,
        "version": VERSION,
        "mode": "web",
        "endpoints": ["/api/ping", "/api/health", "/api/discover",
                      "/api/status", "/api/settings", "/docs"],
    }
