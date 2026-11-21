package com.vlesspro.pemasang

import android.content.ClipboardManager
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.InetSocketAddress
import java.net.Socket

fun saizB(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format("%.1f KB", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format("%.1f MB", b / 1048576.0)
    else -> String.format("%.2f GB", b / 1073741824.0)
}

/** Rangkaian sebenar telefon (WiFi/data), bukan terowong VPN. */
@Suppress("DEPRECATION")
private fun rangkaianAsal(ctx: Context): android.net.Network? {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val senarai = cm.allNetworks.filter { n ->
        val c = cm.getNetworkCapabilities(n)
        c != null && !c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    return senarai.firstOrNull { cm.getNetworkCapabilities(it)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true } ?: senarai.firstOrNull()
}

private suspend fun ambilIp(proksi: java.net.Proxy?, rangkaian: android.net.Network? = null): String = withContext(Dispatchers.IO) {
    try {
        val url = java.net.URL("https://api.ipify.org")
        val c = (if (proksi != null) url.openConnection(proksi) else if (rangkaian != null) rangkaian.openConnection(url) else url.openConnection()) as java.net.HttpURLConnection
        c.connectTimeout = 7000; c.readTimeout = 7000
        c.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) { sebabIp = e.javaClass.simpleName + ": " + (e.message ?: ""); "?" }
}

@Volatile private var sebabIp = ""

private fun masaStr(ms: Long): String {
    val s = ms / 1000
    return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
}

@Composable
private fun CipVpn(t: String, w: Color) {
    Text(t, color = w, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(w.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** Warna aksen ikut jenis protokol pelayan, supaya senarai lebih mudah dibezakan sepintas lalu. */
private fun warnaJenis(jenis: String): Color = when (jenis.uppercase()) {
    "VLESS" -> Color(0xFFA78BFA)
    "VMESS" -> Color(0xFF60A5FA)
    "TROJAN" -> Color(0xFFFB7185)
    "SS" -> Color(0xFF2DD4BF)
    "SSH" -> Color(0xFFFB923C)
    else -> Wr.ac2
}

@Composable
private fun Angka(label: String, nilai: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(nilai, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Wr.mu, fontSize = 11.sp)
    }
}

@Composable
private fun BtnKecil(t: String, modifier: Modifier = Modifier, warna: Color = Wr.ac, klik: () -> Unit) {
    OutlinedButton(onClick = klik, modifier = modifier, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, warna.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = warna)) { Text(t, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
fun SubVpn() {
    var skrin by remember { mutableStateOf("utama") }
    BackHandler(skrin != "utama") { skrin = "utama" }
    when (skrin) {
        "apl" -> SkrinApl { skrin = "utama" }
        "tetapan" -> SkrinTetapan { skrin = "utama" }
        "tentang" -> SkrinTentang { skrin = "utama" }
        "panduan" -> SkrinPanduan { skrin = "utama" }
        "ssh" -> SkrinSsh { skrin = "utama" }
        else -> SkrinUtama { skrin = it }
    }
}

@Composable
private fun SkrinUtama(ke: (String) -> Unit) {
    val ctx = LocalContext.current
    val skop = rememberCoroutineScope()
    var senarai by remember { mutableStateOf(SimpanVpn.semua(ctx)) }
    var manual by remember { mutableStateOf(SimpanVpn.manual(ctx)) }
    var langganan by remember { mutableStateOf(SimpanVpn.langganan(ctx)) }
    var pilih by remember { mutableStateOf(SimpanVpn.pilih(ctx)) }
    var jalan by remember { mutableStateOf(VpnPerkhidmatan.jalan) }
    var sibuk by remember { mutableStateOf(VpnPerkhidmatan.sibuk) }
    var memulih by remember { mutableStateOf(VpnPerkhidmatan.memulih) }
    var ralat by remember { mutableStateOf("") }
    var tempoh by remember { mutableStateOf(0L) }
    var turun by remember { mutableStateOf(0L) }
    var naik by remember { mutableStateOf(0L) }
    var lajuTurun by remember { mutableStateOf(0L) }
    var lajuNaik by remember { mutableStateOf(0L) }
    var ping by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var mengujiPing by remember { mutableStateOf(false) }
    var pingS by remember { mutableStateOf(0L) }
    var mengujiS by remember { mutableStateOf(false) }
    var ipTel by remember { mutableStateOf("") }
    var ipVpn by remember { mutableStateOf("") }
    var menyemakIp by remember { mutableStateOf(false) }
    var sedangImport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var lt = 0L; var ln = 0L; var lm = 0L
        while (true) {
            while (!St.depan) delay(1000)
            jalan = VpnPerkhidmatan.jalan; sibuk = VpnPerkhidmatan.sibuk; memulih = VpnPerkhidmatan.memulih; ralat = VpnPerkhidmatan.ralat
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
    fun muatSemula() {
        senarai = SimpanVpn.semua(ctx); manual = SimpanVpn.manual(ctx); langganan = SimpanVpn.langganan(ctx)
        pilih = SimpanVpn.pilih(ctx); ping = emptyMap()
    }
    fun masuk(teks: String) {
        if (sedangImport) return
        sedangImport = true
        skop.launch {
            val m = withContext(Dispatchers.IO) { try { VpnImport.proses(ctx, teks) } catch (e: Exception) { e.message ?: e.javaClass.simpleName } }
            muatSemula(); sedangImport = false; St.toast(m)
        }
    }
    fun sambung() { if (!VpnKongsi.mula(ctx)) St.toast(Bhs.t("Tambah pelayan dahulu.")) }
    val minta = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) sambung() else St.toast(Bhs.t("Kebenaran VPN tidak diberi."))
    }
    val imbas = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { masuk(it) } }
    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) skop.launch {
            val t = withContext(Dispatchers.IO) { VpnAlat.imbasGambar(ctx, u) }
            if (t == null) St.toast(Bhs.t("Kod QR tidak ditemui dalam gambar.")) else masuk(t)
        }
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
        if (t.isBlank()) { St.toast(Bhs.t("Papan klip kosong.")); return }
        masuk(t)
    }
    fun ujiPing(terbaik: Boolean) {
        if (mengujiPing) return
        mengujiPing = true
        skop.launch {
            val m = withContext(Dispatchers.IO) { VpnAlat.pingSemua(hasil) }
            ping = m.toMap()
            if (terbaik) {
                val b = m.filter { it.value >= 0 }.minByOrNull { it.value }?.key
                if (b != null) { pilih = b; SimpanVpn.setPilih(ctx, b); St.toast(Bhs.f("Dipilih: {0}", hasil[b]?.nama ?: "")) }
                else St.toast(Bhs.t("Semua pelayan gagal dihubungi."))
            }
            mengujiPing = false
        }
    }
    fun semakIp() {
        if (menyemakIp) return
        menyemakIp = true; ipTel = ""; ipVpn = ""; sebabIp = ""
        skop.launch {
            // Apl ini dikecualikan daripada VPN: sambungan terus = IP telefon sebenar; melalui SOCKS Xray = IP pelayan.
            ipTel = ambilIp(null, rangkaianAsal(ctx))
            ipVpn = ambilIp(java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", VpnPerkhidmatan.PORT)))
            menyemakIp = false
        }
    }
    fun ujiSebenar() {
        if (mengujiS) return
        mengujiS = true; pingS = 0
        skop.launch { pingS = withContext(Dispatchers.IO) { VpnAlat.pingSebenar() }; mengujiS = false }
    }

    val keadaan = if (memulih) 1 else if (jalan) 2 else if (sibuk) 1 else 0
    Halaman {
        Text("VPN Telefon", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF22D3EE), Color(0xFFA78BFA)))),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BtnKecil(Bhs.t("Tetapan"), Modifier.weight(1f), Color(0xFF60A5FA)) { ke("tetapan") }
            BtnKecil(Bhs.t("Per-apl"), Modifier.weight(1f), Color(0xFFA78BFA)) { ke("apl") }
            BtnKecil(Bhs.t("Panduan"), Modifier.weight(1f), Color(0xFFFACC15)) { ke("panduan") }
            BtnKecil(Bhs.t("Tentang"), Modifier.weight(1f), Color(0xFF2DD4BF)) { ke("tentang") }
        }
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val trS = rememberInfiniteTransition(label = "stVpn")
            val kilau by trS.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "kVpn")
            if (keadaan == 2) {
                Text(Bhs.t("Bersambung"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF22D3EE))), alpha = kilau),
                    textAlign = TextAlign.Center)
            } else if (keadaan == 1) {
                Text(if (memulih) Bhs.t("Menyambung semula...") else Bhs.t("Menyambung..."), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFFFACC15), Color(0xFFFB923C))), alpha = kilau),
                    textAlign = TextAlign.Center)
            } else {
                Text(Bhs.t("Sentuh untuk sambung"), color = Wr.tx, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            ButangKuasa(jalan, sibuk || memulih, true, Bhs.t("Sila tunggu"), Bhs.t("Putuskan"), Bhs.t("Sambung")) { togol() }
            if (jalan) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.clip(RoundedCornerShape(50)).background(Wr.kad).border(1.dp, Wr.garis, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Wr.ac))
                    Spacer(Modifier.width(10.dp))
                    Text(masaStr(tempoh), color = Wr.ac, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(VpnPerkhidmatan.nama, color = Wr.mu, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            if (ralat.isNotEmpty() && keadaan == 0) Text(ralat, color = Wr.bad, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
        if (jalan) {
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
            KadWr {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Bhs.t("Ping sebenar"), color = Wr.tx, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (pingS > 0) Text("$pingS ms", color = if (pingS < 300) Wr.ac else Wr.wn, fontWeight = FontWeight.Bold)
                    if (pingS < 0) Text(Bhs.t("Gagal"), color = Wr.bad, fontWeight = FontWeight.Bold)
                    TextButton({ ujiSebenar() }, enabled = !mengujiS) { Text(if (mengujiS) Bhs.t("Menguji...") else Bhs.t("Uji"), color = Wr.ac) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Bhs.t("Semak IP keluar"), color = Wr.tx, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ semakIp() }, enabled = !menyemakIp) { Text(if (menyemakIp) Bhs.t("Menyemak...") else Bhs.t("Semak IP"), color = Wr.ac) }
                }
                if (ipTel.isNotEmpty() || ipVpn.isNotEmpty()) {
                    Text(Bhs.f("IP telefon (tanpa VPN): {0}", ipTel.ifEmpty { "..." }), color = Wr.mu, fontSize = 13.sp)
                    Text(Bhs.f("IP melalui pelayan: {0}", ipVpn.ifEmpty { "..." }), color = Wr.tx, fontSize = 13.sp)
                    if (ipTel == "?" && sebabIp.isNotEmpty()) Text(sebabIp, color = Wr.mu, fontSize = 11.sp)
                    if (ipTel.isNotEmpty() && ipVpn.isNotEmpty() && ipTel != "?" && ipVpn != "?") {
                        if (ipTel == ipVpn) Text(Bhs.t("IP sama: trafik mungkin tidak melalui pelayan."), color = Wr.wn, fontSize = 12.sp)
                        else Text(Bhs.t("IP berbeza: trafik melalui pelayan."), color = Wr.ac, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Bhs.t("Pelayan"), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (senarai.isNotEmpty()) {
                TextButton({ ujiPing(false) }, enabled = !mengujiPing) { Text(if (mengujiPing) Bhs.t("Menguji...") else Bhs.t("Uji ping"), color = Wr.ac) }
                TextButton({ ujiPing(true) }, enabled = !mengujiPing) { Text(Bhs.t("Terpantas"), color = Wr.ac, fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BtnKecil(Bhs.t("Tampal"), Modifier.weight(1f)) { tampal() }
            BtnKecil((if (VpnLesen.berlesen(ctx)) "" else "🔒 ") + Bhs.t("Imbas QR"), Modifier.weight(1f)) {
                if (!VpnLesen.berlesen(ctx)) { St.toast(VpnLesen.mesej("Imbas QR")); return@BtnKecil }
                imbas.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(Bhs.t("Halakan kamera ke kod QR"))
                    .setBeepEnabled(false).setOrientationLocked(false).setCaptureActivity(ImbasQr::class.java))
            }
            BtnKecil((if (VpnLesen.berlesen(ctx)) "" else "🔒 ") + Bhs.t("Imej QR"), Modifier.weight(1f)) { if (VpnLesen.berlesen(ctx)) galeri.launch("image/*") else St.toast(VpnLesen.mesej("Imej QR")) }
            BtnKecil((if (VpnLesen.berlesen(ctx)) "" else "🔒 ") + "+ SSH", Modifier.weight(1f)) { if (VpnLesen.berlesen(ctx)) ke("ssh") else St.toast(VpnLesen.mesej("SSH")) }
        }
        if (!VpnLesen.berlesen(ctx)) Text(VpnLesen.nota(), color = Wr.mu, fontSize = 12.sp)
        if (sedangImport) Text(Bhs.t("Mengimport..."), color = Wr.mu, fontSize = 12.sp)
        if (senarai.isEmpty()) {
            KadWr {
                Text(Bhs.t("Belum ada pelayan."), color = Wr.tx, fontWeight = FontWeight.Bold)
                Text(Bhs.t("1. Salin pautan pelayan anda (vless, vmess, trojan atau ss)."), color = Wr.mu, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text(Bhs.t("2. Tekan Tampal."), color = Wr.mu, fontSize = 13.sp)
                Text(Bhs.t("3. Tekan butang kuasa untuk bersambung."), color = Wr.mu, fontSize = 13.sp)
                TextButton({ ke("panduan") }) { Text(Bhs.t("Lihat panduan penuh"), color = Wr.ac, fontWeight = FontWeight.Bold) }
            }
        }
        senarai.forEachIndexed { i, p ->
            val h = hasil.getOrNull(i)
            val dipilih = i == pilih
            val wj = warnaJenis(h?.jenis ?: "")
            val trKad = rememberInfiniteTransition(label = "kad$i")
            val kilauKad by trKad.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "kk$i")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (dipilih) Wr.kad2 else Wr.kad)
                    .border(if (dipilih) 2.dp else 1.5.dp, if (dipilih) Wr.ac.copy(alpha = kilauKad) else Wr.garis, RoundedCornerShape(18.dp))
                    .clickable { pilih = i; SimpanVpn.setPilih(ctx, i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(50)).background(wj))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(h?.nama ?: p.take(30), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (h != null) CipVpn(h.ringkas, wj)
                        ping[i]?.let { ms -> if (ms >= 0) CipVpn("$ms ms", if (ms < 150) Wr.ac else Wr.wn) else CipVpn(Bhs.t("Gagal"), Wr.bad) }
                    }
                }
                if (p in manual) {
                    if (!p.startsWith("ssh://")) TextButton({
                        bg { lapor(Modem.tambahNod(p)) }
                    }) { Text("↗", color = Wr.ac, fontSize = 16.sp) }
                    TextButton({
                        SimpanVpn.simpanManual(ctx, manual.filter { it != p })
                        muatSemula()
                    }) { Text("✕", color = Wr.mu, fontSize = 16.sp) }
                } else CipVpn(Bhs.t("Langganan"), Wr.mu)
            }
        }
        if (langganan.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(Bhs.t("Langganan"), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton({
                    if (!sedangImport) { sedangImport = true; skop.launch { val m = withContext(Dispatchers.IO) { VpnImport.segarkan(ctx) }; muatSemula(); sedangImport = false; St.toast(m) } }
                }) { Text(Bhs.t("Segar semua"), color = Wr.ac, fontWeight = FontWeight.Bold) }
            }
            langganan.forEach { (u, l) ->
                KadWr {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(try { java.net.URL(u).host } catch (_: Exception) { u.take(30) }, color = Wr.tx, fontWeight = FontWeight.Bold)
                            Text(Bhs.f("{0} pelayan", l.size.toString()), color = Wr.mu, fontSize = 12.sp)
                        }
                        TextButton({ SimpanVpn.simpanLangganan(ctx, langganan.filter { it.first != u }); muatSemula() }) { Text("✕", color = Wr.mu, fontSize = 16.sp) }
                    }
                }
            }
        }
        if (manual.any { !it.startsWith("ssh://") }) Text(Bhs.t("Tekan ↗ untuk menghantar pelayan ke modem (perlu disambung ke WiFi modem)."), color = Wr.mu, fontSize = 12.sp)
        Text(Bhs.t("Ping TCP hanyalah anggaran (nod D1G1 menunjukkan ping palsu). Ping sebenar boleh diuji selepas bersambung."), color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Text(Bhs.t("Jika WiFi modem anda sudah menjalankan VLESS Pro, matikan salah satu supaya trafik tidak melalui dua lapisan (lebih perlahan)."), color = Wr.mu, fontSize = 12.sp)
        Text(Bhs.f("Apl {0} · Xray {1}", Kemas.namaSemasa(ctx), VpnPerkhidmatan.VERSI_XRAY), color = Wr.mu, fontSize = 11.sp)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SkrinTetapan(kembali: () -> Unit) {
    val ctx = LocalContext.current
    var auto by remember { mutableStateOf(VpnTetapan.auto(ctx)) }
    var kill by remember { mutableStateOf(VpnTetapan.kill(ctx)) }
    var boot by remember { mutableStateOf(VpnTetapan.boot(ctx)) }
    var cepat by remember { mutableStateOf(VpnTetapan.terpantas(ctx)) }
    var tukar by remember { mutableStateOf(VpnTetapan.tukar(ctx)) }
    var failover by remember { mutableStateOf(VpnTetapan.failover(ctx)) }
    var rLan by remember { mutableStateOf(VpnTetapan.routingLan(ctx)) }
    var rIklan by remember { mutableStateOf(VpnTetapan.routingIklan(ctx)) }
    var dns by remember { mutableStateOf(VpnTetapan.dns(ctx)) }
    var dnsSendiri by remember { mutableStateOf(false) }
    val dnsPilihan = listOf("" to Bhs.t("Lalai (1.1.1.1, 8.8.8.8)"), "1.1.1.1" to "Cloudflare (1.1.1.1)", "8.8.8.8" to "Google (8.8.8.8)",
        "9.9.9.9" to "Quad9 (9.9.9.9)", "208.67.222.222" to "OpenDNS (208.67.222.222)", "94.140.14.14" to "AdGuard (94.140.14.14)")
    LaunchedEffect(Unit) { dnsSendiri = dns.isNotEmpty() && dnsPilihan.none { it.first == dns } }
    Halaman {
        Judul(Bhs.t("Tetapan VPN"))
        Suis(Bhs.t("Auto-tukar dengan VLESS modem"), tukar) { tukar = it; VpnTetapan.set(ctx, "vpn_tukar", it) }
        Text(Bhs.t("Bila VPN telefon dihidupkan, VLESS modem dimatikan automatik, dan sebaliknya, supaya trafik tidak melalui dua lapisan."), color = Wr.mu, fontSize = 12.sp)
        Suis(Bhs.t("Sambung semula automatik"), auto) { auto = it; VpnTetapan.set(ctx, "vpn_auto", it) }
        Text(Bhs.t("Jika sambungan terputus atau rangkaian bertukar (WiFi/data), VPN cuba menyambung semula sendiri."), color = Wr.mu, fontSize = 12.sp)
        if (auto) {
            Suis(Bhs.t("Failover: tukar pelayan lain jika gagal berulang"), failover) { failover = it; VpnTetapan.set(ctx, "vpn_failover", it) }
            Text(Bhs.t("Jika sambung semula ke pelayan yang sama gagal 3 kali berturut-turut, cuba tukar ke pelayan lain yang disimpan (dipilih ikut ping terendah)."), color = Wr.mu, fontSize = 11.sp)
        }
        Suis(Bhs.t("Kill switch"), kill) { kill = it; VpnTetapan.set(ctx, "vpn_kill", it) }
        Text(Bhs.t("Semasa VPN terputus dan sedang cuba semula, internet disekat (tidak bocor ikut IP asal) dan percubaan tidak berhenti."), color = Wr.mu, fontSize = 12.sp)
        Suis(Bhs.t("Sambung semasa telefon dihidupkan"), boot) { boot = it; VpnTetapan.set(ctx, "vpn_boot", it) }
        Text(Bhs.t("Memerlukan kebenaran VPN diberi sekali dan apl tidak dihentikan paksa."), color = Wr.mu, fontSize = 12.sp)
        Suis(Bhs.t("Pilih pelayan terpantas semasa sambung"), cepat) { cepat = it; VpnTetapan.set(ctx, "vpn_terpantas", it) }
        Text(Bhs.t("Menguji ping semua pelayan dan memilih yang terendah setiap kali menyambung."), color = Wr.mu, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text(Bhs.t("Penghalaan (Routing)"), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Suis(Bhs.t("Pintas rangkaian tempatan (LAN)"), rLan) { rLan = it; VpnTetapan.set(ctx, "vpn_routing_lan", it) }
        Text(Bhs.t("Peranti dalam WiFi/rangkaian tempatan anda (printer, NAS, dll) disambung terus, tidak melalui pelayan VPN."), color = Wr.mu, fontSize = 11.sp)
        Suis(Bhs.t("Sekat iklan asas"), rIklan) { rIklan = it; VpnTetapan.set(ctx, "vpn_routing_iklan", it) }
        Text(Bhs.t("Guna senarai category-ads-all (200,000+ domain iklan/penjejak), sama seperti v2rayNG."), color = Wr.mu, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Text(Bhs.t("DNS"), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Pilihan(Bhs.t("Pelayan DNS"), dnsPilihan + ("__c" to Bhs.t("Lain-lain (taip sendiri)")), if (dnsSendiri) "__c" else dns) { v ->
            if (v == "__c") dnsSendiri = true else { dnsSendiri = false; dns = v; VpnTetapan.setDns(ctx, v) }
        }
        if (dnsSendiri) OutlinedTextField(dns, { dns = it.trim(); VpnTetapan.setDns(ctx, dns) }, singleLine = true,
            label = { Text(Bhs.t("Alamat IP (cth 1.1.1.1)")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = medan())
        Text(Bhs.t("Perubahan penghalaan dan DNS terpakai pada sambungan seterusnya."), color = Wr.mu, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Btn(Bhs.t("Buka tetapan VPN Android"), utama = false) {
            try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
        Text(Bhs.t("Untuk kill switch peringkat sistem, di sana hidupkan Sentiasa-hidup VPN dan Sekat sambungan tanpa VPN. Perubahan di halaman ini terpakai pada sambungan seterusnya."), color = Wr.mu, fontSize = 12.sp)
        Btn(Bhs.t("Kembali"), utama = false) { kembali() }
    }
}

@Composable
private fun SkrinApl(kembali: () -> Unit) {
    val ctx = LocalContext.current
    var mod by remember { mutableStateOf(VpnTetapan.modApl(ctx)) }
    var dipilih by remember { mutableStateOf(VpnTetapan.apl(ctx)) }
    var cari by remember { mutableStateOf("") }
    var senarai by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        senarai = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != ctx.packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
    }
    val pilihan = listOf(Bhs.t("Semua apl guna VPN"), Bhs.t("Hanya apl dipilih guna VPN"), Bhs.t("Semua apl guna VPN kecuali yang dipilih"))
    Halaman {
        Judul(Bhs.t("VPN per-apl"))
        pilihan.forEachIndexed { i, t ->
            KadWr(klik = { if (i != 0 && !VpnLesen.berlesen(ctx)) St.toast(VpnLesen.mesej("VPN per-apl")) else { mod = i; VpnTetapan.setModApl(ctx, i) } }) {
                Text((if (mod == i) "◉  " else "○  ") + (if (i != 0 && !VpnLesen.berlesen(ctx)) "🔒 " else "") + t, color = if (mod == i) Wr.ac else Wr.tx, fontWeight = FontWeight.Bold)
            }
        }
        Text(Bhs.t("Perubahan terpakai pada sambungan seterusnya. Sambung semula VPN selepas mengubah."), color = Wr.mu, fontSize = 12.sp)
        if (mod != 0) {
            OutlinedTextField(value = cari, onValueChange = { cari = it }, singleLine = true, colors = medan(),
                label = { Text(Bhs.t("Cari apl")) }, modifier = Modifier.fillMaxWidth())
            Text(Bhs.f("{0} apl dipilih", dipilih.size.toString()), color = Wr.mu, fontSize = 12.sp)
            senarai.filter { cari.isBlank() || it.second.contains(cari, true) }.forEach { (pkg, nama) ->
                Suis(nama, pkg in dipilih) { on ->
                    dipilih = if (on) dipilih + pkg else dipilih - pkg
                    VpnTetapan.setApl(ctx, dipilih)
                }
            }
        }
        Btn(Bhs.t("Kembali"), utama = false) { kembali() }
    }
}

@Composable
private fun SkrinPanduan(kembali: () -> Unit) {
    Halaman {
        Judul(Bhs.t("Cara guna VPN telefon"))
        Text(Bhs.t("VPN telefon menghantar trafik telefon ini melalui pelayan anda sendiri. Sesuai digunakan di luar rumah, ketika VPN modem tidak boleh dicapai."), color = Wr.mu, fontSize = 13.sp)
        @Composable fun langkah(n: String, t: String, d: String) = KadWr {
            Text("$n. " + Bhs.t(t), color = Wr.tx, fontWeight = FontWeight.Bold)
            Text(Bhs.t(d), color = Wr.mu, fontSize = 13.sp)
        }
        langkah("1", "Tambah pelayan", "Salin pautan pelayan (vless, vmess, trojan atau ss) daripada penyedia anda, kemudian tekan Tampal. Pautan langganan juga boleh digunakan.")
        langkah("2", "Sambung", "Tekan butang kuasa. Kali pertama, Android meminta kebenaran VPN: tekan OK. Ikon kunci muncul di bar status.")
        langkah("3", "Semak", "Tekan Semak IP. IP melalui pelayan mesti berbeza daripada IP telefon. Ping sebenar menunjukkan kelajuan sambungan semasa.")
        langkah("4", "Pilih pelayan terbaik", "Tekan Uji ping untuk melihat pelayan yang hidup. Terpantas memilih pelayan dengan ping terendah.")
        langkah("5", "Di rumah dan di luar", "Apabila anda bersambung ke WiFi modem, VPN telefon dan VLESS modem tidak dihidupkan serentak: salah satu dimatikan secara automatik. Boleh ditukar di Tetapan.")
        langkah("6", "Tetapan berguna", "Sambung semula automatik memulihkan VPN jika rangkaian terputus. Failover tukar ke pelayan lain jika pelayan semasa terus gagal. Kill switch menyekat internet jika VPN terputus. Per-apl memilih apl yang menggunakan VPN. Penghalaan boleh pintas LAN atau sekat iklan, dan DNS boleh ditukar ikut citarasa.")
        Btn(Bhs.t("Kembali")) { kembali() }
    }
}

@Composable
private fun SkrinTentang(kembali: () -> Unit) {
    val ctx = LocalContext.current
    var log by remember { mutableStateOf(VpnPerkhidmatan.logTerkini()) }
    LaunchedEffect(Unit) { while (true) { log = VpnPerkhidmatan.logTerkini(); delay(2000) } }
    Halaman {
        Judul(Bhs.t("Tentang"))
        KadWr {
            Text("VLESS Pro", color = Wr.tx, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(Bhs.f("Versi apl: {0} (binaan {1})", Kemas.namaSemasa(ctx), Kemas.versiSemasa(ctx).toString()), color = Wr.tx, fontSize = 13.sp)
            Text(Bhs.f("Teras Xray: {0}", VpnPerkhidmatan.VERSI_XRAY), color = Wr.mu, fontSize = 13.sp)
            Text(android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE, color = Wr.mu, fontSize = 13.sp)
            Text(Bhs.f("Status: {0}", if (VpnPerkhidmatan.jalan) Bhs.t("Bersambung") else Bhs.t("Putus")), color = Wr.mu, fontSize = 13.sp)
        }
        Text(Bhs.t("VPN telefon ini percuma. Hanya versi modem (VLESS Pro modem) yang berbayar."), color = Wr.mu, fontSize = 13.sp)
        Btn(Bhs.t("Hantar log kepada penyokong")) {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "VLESS Pro VPN log").putExtra(android.content.Intent.EXTRA_TEXT, VpnLaporan.bina(ctx))
            try { ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
        Text(Bhs.t("Log tidak mengandungi pautan, kata laluan, UUID atau nama domain."), color = Wr.mu, fontSize = 12.sp)
        if (log.isNotBlank()) {
            Text(Bhs.t("Log terkini"), color = Wr.tx, fontWeight = FontWeight.Bold)
            Text(Diagnosis.bersih(log).lines().takeLast(12).joinToString("\n"), color = Wr.mu, fontSize = 11.sp)
        }
        Btn(Bhs.t("Kembali"), utama = false) { kembali() }
    }
}

@Composable
private fun SkrinSsh(kembali: () -> Unit) {
    val ctx = LocalContext.current
    var nama by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var pengguna by remember { mutableStateOf("") }
    var kata by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    @Composable fun medanTeks(label: String, nilai: String, ubah: (String) -> Unit, rahsia: Boolean = false) {
        OutlinedTextField(value = nilai, onValueChange = ubah, singleLine = true, colors = medan(), label = { Text(label) },
            visualTransformation = if (rahsia) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth())
    }
    Halaman {
        Judul(Bhs.t("Tambah SSH"))
        medanTeks(Bhs.t("Nama"), nama, { nama = it })
        medanTeks(Bhs.t("Hos atau IP"), host, { host = it.trim() })
        medanTeks(Bhs.t("Port"), port, { port = it.filter { c -> c.isDigit() } })
        medanTeks(Bhs.t("Pengguna"), pengguna, { pengguna = it.trim() })
        medanTeks(Bhs.t("Kata laluan"), kata, { kata = it }, true)
        medanTeks(Bhs.t("SNI (kosong = tanpa SSL/TLS)"), sni, { sni = it.trim() })
        medanTeks(Bhs.t("Payload (kosong = tiada)"), payload, { payload = it })
        BtnKecil(Bhs.t("Isi payload WebSocket"), Modifier.fillMaxWidth()) { payload = SshPautan.PAYLOAD_WS }
        Text(Bhs.t("Payload menyokong [crlf], [host], [port]. SNI diisi untuk SSH melalui SSL/TLS. Isi kedua-duanya untuk SSL + payload."), color = Wr.mu, fontSize = 12.sp)
        Btn(Bhs.t("Simpan")) {
            if (host.isBlank() || pengguna.isBlank()) { St.toast(Bhs.t("Isi hos dan pengguna.")); return@Btn }
            val p = SshProfil(nama.ifBlank { host }, host, port.toIntOrNull() ?: 22, pengguna, kata, sni, payload)
            val m = SimpanVpn.manual(ctx).toMutableList(); m.add(SshPautan.bina(p)); SimpanVpn.simpanManual(ctx, m)
            St.toast(Bhs.t("Pelayan SSH disimpan.")); kembali()
        }
        Btn(Bhs.t("Kembali"), utama = false) { kembali() }
    }
}
