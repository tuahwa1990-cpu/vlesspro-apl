package com.vlesspro.pemasang

import android.content.Context

/** Mengumpul maklumat untuk sokongan. Tiada kata laluan, kunci lesen atau UUID nod disertakan. */
object Diagnosis {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val kredensial = Regex("(://)[^@\\s/]+@")

    private fun bersih(s: String) = kredensial.replace(uuid.replace(s, "<uuid>"), "\$1<disorok>@")

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
        sb.append("\nLog terakhir:\n")
        try { sb.append(bersih(Modem.log().lines().takeLast(40).joinToString("\n"))) }
        catch (e: Exception) { sb.append("(log tidak dapat dibaca)") }
        return sb.toString()
    }
}
