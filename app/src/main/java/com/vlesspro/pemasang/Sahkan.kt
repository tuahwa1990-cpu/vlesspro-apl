package com.vlesspro.pemasang

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Maklumat untuk pelanggan sahkan bahawa apl yang dipasang ialah keluaran rasmi, bukan tiruan
 * yang ditandatangani semula oleh pihak lain. Dipaparkan di Lagi > Kemas kini apl.
 */
object Sahkan {
    /** Cap jari SHA-256 sijil tandatangan rasmi VLESS Pro Pemasang (huruf besar, tanpa pemisah). */
    const val SIJIL_RASMI = "825968904402BED08A80576F306F84528AFDE45468665C7CAE865705BF503177"

    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }

    /** Cap jari SHA-256 sijil yang menandatangani apl yang sedang berjalan. */
    @Suppress("DEPRECATION")
    fun sijilSemasa(ctx: Context): String? = try {
        val pm = ctx.packageManager
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            val si = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            si?.apkContentsSigners?.firstOrNull() ?: si?.signingCertificateHistory?.lastOrNull()
        } else {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
        if (sig == null) null else hex(MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()))
    } catch (_: Exception) { null }

    /** SHA-256 fail APK yang dipasang (huruf kecil), sama format seperti versi.json. */
    fun apkSha256(ctx: Context): String {
        val md = MessageDigest.getInstance("SHA-256")
        File(ctx.applicationInfo.sourceDir).inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** "82:59:68:...": dua baris supaya mudah dibandingkan di skrin kecil. */
    fun cantik(hexStr: String): String {
        val pasangan = hexStr.chunked(2)
        return if (pasangan.size <= 16) pasangan.joinToString(":") else pasangan.take(16).joinToString(":") + "\n" + pasangan.drop(16).joinToString(":")
    }
}
