package com.vlesspro.pemasang

import android.content.ClipboardManager
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

private fun saizB(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format("%.1f KB", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format("%.1f MB", b / 1048576.0)
    else -> String.format("%.2f GB", b / 1073741824.0)
}

private fun masaStr(ms: Long): String {
    val s = ms / 1000
    return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
}

/** Butang kuasa bulat besar: kelabu = putus, kuning berdenyut = menyambung, biru = bersambung. */
@Composable
fun ButangKuasa(keadaan: Int, klik: () -> Unit) {
    val warna = when (keadaan) { 2 -> Wr.ac; 1 -> Wr.wn; else -> Wr.mu }
    val t = rememberInfiniteTransition(label = "kuasa")
    val denyut by t.animateFloat(1f, if (keadaan == 1) 1.14f else 1.04f,
        infiniteRepeatable(tween(if (keadaan == 1) 700 else 1800), RepeatMode.Reverse), label = "denyut")
    Box(Modifier.size(190.dp).clip(CircleShape).clickable { klik() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2
            val ikon = r * 0.26f
            drawCircle(warna.copy(alpha = if (keadaan == 0) 0.07f else 0.16f), radius = r * 0.88f * denyut)
            drawCircle(Wr.kad, radius = r * 0.78f)
            drawCircle(warna, radius = r * 0.78f, style = Stroke(width = 5.dp.toPx()))
            drawArc(warna, startAngle = -58f, sweepAngle = 296f, useCenter = false,
                topLeft = Offset(c.x - ikon, c.y - ikon + 4.dp.toPx()), size = Size(ikon * 2, ikon * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
            drawLine(warna, Offset(c.x, c.y - ikon * 1.05f), Offset(c.x, c.y + 2.dp.toPx()), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun Cip(t: String, w: Color) {
    Text(t, color = w, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(w.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun Angka(label: String, nilai: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(nilai, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Wr.mu, fontSize = 11.sp)
    }
}

@Composable
fun SubVpn() {
    val ctx = LocalContext.current
    val skop = rememberCoroutineScope()
    var senarai by remember { mutableStateOf(SimpanVpn.senarai(ctx)) }
    var pilih by remember { mutableStateOf(SimpanVpn.pilih(ctx).coerceIn(0, maxOf(0, senarai.size - 1))) }
    var jalan by remember { mutableStateOf(VpnPerkhidmatan.jalan) }
    var sibuk by remember { mutableStateOf(VpnPerkhidmatan.sibuk) }
    var ralat by remember { mutableStateOf("") }
    var tempoh by remember { mutableStateOf(0L) }
    var turun by remember { mutableStateOf(0L) }
    var naik by remember { mutableStateOf(0L) }
    var lajuTurun by remember { mutableStateOf(0L) }
    var lajuNaik by remember { mutableStateOf(0L) }
    var ping by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var mengujiPing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var lt = 0L; var ln = 0L; var lm = 0L
        while (true) {
            while (!St.depan) delay(1000)
            jalan = VpnPerkhidmatan.jalan; sibuk = VpnPerkhidmatan.sibuk; ralat = VpnPerkhidmatan.ralat
            if (jalan) {
                tempoh = System.currentTimeMillis() - VpnPerkhidmatan.masa0
                val st = withContext(Dispatchers.IO) { VpnPerkhidmatan.statistik() }
                if (st != null && st.size >= 4) {
                    val t = st[3]; val n = st[1]; val now = System.currentTimeMillis()
                    if (lm != 0L && now > lm) { lajuTurun = ((t - lt) * 1000 / (now - lm)).coerceAtLeast(0); lajuNaik = ((n - ln) * 1000 / (now - lm)).coerceAtLeast(0) }
                    turun = t; naik = n; lt = t; ln = n; lm = now
                }
            } else { lajuTurun = 0; lajuNaik = 0; lm = 0L; turun = 0; naik = 0 }
            delay(1000)
        }
    }

    val hasil = senarai.map { p -> try { VlessPautan.hurai(p) } catch (_: Exception) { null } }
    fun sambung() {
        val p = senarai.getOrNull(pilih) ?: return
        VpnPerkhidmatan.ralat = ""
        VpnPerkhidmatan.mula(ctx, p)
    }
    val minta = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) sambung() else St.toast(Bhs.t("Kebenaran VPN tidak diberi."))
    }
    fun togol() {
        if (jalan || sibuk) { VpnPerkhidmatan.henti(ctx); return }
        if (senarai.isEmpty()) { St.toast(Bhs.t("Tambah pelayan dahulu.")); return }
        val i = VpnService.prepare(ctx)
        if (i != null) minta.launch(i) else sambung()
    }
    fun tampal() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        val baris = t.lines().map { it.trim() }.filter { it.startsWith("vless://") || it.startsWith("vmess://") }
        if (baris.isEmpty()) { St.toast(Bhs.t("Tiada pautan vless:// atau vmess:// dalam papan klip.")); return }
        var tambah = 0
        val b = senarai.toMutableList()
        for (l in baris) {
            try { VlessPautan.hurai(l); if (l !in b) { b.add(l); tambah++ } } catch (e: Exception) { St.toast(e.message ?: "") }
        }
        senarai = b; SimpanVpn.simpan(ctx, b)
        if (tambah > 0) St.toast(Bhs.f("{0} pelayan ditambah.", tambah.toString()))
    }
    fun ujiPing() {
        if (mengujiPing) return
        mengujiPing = true
        skop.launch {
            val h = hasil
            val m = mutableMapOf<Int, Long>()
            h.forEachIndexed { i, x ->
                if (x != null) {
                    m[i] = withContext(Dispatchers.IO) {
                        try { val t0 = System.nanoTime(); Socket().use { it.connect(InetSocketAddress(x.host, x.port), 3000) }; (System.nanoTime() - t0) / 1_000_000 } catch (_: Exception) { -1L }
                    }
                    ping = m.toMap()
                }
            }
            mengujiPing = false
        }
    }

    val keadaan = if (jalan) 2 else if (sibuk) 1 else 0
    Halaman {
        Judul("VPN telefon")
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ButangKuasa(keadaan) { togol() }
            Spacer(Modifier.height(10.dp))
            Text(
                when (keadaan) { 2 -> Bhs.t("Bersambung"); 1 -> Bhs.t("Menyambung..."); else -> Bhs.t("Sentuh untuk sambung") },
                color = if (keadaan == 2) Wr.ac else Wr.tx, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
            )
            if (keadaan == 2) {
                Text(VpnPerkhidmatan.nama, color = Wr.mu, fontSize = 13.sp, textAlign = TextAlign.Center)
                Text(masaStr(tempoh), color = Wr.tx, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp))
            }
            if (ralat.isNotEmpty() && keadaan == 0) Text(ralat, color = Wr.bad, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
        if (keadaan == 2) {
            KadWr {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Angka(Bhs.t("Muat turun"), saizB(lajuTurun) + "/s", Modifier.weight(1f))
                    Angka(Bhs.t("Muat naik"), saizB(lajuNaik) + "/s", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Angka(Bhs.t("Jumlah turun"), saizB(turun), Modifier.weight(1f))
                    Angka(Bhs.t("Jumlah naik"), saizB(naik), Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pelayan", color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (senarai.isNotEmpty()) TextButton({ ujiPing() }, enabled = !mengujiPing) { Text(if (mengujiPing) "Menguji..." else "Uji ping", color = Wr.ac) }
            TextButton({ tampal() }) { Text("+ Tampal pautan", color = Wr.ac, fontWeight = FontWeight.Bold) }
        }
        if (senarai.isEmpty()) {
            KadWr {
                Text("Belum ada pelayan.", color = Wr.tx, fontWeight = FontWeight.Bold)
                Text("Salin pautan vless:// atau vmess:// kemudian tekan Tampal pautan.", color = Wr.mu, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        senarai.forEachIndexed { i, p ->
            val h = hasil.getOrNull(i)
            val dipilih = i == pilih
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (dipilih) Wr.kad2 else Wr.kad)
                    .border(1.5.dp, if (dipilih) Wr.ac else Wr.garis, RoundedCornerShape(18.dp))
                    .clickable { pilih = i; SimpanVpn.setPilih(ctx, i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(h?.nama ?: p.take(30), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (h != null) Cip(h.ringkas, Wr.ac2)
                        ping[i]?.let { ms -> if (ms >= 0) Cip("$ms ms", if (ms < 150) Wr.ac else Wr.wn) else Cip(Bhs.t("Gagal"), Wr.bad) }
                    }
                }
                TextButton({
                    val b = senarai.toMutableList(); b.removeAt(i); senarai = b; SimpanVpn.simpan(ctx, b)
                    pilih = pilih.coerceIn(0, maxOf(0, b.size - 1)); SimpanVpn.setPilih(ctx, pilih); ping = emptyMap()
                }) { Text("✕", color = Wr.mu, fontSize = 16.sp) }
            }
        }
        Text("Jika WiFi modem anda sudah menjalankan VLESS Pro, matikan salah satu supaya trafik tidak melalui dua lapisan (lebih perlahan).", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
