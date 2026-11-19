package com.vlesspro.pemasang

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        window.statusBarColor = 0xFF111114.toInt()
        window.navigationBarColor = 0xFF1B1B20.toInt()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0B5FA5))) { Utama() }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Utama() {
    val ctx = LocalContext.current
    val pref = remember { ctx.getSharedPreferences("vp", Context.MODE_PRIVATE) }
    // Skrin pertama ialah panel VLESS Pro; kali pertama (belum ada modem disimpan) mula di Pemasang.
    var tab by remember { mutableIntStateOf(if (pref.getString("ip", null) == null) 1 else 0) }
    var edisi by remember { mutableStateOf(Edisi.values().firstOrNull { it.name == pref.getString("edisi", "") } ?: Edisi.B618) }
    var ip by remember { mutableStateOf(pref.getString("ip", null) ?: edisi.ipLalai) }
    var ralat by remember { mutableStateOf<String?>(null) }
    var kemas by remember { mutableStateOf<InfoApl?>(null) }
    var mengemas by remember { mutableStateOf(false) }
    var pilihCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pilih = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pilihCb?.onReceiveValue(if (uri != null) arrayOf(uri) else null); pilihCb = null
    }

    fun simpan() { pref.edit().putString("edisi", edisi.name).putString("ip", ip).apply() }
    fun panelUrl() = if (ip.startsWith("http")) ip else "http://$ip:8990"

    val web = remember {
        WebView(ctx).apply {
            setBackgroundColor(0xFF111114.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, u: String?, f: android.graphics.Bitmap?) { ralat = null }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) ralat = e.description?.toString() ?: "ralat"
                }
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val u = r.url
                    if (u.scheme == "http" || u.scheme == "https") {
                        val h = Uri.parse(v.url ?: "").host
                        if (h != null && u.host != h) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                            return true
                        }
                        return false
                    }
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(v: WebView, url: String?, message: String?, result: JsResult): Boolean {
                    AlertDialog.Builder(ctx).setMessage(message).setCancelable(false)
                        .setPositiveButton("OK") { _, _ -> result.confirm() }.show()
                    return true
                }
                override fun onJsConfirm(v: WebView, url: String?, message: String?, result: JsResult): Boolean {
                    AlertDialog.Builder(ctx).setMessage(message).setCancelable(false)
                        .setPositiveButton("OK") { _, _ -> result.confirm() }
                        .setNegativeButton("Batal") { _, _ -> result.cancel() }.show()
                    return true
                }
                override fun onJsPrompt(v: WebView, url: String?, message: String?, def: String?, result: JsPromptResult): Boolean {
                    val et = EditText(ctx); et.setText(def ?: "")
                    AlertDialog.Builder(ctx).setMessage(message).setView(et).setCancelable(false)
                        .setPositiveButton("OK") { _, _ -> result.confirm(et.text.toString()) }
                        .setNegativeButton("Batal") { _, _ -> result.cancel() }.show()
                    return true
                }
                override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: WebChromeClient.FileChooserParams): Boolean {
                    pilihCb?.onReceiveValue(null)
                    pilihCb = cb
                    return try { pilih.launch(arrayOf("*/*")); true } catch (_: Exception) { pilihCb = null; false }
                }
            }
            setDownloadListener { url, ua, cd, mime, _ ->
                try {
                    val nama = URLUtil.guessFileName(url, cd, mime)
                    val rq = DownloadManager.Request(Uri.parse(url))
                        .addRequestHeader("User-Agent", ua)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setTitle(nama)
                    CookieManager.getInstance().getCookie(url)?.let { rq.addRequestHeader("Cookie", it) }
                    if (Build.VERSION.SDK_INT >= 29) rq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nama)
                    else rq.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, nama)
                    (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(rq)
                    Toast.makeText(ctx, "Memuat turun $nama ke folder Download", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Muat turun gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            loadUrl(panelUrl())
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }
    BackHandler(enabled = tab == 0 && web.canGoBack()) { web.goBack() }

    LaunchedEffect(Unit) {
        Thread {
            try {
                val t = Kemas.terkini()
                if (t.code > Kemas.versiSemasa(ctx)) kemas = t
            } catch (_: Exception) {}
        }.start()
    }

    val warna = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = Color(0xFF2E6F4E),
        unselectedIconColor = Color(0xFF9AA0A6), unselectedTextColor = Color(0xFF9AA0A6))
    Scaffold(
        containerColor = Color(0xFF111114),
        bottomBar = {
            Column {
                if (tab == 0) kemas?.let { k ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF3A3320)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Versi apl baharu: ${k.name}", color = Color(0xFFFFE28A), fontSize = 13.sp)
                        TextButton(enabled = !mengemas, onClick = {
                            mengemas = true
                            Thread {
                                try {
                                    val m = Kemas.pasang(ctx, k) { }
                                    if (m != null) android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(ctx, m, Toast.LENGTH_LONG).show() }
                                } catch (e: Exception) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(ctx, "Gagal: ${e.message}", Toast.LENGTH_LONG).show() }
                                }
                                mengemas = false
                            }.start()
                        }) { Text(if (mengemas) "Sebentar ..." else "Kemas kini", color = Color.White) }
                    }
                }
                NavigationBar(containerColor = Color(0xFF1B1B20)) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, colors = warna,
                        icon = { Icon(Icons.Filled.Home, null) }, label = { Text("VLESS Pro") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, colors = warna,
                        icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Pemasang") })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            // Panel kekal hidup di belakang supaya sesi tidak hilang bila tukar tab.
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { (web.parent as? android.view.ViewGroup)?.removeView(web); web },
                update = { it.visibility = if (tab == 0) android.view.View.VISIBLE else android.view.View.INVISIBLE })
            if (tab == 0 && ralat != null) {
                Column(Modifier.fillMaxSize().background(Color(0xFF111114)).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VLESS Pro", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Tidak dapat sambung ke modem ($ip).\nPastikan telefon disambung ke WiFi modem dan VLESS Pro sudah dipasang.",
                        color = Color(0xFFC9D4E0), fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { ralat = null; web.loadUrl(panelUrl()) }, modifier = Modifier.fillMaxWidth()) { Text("Cuba lagi") }
                    OutlinedButton(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Pasang VLESS Pro / tukar modem", color = Color.White) }
                }
            }
            if (tab == 1) {
                Pemasangan(edisi, ip,
                    { e -> edisi = e; ip = e.ipLalai; simpan() },
                    { ip = it; simpan() },
                    { _ -> simpan(); ralat = null; web.loadUrl(panelUrl()); tab = 0 })
            }
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
    var kunci by remember { mutableStateOf("") }
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aktifkan lesen", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = kunci, onValueChange = { kunci = it }, label = { Text("Tampal kunci lesen") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Button(onClick = { kerja { Pemasang.aktifkanLesen(edisi, ip, user, pass, kunci) { log.add(it) } } },
                    enabled = !sibuk && ip.isNotEmpty() && kunci.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Aktifkan lesen") }
                Text("Kunci diberi oleh penjual berdasarkan ID Peranti anda. Percubaan 3 hari pun diaktifkan di sini.", fontSize = 12.sp, color = Color.Gray)
            }
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
