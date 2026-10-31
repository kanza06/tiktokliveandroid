package com.ombaharbangkit.tiktoklivereader.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Mencari server Python (PC) di jaringan lokal TANPA mengetik IP.
 *
 * Urutan percobaan:
 *   1. alamat yang tersimpan (bila pernah tersambung)
 *   2. broadcast UDP ke port 47653 — server langsung menjawab (paling cepat)
 *   3. IP dari tabel ARP (gateway / PC yang pernah terlihat HP)
 *   4. sapuan paralel seluruh subnet /24 port 8000 (bila broadcast diblokir WiFi)
 *
 * Hasil: "http://<ip>:8000" yang benar-benar menjawab dengan benar.
 */
object ServerDiscovery {

    const val DISCOVERY_PORT = 47653
    private const val PROBE = "TTL_DISCOVER"
    private const val SOURCE = "ttl"
    private const val TOTAL_TIMEOUT_MS = 9000L
    private const val TIMEOUT_CONNECT = 400
    private const val TIMEOUT_READ = 1200
    private const val USER_AGENT = "TikTokLiveReader-Android"

    /** Cari server; null bila tidak ada di jaringan ini. */
    suspend fun find(ctx: Context, saved: String = ""): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            val savedUrl = ServerClient.normalize(saved)
            if (savedUrl.isNotEmpty() && isServer(savedUrl)) return@withTimeoutOrNull savedUrl

            val ip = localIp(ctx) ?: return@withTimeoutOrNull null
            // ARP dulu: biasanya PC sudah tercatat di tabel ARP -> instan
            arpScan()?.let { return@withTimeoutOrNull it }
            coroutineScope {
                val bcast = async { broadcast(2500L) }
                val sweep = async { sweep(ip) }
                bcast.await() ?: sweep.await()
            }
        }
    }

    /** IP HP di Wi-Fi ("192.168.0.104"); null bila WiFi mati. */
    fun localIp(ctx: Context): String? {
        @Suppress("DEPRECATION")
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val ipInt = try {
            wifi?.connectionInfo?.ipAddress ?: 0
        } catch (_: Exception) {
            0
        }
        if (ipInt == 0) return null
        return "${ipInt and 0xff}.${ipInt shr 8 and 0xff}.${ipInt shr 16 and 0xff}.${ipInt shr 24 and 0xff}"
    }

    // ------------------------------------------------------------------ UDP
    private fun broadcast(timeoutMs: Long): String? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                this.broadcast = true
                soTimeout = 300
            }
            val data = PROBE.toByteArray()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                for (t in listOf("255.255.255.255", "224.0.0.1")) {
                    try {
                        socket.send(DatagramPacket(data, data.size,
                            InetAddress.getByName(t), DISCOVERY_PORT))
                    } catch (_: Exception) {
                    }
                }
                val buf = ByteArray(512)
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                    val text = String(pkt.data, 0, pkt.length)
                    if (text.contains("\"$SOURCE\"")) {
                        val url = ServerClient.normalize(pkt.address.hostAddress ?: "")
                        if (isServer(url)) return url
                    }
                } catch (_: Exception) {
                }
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            socket?.close()
        }
    }

    // ------------------------------------------------------------ ARP + scan
    private suspend fun arpScan(): String? = withContext(Dispatchers.IO) {
        val hosts = ArrayList<String>()
        try {
            FileReader("/proc/net/arp").useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    val ip = cols.getOrNull(0) ?: return@forEach
                    // skip entri tidak valid (flag 0x0 = belum tersambung)
                    if (cols.getOrNull(3) == "0x0") return@forEach
                    if (ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) hosts.add(ip)
                }
            }
        } catch (_: Exception) {
        }
        hosts.firstOrNull { isServer(ServerClient.normalize(it)) }?.let {
            return@withContext ServerClient.normalize(it)
        }
        null
    }

    private suspend fun sweep(localIp: String): String? {
        val prefix = localIp.substringBeforeLast('.')
        val found = AtomicReference<String?>(null)
        val done = AtomicBoolean(false)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(40)
        try {
            val jobs = pool.invokeAll((1..254).map { host ->
                java.util.concurrent.Callable {
                    if (!done.get()) {
                        val url = "http://$prefix.$host:8000"
                        if (isServer(url) && found.compareAndSet(null, url)) done.set(true)
                    }
                }
            })
            jobs.forEach { it.get(2, java.util.concurrent.TimeUnit.SECONDS) }
        } catch (_: Exception) {
        } finally {
            pool.shutdownNow()
        }
        return found.get()
    }

    /** true bila endpoint menjawab sebagai server TikTok Live Reader. */
    fun isServer(baseUrl: String): Boolean {
        if (baseUrl.isBlank()) return false
        return try {
            val conn = URL("$baseUrl/api/ping").openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = TIMEOUT_CONNECT
            conn.readTimeout = TIMEOUT_READ
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val o = JSONObject(text)
            o.optBoolean("ok", false) && o.has("version")
        } catch (_: Exception) {
            false
        }
    }
}
