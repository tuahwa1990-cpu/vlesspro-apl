package com.vlesspro.pemasang

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** Senarai pelayan Fungsi 2 disimpan dalam telefon sahaja: pautan manual + pautan daripada langganan. */
object SimpanVpn {
    private fun p(ctx: Context) = ctx.getSharedPreferences("vp", Context.MODE_PRIVATE)
    private fun tatasusunan(ctx: Context, k: String): List<String> {
        val a = try { JSONArray(p(ctx).getString(k, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getString(it) }
    }

    fun manual(ctx: Context) = tatasusunan(ctx, "vpn_pelayan")
    fun simpanManual(ctx: Context, s: List<String>) { p(ctx).edit().putString("vpn_pelayan", JSONArray(s).toString()).apply() }

    /** Langganan: senarai (url, pautan-pautan). */
    fun langganan(ctx: Context): List<Pair<String, List<String>>> {
        val a = try { JSONArray(p(ctx).getString("vpn_langganan", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val l = o.optJSONArray("l") ?: JSONArray()
            o.optString("u") to (0 until l.length()).map { l.getString(it) }
        }
    }
    fun simpanLangganan(ctx: Context, s: List<Pair<String, List<String>>>) {
        val a = JSONArray()
        s.forEach { (u, l) -> a.put(JSONObject().put("u", u).put("l", JSONArray(l))) }
        p(ctx).edit().putString("vpn_langganan", a.toString()).apply()
    }

    fun semua(ctx: Context): List<String> = (manual(ctx) + langganan(ctx).flatMap { it.second }).distinct()

    fun pilih(ctx: Context): Int {
        val s = semua(ctx)
        p(ctx).getString("vpn_pilih_p", null)?.let { l -> val i = s.indexOf(l); if (i >= 0) return i }
        return p(ctx).getInt("vpn_pilih", 0).coerceIn(0, maxOf(0, s.size - 1))
    }
    fun setPilih(ctx: Context, i: Int) {
        p(ctx).edit().putInt("vpn_pilih", i).putString("vpn_pilih_p", semua(ctx).getOrNull(i)).apply()
    }
}

/** Tetapan Fungsi 2 (semua dalam telefon). */
object VpnTetapan {
    private fun p(ctx: Context) = ctx.getSharedPreferences("vp", Context.MODE_PRIVATE)
    fun auto(ctx: Context) = p(ctx).getBoolean("vpn_auto", true)
    fun kill(ctx: Context) = p(ctx).getBoolean("vpn_kill", false)
    /** Auto-tukar: VPN telefon dan VLESS modem tidak hidup serentak. */
    fun tukar(ctx: Context) = p(ctx).getBoolean("vpn_tukar", true)
    fun boot(ctx: Context) = p(ctx).getBoolean("vpn_boot", false)
    fun terpantas(ctx: Context) = p(ctx).getBoolean("vpn_terpantas", false)
    fun set(ctx: Context, k: String, v: Boolean) { p(ctx).edit().putBoolean(k, v).apply() }
    /** 0 = semua apl, 1 = hanya apl dipilih, 2 = semua kecuali apl dipilih */
    fun modApl(ctx: Context) = p(ctx).getInt("vpn_mod", 0)
    fun setModApl(ctx: Context, m: Int) { p(ctx).edit().putInt("vpn_mod", m).apply() }
    fun apl(ctx: Context): Set<String> = p(ctx).getStringSet("vpn_apl", emptySet()) ?: emptySet()
    fun setApl(ctx: Context, s: Set<String>) { p(ctx).edit().putStringSet("vpn_apl", HashSet(s)).apply() }
}

class SekatanVpn(m: String) : RuntimeException(m)

/** Pelanggan berlesen = apl pernah melihat lesen modem berstatus aktif (disimpan dalam telefon). */
object VpnLesen {
    const val HAD_PERCUMA = 3
    private fun p(ctx: Context) = ctx.getSharedPreferences("vp", Context.MODE_PRIVATE)
    fun berlesen(ctx: Context) = p(ctx).getBoolean("vpn_berlesen", false)
    fun tetap(ctx: Context, v: Boolean) { if (berlesen(ctx) != v) p(ctx).edit().putBoolean("vpn_berlesen", v).apply() }
    fun berbayar(h: VlessPautan.Hasil) = h.jenis == "SSH" || h.jenis == "TROJAN" || h.jenis == "SS"
    fun mesej() = Bhs.t("Ciri ini untuk pelanggan VLESS Pro berlesen. Log masuk modem anda dalam apl ini sekali untuk membukanya.")
}

/** Suis jauh: fail kawal.json di GitHub. Jika gagal dimuat turun, VPN dibenarkan (fail-open). */
object VpnKawal {
    private const val URL_KAWAL = "https://raw.githubusercontent.com/tuahwa1990-cpu/vlesspro-apl/main/kawal.json"
    private fun p(ctx: Context) = ctx.getSharedPreferences("vp", Context.MODE_PRIVATE)

    /** Pulangkan mesej sekatan, atau null jika dibenarkan. Panggil dari benang latar. */
    fun semak(ctx: Context): String? {
        var teks = p(ctx).getString("kawal_json", "") ?: ""
        val masa = p(ctx).getLong("kawal_masa", 0L)
        if (System.currentTimeMillis() - masa > 3_600_000L) {
            try {
                val c = URL(URL_KAWAL).openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                val t = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                JSONObject(t)
                teks = t
                p(ctx).edit().putString("kawal_json", t).putLong("kawal_masa", System.currentTimeMillis()).apply()
            } catch (_: Exception) {}
        }
        return try {
            val j = JSONObject(teks)
            when {
                !j.optBoolean("vpn_telefon", true) -> j.optString("mesej").ifEmpty { Bhs.t("VPN telefon dihentikan buat sementara.") }
                j.optLong("versi_min", 0L) > Kemas.versiSemasa(ctx) -> Bhs.t("Sila kemas kini apl untuk terus guna VPN telefon.")
                else -> null
            }
        } catch (_: Exception) { null }
    }
}

object VpnAlat {
    fun pingTcp(host: String, port: Int, had: Int = 3000): Long = try {
        val t0 = System.nanoTime(); Socket().use { it.connect(InetSocketAddress(host, port), had) }; (System.nanoTime() - t0) / 1_000_000
    } catch (_: Exception) { -1L }

    /** Ping TCP serentak ke semua pelayan (kunci = indeks). */
    fun pingSemua(h: List<VlessPautan.Hasil?>): Map<Int, Long> {
        val m = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        val t = h.mapIndexedNotNull { i, x -> x?.let { Thread { m[i] = pingTcp(it.host, it.port) }.also { th -> th.start() } } }
        t.forEach { try { it.join(4000) } catch (_: Exception) {} }
        return m
    }

    /** Indeks pelayan paling cepat (ping TCP terendah), atau null jika semua gagal. */
    fun terpantas(pautan: List<String>): Int? {
        val h = pautan.map { try { VlessPautan.hurai(it) } catch (_: Exception) { null } }
        return pingSemua(h).filter { it.value >= 0 }.minByOrNull { it.value }?.key
    }

    /** Ping sebenar melalui terowong (Xray SOCKS): masa meminta generate_204. -1 jika gagal. */
    fun pingSebenar(): Long {
        var terbaik = -1L
        repeat(2) {
            try {
                val t0 = System.nanoTime()
                val c = URL("https://www.gstatic.com/generate_204").openConnection(java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", VpnPerkhidmatan.PORT))) as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000
                c.responseCode; c.disconnect()
                val ms = (System.nanoTime() - t0) / 1_000_000
                if (terbaik < 0 || ms < terbaik) terbaik = ms
            } catch (_: Exception) {}
        }
        return terbaik
    }

    /** Baca kod QR daripada gambar galeri. */
    fun imbasGambar(ctx: Context, uri: Uri): String? = try {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
        var skala = 1
        while (maxOf(o.outWidth, o.outHeight) / skala > 1800) skala *= 2
        val bm = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = skala }) }
        if (bm == null) null else {
            val px = IntArray(bm.width * bm.height)
            bm.getPixels(px, 0, bm.width, 0, 0, bm.width, bm.height)
            val src = com.google.zxing.RGBLuminanceSource(bm.width, bm.height, px)
            val rd = com.google.zxing.MultiFormatReader().apply {
                setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE), com.google.zxing.DecodeHintType.TRY_HARDER to true))
            }
            try { rd.decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(src))).text }
            catch (_: Exception) { rd.decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(src.invert()))).text }
        }
    } catch (_: Throwable) { null }
}

/** Import pautan (tampal / QR / langganan). Fungsi blocking: panggil dari benang IO. */
object VpnImport {
    private fun adaSkema(l: String) = VlessPautan.skema.any { l.startsWith(it) }

    private fun nyahkod(badan: String): String {
        val t = badan.trim()
        if (t.contains("://")) return t
        return try {
            var b = t.replace("\n", "").replace("\r", "").replace('-', '+').replace('_', '/')
            while (b.length % 4 != 0) b += "="
            String(android.util.Base64.decode(b, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) { t }
    }

    fun ambilLangganan(url: String): List<String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "v2rayNG/1.8.5")
        val badan = c.inputStream.use { it.readBytes().take(2_000_000).toByteArray().toString(Charsets.UTF_8) }
        val hasil = nyahkod(badan).lines().map { it.trim() }.filter { adaSkema(it) }
            .filter { try { VlessPautan.hurai(it); true } catch (_: Exception) { false } }.distinct()
        if (hasil.isEmpty()) throw IllegalArgumentException(Bhs.t("Tiada pelayan sah dalam langganan ini."))
        return hasil
    }

    fun segarkan(ctx: Context): String {
        if (!VpnLesen.berlesen(ctx)) return VpnLesen.mesej()
        val lama = SimpanVpn.langganan(ctx)
        if (lama.isEmpty()) return Bhs.t("Tiada langganan.")
        var ok = 0; var gagal = 0
        val baru = lama.map { (u, l) -> try { ok++; u to ambilLangganan(u) } catch (_: Exception) { ok--; gagal++; u to l } }
        SimpanVpn.simpanLangganan(ctx, baru)
        return Bhs.f("Langganan dikemas kini: {0} berjaya, {1} gagal.", ok.toString(), gagal.toString())
    }

    fun proses(ctx: Context, teks: String): String {
        val baris = teks.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val pautan = baris.filter { adaSkema(it) }
        val url = baris.filter { it.startsWith("http://") || it.startsWith("https://") }
        if (pautan.isEmpty() && url.isEmpty()) return Bhs.t("Tiada pautan pelayan atau langganan ditemui.")
        var tambah = 0; var ralat = ""
        val m = SimpanVpn.manual(ctx).toMutableList()
        val lesen = VpnLesen.berlesen(ctx)
        for (l in pautan) {
            try {
                val h = VlessPautan.hurai(l)
                if (l in m) continue
                if (!lesen && VpnLesen.berbayar(h)) { ralat = VpnLesen.mesej(); continue }
                if (!lesen && m.size >= VpnLesen.HAD_PERCUMA) { ralat = Bhs.f("Had {0} pelayan untuk pengguna percuma. Padam satu dahulu.", VpnLesen.HAD_PERCUMA.toString()); continue }
                m.add(l); tambah++
            } catch (e: Exception) { ralat = e.message ?: "" }
        }
        SimpanVpn.simpanManual(ctx, m)
        var sub = 0
        val ls = SimpanVpn.langganan(ctx).toMutableList()
        for (u in url) {
            if (!lesen) { ralat = VpnLesen.mesej(); break }
            try {
                val l = ambilLangganan(u)
                val i = ls.indexOfFirst { it.first == u }
                if (i >= 0) ls[i] = u to l else ls.add(u to l)
                sub += l.size
            } catch (e: Exception) { ralat = e.message ?: e.javaClass.simpleName }
        }
        SimpanVpn.simpanLangganan(ctx, ls)
        val jum = tambah + sub
        return if (jum > 0) Bhs.f("{0} pelayan ditambah.", jum.toString()) else ralat.ifEmpty { Bhs.t("Tiada pelayan baharu.") }
    }
}

/** Laporan ringkas untuk sokongan: tiada pautan, kata laluan, UUID atau domain. */
object VpnLaporan {
    fun bina(ctx: Context): String {
        val sb = StringBuilder("VLESS Pro - VPN telefon\n")
        fun b(k: String, v: String) { sb.append(k).append(": ").append(v).append('\n') }
        b("Apl", Kemas.namaSemasa(ctx) + " (" + Kemas.versiSemasa(ctx) + ")")
        b("Xray", VpnPerkhidmatan.VERSI_XRAY)
        b("Telefon", android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE)
        b("Status", if (VpnPerkhidmatan.jalan) "bersambung" else "putus")
        b("Pelayan", SimpanVpn.semua(ctx).size.toString() + " (" + SimpanVpn.semua(ctx).map { it.substringBefore("://") }.groupingBy { it }.eachCount().entries.joinToString { it.key + "=" + it.value } + ")")
        b("Tetapan", "auto=" + VpnTetapan.auto(ctx) + " kill=" + VpnTetapan.kill(ctx) + " boot=" + VpnTetapan.boot(ctx) + " terpantas=" + VpnTetapan.terpantas(ctx) + " modApl=" + VpnTetapan.modApl(ctx) + " apl=" + VpnTetapan.apl(ctx).size)
        b("Ralat terakhir", Diagnosis.bersih(VpnPerkhidmatan.ralat.ifEmpty { "-" }))
        sb.append("\nLog:\n").append(Diagnosis.bersih(VpnPerkhidmatan.logTerkini()))
        return sb.toString()
    }
}
