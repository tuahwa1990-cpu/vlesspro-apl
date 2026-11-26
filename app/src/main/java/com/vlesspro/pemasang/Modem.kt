package com.vlesspro.pemasang

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SalahKata : Exception("Kata laluan salah")
class TiadaSambungan(m: String, sebab: Throwable? = null) : Exception(m, sebab)

/** Alamat IP get-away (router/modem) rangkaian WiFi semasa telefon, jika ada. */
fun gatewayIp(ctx: android.content.Context): String? {
    return try {
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        lp.routes.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress
    } catch (_: Exception) { null }
}

data class Nod(val i: Int, val nama: String, val host: String, val port: Int, val net: String, val sec: String, val pilih: Boolean, val id: String = "", val src: String = "")

/** Klien API modem (VLESS Pro, port 8990). Pengesahan: Basic dengan kata laluan panel. */
object Modem {
    var ip = ""
    var pass = ""

    private fun url(p: String) = "http://$ip:8990$p"

    fun req(path: String, body: String? = null, baca: Int = 10000): Pair<Int, String> {
        // Cuba semula sekali jika sambungan terputus (bukan tamat masa baca). POST hanya jika belum sempat disambung.
        try {
            return req1(path, body, baca)
        } catch (e: TiadaSambungan) {
            val c = e.cause
            val boleh = if (body == null) (c is java.net.ConnectException || c is java.net.UnknownHostException || c is java.net.SocketException || c is java.io.EOFException)
                        else (c is java.net.ConnectException || c is java.net.UnknownHostException)
            if (!boleh) throw e
            Thread.sleep(800)
            return req1(path, body, baca)
        }
    }

    private fun req1(path: String, body: String?, baca: Int): Pair<Int, String> {
        try {
            val c = URL(url(path)).openConnection() as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = baca
            c.setRequestProperty("Authorization", "Basic " + Base64.encodeToString("admin:$pass".toByteArray(), Base64.NO_WRAP))
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val kod = c.responseCode
            val s = (if (kod < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (kod == 401) throw SalahKata()
            return kod to s
        } catch (e: SalahKata) {
            throw e
        } catch (e: Exception) {
            throw TiadaSambungan(e.message ?: "Tiada sambungan", e)
        }
    }

    fun muatNaik(data: ByteArray): Pair<Boolean, String> {
        try {
            val c = URL(url("/api/update/upload")).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 360000
            c.setRequestProperty("Authorization", "Basic " + Base64.encodeToString("admin:$pass".toByteArray(), Base64.NO_WRAP))
            c.requestMethod = "POST"
            c.doOutput = true
            c.setFixedLengthStreamingMode(data.size)
            c.outputStream.use { it.write(data) }
            val kod = c.responseCode
            val s = (if (kod < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (kod == 401) throw SalahKata()
            val j = try { JSONObject(s) } catch (_: Exception) { JSONObject() }
            return j.optBoolean("ok") to j.optString("msg").ifEmpty { "HTTP $kod" }
        } catch (e: SalahKata) { throw e } catch (e: Exception) { throw TiadaSambungan(e.message ?: "Tiada sambungan") }
    }

    fun obj(path: String, body: String? = null): JSONObject {
        val (_, s) = req(path, body)
        return try { JSONObject(s) } catch (_: Exception) { JSONObject() }
    }

    fun arr(path: String): JSONArray {
        val (_, s) = req(path)
        return try { JSONArray(s) } catch (_: Exception) { JSONArray() }
    }

    fun status() = obj("/api/status")
    fun ipInfo() = obj("/api/ip")
    fun graphs() = obj("/api/graphs")
    fun lic() = obj("/api/lic")
    fun system() = obj("/api/system")
    fun overview() = obj("/api/overview")
    fun update(semak: Boolean) = obj("/api/update" + if (semak) "?f=1" else "?c=1")

    fun nodes(): List<Nod> {
        val a = arr("/api/nodes")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Nod(o.optInt("i"), o.optString("name"), o.optString("host"), o.optInt("port"), o.optString("net"), o.optString("sec"), o.optBoolean("sel"), o.optString("id"), o.optString("src"))
        }
    }

    fun ping(): List<Int> {
        val a = arr("/api/ping")
        return (0 until a.length()).map { a.optInt(it, -1) }
    }

    fun tindak(path: String, body: String = ""): Pair<Boolean, String> {
        val j = obj(path, body)
        return j.optBoolean("ok") to j.optString("msg")
    }

    fun mod(vless: Boolean) = tindak("/api/mode", if (vless) "vless" else "xlite")
    fun pilihNod(i: Int) = tindak("/api/node/use", i.toString())
    fun padamNod(i: Int) = tindak("/api/node/del", i.toString())
    fun tambahNod(teks: String) = tindak("/api/node/add", teks)
    fun aktifLesen(k: String): JSONObject = obj("/api/lic", k.trim())
    fun log(): String = req("/api/log").second
    fun kosongLog() = tindak("/api/log/clear")
    fun mulaSemula() = tindak("/api/sys/restart")
    fun reboot() = tindak("/api/sys/reboot")
    fun kemasKini(force: Boolean = false) = tindak("/api/update/apply", if (force) "force" else "")
    fun cfgKemas(j: JSONObject) = tindak("/api/update/cfg", j.toString())
    fun pulihVersi() = tindak("/api/update/rollback")

    /**
     * Tunggu modem selesai kemas kini: UI dimulakan semula, kemudian sahkan semula dengan kata laluan tersimpan.
     * Kembali versi baharu, atau null jika gagal. Melempar SalahKata jika kata laluan ditolak.
     */
    fun tungguSiap(lama: String, log: (String) -> Unit): String? {
        val t0 = System.currentTimeMillis()
        var turun = false
        while ((System.currentTimeMillis() - t0) < 300000) {
            Thread.sleep(3000)
            try {
                val u = obj("/api/update?c=1")
                val st = u.optJSONObject("st")
                val s = st?.optString("state").orEmpty()
                if (s == "err") { log(st?.optString("msg").orEmpty()); return null }
                val cur = u.optString("cur")
                if (cur.isNotEmpty() && (cur != lama || turun)) return cur
                log(st?.optString("msg").orEmpty().ifEmpty { "Mengemas kini ..." })
            } catch (e: SalahKata) {
                throw e
            } catch (_: TiadaSambungan) {
                turun = true
                log("Modem memulakan semula UI ...")
            }
        }
        return null
    }

    fun keadaKemasKini() = obj("/api/update/state")

    /** Cuba cari modem pada IP lalai. Kembali IP atau null. */
    fun kesan(calon: List<String>): String? {
        for (h in calon) {
            try {
                val c = URL("http://$h:8990/api/status").openConnection() as HttpURLConnection
                c.connectTimeout = 1500
                c.readTimeout = 1500
                if (c.responseCode > 0) return h
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Sahkan log masuk. Kembali objek status; melempar SalahKata atau TiadaSambungan. */
    fun log(ipBaru: String, passBaru: String): JSONObject {
        ip = ipBaru
        pass = passBaru
        val (_, s) = req("/api/status", null, 6000)
        return try { JSONObject(s) } catch (_: Exception) { JSONObject() }
    }

    fun tukarKata(baru: String): Pair<Boolean, String> {
        val j = obj("/api/pass", baru)
        if (j.optBoolean("ok")) pass = baru
        return j.optBoolean("ok") to j.optString("msg")
    }
}

/** Keadaan yang dikongsi antara skrin. */
object St {
    var status by mutableStateOf<JSONObject?>(null)
    var ipInfo by mutableStateOf<JSONObject?>(null)
    var nod by mutableStateOf<List<Nod>>(emptyList())
    var ping by mutableStateOf<Map<Int, Int>>(emptyMap())
    var sibuk by mutableStateOf(false)
    var luar by mutableStateOf(false)
    var mesej by mutableStateOf("")
    var vpnMod by mutableStateOf("")
    var perluLog by mutableStateOf(false)
    var aplBaru by mutableStateOf("")
    var jamOfset = 0L
    /** Benar bila apl kelihatan di skrin; semakan berkala dijeda bila apl di latar belakang. */
    @Volatile var depan = true
    /** Masa terakhir VLESS modem dihidupkan melalui apl ini (untuk auto-tukar dengan VPN telefon). */
    @Volatile var masaModOn = 0L

    fun toast(m: String) { mesej = m }

    fun on(): Boolean = status?.let { it.optString("mode") == "vless" && it.optBoolean("vless") } ?: false
}
