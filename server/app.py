"""TikTok Live Reader — Python Engine server.

Jalankan:  python -m server.app          (atau lewat run_server.bat)

Endpoint (dipakai client Android):
  GET  /api/health                 -> info server + IP LAN (untuk isi di HP)
  GET  /api/status                 -> data lengkap + event baru sejak ?since=SEQ
  GET  /api/settings               -> pengaturan aplikasi (sama dengan versi Py)
  POST /api/settings               -> ubah pengaturan
  GET  /api/connect?username=xxx   -> mulai koneksi ke live
  GET  /api/disconnect             -> putuskan koneksi
  GET  /api/demo?on=1&step=1       -> mode demo (data contoh, tanpa TikTok)
  GET  /api/proxy?url=xxx          -> proxy gambar (avatar & gift) untuk Android
  GET  /api/tunnel                 -> status tunnel publik (URL https dari rumah)
  POST /api/tunnel?on=1&provider=  -> nyalakan/matikan tunnel dari HP
  GET  /img/<url-gambar>           -> proxy gambar berbentuk path (dipakai APK)
  GET  /api/qr?text=xxx            -> gambar QR (untuk isi URL di HP)
  GET  /                           -> dashboard HTML sederhana

Keamanan (opsional, disarankan bila dibuka lewat tunnel):
  set env TTL_API_KEY=rahasia  -> semua /api/* wajib header X-API-Key
"""
from __future__ import annotations

import asyncio
import hmac
import io
import json
import os
import socket
import threading
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import httpx
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, Response

from . import config, tunnel as tunnel_mod
from .live_engine import LiveEngine, LiveState

APP_NAME = "TikTok Live Reader Engine"
VERSION = "1.1.0"
ROOT = Path(__file__).resolve().parent.parent
PORT = int(os.environ.get("TTL_PORT", "8000"))
API_KEY = os.environ.get("TTL_API_KEY", "").strip()

state = LiveState()
engine = LiveEngine(state)
IMAGE_DIR = ROOT / "image_cache"
IMAGE_DIR.mkdir(parents=True, exist_ok=True)
_tunnel: Optional[tunnel_mod.Tunnel] = None

# ---------------------------------------------------------------------------
# Lifecycle
# ---------------------------------------------------------------------------
_http: Optional[httpx.AsyncClient] = None
_demo_task: Optional[asyncio.Task] = None
_startup_error = ""


async def _boot_connect():
    cfg = config.load_settings()
    if cfg.get("username") and cfg.get("auto_connect", True):
        await asyncio.sleep(1)
        engine.connect(cfg["username"])


def _safe_boot() -> None:
    """Startup engine + auto-connect di thread sendiri.

    Di Fly.io tidak ada username tersimpan, jadi keduanya hanya menyentuh
    filesystem/thread. Kalau salah satu melempar exception, server HTTP tetap
    hidup agar health check /api/ping tidak gagal terus.
    """
    try:
        engine.start()
    except Exception as exc:  # noqa: BLE001
        print(f"[BOOT] engine gagal start: {exc}", flush=True)
        return
    try:
        cfg = config.load_settings()
        if cfg.get("username") and cfg.get("auto_connect", True):
            engine.connect(cfg["username"])
    except Exception as exc:  # noqa: BLE001
        print(f"[BOOT] auto-connect dilewati: {exc}", flush=True)


# ---------------------------------------------------------------------------
# Discovery: HP menemukan IP PC otomatis (tanpa setting IP manual)
#   * HP mengirim broadcast UDP ke port 47653 -> server menjawab JSON /api/ping
#   * server juga mengumumkan diri ke 255.255.255.255 tiap 3 detik
# ---------------------------------------------------------------------------
DISCOVERY_PORT = 47653
DISCONNECT = "\x00"


def _discovery_payload() -> bytes:
    return json.dumps({"ttl": 1, "app": APP_NAME, "version": VERSION,
                       "port": PORT}).encode("utf-8")


def _discovery_listener() -> None:
    """Jawab broadcast dari HP, dan kirim pengumuman sendiri (untuk app yang tidak bisa broadcast)."""
    sock = None
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        except OSError:
            pass
        sock.bind(("0.0.0.0", DISCOVERY_PORT))
        sock.settimeout(0.5)
    except OSError as exc:
        print(f"[DISCOVERY] tidak aktif: {exc}", flush=True)
        if sock:
            sock.close()
        return

    payload = _discovery_payload()
    last_advert = 0.0
    print(f"[DISCOVERY] siap di UDP {DISCOVERY_PORT} (HP menemukan IP PC otomatis)", flush=True)
    while True:
        try:
            data, addr = sock.recvfrom(1024)
            if data.strip() == b"TTL_DISCOVER":
                sock.sendto(payload, addr)
        except socket.timeout:
            pass
        except Exception:
            pass
        # pengumuman berkala supaya HP yang hanya bisa mendengar tetap menemukan server
        if time.time() - last_advert > 3.0:
            last_advert = time.time()
            try:
                sock.sendto(payload, ("255.255.255.255", DISCOVERY_PORT))
            except OSError:
                pass


def _start_discovery() -> None:
    threading.Thread(target=_discovery_listener, name="discovery", daemon=True).start()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    global _http, _tunnel
    _http = httpx.AsyncClient(timeout=15, follow_redirects=True)
    _start_discovery()      # HP bisa menemukan server ini sendiri
    # Engine & auto-connect dijalankan di background supaya server HTTP siap
    # lebih dulu dan health check Fly tidak menunggu (atau ikut gagal).
    threading.Thread(target=_safe_boot, daemon=True).start()
    if os.environ.get("TTL_TUNNEL", "1") != "0":
        _tunnel = _start_tunnel()
        threading.Thread(target=_print_tunnel_later, daemon=True).start()
    yield
    if _tunnel:
        _tunnel.stop()
    engine.disconnect("Server berhenti")
    if _http:
        await _http.aclose()


def _start_tunnel() -> tunnel_mod.Tunnel:
    """Tunnel dijalankan di thread supaya startup server tidak tertahan."""
    t = tunnel_mod.Tunnel(PORT)

    def run():
        ok = t.start()
        if ok:
            print(f"[TUNNEL] {t.provider}: {t.url}", flush=True)
        else:
            print("[TUNNEL] tidak aktif " + "; ".join(t.log[-2:]), flush=True)

    threading.Thread(target=run, name="tunnel", daemon=True).start()
    return t


app = FastAPI(title=APP_NAME, version=VERSION, docs_url="/docs", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def api_key_guard(request: Request, call_next):
    """Bila TTL_API_KEY diisi, /api/* hanya bisa diakses dengan header X-API-Key.

    Dashboard (/) tetap boleh dibuka, tapi dashboard juga mengirim header.
    """
    if API_KEY and request.url.path.startswith("/api/") and request.url.path != "/api/ping":
        token = request.headers.get("x-api-key") or request.query_params.get("key", "")
        if not hmac.compare_digest(token, API_KEY):
            return JSONResponse({"ok": False, "error": "API key salah / kosong"}, status_code=401)
    return await call_next(request)


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------
def lan_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def current_settings() -> dict:
    return config.load_settings()


# ---------------------------------------------------------------------------
# Endpoint dasar
# ---------------------------------------------------------------------------
@app.get("/api/health")
def health():
    cfg = current_settings()
    return {
        "app": APP_NAME,
        "version": VERSION,
        "ip": lan_ip(),
        "port": PORT,
        "username": cfg.get("username", ""),
        "mode": state.mode,
        "error": _startup_error,
        "tunnel": _tunnel.status()["url"] if _tunnel else None,
        "needs_key": bool(API_KEY),
        "discovery_port": DISCOVERY_PORT,
    }


@app.get("/api/ping")
def ping():
    """Jawaban ringan untuk pencarian server otomatis dari HP."""
    return {"ok": True, "app": APP_NAME, "version": VERSION, "port": PORT,
            "ip": lan_ip(), "needs_key": bool(API_KEY)}


@app.get("/api/tunnel")
def tunnel_status():
    """Status akses luar rumah (URL https publik)."""
    data = _tunnel.status() if _tunnel else {"provider": None, "url": None, "running": False, "tail": []}
    data.update({
        "available": tunnel_mod.providers_status(),
        "api_key_required": bool(API_KEY),
        "lan": f"http://{lan_ip()}:{PORT}",
    })
    return data


@app.post("/api/tunnel")
def tunnel_toggle(on: int = 1):
    """Nyalakan/matikan tunnel dari HP (mis. setelah cloudflared terpasang)."""
    global _tunnel
    if _tunnel:
        _tunnel.stop()
        _tunnel = None
    if on:
        _tunnel = _start_tunnel()
    return tunnel_status()


@app.get("/api/qr")
def qr(text: str):
    """QR code PNG berisi URL/text (tanpa dependency tambahan)."""
    try:
        import qrcode  # type: ignore
    except ImportError:
        return JSONResponse({"ok": False, "error": "pip install qrcode[pil]"}, status_code=503)
    img = qrcode.make(text)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return Response(content=buf.getvalue(), media_type="image/png")


@app.get("/api/status")
def status(since: int = 0, meta: int = 1):
    """Snapshot lengkap feed + event baru sejak SEQ.

    meta=0 menghemat data: daftar penonton per-orang + pengaturan tidak
    dikirim (dipakai client Android saat polling cepat).
    """
    data = state.snapshot(since=since)
    if meta:
        data["settings"] = current_settings()
    else:
        data.pop("active_list", None)
        data.pop("visitors_list", None)
        data.pop("gifters_list", None)
    return data


@app.get("/api/settings")
def get_settings():
    return current_settings()


@app.post("/api/settings")
async def post_settings(request: Request):
    try:
        patch = await request.json()
    except Exception:
        return JSONResponse({"ok": False, "error": "JSON tidak valid"}, status_code=400)
    if not isinstance(patch, dict):
        return JSONResponse({"ok": False, "error": "Harus objek JSON"}, status_code=400)
    cfg = config.update_settings(patch)
    return {"ok": True, "settings": cfg}


# ---------------------------------------------------------------------------
# Koneksi
# ---------------------------------------------------------------------------
@app.get("/api/connect")
def connect(username: str = ""):
    cfg = current_settings()
    username = (username or cfg.get("username") or "").strip().lstrip("@")
    if not username:
        return JSONResponse({"ok": False, "error": "username kosong"}, status_code=400)
    if username != cfg.get("username"):
        cfg = config.update_settings({"username": username}, cfg)
    if state.mode == "demo":
        state.reset()
    ok = engine.connect(username)
    return {"ok": ok, "username": username, "message": state.status_text}


@app.get("/api/disconnect")
async def disconnect():
    if state.mode == "demo":
        await stop_demo()
        return {"ok": True, "status": state.status_text}
    engine.disconnect("Terputus")
    return {"ok": True, "status": "Terputus"}


# ---------------------------------------------------------------------------
# Mode demo
# ---------------------------------------------------------------------------
async def _demo_loop():
    tick = 0
    while True:
        tick += 1
        state.demo_tick(tick)
        await asyncio.sleep(2.0)


@app.get("/api/demo")
async def demo(on: int = 1, step: int = 0):
    """on=1 mulai otomatis; step=N dorong satu data contoh (untuk tes cepat)."""
    global _demo_task
    if on:
        if step:
            if not _demo_task or _demo_task.done():
                state.demo_tick(step)
            return {"ok": True, "mode": "demo", "step": step}
        engine.disconnect("Mode demo")
        state.reset()
        if _demo_task and not _demo_task.done():
            _demo_task.cancel()
        _demo_task = asyncio.get_running_loop().create_task(_demo_loop())
        return {"ok": True, "mode": "demo"}
    await stop_demo()
    return {"ok": True, "mode": "idle"}


async def stop_demo():
    global _demo_task
    if _demo_task and not _demo_task.done():
        _demo_task.cancel()
    _demo_task = None
    state.reset()


# ---------------------------------------------------------------------------
# Proxy gambar (Android tidak bisa selalu memuat CDN TikTok secara langsung)
# ---------------------------------------------------------------------------
async def _proxy_image(url: str) -> Response:
    url = (url or "").strip()
    if url.startswith("/"):
        # path lokal server: /imgc/<nama file di image_cache>
        path = IMAGE_DIR / Path(url.rsplit("/", 1)[-1])
        if path.is_file():
            return FileResponse(path)
        return Response(status_code=404)
    if not url.lower().startswith(("http://", "https://")):
        return Response(status_code=400)
    if _http is None:
        return Response(status_code=503)
    # Beberapa CDN avatar TikTok (p16-common-sign / p19-common-sign) menolak
    # permintaan dengan header tambahan -> dicoba beberapa variasi header.
    ua = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
    variants = (
        {},                                                          # polos (paling sering berhasil)
        {"User-Agent": ua},
        {"User-Agent": ua, "Referer": "https://www.tiktok.com/",
         "Accept": "image/avif,image/webp,image/png,image/jpeg,*/*"},
        {"User-Agent": "Mozilla/5.0 (Linux; Android 13)", "Accept": "*/*"},
    )
    # URL yang didekode Android: "https%3A//host" -> perlu normalisasi
    if "%3a" in url.lower() or "%2f" in url.lower():
        from urllib.parse import unquote
        url = unquote(url)
        if not url.lower().startswith(("http://", "https://")):
            return Response(status_code=400)
    # sebagian CDN avatar membalas 403 bila datang dari UA browser penuh -> coba
    # juga varian tanpa header, dengan UA, dan dengan Referer
    last_status = 502
    for hdrs in variants:
        try:
            r = await _http.get(url, headers=hdrs)
            last_status = r.status_code
            if r.status_code != 200 or not r.content:
                continue
            ctype = r.headers.get("content-type", "image/jpeg")
            if not ctype.startswith("image/"):
                ctype = "image/jpeg"
            return Response(content=r.content, media_type=ctype,
                            headers={"Cache-Control": "public, max-age=604800"})
        except Exception:
            continue
    return Response(status_code=last_status if last_status >= 400 else 502)


@app.get("/api/proxy")
async def proxy(url: str = ""):
    """Proxy gambar lama (query ?url=...)."""
    return await _proxy_image(url)


@app.get("/img/{target:path}")
async def proxy_path(target: str):
    """Proxy gambar berbentuk path: /img/https://cdn.../x.png

    Dipakai APK: URL seperti https://p16-webcast.tiktokcdn.com/img/... mengandung
    host yang sama dengan prefix "/img/" sehingga bentuk path lebih aman daripada query.
    """
    url = target
    if "%3a" in url.lower() or "%2f" in url.lower():
        from urllib.parse import unquote
        url = unquote(url)
    if url.startswith("/"):
        return await _proxy_image(url)
    if not url.lower().startswith(("http://", "https://")):
        return Response(status_code=400)
    return await _proxy_image(url)


# ---------------------------------------------------------------------------
# Dashboard HTML sederhana (bisa dibuka dari browser HP untuk tes cepat)
# ---------------------------------------------------------------------------
DASHBOARD = """<!doctype html>
<html lang="id"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>TikTok Live Reader Engine</title>
<style>
 body{background:#121217;color:#EDEDF2;font-family:'Segoe UI',sans-serif;margin:0;padding:20px}
 .card{max-width:520px;margin:auto;background:#1C1C24;border:1px solid #2E2E3B;border-radius:16px;padding:18px}
 h1{font-size:18px;margin:0 0 4px} .muted{color:#A8A8BA;font-size:13px}
 .ok{color:#3DDC84} .bad{color:#E8204F}
 input{width:100%;box-sizing:border-box;background:#22222C;border:1px solid #2E2E3B;color:#EDEDF2;
   border-radius:8px;padding:10px;margin:10px 0}
 button{background:#E8204F;color:#fff;border:0;border-radius:8px;padding:10px 16px;font-weight:600}
 code{background:#252530;padding:2px 6px;border-radius:6px}
 #log{height:240px;overflow:auto;background:#141419;border-radius:10px;padding:10px;margin-top:12px;font-size:13px}
</style></head><body><div class="card">
<h1>TikTok Live Reader Engine</h1>
<div class="muted">Python engine untuk client Android</div>
<p id="st" class="muted">memuat...</p>
<p id="tn" class="muted">tunnel: memeriksa...</p>
<input id="u" placeholder="username TikTok (tanpa @)">
<div><button onclick="go()">Hubungkan</button> <button onclick="disc()" style="background:#252530">Putus</button></div>
<div id="log"></div>
</div>
<script>
async function st(){const r=await fetch('/api/status?since=0').then(r=>r.json());
 const e=document.getElementById('st');
 e.className=r.ok?'ok':'bad';e.textContent=(r.ok?'● ':'● ')+r.status+' · 👁'+r.active+' 👥'+r.visitors+' 🎁'+r.gifters;
 document.getElementById('u').value=document.getElementById('u').value||r.username;}
async function go(){await fetch('/api/connect?username='+encodeURIComponent(document.getElementById('u').value));
 document.getElementById('log').innerHTML='';st();}
async function disc(){await fetch('/api/disconnect');st();}
let seq=0;
async function poll(){const r=await fetch('/api/status?since='+seq).then(r=>r.json());seq=r.seq;
 const log=document.getElementById('log');
 for(const ev of r.events){
   let t='';if(ev.type==='comment')t='<b>'+ev.user+'</b>: '+ev.text;
   else if(ev.type==='gift')t='🎁 <b>'+ev.user+'</b> mengirim '+ev.gift+' x'+ev.count;
   else if(ev.type==='join')t='👋 '+ev.names.join(' · ')+' bergabung';
   else if(ev.type==='status')t='⚙ '+ev.text;
   else continue;
   const d=document.createElement('div');d.innerHTML=t;log.appendChild(d);}
 while(log.children.length>80)log.removeChild(log.firstChild);
 if(r.events.length)log.scrollTop=log.scrollHeight;}
async function tn(){const t=await fetch('/api/tunnel').then(r=>r.json());
 document.getElementById('tn').innerHTML = t.url
   ? ('🌍 Akses luar rumah: <b>'+t.url+'</b>')
   : ('🏠 Akses lokal: <b>'+t.lan+'</b> (tunnel: '+(t.available.cloudflared?'menyiapkan...':'cloudflared tidak terpasang')+')');}
tn();setInterval(tn,10000);st();setInterval(poll,2000);setInterval(st,5000);
</script></body></html>"""


@app.get("/", response_class=HTMLResponse)
def index():
    return DASHBOARD


def _print_tunnel_later():
    """Tunggu tunnel siap lalu cetak URL publik di jendela server."""
    for _ in range(80):
        time.sleep(0.5)
        if _tunnel and _tunnel.url:
            print("=" * 62)
            print(f"  AKSES DARI LUAR RUMAH ({_tunnel.provider}):")
            print(f"  {_tunnel.url}      <-- isi di aplikasi Android")
            if API_KEY:
                print("  API key     : (nilai TTL_API_KEY yang Anda set)")
            print("=" * 62, flush=True)
            return
        if _tunnel and _tunnel.provider and not _tunnel.status()["running"]:
            print("[TUNNEL] gagal menyala, lihat /api/tunnel", flush=True)
            return


def main():
    import uvicorn

    host = "0.0.0.0"
    ip = lan_ip()
    print("=" * 62)
    print(f"  {APP_NAME}  v{VERSION}")
    print("=" * 62)
    print(f"  PC ini      : http://127.0.0.1:{PORT}")
    print(f"  Untuk HP    : http://{ip}:{PORT}      <-- isi di aplikasi Android")
    print("  Pastikan HP & PC di Wi-Fi yang sama, lalu izinkan port ini di firewall.")
    if API_KEY:
        print("  API key     : aktif (isi di aplikasi Android)")
    print("=" * 62, flush=True)
    uvicorn.run(app, host=host, port=PORT, log_level="info")


if __name__ == "__main__":
    main()
