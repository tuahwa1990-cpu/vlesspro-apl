package com.vlesspro.pemasang

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Profil SSH: SNI diisi = bungkus TLS; payload diisi = hantar payload HTTP/WebSocket sebelum SSH. */
class SshProfil(val nama: String, val host: String, val port: Int, val pengguna: String, val kata: String, val sni: String, val payload: String)

object SshPautan {
    private fun dec(s: String) = try { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { s }

    fun bina(p: SshProfil): String {
        fun e(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val q = buildList {
            if (p.sni.isNotBlank()) add("sni=" + e(p.sni))
            if (p.payload.isNotBlank()) add("payload=" + e(p.payload))
        }.joinToString("&")
        return "ssh://" + e(p.pengguna) + ":" + e(p.kata) + "@" + p.host + ":" + p.port + (if (q.isNotEmpty()) "?$q" else "") + "#" + e(p.nama)
    }

    fun hurai(pautan: String): SshProfil {
        var r = pautan.trim().removePrefix("ssh://")
        val nama = dec(r.substringAfter('#', ""))
        r = r.substringBefore('#')
        val query = r.substringAfter('?', "")
        r = r.substringBefore('?').trimEnd('/')
        val ui = r.substringBeforeLast('@', "")
        val hp = r.substringAfterLast('@')
        val host = hp.substringBeforeLast(':').trim('[', ']')
        val port = hp.substringAfterLast(':', "22").toIntOrNull() ?: 22
        val pengguna = dec(ui.substringBefore(':'))
        val kata = dec(ui.substringAfter(':', ""))
        if (host.isEmpty() || pengguna.isEmpty()) throw IllegalArgumentException(Bhs.t("Pautan tidak sah."))
        val q = query.split('&').filter { it.contains('=') }.associate { dec(it.substringBefore('=')) to dec(it.substringAfter('=')) }
        return SshProfil(nama.ifEmpty { "$host:$port" }, host, port, pengguna, kata, q["sni"].orEmpty(), q["payload"].orEmpty())
    }

    const val PAYLOAD_WS = "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]"
}

/** Cipta soket ke pelayan SSH: TCP biasa, TLS (SNI), dan/atau payload HTTP sebelum protokol SSH bermula. */
class SshKilang(private val p: SshProfil) : com.jcraft.jsch.SocketFactory {
    private fun payload(): String = p.payload
        .replace("[crlf]", "\r\n").replace("[lf]", "\n").replace("[cr]", "\r")
        .replace("\\r\\n", "\r\n").replace("\\n", "\n")
        .replace("[host]", p.host).replace("[port]", p.port.toString())
        .replace("[protocol]", "HTTP/1.1").replace("[ssh]", p.host + ":" + p.port)

    override fun createSocket(host: String, port: Int): Socket {
        var s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(p.host, p.port), 10000)
        s.soTimeout = 12000
        if (p.sni.isNotBlank()) {
            val semua = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            })
            val ctx = SSLContext.getInstance("TLS"); ctx.init(null, semua, null)
            val ss = ctx.socketFactory.createSocket(s, p.sni, p.port, true) as SSLSocket
            try { val pr = ss.sslParameters; pr.serverNames = listOf(SNIHostName(p.sni)); ss.sslParameters = pr } catch (_: Exception) {}
            ss.startHandshake()
            s = ss
        }
        if (p.payload.isNotBlank()) {
            s.getOutputStream().apply { write(payload().toByteArray(Charsets.ISO_8859_1)); flush() }
            // Baca balasan HTTP sehingga baris kosong, bait demi bait supaya tajuk SSH tidak terbaca sama.
            val i = s.getInputStream()
            val b = StringBuilder()
            while (b.length < 8192 && !b.endsWith("\r\n\r\n") && !b.endsWith("\n\n")) {
                val c = i.read()
                if (c < 0) throw java.io.IOException(Bhs.t("Pelayan menutup sambungan selepas payload."))
                b.append(c.toChar())
            }
        }
        s.soTimeout = 0
        return s
    }
    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
}

/** SOCKS5 tempatan (127.0.0.1) yang menyalurkan sambungan melalui sesi SSH (direct-tcpip). Xray menyambung ke sini. */
class SshTerowong(private val p: SshProfil, private val portSocks: Int) {
    @Volatile private var sesi: Session? = null
    @Volatile private var hidup = false
    private var pelayan: ServerSocket? = null
    val sihat get() = hidup && sesi?.isConnected == true

    fun mula() {
        val s = JSch().getSession(p.pengguna, p.host, p.port)
        s.setPassword(p.kata)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.setSocketFactory(SshKilang(p))
        s.setServerAliveInterval(15000)
        s.setServerAliveCountMax(3)
        s.connect(20000)
        sesi = s
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), portSocks), 128)
        pelayan = ss
        hidup = true
        Thread {
            while (hidup) {
                val c = try { ss.accept() } catch (_: Exception) { break }
                Thread { layan(c) }.start()
            }
        }.start()
    }

    fun henti() {
        hidup = false
        try { pelayan?.close() } catch (_: Exception) {}
        pelayan = null
        try { sesi?.disconnect() } catch (_: Exception) {}
        sesi = null
    }

    private fun baca(i: InputStream, n: Int): ByteArray {
        val b = ByteArray(n); var o = 0
        while (o < n) { val r = i.read(b, o, n - o); if (r < 0) throw java.io.EOFException(); o += r }
        return b
    }

    private fun layan(c: Socket) {
        var ch: ChannelDirectTCPIP? = null
        try {
            c.tcpNoDelay = true
            val ci = c.getInputStream(); val co = c.getOutputStream()
            val h = baca(ci, 2)
            baca(ci, h[1].toInt() and 0xff)
            co.write(byteArrayOf(5, 0)); co.flush()
            val r = baca(ci, 4)
            val host = when (r[3].toInt()) {
                1 -> InetAddress.getByAddress(baca(ci, 4)).hostAddress ?: ""
                3 -> String(baca(ci, baca(ci, 1)[0].toInt() and 0xff), Charsets.ISO_8859_1)
                4 -> InetAddress.getByAddress(baca(ci, 16)).hostAddress ?: ""
                else -> ""
            }
            val pb = baca(ci, 2)
            val port = ((pb[0].toInt() and 0xff) shl 8) or (pb[1].toInt() and 0xff)
            if (r[1].toInt() != 1 || host.isEmpty()) { co.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0)); co.flush(); c.close(); return }
            val se = sesi
            if (se == null || !se.isConnected) { co.write(byteArrayOf(5, 1, 0, 1, 0, 0, 0, 0, 0, 0)); co.flush(); c.close(); return }
            val k = se.openChannel("direct-tcpip") as ChannelDirectTCPIP
            ch = k
            k.setHost(host); k.setPort(port)
            val ki = k.inputStream; val ko = k.outputStream
            try { k.connect(10000) } catch (_: Exception) { co.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)); co.flush(); c.close(); return }
            co.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); co.flush()
            val t = Thread {
                try { val b = ByteArray(16384); while (true) { val n = ci.read(b); if (n < 0) break; ko.write(b, 0, n); ko.flush() } } catch (_: Exception) {}
                try { k.disconnect() } catch (_: Exception) {}
            }
            t.start()
            try { val b = ByteArray(16384); while (true) { val n = ki.read(b); if (n < 0) break; co.write(b, 0, n); co.flush() } } catch (_: Exception) {}
        } catch (_: Exception) {
        } finally {
            try { ch?.disconnect() } catch (_: Exception) {}
            try { c.close() } catch (_: Exception) {}
        }
    }
}
