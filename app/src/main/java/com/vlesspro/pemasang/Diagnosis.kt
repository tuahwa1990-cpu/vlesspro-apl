package com.vlesspro.pemasang

import android.content.Context

/** Mengumpul maklumat untuk sokongan. Tiada kata laluan, kunci lesen, UUID nod atau laman yang dilayari. */
object Diagnosis {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val kredensial = Regex("(://)[^@\\s/]+@")

    private val domain = Regex("\\b(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}\\b")
    private val ipv4 = Regex("\\b(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b")

    /** Buang UUID, kelayakan, nama domain dan IP penuh supaya laman yang dilayari tidak terdedah. */
    private fun bersih(s: String) = ipv4.replace(domain.replace(kredensial.replace(uuid.replace(s, "<uuid>"), "\$1<disorok>@"), "<domain>"), "\$1.\$2.x.x")

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
            val masalah = semua.filter { l -> val k = l.lowercase(); "[warning]" in k || "[error]" in k || "failed" in k }
            b("Ringkasan", semua.count { "accepted" in it }.toString() + " sambungan diterima, " + masalah.size + " amaran/ralat dalam " + semua.size + " baris terakhir")
            if (masalah.isEmpty()) sb.append("(tiada amaran atau ralat)\n")
            else sb.append(bersih(masalah.takeLast(20).joinToString("\n"))).append('\n')
        } catch (e: Exception) { sb.append("(log tidak dapat dibaca)\n") }
        val ranap = VpApp.ranapTerakhir(ctx)
        sb.append("\nRanap apl terakhir:\n")
        sb.append(if (ranap.isEmpty()) "(tiada)\n" else bersih(ranap) + "\n")
        return sb.toString()
    }
}
