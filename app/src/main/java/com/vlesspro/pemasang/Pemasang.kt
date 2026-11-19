package com.vlesspro.pemasang

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
            "mkdir -p '$dir' && cat > '$path.tmp' && chmod $mode '$path.tmp' && mv -f '$path.tmp' '$path'",
            ByteArrayInputStream(data), 900
        )
        if (kod != 0) throw RuntimeException("Gagal menghantar $path ke modem: $o")
    }

    override fun close() {
        try { s.disconnect() } catch (_: Exception) {}
    }
}

object Pemasang {
    private const val BASE = "https://raw.githubusercontent.com/tuahwa1990-cpu/vless-pro-b618/main"
    private const val XRAY = "https://github.com/XTLS/Xray-core/releases/download/v26.3.27/"

    fun muat(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.instanceFollowRedirects = true
        if (c.responseCode != 200) throw RuntimeException("Muat turun gagal (HTTP ${c.responseCode}): $url")
        return c.inputStream.use { it.readBytes() }
    }

    fun sha(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun semak(sums: String, nama: String, data: ByteArray) {
        val dijangka = sums.lines().map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && it[1] == nama }?.get(0)
        if (dijangka == null || dijangka != sha(data)) throw RuntimeException("Semakan fail $nama tidak sepadan. Dibatalkan.")
    }

    private fun xray(arkib: String): ByteArray {
        val zip = muat(XRAY + arkib)
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "xray") return z.readBytes()
                e = z.nextEntry
            }
        }
        throw RuntimeException("Fail xray tiada dalam zip.")
    }

    private fun cari(teks: String, corak: String): String? =
        Regex(corak).find(teks)?.groupValues?.get(1)

    fun pasang(edisi: Edisi, ip: String, user: String, pass: String, suisBoot: Boolean, log: (String) -> Unit): Hasil {
        log("Menyambung ke $ip ...")
        Ssh(ip, user, pass).use { ssh ->
            return if (edisi == Edisi.B618) b618(ssh, ip, suisBoot, log) else aw1000(ssh, ip, suisBoot, log)
        }
    }

    private fun b618(ssh: Ssh, ip: String, suisBoot: Boolean, log: (String) -> Unit): Hasil {
        val h = "/online/vlesspro"
        log("[1/4] Menyemak modem ...")
        val x = ssh.run("[ -f /online/xlite/bin/xray ] && echo ada; [ -d $h/etc ] && echo lama; [ -f $h/bin/xray ] && echo vx; true").second
        val lama = x.contains("lama")
        val tiadaXray = !(x.contains("ada") || x.contains("vx"))
        log(if (lama) "Pemasangan sedia ada dijumpai: naik taraf (tetapan dan lesen dikekalkan)." else "Pemasangan baharu.")
        log("[2/4] Memuat turun fail dari GitHub ...")
        val sums = String(muat("$BASE/b618-sha256.txt"))
        val vp = muat("$BASE/new/vp")
        val vpro = muat("$BASE/new/vpro")
        semak(sums, "vp", vp)
        semak(sums, "vpro", vpro)
        val inst = muat("$BASE/vlesspro/install.sh")
        val upg = muat("$BASE/upgrade.sh")
        log("Fail disemak (SHA-256 sepadan). Menghantar ke modem ...")
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
            log("XLITE tiada: memuat turun Xray (sekitar 15 MB) ...")
            ssh.put("$h/bin/xray", xray("Xray-linux-arm32-v7a.zip"))
        }
        log("[3/4] Memasang ...")
        val o = ssh.run(if (lama) "sh $h/upgrade.sh" else "sh $h/install.sh", null, 180).second
        log(o.trim())
        if (suisBoot) {
            log("Memasang suis boot ...")
            log(ssh.run("$h/vp hook-install", null, 60).second.trim())
        }
        log("[4/4] Siap.")
        val id = cari(o, "ID Peranti:\\s*([0-9A-Fa-f-]+)") ?: cari(ssh.run("$h/bin/vpro id", null, 30).second, "([0-9A-Fa-f]{4}(?:-[0-9A-Fa-f]{4}){4})").orEmpty()
        val panel = cari(o, "http://([0-9.]+:[0-9]+)") ?: "$ip:8990"
        return Hasil(true, id, panel)
    }

    private fun aw1000(ssh: Ssh, ip: String, suisBoot: Boolean, log: (String) -> Unit): Hasil {
        val h = "/data/vlesspro"
        log("[1/4] Memuat turun pemasang dari GitHub ...")
        val tgz = muat("$BASE/aw1000/install.tgz")
        val sums = String(muat("$BASE/aw1000-sha256.txt"))
        semak(sums, "install.tgz", tgz)
        log("Fail disemak (SHA-256 sepadan).")
        log("[2/4] Menghantar fail ke modem ...")
        val r = ssh.run(
            "[ -f $h/run/ui.pid ] && kill \$(cat $h/run/ui.pid) 2>/dev/null; rm -f $h/bin/vpro; mkdir -p /data && cd /data && tar xzf -",
            ByteArrayInputStream(tgz), 600
        )
        if (r.first != 0) throw RuntimeException("Gagal membuka pakej pada modem: ${r.second}")
        val x = ssh.run("[ -x $h/bin/xray ] || [ -x /opt/xlite/bin/xray ] || echo tiadaxray").second
        if (x.contains("tiadaxray")) {
            log("Xray tiada pada modem: memuat turun Xray (sekitar 15 MB) ...")
            ssh.put("$h/bin/xray", xray("Xray-linux-arm64-v8a.zip"))
        }
        log("[3/4] Memasang ...")
        val o = ssh.run("sh $h/install.sh", null, 180).second
        log(o.trim())
        if (suisBoot) {
            log("Memasang suis boot ...")
            log(ssh.run("$h/vp hook-install", null, 60).second.trim())
        }
        log("[4/4] Siap.")
        val id = cari(o, "ID Peranti:\\s*([0-9A-Fa-f-]+)").orEmpty()
        val panel = cari(o, "http://([0-9.]+:[0-9]+)") ?: "$ip:8990"
        return Hasil(true, id, panel)
    }

    fun suisBoot(edisi: Edisi, ip: String, user: String, pass: String, log: (String) -> Unit) {
        val h = if (edisi == Edisi.B618) "/online/vlesspro" else "/data/vlesspro"
        Ssh(ip, user, pass).use { log(it.run("$h/vp hook-install", null, 60).second.trim()) }
    }

    fun status(edisi: Edisi, ip: String, user: String, pass: String, log: (String) -> Unit) {
        val h = if (edisi == Edisi.B618) "/online/vlesspro" else "/data/vlesspro"
        Ssh(ip, user, pass).use {
            log(it.run("$h/vp status", null, 30).second.trim())
            log(it.run("VP_HOME=$h $h/bin/vpro id", null, 30).second.trim())
        }
    }
}
