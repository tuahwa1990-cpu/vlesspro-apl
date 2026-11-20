package com.vlesspro.pemasang

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import org.json.JSONObject

/** Bahasa paparan apl. Kamus (Melayu -> en/zh/ta) dimuat daripada assets/i18n.json, sama seperti panel Chrome. */
object Bhs {
    var kod by mutableStateOf("ms")
    private var kamus: Map<String, Map<String, String>> = emptyMap()
    val senarai = listOf(
        Triple("ms", "Bahasa Melayu", ""), Triple("en", "English", "Bahasa Inggeris"),
        Triple("zh", "中文（简体）", "Bahasa Cina"), Triple("ta", "தமிழ்", "Bahasa Tamil"))

    fun muat(ctx: Context) {
        try {
            val j = JSONObject(ctx.assets.open("i18n.json").bufferedReader().use { it.readText() })
            kamus = j.keys().asSequence().associateWith { k ->
                val o = j.getJSONObject(k); o.keys().asSequence().associateWith { o.getString(it) }
            }
        } catch (_: Exception) {}
        kod = ctx.getSharedPreferences("vp", Context.MODE_PRIVATE).getString("bhs", "ms") ?: "ms"
    }

    fun tetap(ctx: Context, k: String) {
        kod = k
        ctx.getSharedPreferences("vp", Context.MODE_PRIVATE).edit().putString("bhs", k).apply()
    }

    fun t(s: String): String {
        if (kod == "ms" || s.isEmpty()) return s
        kamus[kod]?.get(s)?.let { return it }
        if (kod == "ta") kamus["en"]?.get(s)?.let { return it }
        return s
    }

    /** Terjemah output berbilang baris (log Pemasang dan skrip modem): baris penuh, atau awalan sebelum ": ", atau corak khas. */
    fun baris(s: String): String = s.lines().joinToString("\n") { l ->
        if (kod == "ms" || l.isBlank()) return@joinToString l
        val d = t(l)
        if (d != l) return@joinToString d
        Regex("^Belum ada lesen aktif \\(status: (.*)\\)\\. Tampal kunci (?:lesen )?di UI: Tetapan > Lesen\\.$").find(l)?.let { return@joinToString f("Belum ada lesen aktif (status: {0}). Tampal kunci lesen di UI: Tetapan > Lesen.", it.groupValues[1]) }
        Regex("^Buka di telefon: (\\S+)\\s+\\(kata laluan awal: admin\\)$").find(l)?.let { return@joinToString f("Buka di telefon: {0}  (kata laluan awal: admin)", it.groupValues[1]) }
        Regex("^Xray Sedia Ada tidak diubah\\. Mod semasa: (.*)$").find(l)?.let { return@joinToString f("Xray Sedia Ada tidak diubah. Mod semasa: {0}", it.groupValues[1]) }
        Regex("^OK: dipasang \\(salinan asal: (.*)\\)$").find(l)?.let { return@joinToString f("OK: dipasang (salinan asal: {0})", it.groupValues[1]) }
        Regex("^Xray Sedia Ada: ada \\((.*)\\)$").find(l)?.let { return@joinToString f("Xray Sedia Ada: ada ({0})", it.groupValues[1]) }
        val i = l.indexOf(": ")
        if (i > 0) { val p = l.substring(0, i + 1); val tp = t(p); if (tp != p) return@joinToString tp + l.substring(i + 1) }
        l
    }

    /** Rentetan dengan tempat letak {0}, {1}. */
    fun f(kunci: String, vararg a: Any): String {
        var r = t(kunci)
        a.forEachIndexed { i, v -> r = r.replace("{$i}", v.toString()) }
        return r
    }
}

class Tema(val id: String, val nama: String, val fon: String, val terang: Boolean, val w: Map<String, Long>)

object Temas {
    val semua = listOf(
    Tema("asal", "Asal", "system-ui", false, mapOf("bg" to 0xFF111113,"bar" to 0xFF1C1C1F,"cd" to 0xFF191B26,"bd" to 0xFF262A3B,"tx" to 0xFFE9EBF2,"mu" to 0xFF7D869C,"ac" to 0xFF4ADE80,"ac2" to 0xFF38BDF8,"on" to 0xFF052E16,"ib" to 0xFF1B3A30,"in" to 0xFF12141D)),
    Tema("lautan", "Lautan", "sans-serif-condensed", false, mapOf("bg" to 0xFF07131F,"bar" to 0xFF0C2236,"cd" to 0xFF0E2A42,"bd" to 0xFF17405F,"tx" to 0xFFE6F3FF,"mu" to 0xFF7FA6C4,"ac" to 0xFF38BDF8,"ac2" to 0xFF22D3EE,"on" to 0xFF04202F,"ib" to 0xFF0F3550,"in" to 0xFF081A2A)),
    Tema("senja", "Senja", "serif", false, mapOf("bg" to 0xFF1A0F0A,"bar" to 0xFF2A1710,"cd" to 0xFF2F1C13,"bd" to 0xFF4D2D1C,"tx" to 0xFFFFE9D6,"mu" to 0xFFC19A80,"ac" to 0xFFFB923C,"ac2" to 0xFFFACC15,"on" to 0xFF2A1002,"ib" to 0xFF4A2812,"in" to 0xFF22130B)),
    Tema("terminal", "Terminal", "monospace", false, mapOf("bg" to 0xFF000000,"bar" to 0xFF050A05,"cd" to 0xFF07110A,"bd" to 0xFF0F3D1A,"tx" to 0xFFB9FFC8,"mu" to 0xFF4F9A62,"ac" to 0xFF22FF55,"ac2" to 0xFF00E5FF,"on" to 0xFF001A06,"ib" to 0xFF0B2A12,"in" to 0xFF030803)),
    Tema("sakura", "Sakura", "casual", true, mapOf("bg" to 0xFFFFF1F5,"bar" to 0xFFFFE0EA,"cd" to 0xFFFFFFFF,"bd" to 0xFFF7C5D5,"tx" to 0xFF4A1D2C,"mu" to 0xFFA8728A,"ac" to 0xFFE11D74,"ac2" to 0xFFA855F7,"on" to 0xFFFFFFFF,"ib" to 0xFFFFD9E6,"in" to 0xFFFFF7FA)),
    Tema("siang", "Siang", "sans-serif-light", true, mapOf("bg" to 0xFFF4F6FA,"bar" to 0xFFFFFFFF,"cd" to 0xFFFFFFFF,"bd" to 0xFFDDE3EE,"tx" to 0xFF1B2433,"mu" to 0xFF6B778C,"ac" to 0xFF2563EB,"ac2" to 0xFF0891B2,"on" to 0xFFFFFFFF,"ib" to 0xFFDBE7FF,"in" to 0xFFF1F4F9)),
    Tema("neon", "Neon Ungu", "sans-serif-black", false, mapOf("bg" to 0xFF0D0518,"bar" to 0xFF190A2E,"cd" to 0xFF1D0C36,"bd" to 0xFF3D1A6E,"tx" to 0xFFF5E8FF,"mu" to 0xFFA78BCF,"ac" to 0xFFE879F9,"ac2" to 0xFF22D3EE,"on" to 0xFF2A0A3A,"ib" to 0xFF341257,"in" to 0xFF12071F)),
    Tema("hutan", "Hutan", "serif-monospace", false, mapOf("bg" to 0xFF0F1710,"bar" to 0xFF18241A,"cd" to 0xFF1B2A1D,"bd" to 0xFF2E4A33,"tx" to 0xFFE7F1DC,"mu" to 0xFF8EA88A,"ac" to 0xFFA3E635,"ac2" to 0xFF34D399,"on" to 0xFF142003,"ib" to 0xFF27402A,"in" to 0xFF0C130D)),
    Tema("emas", "Emas Hitam", "cursive", false, mapOf("bg" to 0xFF000000,"bar" to 0xFF0D0B06,"cd" to 0xFF14110A,"bd" to 0xFF3A2F12,"tx" to 0xFFF6ECD0,"mu" to 0xFFA8975F,"ac" to 0xFFFACC15,"ac2" to 0xFFF59E0B,"on" to 0xFF1C1500,"ib" to 0xFF2D2408,"in" to 0xFF0A0804)),
    Tema("karbon", "Karbon", "sans-serif-smallcaps", false, mapOf("bg" to 0xFF121212,"bar" to 0xFF1E1E1E,"cd" to 0xFF242424,"bd" to 0xFF3A3A3A,"tx" to 0xFFF0F0F0,"mu" to 0xFF9A9A9A,"ac" to 0xFFFF4D4D,"ac2" to 0xFFFFB020,"on" to 0xFFFFFFFF,"ib" to 0xFF3A1F1F,"in" to 0xFF181818)),
    Tema("aurora", "Aurora", "system-ui", false, mapOf("bg" to 0xFF080B1A,"bar" to 0xFF0E1328,"cd" to 0xFF121935,"bd" to 0xFF222C55,"tx" to 0xFFEAF0FF,"mu" to 0xFF8591B8,"ac" to 0xFF22D3EE,"ac2" to 0xFFA78BFA,"on" to 0xFF03212B,"ib" to 0xFF12315A,"in" to 0xFF0B1024))
    )

    fun fonDari(nama: String): FontFamily? = when (nama) {
        "system-ui" -> null
        else -> try { FontFamily(Typeface.create(nama, Typeface.NORMAL)) } catch (_: Exception) { null }
    }

    fun guna(id: String) {
        val t = semua.firstOrNull { it.id == id } ?: semua.last()
        fun c(k: String) = Color(t.w.getValue(k))
        Wr.id = t.id; Wr.terang = t.terang
        Wr.bg = c("bg"); Wr.bar = c("bar"); Wr.kad = c("cd"); Wr.kad2 = c("ib"); Wr.garis = c("bd")
        Wr.tx = c("tx"); Wr.mu = c("mu"); Wr.ac = c("ac"); Wr.ac2 = c("ac2"); Wr.on = c("on"); Wr.inp = c("in")
        Wr.ff = fonDari(t.fon)
    }

    fun muat(ctx: Context) = guna(ctx.getSharedPreferences("vp", Context.MODE_PRIVATE).getString("tema", "aurora") ?: "aurora")

    fun tetap(ctx: Context, id: String) {
        guna(id)
        ctx.getSharedPreferences("vp", Context.MODE_PRIVATE).edit().putString("tema", id).apply()
    }
}

/** Menggantikan material3.Text dalam pakej ini: terjemah rentetan dan guna fon tema. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = Bhs.t(text), modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        fontFamily = fontFamily ?: Wr.ff, letterSpacing = letterSpacing, textAlign = textAlign, maxLines = maxLines, style = style)
}
