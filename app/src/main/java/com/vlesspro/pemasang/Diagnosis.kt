package com.vlesspro.pemasang

import android.content.Context

/** Mengumpul maklumat untuk sokongan. Tiada kata laluan, kunci lesen, UUID nod atau laman yang dilayari. */
object Diagnosis {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val kredensial = Regex("(://)[^@\\s/]+@")

    private val domain = Regex("\\b(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}\\b")
    private val ipv4 = Regex("\\b(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b")

    /** Buang UUID, kelayakan, nama domain dan IP penuh supaya laman yang dilayari tidak terdedah. */
    fun bersih(s: String) = ipv4.replace(domain.replace(kredensial.replace(uuid.replace(s, "<uuid>"), "\$1<disorok>@"), "<domain>"), "\$1.\$2.x.x")

    fun kumpul(ctx: Context): String {
        val sb = StringBuilder("VLESS Pro - Diagnosis\n")
        fun b(k: String, v: String) { sb.append(k).append(": ").append(v).append('\n') }
        b("Apl", Kemas.namaSemasa(ctx) + " (" + Kemas.versiSemasa(ctx) + ")")
        b("Telefon", android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE)
        b("Bahasa", java.util.Locale.getDefault().toString())
        b("Rangkaian telefon", Rangkaian.jenis(ctx))
        b("Modem", Modem.ip)
        try {
            val s = Modem.status()
            b("Versi modem", s.optString("version"))
            b("Mod", s.optString("mode") + (if (s.optBoolean("vless")) " (VLESS hidup)" else " (VLESS tidak hidup)"))
            b("Lesen", s.optJSONObject("lic")?.optString("state").orEmpty())
        } catch (e: Exception) { b("Status modem", "gagal: " + (e.message ?: e.javaClass.simpleName)) }
        try {
            val y = Modem.system()
            b("Edisi", y.optString("edisi") + " / " + y.optString("arch"))
            b("Xray", if (y.optBoolean("xray")) "berjalan" else "berhenti")
            b("Hidup sejak (saat)", y.optString("uptime"))
            b("Memori bebas (KB)", y.optString("memfree") + " / " + y.optString("memtot"))
        } catch (e: Exception) { b("Sistem modem", "gagal: " + (e.message ?: e.javaClass.simpleName)) }
        sb.append("\nUji sambungan:\n")
        try { Ujian.jalan(ctx).forEach { sb.append("- ").append(it.second).append('\n') } }
        catch (e: Exception) { sb.append("- gagal: ").append(e.message ?: e.javaClass.simpleName).append('\n') }
        val fmt = { z: java.util.TimeZone ->
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply { timeZone = z }.format(java.util.Date()) + " " + z.getDisplayName(false, java.util.TimeZone.SHORT)
        }
        b("Masa telefon", fmt(java.util.TimeZone.getDefault()))
        b("Masa UTC (log modem guna UTC)", fmt(java.util.TimeZone.getTimeZone("UTC")))
        sb.append("\nLog modem (amaran dan ralat sahaja; domain dan IP disorok):\n")
        try {
            val semua = Modem.log().lines().takeLast(300)
            val semuaMasalah = semua.filter { l -> val k = l.lowercase(); "[warning]" in k || "[error]" in k || "failed" in k }
            // Amaran "closed pipe" daripada semakan IP ialah perkara biasa (permintaan lambat ditinggalkan); tidak dipaparkan.
            val jinak = semuaMasalah.filter { "closed pipe" in it }
            val masalah = semuaMasalah - jinak.toSet()
            b("Ringkasan", semua.count { "accepted" in it }.toString() + " sambungan diterima, " + masalah.size + " amaran/ralat sebenar, " + jinak.size + " amaran jinak disembunyikan, dalam " + semua.size + " baris terakhir")
            if (masalah.isEmpty()) sb.append("(tiada amaran atau ralat)\n")
            else sb.append(bersih(masalah.takeLast(20).joinToString("\n"))).append('\n')
        } catch (e: Exception) { sb.append("(log tidak dapat dibaca)\n") }
        val ranap = VpApp.ranapTerakhir(ctx)
        sb.append("\nRanap apl terakhir:\n")
        sb.append(if (ranap.isEmpty()) "(tiada)\n" else bersih(ranap) + "\n")
        return sb.toString()
    }

    /** Diagnosis am + fokus Failover (cfg, kelayakan setiap nod, rekod penuh), guna data yang sudah dimuat di skrin. */
    fun kumpulFailover(ctx: Context, d: org.json.JSONObject?, nod: List<Nod>, ping: List<Int>): String {
        val sb = StringBuilder(kumpul(ctx))
        sb.append("\n--- FOKUS: FAILOVER ---\n")
        fun b(k: String, v: String) { sb.append(k).append(": ").append(v).append('\n') }
        if (d == null) { sb.append("(data failover tidak dapat dimuat)\n"); return sb.toString() }
        b("Failover hidup", d.optJSONObject("cfg")?.optBoolean("on").toString())
        b("Profil kumpulan sedang digunakan", d.optBoolean("grp").toString())
        b("Pemantauan aktif sekarang (auto)", d.optBoolean("auto").toString())
        b("Ujian gagal berturut", d.optInt("fails").toString() + "/3")
        b("Nod semasa", d.optString("cur").ifEmpty { "-" })
        b("Jumlah nod", d.optInt("n").toString())
        sb.append("Status setiap nod:\n")
        nod.forEachIndexed { i, nd ->
            val p = ping.getOrElse(i) { -1 }
            val status = if (nd.pilih) "digunakan sekarang" else when { p == -2 -> "tidak diuji"; p < 0 -> "tidak boleh dicapai"; else -> "$p ms" }
            sb.append("- ").append(nd.nama).append(": ").append(status).append('\n')
        }
        val log = d.optJSONArray("log")
        sb.append("Rekod tukar nod (terbaharu dahulu):\n")
        if (log == null || log.length() == 0) sb.append("(tiada)\n")
        else for (i in 0 until log.length()) {
            val o = log.getJSONObject(i)
            sb.append("- ").append(ProfilAlat.waktu(o.optLong("t"))).append("  ").append(o.optString("from")).append(" -> ").append(o.optString("to"))
                .append(" (").append(o.optString("why")).append(")\n")
        }
        return sb.toString()
    }
}
