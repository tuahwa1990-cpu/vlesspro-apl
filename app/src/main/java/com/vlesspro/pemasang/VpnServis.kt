package com.vlesspro.pemasang

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VpnPerkhidmatan : VpnService() {
    companion object {
        @Volatile var jalan = false
        @Volatile var sibuk = false
        @Volatile var memulih = false
        @Volatile var ralat = ""
        @Volatile var nama = ""
        @Volatile var masa0 = 0L
        const val HENTI = "com.vlesspro.pemasang.VPN_HENTI"
        const val PORT = 10808
        const val PORT_SSH = 10809
        const val VERSI_XRAY = "26.3.27"
        private const val SALURAN = "vpn"

        private val logSemua = StringBuilder()
        fun catat(m: String) {
            synchronized(logSemua) {
                logSemua.append(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())).append(' ').append(m).append('\n')
                if (logSemua.length > 8000) logSemua.delete(0, 3000)
            }
        }
        fun logTerkini(): String = synchronized(logSemua) { logSemua.toString() }

        fun statistik(): LongArray? = if (!jalan) null else try { HevLib.muat(); TProxyService.TProxyGetStats() } catch (_: Throwable) { null }

        fun mula(ctx: Context, pautan: String, auto: Boolean = false, boot: Boolean = false) {
            val i = Intent(ctx, VpnPerkhidmatan::class.java).putExtra("pautan", pautan).putExtra("auto", auto).putExtra("boot", boot)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun henti(ctx: Context) {
            ctx.startService(Intent(ctx, VpnPerkhidmatan::class.java).setAction(HENTI))
        }
    }

    private var xray: Process? = null
    private var ssh: SshTerowong? = null
    private var tun: ParcelFileDescriptor? = null
    private var hasil: VlessPautan.Hasil? = null
    @Volatile private var tamat = false
    private var cb: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == HENTI) { berhenti(); return START_NOT_STICKY }
        val pautan = intent?.getStringExtra("pautan")
        if (pautan == null || sibuk || jalan) {
            if (pautan != null && !jalan) notif(Bhs.t("Menyambung..."))
            return START_NOT_STICKY
        }
        val auto = intent.getBooleanExtra("auto", false)
        val boot = intent.getBooleanExtra("boot", false)
        sibuk = true; ralat = ""; tamat = false; memulih = false
        notif(Bhs.t("Menyambung..."))
        VpnKongsi.kemaskini(this)
        Thread {
            var cuba = 0
            val maks = if (boot) 12 else 1
            while (!tamat) {
                cuba++
                try { sambung(pautan, auto); return@Thread } catch (e: Throwable) {
                    catat("Gagal: " + (e.message ?: e.javaClass.simpleName))
                    if (cuba >= maks || tamat || e is SekatanVpn) { gagal(if (e is SekatanVpn) (e.message ?: "") else huraiRalat(e)); return@Thread }
                    bersihTeras()
                    try { if (tun != null) TProxyService.TProxyStopService() } catch (_: Throwable) {}
                    try { tun?.close() } catch (_: Exception) {}
                    tun = null
                    try { Thread.sleep(5000) } catch (_: Exception) {}
                }
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun notif(teks: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(SALURAN, "VPN", NotificationManager.IMPORTANCE_LOW))
        val buka = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val henti = PendingIntent.getService(this, 1, Intent(this, VpnPerkhidmatan::class.java).setAction(HENTI), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, SALURAN).setSmallIcon(R.drawable.ic_launcher).setContentTitle("VLESS Pro").setContentText(teks)
            .setContentIntent(buka).setOngoing(true)
            .addAction(Notification.Action.Builder(R.drawable.ic_launcher, Bhs.t("Putus"), henti).build()).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) else startForeground(7, n)
        } catch (_: Exception) { try { startForeground(7, n) } catch (_: Exception) {} }
    }

    /** Mulakan SSH (jika perlu) dan Xray. Tun tidak disentuh, jadi boleh dipanggil semula untuk pulih. */
    private fun mulaTeras(h: VlessPautan.Hasil) {
        h.ssh?.let { p ->
            catat("SSH: menyambung")
            val t = SshTerowong(p, PORT_SSH)
            try { t.mula() } catch (e: Throwable) { t.henti(); throw e }
            ssh = t
        }
        val bin = File(applicationInfo.nativeLibraryDir, "libxray.so")
        if (!bin.exists()) throw RuntimeException(Bhs.t("Teras Xray tiada dalam binaan apl ini."))
        val cfg = File(filesDir, "vpn-xray.json").apply { writeText(VlessPautan.konfig(h, PORT)) }
        val pb = ProcessBuilder(bin.path, "run", "-c", cfg.path).redirectErrorStream(true)
        pb.environment()["XRAY_LOCATION_ASSET"] = filesDir.path
        val pr = pb.start()
        xray = pr
        val log = StringBuilder()
        Thread {
            try { pr.inputStream.bufferedReader().forEachLine { synchronized(log) { log.append(it).append('\n'); if (log.length > 4000) log.delete(0, 1000) }; catat("xray: " + it.take(200)) } } catch (_: Exception) {}
            if (xray === pr && jalan && !tamat && !memulih) pulih("Xray berhenti")
        }.start()
        Thread.sleep(1200)
        if (!pr.isAlive) throw RuntimeException(Bhs.f("Xray berhenti: {0}", synchronized(log) { log.toString().trim().takeLast(300) }))
    }

    private fun bersihTeras() {
        val x = xray
        xray = null
        try { x?.destroy() } catch (_: Exception) {}
        try { ssh?.henti() } catch (_: Exception) {}
        ssh = null
    }

    private fun sambung(pautan0: String, auto: Boolean) {
        var pautan = pautan0
        if (auto) {
            val semua = SimpanVpn.semua(this)
            if (semua.size > 1) {
                val i = VpnAlat.terpantas(semua)
                if (i != null) { pautan = semua[i]; catat("Pilih terpantas: pelayan " + (i + 1)) }
            }
        }
        val h = VlessPautan.hurai(pautan)
        VpnKawal.semak(this)?.let { throw SekatanVpn(it) }
        val lesen = VpnLesen.berlesen(this)
        if (!lesen && VpnLesen.berbayar(h)) throw SekatanVpn(VpnLesen.mesej())
        hasil = h
        nama = h.nama
        notif(Bhs.f("Menyambung ke {0}", h.nama))
        catat("Menyambung (" + h.ringkas + ")")
        mulaTeras(h)

        val b = Builder().setSession("VLESS Pro").setMtu(1500).addAddress("26.26.26.1", 30)
            .addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
            .addAddress("fd00:26:26::1", 126).addRoute("::", 0)
        val mod = if (lesen) VpnTetapan.modApl(this) else 0
        val apl = VpnTetapan.apl(this)
        if (mod == 1 && apl.isNotEmpty()) {
            for (a in apl) try { b.addAllowedApplication(a) } catch (_: Exception) {}
        } else {
            if (mod == 2) for (a in apl) try { b.addDisallowedApplication(a) } catch (_: Exception) {}
            try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
        }
        val fd = b.establish() ?: throw RuntimeException(Bhs.t("Kebenaran VPN tidak diberi."))
        tun = fd
        val yml = File(filesDir, "vpn-hev.yml")
        yml.writeText("tunnel:\n  mtu: 1500\n  ipv4: 26.26.26.1\n  ipv6: 'fd00:26:26::1'\nsocks5:\n  port: $PORT\n  address: 127.0.0.1\n  udp: 'udp'\nmisc:\n  log-level: warn\n")
        HevLib.muat()
        if (!TProxyService.TProxyStartService(yml.path, fd.fd)) throw RuntimeException(Bhs.t("Penerowong gagal dimulakan."))
        jalan = true; sibuk = false; masa0 = System.currentTimeMillis()
        catat("Bersambung")
        notif(Bhs.f("Bersambung ke {0}", h.nama))
        tukarPending = true
        cubaMatiModem()
        VpnKongsi.kemaskini(this)
        daftarRangkaian()
        mulaPengawas()
    }

    /** Pulihkan teras (Xray/SSH) sahaja; tun kekal supaya trafik tidak bocor (kill switch) semasa cuba semula. */
    @Synchronized private fun pulih(sebab: String) {
        if (memulih || tamat || !jalan) return
        val h = hasil ?: return
        memulih = true
        catat("Putus: $sebab")
        notif(Bhs.t("Menyambung semula..."))
        VpnKongsi.kemaskini(this)
        Thread {
            if (!VpnTetapan.auto(this)) { ralat = Bhs.t("Sambungan terputus."); berhenti(); return@Thread }
            val kill = VpnTetapan.kill(this)
            var cuba = 0
            while (!tamat) {
                cuba++
                try {
                    bersihTeras()
                    mulaTeras(h)
                    catat("Disambung semula")
                    memulih = false
                    notif(Bhs.f("Bersambung ke {0}", h.nama))
                    VpnKongsi.kemaskini(this)
                    return@Thread
                } catch (e: Throwable) { catat("Cuba semula " + cuba + " gagal: " + (e.message ?: e.javaClass.simpleName)) }
                if (!kill && cuba >= 6) { ralat = Bhs.t("Gagal menyambung semula."); berhenti(); return@Thread }
                var t = 0
                while (!tamat && t < minOf(3000 * cuba, 20000)) { try { Thread.sleep(500) } catch (_: Exception) {}; t += 500 }
            }
        }.start()
    }

    @Volatile private var tukarPending = false
    @Volatile private var tukarJalan = false

    /** Matikan VLESS modem (auto-tukar). Jika modem tak dapat dicapai (bukan WiFi modem), cuba lagi kemudian. */
    private fun cubaMatiModem() {
        if (!tukarPending || tukarJalan || !VpnTetapan.tukar(this)) return
        tukarJalan = true
        Thread {
            try {
                if (Modem.ip.isNotBlank()) {
                    val st = Modem.status()
                    if (st.optString("mode") == "vless" && st.optBoolean("vless")) { Modem.mod(false); catat("VLESS modem dimatikan (auto-tukar)") }
                    tukarPending = false
                }
            } catch (_: Throwable) {} finally { tukarJalan = false }
        }.start()
    }

    private fun mulaPengawas() {
        Thread {
            var gagalBerturut = 0
            var kitar = 0
            while (!tamat) {
                try { Thread.sleep(5000) } catch (_: Exception) {}
                if (tamat || !jalan || memulih) { gagalBerturut = 0; continue }
                val x = xray
                if (x == null || !x.isAlive || (hasil?.ssh != null && ssh?.sihat != true)) { pulih("Teras tidak aktif"); continue }
                kitar++
                cubaMatiModem()
                if (kitar % 4 == 0) {
                    if (VpnAlat.pingSebenar() < 0) gagalBerturut++ else gagalBerturut = 0
                    if (gagalBerturut >= 4) { gagalBerturut = 0; pulih("Pelayan tidak membalas") }
                }
            }
        }.start()
    }

    private fun daftarRangkaian() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val c = object : ConnectivityManager.NetworkCallback() {
                private val ada = LinkedHashSet<Network>()
                private var aktif: Network? = null
                private var hilang = false
                private fun picu(s: String) {
                    if (!jalan || memulih || tamat) return
                    Thread { try { Thread.sleep(1500) } catch (_: Exception) {}; pulih(s) }.start()
                }
                @Synchronized override fun onAvailable(n: Network) {
                    ada.add(n)
                    cubaMatiModem()
                    if (aktif == null) { aktif = n; if (hilang) { hilang = false; picu("Rangkaian kembali") } }
                }
                @Synchronized override fun onLost(n: Network) {
                    ada.remove(n)
                    if (n == aktif) {
                        aktif = ada.firstOrNull()
                        if (aktif == null) hilang = true else picu("Rangkaian bertukar")
                    }
                }
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), c)
            cb = c
        } catch (_: Exception) {}
    }

    private fun huraiRalat(e: Throwable): String {
        val b = StringBuilder(e.javaClass.simpleName + ": " + (e.message ?: ""))
        var c = e.cause; var n = 0
        while (c != null && n < 3) { b.append(" <- ").append(c.javaClass.simpleName).append(": ").append(c.message ?: ""); c = c.cause; n++ }
        e.stackTrace.take(3).forEach { b.append("\n  ").append(it.toString()) }
        return b.toString()
    }

    private fun gagal(m: String) {
        ralat = m
        berhenti()
    }

    private fun berhenti() {
        tamat = true
        try { cb?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } } catch (_: Exception) {}
        cb = null
        try { if (tun != null) TProxyService.TProxyStopService() } catch (_: Throwable) {}
        bersihTeras()
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        if (jalan) catat("Diputuskan")
        jalan = false; sibuk = false; memulih = false
        stopForeground(true)
        stopSelf()
        VpnKongsi.kemaskini(this)
    }

    override fun onRevoke() { berhenti() }
    override fun onDestroy() { if (jalan || tun != null) berhenti(); super.onDestroy() }
}
