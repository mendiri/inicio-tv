#!/usr/bin/env bash
# Compila Inicio sin Gradle (aapt + javac + dx + apksigner), con las herramientas de Ubuntu:
#   apt install aapt zipalign apksigner dalvik-exchange android-sdk-platform-23
# En Android Studio basta con abrir esta carpeta y compilar como siempre.
set -euo pipefail
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
M=app/src/main
OUT=build
rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes
# El manifiesto de Gradle no lleva package ni versiones (van en app/build.gradle); aapt sí los necesita.
VC=$(grep -oE "versionCode [0-9]+" app/build.gradle | grep -oE "[0-9]+")
VN=$(grep -oE "versionName '[^']+'" app/build.gradle | cut -d"'" -f2)
sed "s|<manifest |<manifest package=\"com.inicio.tv\" android:versionCode=\"$VC\" android:versionName=\"$VN\" |" $M/AndroidManifest.xml > $OUT/AndroidManifest.xml
aapt package -f -m -J $OUT/gen -M $OUT/AndroidManifest.xml -S $M/res -A $M/assets -I $SDK -F $OUT/base.apk --min-sdk-version 25 --target-sdk-version 28
javac -nowarn -encoding UTF-8 --release 8 -Xlint:-options -cp $SDK -d $OUT/classes $(find $M/java $OUT/gen -name '*.java') 2>&1 | grep -v "bootstrap class path" || true
[ -f $OUT/classes/com/inicio/tv/MainActivity.class ] || { echo "javac falló"; exit 1; }
dalvik-exchange --dex --min-sdk-version=25 --output=$OUT/classes.dex $OUT/classes
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT && aapt add -f unsigned.apk classes.dex >/dev/null)
zipalign -f 4 $OUT/unsigned.apk $OUT/aligned.apk
KS=${KEYSTORE:-$HOME/.inicio-debug.keystore}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias inicio \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Inicio TV" >/dev/null 2>&1
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out $OUT/Inicio.apk $OUT/aligned.apk
apksigner verify $OUT/Inicio.apk && ls -la $OUT/Inicio.apk
