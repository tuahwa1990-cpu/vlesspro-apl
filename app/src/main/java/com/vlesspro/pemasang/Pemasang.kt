package com.vlesspro.pemasang

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

enum class Edisi(val nama: String, val ipLalai: String) {
    B618("Huawei B618-65D", "192.168.8.1"),
    AW1000("AW1000 / AW1000 Ultra", "192.168.1.1")
}

class Hasil(val ok: Boolean, val id: String = "", val panel: String = "")

/** Aliran data yang melaporkan kemajuan penghantaran ke modem. */
class Kira(private val d: ByteArray, nama: String) : ByteArrayInputStream(d) {
    private val nm = nama
    init { Pemasang.mulaKemajuan("hantar", nm, 0f) }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        Pemasang.mulaKemajuan("hantar", nm, if (d.isEmpty()) 1f else pos.toFloat() / d.size)
        return n
    }
    override fun close() { Pemasang.tamatKemajuan(); super.close() }
}

/** Aliran fail yang melaporkan kemajuan penghantaran ke modem. */
class KiraFail(private val f: File, private val nm: String) : FilterInputStream(FileInputStream(f)) {
    private var baca = 0L
    init { Pemasang.mulaKemajuan("hantar", nm, 0f) }
    override fun read(): Int { val n = super.read(); if (n >= 0) lapor(1); return n }
    override fun read(b: ByteArray, off: Int, len: Int): Int { val n = super.read(b, off, len); if (n > 0) lapor(n); return n }
    private fun lapor(n: Int) { baca += n; Pemasang.mulaKemajuan("hantar", nm, if (f.length() == 0L) 1f else (baca.toFloat() / f.length()).coerceAtMost(1f)) }
    override fun close() { Pemasang.tamatKemajuan(); super.close() }
}

class Ssh(ip: String, user: String, pass: String) : AutoCloseable {
    private val s: Session

    init {
        s = JSch().getSession(user, ip, 22)
        if (pass.isNotEmpty()) s.setPassword(pass)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey")
        s.setConfig("server_host_key", "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa,ssh-dss")
        s.setConfig("PubkeyAcceptedAlgorithms", "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa")
        s.setConfig("kex", "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1")
        val ciphers = "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com,aes128-cbc,aes192-cbc,aes256-cbc,3des-cbc"
        s.setConfig("cipher.s2c", ciphers)
        s.setConfig("cipher.c2s", ciphers)
        val macs = "hmac-sha2-256,hmac-sha2-512,hmac-sha1,hmac-md5"
        s.setConfig("mac.s2c", macs)
        s.setConfig("mac.c2s", macs)
        s.connect(10000)
    }

    /** Jalankan arahan. Kembali (kod keluar, output). Tamat masa dalam saat. */
    fun run(cmd: String, input: InputStream? = null, timeoutSec: Int = 120): Pair<Int, String> {
        val ch = s.openChannel("exec") as ChannelExec
        ch.setCommand(cmd)
        ch.setInputStream(input ?: ByteArrayInputStream(ByteArray(0)))
        val out = ByteArrayOutputStream()
        ch.setOutputStream(out)
        ch.setErrStream(out)
        ch.connect(10000)
        val tamat = System.currentTimeMillis() + timeoutSec * 1000L
        while (!ch.isClosed && System.currentTimeMillis() < tamat) Thread.sleep(100)
        val kod = if (ch.isClosed) ch.exitStatus else 0
        ch.disconnect()
        return kod to out.toString("UTF-8")
    }

    fun put(path: String, data: ByteArray, mode: String = "755") {
        val dir = path.substringBeforeLast('/')
        val (kod, o) = run(
            "mkdir -p '$dir' && cat > '$path.tmp' && chmod $mode '$path.tmp' && { rm -f '$path'; mv '$path.tmp' '$path'; }",
            Kira(data, path.substringAfterLast('/')), 900
        )
        if (kod != 0) throw RuntimeException(Bhs.f("Gagal menghantar {0} ke modem: {1}", path, o))
    }

    fun putFail(path: String, f: File, mode: String = "755") {
        val dir = path.substringBeforeLast('/')
        val (kod, o) = run(
            "mkdir -p '$dir' && cat > '$path.tmp' && chmod $mode '$path.tmp' && { rm -f '$path'; mv '$path.tmp' '$path'; }",
            KiraFail(f, path.substringAfterLast('/')), 900
        )
        if (kod != 0) throw RuntimeException(Bhs.f("Gagal menghantar {0} ke modem: {1}", path, o))
    }

    override fun close() {
        try { s.disconnect() } catch (_: Exception) {}
    }
}

object Pemasang {
    private const val BASE = "https://raw.githubusercontent.com/tuahwa1990-cpu/vless-pro-b618/main"
    private const val XRAY = "https://github.com/XTLS/Xray-core/releases/download/v26.3.27/"

    /** Kemajuan semasa: jenis ("muat"/"hantar"), nama fail, nisbah 0..1 (-2 jika tidak diketahui), -1 jika tiada. */
    var kJenis by mutableStateOf("")
    var kNama by mutableStateOf("")
    var kNisbah by mutableStateOf(-1f)

    fun mulaKemajuan(jenis: String, nama: String, nisbah: Float) { kJenis = jenis; kNama = nama; kNisbah = nisbah }
    fun tamatKemajuan() { kNisbah = -1f }

    /** Muat turun dengan cuba semula (sehingga 3 kali) jika sambungan terputus, contohnya semasa VLESS bertukar laluan. */
    fun muat(url: String): ByteArray {
        var salah: Exception? = null
        for (cuba in 1..3) {
            try { return muat1(url) } catch (e: java.io.IOException) {
                salah = e
                if (cuba < 3) Thread.sleep(1500L * cuba)
            }
        }
        throw salah ?: RuntimeException("muat turun gagal")
    }

    private fun muat1(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Connection", "close") // elak guna semula sambungan lama yang sudah ditutup
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.instanceFollowRedirects = true
        if (c.responseCode != 200) throw RuntimeException(Bhs.f("Muat turun gagal (HTTP {0}): {1}", c.responseCode, url))
        val jum = c.contentLengthLong
        val nama = url.substringAfterLast('/').substringBefore('?')
        val out = ByteArrayOutputStream()
        try {
            mulaKemajuan("muat", nama, if (jum > 0) 0f else -2f)
            c.inputStream.use { i ->
                val b = ByteArray(32768)
                while (true) {
                    val n = i.read(b)
                    if (n < 0) break
                    out.write(b, 0, n)
                    if (jum > 0) kNisbah = (out.size().toFloat() / jum).coerceAtMost(1f)
                }
            }
        } finally { tamatKemajuan() }
        return out.toByteArray()
    }

    fun sha(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun semak(sums: String, nama: String, data: ByteArray) {
        val dijangka = sums.lines().map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && it[1] == nama }?.get(0)
        if (dijangka == null || dijangka != sha(data)) throw RuntimeException(Bhs.f("Semakan fail {0} tidak sepadan. Dibatalkan.", nama))
    }

    /** Muat turun zip Xray dan ekstrak ke fail sementara (bukan memori), supaya telefon RAM kecil tidak ranap. */
    private fun xray(arkib: String): File {
        val zip = muat(XRAY + arkib)
        val out = File.createTempFile("xray", ".bin")
        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "xray") {
                        out.outputStream().use { z.copyTo(it) }
                        return out
                    }
                    e = z.nextEntry
                }
            }
        } catch (e: Exception) { out.delete(); throw e }
        out.delete()
        throw RuntimeException(Bhs.t("Fail xray tiada dalam zip."))
    }

    /** Hantar fail Xray ke modem dan padam fail sementara. */
    private fun hantarXray(ssh: Ssh, tujuan: String, arkib: String) {
        val f = xray(arkib)
        try { ssh.putFail(tujuan, f) } finally { f.delete() }
    }

    private fun cari(teks: String, corak: String): String? =
        Regex(corak).find(teks)?.groupValues?.get(1)

    fun pasang(edisi: Edisi, ip: String, user: String, pass: String, suisBoot: Boolean, web: Boolean, log: (String) -> Unit): Hasil {
        log(Bhs.t("Menyambung ke") + " $ip ...")
        Ssh(ip, user, pass).use { ssh ->
            // Tanpa web: edisi apl sahaja (tiada halaman web langsung dalam program). Dengan web: edisi biasa.
            val ed = if (web) BASE else "$BASE/aplsahaja"
            log(Bhs.t(if (web) "Edisi: apl dan Chrome." else "Edisi: apl sahaja (tanpa antara muka web)."))
            val r = if (edisi == Edisi.B618) b618(ssh, ip, suisBoot, ed, log) else aw1000(ssh, ip, suisBoot, ed, log)
            if (r.ok && web) {
                val h = if (edisi == Edisi.B618) "/online/vlesspro" else "/data/vlesspro"
                ssh.run("rm -f $h/etc/noweb", null, 30)
            }
            return r
        }
    }

    private fun b618(ssh: Ssh, ip: String, suisBoot: Boolean, ed: String, log: (String) -> Unit): Hasil {
        val h = "/online/vlesspro"
        log(Bhs.t("[1/4] Menyemak modem ..."))
        val x = ssh.run("[ -f /online/xlite/bin/xray ] && echo ada; [ -d $h/etc ] && echo lama; [ -f $h/bin/xray ] && echo vx; true").second
        val lama = x.contains("lama")
        val tiadaXray = !x.contains("vx") // VLESS Pro sentiasa memakai xray sendiri, bukan xray VPN asal
        log(Bhs.t(if (lama) "Pemasangan sedia ada dijumpai: naik taraf (tetapan dan lesen dikekalkan)." else "Pemasangan baharu."))
        log(Bhs.t("[2/4] Memuat turun fail dari GitHub ..."))
        val sums = String(muat("$ed/b618-sha256.txt"))
        val vp = muat("$ed/new/vp")
        val vpro = muat("$ed/new/vpro")
        semak(sums, "vp", vp)
        semak(sums, "vpro", vpro)
        val inst = muat("$BASE/vlesspro/install.sh")
        val upg = muat("$BASE/upgrade.sh")
        log(Bhs.t("Fail disemak (SHA-256 sepadan). Menghantar ke modem ..."))
        if (lama) {
            ssh.put("$h/new/vp", vp)
            ssh.put("$h/new/vpro", vpro)
            ssh.put("$h/upgrade.sh", upg)
        } else {
            ssh.put("$h/install.sh", inst)
            ssh.put("$h/vp", vp)
            ssh.put("$h/bin/vpro", vpro)
        }
        if (tiadaXray) {
            log(Bhs.t("Memasang Xray sendiri untuk VLESS Pro (sekitar 20 MB) ..."))
            hantarXray(ssh, "$h/bin/xray", "Xray-linux-arm32-v7a.zip")
        }
        log(Bhs.t("[3/4] Memasang ..."))
        val o = ssh.run(if (lama) "sh $h/upgrade.sh" else "sh $h/install.sh", null, 180).second
        log(Bhs.baris(o.trim()))
        if (suisBoot) {
            log(Bhs.t("Memasang suis boot ..."))
            log(ssh.run("$h/vp hook-install", null, 60).second.trim().let { Bhs.baris(it) })
        }
        log(Bhs.t("[4/4] Siap."))
        val id = cari(o, "ID Peranti:\\s*([0-9A-Fa-f-]+)") ?: cari(ssh.run("$h/bin/vpro id", null, 30).second, "([0-9A-Fa-f]{4}(?:-[0-9A-Fa-f]{4}){4})").orEmpty()
        val panel = cari(o, "http://([0-9.]+:[0-9]+)") ?: "$ip:8990"
        return Hasil(true, id, panel)
    }

    private fun aw1000(ssh: Ssh, ip: String, suisBoot: Boolean, ed: String, log: (String) -> Unit): Hasil {
        val h = "/data/vlesspro"
        log(Bhs.t("[1/4] Memuat turun pemasang dari GitHub ..."))
        val tgz = muat("$ed/aw1000/install.tgz")
        val sums = String(muat("$ed/aw1000-sha256.txt"))
        semak(sums, "install.tgz", tgz)
        log(Bhs.t("Fail disemak (SHA-256 sepadan)."))
        log(Bhs.t("Membersihkan ruang storan lama pada modem ..."))
        // run/vp.log tumbuh tanpa had (setiap panggilan "vp" ditambah terus); pada storan modem yang
        // kecil ini boleh membesar sehingga tiada ruang tersisa untuk extract pakej baharu, menyebabkan
        // tar gagal menulis separuh jalan. Potong fail log lama dan buang sisa fail sementara dahulu.
        ssh.run(
            "[ -f $h/run/vp.log ] && [ \$(wc -c < $h/run/vp.log 2>/dev/null || echo 0) -gt 262144 ] && " +
                "{ tail -c 65536 $h/run/vp.log > $h/run/vp.log.tmp 2>/dev/null && mv $h/run/vp.log.tmp $h/run/vp.log; }; " +
                "rm -f $h/new/*.tmp $h/*.tmp /data/*.tmp 2>/dev/null; true",
            null, 30
        )
        val ruang = ssh.run("df -k /data 2>/dev/null | tail -n 1 | awk '{print \$4}'", null, 15).second.trim().toLongOrNull()
        if (ruang != null && ruang < 2048) {
            throw RuntimeException(Bhs.f(
                "Ruang storan pada modem tidak mencukupi untuk pasang/naik taraf ({0} KB sahaja tersisa pada /data). " +
                    "Sambung ke modem melalui SSH dan padam fail/log lama yang tidak diperlukan (cth. /data/vlesspro/run/vp.log, cache lama), kemudian cuba lagi.",
                ruang.toString()
            ))
        }
        log(Bhs.t("[2/4] Menghantar fail ke modem ..."))
        val r = ssh.run(
            "[ -f $h/run/ui.pid ] && kill \$(cat $h/run/ui.pid) 2>/dev/null; rm -f $h/bin/vpro; mkdir -p /data && cd /data && tar xzf -",
            Kira(tgz, "install.tgz"), 600
        )
        if (r.first != 0) throw RuntimeException(Bhs.f(
            "Gagal membuka pakej pada modem (mungkin ruang storan tidak mencukupi): {0}", r.second
        ))
        val x = ssh.run("[ -x $h/bin/xray ] || echo tiadaxray").second
        if (x.contains("tiadaxray")) {
            log(Bhs.t("Memasang Xray sendiri untuk VLESS Pro (sekitar 20 MB) ..."))
            hantarXray(ssh, "$h/bin/xray", "Xray-linux-arm64-v8a.zip")
        }
        log(Bhs.t("[3/4] Memasang ..."))
        val o = ssh.run("sh $h/install.sh", null, 180).second
        log(Bhs.baris(o.trim()))
        if (suisBoot) {
            log(Bhs.t("Memasang suis boot ..."))
            log(ssh.run("$h/vp hook-install", null, 60).second.trim().let { Bhs.baris(it) })
        }
        log(Bhs.t("[4/4] Siap."))
        val id = cari(o, "ID Peranti:\\s*([0-9A-Fa-f-]+)").orEmpty()
        val panel = cari(o, "http://([0-9.]+:[0-9]+)") ?: "$ip:8990"
        return Hasil(true, id, panel)
    }

    fun suisBoot(edisi: Edisi, ip: String, user: String, pass: String, log: (String) -> Unit) {
        val h = if (edisi == Edisi.B618) "/online/vlesspro" else "/data/vlesspro"
        Ssh(ip, user, pass).use { log(it.run("$h/vp hook-install", null, 60).second.trim().let { Bhs.baris(it) }) }
    }

    fun status(edisi: Edisi, ip: String, user: String, pass: String, log: (String) -> Unit) {
        val h = if (edisi == Edisi.B618) "/online/vlesspro" else "/data/vlesspro"
        Ssh(ip, user, pass).use {
            log(it.run("$h/vp status", null, 30).second.trim().let { Bhs.baris(it) })
            log(it.run("VP_HOME=$h $h/bin/vpro id", null, 30).second.trim().let { Bhs.baris(it) })
        }
    }

    // Aktifkan kunci lesen pada modem (vpro activate KEY) dan paparkan keadaan lesen.
    fun aktifkanLesen(edisi: Edisi, ip: String, user: String, pass: String, kunci: String, log: (String) -> Unit) {
        val k = kunci.trim().replace(Regex("\\s+"), "")
        if (k.isEmpty()) { log(Bhs.t("Kunci lesen kosong.")); return }
        if (!Regex("[A-Za-z0-9._~+/=-]{20,}").matches(k)) { log(Bhs.t("Kunci lesen tidak sah (ada aksara pelik atau terlalu pendek). Salin semula kunci penuh.")); return }
        val h = if (edisi == Edisi.B618) "/online/vlesspro" else "/data/vlesspro"
        Ssh(ip, user, pass).use {
            val o = it.run("VP_HOME=$h $h/bin/vpro activate '$k'", null, 30).second
            val m = o.lines().mapNotNull { l -> val p = l.split("=", limit = 2); if (p.size == 2) p[0].trim() to p[1].trim() else null }.toMap()
            val st = m["state"].orEmpty()
            val teks = when (st) {
                "active" -> Bhs.f("LESEN AKTIF. Nama: {0}, luput: {1}", m["name"].orEmpty(), m["exp"].orEmpty().ifEmpty { "-" })
                "expired" -> Bhs.t("Lesen ini sudah luput. Minta kunci baharu.")
                "revoked" -> Bhs.t("Lesen ini telah dibatalkan.")
                "" -> Bhs.t("Tiada jawapan daripada modem. Pastikan VLESS Pro sudah dipasang.") + "\n" + o.trim()
                else -> Bhs.f("Lesen tidak diterima (keadaan: {0}). Pastikan kunci dijana untuk ID peranti ini.", st)
            }
            log(teks)
            log(Bhs.baris(o.trim()))
        }
    }
}
