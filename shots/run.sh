#!/bin/bash
set -x
OUT=shots/out; mkdir -p $OUT
P=br.com.musibox; A=$P/com.musibox.app.MainActivity
clean(){
  for k in 1 2 3; do
    adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
    x=$(adb shell cat /sdcard/u.xml)
    if echo "$x" | grep -q "isn't responding"; then
      b=$(echo "$x" | grep -o 'text="Wait"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
      set -- $b; [ -n "$4" ] && adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 1
    else break; fi
  done
}
tapText(){
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  b=$(adb shell cat /sdcard/u.xml | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ -n "$4" ] && adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
cap(){ sleep ${2:-3}; clean; adb exec-out screencap -p > $OUT/$1.png; adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml > $OUT/$1.xml; }
st(){ adb shell am start -n $A "$@"; }
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb shell settings put global hide_error_dialogs 1 || true
adb install -r -g shots.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1030
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell mkdir -p /sdcard/Music/Demo
for f in shots/songs/*.mp3; do adb push "$f" /sdcard/Music/Demo/ > /dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 10
adb shell am start -W -n $A; sleep 40; clean
adb shell am force-stop $P; sleep 3
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1030
adb shell am start -W -n $A --ez shot_play true --es shot_wall hy_hoi_an; cap 01_home 10
adb shell input swipe 1000 632 150 632 400; sleep 2
tapText "Mais ouvidas"; cap 02_mais_ouvidas 3
st --es shot_route player; cap 04_player 7
st --es shot_route equalizer; cap 05_eq 4
st --es shot_route car_mode; cap 06_car 4
st --es shot_route shot_playlist; cap 07_playlist 4
st --es shot_route themes --es shot_wall hy_lofoten; cap 08_temas 5
tapText "Cor do app"; cap 10_cor 4
adb shell input keyevent BACK; sleep 2
st --es shot_route browser_home; sleep 4; tapText "Desativar"; cap 11_browser 3
st --es shot_route vault --es shot_wall hy_hoi_an; cap 12_vault 6
adb logcat -d -t 400 '*:E' > $OUT/logcat.txt || true
