#!/usr/bin/env bash
# Sediakan komponen asli Fungsi 2 (VPN telefon): teras Xray + penerowong hev-socks5-tunnel.
# Dijalankan oleh CI sebelum gradle. Keluaran: app/src/main/jniLibs/<abi>/{libxray.so,libhev-socks5-tunnel.so}
set -euo pipefail
XVER="${XVER:-v26.3.27}"
J="$PWD/app/src/main/jniLibs"
T="$(mktemp -d)"
mkdir -p "$J/arm64-v8a" "$J/armeabi-v7a"

echo "== Xray $XVER =="
curl -fsSL -o "$T/a64.zip" "https://github.com/XTLS/Xray-core/releases/download/$XVER/Xray-android-arm64-v8a.zip"
curl -fsSL -o "$T/a32.zip" "https://github.com/XTLS/Xray-core/releases/download/$XVER/Xray-linux-arm32-v7a.zip"
unzip -o -j "$T/a64.zip" xray -d "$T/x64" >/dev/null; cp "$T/x64/xray" "$J/arm64-v8a/libxray.so"
unzip -o -j "$T/a32.zip" xray -d "$T/x32" >/dev/null; cp "$T/x32/xray" "$J/armeabi-v7a/libxray.so"
ls -l "$J"/*/libxray.so

echo "== hev-socks5-tunnel =="
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_ROOT:-}}}"
[ -n "$NDK" ] || { echo "NDK tiada"; exit 1; }
mkdir -p "$T/proj"
git clone --recursive --depth 1 https://github.com/heiher/hev-socks5-tunnel "$T/proj/jni"
(cd "$T/proj/jni" && git log -1 --format='hev %h %cd')
echo "-- rujukan nama kelas JNI (sebelum ubah) --"
grep -rn "PKGNAME\|CLSNAME\|htproxy" "$T/proj/jni/Android.mk" "$T/proj/jni/src/hev-jni.c" 2>/dev/null || true
# Padankan nama kelas JNI dengan com.vlesspro.pemasang.TProxyService
sed -i -E 's#-DPKGNAME=[^ ]+#-DPKGNAME=com/vlesspro/pemasang#; s#-DCLSNAME=[^ ]+#-DCLSNAME=TProxyService#' "$T/proj/jni/Android.mk"
grep -rl "hev/htproxy" "$T/proj/jni" --include=*.mk --include=*.c --include=*.h 2>/dev/null | xargs -r sed -i 's#hev/htproxy#com/vlesspro/pemasang#g'
(cd "$T/proj" && "$NDK/ndk-build" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=jni/Android.mk APP_ABI="arm64-v8a armeabi-v7a" APP_PLATFORM=android-26 -j4)
for a in arm64-v8a armeabi-v7a; do
  cp "$T/proj/libs/$a/libhev-socks5-tunnel.so" "$J/$a/"
  strings "$J/$a/libhev-socks5-tunnel.so" | grep -q "com/vlesspro/pemasang/TProxyService" \
    || { echo "GAGAL: nama kelas JNI tidak dipadankan ($a)"; strings "$J/$a/libhev-socks5-tunnel.so" | grep -i "TProxyService" || true; exit 1; }
done
ls -l "$J"/*/
