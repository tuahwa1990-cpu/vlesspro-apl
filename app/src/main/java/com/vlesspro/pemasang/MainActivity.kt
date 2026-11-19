package com.vlesspro.pemasang

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0B5FA5))) { Utama() }
        }
    }
}

@Composable
fun Utama() {
    val ctx = LocalContext.current
    val pref = remember { ctx.getSharedPreferences("vp", Context.MODE_PRIVATE) }
    var tab by remember { mutableIntStateOf(0) }
    var edisi by remember { mutableStateOf(Edisi.values().firstOrNull { it.name == pref.getString("edisi", "") } ?: Edisi.B618) }
    var ip by remember { mutableStateOf(pref.getString("ip", null) ?: edisi.ipLalai) }
    var panel by remember { mutableStateOf<String?>(null) }

    fun simpan() { pref.edit().putString("edisi", edisi.name).putString("ip", ip).apply() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Pemasang") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Panel VLESS Pro") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) Pemasangan(edisi, ip, { e -> edisi = e; ip = e.ipLalai; simpan() }, { ip = it; simpan() }, { p -> panel = p; tab = 1 })
            else PanelWeb(panel ?: "$ip:8990", ip)
        }
    }
}

@Composable
fun Pemasangan(edisi: Edisi, ip: String, ubahEdisi: (Edisi) -> Unit, ubahIp: (String) -> Unit, bukaPanel: (String) -> Unit) {
    val ctx = LocalContext.current
    var user by remember { mutableStateOf("root") }
    var pass by remember { mutableStateOf("") }
    var boot by remember { mutableStateOf(true) }
    var sibuk by remember { mutableStateOf(false) }
    var hasil by remember { mutableStateOf<Hasil?>(null) }
    var kemas by remember { mutableStateOf<InfoApl?>(null) }
    val log = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        Thread {
            try {
                val t = Kemas.terkini()
                if (t.code > Kemas.versiSemasa(ctx)) kemas = t
            } catch (_: Exception) {}
        }.start()
    }

    fun kerja(tugas: () -> Unit) {
        if (sibuk) return
        sibuk = true; hasil = null; log.clear()
        Thread {
            try { tugas() } catch (e: Exception) { log.add("GAGAL: ${e.message ?: e.javaClass.simpleName}") }
            sibuk = false
        }.start()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F7FB)).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("VLESS Pro Pemasang", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Pasang, kemas kini dan urus VLESS Pro pada modem anda terus dari telefon, tanpa Termux.", fontSize = 13.sp, color = Color.DarkGray)

        kemas?.let { k ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Versi apl baharu tersedia: ${k.name}", fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        kerja {
                            val m = Kemas.pasang(ctx, k) { log.add(it) }
                            if (m != null) log.add(m)
                        }
                    }, enabled = !sibuk) { Text("Kemas kini apl sekarang") }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pilih modem", fontWeight = FontWeight.SemiBold)
                Edisi.values().forEach { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = edisi == e, onClick = { ubahEdisi(e) }, enabled = !sibuk)
                        Text(e.nama)
                    }
                }
                OutlinedTextField(ip, { ubahIp(it.trim()) }, label = { Text("Alamat IP modem") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(), enabled = !sibuk)
                OutlinedTextField(user, { user = it.trim() }, label = { Text("Pengguna SSH") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !sibuk)
                OutlinedTextField(pass, { pass = it }, label = { Text("Kata laluan SSH (kosong jika tiada)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !sibuk)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(boot, { boot = it }, enabled = !sibuk)
                    Text("Pasang suis boot (hidup sendiri selepas modem restart)", fontSize = 13.sp)
                }
                Text("Telefon mesti disambung ke WiFi modem dan SSH modem dihidupkan.", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Button(onClick = {
            kerja { hasil = Pemasang.pasang(edisi, ip, user, pass, boot) { log.add(it) } }
        }, enabled = !sibuk && ip.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (sibuk) "Sedang berjalan ..." else "Pasang / Kemas kini VLESS Pro")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { kerja { Pemasang.status(edisi, ip, user, pass) { log.add(it) } } },
                enabled = !sibuk && ip.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Semak status") }
            OutlinedButton(onClick = { kerja { Pemasang.suisBoot(edisi, ip, user, pass) { log.add(it) } } },
                enabled = !sibuk && ip.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Pasang suis boot") }
        }
        OutlinedButton(onClick = { bukaPanel(hasil?.panel ?: "$ip:8990") }, modifier = Modifier.fillMaxWidth()) {
            Text("Buka panel VLESS Pro dalam apl")
        }

        hasil?.let { h ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F6EA))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Siap dipasang", fontWeight = FontWeight.Bold)
                    if (h.id.isNotEmpty()) {
                        Text("ID Peranti:  ${h.id}", fontFamily = FontFamily.Monospace)
                        OutlinedButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("ID", h.id))
                        }) { Text("Salin ID Peranti") }
                        Text("Hantar ID ini kepada penjual untuk mendapatkan kunci lesen.", fontSize = 12.sp)
                    }
                    Text("Panel: http://${h.panel}   Kata laluan awal: admin", fontSize = 12.sp)
                }
            }
        }

        if (log.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF10151C)), shape = RoundedCornerShape(10.dp)) {
                Text(log.joinToString("\n"), color = Color(0xFFC9D4E0), fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
            }
        }
        Text("Versi apl ${Kemas.versiSemasa(ctx)}", fontSize = 11.sp, color = Color.Gray)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PanelWeb(alamat: String, ip: String) {
    var url by remember(alamat) { mutableStateOf(alamat) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var ralat by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = web?.canGoBack() == true) { web?.goBack() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(url, { url = it.trim() }, singleLine = true, modifier = Modifier.weight(1f),
                label = { Text("Alamat panel") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Button(onClick = { ralat = null; web?.loadUrl(if (url.startsWith("http")) url else "http://$url") }) { Text("Buka") }
        }
        ralat?.let {
            Text("Tidak dapat sambung ke panel. Pastikan telefon disambung ke WiFi modem dan VLESS Pro sudah dipasang.\n($it)",
                color = Color(0xFFB00020), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                        if (r.isForMainFrame) ralat = e.description?.toString() ?: "ralat"
                    }
                    override fun onPageFinished(v: WebView, u: String) { if (ralat != null && u.startsWith("http")) { /* kekal mesej sehingga muat semula */ } }
                }
                web = this
                loadUrl(if (alamat.startsWith("http")) alamat else "http://$alamat")
            }
        })
    }
}
