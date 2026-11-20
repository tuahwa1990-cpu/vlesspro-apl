package com.vlesspro.pemasang

import android.app.Application
import java.io.File

/** Menyimpan jejak ralat ranap terakhir supaya ia boleh disertakan dalam Hantar diagnosis. */
class VpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = this
        val asal = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val jejak = android.util.Log.getStackTraceString(e).lines().take(25).joinToString("\n")
                val masa = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.US).format(java.util.Date())
                File(filesDir, "ranap.txt").writeText("$masa (apl ${Kemas.namaSemasa(this)}, benang ${t.name})\n$jejak")
            } catch (_: Throwable) {}
            asal?.uncaughtException(t, e)
        }
    }

    companion object {
        lateinit var ctx: Application
        /** Jejak ranap terakhir, atau kosong jika tiada. */
        fun ranapTerakhir(ctx: android.content.Context): String =
            try { File(ctx.filesDir, "ranap.txt").takeIf { it.exists() }?.readText().orEmpty() } catch (_: Exception) { "" }
    }
}
