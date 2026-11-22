package com.vlesspro.pemasang

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class InfoApl(val code: Long, val name: String, val sha: String, val apk: String,
              val date: String = "", val size: Long = 0, val title: String = "", val items: List<String> = emptyList(), val history: JSONArray = JSONArray())

object Kemas {
    private const val BASE = "https://github.com/tuahwa1990-cpu/vlesspro-apl/releases/latest/download/"

    fun namaSemasa(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    fun versiSemasa(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    /** Senibina CPU telefon ini, jika disokong oleh APK berasingan (arm64-v8a / armeabi-v7a); null jika tidak
     * (cth. x86 emulator) — dalam kes itu APK universal (medan rata "apk"/"sha256") digunakan. */
    private fun abiTelefon(): String? =
        android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" }

    /** Baca versi.json. Jika ada medan "abi" (APK berasingan ikut senibina) dan telefon ini disokong, guna
     * fail yang sepadan dengan senibina telefon (lebih kecil); jika tidak, guna APK universal (medan rata). */
    fun terkini(): InfoApl {
        val j = JSONObject(String(Pemasang.muat(BASE + "versi.json")))
        val a = j.optJSONArray("items") ?: JSONArray()
        var apk = j.getString("apk"); var sha = j.getString("sha256"); var size = j.optLong("size")
        val abi = abiTelefon()
        val mAbi = j.optJSONObject("abi")
        if (abi != null && mAbi != null) mAbi.optJSONObject(abi)?.let {
            apk = it.getString("apk"); sha = it.getString("sha256"); size = it.optLong("size", size)
        }
        return InfoApl(j.getLong("code"), j.getString("name"), sha, apk,
            j.optString("date"), size, j.optString("title"), (0 until a.length()).map { a.getString(it) }, j.optJSONArray("history") ?: JSONArray())
    }

    /** Muat turun APK, semak SHA-256, kemudian buka pemasang Android. Kembali mesej jika perlu kebenaran. */
    fun pasang(ctx: Context, info: InfoApl, log: (String) -> Unit): String? {
        log(Bhs.f("Memuat turun {0} ...", info.name))
        val data = Pemasang.muat(BASE + info.apk)
        if (!Pemasang.sha(data).equals(info.sha, ignoreCase = true)) throw RuntimeException(Bhs.t("Semakan fail APK tidak sepadan. Dibatalkan."))
        val dir = File(ctx.cacheDir, "apk").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "kemas-kini.apk")
        f.writeBytes(data)
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Benarkan \"Pasang apl tidak dikenali\" untuk apl ini, kemudian tekan Kemas kini sekali lagi."
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return null
    }
}
