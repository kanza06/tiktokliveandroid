## TikTok Live Chat v1.0

APK Android yang **langsung tersambung** ke server cloud Fly.io tanpa mengisi IP.

### Unduh
`tiktoklivechatv1.0.apk` (lampiran di bawah)

- Ukuran: 5.35 MB
- Package: `com.ombaharbangkit.tiktoklivereader`
- minSdk 24 (Android 7.0+), targetSdk 34
- SHA-256: `FF887AFBA7133C754151F24132115524DB4505D47E41BA0D8FA24DDF5407109B`

### Yang baru
- Server default `https://tiktokliveandroid.fly.dev` — tidak perlu setting IP.
- Perbaikan `normalize()`: URL https tidak lagi ditambahi `:8000`.
- Hasil scan IP LAN tidak lagi menimpa URL cloud saat server Fly lambat merespons.
- Status bar menampilkan "Menghubungi tiktokliveandroid.fly.dev".
- Endpoint server cloud `/api/status` + `/api/settings` disamakan dengan kontrak APK.

### Deploy Fly
- `fly.toml`: app `tiktokliveandroid`, `internal_port = 8080`, machine selalu hidup (`min_machines_running = 1`).
- Health check `GET /api/ping` → 200, status Checks 1/1.
- Mode web-only (`TTL_WEB_ONLY=1`) agar health check tidak bergantung pada engine TikTokLive.

### Catatan
Versi ini tersambung ke cloud, tetapi komentar/gift live masih perlu server PC lokal
(`run_server.bat`) karena engine TikTokLive belum diaktifkan di Fly.
