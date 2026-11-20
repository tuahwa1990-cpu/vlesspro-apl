package com.vlesspro.pemasang

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

// ───────────────────── Fungsi 2: VPN terus dari telefon ─────────────────────

/** JNI hev-socks5-tunnel (tun2socks). Nama kelas ini dipadankan semasa binaan CI (tools/sedia-vpn.sh). */
object HevLib {
    @Volatile private var dimuat = false
    /** Muat pustaka asli DI LUAR initialiser kelas TProxyService supaya FindClass dalam JNI_OnLoad menemui kelas yang sudah siap. */
    @Synchronized fun muat() {
        if (dimuat) return
        Class.forName("com.vlesspro.pemasang.TProxyService")
        System.loadLibrary("hev-socks5-tunnel")
        dimuat = true
    }
}

/** JNI hev-socks5-tunnel (tun2socks). Nama kelas ini dipadankan semasa binaan CI (tools/sedia-vpn.sh). */
class TProxyService {
    companion object {
        // Tandatangan mesti sama dengan jadual native_methods dalam hev-jni.c: (String,int)Z, ()Z, ()Z, ()[J
        @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean
        @JvmStatic external fun TProxyStopService(): Boolean
        @JvmStatic external fun TProxyIsRunning(): Boolean
        @JvmStatic external fun TProxyGetStats(): LongArray
    }
}

/** Pautan vless:// dan vmess:// kepada konfigurasi Xray. */
object VlessPautan {
    class Hasil(val nama: String, val outbound: JSONObject, val jenis: String, val rangkaian: String, val sekuriti: String, val host: String, val port: Int) {
        val ringkas get() = listOf(jenis, rangkaian.uppercase(), sekuriti.uppercase().takeIf { it != "NONE" }).filterNotNull().joinToString(" · ")
    }

    private fun dec(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }
    private fun tak() = IllegalArgumentException(Bhs.t("Pautan tidak sah."))

    /** Tetapan aliran (tcp/ws/grpc/tls/reality) daripada parameter pautan. */
    private fun aliran(q: Map<String, String>, host: String): JSONObject {
        val net = (q["type"] ?: "tcp").let { if (it == "splithttp") "xhttp" else it }
        val sec = q["security"] ?: "none"
        val ss = JSONObject().put("network", net).put("security", sec)
        val sni = q["sni"]?.takeIf { it.isNotEmpty() } ?: q["host"]?.takeIf { it.isNotEmpty() } ?: host
        when (sec) {
            "tls" -> {
                val t = JSONObject().put("serverName", sni).put("fingerprint", q["fp"]?.takeIf { it.isNotEmpty() } ?: "chrome")
                q["alpn"]?.takeIf { it.isNotEmpty() }?.let { t.put("alpn", JSONArray(it.split(','))) }
                ss.put("tlsSettings", t)
            }
            "reality" -> ss.put("realitySettings", JSONObject()
                .put("serverName", q["sni"] ?: "").put("fingerprint", q["fp"] ?: "chrome")
                .put("publicKey", q["pbk"] ?: "").put("shortId", q["sid"] ?: "").put("spiderX", q["spx"] ?: ""))
        }
        val hostHdr = q["host"]?.takeIf { it.isNotEmpty() } ?: host
        when (net) {
            "ws" -> ss.put("wsSettings", JSONObject().put("path", q["path"] ?: "/").put("headers", JSONObject().put("Host", hostHdr)))
            "grpc" -> ss.put("grpcSettings", JSONObject().put("serviceName", q["serviceName"] ?: "").put("multiMode", q["mode"] == "multi"))
            "httpupgrade" -> ss.put("httpupgradeSettings", JSONObject().put("path", q["path"] ?: "/").put("host", hostHdr))
            "xhttp" -> ss.put("xhttpSettings", JSONObject().put("path", q["path"] ?: "/").put("host", hostHdr).put("mode", q["mode"] ?: "auto"))
        }
        return ss
    }

    private fun vless(p: String): Hasil {
        var r = p.removePrefix("vless://")
        val nama = dec(r.substringAfter('#', ""))
        r = r.substringBefore('#')
        val query = r.substringAfter('?', "")
        r = r.substringBefore('?').trimEnd('/')
        val uuid = dec(r.substringBeforeLast('@'))
        val hp = r.substringAfterLast('@')
        val host: String
        val port: Int
        if (hp.startsWith("[")) { host = hp.substring(1, hp.indexOf(']')); port = hp.substringAfter("]:").toInt() }
        else { host = hp.substringBeforeLast(':'); port = hp.substringAfterLast(':').toInt() }
        if (uuid.isEmpty() || host.isEmpty()) throw tak()
        val q = query.split('&').filter { it.contains('=') }.associate { dec(it.substringBefore('=')) to dec(it.substringAfter('=')) }
        val user = JSONObject().put("id", uuid).put("encryption", q["encryption"] ?: "none")
        q["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        val ss = aliran(q, host)
        val out = JSONObject().put("tag", "proxy").put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user)))))
            .put("streamSettings", ss)
        return Hasil(nama.ifEmpty { "$host:$port" }, out, "VLESS", ss.optString("network"), ss.optString("security"), host, port)
    }

    private fun vmess(p: String): Hasil {
        val b64 = p.removePrefix("vmess://").trim().replace('-', '+').replace('_', '/')
        val j = JSONObject(String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8))
        val host = j.optString("add"); val port = j.optString("port").toIntOrNull() ?: throw tak()
        val id = j.optString("id")
        if (host.isEmpty() || id.isEmpty()) throw tak()
        val net = j.optString("net").ifEmpty { "tcp" }
        val q = mutableMapOf(
            "type" to net, "security" to (if (j.optString("tls") == "tls") "tls" else "none"),
            "sni" to j.optString("sni"), "host" to j.optString("host"), "path" to j.optString("path"),
            "alpn" to j.optString("alpn"), "fp" to j.optString("fp"), "serviceName" to j.optString("path")
        )
        val ss = aliran(q, host)
        val user = JSONObject().put("id", id).put("alterId", j.optString("aid").toIntOrNull() ?: 0).put("security", j.optString("scy").ifEmpty { "auto" })
        val out = JSONObject().put("tag", "proxy").put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user)))))
            .put("streamSettings", ss)
        return Hasil(j.optString("ps").ifEmpty { "$host:$port" }, out, "VMESS", ss.optString("network"), ss.optString("security"), host, port)
    }

    fun hurai(pautan: String): Hasil {
        val p = pautan.trim()
        try {
            return when {
                p.startsWith("vless://") -> vless(p)
                p.startsWith("vmess://") -> vmess(p)
                else -> throw IllegalArgumentException(Bhs.t("Hanya pautan vless:// dan vmess:// disokong buat masa ini."))
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw tak()
        }
    }

    fun konfig(h: Hasil, portSocks: Int): String = JSONObject()
        .put("log", JSONObject().put("loglevel", "warning"))
        .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
        .put("inbounds", JSONArray().put(JSONObject().put("tag", "socks").put("listen", "127.0.0.1").put("port", portSocks).put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))))
        .put("outbounds", JSONArray().put(h.outbound).put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
        .toString()
}

/** Senarai pelayan Fungsi 2 disimpan dalam telefon sahaja. */
object SimpanVpn {
    private fun p(ctx: Context) = ctx.getSharedPreferences("vp", Context.MODE_PRIVATE)
    fun senarai(ctx: Context): List<String> {
        val a = try { JSONArray(p(ctx).getString("vpn_pelayan", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getString(it) }
    }
    fun simpan(ctx: Context, s: List<String>) { p(ctx).edit().putString("vpn_pelayan", JSONArray(s).toString()).apply() }
    fun pilih(ctx: Context) = p(ctx).getInt("vpn_pilih", 0)
    fun setPilih(ctx: Context, i: Int) { p(ctx).edit().putInt("vpn_pilih", i).apply() }
}

class VpnPerkhidmatan : VpnService() {
    companion object {
        @Volatile var jalan = false
        @Volatile var sibuk = false
        @Volatile var ralat = ""
        @Volatile var nama = ""
        @Volatile var masa0 = 0L
        fun statistik(): LongArray? = if (!jalan) null else try { HevLib.muat(); TProxyService.TProxyGetStats() } catch (_: Throwable) { null }
        const val HENTI = "com.vlesspro.pemasang.VPN_HENTI"
        const val PORT = 10808
        private const val SALURAN = "vpn"

        fun mula(ctx: Context, pautan: String) {
            ctx.startService(Intent(ctx, VpnPerkhidmatan::class.java).putExtra("pautan", pautan))
        }
        fun henti(ctx: Context) {
            ctx.startService(Intent(ctx, VpnPerkhidmatan::class.java).setAction(HENTI))
        }
    }

    private var xray: Process? = null
    private var tun: ParcelFileDescriptor? = null
    private val log = StringBuilder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == HENTI) { berhenti(); return START_NOT_STICKY }
        val pautan = intent?.getStringExtra("pautan") ?: return START_NOT_STICKY
        if (sibuk || jalan) return START_NOT_STICKY
        sibuk = true; ralat = ""
        Thread { try { sambung(pautan) } catch (e: Throwable) { gagal(huraiRalat(e)) } }.start()
        return START_NOT_STICKY
    }

    private fun notif(teks: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(SALURAN, "VPN", NotificationManager.IMPORTANCE_LOW))
        val buka = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val henti = PendingIntent.getService(this, 1, Intent(this, VpnPerkhidmatan::class.java).setAction(HENTI), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, SALURAN).setSmallIcon(R.drawable.ic_launcher).setContentTitle("VLESS Pro").setContentText(teks)
            .setContentIntent(buka).setOngoing(true)
            .addAction(Notification.Action.Builder(R.drawable.ic_launcher, Bhs.t("Putus"), henti).build()).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) else startForeground(7, n)
        } catch (_: Exception) { try { startForeground(7, n) } catch (_: Exception) {} }
    }

    private fun sambung(pautan: String) {
        val h = VlessPautan.hurai(pautan)
        nama = h.nama
        notif(Bhs.f("Menyambung ke {0}", h.nama))
        val bin = File(applicationInfo.nativeLibraryDir, "libxray.so")
        if (!bin.exists()) throw RuntimeException(Bhs.t("Teras Xray tiada dalam binaan apl ini."))
        val cfg = File(filesDir, "vpn-xray.json").apply { writeText(VlessPautan.konfig(h, PORT)) }
        val pb = ProcessBuilder(bin.path, "run", "-c", cfg.path).redirectErrorStream(true)
        pb.environment()["XRAY_LOCATION_ASSET"] = filesDir.path
        val pr = pb.start()
        xray = pr
        log.setLength(0)
        Thread {
            try { pr.inputStream.bufferedReader().forEachLine { synchronized(log) { log.append(it).append('\n'); if (log.length > 4000) log.delete(0, 1000) } } } catch (_: Exception) {}
        }.start()
        Thread.sleep(1200)
        if (!pr.isAlive) throw RuntimeException(Bhs.f("Xray berhenti: {0}", synchronized(log) { log.toString().trim().takeLast(300) }))

        val b = Builder().setSession("VLESS Pro").setMtu(1500).addAddress("26.26.26.1", 30)
            .addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
            .addAddress("fd00:26:26::1", 126).addRoute("::", 0)
        try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
        val fd = b.establish() ?: throw RuntimeException(Bhs.t("Kebenaran VPN tidak diberi."))
        tun = fd
        val yml = File(filesDir, "vpn-hev.yml")
        yml.writeText("tunnel:\n  mtu: 1500\n  ipv4: 26.26.26.1\n  ipv6: 'fd00:26:26::1'\nsocks5:\n  port: $PORT\n  address: 127.0.0.1\n  udp: 'udp'\nmisc:\n  log-level: warn\n")
        HevLib.muat()
        if (!TProxyService.TProxyStartService(yml.path, fd.fd)) throw RuntimeException(Bhs.t("Penerowong gagal dimulakan."))
        jalan = true; sibuk = false; masa0 = System.currentTimeMillis()
        notif(Bhs.f("Bersambung ke {0}", h.nama))
    }

    private fun huraiRalat(e: Throwable): String {
        val b = StringBuilder(e.javaClass.simpleName + ": " + (e.message ?: ""))
        var c = e.cause; var n = 0
        while (c != null && n < 3) { b.append(" <- ").append(c.javaClass.simpleName).append(": ").append(c.message ?: ""); c = c.cause; n++ }
        e.stackTrace.take(3).forEach { b.append("\n  ").append(it.toString()) }
        return b.toString()
    }

    private fun gagal(m: String) {
        ralat = m
        berhenti()
    }

    private fun berhenti() {
        try { if (tun != null) TProxyService.TProxyStopService() } catch (_: Throwable) {}
        try { xray?.destroy() } catch (_: Exception) {}
        xray = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        jalan = false; sibuk = false
        stopForeground(true)
        stopSelf()
    }

    override fun onRevoke() { berhenti() }
    override fun onDestroy() { if (jalan || tun != null) berhenti(); super.onDestroy() }
}

