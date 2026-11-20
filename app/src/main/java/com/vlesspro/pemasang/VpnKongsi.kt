package com.vlesspro.pemasang

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews

/** Logik kongsi untuk apl, jubin Tetapan Pantas dan widget skrin utama. */
object VpnKongsi {
    fun kemaskini(ctx: Context) {
        try { TileService.requestListeningState(ctx, ComponentName(ctx, VpnTile::class.java)) } catch (_: Throwable) {}
        try { VpnWidget.segar(ctx) } catch (_: Throwable) {}
    }

    /** Sambung pelayan terpilih mengikut tetapan. Pulangkan false jika tiada pelayan. */
    fun mula(ctx: Context, boot: Boolean = false): Boolean {
        val s = SimpanVpn.semua(ctx)
        val p = s.getOrNull(SimpanVpn.pilih(ctx)) ?: return false
        VpnPerkhidmatan.ralat = ""
        VpnPerkhidmatan.mula(ctx, p, VpnTetapan.terpantas(ctx), boot)
        return true
    }

    fun bukaApl(ctx: Context) {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Untuk widget: putus jika hidup, sambung jika kebenaran sudah ada, jika tidak buka apl. */
    fun togol(ctx: Context) {
        if (VpnPerkhidmatan.jalan || VpnPerkhidmatan.sibuk) { VpnPerkhidmatan.henti(ctx); return }
        if (SimpanVpn.semua(ctx).isEmpty() || VpnService.prepare(ctx) != null) { bukaApl(ctx); return }
        try { mula(ctx) } catch (_: Throwable) { bukaApl(ctx) }
    }
}

class VpnTile : TileService() {
    override fun onStartListening() { segar() }
    private fun segar() {
        val t = qsTile ?: return
        t.state = if (VpnPerkhidmatan.jalan && !VpnPerkhidmatan.memulih) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "VLESS Pro VPN"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = when {
            VpnPerkhidmatan.memulih -> Bhs.t("Menyambung semula...")
            VpnPerkhidmatan.jalan -> Bhs.t("Bersambung")
            VpnPerkhidmatan.sibuk -> Bhs.t("Menyambung...")
            else -> Bhs.t("Putus")
        }
        t.updateTile()
    }
    @Suppress("DEPRECATION")
    override fun onClick() {
        if (VpnPerkhidmatan.jalan || VpnPerkhidmatan.sibuk) { VpnPerkhidmatan.henti(this); return }
        if (SimpanVpn.semua(this).isEmpty() || VpnService.prepare(this) != null) {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
            else startActivityAndCollapse(i)
            return
        }
        try { VpnKongsi.mula(this) } catch (_: Throwable) {}
    }
}

class VpnWidget : AppWidgetProvider() {
    companion object {
        const val TOGOL = "com.vlesspro.pemasang.WIDGET_TOGOL"
        fun segar(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, VpnWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget_vpn)
            val hidup = VpnPerkhidmatan.jalan && !VpnPerkhidmatan.memulih
            v.setTextViewText(R.id.wstatus, when {
                VpnPerkhidmatan.memulih -> Bhs.t("Menyambung semula...")
                VpnPerkhidmatan.jalan -> Bhs.t("Bersambung")
                VpnPerkhidmatan.sibuk -> Bhs.t("Menyambung...")
                else -> Bhs.t("Sentuh untuk sambung")
            })
            v.setTextColor(R.id.wstatus, if (hidup) 0xFF4ADE80.toInt() else 0xFFB0B6C4.toInt())
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, VpnWidget::class.java).setAction(TOGOL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.wakar, pi)
            m.updateAppWidget(ids, v)
        }
    }
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) { segar(ctx) }
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == TOGOL) { VpnKongsi.togol(ctx); segar(ctx) } else super.onReceive(ctx, i)
    }
}

/** Sambung automatik selepas telefon dihidupkan (jika dihidupkan dalam Tetapan). */
class VpnBoot : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED || !VpnTetapan.boot(ctx)) return
        if (VpnService.prepare(ctx) != null) return
        try { VpnKongsi.mula(ctx, true) } catch (_: Throwable) {}
    }
}

/** Imbas QR menegak. */
class ImbasQr : com.journeyapps.barcodescanner.CaptureActivity()
