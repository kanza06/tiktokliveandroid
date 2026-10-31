"""TikTok Live Reader — Python Engine (server).

Jalankan server:
    venv312\\Scripts\\python.exe server_app.py
atau cukup klik dua kali run_server.bat

Server ini membaca komentar, gift, dan penonton dari TikTok Live, lalu
melayaninya untuk aplikasi Android (Kotlin) di jaringan yang sama.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from server.app import main  # noqa: E402

if __name__ == "__main__":
    main()
