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
    import uvicorn

    from server.webapp import app  # noqa: E402   (tanpa engine / TikTokLive)

    def main() -> None:
        host = "0.0.0.0"
        port = int(os.environ.get("TTL_PORT", "8080"))
        print(f"[WEB] TikTok Live Reader Engine (web-only) di http://{host}:{port}", flush=True)
        uvicorn.run(app, host=host, port=port, log_level="info")

else:
    from server.app import main  # noqa: E402

if __name__ == "__main__":
    main()
