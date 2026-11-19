package com.vlesspro.pemasang

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL

/** Bantuan rangkaian telefon: WiFi atau data mudah alih. */
object Rangkaian {
    private fun cm(ctx: Context) = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** "wifi", "data" atau "tiada" untuk rangkaian lalai telefon sekarang. */
    fun jenis(ctx: Context): String {
        val m = cm(ctx)
        val c = m.getNetworkCapabilities(m.activeNetwork) ?: return "tiada"
        return when {
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "data"
            else -> "tiada"
        }
    }

    /** Rangkaian WiFi yang sedang bersambung (walaupun Android tidak menjadikannya lalai). */
    fun wifi(ctx: Context): Network? {
        val m = cm(ctx)
        return m.allNetworks.firstOrNull { m.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    /** Tunggu sehingga WiFi wujud. Kembali benar jika ada. */
    fun tungguWifi(ctx: Context, saat: Int): Boolean {
        var n = 0
        while (n < saat * 2) {
            if (wifi(ctx) != null) return true
            Thread.sleep(500)
            n++
        }
        return wifi(ctx) != null
    }

    /** Ikat semua sambungan apl kepada WiFi supaya tidak lompat ke data mudah alih. */
    fun ikat(ctx: Context) {
        try { cm(ctx).bindProcessToNetwork(wifi(ctx)) } catch (_: Exception) {}
    }

    fun lepas(ctx: Context) {
        try { cm(ctx).bindProcessToNetwork(null) } catch (_: Exception) {}
    }

    /** Ralat yang menunjukkan sambungan ke modem terputus di tengah jalan. */
    fun putus(e: Exception): Boolean {
        val m = (e.message ?: "").lowercase()
        return "session is down" in m || "socket is not established" in m || "connection reset" in m ||
            "broken pipe" in m || "timeout" in m || "unreachable" in m || "connection refused" in m
    }
}

/** Ujian sambungan satu butang: telefon, WiFi, VLESS Pro dan IP keluar. */
object Ujian {
    fun jalan(ctx: Context): List<Pair<Color, String>> {
        val h = ArrayList<Pair<Color, String>>()
        val jenis = Rangkaian.jenis(ctx)
        h += when (jenis) {
            "wifi" -> Wr.ok to Bhs.t("Telefon disambung ke WiFi.")
            "data" -> Wr.bad to Bhs.t("Telefon guna data mudah alih, bukan WiFi modem. Trafik tidak melalui modem.")
            else -> Wr.bad to Bhs.t("Telefon tiada sambungan internet.")
        }
        val st = try { Modem.status() } catch (e: Exception) {
            h += Wr.bad to Bhs.t("Tidak dapat hubungi modem.")
            return h
        }
        val vless = st.optString("mode") == "vless" && st.optBoolean("vless")
        h += if (vless) Wr.ok to Bhs.t("VLESS Pro hidup.")
        else Wr.wn to Bhs.t("VLESS Pro tidak aktif. Trafik guna VPN asal atau SIM asal.")

        // IP melalui WiFi modem (dipaksa) dan IP lalai telefon (yang digunakan apl lain).
        fun ipLuar(n: Network?): Pair<String, Long> {
            return try {
                val t0 = System.currentTimeMillis()
                val u = URL("https://api.ipify.org")
                val c = (if (n != null) n.openConnection(u) else u.openConnection()) as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                val ip = c.inputStream.bufferedReader().use { it.readText() }.trim()
                ip to (System.currentTimeMillis() - t0)
            } catch (_: Exception) { "" to -1L }
        }
        val wf = Rangkaian.wifi(ctx)
        val (ipWifi, msWifi) = if (wf != null) ipLuar(wf) else ("" to -1L)
        val (ipLalai, msLalai) = ipLuar(null)
        val tel = ipWifi.ifEmpty { ipLalai }
        val ms = if (ipWifi.isNotEmpty()) msWifi else msLalai
        if (tel.isEmpty()) {
            h += Wr.bad to Bhs.t("Telefon tidak dapat capai internet.")
            return h
        }
        h += Wr.tx to (if (ipWifi.isNotEmpty()) Bhs.f("IP melalui WiFi modem: {0} ({1} ms)", ipWifi, msWifi) else Bhs.f("IP telefon: {0} ({1} ms)", ipLalai, msLalai))
        if (ipWifi.isNotEmpty() && ipLalai.isNotEmpty() && ipWifi != ipLalai)
            h += Wr.bad to Bhs.f("Apl lain pada telefon guna IP {0}, bukan WiFi modem. Matikan data mudah alih.", ipLalai)

        val info = try { Modem.obj("/api/ip?force=1") } catch (_: Exception) { null }
        val ipM = info?.optString("ip").orEmpty()
        val neg = info?.optString("country").orEmpty()
        if (vless) {
            if (ipM.isEmpty()) h += Wr.wn to Bhs.t("IP keluar VLESS belum dapat disemak.")
            else if (tel == ipM) h += Wr.ok to Bhs.f("WiFi modem melalui VLESS Pro. IP keluar: {0} {1}", ipM, neg).trim()
            else h += Wr.bad to Bhs.f("IP telefon berbeza daripada IP VLESS ({0}). Trafik ini tidak melalui VLESS Pro.", ipM)
        } else {
            h += Wr.tx to Bhs.t("Trafik guna sambungan asal, bukan VLESS Pro.")
        }
        return h
    }
}

@Composable
fun KadUjian() {
    val ctx = LocalContext.current
    var jalan by remember { mutableStateOf(false) }
    var hasil by remember { mutableStateOf<List<Pair<Color, String>>>(emptyList()) }
    KadWr {
        Text("UJI SAMBUNGAN", color = Wr.mu, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Btn(if (jalan) "Sedang menguji ..." else "Uji sambungan", aktif = !jalan && St.status != null) {
            jalan = true; hasil = emptyList()
            Thread {
                try { hasil = Ujian.jalan(ctx) } catch (e: Exception) { hasil = listOf(Wr.bad to (Bhs.t("Ralat") + ": ${e.message ?: e.javaClass.simpleName}")) }
                jalan = false
            }.start()
        }
        hasil.forEach { (w, t) ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(w))
                Spacer(Modifier.width(10.dp))
                Text(t, color = Wr.tx, fontSize = 13.sp)
            }
        }
    }
}
