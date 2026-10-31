"""TikTok Live Reader — Python Engine (server).

Jalankan server:
    venv312\\Scripts\\python.exe server_app.py
atau cukup klik dua kali run_server.bat

Server ini membaca komentar, gift, dan penonton dari TikTok Live, lalu
melayaninya untuk aplikasi Android (Kotlin) di jaringan yang sama.

Di Fly.io (env TTL_WEB_ONLY=1): boot hanya memuat modul web. Import TikTokLive
baru dilakukan saat ada koneksi live sungguhan, jadi health check tidak
bergantung pada startup engine.
"""
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

if os.environ.get("TTL_WEB_ONLY", "0") == "1":
    import socket

    import uvicorn

    from server.webapp import app  # noqa: E402   (tanpa engine / TikTokLive)

    def _port_from_env() -> int:
        """Fly menyuntikkan PORT; TTL_PORT menang kalau diisi."""
        for key in ("TTL_PORT", "PORT"):
            raw = os.environ.get(key, "").strip()
            if raw.isdigit():
                return int(raw)
        return 8080

    def _port_busy(host: str, port: int) -> bool:
        """Cek cepat: port sudah didengarkan proses lain?"""
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(1.0)
            try:
                return s.connect_ex(("127.0.0.1", port)) == 0
            except OSError:
                return False

    def main() -> None:
        # WAJIB 0.0.0.0 + port 8080 (internal_port di fly.toml).
        # 127.0.0.1 akan membuat Fly tidak bisa menjangkau app -> Checks 0/1.
        host = "0.0.0.0"
        port = _port_from_env()
        print(f"[WEB] bind http://{host}:{port}")
        if _port_busy(host, port):
            # Server lain sudah melayani port ini; jangan crash-boot-loop,
            # cukup pakai proses yang sudah ada.
            print(f"[WEB] port {port} sudah dipakai server lain, skip mulai uvicorn", flush=True)
            return
        print(f"[WEB] TikTok Live Reader Engine (web-only) siap di :{port}", flush=True)
        uvicorn.run(app, host=host, port=port, log_level="info")

else:
    from server.app import main  # noqa: E402

if __name__ == "__main__":
    main()
