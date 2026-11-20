package com.vlesspro.pemasang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Perjanjian pengguna: mesti disetujui sebelum apl digunakan. Naikkan VERSI jika teks diubah supaya pengguna diminta setuju semula. */
object Janji {
    const val VERSI = 1
    private fun p(ctx: Context) = ctx.getSharedPreferences("vp", Context.MODE_PRIVATE)

    fun diterima(ctx: Context) = p(ctx).getInt("janji_ver", 0) >= VERSI

    fun terima(ctx: Context) {
        p(ctx).edit().putInt("janji_ver", VERSI).putLong("janji_masa", System.currentTimeMillis())
            .putString("janji_apl", Kemas.namaSemasa(ctx) + " (" + Kemas.versiSemasa(ctx) + ")").apply()
    }

    @android.annotation.SuppressLint("HardwareIds")
    private fun idTelefon(ctx: Context): String = try {
        val a = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        MessageDigest.getInstance("SHA-256").digest(("vlesspro:$a").toByteArray()).joinToString("") { "%02x".format(it) }.take(12).uppercase()
    } catch (_: Exception) { "-" }

    /** Resit persetujuan (boleh disalin dan dihantar kepada penjual sebagai bukti). */
    fun resit(ctx: Context): String {
        val ms = p(ctx).getLong("janji_masa", 0L)
        val tarikh = if (ms == 0L) "-" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US).format(Date(ms))
        return "VLESS Pro - Resit persetujuan\nPerjanjian: v" + p(ctx).getInt("janji_ver", 0) + "\nTarikh: " + tarikh +
            "\nApl: " + p(ctx).getString("janji_apl", "-") + "\nTelefon: " + android.os.Build.MODEL + " / Android " + android.os.Build.VERSION.RELEASE +
            "\nID telefon: " + idTelefon(ctx)
    }

    val bahagian = listOf(
        "1. Produk dan lesen" to "VLESS Pro modem ialah produk digital. Kunci lesen dijana khas untuk ID Peranti modem anda dan sah seumur hidup bagi modem itu sahaja. Kunci tidak boleh dipindah, dikongsi atau dijual semula.",
        "2. VPN telefon" to "Fungsi VPN telefon disediakan sebagaimana adanya, tanpa jaminan. Beberapa ciri tambahan memerlukan lesen modem yang sah.",
        "3. Sebelum membayar" to "Anda mengesahkan telah membaca penerangan produk, memahami fungsinya dan keperluan modem anda, dan membuat keputusan membeli sendiri.",
        "4. Bayaran balik" to "Kunci lesen ialah produk digital. Selepas kunci lesen dihantar kepada anda, tiada bayaran balik diberikan, kecuali jika dikehendaki oleh undang-undang yang terpakai. Hak anda di bawah undang-undang perlindungan pengguna tidak terjejas.",
        "5. Penyalahgunaan" to "Anda tidak akan membuat tuntutan bayaran balik, aduan atau laporan yang palsu atau tidak berasas, dan tidak akan menyalahgunakan lesen (berkongsi, menjual semula atau memintas semakan lesen). Penyalahgunaan boleh menyebabkan lesen dibatalkan.",
        "6. Rekod sebagai bukti" to "Anda bersetuju rekod pembayaran, ID Peranti, tarikh lesen dijana dan persetujuan ini boleh digunakan sebagai bukti jika berlaku pertikaian.",
        "7. Tanggungjawab" to "Anda bertanggungjawab ke atas modem, rangkaian dan penggunaan anda serta mematuhi undang-undang. Penjual tidak bertanggungjawab atas kerugian tidak langsung akibat penggunaan, setakat yang dibenarkan undang-undang.",
        "8. Pindaan" to "Perjanjian ini boleh dikemas kini. Anda akan diminta bersetuju semula jika ada perubahan."
    )
}

@Composable
private fun TeksJanji() {
    Text(Bhs.t("Perjanjian Pengguna VLESS Pro"), color = Wr.tx, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    Janji.bahagian.forEach { (tajuk, isi) ->
        Text(Bhs.t(tajuk), color = Wr.ac, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        Text(Bhs.t(isi), color = Wr.tx, fontSize = 13.sp)
    }
}

/** Skrin yang mesti disetujui sebelum apl boleh digunakan. */
@Composable
fun SkrinJanji(diterima: () -> Unit) {
    val ctx = LocalContext.current
    var setuju by remember { mutableStateOf(false) }
    Halaman {
        Spacer(Modifier.height(24.dp))
        TeksJanji()
        Suis(Bhs.t("Saya telah membaca dan bersetuju dengan perjanjian ini."), setuju) { setuju = it }
        Btn(Bhs.t("Setuju dan teruskan"), aktif = setuju) { Janji.terima(ctx); diterima() }
        Btn(Bhs.t("Keluar"), utama = false) { (ctx as? android.app.Activity)?.finish() }
    }
}

/** Lihat perjanjian dan salin resit persetujuan (Lagi > Perjanjian). */
@Composable
fun SubJanji() {
    val ctx = LocalContext.current
    Halaman {
        Judul(Bhs.t("Perjanjian"))
        KadWr {
            Text(Bhs.t("Resit persetujuan"), color = Wr.tx, fontWeight = FontWeight.Bold)
            Text(Janji.resit(ctx), color = Wr.mu, fontSize = 12.sp)
        }
        Btn(Bhs.t("Salin resit")) {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("resit", Janji.resit(ctx)))
            St.toast(Bhs.t("Resit disalin."))
        }
        TeksJanji()
    }
}
