package com.vlesspro.pemasang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

object Wr {
    var id by mutableStateOf("aurora")
    var terang by mutableStateOf(false)
    var bg by mutableStateOf(Color(0xFF080B1A))
    var bar by mutableStateOf(Color(0xFF0E1328))
    var kad by mutableStateOf(Color(0xFF121935))
    var kad2 by mutableStateOf(Color(0xFF182046))
    var garis by mutableStateOf(Color(0xFF222C55))
    var ac by mutableStateOf(Color(0xFF22D3EE))
    var ac2 by mutableStateOf(Color(0xFFA78BFA))
    var tx by mutableStateOf(Color(0xFFEAF0FF))
    var mu by mutableStateOf(Color(0xFF8591B8))
    var on by mutableStateOf(Color(0xFF03212B))
    var inp by mutableStateOf(Color(0xFF0B1024))
    var ff by mutableStateOf<FontFamily?>(null)
    val bad = Color(0xFFF87171)
    val wn = Color(0xFFF59E0B)
    val ok = Color(0xFF4ADE80)
    val grad get() = Brush.linearGradient(listOf(ac, ac2))
}

fun bg(blok: () -> Unit) {
    Thread {
        try { blok() } catch (e: SalahKata) { St.toast("Kata laluan salah") }
        catch (e: TiadaSambungan) { St.luar = true; St.toast("Tiada sambungan ke modem") }
        catch (e: Exception) { St.toast(Bhs.t("Ralat") + ": ${e.message ?: e.javaClass.simpleName}") }
    }.start()
}

fun fB(n: Double): String {
    var v = n
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    while (v >= 1024 && i < 4) { v /= 1024; i++ }
    return (if (i == 0) "%.0f".format(v) else if (v >= 100) "%.0f".format(v) else "%.1f".format(v)) + " " + u[i]
}

@Composable
fun KadWr(modifier: Modifier = Modifier, klik: (() -> Unit)? = null, isi: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Wr.kad2, Wr.kad)))
        .border(1.dp, Wr.garis, RoundedCornerShape(24.dp))
    Column((if (klik != null) m.clickable { klik() } else m).padding(16.dp), content = isi)
}

@Composable
fun IkonKuasa(warna: Color, saiz: Int = 34) {
    Canvas(Modifier.size(saiz.dp)) {
        val w = this.size.width
        val st = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
        drawArc(warna, -55f, 290f, false, topLeft = Offset(w * 0.17f, w * 0.2f), size = Size(w * 0.66f, w * 0.66f), style = st)
        drawLine(warna, Offset(w * 0.5f, w * 0.1f), Offset(w * 0.5f, w * 0.5f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
    }
}

@Composable
fun IkonDenyut(warna: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = this.size.width
        val p = Path().apply {
            moveTo(0f, w * 0.55f); lineTo(w * 0.28f, w * 0.55f); lineTo(w * 0.4f, w * 0.2f)
            lineTo(w * 0.6f, w * 0.85f); lineTo(w * 0.72f, w * 0.55f); lineTo(w, w * 0.55f)
        }
        drawPath(p, warna, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
    }
}

@Composable
fun IkonPerisai(warna: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = this.size.width
        val p = Path().apply {
            moveTo(w * 0.5f, w * 0.06f); lineTo(w * 0.86f, w * 0.2f); lineTo(w * 0.86f, w * 0.5f)
            cubicTo(w * 0.86f, w * 0.74f, w * 0.7f, w * 0.9f, w * 0.5f, w * 0.96f)
            cubicTo(w * 0.3f, w * 0.9f, w * 0.14f, w * 0.74f, w * 0.14f, w * 0.5f)
            lineTo(w * 0.14f, w * 0.2f); close()
        }
        drawPath(p, warna, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
    }
}

@Composable
fun IkonSenarai(warna: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = this.size.width
        for (i in 0..2) {
            val y = w * (0.22f + 0.28f * i)
            drawCircle(warna, w * 0.06f, Offset(w * 0.14f, y))
            drawLine(warna, Offset(w * 0.3f, y), Offset(w * 0.92f, y), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun IkonRumah(warna: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = this.size.width
        val p = Path().apply {
            moveTo(w * 0.1f, w * 0.48f); lineTo(w * 0.5f, w * 0.12f); lineTo(w * 0.9f, w * 0.48f)
            moveTo(w * 0.22f, w * 0.42f); lineTo(w * 0.22f, w * 0.88f); lineTo(w * 0.78f, w * 0.88f); lineTo(w * 0.78f, w * 0.42f)
        }
        drawPath(p, warna, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
    }
}

@Composable
fun IkonGrid(warna: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = this.size.width
        for (x in 0..1) for (y in 0..1)
            drawRoundRect(warna, Offset(w * (0.12f + 0.42f * x), w * (0.12f + 0.42f * y)), Size(w * 0.34f, w * 0.34f),
                androidx.compose.ui.geometry.CornerRadius(w * 0.08f), style = Stroke(width = w * 0.09f))
    }
}

// ───────────────────────── Akar apl ─────────────────────────

@Composable
fun AppNatif() {
    val ctx = LocalContext.current
    val pref = remember { ctx.getSharedPreferences("vp", Context.MODE_PRIVATE) }
    var tahap by remember { mutableStateOf("mula") } // mula | setup | utama | pasang
    var ralatSetup by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val ip = pref.getString("ip", null)
        val ps = pref.getString("pass", null)
        if (ip == null || ps == null) { delay(900); tahap = "setup"; return@LaunchedEffect }
        Modem.ip = ip; Modem.pass = ps
        val t0 = System.currentTimeMillis()
        val ok = withContext(Dispatchers.IO) {
            try { Modem.log(ip, ps); 1 } catch (_: SalahKata) { 2 } catch (_: TiadaSambungan) { 0 }
        }
        val sisa = 1100 - (System.currentTimeMillis() - t0)
        if (sisa > 0) delay(sisa)
        when (ok) {
            2 -> { ralatSetup = "Kata laluan panel telah berubah. Masukkan semula."; tahap = "setup" }
            else -> { St.luar = ok == 0; tahap = "utama" }
        }
    }

    CompositionLocalProvider(LocalContentColor provides Wr.tx) {
    Box(Modifier.fillMaxSize().background(Wr.bg)) {
        when (tahap) {
            "mula" -> Splash()
            "setup" -> Setup(pref, ralatSetup, { tahap = "utama" }, { tahap = "pasang" }, { tahap = "vpn" })
            "vpn" -> Column(Modifier.fillMaxSize()) {
                BackHandler { tahap = "setup" }
                TextButton(onClick = { tahap = "setup" }, modifier = Modifier.padding(8.dp)) { Text("‹ Kembali", color = Wr.ac, fontSize = 16.sp) }
                Box(Modifier.weight(1f)) { SubVpn() }
            }
            "pasang" -> Column(Modifier.fillMaxSize()) {
                var edisi by remember { mutableStateOf(Edisi.B618) }
                var ip by remember { mutableStateOf(edisi.ipLalai) }
                Box(Modifier.weight(1f)) {
                    Pemasangan(edisi, ip, { e -> edisi = e; ip = e.ipLalai }, { ip = it }, { tahap = "setup" })
                }
            }
            else -> Utama2(pref) { tahap = "setup"; ralatSetup = "" }
        }
        if (St.mesej.isNotEmpty()) {
            LaunchedEffect(St.mesej) { delay(2600); St.mesej = "" }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp).clip(RoundedCornerShape(16.dp))
                .background(Wr.kad2).padding(horizontal = 18.dp, vertical = 11.dp)) {
                Text(St.mesej, color = Wr.tx, fontSize = 14.sp)
            }
        }
    }
    }
}

@Composable
fun Setup(pref: android.content.SharedPreferences, ralat0: String, siap: () -> Unit, pasang: () -> Unit, vpn: () -> Unit) {
    var ip by remember { mutableStateOf(pref.getString("ip", "192.168.8.1") ?: "192.168.8.1") }
    var ps by remember { mutableStateOf("") }
    var baru by remember { mutableStateOf("") }
    var ralat by remember { mutableStateOf(ralat0) }
    var sibuk by remember { mutableStateOf(false) }
    var tukar by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("VLESS PRO", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp, style = TextStyle(brush = Wr.grad))
        Text("Sambung ke modem anda", color = Wr.mu, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        KadWr {
            OutlinedTextField(ip, { ip = it.trim() }, label = { Text("Alamat IP modem") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = medan())
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                sibuk = true; ralat = "Mencari modem ..."
                bg {
                    val h = Modem.kesan(listOf("192.168.8.1", "192.168.1.1"))
                    if (h != null) { ip = h; ralat = "Modem dijumpai: $h" } else ralat = "Modem tidak dijumpai. Sambung telefon ke WiFi modem."
                    sibuk = false
                }
            }, enabled = !sibuk, modifier = Modifier.fillMaxWidth()) { Text("Cari modem automatik", color = Wr.ac) }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(ps, { ps = it }, label = { Text("Kata laluan panel (awal: admin)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), colors = medan())
            if (tukar) {
                Spacer(Modifier.height(10.dp))
                Text("Pemasangan baharu: tetapkan kata laluan anda sendiri (min 6 aksara).", color = Wr.wn, fontSize = 13.sp)
                OutlinedTextField(baru, { baru = it }, label = { Text("Kata laluan baharu") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(), colors = medan())
            }
        }
        if (ralat.isNotEmpty()) Text(ralat, color = if (ralat.startsWith("Modem dijumpai")) Wr.ok else Wr.wn, fontSize = 13.sp)
        Button(onClick = {
            sibuk = true; ralat = "Menyambung ..."
            bg {
                try {
                    val s = Modem.log(ip, if (tukar) "admin" else ps.ifEmpty { "admin" })
                    if (s.optBoolean("mustchange")) {
                        if (!tukar) { tukar = true; ralat = "Tetapkan kata laluan baharu di bawah, kemudian tekan Sambung." }
                        else {
                            val (ok, m) = Modem.tukarKata(baru)
                            if (!ok) ralat = m.ifEmpty { "Gagal menukar kata laluan" }
                            else { pref.edit().putString("ip", ip).putString("pass", Modem.pass).apply(); St.luar = false; siap() }
                        }
                    } else {
                        pref.edit().putString("ip", ip).putString("pass", Modem.pass).apply(); St.luar = false; siap()
                    }
                } catch (_: SalahKata) { ralat = "Kata laluan salah." }
                catch (e: TiadaSambungan) { ralat = "Tidak dapat sambung ke $ip. Pastikan WiFi modem dan VLESS Pro sudah dipasang." }
                sibuk = false
            }
        }, enabled = !sibuk && ip.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Wr.ac, contentColor = Wr.on)) {
            Text(if (sibuk) "Sila tunggu ..." else "Sambung", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = pasang, modifier = Modifier.fillMaxWidth()) {
            Text("VLESS Pro belum dipasang pada modem? Pasang sekarang", color = Wr.ac2)
        }
        OutlinedButton(onClick = vpn, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("VPN telefon (tanpa modem)", color = Wr.ac)
        }
    }
}

@Composable
fun medan() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Wr.tx, unfocusedTextColor = Wr.tx, focusedBorderColor = Wr.ac, unfocusedBorderColor = Wr.garis,
    focusedLabelColor = Wr.ac, unfocusedLabelColor = Wr.mu, cursorColor = Wr.ac)

// ───────────────────────── Skrin utama ─────────────────────────

@Composable
fun Utama2(pref: android.content.SharedPreferences, keluar: () -> Unit) {
    val ctx0 = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf<String?>(null) }
    var sahJom by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var n = 0
        var tukarMasa = 0L
        var ipMasa = 0L // semakan IP keluar: jarang, kerana modem menghantar beberapa permintaan melalui VLESS setiap kali
        while (true) {
            while (!St.depan) delay(1000) // jeda bila apl di latar belakang
            withContext(Dispatchers.IO) {
                try {
                    val s = Modem.status()
                    St.status = s
                    St.jamOfset = s.optLong("now") - System.currentTimeMillis() / 1000
                    s.optJSONObject("lic")?.let { VpnLesen.tetap(ctx0, it.optString("state") == "active") }
                    St.luar = false
                    val kini = System.currentTimeMillis()
                    if (s.optBoolean("vless") && kini - ipMasa > (if (St.ipInfo == null) 15000L else 120000L)) {
                        ipMasa = kini
                        St.ipInfo = Modem.ipInfo().takeIf { it.optBoolean("ok") }
                    }
                    if (VpnTetapan.tukar(ctx0) && VpnPerkhidmatan.jalan && !VpnPerkhidmatan.memulih && St.on() && !St.sibuk && kini - tukarMasa > 60000) {
                        tukarMasa = kini
                        if (VpnPerkhidmatan.masa0 > St.masaModOn) {
                            Modem.mod(false)
                            St.toast("VLESS modem dimatikan kerana VPN telefon hidup")
                        } else {
                            VpnPerkhidmatan.henti(ctx0)
                            St.toast("VPN telefon dimatikan kerana VLESS modem hidup")
                        }
                    }
                    if (n % 4 == 0 || St.nod.isEmpty()) St.nod = Modem.nodes()
                    if (n % 4 == 0) St.vpnMod = Modem.obj("/api/opts").optString("vpnmode_eff")
                } catch (_: SalahKata) { St.toast("Kata laluan telah berubah"); St.perluLog = true }
                catch (_: Exception) { St.luar = true }
            }
            n++
            delay(3000)
        }
    }
    LaunchedEffect(Unit) {
        if (pref.getBoolean("autoapl", true)) withContext(Dispatchers.IO) {
            try {
                val t = Kemas.terkini()
                St.aplBaru = if (t.code > Kemas.versiSemasa(ctx0)) t.name else ""
                pref.edit().putLong("cekapl", System.currentTimeMillis() / 1000).apply()
            } catch (_: Exception) {}
        }
    }
    LaunchedEffect(St.perluLog) { if (St.perluLog) { St.perluLog = false; pref.edit().remove("pass").apply(); keluar() } }
    BackHandler(enabled = sub != null) { sub = indukSub(sub) }
    BackHandler(enabled = sub == null && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (St.luar) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF3A2A12)).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Tiada sambungan ke modem. Sambung telefon ke WiFi modem.", color = Wr.wn, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = keluar) { Text("Tukar", color = Wr.tx, fontSize = 12.sp) }
                }
            }
            if (St.aplBaru.isNotEmpty() && sub != "apl") {
                Row(Modifier.fillMaxWidth().background(Wr.kad2).clickable { sub = "apl" }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(Bhs.t("Versi apl baharu tersedia") + " · v" + St.aplBaru, color = Wr.wn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Kemas kini", color = Wr.ac, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.weight(1f)) {
                if (sub != null) Sub(sub!!, pref, keluar, { sub = it }) { sub = indukSub(sub) }
                else when (tab) {
                    0 -> Utama()
                    1 -> Profil { sub = it }
                    2 -> SubVpn()
                    else -> Lagi { sub = it }
                }
            }
        }
        if (sub == null) BarBawah(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun BarBawah(tab: Int, pilih: (Int) -> Unit, modifier: Modifier) {
    val on = St.on()
    val busy = St.sibuk
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(33.dp)).background(Wr.bar.copy(alpha = 0.92f))
            .border(1.dp, Wr.garis, RoundedCornerShape(33.dp)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            @Composable fun item(i: Int, nama: String, ikon: @Composable (Color) -> Unit) {
                val a = tab == i
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).clickable { pilih(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ikon(if (a) Wr.ac else Wr.mu)
                    Text(nama, fontSize = 11.sp, color = if (a) Wr.ac else Wr.mu, fontWeight = if (a) FontWeight.Bold else FontWeight.Normal)
                }
            }
            item(0, "Utama") { IkonRumah(it) }
            item(1, "Profil") { IkonSenarai(it) }
            Spacer(Modifier.width(72.dp))
            item(2, "VPN") { IkonPerisai(it) }
            item(3, "Lagi") { IkonGrid(it) }
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).size(66.dp).clip(CircleShape)
            .background(if (on) Wr.grad else Brush.linearGradient(listOf(Color(0xFF3B4470), Color(0xFF232A4A))))
            .border(3.dp, Wr.bg, CircleShape).clickable(enabled = !busy) { pindahMod() },
            contentAlignment = Alignment.Center) {
            IkonKuasa(if (on) Wr.on else Color(0xFFCFD5E8), 30)
        }
    }
}

// ───────────────────────── Utama ─────────────────────────

private var sahPending: (() -> Unit)? = null

fun pindahMod() {
    val s = St.status ?: return
    if (s.optBoolean("locked")) { St.toast("Aktifkan lesen dahulu (Lagi > Lesen)"); return }
    val hidup = St.on()
    if (!hidup) {
        St.masaModOn = System.currentTimeMillis()
        if (VpnTetapan.tukar(VpApp.ctx) && (VpnPerkhidmatan.jalan || VpnPerkhidmatan.sibuk)) {
            VpnPerkhidmatan.henti(VpApp.ctx)
            St.toast("VPN telefon dimatikan kerana VLESS modem dihidupkan")
        }
    }
    St.sibuk = true
    bg {
        try {
            val (ok, m) = Modem.mod(!hidup)
            St.toast(m.ifEmpty { if (ok) "Diproses" else "Gagal" })
            var n = 0
            while (n < 22) {
                Thread.sleep(3000)
                val st = Modem.status()
                St.status = st
                val siap = if (!hidup) st.optString("mode") == "vless" && st.optBoolean("vless") else st.optString("mode") == "xlite"
                if (siap) break
                n++
            }
            St.ipInfo = null
        } finally {
            St.sibuk = false
        }
    }
}

@Composable
fun Utama() {
    val s = St.status
    val on = St.on()
    val sibuk = St.sibuk
    var sahkan by remember { mutableStateOf(false) }
    var kini by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { kini = System.currentTimeMillis() / 1000; delay(1000) } }

    val tr = rememberInfiniteTransition(label = "u")
    val putar by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(if (sibuk) 900 else 3200, easing = LinearEasing)), label = "p")
    val nafas by tr.animateFloat(0.85f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n")

    val jam = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val sapa = if (jam < 11) "Selamat pagi" else if (jam < 15) "Selamat tengah hari" else if (jam < 19) "Selamat petang" else "Selamat malam"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("VLESS PRO", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, style = TextStyle(brush = Wr.grad))
        Column {
            Text(sapa, color = Wr.mu, fontSize = 14.sp)
            Text(if (s == null) "Menyambung ..." else if (on) "Anda disambung" else if (s.optString("mode") == "xlite") "VLESS Pro tidak aktif" else "Tiada sambungan",
                fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Wr.ac else Wr.tx)
            val ip = St.ipInfo
            Text(if (on && ip != null) "${ip.optString("ip")} · ${ip.optString("country")}"
            else if (s != null && !on && s.optBoolean("xlite")) "VPN asal sedang digunakan" else if (on) "Memeriksa IP ..." else "Sentuh butang untuk menyambung",
                color = Wr.mu, fontSize = 14.sp)
        }

        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(250.dp)) {
                val w = size.width
                drawCircle(Wr.garis, w / 2 - 6f, style = Stroke(2f))
                if (on || sibuk) {
                    drawArc(Brush.sweepGradient(listOf(Color.Transparent, Wr.ac, Wr.ac2, Color.Transparent)), putar, 200f, false,
                        topLeft = Offset(6f, 6f), size = Size(w - 12f, w - 12f), style = Stroke(9f, cap = StrokeCap.Round))
                }
            }
            Box(Modifier.size(172.dp).clip(CircleShape)
                .background(if (on) Brush.radialGradient(listOf(Color(0xFF7DE7F7), Wr.ac, Wr.ac2)) else Brush.radialGradient(listOf(Color(0xFF4A5380), Color(0xFF232A4A))))
                .clickable(enabled = !sibuk && s != null) { sahkan = true }
                .padding(if (on) 0.dp else 0.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IkonKuasa(if (on) Wr.on else Color(0xFFCFD5E8), 56)
                    Spacer(Modifier.height(6.dp))
                    Text(if (sibuk) "Sila tunggu" else if (on) "Putuskan" else "Sambung", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                        color = if (on) Wr.on else Color(0xFFCFD5E8))
                }
            }
        }

        val since = s?.optLong("since") ?: 0L
        val saat = if (on && since > 0) (kini + St.jamOfset - since).coerceAtLeast(0) else 0L
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(Wr.kad).border(1.dp, Wr.garis, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Wr.ac else Wr.mu))
                Spacer(Modifier.width(10.dp))
                Text("%02d:%02d:%02d".format(saat / 3600, saat % 3600 / 60, saat % 60), color = if (on) Wr.ac else Wr.mu,
                    fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("SESI", color = Wr.mu, fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }

        KadWr {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("MUAT TURUN", color = Wr.mu, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(if (on) fB(s?.optDouble("rx") ?: 0.0) + "/s" else "-", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
                }
                Column(Modifier.weight(1f)) {
                    Text("MUAT NAIK", color = Wr.mu, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(if (on) fB(s?.optDouble("tx") ?: 0.0) + "/s" else "-", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
                }
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val l = s?.optJSONObject("lic")
            val st = l?.optString("state").orEmpty()
            val exp = l?.optString("exp").orEmpty()
            val lt = when {
                s == null -> "..."
                st == "active" && (exp == "0" || exp.isEmpty()) -> "Lesen kekal"
                st == "active" -> {
                    val d = try { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).parse(exp) } catch (_: Exception) { null }
                    val n = if (d != null) ((d.time - System.currentTimeMillis()) / 86400000L + 1).toInt() else -1
                    if (n == 1) Bhs.t("Lesen 1 hari") else if (n >= 0) Bhs.f("Lesen {0} hari", n) else "Lesen aktif"
                }
                st == "expired" -> "Lesen luput"
                st == "none" || st.isEmpty() -> "Tiada lesen"
                else -> Bhs.t("Lesen") + ": $st"
            }
            Cip(lt, if (st == "active") Wr.ok else Wr.bad)
            Cip(if (s?.optString("mode") == "vless") "VLESS Pro" else "VPN asal", Wr.ac2)
            val nama = s?.optString("node").orEmpty()
            if (nama.isNotEmpty()) Cip(nama, Wr.ac)
            if (on && St.vpnMod.isNotEmpty()) Cip(Bhs.f("Mod {0}", St.vpnMod), Wr.wn)
        }

        KadUjian()
    }

    if (sahkan) {
        AlertDialog(onDismissRequest = { sahkan = false }, containerColor = Wr.kad,
            title = { Text(if (on) "Putuskan sambungan?" else "Sambung VLESS Pro?", color = Wr.tx) },
            text = {
                val hx = s?.optBoolean("hasx", true) ?: true
                Text(if (on) (if (hx) "VLESS Pro diputuskan dan VPN asal dipulihkan. Internet terhenti seketika." else "VLESS Pro diputuskan. Sambungan kembali tanpa proksi.")
                else (if (hx) "VPN asal dihentikan sementara. Internet terhenti seketika." else "Internet terhenti seketika."), color = Wr.mu)
            },
            confirmButton = { TextButton(onClick = { sahkan = false; pindahMod() }) { Text(if (on) "Putuskan" else "Sambung", color = if (on) Wr.bad else Wr.ac) } },
            dismissButton = { TextButton(onClick = { sahkan = false }) { Text("Batal", color = Wr.mu) } })
    }
}

@Composable
fun Cip(t: String, w: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Wr.kad).border(1.dp, Wr.garis, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(w))
        Spacer(Modifier.width(7.dp))
        Text(t, color = Wr.tx, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ───────────────────────── Statistik ─────────────────────────

@Composable
fun Statistik() {
    var live by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var ov by remember { mutableStateOf<org.json.JSONObject?>(null) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            while (!St.depan) delay(1000)
            withContext(Dispatchers.IO) {
                try {
                    val g = Modem.graphs()
                    val a = g.optJSONArray("live") ?: g.optJSONArray("speed")
                    if (a != null) live = (0 until a.length()).map { val p = a.optJSONArray(it); (p?.optDouble(0) ?: 0.0) to (p?.optDouble(1) ?: 0.0) }
                    if (n % 5 == 0) ov = Modem.overview()
                } catch (_: Exception) {}
            }
            n++
            delay(3000)
        }
    }
    val s = St.status
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Statistik", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
        KadWr {
            Text("Kelajuan (3 minit terakhir)", color = Wr.mu, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            val mx = (live.maxOfOrNull { maxOf(it.first, it.second) } ?: 0.0).coerceAtLeast(20 * 1024.0)
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val w = size.width; val h = size.height
                fun garis(sel: (Pair<Double, Double>) -> Double, c: Color) {
                    if (live.size < 2) return
                    val p = Path()
                    live.forEachIndexed { i, v ->
                        val x = w * i / (live.size - 1)
                        val y = h - (sel(v) / mx * (h - 8f)).toFloat() - 4f
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, c, style = Stroke(5f, cap = StrokeCap.Round))
                }
                drawLine(Wr.garis, Offset(0f, h - 1f), Offset(w, h - 1f), 2f)
                garis({ it.first }, Wr.ac)
                garis({ it.second }, Wr.ac2)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● Muat turun", color = Wr.ac, fontSize = 12.sp)
                Text("● Muat naik", color = Wr.ac2, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(Bhs.t("puncak") + " " + fB(mx) + "/s", color = Wr.mu, fontSize = 12.sp)
            }
        }
        KadWr {
            Baris("Muat turun semasa", fB(s?.optDouble("rx") ?: 0.0) + "/s")
            Baris("Muat naik semasa", fB(s?.optDouble("tx") ?: 0.0) + "/s")
            ov?.let {
                Baris("IP WAN", it.optString("wanip").ifEmpty { "-" })
                Baris("Get laluan", it.optString("gw").ifEmpty { "-" })
                val tot = it.optDouble("memtot"); val fr = it.optDouble("memfree")
                if (tot > 0) Baris("Memori bebas", fB(fr * 1024) + " / " + fB(tot * 1024))
                Baris("Sambungan gagal (1 jam)", it.optInt("drops1h").toString())
            }
        }
    }
}

@Composable
fun Baris(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(k, color = Wr.mu, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(v, color = Wr.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ───────────────────────── Lagi ─────────────────────────

@Composable
fun Lagi(buka: (String) -> Unit) {
    val item = listOf(
        Triple("lesen", "Lesen", "Aktifkan dan semak"),
        Triple("plus", "Feature Plus", "SQM, iklan, kuota, peranti"),
        Triple("laluan", "Penghalaan", "Peraturan laluan"),
        Triple("kumpulan", "Kumpulan", "Gabung beberapa profil"),
        Triple("langganan", "Langganan", "Import pautan langganan"),
        Triple("tetapan", "Tetapan", "DNS, mod, kata laluan"),
        Triple("kemas", "Kemas kini modem", "GitHub atau fail"),
        Triple("log", "Log", "Rekod sambungan"),
        Triple("sistem", "Sistem", "Maklumat modem"),
        Triple("statistik", "Statistik", "Trafik dan penggunaan"),
        Triple("janji", "Perjanjian", "Syarat dan resit persetujuan"),
        Triple("pasang", "Pasang / urus", "Pemasang SSH"),
        Triple("apl", "Kemas kini apl", "Versi apl ini"),
        Triple("tema", "Tema", "Rupa dan jenis tulisan"),
        Triple("bhs", "Bahasa", "Bahasa paparan apl"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lagi", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
        item.chunked(2).forEach { baris ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                baris.forEach { (id, t, d) ->
                    Box(Modifier.weight(1f)) { KadWr(klik = { buka(id) }) {
                        Text(t, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(d, color = Wr.mu, fontSize = 12.sp)
                    } }
                }
                if (baris.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        KadWr(klik = { buka("keluar") }) { Text("Tukar modem / kata laluan", color = Wr.bad, fontWeight = FontWeight.Bold) }
    }
}

// Halaman dalam Feature Plus kembali ke senarai Feature Plus; yang lain kembali ke Lagi.
fun indukSub(id: String?): String? = if (id in setOf("sqm", "adblock", "dev", "kuota", "wan", "failover")) "plus" else null

@Composable
fun Sub(id: String, pref: android.content.SharedPreferences, keluar: () -> Unit, ke: (String) -> Unit, tutup: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = tutup) { Text("‹ Kembali", color = Wr.ac, fontSize = 16.sp) }
        }
        Box(Modifier.weight(1f)) {
            when (id) {
                "lesen" -> SubLesen()
                "kemas" -> SubKemas()
                "log" -> SubLog()
                "sistem" -> SubSistem()
                "apl" -> SubApl()
                "plus" -> SubPlus(ke)
                "tema" -> SubTema()
                "bhs" -> SubBahasa()
                "sqm" -> SubSqm()
                "vpn" -> SubVpn()
                "statistik" -> Statistik()
                "janji" -> SubJanji()
                "adblock" -> SubAdblock()
                "dev" -> SubPeranti()
                "kuota" -> SubKuota()
                "wan" -> SubWan()
                "failover" -> SubFailover()
                "laluan" -> SubLaluan()
                "kumpulan" -> SubKumpulan()
                "langganan" -> SubLangganan()
                "tetapan" -> SubTetapan()
                "pasang" -> {
                    var edisi by remember { mutableStateOf(if (Modem.ip == "192.168.1.1") Edisi.AW1000 else Edisi.B618) }
                    var ip by remember { mutableStateOf(Modem.ip.ifEmpty { edisi.ipLalai }) }
                    Pemasangan(edisi, ip, { e -> edisi = e; ip = e.ipLalai }, { ip = it }, { tutup() })
                }
                "keluar" -> {
                    LaunchedEffect(Unit) { pref.edit().remove("pass").apply(); keluar() }
                }
            }
        }
    }
}

@Composable
fun Halaman(isi: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = isi)
}

@Composable
fun SubLesen() {
    var l by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var kunci by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { try { l = Modem.lic() } catch (_: Exception) {} } }
    Halaman {
        Text("Lesen", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
        KadWr {
            val st = l?.optString("state").orEmpty()
            Baris("Keadaan", when (st) { "active" -> "Aktif"; "expired" -> "Luput"; "none", "" -> "Belum ada"; else -> st })
            l?.optString("name")?.takeIf { it.isNotEmpty() }?.let { Baris("Nama", it) }
            l?.optString("exp")?.takeIf { it.isNotEmpty() }?.let { Baris("Luput", if (it == "0") "Seumur hidup" else it) }
            val id = l?.optString("id").orEmpty()
            Baris("ID Peranti", id.ifEmpty { "-" })
            if (id.isNotEmpty()) OutlinedButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ID", id)); St.toast("ID disalin")
            }) { Text("Salin ID Peranti", color = Wr.ac) }
        }
        KadWr {
            Text("Aktifkan lesen", color = Wr.tx, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(kunci, { kunci = it }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(), label = { Text("Tampal kunci lesen") }, colors = medan())
            Spacer(Modifier.height(10.dp))
            Button(enabled = kunci.isNotBlank(), onClick = {
                val k = kunci
                bg {
                    val j = Modem.aktifLesen(k)
                    l = j
                    St.toast(if (j.optString("state") == "active") "Lesen diaktifkan" else Bhs.t("Lesen ditolak") + " (${j.optString("state")})")
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Wr.ac, contentColor = Wr.on)) { Text("Aktifkan") }
        }
    }
}

@Composable
fun SubLog() {
    var t by remember { mutableStateOf("Memuatkan ...") }
    fun muat() = bg { t = Modem.log().ifEmpty { "(kosong)" } }
    LaunchedEffect(Unit) { muat() }
    Halaman {
        Text("Log", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { muat() }) { Text("Muat semula", color = Wr.ac) }
            OutlinedButton(onClick = { bg { Modem.kosongLog(); t = "(kosong)"; St.toast("Log dikosongkan") } }) { Text("Kosongkan", color = Wr.bad) }
        }
        KadWr { Text(t, color = Wr.mu, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
    }
}

@Composable
fun SubSistem() {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var sah by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { try { s = Modem.system() } catch (_: Exception) {} } }
    Halaman {
        Text("Sistem", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
        KadWr {
            s?.let {
                Baris("Edisi", it.optString("edisi"))
                Baris("Seni bina", it.optString("arch"))
                val up = it.optString("uptime").toDoubleOrNull()?.toLong() ?: 0L
                Baris("Hidup sejak", Bhs.f("{0} hari {1} jam", up / 86400, up % 86400 / 3600))
                Baris("Beban", it.optString("load").split(" ").take(3).joinToString(" "))
                Baris("Memori bebas", fB(it.optDouble("memfree") * 1024) + " / " + fB(it.optDouble("memtot") * 1024))
                Baris("Simpanan bebas", fB(it.optDouble("diskfree")) + " / " + fB(it.optDouble("disktot")))
                Baris("Xray", if (it.optBoolean("xray")) "Berjalan" else "Berhenti")
            } ?: Text("Memuatkan ...", color = Wr.mu)
        }
        OutlinedButton(onClick = {
            St.toast("Mengumpul maklumat ...")
            Thread {
                val t = try { Diagnosis.kumpul(ctx) } catch (e: Exception) { "Diagnosis gagal: " + (e.message ?: e.javaClass.simpleName) }
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Diagnosis", t))
                St.toast("Diagnosis disalin dan sedia dihantar")
                val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, t)
                ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }.start()
        }, modifier = Modifier.fillMaxWidth()) { Text("Hantar diagnosis", color = Wr.ac) }
        Text("Menyalin versi apl dan modem, status dan amaran log terakhir (tanpa kata laluan, kunci lesen atau laman yang dilayari).", color = Wr.mu, fontSize = 12.sp)
        OutlinedButton(onClick = { sah = "restart" }, modifier = Modifier.fillMaxWidth()) { Text("Mulakan semula VLESS Pro", color = Wr.wn) }
        OutlinedButton(onClick = { sah = "reboot" }, modifier = Modifier.fillMaxWidth()) { Text("But semula modem", color = Wr.bad) }
    }
    sah?.let { k ->
        AlertDialog(onDismissRequest = { sah = null }, containerColor = Wr.kad,
            title = { Text(if (k == "reboot") "But semula modem?" else "Mulakan semula VLESS Pro?", color = Wr.tx) },
            text = { Text("Internet terhenti seketika.", color = Wr.mu) },
            confirmButton = { TextButton(onClick = { sah = null; bg { val (_, m) = if (k == "reboot") Modem.reboot() else Modem.mulaSemula(); St.toast(m.ifEmpty { "Dihantar" }) } }) { Text("Teruskan", color = Wr.ac) } },
            dismissButton = { TextButton(onClick = { sah = null }) { Text("Batal", color = Wr.mu) } })
    }
}

@Composable
fun SubApl() {
    val ctx = LocalContext.current
    val pref = remember { ctx.getSharedPreferences("vp", Context.MODE_PRIVATE) }
    var info by remember { mutableStateOf<InfoApl?>(null) }
    var teks by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var semua by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(pref.getBoolean("autoapl", true)) }
    var cek by remember { mutableLongStateOf(pref.getLong("cekapl", 0L)) }
    val semasa = Kemas.versiSemasa(ctx)
    fun semak() = bg {
        teks = "Menyemak ..."
        try {
            val t = Kemas.terkini(); info = t
            cek = System.currentTimeMillis() / 1000; pref.edit().putLong("cekapl", cek).apply()
            St.aplBaru = if (t.code > semasa) t.name else ""
            teks = ""
        } catch (e: Exception) { teks = Bhs.t("Tidak dapat menyemak: ") + (e.message ?: "") }
    }
    LaunchedEffect(Unit) { semak() }
    val baru = info?.let { it.code > semasa } ?: false
    fun pasang() {
        val k = info ?: return
        sibuk = true
        bg {
            try { val m = Kemas.pasang(ctx, k) { teks = it }; teks = m ?: Bhs.t("Fail sedia. Pemasang Android dibuka.") } finally { sibuk = false }
        }
    }
    Halaman {
        Judul("Kemas kini apl")
        KadWr {
            Text("KEMAS KINI APL", color = Wr.mu, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Wr.kad2), contentAlignment = Alignment.Center) {
                    Text(if (baru) "↑" else "✓", color = if (baru) Wr.wn else Wr.ok, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("v" + (info?.name ?: Kemas.namaSemasa(ctx)), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
                    val k = info
                    Text(Bhs.t("Dipasang") + " v" + Kemas.namaSemasa(ctx) + (k?.date?.takeIf { it.isNotEmpty() }?.let { " · " + Bhs.t("dikeluarkan") + " $it" } ?: "") +
                        (if ((k?.size ?: 0L) > 0) " · %.1f MB".format(k!!.size / 1048576.0) else ""), color = Wr.mu, fontSize = 12.sp)
                }
            }
            info?.let { k ->
                if (k.title.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(k.title, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                k.items.forEach { Text("•  " + Bhs.t(it), color = Wr.tx, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp)) }
                if (k.history.length() > 0) {
                    Text(if (semua) "Sembunyikan" else "Tunjuk semua apa yang baharu", color = Wr.ac, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { semua = !semua }.padding(vertical = 10.dp))
                    if (semua) for (i in 0 until k.history.length()) {
                        val o = k.history.getJSONObject(i)
                        Text("v" + o.optString("version") + " · " + o.optString("date"), color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(o.optString("title"), color = Wr.tx, fontWeight = FontWeight.Bold)
                        val its = o.optJSONArray("items") ?: org.json.JSONArray()
                        for (j in 0 until its.length()) Text("•  " + Bhs.t(its.getString(j)), color = Wr.mu, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (info != null) Text(if (baru) "Versi apl baharu tersedia" else "Anda menggunakan versi apl terkini.", color = if (baru) Wr.wn else Wr.ok, fontWeight = FontWeight.SemiBold)
            if (teks.isNotEmpty() || sibuk) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (sibuk) CircularProgressIndicator(Modifier.size(18.dp), color = Wr.ac, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text(teks.ifEmpty { "Sila tunggu ..." }, color = Wr.ac, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (baru) Btn("Kemas kini apl sekarang", aktif = !sibuk) { pasang() }
            else if (info != null) OutlinedButton(onClick = { pasang() }, enabled = !sibuk, modifier = Modifier.fillMaxWidth().height(50.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac)) { Text("Pasang semula apl", color = Wr.tx, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            Btn("Semak sekarang", false, !sibuk) { semak() }
            val now = System.currentTimeMillis() / 1000
            Text(Bhs.t("Semakan terakhir") + ": " + when {
                cek == 0L -> Bhs.t("belum pernah")
                now - cek < 90 -> Bhs.t("baru sahaja")
                now - cek < 5400 -> Bhs.f("{0} minit lalu", (now - cek) / 60)
                now - cek < 172800 -> Bhs.f("{0} jam lalu", (now - cek) / 3600)
                else -> Bhs.f("{0} hari lalu", (now - cek) / 86400) }, color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        KadWr {
            Suis("Semak apabila apl dibuka", auto) { auto = it; pref.edit().putBoolean("autoapl", it).apply() }
            Text("Semak versi apl baharu setiap kali apl dibuka dan tunjuk jalur pemberitahuan di skrin Utama.", color = Wr.mu, fontSize = 12.sp)
        }
        KadWr {
            Text("Maklumat apl", color = Wr.tx, fontWeight = FontWeight.Bold)
            Baris("Nama versi", Kemas.namaSemasa(ctx))
            Baris("Kod binaan", semasa.toString())
            Baris("Pakej", ctx.packageName)
            Baris("Peranti telefon", android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE)
            if (Modem.ip.isNotEmpty()) Baris("Modem disambung", Modem.ip)
            Text("Sumber: GitHub (tuahwa1990-cpu/vlesspro-apl). Fail disemak SHA-256 sebelum dipasang.", color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
