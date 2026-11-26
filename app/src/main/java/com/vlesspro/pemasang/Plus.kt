package com.vlesspro.pemasang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun Judul(t: String) = Text(t, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)

@Composable
fun Suis(t: String, on: Boolean, ubah: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = Wr.tx, modifier = Modifier.weight(1f))
        Switch(on, ubah, colors = SwitchDefaults.colors(checkedTrackColor = Wr.ac, checkedThumbColor = Wr.on))
    }
}

@Composable
fun Btn(t: String, utama: Boolean = true, aktif: Boolean = true, klik: () -> Unit) {
    Button(onClick = klik, enabled = aktif, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = if (utama) Wr.ac else Wr.kad2, contentColor = if (utama) Wr.on else Wr.ac)) { Text(t, fontWeight = FontWeight.Bold) }
}

fun lapor(r: Pair<Boolean, String>) = St.toast(r.second.ifEmpty { if (r.first) "Siap" else "Gagal" })

private fun JSONObject.post(path: String, o: JSONObject) = Modem.tindak(path, o.toString())

@Composable
fun <T> muat(sel: () -> T): MutableState<T?> {
    val st = remember { mutableStateOf<T?>(null) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { try { st.value = sel() } catch (_: Exception) {} } }
    return st
}

// ───────────── Feature Plus hub ─────────────
@Composable
fun SubPlus(ke: (String) -> Unit) {
    val it = listOf(
        Triple("sqm", "SQM", "Had dan kawal lag"),
        Triple("adblock", "Sekat iklan", "Senarai dan statistik"),
        Triple("dev", "Peranti", "Siapa guna WiFi"),
        Triple("kuota", "Kuota", "Had data bulanan"),
        Triple("wan", "Tukar IP WAN", "Dapatkan IP baharu"),
        Triple("failover", "Failover", "Tukar nod automatik"),
        Triple("bandlock", "Kunci Band", "Kunci band 5G/4G/3G"),
        Triple("notify", "Notifikasi Telegram", "Amaran kuota dan WAN"),
        Triple("bandsched", "Jadual Kunci Band", "Tukar band automatik"),
        Triple("signal", "Isyarat & Sel", "RSRP/RSRQ/SINR masa nyata"),
        Triple("ttl", "Mod TTL/NSS", "Elak pengesanan hotspot"),
        Triple("simtukar", "Tukar SIM", "Slot SIM 1 dan SIM 2"),
    )
    Halaman {
        Judul("Feature Plus")
        it.chunked(2).forEach { b ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                b.forEach { (id, t, d) -> Box(Modifier.weight(1f)) { KadWr(klik = { ke(id) }) {
                    Text(t, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(d, color = Wr.mu, fontSize = 12.sp) } } }
                if (b.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SubSqm() {
    val d = muat { Modem.obj("/api/sqm") }
    var on by remember { mutableStateOf(false) }
    var up by remember { mutableStateOf("") }
    fun papar(x: Double) = if (x <= 0.0) "" else if (x == Math.floor(x)) x.toInt().toString() else x.toString()
    LaunchedEffect(d.value) { d.value?.optJSONObject("cfg")?.let { on = it.optBoolean("on"); up = papar(it.optDouble("up")) } }
    Halaman {
        Judul("SQM")
        val v = d.value
        if (v != null && v.optBoolean("unsupported")) KadWr { Text("Ciri SQM belum disokong pada modem B618.", color = Wr.wn) }
        else KadWr {
            Text("Hadkan kelajuan muat naik supaya lag kekal rendah semasa muat naik.", color = Wr.mu, fontSize = 13.sp)
            Suis("Hidupkan SQM", on) { on = it }
            OutlinedTextField(up, { up = it }, label = { Text("Had muat naik (Mbit/s)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 20, 50, 100).forEach { n ->
                    FilterChip(up.toDoubleOrNull() == n.toDouble(), { up = n.toString() }, label = { Text("$n Mbit/s", fontSize = 12.sp) })
                }
            }
            Text("Tip: tetapkan kira-kira 80 hingga 90% kelajuan muat naik sebenar anda (uji dengan Speedtest). Had yang lebih tinggi daripada kelajuan sebenar tidak memberi kesan.", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Text("Had muat turun tidak disokong pada modem ini kerana pecutan perkakasan.", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Btn("Simpan") {
                val nilai = up.toDoubleOrNull() ?: 0.0
                if (on && nilai <= 0.0) St.toast("Tetapkan had muat naik dahulu (lebih daripada 0).")
                else bg { lapor(Modem.tindak("/api/sqm", JSONObject().put("on", on).put("down", 0.0).put("up", nilai).toString())) }
            }
            if (v != null) {
                val cfgUp = v.optJSONObject("cfg")?.optDouble("up") ?: 0.0
                Text(if (v.optBoolean("active")) Bhs.f("SQM aktif. Had muat naik {0} Mbit/s", papar(cfgUp)) else Bhs.t("SQM tidak aktif"),
                    color = if (v.optBoolean("active")) Wr.ok else Wr.mu, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Text(Bhs.t("Antara muka") + ": ${v.optString("iface")}", color = Wr.mu, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SubAdblock() {
    val d = muat { Modem.obj("/api/adblock") }
    var on by remember { mutableStateOf(false) }
    var sen by remember { mutableStateOf(setOf<String>()) }
    var hitam by remember { mutableStateOf("") }
    var putih by remember { mutableStateOf("") }
    LaunchedEffect(d.value) {
        d.value?.optJSONObject("cfg")?.let { c ->
            on = c.optBoolean("on")
            sen = (c.optJSONArray("lists") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            hitam = (c.optJSONArray("black") ?: JSONArray()).let { a -> (0 until a.length()).joinToString("\n") { a.getString(it) } }
            putih = (c.optJSONArray("white") ?: JSONArray()).let { a -> (0 until a.length()).joinToString("\n") { a.getString(it) } }
        }
    }
    fun senarai(t: String) = JSONArray(t.split(Regex("[\\s,]+")).filter { it.isNotBlank() })
    Halaman {
        Judul("Sekat iklan")
        val v = d.value
        KadWr {
            Suis("Hidupkan sekat iklan", on) { on = it }
            val lists = v?.optJSONArray("lists")
            if (lists != null) for (i in 0 until lists.length()) {
                val l = lists.getJSONObject(i)
                val id = l.optString("id")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(id in sen, { c -> sen = if (c) sen + id else sen - id }, colors = CheckboxDefaults.colors(checkedColor = Wr.ac))
                    Column { Text(l.optString("name"), color = Wr.tx, fontSize = 14.sp); Text(Bhs.f("{0} domain", l.optInt("n")), color = Wr.mu, fontSize = 11.sp) }
                }
            }
            OutlinedTextField(hitam, { hitam = it }, label = { Text("Senarai hitam tambahan") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = medan())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(putih, { putih = it }, label = { Text("Senarai putih (dikecualikan)") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = medan())
            Spacer(Modifier.height(10.dp))
            Btn("Simpan") { bg { lapor(Modem.tindak("/api/adblock", JSONObject().put("on", on).put("lists", JSONArray(sen.toList())).put("black", senarai(hitam)).put("white", senarai(putih)).toString())) } }
            Spacer(Modifier.height(8.dp))
            Btn("Kemas kini senarai", false) { bg { lapor(Modem.tindak("/api/adblock/update")) } }
        }
        v?.let {
            KadWr {
                Baris("Domain disekat dalam senarai", it.optInt("total").toString())
                val t = it.optJSONObject("stats")?.optJSONObject("today")
                if (t != null) { Baris("Soalan hari ini", t.optInt("q").toString()); Baris("Disekat hari ini", t.optInt("b").toString()) }
                val top = it.optJSONObject("stats")?.optJSONArray("top")
                if (top != null && top.length() > 0) {
                    Spacer(Modifier.height(6.dp)); Text("Paling banyak disekat", color = Wr.mu, fontSize = 12.sp)
                    for (i in 0 until minOf(top.length(), 8)) { val o = top.getJSONObject(i); Baris(o.optString("d"), o.optInt("n").toString()) }
                }
            }
        }
    }
}

@Composable
fun SubPeranti() {
    var senarai by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var ubah by remember { mutableStateOf<JSONObject?>(null) }
    fun muatSemula() = bg { val a = Modem.obj("/api/devices").optJSONArray("devs") ?: JSONArray(); senarai = (0 until a.length()).map { a.getJSONObject(it) } }
    LaunchedEffect(Unit) { muatSemula() }
    Halaman {
        Judul("Peranti")
        Text(Bhs.f("{0} peranti disambung", senarai.size), color = Wr.mu)
        senarai.forEach { o ->
            KadWr(klik = { ubah = o }) {
                Text(o.optString("name").ifEmpty { o.optString("host").ifEmpty { "(tanpa nama)" } }, color = Wr.tx, fontWeight = FontWeight.Bold)
                Text("${o.optString("ip")} · ${o.optString("mac")}", color = Wr.mu, fontSize = 12.sp)
            }
        }
    }
    ubah?.let { o ->
        var n by remember { mutableStateOf(o.optString("name")) }
        AlertDialog(onDismissRequest = { ubah = null }, containerColor = Wr.kad, title = { Text("Namakan peranti", color = Wr.tx) },
            text = { OutlinedTextField(n, { n = it }, singleLine = true, label = { Text(o.optString("mac")) }, colors = medan()) },
            confirmButton = { TextButton(onClick = { ubah = null; bg { lapor(Modem.tindak("/api/devices/name", JSONObject().put("mac", o.optString("mac")).put("name", n).toString())); muatSemula() } }) { Text("Simpan", color = Wr.ac) } },
            dismissButton = { TextButton(onClick = { ubah = null }) { Text("Batal", color = Wr.mu) } })
    }
}

@Composable
fun SubKuota() {
    val d = muat { Modem.obj("/api/quota") }
    var on by remember { mutableStateOf(false) }
    var had by remember { mutableStateOf("") }
    var tempoh by remember { mutableStateOf("month") }
    var rday by remember { mutableStateOf("1") }
    var potong by remember { mutableStateOf(false) }
    LaunchedEffect(d.value) { d.value?.optJSONObject("cfg")?.let { on = it.optBoolean("on"); had = it.optDouble("limit").toString(); tempoh = it.optString("period").ifEmpty { "month" }; rday = it.optInt("rday", 1).toString(); potong = it.optBoolean("cut") } }
    Halaman {
        Judul("Kuota")
        d.value?.let { v ->
            KadWr {
                val used = v.optDouble("used"); val lim = v.optJSONObject("cfg")?.optDouble("limit") ?: 0.0
                Baris("Digunakan", "%.2f GB".format(used) + if (lim > 0) " / %.0f GB".format(lim) else "")
                if (lim > 0) LinearProgressIndicator(progress = { (used / lim).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Wr.ac, trackColor = Wr.garis)
                Baris("Keadaan", when (v.optString("state")) { "full" -> "Habis"; "warn" -> "Hampir habis"; "ok" -> "Baik"; else -> "Mati" })
            }
        }
        KadWr {
            Suis("Hidupkan kuota", on) { on = it }
            OutlinedTextField(had, { had = it }, label = { Text("Had (GB)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tempoh == "month", { tempoh = "month" }, label = { Text("Bulanan") })
                FilterChip(tempoh == "day", { tempoh = "day" }, label = { Text("Harian") })
            }
            if (tempoh == "month") OutlinedTextField(rday, { rday = it }, label = { Text("Hari tetapan semula (1-28)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Suis("Putuskan internet bila habis", potong) { potong = it }
            Btn("Simpan") { bg { lapor(Modem.tindak("/api/quota", JSONObject().put("on", on).put("limit", had.toDoubleOrNull() ?: 0.0).put("period", tempoh).put("rday", rday.toIntOrNull() ?: 1).put("cut", potong).toString())) } }
            Spacer(Modifier.height(8.dp))
            Btn("Tetapkan semula pengiraan", false) { bg { lapor(Modem.tindak("/api/quota/reset")) } }
        }
    }
}

@Composable
fun SubWan() {
    var s by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { while (true) { while (!St.depan) delay(1000); withContext(Dispatchers.IO) { try { s = Modem.obj("/api/wanip") } catch (_: Exception) {} }; delay(2500) } }
    Halaman {
        Judul("Tukar IP WAN")
        KadWr {
            val v = s
            if (v != null && v.optBoolean("unsupported")) Text("Belum disokong pada modem B618.", color = Wr.wn)
            else {
                Baris("Keadaan", when (v?.optString("state")) { "running" -> "Sedang berjalan"; "done" -> "Siap"; "fail" -> "Gagal"; else -> "Sedia" })
                v?.optString("step")?.takeIf { it.isNotEmpty() }?.let { Text(it, color = Wr.mu, fontSize = 12.sp) }
                v?.optString("before")?.takeIf { it.isNotEmpty() }?.let { Baris("IP sebelum", it) }
                v?.optString("after")?.takeIf { it.isNotEmpty() }?.let { Baris("IP selepas", it) }
                v?.optString("msg")?.takeIf { it.isNotEmpty() }?.let { Text(it, color = Wr.mu, fontSize = 12.sp) }
                Spacer(Modifier.height(10.dp))
                Btn("Tukar IP sekarang", aktif = v?.optString("state") != "running") { bg { lapor(Modem.tindak("/api/wanip/run")) } }
                Text("Internet terhenti seketika semasa IP ditukar.", color = Wr.mu, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SubFailover() {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var senarai by remember { mutableStateOf<List<Nod>>(emptyList()) }
    var ping by remember { mutableStateOf<List<Int>>(emptyList()) }
    var on by remember { mutableStateOf(false) }
    var segarKunci by remember { mutableStateOf(0) }
    var segar by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    LaunchedEffect(segarKunci) {
        while (true) {
            while (!St.depan) delay(1000)
            segar = true
            withContext(Dispatchers.IO) {
                try {
                    val dd = Modem.obj("/api/failover")
                    d = dd
                    on = dd.optJSONObject("cfg")?.optBoolean("on") ?: false
                    senarai = Modem.nodes()
                    ping = Modem.ping()
                } catch (_: Exception) {}
            }
            segar = false
            delay(8000)
        }
    }

    Halaman {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Judul("Failover")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { segarKunci++ }) { Text("⟳", fontSize = 22.sp, color = Wr.ac) }
        }
        val v = d
        val grp = v?.optBoolean("grp") ?: false
        val autoAktif = v?.optBoolean("auto") ?: false
        val fails = (v?.optInt("fails") ?: 0).coerceIn(0, 3)
        KadWr {
            Text("Jika nod semasa gagal, tukar ke nod lain secara automatik.", color = Wr.mu, fontSize = 13.sp)
            Suis("Hidupkan failover", on) { on = it }
            Btn("Simpan") { bg { lapor(Modem.tindak("/api/failover", JSONObject().put("on", on).toString())) } }
            Spacer(Modifier.height(6.dp))
            when {
                !on -> Text("Failover dimatikan.", color = Wr.mu, fontSize = 12.sp)
                grp -> Text("Tidak aktif sekarang: profil KUMPULAN sedang digunakan. Pilih satu nod individu (bukan kumpulan) untuk mengaktifkan failover.", color = Wr.wn, fontSize = 12.sp)
                !autoAktif -> Text("Tidak akan bertindak sekarang: hidupkan Sambung semula automatik dalam Tetapan, dan pastikan VLESS Pro sedang bersambung.", color = Wr.wn, fontSize = 12.sp)
                else -> Text("Aktif dan sedang memantau sambungan.", color = Wr.ok, fontSize = 12.sp)
            }
            if (on && !grp && autoAktif) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ujian gagal berturut" + ": $fails/3", color = if (fails > 0) Wr.wn else Wr.mu, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Row {
                        repeat(3) { i -> Box(Modifier.padding(start = 3.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (i < fails) Wr.wn else Wr.garis)) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            v?.let { Baris("Nod semasa", it.optString("cur").ifEmpty { "-" }); Baris("Jumlah nod", it.optInt("n").toString()) }
        }
        if (senarai.isNotEmpty()) KadWr {
            Text("Status setiap nod", color = Wr.tx, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            senarai.forEachIndexed { i, nd ->
                val p = ping.getOrElse(i) { -1 }
                val (warna, teks) = when {
                    nd.pilih -> Wr.ac to "Digunakan sekarang"
                    p == -2 -> Wr.mu to "Tidak diuji"
                    p < 0 -> Wr.bad to "Tidak boleh dicapai"
                    p < 300 -> Wr.ok to "$p ms"
                    else -> Wr.wn to "$p ms"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(warna))
                    Spacer(Modifier.width(8.dp))
                    Text(nd.nama, color = Wr.tx, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(teks, color = warna, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (segar) Text("Menyegarkan...", color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        val log = v?.optJSONArray("log")
        if (log != null && log.length() > 0) KadWr {
            Text("Rekod terkini", color = Wr.mu, fontSize = 12.sp)
            for (i in 0 until minOf(log.length(), 8)) {
                val o = log.getJSONObject(i)
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(ProfilAlat.waktu(o.optLong("t")) + "  " + o.optString("from") + " → " + o.optString("to"), color = Wr.tx, fontSize = 12.sp)
                    val sebab = o.optString("why")
                    if (sebab.isNotEmpty()) Text(sebab, color = Wr.mu, fontSize = 11.sp)
                }
            }
        }
        KadWr {
            Text("Bagaimana failover berfungsi", color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            listOf(
                "Hanya memantau semasa VLESS Pro bersambung dan sedang digunakan.",
                "Perlu beberapa ujian gagal berturut-turut dahulu sebelum bertukar, supaya tidak tukar nod akibat gangguan sekejap sahaja.",
                "Tidak aktif jika profil KUMPULAN sedang dipilih; pilih satu nod untuk mengaktifkan.",
                "Hanya bertukar ke nod lain yang disahkan boleh dicapai."
            ).forEach { Text("•  " + it, color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
        KadWr {
            OutlinedButton(onClick = {
                val dSnap = v; val nodSnap = senarai; val pingSnap = ping
                St.toast("Mengumpul maklumat ...")
                Thread {
                    val t = try { Diagnosis.kumpulFailover(ctx, dSnap, nodSnap, pingSnap) } catch (e: Exception) { "Diagnosis gagal: " + (e.message ?: e.javaClass.simpleName) }
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Diagnosis", t))
                    St.toast("Diagnosis disalin dan sedia dihantar")
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, t)
                    ctx.startActivity(android.content.Intent.createChooser(i, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }.start()
            }, modifier = Modifier.fillMaxWidth()) { Text("Hantar diagnosis failover", color = Wr.ac) }
            Text("Menyalin status failover, kelayakan setiap nod dan rekod penuh tukar nod, bersama maklumat am apl dan modem.", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ───────────── Kunci Band ─────────────
private data class GrupBand(val rat: String, val judul: String, val cur: String, val sup: String)
private val grupBand = listOf(
    GrupBand("5", "Band 5G NR", "current_nr_band", "support_nr_band"),
    GrupBand("4", "Band 4G LTE", "current_lte_band", "support_lte_band"),
    GrupBand("3", "Band 3G UMTS", "current_umts_band", "support_umts_band"),
)
private fun bandLabel(rat: String, b: Int) = (if (rat == "5") "n" else "B") + b
private fun parseBand(s: String?): List<Int> {
    if (s.isNullOrEmpty() || s == "0") return emptyList()
    return s.split("/").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }.filter { it != 0 }
}

// ───────────── Gauge isyarat (dikongsi SubBandlock & SubSignal) ─────────────
private fun sigQualCls(key: String, v: String): String {
    val n = v.toDoubleOrNull() ?: return ""
    return when (key) {
        "RSRP" -> if (n >= -90) "ok" else if (n >= -105) "wn" else "bad"
        "RSRQ" -> if (n >= -10) "ok" else if (n >= -15) "wn" else "bad"
        "SINR", "SNR" -> if (n >= 13) "ok" else if (n >= 0) "wn" else "bad"
        "RSSI" -> if (n >= -75) "ok" else if (n >= -95) "wn" else "bad"
        else -> ""
    }
}
private fun sigColorCls(cls: String): Color = when (cls) { "ok" -> Wr.ok; "wn" -> Wr.wn; "bad" -> Wr.bad; else -> Wr.ac2 }
private fun sigPctOf(key: String, v: String): Float? {
    val n = v.toDoubleOrNull() ?: return null
    val r = when (key) {
        "RSRP" -> -130.0 to -60.0
        "RSRQ" -> -20.0 to -3.0
        "SINR", "SNR" -> -10.0 to 30.0
        "RSSI" -> -110.0 to -50.0
        else -> return null
    }
    return ((n - r.first) / (r.second - r.first)).coerceIn(0.0, 1.0).toFloat()
}

@Composable
private fun SigGauge(label: String, value: String, unit: String, pct: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                drawArc(color = Color.White.copy(alpha = 0.12f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawArc(color = color, startAngle = -90f, sweepAngle = 360f * pct, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (unit.isNotEmpty()) Text(unit, color = Wr.mu, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Wr.mu, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
fun SubBandlock() {
    var q by remember { mutableStateOf<JSONObject?>(null) }
    var cek by remember { mutableStateOf(0) }
    val pilihan = remember { mutableStateMapOf<String, Set<Int>>() }
    var sig by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(cek) {
        withContext(Dispatchers.IO) { try { q = Modem.obj("/api/bandlock") } catch (_: Exception) {} }
    }
    LaunchedEffect(Unit) {
        while (true) { try { sig = withContext(Dispatchers.IO) { Modem.obj("/api/signal") } } catch (_: Exception) {}; delay(5000) }
    }
    Halaman {
        Judul("Kunci Band")
        val sv = sig
        if (sv != null && sv.optBoolean("supported")) {
            val groups = sv.optJSONArray("groups") ?: JSONArray()
            if (groups.length() > 0) {
                KadWr {
                    Text("Isyarat Semasa", color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    for (gi in 0 until groups.length()) {
                        val g = groups.getJSONArray(gi)
                        if (groups.length() > 1) {
                            Spacer(Modifier.height(if (gi == 0) 4.dp else 10.dp))
                            Text("Sel " + (gi + 1), color = Wr.mu, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        val gaugeItems = mutableListOf<@Composable () -> Unit>()
                        for (j in 0 until g.length()) {
                            val it = g.getJSONObject(j)
                            val value = it.optString("value")
                            if (value.isEmpty()) continue
                            val key = it.optString("key")
                            val unit = it.optString("unit")
                            val label = it.optString("full_name").ifEmpty { key }
                            val pct = sigPctOf(key, value)
                            if (pct != null) {
                                val color = sigColorCls(sigQualCls(key, value))
                                gaugeItems.add { SigGauge(label, value, unit, pct, color) }
                            } else {
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, color = Wr.mu, fontSize = 13.sp)
                                    Text(value + (unit.let { u -> if (u.isNotEmpty()) " $u" else "" }), color = Wr.mu, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        if (gaugeItems.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                gaugeItems.forEach { it() }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        val v = q
        when {
            v == null -> KadWr { Text("Memuatkan...", color = Wr.mu) }
            !v.optBoolean("supported") -> KadWr { Text("Ciri ini belum disokong pada modem ini.", color = Wr.wn) }
            v.optString("err").isNotEmpty() -> KadWr { Text(v.optString("err"), color = Wr.bad) }
            else -> {
                val data = try { JSONObject(v.optString("raw").ifEmpty { "{}" }) } catch (_: Exception) { JSONObject() }
                val stored = v.optString("stored")
                val storedMap = remember(stored) {
                    stored.split(";").filter { it.isNotBlank() }.mapNotNull {
                        val kv = it.split(",")
                        if (kv.size == 2) kv[0] to kv[1].split("/").mapNotNull { b -> b.toIntOrNull() }.toSet() else null
                    }.toMap()
                }
                grupBand.forEach { g ->
                    if (!pilihan.containsKey(g.rat)) {
                        val cur = parseBand(data.optString(g.cur))
                        pilihan[g.rat] = storedMap[g.rat] ?: cur.toSet()
                    }
                }
                KadWr {
                    Text("Pilih sekurang-kurangnya satu band bagi setiap jenis yang disokong, atau tekan Buka Kunci untuk kembali automatik.", color = Wr.mu, fontSize = 12.sp)
                    grupBand.forEach { g ->
                        val sup = parseBand(data.optString(g.sup))
                        val cur = parseBand(data.optString(g.cur))
                        Spacer(Modifier.height(10.dp))
                        Text(g.judul, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (sup.isEmpty()) {
                            Text("Tiada band disokong untuk jenis ini.", color = Wr.mu, fontSize = 12.sp)
                        } else {
                            sup.chunked(4).forEach { baris ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    baris.forEach { b ->
                                        val dipilih = pilihan[g.rat]?.contains(b) == true
                                        Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                            .background(if (dipilih) Wr.ac.copy(alpha = 0.22f) else Wr.kad2)
                                            .border(1.dp, if (dipilih) Wr.ac else Wr.garis, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                            .clickable {
                                                val s = pilihan[g.rat].orEmpty().toMutableSet()
                                                if (s.contains(b)) s.remove(b) else s.add(b)
                                                pilihan[g.rat] = s
                                            }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text(bandLabel(g.rat, b), color = Wr.tx, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                        if (cur.isNotEmpty()) Text("Semasa:" + " " + cur.joinToString(", ") { bandLabel(g.rat, it) }, color = Wr.mu, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Btn("Terapkan Kunci Band") {
                        val arg = grupBand.mapNotNull { g ->
                            val s = pilihan[g.rat].orEmpty()
                            if (s.isEmpty()) null else g.rat + "," + s.sorted().joinToString("/")
                        }.joinToString(";")
                        if (arg.isEmpty()) { St.toast(Bhs.t("Pilih sekurang-kurangnya satu band")); return@Btn }
                        bg { lapor(Modem.tindak("/api/bandlock/set", JSONObject().put("arg", arg).toString())); cek++ }
                    }
                    Spacer(Modifier.height(8.dp))
                    Btn("Buka Kunci (Automatik)", utama = false) {
                        bg { lapor(Modem.tindak("/api/bandlock/set", JSONObject().put("arg", "").toString())); cek++ }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Radio modem akan dimulakan semula (~6 saat) selepas kunci band ditukar. Internet terputus seketika.", color = Wr.mu, fontSize = 12.sp)
                }
            }
        }
    }
}

// ───────────── Notifikasi Telegram ─────────────
@Composable
fun SubNotify() {
    val d = muat { Modem.obj("/api/notify") }
    var on by remember { mutableStateOf(false) }
    var tok by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf("") }
    var qw by remember { mutableStateOf(false) }
    var qf by remember { mutableStateOf(false) }
    var wd by remember { mutableStateOf(false) }
    var wu by remember { mutableStateOf(false) }
    var sibuk by remember { mutableStateOf(false) }
    LaunchedEffect(d.value) { d.value?.optJSONObject("cfg")?.let {
        on = it.optBoolean("on"); tok = it.optString("bot_token"); chat = it.optString("chat_id")
        qw = it.optBoolean("quota_warn"); qf = it.optBoolean("quota_full"); wd = it.optBoolean("wan_down"); wu = it.optBoolean("wan_up")
    } }
    fun cfg() = JSONObject().put("on", on).put("bot_token", tok.trim()).put("chat_id", chat.trim())
        .put("quota_warn", qw).put("quota_full", qf).put("wan_down", wd).put("wan_up", wu)
    Halaman {
        Judul("Notifikasi Telegram")
        KadWr {
            Text("Cipta bot melalui @BotFather di Telegram untuk dapatkan token, dan hantar /start kepada bot itu untuk dapatkan Chat ID anda (cth melalui bot @userinfobot).", color = Wr.mu, fontSize = 13.sp)
            Suis("Hidupkan notifikasi", on) { on = it }
            OutlinedTextField(tok, { tok = it }, label = { Text("Token Bot") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(chat, { chat = it }, label = { Text("Chat ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan())
            Spacer(Modifier.height(8.dp))
            Suis("Kuota hampir habis", qw) { qw = it }
            Suis("Kuota habis", qf) { qf = it }
            Suis("WAN terputus", wd) { wd = it }
            Suis("WAN pulih", wu) { wu = it }
            Spacer(Modifier.height(8.dp))
            Btn("Simpan", aktif = !sibuk) { sibuk = true; bg { lapor(Modem.tindak("/api/notify/set", cfg().toString())); sibuk = false } }
            Spacer(Modifier.height(8.dp))
            Btn("Hantar Mesej Ujian", utama = false, aktif = !sibuk) { sibuk = true; bg {
                Modem.tindak("/api/notify/set", cfg().toString()); lapor(Modem.tindak("/api/notify/test")); sibuk = false } }
        }
    }
}

// ───────────── Jadual Kunci Band ─────────────
@Composable
fun SubBandSched() {
    val bl = muat { Modem.obj("/api/bandlock") }
    var sc by remember { mutableStateOf<JSONObject?>(null) }
    var cek by remember { mutableStateOf(0) }
    LaunchedEffect(cek) { withContext(Dispatchers.IO) { try { sc = Modem.obj("/api/bandsched") } catch (_: Exception) {} } }
    val pilihan = remember { mutableStateMapOf<String, Set<Int>>() }
    var masa by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    Halaman {
        Judul("Jadual Kunci Band")
        val v = bl.value
        if (v == null || !v.optBoolean("supported")) KadWr { Text("Ciri kunci band tidak disokong pada modem ini.", color = Wr.wn) }
        else {
            val data = try { JSONObject(v.optString("raw").ifEmpty { "{}" }) } catch (_: Exception) { JSONObject() }
            KadWr {
                Text("Pilih band, masa (HH:MM), kemudian tekan Tambah. Kosongkan pilihan band untuk jadual buka kunci (automatik).", color = Wr.mu, fontSize = 12.sp)
                grupBand.forEach { g ->
                    val sup = parseBand(data.optString(g.sup))
                    if (sup.isEmpty()) return@forEach
                    Spacer(Modifier.height(8.dp))
                    Text(g.judul, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    sup.chunked(4).forEach { baris ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            baris.forEach { b ->
                                val dipilih = pilihan[g.rat]?.contains(b) == true
                                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(if (dipilih) Wr.ac.copy(alpha = 0.22f) else Wr.kad2)
                                    .border(1.dp, if (dipilih) Wr.ac else Wr.garis, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val s = pilihan[g.rat].orEmpty().toMutableSet()
                                        if (s.contains(b)) s.remove(b) else s.add(b)
                                        pilihan[g.rat] = s
                                    }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(bandLabel(g.rat, b), color = Wr.tx, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(masa, { masa = it }, label = { Text("Masa (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nota, { nota = it }, label = { Text("Nota (pilihan)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = medan())
                Spacer(Modifier.height(8.dp))
                Btn("+ Tambah Jadual", utama = false) {
                    if (!Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(masa.trim())) { St.toast("Format masa mesti HH:MM"); return@Btn }
                    val arg = grupBand.mapNotNull { g -> val s = pilihan[g.rat].orEmpty(); if (s.isEmpty()) null else g.rat + "," + s.sorted().joinToString("/") }.joinToString(";")
                    val list = (sc?.optJSONArray("list") ?: JSONArray())
                    val baru = JSONArray(); for (i in 0 until list.length()) baru.put(list.get(i))
                    baru.put(JSONObject().put("time", masa.trim()).put("arg", arg).put("note", nota.trim()))
                    bg { lapor(Modem.tindak("/api/bandsched/set", JSONObject().put("list", baru).toString())); cek++ }
                    masa = ""; nota = ""
                }
            }
            val list = sc?.optJSONArray("list") ?: JSONArray()
            if (list.length() == 0) KadWr { Text("Tiada jadual lagi.", color = Wr.mu) }
            for (i in 0 until list.length()) {
                val e = list.getJSONObject(i)
                val lbl = e.optString("arg").ifEmpty { null }?.split(";")?.joinToString(" · ") { p ->
                    val kv = p.split(","); kv.getOrNull(1)?.split("/")?.joinToString(",") { b -> bandLabel(kv[0], b.toIntOrNull() ?: 0) } ?: p
                } ?: "Buka Kunci (Automatik)"
                KadWr {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.optString("time") + " — " + lbl, color = Wr.tx, fontWeight = FontWeight.Bold)
                            if (e.optString("note").isNotEmpty()) Text(e.optString("note"), color = Wr.mu, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            val baru = JSONArray(); for (j in 0 until list.length()) if (j != i) baru.put(list.get(j))
                            bg { lapor(Modem.tindak("/api/bandsched/set", JSONObject().put("list", baru).toString())); cek++ }
                        }) { Text("✕", color = Wr.bad) }
                    }
                }
            }
        }
    }
}

// ───────────── Isyarat & Sel ─────────────
@Composable
fun SubSignal() {
    var q by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        while (true) { try { q = withContext(Dispatchers.IO) { Modem.obj("/api/signal") } } catch (_: Exception) {}; delay(5000) }
    }
    Halaman {
        Judul("Isyarat & Sel")
        val v = q
        if (v == null) KadWr { Text("Memuatkan...", color = Wr.mu) }
        else if (!v.optBoolean("supported")) KadWr { Text("Ciri ini belum disokong pada modem ini.", color = Wr.wn) }
        else if (v.optString("err").isNotEmpty()) KadWr { Text(v.optString("err"), color = Wr.bad) }
        else {
            val groups = v.optJSONArray("groups") ?: JSONArray()
            if (groups.length() == 0) KadWr { Text("Tiada data sel buat masa ini.", color = Wr.mu) }
            for (i in 0 until groups.length()) {
                val g = groups.getJSONArray(i)
                KadWr {
                    Text("Sel " + (i + 1), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    val gaugeItems = mutableListOf<@Composable () -> Unit>()
                    for (j in 0 until g.length()) {
                        val it = g.getJSONObject(j)
                        val value = it.optString("value")
                        if (value.isEmpty()) continue
                        val key = it.optString("key")
                        val unit = it.optString("unit")
                        val label = it.optString("full_name").ifEmpty { key }
                        val pct = sigPctOf(key, value)
                        if (pct != null) {
                            val color = sigColorCls(sigQualCls(key, value))
                            gaugeItems.add { SigGauge(label, value, unit, pct, color) }
                        } else {
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, color = Wr.mu, fontSize = 13.sp)
                                Text(value + (unit.let { u -> if (u.isNotEmpty()) " $u" else "" }), color = Wr.mu, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    if (gaugeItems.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            gaugeItems.forEach { it() }
                        }
                    }
                }
            }
        }
    }
}

// ───────────── Mod TTL/NSS ─────────────
@Composable
fun SubTtl() {
    var q by remember { mutableStateOf<JSONObject?>(null) }
    var cek by remember { mutableStateOf(0) }
    var sibuk by remember { mutableStateOf(false) }
    LaunchedEffect(cek) { withContext(Dispatchers.IO) { try { q = Modem.obj("/api/ttl") } catch (_: Exception) {} } }
    Halaman {
        Judul("Mod TTL/NSS")
        val v = q
        if (v == null) KadWr { Text("Memuatkan...", color = Wr.mu) }
        else if (!v.optBoolean("supported")) KadWr { Text("Ciri ini belum disokong pada modem ini.", color = Wr.wn) }
        else {
            val mod = v.optString("mode")
            KadWr {
                Text("Mod NSS: pecutan perkakasan, lalai, paling laju. Mod VPN/TTL: elak pengesanan perkongsian data (hotspot) oleh telco, mungkin sedikit lebih perlahan.", color = Wr.mu, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("Mod semasa: " + when (mod) { "nss" -> "NSS (lalai)"; "vpn" -> "VPN/TTL"; else -> "Tidak diketahui" }, color = Wr.tx, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Btn("Guna Mod NSS (lalai)", aktif = !sibuk) { sibuk = true; bg { lapor(Modem.tindak("/api/ttl/set", "nss")); sibuk = false; cek++ } }
                Spacer(Modifier.height(8.dp))
                Btn("Guna Mod VPN/TTL", utama = false, aktif = !sibuk) { sibuk = true; bg { lapor(Modem.tindak("/api/ttl/set", "vpn")); sibuk = false; cek++ } }
                Spacer(Modifier.height(10.dp))
                Text("Rangkaian akan terputus seketika (~10 saat) selepas mod ditukar.", color = Wr.mu, fontSize = 12.sp)
            }
        }
    }
}

// ───────────── Tukar SIM ─────────────
@Composable
fun SubSim() {
    var q by remember { mutableStateOf<JSONObject?>(null) }
    var cek by remember { mutableStateOf(0) }
    var sibuk by remember { mutableStateOf(false) }
    LaunchedEffect(cek) { withContext(Dispatchers.IO) { try { q = Modem.obj("/api/sim") } catch (_: Exception) {} } }
    Halaman {
        Judul("Tukar SIM")
        val v = q
        if (v == null) KadWr { Text("Memuatkan...", color = Wr.mu) }
        else if (!v.optBoolean("supported")) KadWr { Text("Ciri ini belum disokong pada modem ini.", color = Wr.wn) }
        else if (v.optString("err").isNotEmpty()) KadWr { Text(v.optString("err"), color = Wr.bad) }
        else {
            val cur = v.optString("current")
            val slots = v.optJSONArray("slots") ?: JSONArray()
            KadWr {
                Text("SIM semasa: SIM $cur. Menukar SIM mungkin memutuskan internet seketika sehingga modem mendaftar semula ke rangkaian.", color = Wr.mu, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                for (i in 0 until slots.length()) {
                    val s = slots.getString(i)
                    val sama = s == cur
                    Btn(if (sama) "SIM $s (sedang digunakan)" else "Tukar ke SIM $s", utama = !sama, aktif = !sama && !sibuk) {
                        sibuk = true; bg { lapor(Modem.tindak("/api/sim/set", s)); sibuk = false; cek++ }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ───────────── Penghalaan ─────────────
@Composable
fun SubLaluan() {
    val d = muat { Modem.obj("/api/routes") }
    var ds by remember { mutableStateOf("AsIs") }
    var peraturan by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var jenis by remember { mutableStateOf("domain") }
    var nilai by remember { mutableStateOf("") }
    var tindakan by remember { mutableStateOf("direct") }
    var langsung by remember { mutableStateOf("") }
    LaunchedEffect(d.value) {
        d.value?.let { v ->
            ds = v.optString("ds").ifEmpty { "AsIs" }
            val a = v.optJSONArray("rules") ?: JSONArray()
            peraturan = (0 until a.length()).map { val o = a.getJSONObject(it); Triple(o.optString("t"), o.optString("v"), o.optString("a")) }
        }
        withContext(Dispatchers.IO) { try { langsung = Modem.obj("/api/direct").optString("text") } catch (_: Exception) {} }
    }
    Halaman {
        Judul("Penghalaan")
        KadWr {
            Text("Strategi domain", color = Wr.mu, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { FilterChip(ds == it, { ds = it }, label = { Text(it, fontSize = 11.sp) }) }
            }
        }
        KadWr {
            Text(Bhs.t("Peraturan") + " (${peraturan.size})", color = Wr.tx, fontWeight = FontWeight.Bold)
            peraturan.forEachIndexed { i, (t, v, a) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$t: $v", color = Wr.tx, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(when (a) { "direct" -> "Terus"; "proxy" -> "Proksi"; else -> "Sekat" }, color = if (a == "block") Wr.bad else if (a == "proxy") Wr.ac else Wr.ok, fontSize = 12.sp)
                }
                TextButton(onClick = { peraturan = peraturan.filterIndexed { j, _ -> j != i } }, contentPadding = PaddingValues(0.dp)) { Text("Buang peraturan ini", color = Wr.mu, fontSize = 11.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("domain", "keyword", "ip", "geosite", "geoip").forEach { FilterChip(jenis == it, { jenis = it }, label = { Text(it, fontSize = 11.sp) }) } }
            OutlinedTextField(nilai, { nilai = it.trim() }, singleLine = true, label = { Text("Nilai (cth: google.com, cn, 10.0.0.0/8)") }, modifier = Modifier.fillMaxWidth(), colors = medan())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("direct" to "Terus", "proxy" to "Proksi", "block" to "Sekat").forEach { (k, t) -> FilterChip(tindakan == k, { tindakan = k }, label = { Text(t) }) } }
            Spacer(Modifier.height(6.dp))
            Btn("Tambah peraturan", false, nilai.isNotEmpty()) { peraturan = peraturan + Triple(jenis, nilai, tindakan); nilai = "" }
            Spacer(Modifier.height(8.dp))
            Btn("Simpan penghalaan") {
                val rs = JSONArray(); peraturan.forEach { (t, v, a) -> rs.put(JSONObject().put("t", t).put("v", v).put("a", a)) }
                bg { lapor(Modem.tindak("/api/routes", JSONObject().put("ds", ds).put("rules", rs).toString())) }
            }
        }
        KadWr {
            Text("Domain/IP sambung terus (pintasan)", color = Wr.tx, fontWeight = FontWeight.Bold)
            OutlinedTextField(langsung, { langsung = it }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = medan())
            Spacer(Modifier.height(8.dp))
            Btn("Simpan senarai terus", false) { bg { lapor(Modem.tindak("/api/direct", langsung)) } }
        }
    }
}

// ───────────── Kumpulan & Langganan ─────────────
@Composable
fun SubKumpulan() {
    var gs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var baru by remember { mutableStateOf(false) }
    fun m() = bg { val a = Modem.arr("/api/groups"); gs = (0 until a.length()).map { a.getJSONObject(it) }; St.nod = Modem.nodes() }
    LaunchedEffect(Unit) { m() }
    Halaman {
        Judul("Kumpulan")
        Text("Gabungkan beberapa profil. Pilih satu kumpulan untuk menukar secara automatik.", color = Wr.mu, fontSize = 13.sp)
        gs.forEach { g ->
            KadWr {
                Text((if (g.optBoolean("sel")) "✓ " else "") + g.optString("name"), color = if (g.optBoolean("sel")) Wr.ac else Wr.tx, fontWeight = FontWeight.Bold)
                val a = g.optJSONArray("members") ?: JSONArray()
                Text((0 until a.length()).joinToString(", ") { a.getString(it) } + " · " + g.optString("strat"), color = Wr.mu, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { bg { lapor(Modem.tindak("/api/group/use", g.optInt("i").toString())); m() } }) { Text("Guna", color = Wr.ac) }
                    TextButton(onClick = { bg { lapor(Modem.tindak("/api/group/del", g.optInt("i").toString())); m() } }) { Text("Padam", color = Wr.bad) }
                }
            }
        }
        Btn("Cipta kumpulan") { baru = true }
    }
    if (baru) {
        var nama by remember { mutableStateOf("") }
        var strat by remember { mutableStateOf("leastPing") }
        var pilih by remember { mutableStateOf(setOf<String>()) }
        AlertDialog(onDismissRequest = { baru = false }, containerColor = Wr.kad, title = { Text("Kumpulan baharu", color = Wr.tx) },
            text = {
                Column {
                    OutlinedTextField(nama, { nama = it }, singleLine = true, label = { Text("Nama") }, colors = medan())
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("leastPing" to "Ping", "random" to "Rawak", "roundRobin" to "Bergilir").forEach { (k, t) -> FilterChip(strat == k, { strat = k }, label = { Text(t, fontSize = 11.sp) }) } }
                    St.nod.forEach { n -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(n.nama in pilih, { c -> pilih = if (c) pilih + n.nama else pilih - n.nama }); Text(n.nama, color = Wr.tx, fontSize = 13.sp) } }
                }
            },
            confirmButton = {
                TextButton(enabled = nama.isNotBlank() && pilih.size >= 2, onClick = {
                    baru = false
                    bg {
                        val ids = St.nod.filter { it.nama in pilih }.map { Modem.obj("/api/node/link?i=${it.i}").optString("link") }.filter { it.isNotEmpty() }
                        lapor(Modem.tindak("/api/group/add", JSONObject().put("name", nama).put("strat", strat).put("members", JSONArray(ids.map { idPautan(it) })).toString())); m()
                    }
                }) { Text("Cipta", color = Wr.ac) }
            },
            dismissButton = { TextButton(onClick = { baru = false }) { Text("Batal", color = Wr.mu) } })
    }
}

/** Sama seperti nid() di modem: 4 bait pertama SHA-1 pautan (hex). */
fun idPautan(l: String): String = java.security.MessageDigest.getInstance("SHA-1").digest(l.trim().toByteArray()).take(4).joinToString("") { "%02x".format(it) }

@Composable
fun SubLangganan() {
    var ss by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var baru by remember { mutableStateOf(false) }
    var padam by remember { mutableStateOf<JSONObject?>(null) }
    var bukaId by remember { mutableStateOf<String?>(null) }
    var nodSub by remember { mutableStateOf<List<Nod>>(emptyList()) }
    var pingSub by remember { mutableStateOf<List<Int>>(emptyList()) }
    val ctx = LocalContext.current
    fun m() = bg { val a = Modem.arr("/api/subs"); ss = (0 until a.length()).map { a.getJSONObject(it) }; St.nod = Modem.nodes() }
    LaunchedEffect(Unit) { m() }
    LaunchedEffect(bukaId) {
        val id = bukaId ?: return@LaunchedEffect
        nodSub = emptyList()
        withContext(Dispatchers.IO) {
            try {
                val semua = Modem.nodes()
                nodSub = semua.filter { it.src == id }
                pingSub = Modem.ping()
            } catch (_: Exception) {}
        }
    }
    Halaman {
        Judul("Langganan")
        if (ss.isEmpty()) Text("Tiada langganan lagi. Tekan butang Tambah langganan di bawah untuk import pautan.", color = Wr.mu, fontSize = 13.sp)
        ss.forEach { s ->
            val id = s.optString("id")
            val terbuka = bukaId == id && id.isNotEmpty()
            KadWr {
                Row(Modifier.fillMaxWidth().clickable(enabled = id.isNotEmpty()) { bukaId = if (terbuka) null else id },
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.optString("name"), color = Wr.tx, fontWeight = FontWeight.Bold)
                        Text(Bhs.f("{0} profil", s.optInt("count")), color = Wr.mu, fontSize = 12.sp)
                        val up = s.optLong("updated")
                        Text(if (up > 0) Bhs.f("Dikemas kini: {0}", ProfilAlat.waktu(up)) else "Belum pernah dikemas kini",
                            color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (id.isNotEmpty()) Text(if (terbuka) "▲" else "▼", color = Wr.mu, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { St.toast("Mengemas kini ..."); bg { lapor(Modem.tindak("/api/sub/refresh", s.optInt("i").toString())); m() } }) { Text("Kemas kini", color = Wr.ac) }
                    TextButton(onClick = { padam = s }) { Text("Padam", color = Wr.bad) }
                }
                if (terbuka) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Wr.garis))
                    Spacer(Modifier.height(8.dp))
                    if (nodSub.isEmpty()) Text("Memuatkan ...", color = Wr.mu, fontSize = 12.sp)
                    else nodSub.forEach { nd ->
                        val p = pingSub.getOrElse(nd.i) { -1 }
                        val (warna, teks) = when {
                            nd.pilih -> Wr.ac to "Digunakan sekarang"
                            p == -2 -> Wr.mu to "Tidak diuji"
                            p < 0 -> Wr.bad to "Tidak boleh dicapai"
                            else -> Wr.ok to "$p ms"
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(warna))
                            Spacer(Modifier.width(8.dp))
                            Text(nd.nama, color = Wr.tx, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(teks, color = warna, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Btn("Tambah langganan") { baru = true }
    }
    if (baru) {
        var nama by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var ralat by remember { mutableStateOf("") }
        var sibuk by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!sibuk) baru = false }, containerColor = Wr.kad, title = { Text("Langganan baharu", color = Wr.tx) },
            text = {
                Column {
                    OutlinedTextField(nama, { nama = it }, singleLine = true, label = { Text("Nama") }, colors = medan())
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(url, { url = it.trim(); ralat = "" }, label = { Text("URL langganan (http...)") },
                            colors = medan(), modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val c = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                            val teks = c?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
                            if (!teks.isNullOrEmpty()) { url = teks; ralat = "" }
                        }) { Text("Tampal", color = Wr.ac) }
                    }
                    if (ralat.isNotEmpty()) Text(ralat, color = Wr.bad, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    if (sibuk) Text("Mengimport ...", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(enabled = url.startsWith("http") && !sibuk, onClick = {
                sibuk = true
                bg {
                    val r = Modem.tindak("/api/sub/add", JSONObject().put("name", nama).put("url", url).toString())
                    sibuk = false
                    if (r.first) { baru = false; St.toast(r.second.ifEmpty { "Siap" }); m() } else { ralat = r.second.ifEmpty { "Gagal import" } }
                }
            }) { Text("Tambah", color = Wr.ac) } },
            dismissButton = { TextButton(enabled = !sibuk, onClick = { baru = false }) { Text("Batal", color = Wr.mu) } })
    }
    padam?.let { s ->
        AlertDialog(onDismissRequest = { padam = null }, containerColor = Wr.kad,
            title = { Text("Padam langganan ini?", color = Wr.tx) },
            text = { Text(Bhs.f("{0}: semua {1} nod yang diimportnya akan dibuang. Tindakan ini tidak boleh dibatalkan.", s.optString("name"), s.optInt("count")),
                color = Wr.mu, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = {
                val i = s.optInt("i"); padam = null; bukaId = null
                bg { lapor(Modem.tindak("/api/sub/del", i.toString())); m() }
            }) { Text("Padam", color = Wr.bad) } },
            dismissButton = { TextButton(onClick = { padam = null }) { Text("Batal", color = Wr.mu) } })
    }
}

// ───────────── Menu pilihan (seperti dropdown web) ─────────────
@Composable
fun Pilihan(label: String, pilihan: List<Pair<String, String>>, nilai: String, ubah: (String) -> Unit) {
    var buka by remember { mutableStateOf(false) }
    val teks = pilihan.firstOrNull { it.first == nilai }?.second ?: nilai
    Text(label, color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    Box {
        OutlinedButton(onClick = { buka = true }, modifier = Modifier.fillMaxWidth()) {
            Text(teks, modifier = Modifier.weight(1f), maxLines = 1)
            Text("▾", color = Wr.mu)
        }
        DropdownMenu(buka, { buka = false }) {
            pilihan.forEach { (v, t) -> DropdownMenuItem({ Text(t) }, { ubah(v); buka = false }) }
        }
    }
}

// ───────────── Tetapan ─────────────
@Composable
fun SubTetapan() {
    val o = muat { Modem.obj("/api/opts") }
    val st = muat { Modem.obj("/api/settings") }
    var auto by remember { mutableStateOf(true) }
    var dns by remember { mutableStateOf("") }
    var dnsSendiri by remember { mutableStateOf(false) }
    var qs by remember { mutableStateOf("UseIP") }
    var ping by remember { mutableStateOf("") }
    var pingSendiri by remember { mutableStateOf(false) }
    var logLevel by remember { mutableStateOf("warning") }
    var reboot by remember { mutableStateOf("") }
    var rebootOn by remember { mutableStateOf(false) }
    var mod by remember { mutableStateOf("redirect") }
    var kata by remember { mutableStateOf("") }
    var hook by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf(true) }
    val ctx = LocalContext.current
    val dnsPilihan = listOf("" to "Lalai (8.8.8.8)", "1.1.1.1" to "Cloudflare (1.1.1.1)", "8.8.8.8" to "Google (8.8.8.8)",
        "9.9.9.9" to "Quad9 (9.9.9.9)", "208.67.222.222" to "OpenDNS (208.67.222.222)", "94.140.14.14" to "AdGuard (94.140.14.14)",
        "185.228.168.9" to "CleanBrowsing (185.228.168.9)")
    val qsPilihan = listOf("UseIP" to "IPv4 + IPv6", "UseIPv4" to "IPv4 Sahaja", "UseIPv6" to "IPv6 Sahaja")
    val pingPilihan = listOf(
        "http://www.gstatic.com/generate_204" to "Google (gstatic)",
        "http://connectivitycheck.gstatic.com/generate_204" to "Google (connectivitycheck)",
        "http://cp.cloudflare.com/generate_204" to "Cloudflare",
        "http://detectportal.firefox.com/success.txt" to "Firefox",
        "http://www.msftconnecttest.com/connecttest.txt" to "Microsoft")
    val logPilihan = listOf("debug" to "debug", "info" to "info", "warning" to "warning", "error" to "error", "none" to "none")
    val lain = "__c" to "Lain-lain (taip sendiri)"
    LaunchedEffect(o.value) { o.value?.let {
        auto = it.optBoolean("autorecon")
        dns = it.optString("dns"); dnsSendiri = dns.isNotEmpty() && dnsPilihan.none { p -> p.first == dns }
        qs = it.optString("qs").ifEmpty { "UseIP" }
        ping = it.optString("pingurl"); pingSendiri = ping.isNotEmpty() && pingPilihan.none { p -> p.first == ping }
        logLevel = it.optString("loglevel").ifEmpty { "warning" }
        reboot = it.optString("reboot"); rebootOn = reboot.isNotEmpty()
        mod = it.optString("vpnmode").ifEmpty { "redirect" }
    } }
    LaunchedEffect(st.value) { hook = st.value?.optBoolean("hook") ?: false; web = st.value?.optBoolean("web", true) ?: true }
    Halaman {
        Judul("Tetapan")
        KadWr {
            Suis("Sambung semula automatik", auto) { auto = it }
            Text("Mulakan semula xray jika terputus atau ujian gagal 3 kali.", color = Wr.mu, fontSize = 11.sp)
            Pilihan("Pelayan DNS", dnsPilihan + lain, if (dnsSendiri) "__c" else dns) { v -> if (v == "__c") dnsSendiri = true else { dnsSendiri = false; dns = v } }
            if (dnsSendiri) OutlinedTextField(dns, { dns = it.trim() }, singleLine = true, label = { Text("Alamat IP (cth 1.1.1.1)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = medan())
            Pilihan("Query strategy", qsPilihan, qs) { qs = it }
            Pilihan("URL ujian sambungan", pingPilihan + lain, if (pingSendiri) "__c" else ping) { v -> if (v == "__c") pingSendiri = true else { pingSendiri = false; ping = v } }
            if (pingSendiri) OutlinedTextField(ping, { ping = it.trim() }, singleLine = true, label = { Text("URL (http:// atau https://)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = medan())
            Text("Digunakan untuk uji sama ada internet melalui nod semasa benar-benar berfungsi (bukan hanya sambungan TCP).", color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Pilihan("Tahap log xray", logPilihan, logLevel) { logLevel = it }
            Spacer(Modifier.height(10.dp))
            Suis("But semula modem berjadual harian", rebootOn) { rebootOn = it; if (!it) reboot = "" else if (reboot.isEmpty()) reboot = "04:00" }
            Text("Berbeza daripada butang But semula modem di Sistem (yang but semula serta-merta bila ditekan). Pilihan ini but semula modem secara AUTOMATIK setiap hari pada masa yang ditetapkan (contohnya lewat malam, trafik rendah), untuk bersihkan memori dan kekalkan prestasi dalam jangka panjang.",
                color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            if (rebootOn) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("00:00", "02:00", "03:00", "04:00", "05:00").forEach { t -> FilterChip(reboot == t, { reboot = t }, label = { Text(t, fontSize = 12.sp) }) }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(reboot, { reboot = it.trim() }, singleLine = true, label = { Text("Masa (HH:MM)") }, modifier = Modifier.fillMaxWidth(), colors = medan())
            }
            Text("Mod VPN", color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("redirect", "tproxy", "tun").forEach { m ->
                    val ok = when (m) { "tproxy" -> o.value?.optBoolean("cap_tproxy") ?: false; "tun" -> o.value?.optBoolean("cap_tun") ?: false; else -> true }
                    FilterChip(mod == m, { mod = m }, enabled = ok, label = { Text(m) })
                }
            }
            Text(when (mod) {
                "redirect" -> "Redirect: hanya TCP melalui nod, paling serasi."
                else -> "TProxy dan TUN: TCP dan UDP (termasuk permainan dan panggilan) melalui nod, nod mesti menyokong UDP."
            }, color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Btn("Simpan tetapan") { bg { lapor(Modem.tindak("/api/opts", JSONObject().put("autorecon", auto).put("dns", dns).put("qs", qs).put("pingurl", ping).put("loglevel", logLevel).put("reboot", reboot).put("vpnmode", mod).toString())) } }
        }
        KadWr {
            Suis("Suis boot (mula automatik selepas modem hidup)", hook) { hook = it; bg { lapor(Modem.tindak("/api/hook", if (it) "on" else "off")) } }
            if (st.value?.optBoolean("webtetap") != true) {
                Suis("Antara muka web (Chrome)", web) { web = it; bg { lapor(Modem.tindak("/api/web", if (it) "on" else "off")) } }
                Text("Matikan jika mahu guna apl sahaja. Apl dan VLESS Pro terus berfungsi seperti biasa.", color = Wr.mu, fontSize = 12.sp)
            }
            st.value?.let { Baris("Versi VLESS Pro", "v" + it.optString("version")); Baris("Edisi", it.optString("edisi")); Baris("MAC", it.optString("mac")) }
        }
        KadWr {
            Text("Tukar kata laluan panel", color = Wr.tx, fontWeight = FontWeight.Bold)
            OutlinedTextField(kata, { kata = it }, singleLine = true, label = { Text("Kata laluan baharu (min 6)") }, modifier = Modifier.fillMaxWidth(), colors = medan())
            Spacer(Modifier.height(8.dp))
            Btn("Tukar", aktif = kata.length >= 6) {
                val k = kata
                bg { val r = Modem.tukarKata(k); lapor(r); if (r.first) { ctx.getSharedPreferences("vp", Context.MODE_PRIVATE).edit().putString("pass", k).apply(); kata = "" } }
            }
        }
    }
}

// ───────────── Kemas kini modem dari fail .tgz ─────────────
fun namaFail(ctx: Context, uri: android.net.Uri): String =
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else ""
    }.orEmpty()

@Composable
fun KadFail(aktif: Boolean, adaPrev: Boolean, selepas: (Boolean, String) -> Unit, pulih: () -> Unit) {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    var nama by remember { mutableStateOf("") }
    var teks by remember { mutableStateOf("") }
    val pilih = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; nama = namaFail(ctx, u); teks = "" }
    }
    KadWr {
        Text("Tiada internet? Pilih fail .tgz kemas kini:", color = Wr.mu, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Wr.inp)
            .border(1.dp, Wr.garis, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { pilih.launch(arrayOf("*/*")) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Wr.kad2).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text("Pilih fail", color = Wr.ac, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(nama.ifEmpty { "Tiada fail dipilih" }, color = if (nama.isEmpty()) Wr.mu else Wr.tx, fontSize = 13.sp, maxLines = 2)
        }
        Text("armv7 = B618-65D · arm64 = AW1000 / AW1000 Ultra", color = Wr.mu, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedButton(enabled = uri != null && aktif, modifier = Modifier.fillMaxWidth().height(50.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac),
            onClick = {
                val u = uri ?: return@OutlinedButton
                teks = "Membaca fail ..."
                bg {
                    val n = nama.lowercase()
                    val arch = try { Modem.obj("/api/settings").optString("arch") } catch (_: Exception) { "" }
                    val salah = (arch == "arm64" && n.contains("armv7")) || (arch == "arm" && n.contains("arm64"))
                    if (salah) { teks = "Fail ini untuk modem lain (armv7 atau arm64). Pilih fail yang betul."; return@bg }
                    val data = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: ByteArray(0)
                    if (data.isEmpty()) { teks = "Fail kosong"; return@bg }
                    teks = "Menghantar ${data.size / 1024} KB ke modem ..."
                    val r = Modem.muatNaik(data)
                    teks = r.second
                    selepas(r.first, r.second)
                }
            }) { Text("Pasang dari fail", color = Wr.tx, fontWeight = FontWeight.Bold) }
        if (adaPrev) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(enabled = aktif, modifier = Modifier.fillMaxWidth().height(50.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac),
                onClick = pulih) { Text("Pulih versi sebelum", color = Wr.tx, fontWeight = FontWeight.Bold) }
        }
        if (teks.isNotEmpty()) Text(teks, color = Wr.ac, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ───────────── Tema dan bahasa ─────────────
@Composable
fun SubTema() {
    val ctx = LocalContext.current
    Halaman {
        Judul("Tema")
        Text("Pilih rupa dan jenis tulisan apl. Disimpan pada telefon ini.", color = Wr.mu, fontSize = 13.sp)
        Temas.semua.forEach { t ->
            val c = { k: String -> Color(t.w.getValue(k)) }
            val dipilih = Wr.id == t.id
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(c("cd"))
                .border(2.dp, if (dipilih) c("ac") else c("bd"), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .clickable { Temas.tetap(ctx, t.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c("ac")))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(t.nama, color = c("tx"), fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = Temas.fonDari(t.fon))
                    androidx.compose.material3.Text("Aa Bb 0123 · " + Bhs.t("Sambung"), color = c("mu"), fontSize = 13.sp, fontFamily = Temas.fonDari(t.fon))
                }
                Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c("ac2")))
            }
        }
    }
}

@Composable
fun SubBahasa() {
    val ctx = LocalContext.current
    Halaman {
        Judul("Bahasa")
        Text("Pilih bahasa paparan apl. Disimpan pada telefon ini.", color = Wr.mu, fontSize = 13.sp)
        Bhs.senarai.forEach { (k, n, s) ->
            KadWr(klik = { Bhs.tetap(ctx, k) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(n, color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = Wr.ff)
                        if (s.isNotEmpty()) Text(s, color = Wr.mu, fontSize = 12.sp)
                    }
                    if (Bhs.kod == k) Text("✓", color = Wr.ac, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ───────────── Kemas kini (gabungan: modem + apl) ─────────────
@Composable
fun SubKemasGabung() {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text("Kemas kini web/Chrome", fontSize = 12.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
            FilterChip(tab == 1, { tab = 1 }, label = { Text("Kemas kini apl", fontSize = 12.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
        }
        Box(Modifier.weight(1f)) { if (tab == 0) SubKemas() else SubApl() }
    }
}

// ───────────── Kemas kini modem (seperti panel Chrome) ─────────────
@Composable
fun SubKemas() {
    var u by remember { mutableStateOf<JSONObject?>(null) }
    var proses by remember { mutableStateOf("") }
    var jalan by remember { mutableStateOf(false) }
    var semua by remember { mutableStateOf(false) }
    var tukarSvr by remember { mutableStateOf(false) }
    var pulih by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("notify") }
    fun muat(f: Boolean) = bg { u = Modem.update(f); mode = u?.optString("mode").orEmpty().ifEmpty { "notify" } }
    LaunchedEffect(Unit) { muat(false) }

    /** Selepas kemas kini bermula: tunggu modem hidup semula, log masuk semula, kemudian muat data baharu. */
    fun ikut(lama: String) = bg {
        jalan = true
        try {
            val baru = Modem.tungguSiap(lama) { proses = it }
            if (baru == null) { proses = if (proses.isEmpty()) "Gagal" else proses }
            else {
                proses = ""
                St.status = null; St.nod = emptyList(); St.ipInfo = null
                u = Modem.update(true)
                St.toast(Bhs.f("Dikemas kini ke v{0}", baru))
            }
        } catch (_: SalahKata) { St.perluLog = true }
        finally { jalan = false }
    }

    Halaman {
        Judul("Kemas kini")
        val v = u
        val cur = v?.optString("cur").orEmpty()
        val latest = v?.optString("latest").orEmpty()
        val baru = v?.optBoolean("newer") ?: false
        val lama = v?.optBoolean("older") ?: false
        val info = v?.optJSONObject("info")
        KadWr {
            Text("KEMAS KINI DALAM TALIAN", color = Wr.mu, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Wr.kad2), contentAlignment = Alignment.Center) {
                    Text(if (baru) "↑" else "✓", color = if (baru) Wr.wn else Wr.ok, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("v" + latest.ifEmpty { cur }, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Wr.tx)
                    val sz = info?.optLong("size") ?: 0L
                    Text(Bhs.t("Dipasang") + " v$cur" + (info?.optString("date")?.takeIf { it.isNotEmpty() }?.let { " · " + Bhs.t("dikeluarkan") + " $it" } ?: "") +
                        (if (sz > 0) " · %.1f MB".format(sz / 1048576.0) else ""), color = Wr.mu, fontSize = 12.sp)
                }
            }
            if (info != null) {
                Spacer(Modifier.height(12.dp))
                Text(info.optString("title"), color = Wr.tx, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val it = info.optJSONArray("items") ?: JSONArray()
                for (i in 0 until it.length()) Text("•  " + Bhs.t(it.getString(i)), color = Wr.tx, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
                val h = info.optJSONArray("history")
                if (h != null && h.length() > 0) {
                    Text(if (semua) "Sembunyikan" else "Tunjuk semua apa yang baharu", color = Wr.ac, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { semua = !semua }.padding(vertical = 10.dp))
                    if (semua) for (i in 0 until h.length()) {
                        val o = h.getJSONObject(i)
                        Text("v" + o.optString("version") + " · " + o.optString("date"), color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(o.optString("title"), color = Wr.tx, fontWeight = FontWeight.Bold)
                        val its = o.optJSONArray("items") ?: JSONArray()
                        for (k in 0 until its.length()) Text("•  " + Bhs.t(its.getString(k)), color = Wr.mu, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(when { baru -> "Versi baharu tersedia."; lama -> "Versi anda lebih baharu daripada yang ada di pelayan."; else -> "Anda menggunakan versi terkini." },
                color = if (baru) Wr.wn else Wr.ok, fontWeight = FontWeight.SemiBold)
            v?.optString("msg")?.takeIf { it.isNotEmpty() && !(v.optBoolean("ok")) }?.let { Text(it, color = Wr.bad, fontSize = 12.sp) }
            if (proses.isNotEmpty() || jalan) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (jalan) CircularProgressIndicator(Modifier.size(18.dp), color = Wr.ac, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text(proses.ifEmpty { "Memulakan ..." }, color = Wr.ac, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (baru) Btn("Kemas kini sekarang", aktif = !jalan) { bg { val (ok, m) = Modem.kemasKini(false); proses = m; if (ok) ikut(cur) } }
            else if (latest.isNotEmpty()) OutlinedButton(onClick = { bg { val (ok, m) = Modem.kemasKini(true); proses = m; if (ok) ikut(cur) } }, enabled = !jalan,
                modifier = Modifier.fillMaxWidth().height(50.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Wr.ac)) { Text(Bhs.t("Pasang semula") + " v$latest", color = Wr.tx, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            Btn("Semak sekarang", false, !jalan) { muat(true); St.toast("Semakan siap") }
            val at = v?.optLong("checked") ?: 0L
            val now = v?.optLong("now") ?: 0L
            Text(Bhs.t("Semakan terakhir") + ": " + when {
                at == 0L -> Bhs.t("belum pernah")
                now - at < 90 -> Bhs.t("baru sahaja")
                now - at < 5400 -> Bhs.f("{0} minit lalu", (now - at) / 60)
                now - at < 172800 -> Bhs.f("{0} jam lalu", (now - at) / 3600)
                else -> Bhs.f("{0} hari lalu", (now - at) / 86400) }, color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        KadWr {
            Text("KEMAS KINI AUTOMATIK", color = Wr.mu, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("notify" to "Beritahu saya", "auto" to "Pasang automatik", "off" to "Tutup").forEach { (k, t) ->
                    FilterChip(mode == k, { mode = k; bg { lapor(Modem.cfgKemas(JSONObject().put("mode", k))) } }, label = { Text(t, fontSize = 12.sp, maxLines = 1) })
                }
            }
            Text(when (mode) {
                "auto" -> "Semak setiap hari dan pasang versi baharu secara automatik. Internet tidak terputus, hanya UI dimulakan semula."
                "off" -> "Tiada semakan automatik. Tekan Semak sekarang untuk menyemak sendiri."
                else -> "Semak setiap hari dan tunjuk loceng apabila versi baharu keluar. Anda pilih bila hendak pasang." }, color = Wr.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Bhs.t("Pelayan kemas kini") + ": " + v?.optString("server").orEmpty(), color = Wr.mu, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Tukar", color = Wr.ac, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { tukarSvr = true }.padding(8.dp))
            }
        }
        Text("PASANG DARI FAIL", color = Wr.mu, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        KadFail(!jalan, v?.optBoolean("prev") == true, { ok, m -> St.toast(m); if (ok) ikut(cur) }, { pulih = true })
    }
    if (pulih) AlertDialog(onDismissRequest = { pulih = false }, containerColor = Wr.kad,
        title = { Text("Pulihkan versi sebelum?", color = Wr.tx) }, text = { Text("UI akan dimulakan semula.", color = Wr.mu) },
        confirmButton = { TextButton(onClick = { pulih = false; val c = u?.optString("cur").orEmpty(); bg { val (ok, m) = Modem.pulihVersi(); proses = m; if (ok) ikut(c) } }) { Text("Pulihkan", color = Wr.ac) } },
        dismissButton = { TextButton(onClick = { pulih = false }) { Text("Batal", color = Wr.mu) } })
    if (tukarSvr) {
        var url by remember { mutableStateOf(u?.optString("url").orEmpty()) }
        AlertDialog(onDismissRequest = { tukarSvr = false }, containerColor = Wr.kad, title = { Text("Pelayan kemas kini", color = Wr.tx) },
            text = { OutlinedTextField(url, { url = it.trim() }, label = { Text("https://…-latest.txt") }, colors = medan()) },
            confirmButton = { TextButton(onClick = { tukarSvr = false; bg { lapor(Modem.cfgKemas(JSONObject().put("server", url))); u = Modem.update(true) } }) { Text("Simpan", color = Wr.ac) } },
            dismissButton = { TextButton(onClick = { tukarSvr = false }) { Text("Batal", color = Wr.mu) } })
    }
}
