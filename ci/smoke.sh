#!/usr/bin/env bash
# Teste de fumaça no emulador: instala, percorre as telas principais e tira prints.
set -u
OUT=${OUT:-smoke-out}
mkdir -p "$OUT"
APK=apk/teste.apk
PKG=$(aapt2 dump packagename "$APK" 2>/dev/null || true)
[ -z "$PKG" ] && PKG=$(grep -m1 'musibox.applicationId' gradle.properties | cut -d= -f2)
echo "APK=$APK PKG=$PKG"
UI="python3 ci/ui.py"
shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "print $1"; }
step() { echo "== $1"; }

adb logcat -c
adb install -r -g "$APK" || { echo "FALHA AO INSTALAR"; exit 1; }

step "Músicas de teste"
for i in 1 2 3; do
  ffmpeg -loglevel error -f lavfi -i "sine=frequency=$((300*i)):duration=40" -metadata title="Faixa de Teste $i" \
    -metadata artist="Artista Demo" -metadata album="Álbum Demo" -b:a 128k "/tmp/teste$i.mp3"
  adb push "/tmp/teste$i.mp3" "/sdcard/Music/teste$i.mp3" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Music/teste$i.mp3" >/dev/null
done
ffmpeg -loglevel error -f lavfi -i testsrc=duration=6:size=640x360:rate=24 -f lavfi -i "sine=frequency=500:duration=6" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest /tmp/video.mp4
adb push /tmp/video.mp4 /sdcard/Movies/video_teste.mp4 >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Movies/video_teste.mp4" >/dev/null
sleep 5

step "Primeira abertura"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 6
shot 01_apresentacao
$UI tap "Próximo"; $UI tap "Próximo"; shot 02_apresentacao3
$UI tap "Começar"
shot 03_conta
$UI tap "Continuar sem conta"
$UI wait "músicas encontradas" 40; shot 04_indexacao
$UI tap "Ir para o início"
shot 05_inicio

step "Biblioteca e player"
$UI tap "Músicas" 10; shot 06_musicas
$UI tap "Faixa de Teste 1"; sleep 3; shot 07_tocando
$UI tap "Faixa de Teste 1"; sleep 2; shot 08_player
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell cmd statusbar expand-notifications; sleep 3; shot 09_notificacao
adb shell cmd statusbar collapse; sleep 1
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3

step "Baixar"
$UI tap "Baixar" 10; shot 10_baixar
$UI tap "Cole o link aqui"
adb shell input text "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
adb shell input keyevent KEYCODE_ENTER
sleep 25; shot 11_analise
$UI tap "Apenas áudio" 5
$UI tap "Galeria do dispositivo" 5
shot 12_opcoes
$UI tap "Baixar agora" 5
sleep 40; shot 13_download
$UI tap "Downloads" 5; sleep 5; shot 14_downloads

step "Compartilhar link (menu do Android)"
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Veja https://vimeo.com/76979871" -n "$PKG/com.musibox.app.share.DownloadShareAlias" >/dev/null 2>&1
sleep 20; shot 15_compartilhar
adb shell input keyevent KEYCODE_BACK; sleep 2

step "Cofre"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3
$UI tap "Cofre" 10; sleep 2; shot 16_cofre_pin
for d in 1 2 3 4 5 6; do $UI tapx "$d" 3 >/dev/null; done
for d in 1 2 3 4 5 6; do $UI tapx "$d" 3 >/dev/null; done
sleep 2; shot 17_cofre_biometria
$UI tap "Agora não" 5
sleep 2; shot 18_cofre

step "Configurações"
$UI tapx "Mais" 10; shot 19_mais
$UI tap "Configurações" 10; shot 20_configuracoes

step "Rotação"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 3; shot 21_paisagem
adb shell settings put system user_rotation 0; sleep 2

adb logcat -d > "$OUT/logcat.txt"
grep -E "FATAL EXCEPTION|AndroidRuntime" -A 30 "$OUT/logcat.txt" > "$OUT/crashes.txt" || true
if [ -s "$OUT/crashes.txt" ]; then echo "CRASH ENCONTRADO"; head -60 "$OUT/crashes.txt"; else echo "Nenhum crash."; fi
exit 0
