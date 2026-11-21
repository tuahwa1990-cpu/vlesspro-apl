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
    class Hasil(val nama: String, val outbound: JSONObject, val jenis: String, val rangkaian: String, val sekuriti: String, val host: String, val port: Int, val ssh: SshProfil? = null) {
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

    private fun hostPort(hp: String): Pair<String, Int> =
        if (hp.startsWith("[")) hp.substring(1, hp.indexOf(']')) to hp.substringAfter("]:").toInt()
        else hp.substringBeforeLast(':') to hp.substringAfterLast(':').toInt()

    private fun b64(s: String): String {
        var t = s.trim().replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(android.util.Base64.decode(t, android.util.Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun trojan(p: String): Hasil {
        var r = p.removePrefix("trojan://")
        val nama = dec(r.substringAfter('#', ""))
        r = r.substringBefore('#')
        val query = r.substringAfter('?', "")
        r = r.substringBefore('?').trimEnd('/')
        val kata = dec(r.substringBeforeLast('@'))
        val (host, port) = hostPort(r.substringAfterLast('@'))
        if (kata.isEmpty() || host.isEmpty()) throw tak()
        val q = query.split('&').filter { it.contains('=') }.associate { dec(it.substringBefore('=')) to dec(it.substringAfter('=')) }.toMutableMap()
        if (!q.containsKey("security")) q["security"] = "tls"
        val ss = aliran(q, host)
        val out = JSONObject().put("tag", "proxy").put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().put("address", host).put("port", port).put("password", kata))))
            .put("streamSettings", ss)
        return Hasil(nama.ifEmpty { "$host:$port" }, out, "TROJAN", ss.optString("network"), ss.optString("security"), host, port)
    }

    private fun shadowsocks(p: String): Hasil {
        var r = p.removePrefix("ss://")
        val nama = dec(r.substringAfter('#', ""))
        r = r.substringBefore('#')
        if (r.substringAfter('?', "").contains("plugin=")) throw IllegalArgumentException(Bhs.t("Pemalam (plugin) Shadowsocks belum disokong."))
        r = r.substringBefore('?').trimEnd('/')
        val cred: String; val hp: String
        if ('@' in r) {
            val ui = r.substringBeforeLast('@')
            cred = dec(ui).takeIf { ':' in it } ?: b64(ui)
            hp = r.substringAfterLast('@')
        } else {
            val d = b64(r)
            cred = d.substringBeforeLast('@'); hp = d.substringAfterLast('@')
        }
        val method = cred.substringBefore(':'); val kata = cred.substringAfter(':')
        val (host, port) = hostPort(hp)
        if (method.isEmpty() || host.isEmpty()) throw tak()
        val out = JSONObject().put("tag", "proxy").put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().put("address", host).put("port", port).put("method", method).put("password", kata))))
        return Hasil(nama.ifEmpty { "$host:$port" }, out, "SS", "tcp", "none", host, port)
    }

    private fun ssh(p: String): Hasil {
        val s = SshPautan.hurai(p)
        val out = JSONObject().put("tag", "proxy").put("protocol", "socks")
            .put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().put("address", "127.0.0.1").put("port", VpnPerkhidmatan.PORT_SSH))))
        return Hasil(s.nama, out, "SSH", if (s.payload.isNotBlank()) "payload" else "tcp", if (s.sni.isNotBlank()) "tls" else "none", s.host, s.port, s)
    }

    val skema = listOf("vless://", "vmess://", "trojan://", "ss://", "ssh://")

    fun hurai(pautan: String): Hasil {
        val p = pautan.trim()
        try {
            return when {
                p.startsWith("vless://") -> vless(p)
                p.startsWith("vmess://") -> vmess(p)
                p.startsWith("trojan://") -> trojan(p)
                p.startsWith("ss://") -> shadowsocks(p)
                p.startsWith("ssh://") -> ssh(p)
                else -> throw IllegalArgumentException(Bhs.t("Pautan tidak dikenali. Disokong: vless, vmess, trojan, ss, ssh."))
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw tak()
        }
    }

    fun konfig(h: Hasil, portSocks: Int, ctx: Context? = null): String {
        val ssh = h.ssh != null
        val dnsTersuai = ctx?.let { VpnTetapan.dns(it) }.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val dns = JSONObject().put("servers", JSONArray().apply {
            if (dnsTersuai.isNotEmpty()) dnsTersuai.forEach { put(if (ssh) "tcp://$it" else it) }
            else if (ssh) { put("tcp://1.1.1.1"); put("tcp://8.8.8.8") } else { put("1.1.1.1"); put("8.8.8.8") }
        })
        val keluar = JSONArray().put(h.outbound).put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        val j = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", dns)
            .put("inbounds", JSONArray().put(JSONObject().put("tag", "socks").put("listen", "127.0.0.1").put("port", portSocks).put("protocol", "socks")
                .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
                // hev-socks5-tunnel hantar sambungan ikut IP sahaja (DNS diselesaikan oleh Android sebelum
                // sampai ke tun2socks), jadi Xray TIDAK nampak nama domain secara semula jadi. "sniffing"
                // membaca SNI (TLS) / Host (HTTP) daripada trafik itu sendiri supaya peraturan "domain"
                // (termasuk geosite:category-ads-all) benar-benar dapat dipadankan.
                .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray(listOf("http", "tls"))).put("routeOnly", false))))
        val peraturan = JSONArray()
        if (ssh) {
            // SSH hanya bawa TCP: pertanyaan DNS (UDP 53) ditukar kepada DNS-over-TCP melalui terowong.
            keluar.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
            peraturan.put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("socks")).put("network", "udp").put("port", "53").put("outboundTag", "dns-out"))
        }
        if (ctx != null && VpnTetapan.routingIklan(ctx)) {
            keluar.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            // geosite:category-ads-all — senarai penuh (200,000+ domain) dibina ke dalam geosite.dat
            // yang disalin daripada aset (lihat VpnServis.pastikanGeosite), sama seperti v2rayNG.
            peraturan.put(JSONObject().put("type", "field").put("domain", JSONArray(listOf("geosite:category-ads-all"))).put("outboundTag", "block"))
        }
        if (ctx != null && VpnTetapan.routingLan(ctx)) {
            peraturan.put(JSONObject().put("type", "field").put("ip", JSONArray(SENARAI_LAN_VPN)).put("outboundTag", "direct"))
        }
        if (peraturan.length() > 0) j.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", peraturan))
        return j.put("outbounds", keluar).toString()
    }
}

