package com.vlesspro.pemasang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0B5FA5))) { Skrin() }
        }
    }
}

@Composable
fun Skrin() {
    val ctx = LocalContext.current
    var edisi by remember { mutableStateOf(Edisi.B618) }
    var ip by remember { mutableStateOf(Edisi.B618.ipLalai) }
    var user by remember { mutableStateOf("root") }
    var pass by remember { mutableStateOf("") }
    var boot by remember { mutableStateOf(true) }
    var sibuk by remember { mutableStateOf(false) }
    var hasil by remember { mutableStateOf<Hasil?>(null) }
    val log = remember { mutableStateListOf<String>() }

    fun kerja(tugas: () -> Unit) {
        if (sibuk) return
        sibuk = true; hasil = null; log.clear()
        Thread {
            try { tugas() } catch (e: Exception) { log.add("GAGAL: ${e.message ?: e.javaClass.simpleName}") }
            sibuk = false
        }.start()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F7FB)).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("VLESS Pro Pemasang", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Pasang atau kemas kini VLESS Pro pada modem anda terus dari telefon, tanpa Termux.", fontSize = 13.sp, color = Color.DarkGray)

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pilih modem", fontWeight = FontWeight.SemiBold)
                Edisi.values().forEach { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = edisi == e, onClick = { edisi = e; ip = e.ipLalai }, enabled = !sibuk)
                        Text(e.nama)
                    }
                }
                OutlinedTextField(ip, { ip = it.trim() }, label = { Text("Alamat IP modem") }, singleLine = true,
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
            kerja {
                val h = Pemasang.pasang(edisi, ip, user, pass, boot) { log.add(it) }
                hasil = h
            }
        }, enabled = !sibuk && ip.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (sibuk) "Sedang berjalan ..." else "Pasang / Kemas kini VLESS Pro")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { kerja { Pemasang.status(edisi, ip, user, pass) { log.add(it) } } },
                enabled = !sibuk && ip.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Semak status") }
            OutlinedButton(onClick = { kerja { Pemasang.suisBoot(edisi, ip, user, pass) { log.add(it) } } },
                enabled = !sibuk && ip.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Pasang suis boot") }
        }
        OutlinedButton(onClick = {
            val a = hasil?.panel ?: "$ip:8990"
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$a")))
        }, modifier = Modifier.fillMaxWidth()) { Text("Buka panel VLESS Pro") }

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
    }
}
