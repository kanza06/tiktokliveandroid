# ============================================================
# TikTok Live Reader — Python Engine (Fly.io)
# Hanya server/ yang di-deploy. APK Android & file desktop TIDAK ikut.
# ============================================================
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# dependensi sistem minimal (TikTokLive butuh ini)
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements_server.txt .
RUN pip install --upgrade pip && pip install -r requirements_server.txt

COPY server ./server
COPY server_app.py .

# port HARUS 8080: Fly meneruskan trafik ke http_service.internal_port = 8080.
# server_app.py mengikat host 0.0.0.0 (bukan 127.0.0.1) lewat uvicorn.
ENV TTL_PORT=8080 \
    TTL_TUNNEL=0 \
    TTL_DISCOVERY=0 \
    TTL_WEB_ONLY=1

EXPOSE 8080

CMD ["python", "server_app.py"]
