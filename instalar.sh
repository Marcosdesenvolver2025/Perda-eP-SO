#!/bin/bash
# Instala o MESMO arquivo que o usuário baixa e abre o app.
exec > >(tee install.log) 2>&1
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
echo "abilist: $(adb shell getprop ro.product.cpu.abilist)"
for f in MusiBox.apk MusiBox-celular-antigo.apk; do
  PKG=$("$BT/aapt2" dump packagename "$f")
  ABI=$("$BT/aapt2" dump badging "$f" | sed -n "s/native-code: '\(.*\)'/\1/p")
  adb shell getprop ro.product.cpu.abilist | grep -q "$ABI" || { echo "$f: emulador sem $ABI, pulando"; continue; }
  adb uninstall $PKG > /dev/null 2>&1
  t0=$(date +%s)
  r=$(adb install "$f" 2>&1 | tail -1)
  echo "$f: install=$r em $(( $(date +%s) - t0 ))s"
  case "$r" in Success*) ;; *) continue ;; esac
  adb shell dumpsys package $PKG | grep -m2 -E "versionName|primaryCpuAbi"
  adb logcat -c
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep 15
  echo "pid: $(adb shell pidof $PKG)"
  adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity"
  adb logcat -d | grep -E "FATAL EXCEPTION" -A 15 | head -40
  adb logcat -d | grep -iE "FirebaseApp|FirebaseInitProvider" | head -5
done
adb shell pm list packages | grep -i musibox || true
