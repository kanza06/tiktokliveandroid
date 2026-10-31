"""Debug koneksi TikTokLive langsung (tanpa HTTP)."""
import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

USER = sys.argv[1] if len(sys.argv) > 1 else "lucas.vic"


async def main():
    from TikTokLive import TikTokLiveClient
    from TikTokLive.events import CommentEvent, ConnectEvent, DisconnectEvent, GiftEvent

    client = TikTokLiveClient(unique_id="@" + USER)

    @client.on(ConnectEvent)
    async def _c(e):
        print("[CONNECT]", time.strftime("%H:%M:%S"), flush=True)

    @client.on(DisconnectEvent)
    async def _d(e):
        print("[DISCONNECT]", time.strftime("%H:%M:%S"), flush=True)

    @client.on(CommentEvent)
    async def _m(e):
        print("[COMMENT]", e.user.nickname, ":", e.comment, flush=True)

    @client.on(GiftEvent)
    async def _g(e):
        print("[GIFT]", e.user.nickname, e.gift.name, e.repeat_count, flush=True)

    t0 = time.time()
    try:
        task = await asyncio.wait_for(client.start(), timeout=60)
        print(f"[START OK] {time.time()-t0:.1f}s", flush=True)
        await asyncio.wait_for(task, timeout=45)
    except asyncio.TimeoutError:
        print(f"[TIMEOUT] {time.time()-t0:.1f}s", flush=True)
    except Exception as e:
        print(f"[ERROR] {type(e).__name__}: {e}", flush=True)


asyncio.run(main())
