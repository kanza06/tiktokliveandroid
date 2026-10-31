"""Engine TikTok Live.

Menggantikan TikTokWorker + widget PySide6 pada aplikasi desktop:
  * koneksi TikTok Live (TikTokLive 7.x)
  * feed komentar / gift / "bergabung" (logika bergabung digabung 2-per-baris)
  * counter penonton aktif, pernah mampir, pemberi gift
  * penghapusan penonton yang sudah keluar live (3 menit tanpa aktivitas)

Client Android hanya membaca hasilnya lewat endpoint REST (polling ringan),
jadi tidak perlu WebSocket / dependency khusus di sisi Kotlin.
"""
from __future__ import annotations

import asyncio
import threading
import time
from collections import OrderedDict, deque
from typing import Deque, Dict, List, Optional

from . import config

# ---------------------------------------------------------------------------
# Batas feed (setara Feed(limit=...) di main.py)
# ---------------------------------------------------------------------------
COMMENT_LIMIT = 200
GIFT_LIMIT = 40
JOIN_LIMIT = 12
EVENT_TTL = 120.0          # kejadian lebih tua dari ini tidak perlu dikirim
VIEWER_TTL = 180.0         # penonton dianggap keluar setelah 3 menit tanpa aktivitas
JOIN_BATCH_DELAY = 1.0     # baris "bergabung" digabung (2 nama per baris)


def now_ts() -> float:
    return round(time.time(), 3)


def name_of(user) -> str:
    if user is None:
        return "?"
    return getattr(user, "nickname", None) or getattr(user, "unique_id", None) or "?"


def avatar_url(user) -> str:
    """URL foto profil pengguna dari TikTok (bukan huruf inisial di client).

    Dicoba berurutan: avatar_thumb (kecil), avatar_medium, avatar_large.
    Di TikTokLive 7.x bisa berupa objek dengan .url_list, atau langsung string.
    """
    if user is None:
        return ""
    for attr in ("avatar_thumb", "avatar_medium", "avatar_large", "avatar"):
        try:
            val = getattr(user, attr, None)
            if not val:
                continue
            urls = getattr(val, "url_list", None)
            if isinstance(val, str):
                return val
            if urls:
                for u in urls:
                    if u:
                        return str(u)
        except Exception:
            continue
    # sebagian event (mis. gift) membawa user kecil tanpa avatar -> cari di dict
    try:
        data = getattr(user, "_data", None) or {}
        for key in ("avatarThumb", "avatarMedium", "avatarLarger"):
            inner = getattr(data, key, None)
            if isinstance(inner, dict):
                for u in inner.get("urlList") or inner.get("url_list") or []:
                    if u:
                        return str(u)
    except Exception:
        pass
    return ""


def gift_image_url(gift) -> str:
    """URL gambar gift (icon/preview) dari objek Gift."""
    try:
        for attr in ("icon", "preview_image", "image"):
            img = getattr(gift, attr, None)
            if img and getattr(img, "url_list", None):
                return img.url_list[0]
    except Exception:
        pass
    return ""


def _int(value, default=0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


class LiveState:
    """Seluruh data live + urutan kejadian, aman dibaca dari thread lain."""

    def __init__(self):
        self._lock = threading.RLock()
        self._seq = 0
        self.reset()

    # ------------------------------------------------------------------ dasar
    def reset(self, keep_events: bool = False):
        """Kosongkan semua data live. keep_events=True mempertahankan ring buffer
        kejadian supaya client (since=SEQ) tidak kehilangan urutan."""
        with self._lock:
            kept = list(self.events) if keep_events else []
            self.status_text = "Belum terhubung"
            self.status_ok = False
            self.connected = False
            self.username = ""
            self.mode = "idle"          # idle | connecting | live | error
            self.started_at: Optional[float] = None
            self.room_id: Optional[int] = None
            self.error = ""

            self.visitors: "OrderedDict[str, float]" = OrderedDict()   # pernah mampir
            self.joined: Dict[str, float] = {}                         # penonton aktif
            self.gifters: Dict[str, int] = {}                          # name -> total gift
            self.avatars: Dict[str, str] = {}                          # name -> URL avatar

            self.comments: Deque[dict] = deque(maxlen=COMMENT_LIMIT)
            self.gifts: Deque[dict] = deque(maxlen=GIFT_LIMIT)
            self.joins: Deque[dict] = deque(maxlen=JOIN_LIMIT)
            self.events: Deque[dict] = deque(kept, maxlen=800)

            self.likes = 0
            self.follows = 0
            self.shares = 0
            self.popularity = 0

            self._join_queue: List[str] = []
            self._join_flush_at = 0.0

    def bump_seq(self) -> int:
        with self._lock:
            self._seq += 1
            return self._seq

    def get_seq(self) -> int:
        with self._lock:
            return self._seq

    def _emit(self, kind: str, data: dict) -> dict:
        """Catat kejadian ke ring buffer (dipakai polling ?since=)."""
        ev = {"seq": self.bump_seq(), "t": now_ts(), "type": kind}
        ev.update(data)
        self.events.append(ev)
        return ev

    # --------------------------------------------------------------- status
    def set_status(self, text: str, ok: bool, mode: Optional[str] = None, error: str = ""):
        with self._lock:
            changed = (text != self.status_text or ok != self.status_ok or mode != self.mode)
            self.status_text = text
            self.status_ok = ok
            self.connected = ok
            if mode:
                self.mode = mode
            if error:
                self.error = error
            if ok:
                self.error = ""
                if self.started_at is None:
                    self.started_at = now_ts()
            else:
                self.started_at = None
        if changed:
            self._emit("status", {"text": text, "ok": ok, "mode": self.mode})

    # ------------------------------------------------------------- penonton
    def _touch(self, name: str, avatar: str = ""):
        """Tandai user aktif; daftarkan sebagai penonton bila belum dikenal."""
        t = time.time()
        if avatar and name not in self.avatars:
            self.avatars[name] = avatar
        if name in self.joined:
            self.joined[name] = t
            return False
        self.joined[name] = t
        if name not in self.visitors:
            self.visitors[name] = t
            return True
        return False

    def add_join(self, name: str, avatar: str = ""):
        with self._lock:
            first = self._touch(name, avatar)
            if not first:
                return
            self._join_queue.append(name)
            if not self._join_flush_at:
                self._join_flush_at = time.time() + JOIN_BATCH_DELAY
        if first:
            self._emit("viewer", {"action": "join", "name": name, "avatar": self.avatars.get(name, "")})

    def flush_joins(self):
        """Keluarkan antrean join -> baris feed berisi maksimal 2 nama."""
        with self._lock:
            if not self._join_queue:
                self._join_flush_at = 0.0
                return
            names = self._join_queue[:2]
            del self._join_queue[:2]
            row = {"id": self.bump_seq(), "t": now_ts(), "names": names,
                   "avatars": [self.avatars.get(n, "") for n in names]}
            self.joins.append(row)
            if not self._join_queue:
                self._join_flush_at = 0.0
        self._emit("join", {"names": names, "avatars": row["avatars"], "id": row["id"]})

    # ------------------------------------------------------------- kejadian
    def add_comment(self, user, text: str):
        name = name_of(user)
        avatar = avatar_url(user)
        with self._lock:
            self._touch(name, avatar)
            row = {"id": self.bump_seq(), "t": now_ts(), "user": name, "text": text,
                   "avatar": self.avatars.get(name, "") or avatar}
            self.comments.append(row)
        self._emit("comment", row)

    def add_gift(self, user, gift_name: str, count: int, image: str, diamond: int = 0):
        name = name_of(user)
        avatar = avatar_url(user)
        with self._lock:
            self._touch(name, avatar)
            self.gifters[name] = self.gifters.get(name, 0) + max(count, 1)
            total = self.gifters[name]
            row = {"id": self.bump_seq(), "t": now_ts(), "user": name, "gift": gift_name,
                   "count": max(count, 1), "image": image, "diamond": diamond,
                   "avatar": self.avatars.get(name, ""), "total": total}
            self.gifts.append(row)
        self._emit("gift", row)

    def add_like(self, user, count: int, total: int):
        with self._lock:
            self._touch(name_of(user), avatar_url(user))
            self.likes = max(self.likes, _int(total))
        self._emit("like", {"count": count, "total": self.likes})

    def add_follow(self, user):
        name = name_of(user)
        with self._lock:
            self.follows += 1
            self._touch(name, avatar_url(user))
            total = self.follows
        self._emit("follow", {"user": name, "total": total})

    def add_share(self, user):
        name = name_of(user)
        with self._lock:
            self.shares += 1
            self._touch(name, avatar_url(user))
        self._emit("share", {"user": name})

    def set_popularity(self, total: int):
        with self._lock:
            self.popularity = _int(total)

    # ------------------------------------------------------------ demo mode
    def _demo_join(self, name: str):
        """Seperti pengguna nyata: kirim baris bergabung + event penonton."""
        self.add_join(name)
        self.flush_joins()

    # -------------------------------------------------------------- sweep
    def sweep_viewers(self):
        with self._lock:
            t = time.time()
            gone = [n for n, ts in self.joined.items() if t - ts > VIEWER_TTL]
            for n in gone:
                del self.joined[n]
                self._emit("viewer", {"action": "leave", "name": n})

    # -------------------------------------------------------------- export
    def counts(self) -> dict:
        now = time.time()

        def dur(ts):
            return int(max(now - ts, 0))

        return {
            "active": len(self.joined),
            "active_list": [{"name": n, "seconds": dur(ts)} for n, ts in
                            sorted(self.joined.items(), key=lambda kv: kv[1])],
            "visitors": len(self.visitors),
            "visitors_list": [{"name": n, "seconds": dur(ts)} for n, ts in
                              sorted(self.visitors.items(), key=lambda kv: kv[1])],
            "gifters": len(self.gifters),
            "gifters_list": [{"name": n, "total": v} for n, v in
                             sorted(self.gifters.items(), key=lambda kv: -kv[1])],
        }

    def snapshot(self, since: int = 0) -> dict:
        with self._lock:
            self.sweep_viewers()
            events = [dict(e) for e in self.events if e["seq"] > since]
            head = {
                "username": self.username,
                "status": self.status_text,
                "ok": self.status_ok,
                "connected": self.connected,
                "mode": self.mode,
                "error": self.error,
                "live_seconds": int(time.time() - self.started_at) if self.started_at else 0,
                "room_id": self.room_id,
                "likes": self.likes,
                "follows": self.follows,
                "shares": self.shares,
                "popularity": self.popularity,
            }
            head.update(self.counts())
            head["comments"] = [dict(c) for c in self.comments]
            head["gifts"] = [dict(g) for g in self.gifts]
            head["joins"] = [dict(j) for j in self.joins]
            head["events"] = events
            head["seq"] = self._seq
            head["server_time"] = now_ts()
            return head

    # ------------------------------------------------------------ demo mode
    def demo_tick(self, tick: int = 0):
        """Isi data palsu — dipakai tombol "Mode Demo" di client Android
        untuk mengecek tampilan tanpa benar-benar live."""
        # Mode demo: user pertama memakai foto contoh dari image_cache (URL CDN
        # contoh lama sudah mati). Ini juga untuk menguji avatar foto di APK.
        demo_users = [
            ("Kahfi", "/imgc/a067b8114430430809ab81d45098aede.jpg"),
            ("Rina", ""), ("Bagas", ""), ("Siska", ""), ("Dimas", ""), ("Nadia", ""),
        ]
        texts = [
            "Halo bang, salam dari Medan 🔥", "Gamenya seru banget!", "Kapan live lagi?",
            "Mantap 👍👍", "Request lagu dong", "Sudah follow ya bang",
            "Wkwkwk lucu banget", "Semangat terus!",
        ]
        gifts = [("Mawar", 1, 44), ("Rosa", 5, 44), ("TikTok", 1, 120), ("Donat", 3, 66),
                 ("Singa", 1, 399)]
        with self._lock:
            if self.mode != "demo":
                self.status_text = "Mode Demo (data contoh)"
                self.status_ok = True
                self.connected = True
            self.username = "demo_mode"
            self.mode = "demo"
            self.started_at = self.started_at or now_ts()
            self.likes += 7
            self.popularity += 3
        u = demo_users[tick % len(demo_users)]
        self.add_comment(_DemoUser(*u), texts[tick % len(texts)])
        if tick % 3 == 0:
            g = gifts[(tick // 3) % len(gifts)]
            self.add_gift(_DemoUser(*demo_users[(tick + 2) % len(demo_users)]),
                          g[0], g[1], self._demo_gift_img(g[2]))
        if tick % 2 == 0:
            self._demo_join(demo_users[(tick + 1) % len(demo_users)][0])
        if tick % 5 == 0:
            self.add_follow(_DemoUser(*demo_users[(tick + 3) % len(demo_users)]))

    # CDN ikon gift bawaan TikTok sudah mati (selalu HTTP 400), jadi ikon yang
    # tersedia di image_cache/ dipakai untuk mode demo.
    _DEMO_GIFT_IMG = {
        44: "/imgc/a4e098df8ea12e7aa5c3810fec56974c.png",   # mawar / rosa
        120: "/imgc/582bb8f0078cf8114fd66557cc0210d3.png",  # makanan
        399: "/imgc/3fecd45fda6099efc58675a57ed301ba.jpg",  # banner
    }

    @classmethod
    def _demo_gift_img(cls, diamond: int) -> str:
        return cls._DEMO_GIFT_IMG.get(diamond, "/imgc/a4e098df8ea12e7aa5c3810fec56974c.png")

    def stop_demo(self):
        with self._lock:
            self.reset()


class _DemoUser:
    """Objek tiruan dengan atribut seperti ExtendedUser TikTokLive."""

    class _Thumb:
        def __init__(self, url):
            self.url_list = [url] if url else []

    def __init__(self, name: str, avatar: str = ""):
        self.nickname = name
        self.unique_id = name.lower()
        self.avatar_thumb = self._Thumb(avatar)


class LiveEngine:
    """Mengelola satu koneksi TikTok Live di event loop asyncio terpisah."""

    def __init__(self, state: LiveState):
        self.state = state
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._client = None
        self._task: Optional[asyncio.Task] = None
        self._runner: Optional[asyncio.Task] = None
        self._gen = 0
        self._thread: Optional[threading.Thread] = None
        self._ready = threading.Event()

    # ------------------------------------------------------------ lifecycle
    def start(self):
        """Jalankan event loop engine di thread sendiri.

        Penting: loop TIDAK boleh dibuat di thread milik uvicorn/asyncio,
        karena loop itu dimiliki (dan nanti ditutup) oleh asyncio.run() milik
        framework. Dengan thread sendiri, engine punya loop yang hidup terus.
        """
        if self._thread and self._thread.is_alive():
            return
        self._ready.clear()
        self._thread = threading.Thread(target=self._loop_worker, name="tiktok-engine",
                                        daemon=True)
        self._thread.start()
        self._ready.wait(timeout=10)

    def _loop_worker(self):
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        self._loop.create_task(self._sweeper())
        self._ready.set()
        try:
            self._loop.run_forever()
        finally:
            try:
                pending = [t for t in asyncio.all_tasks(self._loop) if not t.done()]
                for t in pending:
                    t.cancel()
                if pending:
                    self._loop.run_until_complete(
                        asyncio.gather(*pending, return_exceptions=True))
            except Exception:
                pass
            try:
                self._loop.close()
            except Exception:
                pass

    async def _sweeper(self):
        while True:
            try:
                self.state.sweep_viewers()
                self.state.flush_joins() if self._join_due() else None
            except Exception:
                pass
            await asyncio.sleep(1.0)

    def _join_due(self) -> bool:
        at = getattr(self.state, "_join_flush_at", None)
        return bool(at and time.time() >= at)

    # -------------------------------------------------------------- kontrol
    def connect(self, username: str = "") -> bool:
        """Sambungkan ke TikTok Live. Return False bila sudah berjalan."""
        username = (username or "").strip().lstrip("@")
        if not username:
            self.state.set_status("Isi username TikTok dulu", False, "error", "username kosong")
            return False
        if self._runner and not self._runner.done():
            return False
        if self._loop is None or not self._loop.is_running():
            self.state.set_status("Engine belum siap", False, "error", "engine belum siap")
            return False
        # Jangan reset seq/events: client Android memegang posisi "since" —
        # kalau seq turun, client tidak akan pernah menerima event baru.
        self.state.reset(keep_events=True)
        self.state.username = username
        self._gen += 1
        gen = self._gen
        self.state.set_status(f"Menghubungkan ke @{username}...", False, "connecting")
        # Jadwalkan task di loop engine (tanpa menunggu selesai)
        holder: list = []
        done = threading.Event()

        def schedule():
            holder.append(self._loop.create_task(self._run(username, gen)))
            done.set()

        self._loop.call_soon_threadsafe(schedule)
        done.wait(timeout=5)
        self._runner = holder[0] if holder else None
        return True

    def disconnect(self, reason: str = "Terputus"):
        self._gen += 1
        self.state.set_status(reason, False, "idle")
        if self._loop is None or not self._loop.is_running():
            return
        if self._client is not None and self._task is not None and not self._task.done():
            asyncio.run_coroutine_threadsafe(self._safe_disconnect(), self._loop)
        if self._runner and not self._runner.done():
            self._loop.call_soon_threadsafe(self._runner.cancel)
        self._client = None
        self._task = None

    async def _safe_disconnect(self):
        client = self._client
        if client is None:
            return
        try:
            await asyncio.wait_for(client.disconnect(), timeout=5)
        except Exception:
            pass

    # ---------------------------------------------------------------- konek
    async def _run(self, username: str, gen: int):
        from TikTokLive import TikTokLiveClient
        from TikTokLive.client.errors import UserOfflineError

        # Percobaan pertama + 2 kali ulang (mirip max_tries=3 di main.py);
        # setelah itu auto_reconnect menunggu reconnect_interval lalu mulai lagi.
        tries = 3
        for attempt in range(1, tries + 1):
            if gen != self._gen:
                return
            try:
                client = TikTokLiveClient(unique_id="@" + username)
                self._client = client
                self._wire(client, gen)
                connect_task = asyncio.ensure_future(client.start())
                self._task = connect_task
                # start() bisa menggantung lama saat TikTok membatasi request
                await asyncio.wait({connect_task}, timeout=60)
                if not connect_task.done():
                    connect_task.cancel()
                    raise TimeoutError("TikTok tidak merespons (timeout 60 detik)")
                self._task = connect_task.result()
                self.state.set_status(f"Terhubung ke @{username}", True, "live")
                await self._task          # hidup sampai terputus / dibatalkan
                if gen != self._gen:
                    return
                self.state.set_status("Live berakhir / terputus", False, "idle")
            except asyncio.CancelledError:
                raise
            except UserOfflineError:
                self.state.set_status("Live sudah selesai / user offline", False, "idle",
                                      "UserOfflineError")
                return
            except Exception as e:
                if gen != self._gen:
                    return
                name = type(e).__name__
                msg = str(e).split("->", 1)[-1].strip()[:160]
                self.state.set_status(f"Gagal: {name} - {msg}", False, "error", msg)
            finally:
                self._client = None
                self._task = None

            cfg = config.load_settings()
            if not cfg.get("auto_reconnect", True):
                return
            wait = int(cfg.get("reconnect_interval", 15))
            for _ in range(wait):
                if gen != self._gen:
                    return
                await asyncio.sleep(1)
            if gen != self._gen:
                return
            self.state.set_status(f"Menyambung ulang @{username} ({attempt})...", False, "connecting")

    def _wire(self, client, gen: int):
        from TikTokLive.events import (
            CommentEvent, ConnectEvent, DisconnectEvent, FollowEvent, GiftEvent, JoinEvent,
            LikeEvent, LiveEndEvent, RoomUserSeqEvent, ShareEvent,
        )
        st = self.state

        def guard():
            return gen == self._gen

        @client.on(ConnectEvent)
        async def on_connect(event):
            if guard():
                try:
                    st.room_id = int(getattr(client, "room_id", 0) or 0)
                except Exception:
                    pass
                st.set_status(f"Terhubung ke @{st.username}", True, "live")

        @client.on(DisconnectEvent)
        async def on_disconnect(event):
            if guard():
                st.set_status("Terputus", False, "idle")

        @client.on(JoinEvent)
        async def on_join(event):
            if guard():
                st.add_join(name_of(event.user), avatar_url(event.user))

        @client.on(CommentEvent)
        async def on_comment(event):
            if guard():
                st.add_comment(event.user, event.comment)

        @client.on(GiftEvent)
        async def on_gift(event):
            if not guard():
                return
            # Gift beruntun: kirim hanya saat rangkaian selesai (sama seperti desktop)
            try:
                if getattr(event.gift, "streakable", False) and getattr(event, "streaking", False):
                    return
            except Exception:
                pass
            gift_name = getattr(event.gift, "name", "Gift") or "Gift"
            count = _int(getattr(event, "repeat_count", 1), 1) or 1
            diamond = _int(getattr(event.gift, "diamond_count", 0))
            st.add_gift(event.user, gift_name, count, gift_image_url(event.gift), diamond)

        @client.on(LikeEvent)
        async def on_like(event):
            if guard():
                st.add_like(event.user, _int(getattr(event, "count", 0)),
                            _int(getattr(event, "total", 0)))

        @client.on(FollowEvent)
        async def on_follow(event):
            if guard():
                st.add_follow(event.user)

        @client.on(ShareEvent)
        async def on_share(event):
            if guard():
                st.add_share(event.user)

        @client.on(RoomUserSeqEvent)
        async def on_user_seq(event):
            if guard():
                st.set_popularity(getattr(event, "popularity", 0) or getattr(event, "total", 0))

        @client.on(LiveEndEvent)
        async def on_live_end(event):
            if guard():
                st.set_status("Live berakhir", False, "idle")
