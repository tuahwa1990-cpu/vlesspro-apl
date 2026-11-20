package com.vlesspro.pemasang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kegemaran dan tag profil modem. Disimpan dalam telefon, dikunci pada id profil (cincang pautan). */
object ProfilMeta {
    private fun p(c: Context) = c.getSharedPreferences("vp", Context.MODE_PRIVATE)
    fun fav(c: Context): Set<String> = HashSet(p(c).getStringSet("pro_fav", emptySet()) ?: emptySet())
    fun togolFav(c: Context, id: String) {
        val s = fav(c).toMutableSet()
        if (!s.add(id)) s.remove(id)
        p(c).edit().putStringSet("pro_fav", s).apply()
    }
    private fun tagObj(c: Context) = try { JSONObject(p(c).getString("pro_tag", "{}")) } catch (_: Exception) { JSONObject() }
    fun tag(c: Context, id: String): String = tagObj(c).optString(id)
    fun setTag(c: Context, id: String, t: String) {
        val o = tagObj(c)
        if (t.isBlank()) o.remove(id) else o.put(id, t.trim().take(16))
        p(c).edit().putString("pro_tag", o.toString()).apply()
    }
    fun semuaTag(c: Context): List<String> {
        val o = tagObj(c)
        return o.keys().asSequence().map { o.optString(it) }.filter { it.isNotEmpty() }.distinct().sorted().toList()
    }
    /** Bila pautan diedit id berubah: bawa kegemaran dan tag ke id baharu. */
    fun pindah(c: Context, lama: String, baru: String) {
        if (lama == baru) return
        if (lama in fav(c)) { togolFav(c, lama); if (baru !in fav(c)) togolFav(c, baru) }
        val t = tag(c, lama)
        if (t.isNotEmpty()) { setTag(c, baru, t); setTag(c, lama, "") }
    }
}

/**
 * Pembina pautan SSH mengikut format yang difahami MODEM (vless-pro-b618 proto.go parseSSH):
 * ssh://pengguna:kata@hos:port?mode=ws|tls|tlsws&sni=..&payload=..&proxy=hos:port#nama
 * Ini BERBEZA daripada format SSH apl VPN telefon (SshPautan dalam VpnSsh.kt), yang tiada medan "mode"/"proxy".
 */
object ModemSsh {
    private fun e(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun bina(nama: String, host: String, port: Int, pengguna: String, kata: String, mod: String, sni: String, payload: String, proksi: String): String {
        val q = buildList {
            add("mode=" + mod)
            if (sni.isNotBlank()) add("sni=" + e(sni))
            if (payload.isNotBlank()) add("payload=" + e(payload))
            if (proksi.isNotBlank()) add("proxy=" + e(proksi))
        }.joinToString("&")
        val n = nama.trim().ifEmpty { "SSH $host" }
        return "ssh://" + e(pengguna) + ":" + e(kata) + "@" + host + ":" + port + "?" + q + "#" + e(n)
    }
}

object ProfilAlat {
    /** Tukar nama paparan dalam pautan (vmess: medan ps; lain-lain: serpihan #nama). */
    fun namaSemula(link: String, nama: String): String {
        val l = link.trim()
        val n = nama.trim()
        if (l.startsWith("vmess://")) {
            try {
                var b = l.removePrefix("vmess://").substringBefore('#').replace('-', '+').replace('_', '/')
                while (b.length % 4 != 0) b += "="
                val o = JSONObject(String(Base64.decode(b, Base64.DEFAULT), Charsets.UTF_8))
                o.put("ps", n)
                return "vmess://" + Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP)
            } catch (_: Exception) { return l }
        }
        return l.substringBefore('#') + "#" + android.net.Uri.encode(n)
    }

    fun qr(teks: String, px: Int = 720): Bitmap? = try {
        val m = QRCodeWriter().encode(teks, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        for (x in 0 until px) for (y in 0 until px) bmp.setPixel(x, y, if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        bmp
    } catch (_: Exception) { null }

    fun waktu(t: Long): String = SimpleDateFormat("d/M HH:mm", Locale.getDefault()).format(Date(t * 1000))

    /** Data setiap profil daripada modem. Null jika modem belum menyokongnya. */
    fun guna(): JSONObject? {
        val (kod, s) = Modem.req("/api/nodeusage")
        if (kod != 200) return null
        return try { JSONObject(s) } catch (_: Exception) { null }
    }

    fun salinPapanKlip(ctx: Context, t: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("pautan", t))
    }
}

@Composable
fun Profil(ke: (String) -> Unit) {
    val ctx = LocalContext.current
    var tambah by remember { mutableStateOf(false) }
    var padam by remember { mutableStateOf<Nod?>(null) }
    var edit by remember { mutableStateOf<Pair<Nod, String>?>(null) }
    var nama by remember { mutableStateOf<Pair<Nod, String>?>(null) }
    var tagDlg by remember { mutableStateOf<Nod?>(null) }
    var qrDlg by remember { mutableStateOf<Pair<Nod, android.graphics.Bitmap?>?>(null) }
    var tapis by remember { mutableStateOf("") }
    var susun by remember { mutableStateOf(false) }
    var fo by remember { mutableStateOf<JSONObject?>(null) }
    var foOn by remember { mutableStateOf(false) }
    var kuota by remember { mutableStateOf<JSONObject?>(null) }
    var guna by remember { mutableStateOf<JSONObject?>(null) }
    var gunaAda by remember { mutableStateOf(true) }
    var meta by remember { mutableStateOf(0) }
    val fav = remember(meta) { ProfilMeta.fav(ctx) }

    fun muat() = bg {
        St.nod = Modem.nodes()
        fo = Modem.obj("/api/failover")
        kuota = Modem.obj("/api/quota")
        val g = ProfilAlat.guna()
        gunaAda = g != null
        guna = g
    }
    LaunchedEffect(Unit) { muat() }
    LaunchedEffect(fo) { foOn = fo?.optJSONObject("cfg")?.optBoolean("on") ?: false }

    val pilihFail = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) bg {
            val t = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(60000) }.orEmpty()
            if (t.isBlank()) St.toast(Bhs.t("Fail kosong.")) else { lapor(Modem.tambahNod(t)); muat() }
        }
    }

    fun bukaFail() = pilihFail.launch("text/*")

    fun ujiDanPilihTerpantas() = bg {
        St.toast(Bhs.t("Menguji semua profil ..."))
        val r = Modem.ping()
        St.ping = r.mapIndexed { i, v -> i to v }.toMap()
        val terbaik = r.withIndex().filter { it.value > 0 }.minByOrNull { it.value }
        if (terbaik == null) { St.toast(Bhs.t("Tiada profil yang boleh dihubungi.")); return@bg }
        val (ok, m) = Modem.pilihNod(terbaik.index)
        St.toast(if (ok) Bhs.f("Dipilih: {0} ({1} ms)", St.nod.getOrNull(terbaik.index)?.nama.orEmpty(), terbaik.value.toString()) else m)
        St.nod = Modem.nodes()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Profil", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)

            // Failover
            KadWr {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Failover automatik", color = Wr.tx, fontWeight = FontWeight.Bold)
                        Text("Jika profil semasa gagal, modem tukar sendiri ke profil terpantas yang hidup.", color = Wr.mu, fontSize = 12.sp)
                    }
                    Switch(foOn, { v ->
                        foOn = v
                        bg { lapor(Modem.tindak("/api/failover", JSONObject().put("on", v).toString())); fo = Modem.obj("/api/failover") }
                    }, colors = SwitchDefaults.colors(checkedTrackColor = Wr.ac, checkedThumbColor = Wr.on))
                }
                if (fo?.optBoolean("grp") == true) Text("Kumpulan sedang digunakan. Kumpulan memilih profil sendiri.", color = Wr.wn, fontSize = 12.sp)
                val log = fo?.optJSONArray("log")
                if (foOn && log != null && log.length() > 0) {
                    val o = log.getJSONObject(0)
                    Text(Bhs.f("Tukar terakhir {0}: {1} → {2}", ProfilAlat.waktu(o.optLong("t")), o.optString("from"), o.optString("to")), color = Wr.mu, fontSize = 12.sp)
                }
            }

            // Uji dan susun
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    St.toast(Bhs.t("Menguji semua profil ..."))
                    bg { val r = Modem.ping(); St.ping = r.mapIndexed { i, v -> i to v }.toMap(); St.toast(Bhs.t("Ujian siap")) }
                }, border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("Uji semua", color = Wr.ac) }
                OutlinedButton(onClick = { ujiDanPilihTerpantas() },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("Guna terpantas", color = Wr.ac) }
                Spacer(Modifier.weight(1f))
                Text(Bhs.f("{0} profil", St.nod.size), color = Wr.mu, fontSize = 13.sp)
            }
            Text("Ujian ini ping TCP ke pelayan: ia menunjukkan pelayan hidup dan anggaran jarak, bukan kelajuan sebenar.", color = Wr.mu, fontSize = 11.sp)

            // Penapis
            val tagAda = remember(meta) { ProfilMeta.semuaTag(ctx) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(tapis == "", { tapis = "" }, label = { Text("Semua", fontSize = 12.sp) })
                FilterChip(tapis == "*", { tapis = "*" }, label = { Text("⭐", fontSize = 12.sp) })
                tagAda.forEach { t -> FilterChip(tapis == t, { tapis = t }, label = { Text(t, fontSize = 12.sp) }) }
                FilterChip(susun, { susun = !susun }, label = { Text("Laju dahulu", fontSize = 12.sp) })
            }

            if (St.nod.isEmpty()) {
                KadWr { Text("Belum ada profil. Tekan + untuk menambah pautan vless://, vmess://, trojan://, ss:// atau WireGuard.", color = Wr.mu) }
            }
            var senarai = St.nod.filter { n ->
                when (tapis) { "" -> true; "*" -> n.id in fav; else -> ProfilMeta.tag(ctx, n.id) == tapis }
            }
            senarai = if (susun) senarai.sortedBy { n -> St.ping[n.i]?.takeIf { it > 0 } ?: Int.MAX_VALUE }
                      else senarai.sortedByDescending { it.id in fav }
            senarai.forEach { n ->
                val ms = St.ping[n.i]
                val tag = ProfilMeta.tag(ctx, n.id)
                val pakai = guna?.optJSONObject(n.id)
                KadWr(klik = {
                    if (!n.pilih) bg { val (ok, m) = Modem.pilihNod(n.i); St.toast(m.ifEmpty { if (ok) "Dipilih" else "Gagal" }); St.nod = Modem.nodes() }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (n.pilih) Wr.grad else Brush.linearGradient(listOf(Wr.garis, Wr.garis))),
                            contentAlignment = Alignment.Center) {
                            Text(if (n.pilih) "✓" else n.nama.take(1).uppercase(), color = if (n.pilih) Wr.on else Wr.tx, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if (n.id in fav) "⭐ " else "") + n.nama, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                            Text("${n.host}:${n.port} · ${n.net} · ${n.sec}", color = Wr.mu, fontSize = 12.sp, maxLines = 1)
                            if (tag.isNotEmpty() || pakai != null) {
                                val d = if (pakai != null) "↓ " + fB(pakai.optDouble("rx")) + "  ↑ " + fB(pakai.optDouble("tx")) else ""
                                Text(listOf(if (tag.isNotEmpty()) "#$tag" else "", d).filter { it.isNotEmpty() }.joinToString("  ·  "), color = Wr.ac2, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        if (ms != null) {
                            val t = if (ms > 0) "✅ $ms ms" else if (ms == -2) "UDP" else "❌ " + Bhs.t("Tamat")
                            val w = if (ms in 1..250) Wr.ok else if (ms > 250) Wr.wn else if (ms == -2) Wr.mu else Wr.bad
                            Text(t, color = w, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Box {
                            var m by remember { mutableStateOf(false) }
                            Text("⋮", color = Wr.mu, fontSize = 22.sp, modifier = Modifier.clickable { m = true }.padding(horizontal = 10.dp, vertical = 4.dp))
                            DropdownMenu(m, { m = false }) {
                                DropdownMenuItem({ Text(if (n.id in fav) "Buang kegemaran" else "⭐ Kegemaran") }, { m = false; ProfilMeta.togolFav(ctx, n.id); meta++ })
                                DropdownMenuItem({ Text("Tag") }, { m = false; tagDlg = n })
                                DropdownMenuItem({ Text("Namakan semula") }, { m = false; nama = n to n.nama })
                                DropdownMenuItem({ Text("Kod QR") }, { m = false; bg {
                                    val l = Modem.obj("/api/node/link?i=${n.i}").optString("link")
                                    val b = ProfilAlat.qr(l)
                                    if (b == null) { St.toast(Bhs.t("Pautan terlalu panjang untuk kod QR. Guna Salin pautan.")) } else { qrDlg = n to b }
                                } })
                                DropdownMenuItem({ Text("Salin pautan") }, { m = false; bg {
                                    ProfilAlat.salinPapanKlip(ctx, Modem.obj("/api/node/link?i=${n.i}").optString("link")); St.toast(Bhs.t("Pautan disalin")) } })
                                DropdownMenuItem({ Text("Hantar ke VPN telefon") }, { m = false; bg {
                                    val l = Modem.obj("/api/node/link?i=${n.i}").optString("link")
                                    St.toast(VpnImport.proses(ctx, l)) } })
                                DropdownMenuItem({ Text("Edit pautan") }, { m = false; bg { edit = n to Modem.obj("/api/node/link?i=${n.i}").optString("link") } })
                                DropdownMenuItem({ Text("Padam", color = Wr.bad) }, { m = false; padam = n })
                            }
                        }
                    }
                }
            }

            // Data
            KadWr {
                Text("Data", color = Wr.tx, fontWeight = FontWeight.Bold)
                val k = kuota
                if (k != null && k.has("rx")) {
                    val rx = k.optDouble("rx"); val tx = k.optDouble("tx")
                    val had = k.optJSONObject("cfg")?.takeIf { it.optBoolean("on") }?.optDouble("limit") ?: 0.0
                    Text(Bhs.f("Tempoh ini: {0} (↓ {1}  ↑ {2})", fB(rx + tx), fB(rx), fB(tx)) + if (had > 0) Bhs.f(" · had {0} GB", "%.0f".format(had)) else "", color = Wr.mu, fontSize = 13.sp)
                } else Text("Data tidak tersedia.", color = Wr.mu, fontSize = 13.sp)
                if (!gunaAda) Text("Data setiap profil memerlukan kemas kini modem.", color = Wr.mu, fontSize = 12.sp)
                else Text("Data setiap profil dikira daripada trafik modem semasa profil itu dipilih.", color = Wr.mu, fontSize = 12.sp)
                Row {
                    TextButton(onClick = { ke("kuota") }) { Text("Tetapan kuota", color = Wr.ac) }
                    if (gunaAda) TextButton(onClick = { bg { lapor(Modem.tindak("/api/nodeusage/reset")); muat() } }) { Text("Set semula data profil", color = Wr.mu) }
                }
            }

            // Pengurusan
            KadWr {
                Text("Urus profil", color = Wr.tx, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ke("langganan") }, border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac)) { Text("Langganan", color = Wr.ac) }
                    OutlinedButton(onClick = { ke("kumpulan") }, border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac)) { Text("Kumpulan", color = Wr.ac) }
                    OutlinedButton(onClick = { bg {
                        val links = St.nod.map { Modem.obj("/api/node/link?i=${it.i}").optString("link") }.filter { it.isNotEmpty() }
                        if (links.isEmpty()) { St.toast(Bhs.t("Tiada profil untuk dieksport.")); return@bg }
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, links.joinToString("\n")); putExtra(Intent.EXTRA_SUBJECT, "VLESS Pro profil") }
                        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } }, border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac)) { Text("Eksport", color = Wr.ac) }
                    OutlinedButton(onClick = { bukaFail() }, border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac)) { Text("Import fail", color = Wr.ac) }
                }
                Text("Eksport mengandungi kunci profil anda. Jangan kongsi kepada orang lain.", color = Wr.mu, fontSize = 11.sp)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 112.dp).size(58.dp).clip(RoundedCornerShape(20.dp)).background(Wr.grad)
            .clickable { tambah = true }, contentAlignment = Alignment.Center) {
            Text("+", fontSize = 32.sp, color = Wr.on, fontWeight = FontWeight.Bold)
        }
    }

    if (tambah) {
        var tab by remember { mutableIntStateOf(0) }
        var teks by remember { mutableStateOf("") }
        var tajukWg by remember { mutableStateOf("") }
        var nama by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("22") }
        var pengguna by remember { mutableStateOf("") }
        var kata by remember { mutableStateOf("") }
        var mod by remember { mutableStateOf("ws") }
        var sni by remember { mutableStateOf("") }
        var payload by remember { mutableStateOf("") }
        var proksi by remember { mutableStateOf("") }
        var ralat by remember { mutableStateOf("") }

        fun pasteKe(set: (String) -> Unit) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            set(cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty())
        }

        AlertDialog(onDismissRequest = { tambah = false }, containerColor = Wr.kad,
            title = { Text("Tambah profil", color = Wr.tx) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(tab == 0, { tab = 0; ralat = "" }, label = { Text("VLESS / Lain", fontSize = 12.sp) })
                        FilterChip(tab == 1, { tab = 1; ralat = "" }, label = { Text("SSH", fontSize = 12.sp) })
                        FilterChip(tab == 2, { tab = 2; ralat = "" }, label = { Text("WireGuard", fontSize = 12.sp) })
                    }
                    Spacer(Modifier.height(10.dp))
                    when (tab) {
                        0 -> {
                            Text("Tampal pautan vless://, vmess://, trojan:// atau ss:// (satu baris setiap profil).", color = Wr.mu, fontSize = 12.sp)
                            OutlinedTextField(teks, { teks = it }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                label = { Text("Tampal pautan di sini") }, colors = medan())
                            TextButton(onClick = { pasteKe { teks = it } }) { Text("Tampal dari papan klip", color = Wr.ac) }
                        }
                        1 -> {
                            Text("Isi butiran pelayan SSH. Jika menggunakan IP \"bug\" (bukan IP pelayan sebenar), isi dalam ruangan Proksi.", color = Wr.mu, fontSize = 12.sp)
                            OutlinedTextField(nama, { nama = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Nama (pilihan)") }, colors = medan())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(host, { host = it }, singleLine = true, modifier = Modifier.weight(2f), label = { Text("Host / IP") }, colors = medan())
                                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Port") }, colors = medan())
                            }
                            OutlinedTextField(pengguna, { pengguna = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Nama pengguna") }, colors = medan())
                            OutlinedTextField(kata, { kata = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Kata laluan") }, colors = medan())
                            Text("Mod sambungan", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("ws" to "WS (payload)", "tls" to "TLS/SSL", "tlsws" to "TLS + payload").forEach { (k, t) ->
                                    FilterChip(mod == k, { mod = k }, label = { Text(t, fontSize = 11.sp) })
                                }
                            }
                            if (mod == "tls" || mod == "tlsws") OutlinedTextField(sni, { sni = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                label = { Text("SNI (kosong = guna host)") }, colors = medan())
                            if (mod == "ws" || mod == "tlsws") {
                                OutlinedTextField(payload, { payload = it }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    label = { Text("Payload (kosong = guna lalai)") }, colors = medan())
                                TextButton(onClick = { payload = SshPautan.PAYLOAD_WS }) { Text("Isi payload lalai", color = Wr.ac) }
                            }
                            OutlinedTextField(proksi, { proksi = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                label = { Text("Proksi / IP bug (pilihan, format host:port)") }, colors = medan())
                            Text("Dibiarkan kosong jika tiada guna IP bug. Sambungan terus ke Host/IP di atas.", color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            if (ralat.isNotEmpty()) Text(ralat, color = Wr.bad, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        else -> {
                            Text("Tampal kandungan fail .conf WireGuard, atau pautan wireguard://.", color = Wr.mu, fontSize = 12.sp)
                            OutlinedTextField(tajukWg, { tajukWg = it }, minLines = 6, maxLines = 10, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                label = { Text("Tampal di sini") }, colors = medan())
                            TextButton(onClick = { pasteKe { tajukWg = it } }) { Text("Tampal dari papan klip", color = Wr.ac) }
                        }
                    }
                }
            },
            confirmButton = {
                val aktif = when (tab) { 0 -> teks.isNotBlank(); 1 -> host.isNotBlank() && pengguna.isNotBlank(); else -> tajukWg.isNotBlank() }
                TextButton(enabled = aktif, onClick = {
                    when (tab) {
                        0 -> { val t = teks; tambah = false; bg { lapor(Modem.tambahNod(t)); muat() } }
                        1 -> {
                            val p = port.toIntOrNull()
                            if (p == null || p !in 1..65535) { ralat = Bhs.t("Port tidak sah."); return@TextButton }
                            if (proksi.isNotBlank() && !proksi.contains(':')) { ralat = Bhs.t("Format proksi mesti host:port."); return@TextButton }
                            val link = ModemSsh.bina(nama, host.trim(), p, pengguna.trim(), kata, mod, sni.trim(), payload, proksi.trim())
                            tambah = false
                            bg { lapor(Modem.tambahNod(link)); muat() }
                        }
                        else -> { val t = tajukWg; tambah = false; bg { lapor(Modem.tambahNod(t)); muat() } }
                    }
                }) { Text("Tambah", color = Wr.ac) }
            },
            dismissButton = { TextButton(onClick = { tambah = false }) { Text("Batal", color = Wr.mu) } })
    }
    edit?.let { (nd, ln) ->
        var t by remember { mutableStateOf(ln) }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = Wr.kad, title = { Text("Edit ${nd.nama}", color = Wr.tx) },
            text = { OutlinedTextField(t, { t = it }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(), colors = medan()) },
            confirmButton = { TextButton(onClick = {
                edit = null
                bg {
                    val (ok, m) = Modem.tindak("/api/node/edit", JSONObject().put("i", nd.i).put("link", t.trim()).toString())
                    if (ok) ProfilMeta.pindah(ctx, nd.id, idPautan(t.trim()))
                    St.toast(m.ifEmpty { if (ok) "Disimpan" else "Gagal" }); muat(); meta++
                }
            }) { Text("Simpan", color = Wr.ac) } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Batal", color = Wr.mu) } })
    }
    nama?.let { (nd, n0) ->
        var t by remember { mutableStateOf(n0) }
        AlertDialog(onDismissRequest = { nama = null }, containerColor = Wr.kad, title = { Text("Namakan semula", color = Wr.tx) },
            text = { OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan()) },
            confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = {
                nama = null
                bg {
                    val lama = Modem.obj("/api/node/link?i=${nd.i}").optString("link")
                    val baru = ProfilAlat.namaSemula(lama, t)
                    if (baru == lama) { St.toast(Bhs.t("Nama tidak dapat ditukar untuk jenis pautan ini. Guna Edit pautan.")); return@bg }
                    val (ok, m) = Modem.tindak("/api/node/edit", JSONObject().put("i", nd.i).put("link", baru).toString())
                    if (ok) ProfilMeta.pindah(ctx, nd.id, idPautan(baru))
                    St.toast(m.ifEmpty { if (ok) "Disimpan" else "Gagal" }); muat(); meta++
                }
            }) { Text("Simpan", color = Wr.ac) } },
            dismissButton = { TextButton(onClick = { nama = null }) { Text("Batal", color = Wr.mu) } })
    }
    tagDlg?.let { nd ->
        var t by remember { mutableStateOf(ProfilMeta.tag(ctx, nd.id)) }
        AlertDialog(onDismissRequest = { tagDlg = null }, containerColor = Wr.kad, title = { Text("Tag profil", color = Wr.tx) },
            text = {
                Column {
                    OutlinedTextField(t, { t = it.take(16) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan(), label = { Text("Contoh: Game, Streaming") })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (ProfilMeta.semuaTag(ctx) + listOf("Game", "Streaming", "Harian")).distinct().forEach { s -> FilterChip(t == s, { t = s }, label = { Text(s, fontSize = 12.sp) }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ProfilMeta.setTag(ctx, nd.id, t); tagDlg = null; meta++ }) { Text("Simpan", color = Wr.ac) } },
            dismissButton = { TextButton(onClick = { tagDlg = null }) { Text("Batal", color = Wr.mu) } })
    }
    qrDlg?.let { (nd, bmp) ->
        AlertDialog(onDismissRequest = { qrDlg = null }, containerColor = Wr.kad, title = { Text(nd.nama, color = Wr.tx, maxLines = 1) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(260.dp).background(androidx.compose.ui.graphics.Color.White))
                    Text("Imbas dengan apl VPN telefon (Imbas QR) atau apl lain. Jangan tunjukkan kepada orang lain.", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { qrDlg = null }) { Text("Tutup", color = Wr.ac) } })
    }
    padam?.let { n ->
        AlertDialog(onDismissRequest = { padam = null }, containerColor = Wr.kad,
            title = { Text("Padam profil?", color = Wr.tx) }, text = { Text(n.nama, color = Wr.mu) },
            confirmButton = {
                TextButton(onClick = {
                    padam = null
                    bg { lapor(Modem.padamNod(n.i)); muat() }
                }) { Text("Padam", color = Wr.bad) }
            },
            dismissButton = { TextButton(onClick = { padam = null }) { Text("Batal", color = Wr.mu) } })
    }
}
