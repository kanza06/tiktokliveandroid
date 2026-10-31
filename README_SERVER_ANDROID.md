# TikTok Live Reader — Python Engine + Client Android 📱🎥

Versi ini memecah aplikasi desktop menjadi dua bagian:

| Bagian | Teknologi | Fungsi |
|---|---|---|
| **Server (engine)** | Python + TikTokLive + FastAPI | Membaca komentar, gift, penonton TikTok Live dari PC/laptop |
| **Client** | Android Kotlin (APK) | Menampilkan komentar & gift di HP, setting sama persis dengan versi Py |

> Aplikasi Py lama (`main.py` / `.exe`) tetap bisa dipakai. Mode server ini **tidak mengubah** `main.py`,
> hanya membaca & menulis `settings.json`-nya lewat API.

---

## 0. PC tetap jadi server? Bisa — dan itu yang disarankan ✅

| Cara | Server dijalankan di | HP akses lewat | Aman dari blokir TikTok? |
|---|---|---|---|
| `run_server.bat` | **PC ini** | Wi-Fi rumah (IP LAN) | ✅ ya |
| `run_server_tunnel.bat` | **PC ini** | Internet (URL `https://xxx.trycloudflare.com`) | ✅ ya (koneksi keluar tetap IP rumah) |
| Render / VPS | datacenter | Internet | ❌ sering diblokir TikTok |

Jadi **tidak perlu pindah ke Render**. Engine tetap di komputer ini; tunnel hanya
"pintu masuk" untuk HP, bukan tempat TikTok dihubungi.

### Akses dari luar rumah (tunnel)

1. Klik dua kali **`run_server_tunnel.bat`**
2. Buka `run_server_tunnel.bat` dengan Notepad → ganti baris
   `set TTL_API_KEY=ganti-dengan-kunci-rahasia-anda` dengan kunci pilihan Anda
3. Tunggu baris di jendela server:

   ```
   AKSES DARI LUAR RUMAH (cloudflared):
   https://xxxx-yyyy.trycloudflare.com      <-- isi di aplikasi Android
   ```

4. Di aplikasi Android: 🌐 → tempel URL itu → isi **API key** yang sama → *Simpan & Sambung*

Catatan:
- URL `trycloudflare.com` **berganti setiap server dijalankan**. Tombol **URL Tunnel**
  di aplikasi bisa mengambil URL baru otomatis (selama HP masih memakai IP LAN).
- API key wajib diisi bila memakai tunnel — tanpa itu siapa pun yang tahu URL bisa
  melihat komentar & memutus koneksi live Anda.
- Tanpa tunel (`run_server.bat`), API key boleh dikosongkan.
- Tunnel butuh `cloudflared.exe` (diunduh otomatis ke folder `server/`). Alternatif:
  `ngrok` atau `playit` — atur di `server/tunnel.json`.

## 1. Menjalankan server (PC)

```
run_server.bat
```

Atau manual:

```
venv312\Scripts\python.exe -m pip install -r requirements_server.txt
venv312\Scripts\python.exe server_app.py
```

Saat start, jendela server menampilkan:

```
  PC ini      : http://127.0.0.1:8000
  Untuk HP    : http://192.168.0.104:8000      <-- isi di aplikasi Android
```

- PC dan HP harus di **Wi-Fi yang sama**
- APK v1.2 **menemukan server sendiri** (UDP port **47653** + sapuan subnet port 8000),
  jadi IP tidak perlu diketik. Firewall perlu mengizinkan **TCP 8000** dan **UDP 47653**
  (bila Windows bertanya, klik **Allow**). Tanpa hak admin, izinkan lewat
  *Windows Security → Firewall & network protection → Allow an app*.
- Buka `http://<IP-PC>:8000` dari browser HP untuk tes cepat (dashboard sederhana)

### Pengaturan server

Tersimpan di `server/settings_server.json` (format sama dengan `settings.json`):

```json
{
  "username": "lucas.vic",
  "opacity": 90,
  "font_size": 14,
  "font_family": "Segoe UI",
  "theme": "dark",
  "speed_unit": "kbps",
  "auto_connect": true,
  "auto_reconnect": true,
  "reconnect_interval": 15
}
```

Isi `username` + `auto_connect: true` → server langsung menyambung saat dijalankan.

---

## 2. Memasang aplikasi Android

APK siap pasang sudah tersedia:

| File | Ukuran | Keterangan |
|---|---|---|
| `apk/TikTokLiveReader-Android-v1.2.apk` | 5,3 MB | **pakai ini** (release) |
| `apk/TikTokLiveReader-Android-v1.2-debug.apk` | 6,5 MB | versi debug untuk log |

1. Salin APK ke HP (kabel/USB/WhatsApp/Drive)
2. Pasang APK (izinkan "Install unknown apps")
3. Buka app → splash → dashboard
4. Buka app — server dicari otomatis (tidak perlu mengisi IP). Bila perlu manual:
   **🌐** → **🔍 Cari Otomatis**, atau isi IP PC lalu **Simpan**
5. Tekan **⚙** → isi username TikTok (tanpa @) → **Hubungkan**
6. Tekan **🧪** untuk **Mode Demo** (data contoh) tanpa perlu live

### Build APK sendiri

```
cd android
gradlew.bat assembleDebug
```

Requirement: JDK 17, Android SDK (platform 34, build-tools 34). Lokasi SDK ada di `android/local.properties`.

APK hasil build ada di `android/app/build/outputs/apk/debug/app-debug.apk`.
Untuk APK release:

```
gradlew.bat assembleRelease
```

atau cukup jalankan `build_android.bat` di folder proyek.

---

## 3. Fitur yang sama dengan versi Py

- 📖 **Feed komentar** realtime (avatar asli TikTok, nama bold)
- 🎁 **Panel gift** (pengirim, jenis gift, jumlah, gambar gift)
- 👋 **Baris "bergabung"** — 2 penonton per baris, digabung otomatis
- 👁 **Penonton aktif**, 👥 **pernah mampir**, 🎁 **pemberi gift** — ketuk ikon untuk melihat
  daftar + lama menonton / total gift
- 🌐 **Kecepatan jaringan** ⬆ ⬇ dengan satuan **Kbps / Mbps / MBps**
- ⚙ **Pengaturan**: username, transparansi, ukuran font, jenis font, satuan kecepatan, tema Terang/Gelap
- 🔄 **Auto-reconnect** (bawaan 15 detik, bisa diubah di `settings_server.json`)
- ❤ Like, ➕ follow, 🔁 share ikut dicatat (tampil di footer)

Perbedaan yang wajar di HP: mode "always on top" ala Windows tidak berlaku di Android
(sebagai gantinya aplikasi tetap berjalan selama server menyala).

---

## 4. Struktur proyek

```
ttpyapk/
├── main.py                     # aplikasi desktop lama (PySide6) — tidak diubah
├── server_app.py               # entry point server
├── run_server.bat              # jalankan server
├── requirements_server.txt     # dependency server
├── server/
│   ├── app.py                  # REST API FastAPI + dashboard HTML
│   ├── live_engine.py          # engine TikTok Live + feed/counter (pengganti TikTokWorker)
│   ├── config.py               # settings_server.json (validasi sama dengan main.py)
│   ├── selftest.py             # tes cepat API (mode demo)
│   └── debug_*.py              # utilitas debug koneksi
└── android/
    ├── app/src/main/java/com/ombaharbangkit/tiktoklivereader/
    │   ├── SplashActivity.kt   # layar loading (mirip SplashWindow)
    │   ├── MainActivity.kt     # dashboard (mirip MainWindow)
    │   ├── data/ServerClient.kt# HTTP ke engine (OkHttp)
    │   ├── data/Prefs.kt       # pengaturan tersimpan di HP
    │   ├── model/Models.kt     # model + parser JSON
    │   └── ui/                 # tema, adapter, dialog, pemuat gambar
    └── app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. REST API

| Endpoint | Keterangan |
|---|---|
| `GET /api/health` | info server + IP LAN |
| `GET /api/status?since=SEQ` | snapshot lengkap + event baru sejak SEQ |
| `GET /api/settings` / `POST /api/settings` | pengaturan (sama dengan settings.json) |
| `GET /api/connect?username=x` | mulai koneksi ke live |
| `GET /api/disconnect` | putus |
| `GET /api/demo?on=1` | mode demo |
| `GET /api/proxy?url=...` | proxy gambar avatar/gift |
| `GET /` | dashboard HTML |
| `GET /docs` | dokumentasi Swagger otomatis |

Client Android memakai **polling** `/api/status?since=SEQ` tiap 1,5 detik
(mirip `Feed.add` di versi Py yang langsung menambah baris saat event masuk).
`since` memastikan event tidak terlewat dan tidak dobel.

---

## 6. Masalah umum

| Masalah | Solusi |
|---|---|
| HP: "Server tidak terjangkau" | IP salah / beda Wi-Fi / firewall blokir port 8000 |
| Status terus "Menghubungkan..." | TikTok membatasi request — tunggu, auto-reconnect akan mencoba |
| `UserOfflineError` | Akun belum live atau username salah |
| Gambar gift tidak muncul | CDN TikTok lambat — server sudah mem-proxy, coba beberapa saat |
| Build APK gagal | pastikan JDK 17 & `android/local.properties` berisi `sdk.dir` |

Library `TikTokLive` bukan resmi dari TikTok, perilakunya bisa berubah sewaktu-waktu.
