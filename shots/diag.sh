#!/bin/bash
set -x
OUT=shots/diag; mkdir -p $OUT
P=br.com.musibox; A=$P/com.musibox.app.MainActivity
st(){ adb shell am start -n $A "$@"; }
snap(){
  t=$1
  echo "=== $t ===" >> $OUT/timeline.txt
  adb shell dumpsys media_session | grep -E "state=PlaybackState|package=br.com.musibox|active=" | head -6 >> $OUT/timeline.txt
  adb shell dumpsys activity services $P | grep -E "ServiceRecord|isForeground|foregroundId|startRequested|createdFromFg" >> $OUT/timeline.txt
  adb shell dumpsys activity processes $P | grep -E "procState|curProcState|setProcState|frozen" | head -4 >> $OUT/timeline.txt
}
adb shell getprop ro.build.version.sdk > $OUT/sdk.txt
adb install -r -g shots.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb shell mkdir -p /sdcard/Music/Demo
for f in shots/songs/*.mp3; do adb push "$f" /sdcard/Music/Demo/ > /dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 8
adb shell am start -W -n $A; sleep 40
adb shell am force-stop $P; sleep 3
adb logcat -c
st --ez shot_play true; sleep 12
snap "tocando_app_aberto"
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input keyevent 223; sleep 5
snap "tela_desligada_5s"
prev=5
for s in 30 60 75 90 120 150 180; do
  sleep $((s-prev)); prev=$s
  snap "tela_desligada_${s}s"
done
adb logcat -d > $OUT/logcat_full.txt
grep -iE "musibox|app idle|ForegroundService|MediaSession|startForeground|stopForeground|Stopping service|freez|ActivityManager: Killing" $OUT/logcat_full.txt | grep -viE "chatty" | tail -400 > $OUT/logcat_key.txt || true
