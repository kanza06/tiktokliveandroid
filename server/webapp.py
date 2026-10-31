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
def status(since: int = 0):
    """Belum ada engine di mode web, jadi tidak ada event."""
    return {
        "ok": True,
        "mode": "web",
        "seq": 0,
        "since": since,
        "events": [],
        "stats": {"comments": 0, "gifts": 0, "viewers": 0, "likes": 0},
    }


@app.get("/")
def index():
    return {
        "app": APP_NAME,
        "version": VERSION,
        "mode": "web",
        "endpoints": ["/api/ping", "/api/health", "/api/discover", "/api/status", "/docs"],
    }
