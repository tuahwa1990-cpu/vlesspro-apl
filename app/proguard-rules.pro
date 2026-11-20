# Peraturan R8/ProGuard untuk VLESS Pro Pemasang.
# Matlamat: kaburkan nama kelas/kaedah dalam APK supaya lebih sukar dinyahkompil dan ditiru,
# tanpa memecahkan fungsi yang bergantung pada nama kelas tetap (JNI, refleksi).

# ── JNI (hev-socks5-tunnel tun2socks) ──
# Nama kelas dan kaedah native() mesti kekal SAMA seperti dalam hev-jni.c, jika tidak
# System.loadLibrary/JNI gagal mengikat (UnsatisfiedLinkError) dan VPN telefon tak boleh disambung.
-keep class com.vlesspro.pemasang.TProxyService { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ── JSch (com.jcraft.jsch, fork mwiede) ──
# JSch memilih algoritma cipher/kex/mac/kompresi semasa masa jalan dengan memuatkan
# kelas mengikut nama rentetan (refleksi). Jika nama kelas ditukar, rundingan SSH gagal senyap.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# ── zxing (imbas/jana kod QR) ──
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**

# ── org.json ──
# Dibekalkan oleh platform Android; jangan kaburkan supaya serasi dengan kelas sistem.
-keep class org.json.** { *; }

# ── Komponen Android yang dirujuk dalam AndroidManifest.xml ──
# (Perkhidmatan, penerima dan aktiviti sudah dikekalkan secara automatik oleh peraturan
# lalai AGP, tetapi dinyatakan semula di sini untuk jelas dan selamat.)
-keep class com.vlesspro.pemasang.VpnPerkhidmatan { *; }
-keep class com.vlesspro.pemasang.VpnTile { *; }
-keep class com.vlesspro.pemasang.VpnWidget { *; }
-keep class com.vlesspro.pemasang.VpnBoot { *; }
-keep class com.vlesspro.pemasang.ImbasQr { *; }

-dontwarn kotlinx.coroutines.**
