#!/bin/bash
set -x
OUT=shots/out; mkdir -p $OUT
P=br.com.musibox; A=$P/com.musibox.app.MainActivity
cap(){ sleep ${2:-3}; adb exec-out screencap -p > $OUT/$1.png; }
st(){ adb shell am start -n $A "$@"; }
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
adb shell am start -W -n $A; cap 00_first 35
adb shell am force-stop $P; sleep 3
adb shell am start -W -n $A --ez shot_play true --es shot_wall hy_hoi_an; cap 01_home 10
st --es shot_route player; cap 02_player 7
st --es shot_route equalizer; cap 03_eq 4
st --es shot_route car_mode; cap 04_car 4
st --es shot_route home; cap 05_home2 3
st --es shot_route shot_playlist; cap 06_playlist 4
st --es shot_route home --es shot_wall hy_lofoten; cap 07_home_lofoten 4
adb shell input tap 650 2297; cap 08_temas 4
adb shell input tap 540 1567; cap 09_cor 3
adb shell input keyevent BACK; sleep 2
adb shell input tap 430 2297; cap 10_browser 5
st --es shot_route vault; cap 11_vault 4
adb shell input keyevent BACK; sleep 1
st --es shot_route home --es shot_wall hy_hoi_an; sleep 3
adb shell input tap 210 2297; sleep 2
adb shell input swipe 900 484 150 484 500; cap 12_chips 2
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml $OUT/ui.xml || true
adb logcat -d -t 600 '*:E' > $OUT/logcat.txt || true
adb shell wm size > $OUT/wm.txt; adb shell wm density >> $OUT/wm.txt
