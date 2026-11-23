package com.vlesspro.pemasang

/**
 * Kesan negara pelayan daripada nama/remark nod (VLESS/VMess/Trojan/SS), supaya
 * VPN Telefon boleh kumpul & tapis pelayan ikut negara (gaya Surfshark/ExpressVPN),
 * walaupun nod itu sendiri cuma nod VLESS/SS/Trojan/WireGuard biasa.
 *
 * Tiada rangkaian pelayan Surfshark/ExpressVPN sebenar terlibat di sini — ini hanya
 * mengecam teks nama nod (emoji bendera, kod ISO, atau nama negara) yang biasa
 * digunakan oleh pembekal nod/pautan langganan.
 */
object Negara {
    // kod ISO 3166-1 alpha-2 -> (bendera emoji, nama paparan)
    private val KOD = mapOf(
        "MY" to "Malaysia", "SG" to "Singapura", "ID" to "Indonesia", "TH" to "Thailand",
        "VN" to "Vietnam", "PH" to "Filipina", "KH" to "Kemboja", "LA" to "Laos",
        "MM" to "Myanmar", "BN" to "Brunei",
        "JP" to "Jepun", "KR" to "Korea Selatan", "HK" to "Hong Kong", "TW" to "Taiwan",
        "CN" to "China", "MO" to "Macau", "MN" to "Mongolia",
        "IN" to "India", "PK" to "Pakistan", "BD" to "Bangladesh", "LK" to "Sri Lanka",
        "US" to "Amerika Syarikat", "CA" to "Kanada", "MX" to "Mexico", "BR" to "Brazil",
        "AR" to "Argentina",
        "GB" to "United Kingdom", "UK" to "United Kingdom", "DE" to "Jerman", "FR" to "Perancis",
        "NL" to "Belanda", "BE" to "Belgium", "CH" to "Switzerland", "SE" to "Sweden",
        "NO" to "Norway", "FI" to "Finland", "DK" to "Denmark", "PL" to "Poland",
        "IT" to "Itali", "ES" to "Sepanyol", "PT" to "Portugal", "IE" to "Ireland",
        "AT" to "Austria", "RO" to "Romania", "UA" to "Ukraine", "RU" to "Russia",
        "TR" to "Turki",
        "AU" to "Australia", "NZ" to "New Zealand",
        "AE" to "UAE", "SA" to "Arab Saudi", "QA" to "Qatar", "IL" to "Israel", "EG" to "Mesir",
        "ZA" to "Afrika Selatan", "NG" to "Nigeria",
    )
    private val BENDERA = mapOf(
        "MY" to "🇲🇾", "SG" to "🇸🇬", "ID" to "🇮🇩", "TH" to "🇹🇭", "VN" to "🇻🇳", "PH" to "🇵🇭",
        "KH" to "🇰🇭", "LA" to "🇱🇦", "MM" to "🇲🇲", "BN" to "🇧🇳",
        "JP" to "🇯🇵", "KR" to "🇰🇷", "HK" to "🇭🇰", "TW" to "🇹🇼", "CN" to "🇨🇳", "MO" to "🇲🇴", "MN" to "🇲🇳",
        "IN" to "🇮🇳", "PK" to "🇵🇰", "BD" to "🇧🇩", "LK" to "🇱🇰",
        "US" to "🇺🇸", "CA" to "🇨🇦", "MX" to "🇲🇽", "BR" to "🇧🇷", "AR" to "🇦🇷",
        "GB" to "🇬🇧", "UK" to "🇬🇧", "DE" to "🇩🇪", "FR" to "🇫🇷", "NL" to "🇳🇱", "BE" to "🇧🇪",
        "CH" to "🇨🇭", "SE" to "🇸🇪", "NO" to "🇳🇴", "FI" to "🇫🇮", "DK" to "🇩🇰", "PL" to "🇵🇱",
        "IT" to "🇮🇹", "ES" to "🇪🇸", "PT" to "🇵🇹", "IE" to "🇮🇪", "AT" to "🇦🇹", "RO" to "🇷🇴",
        "UA" to "🇺🇦", "RU" to "🇷🇺", "TR" to "🇹🇷",
        "AU" to "🇦🇺", "NZ" to "🇳🇿",
        "AE" to "🇦🇪", "SA" to "🇸🇦", "QA" to "🇶🇦", "IL" to "🇮🇱", "EG" to "🇪🇬",
        "ZA" to "🇿🇦", "NG" to "🇳🇬",
    )
    // kata kunci nama penuh negara (huruf kecil) -> kod ISO, untuk nod yang namakan negara penuh, bukan kod
    private val KATA_KUNCI = mapOf(
        "malaysia" to "MY", "singapore" to "SG", "singapura" to "SG", "indonesia" to "ID",
        "thailand" to "TH", "vietnam" to "VN", "philippines" to "PH", "filipina" to "PH",
        "japan" to "JP", "jepun" to "JP", "korea" to "KR", "hongkong" to "HK", "hong kong" to "HK",
        "taiwan" to "TW", "china" to "CN",
        "india" to "IN",
        "united states" to "US", "america" to "US", "usa" to "US", "canada" to "CA",
        "united kingdom" to "GB", "britain" to "GB", "england" to "GB", "london" to "GB",
        "germany" to "DE", "jerman" to "DE", "france" to "FR", "perancis" to "FR",
        "netherlands" to "NL", "holland" to "NL", "belanda" to "NL",
        "russia" to "RU", "turkey" to "TR", "turki" to "TR",
        "australia" to "AU",
        "uae" to "AE", "dubai" to "AE", "emirates" to "AE",
    )
    private val RX_BENDERA = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")
    // "[SG]", "SG-01", "SG01", "-SG-" dsb
    private val RX_KOD = Regex("(?:^|[^A-Za-z])([A-Za-z]{2})(?:[^A-Za-z]|$)")

    data class Info(val kod: String, val bendera: String, val nama: String)
    val LAIN = Info("--", "🌐", "Lain-lain")

    fun kesan(namaNod: String?): Info {
        val s = namaNod?.trim().orEmpty()
        if (s.isEmpty()) return LAIN
        RX_BENDERA.find(s)?.let { m ->
            val emoji = m.value
            val kod = emojiKeKod(emoji)
            if (kod != null && KOD.containsKey(kod)) return Info(kod, BENDERA[kod] ?: emoji, KOD[kod]!!)
            return Info(emoji, emoji, s) // bendera dikesan tapi kod tak dikenali — guna emoji itu sendiri sebagai kumpulan
        }
        val lower = s.lowercase()
        for ((kw, kod) in KATA_KUNCI) if (lower.contains(kw)) return Info(kod, BENDERA[kod] ?: "🌐", KOD[kod] ?: kod)
        for (m in RX_KOD.findAll(s)) {
            val kod = m.groupValues[1].uppercase()
            if (KOD.containsKey(kod)) return Info(kod, BENDERA[kod] ?: "🌐", KOD[kod]!!)
        }
        return LAIN
    }

    private fun emojiKeKod(emoji: String): String? {
        if (emoji.length < 4) return null
        val a = Character.codePointAt(emoji, 0) - 0x1F1E6
        val b = Character.codePointAt(emoji, 2) - 0x1F1E6
        if (a !in 0..25 || b !in 0..25) return null
        return "" + ('A' + a) + ('A' + b)
    }
}
