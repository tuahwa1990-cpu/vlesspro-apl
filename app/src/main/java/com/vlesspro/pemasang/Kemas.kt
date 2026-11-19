package com.vlesspro.pemasang

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File

class InfoApl(val code: Long, val name: String, val sha: String, val apk: String)

object Kemas {
    private const val BASE = "https://github.com/tuahwa1990-cpu/vlesspro-apl/releases/latest/download/"

    fun versiSemasa(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    fun terkini(): InfoApl {
        val j = JSONObject(String(Pemasang.muat(BASE + "versi.json")))
        return InfoApl(j.getLong("code"), j.getString("name"), j.getString("sha256"), j.getString("apk"))
    }

    /** Muat turun APK, semak SHA-256, kemudian buka pemasang Android. Kembali mesej jika perlu kebenaran. */
    fun pasang(ctx: Context, info: InfoApl, log: (String) -> Unit): String? {
        log("Memuat turun ${info.name} ...")
        val data = Pemasang.muat(BASE + info.apk)
        if (!Pemasang.sha(data).equals(info.sha, ignoreCase = true)) throw RuntimeException("Semakan fail APK tidak sepadan. Dibatalkan.")
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
