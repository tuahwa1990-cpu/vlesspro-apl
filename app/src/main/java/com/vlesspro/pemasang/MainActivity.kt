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
import android.webkit.JavascriptInterface
import android.widget.EditText
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
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

// Paksa tinggi halaman panel mengikut tetingkap sebenar (elak kandungan terpotong).
const val BETUL_TINGGI = "try{var h=window.innerHeight;document.documentElement.style.height=h+'px';document.body.style.height=h+'px';window.dispatchEvent(new Event('resize'))}catch(e){}"

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
    var mula by remember { mutableStateOf(true) }
    var tinggiWv by remember { mutableIntStateOf(0) }
    var diag by remember { mutableStateOf("") }
    var mencari by remember { mutableStateOf(false) }
    var vlessHidup by remember { mutableStateOf<Boolean?>(null) }
    var lesenTeks by remember { mutableStateOf("") }
    var nodNama by remember { mutableStateOf("") }
    val haptik = LocalHapticFeedback.current
    var pilihCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pilih = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pilihCb?.onReceiveValue(if (uri != null) arrayOf(uri) else null); pilihCb = null
    }

    fun simpan() { pref.edit().putString("edisi", edisi.name).putString("ip", ip).apply() }
    fun panelUrl() = if (ip.startsWith("http")) ip else "http://$ip:8990"

    val web = remember {
        WebView(ctx).apply {
            setBackgroundColor(0xFF111114.toInt())
            addJavascriptInterface(object {
                @JavascriptInterface fun d(t: String) { diag = t }
                @JavascriptInterface fun s(t: String) {
                    try {
                        val j = JSONObject(t)
                        vlessHidup = j.optBoolean("vless")
                        nodNama = j.optString("node", "")
                        val l = j.optJSONObject("lic")
                        val st = l?.optString("state").orEmpty()
                        val exp = l?.optString("exp").orEmpty()
                        lesenTeks = when {
                            st == "active" && (exp == "0" || exp.isEmpty()) -> "Lesen seumur hidup"
                            st == "active" -> {
                                val f = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                                val d = f.parse(exp)
                                val baki = if (d != null) ((d.time - System.currentTimeMillis()) / 86400000L + 1).toInt() else -1
                                if (baki >= 0) "Lesen: $baki hari lagi" else "Lesen aktif"
                            }
                            st == "expired" -> "Lesen luput"
                            st == "none" || st.isEmpty() -> "Belum ada lesen"
                            else -> "Lesen: $st"
                        }
                    } catch (_: Exception) {}
                }
            }, "VPApp")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, u: String?, f: android.graphics.Bitmap?) { ralat = null }
                override fun onPageFinished(v: WebView, u: String?) {
                    v.evaluateJavascript(BETUL_TINGGI, null)
                }
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
            // Muat panel hanya selepas WebView ada saiz sebenar; jika dimuat sebelum itu, tinggi paparan
            // (height:100% dalam CSS panel) tersalah kira dan kandungan terpotong.
            var dimuat = false
            addOnLayoutChangeListener { v, l, t, r, b, ot, ob, _, _ ->
                tinggiWv = b - t
                if (dimuat && (b - t) != 0 && (b - t) != 0 && ((b - t) != ob - ot)) (v as WebView).evaluateJavascript(BETUL_TINGGI, null)
                if (!dimuat && b - t > 200 && r - l > 200) { dimuat = true; v.post { (v as WebView).loadUrl(panelUrl()) } }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }
    fun ada(h: String): Boolean = try {
        val c = URL("http://$h:8990/api/status").openConnection() as HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 1500
        c.responseCode > 0
    } catch (_: Exception) { false }

    // Cari modem: cuba IP tersimpan dahulu, kemudian dua IP lalai.
    fun kesan(selesai: () -> Unit) {
        mencari = true
        Thread {
            val calon = (listOf(ip) + Edisi.values().map { it.ipLalai }).distinct()
            val jumpa = calon.firstOrNull { ada(it) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (jumpa != null) {
                    val perluMuat = jumpa != ip || ralat != null
                    ip = jumpa
                    Edisi.values().firstOrNull { it.ipLalai == jumpa }?.let { edisi = it }
                    simpan()
                    ralat = null
                    if (perluMuat) web.loadUrl(panelUrl())
                }
                mencari = false
                selesai()
            }
        }.start()
    }

    BackHandler(enabled = tab == 0 && web.canGoBack()) { web.goBack() }

    LaunchedEffect(Unit) {
        Thread {
            try {
                val t = Kemas.terkini()
                if (t.code > Kemas.versiSemasa(ctx)) kemas = t
            } catch (_: Exception) {}
        }.start()
    }

    LaunchedEffect(Unit) {
        val t0 = System.currentTimeMillis()
        kesan {
            Thread {
                val sisa = 1300 - (System.currentTimeMillis() - t0)
                if (sisa > 0) Thread.sleep(sisa)
                android.os.Handler(android.os.Looper.getMainLooper()).post { mula = false }
            }.start()
        }
    }
    LaunchedEffect(tab, ralat) {
        while (tab == 0 && ralat == null) {
            web.evaluateJavascript("try{var m=document.querySelector('main');VPApp.d('wv=$tinggiWv'+'px vh='+innerHeight+' app='+document.getElementById('app').offsetHeight+' main='+(m?m.clientHeight:-1)+' dpr='+devicePixelRatio)}catch(e){}", null)
            web.evaluateJavascript("fetch('/api/status').then(function(r){return r.json()}).then(function(j){VPApp.s(JSON.stringify(j))}).catch(function(){})", null)
            kotlinx.coroutines.delay(8000)
        }
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
                    NavigationBarItem(selected = tab == 0, onClick = { haptik.performHapticFeedback(HapticFeedbackType.TextHandleMove); tab = 0 }, colors = warna,
                        icon = { Icon(Icons.Filled.Home, null) }, label = { Text("VLESS Pro") })
                    NavigationBarItem(selected = tab == 1, onClick = { haptik.performHapticFeedback(HapticFeedbackType.TextHandleMove); tab = 1 }, colors = warna,
                        icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Urus") })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            // Panel kekal hidup di belakang supaya sesi tidak hilang bila tukar tab.
            Column(Modifier.fillMaxSize()) {
                if (tab == 0 && ralat == null) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFF16161A)).padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(
                            when (vlessHidup) { true -> Color(0xFF4ADE80); false -> Color(0xFFF59E0B); null -> Color(0xFF6B7280) }))
                        Column(Modifier.weight(1f)) {
                            Text("${edisi.nama} · $ip", color = Color(0xFFE9EBF2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            val ringkas = listOf(
                                when (vlessHidup) { true -> if (nodNama.isNotEmpty()) "VLESS aktif: $nodNama" else "VLESS aktif"; false -> "VLESS tidak aktif"; null -> "Menyemak ..." },
                                lesenTeks).filter { it.isNotEmpty() }.joinToString("  ·  ")
                            Text(ringkas, color = Color(0xFF9AA0A6), fontSize = 11.sp, maxLines = 1)
                            if (diag.isNotEmpty()) Text(diag, color = Color(0xFF6B7280), fontSize = 9.sp, maxLines = 1)
                        }
                        TextButton(onClick = { haptik.performHapticFeedback(HapticFeedbackType.LongPress); web.reload() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Muat semula", color = Color(0xFF4ADE80), fontSize = 12.sp) }
                    }
                }
                AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { (web.parent as? android.view.ViewGroup)?.removeView(web); web },
                    update = { it.visibility = if (tab == 0) android.view.View.VISIBLE else android.view.View.INVISIBLE })
            }
            if (tab == 0 && ralat != null) {
                Column(Modifier.fillMaxSize().background(Color(0xFF111114)).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VLESS Pro", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Tidak dapat sambung ke modem ($ip).", color = Color(0xFFF87171), fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1D29)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Semak senarai ini:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("1. Telefon disambung ke WiFi modem (bukan data sim)", color = Color(0xFFC9D4E0), fontSize = 13.sp)
                        Text("2. Modem sudah dihidupkan sepenuhnya", color = Color(0xFFC9D4E0), fontSize = 13.sp)
                        Text("3. VLESS Pro sudah dipasang (tab Urus)", color = Color(0xFFC9D4E0), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { ralat = null; web.loadUrl(panelUrl()) }, modifier = Modifier.fillMaxWidth()) { Text("Cuba lagi") }
                    OutlinedButton(enabled = !mencari, onClick = { kesan { } }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (mencari) "Mencari modem ..." else "Cari modem automatik", color = Color.White)
                    }
                    OutlinedButton(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Pasang VLESS Pro / tukar modem", color = Color.White) }
                }
            }
            if (tab == 1) {
                Pemasangan(edisi, ip,
                    { e -> edisi = e; ip = e.ipLalai; simpan() },
                    { ip = it; simpan() },
                    { _ -> simpan(); ralat = null; web.loadUrl(panelUrl()); tab = 0 })
            }
            if (mula) Splash()
        }
    }
}

@Composable
fun Splash() {
    val t = rememberInfiniteTransition(label = "s")
    val g by t.animateFloat(0f, 1000f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "g")
    Box(Modifier.fillMaxSize().background(Color(0xFF111114)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VLESS PRO", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp,
                style = TextStyle(brush = Brush.linearGradient(
                    listOf(Color(0xFF38BDF8), Color(0xFF4ADE80), Color(0xFFFACC15), Color(0xFFFB923C), Color(0xFFF472B6), Color(0xFF38BDF8)),
                    start = androidx.compose.ui.geometry.Offset(g - 600f, 0f), end = androidx.compose.ui.geometry.Offset(g, 0f),
                    tileMode = androidx.compose.ui.graphics.TileMode.Mirror)))
            Spacer(Modifier.height(14.dp))
            Text("Mencari modem anda ...", color = Color(0xFF9AA0A6), fontSize = 13.sp)
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
