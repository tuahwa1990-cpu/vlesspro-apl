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
        window.statusBarColor = 0xFF080B1A.toInt()
        window.navigationBarColor = 0xFF080B1A.toInt()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF22D3EE))) { AppNatif() }
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
