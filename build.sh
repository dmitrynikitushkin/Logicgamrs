#!/usr/bin/env bash
# Builds dist/dumalki.apk without Android Studio or Gradle.
# Tools (Ubuntu/Debian): sudo apt-get install -y android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner openjdk-17-jdk
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
KEYSTORE="${KEYSTORE:-keystore/dumalki.jks}"
KS_PASS="${KS_PASS:-dumalki}"
KEY_ALIAS="${KEY_ALIAS:-dumalki}"
OUT=build

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" dist

echo "1/6 Ресурсы → R.java"
aapt package -f -m -J "$OUT/gen" -M app/AndroidManifest.xml -S app/res -I "$ANDROID_JAR"

echo "2/6 Компиляция Java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find app/src "$OUT/gen" -name '*.java')

echo "3/6 DEX"
dalvik-exchange --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"

echo "4/6 Упаковка APK"
aapt package -f -M app/AndroidManifest.xml -S app/res -A app/assets -I "$ANDROID_JAR" \
  -0 arsc -0 woff2 -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add -0 arsc unsigned.apk classes.dex >/dev/null)

echo "5/6 Выравнивание"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "6/6 Подпись"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Dumalki" >/dev/null
  echo "   создан новый ключ подписи: $KEYSTORE (сохраните его, чтобы ставить обновления поверх)"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" \
  --out dist/dumalki.apk "$OUT/aligned.apk"
apksigner verify dist/dumalki.apk
echo "Готово: dist/dumalki.apk ($(du -h dist/dumalki.apk | cut -f1))"
