#!/usr/bin/env bash
# Teste de fumaça no emulador: instala, percorre as telas principais e tira prints.
set -u
OUT=${OUT:-smoke-out}
mkdir -p "$OUT"
exec > >(tee -a "$OUT/steps.log") 2>&1
APK=apk/teste.apk
PKG=$(grep -m1 'musibox.applicationId' gradle.properties | cut -d= -f2)
echo "APK=$APK PKG=$PKG"
UI="python3 ci/ui.py"
shot() { sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "print $1"; }
step() { echo "== $1"; }
launch() { adb shell am start -n "$PKG/com.musibox.app.MainActivity" "$@" >/dev/null 2>&1; sleep 3; }

adb logcat -c
adb install -r -g "$APK" || { echo "FALHA AO INSTALAR"; exit 1; }

step "Arquivos de teste"
for i in 1 2 3; do
  ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=$((300*i)):duration=40" -metadata title="Faixa de Teste $i" \
    -metadata artist="Artista Demo" -metadata album="Album Demo" -b:a 128k "/tmp/teste$i.mp3"
  adb push "/tmp/teste$i.mp3" "/sdcard/Music/teste$i.mp3" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Music/teste$i.mp3" >/dev/null
done
mkdir -p /tmp/media
ffmpeg -loglevel error -y -f lavfi -i testsrc=duration=8:size=640x360:rate=24 -f lavfi -i "sine=frequency=500:duration=8" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest /tmp/media/clipe_demo.mp4
adb push /tmp/media/clipe_demo.mp4 /sdcard/Movies/clipe_demo.mp4 >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Movies/clipe_demo.mp4" >/dev/null
# Servidor local (o emulador acessa o computador por 10.0.2.2) para testar um download completo
(cd /tmp/media && python3 -m http.server 8000 >/dev/null 2>&1 &)
sleep 4

step "Primeira abertura"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 6
shot 01_apresentacao
$UI tap "Próximo"; $UI tap "Próximo"; $UI tap "Começar"
shot 02_conta
$UI tap "Continuar sem conta"
$UI wait "músicas encontradas" 40; shot 03_indexacao
$UI tap "Ir para o início"
shot 04_inicio

step "Biblioteca e player"
$UI tapx "Músicas" 10; sleep 2; shot 05_musicas
$UI tap "Faixa de Teste 1"; sleep 4; shot 06_tocando
launch --ez open_player true; sleep 3; shot 07_player
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell cmd statusbar expand-notifications; sleep 3; shot 08_notificacao
adb shell cmd statusbar collapse; sleep 1
launch

step "Download completo (servidor local)"
$UI tapx "Baixar" 10; shot 09_baixar
$UI tap "Cole o link aqui"
adb shell input text "http://10.0.2.2:8000/clipe_demo.mp4"
adb shell input keyevent KEYCODE_ENTER
$UI wait "Opções de download" 90; shot 10_opcoes
$UI tap "Apenas áudio" 5
$UI tap "Galeria do dispositivo" 5
shot 11_escolhas
$UI tap "Baixar agora" 5
sleep 45
$UI tapx "Downloads" 5; sleep 3; shot 12_downloads

step "Download para o cofre pelo Compartilhar"
adb shell am start -n "$PKG/com.musibox.app.share.DownloadShareAlias" -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "Veja http://10.0.2.2:8000/clipe_demo.mp4" >/dev/null 2>&1
$UI wait "Baixar" 60; sleep 3; shot 13_compartilhar
$UI tap "Salvar no cofre" 10
$UI tap "Alta qualidade" 3 || $UI tap "Rápido (" 3 || true
shot 14_compartilhar_cofre
$UI tapx "Baixar" 10
sleep 40

step "Cofre"
launch
$UI tapx "Cofre" 10; sleep 2
for d in 1 2 3 4 5 6; do $UI tapx "$d" 3 >/dev/null; done
sleep 1
for d in 1 2 3 4 5 6; do $UI tapx "$d" 3 >/dev/null; done
sleep 3
$UI tap "Configurações do cofre" 10
$UI tap "Bloquear capturas de tela" 10
shot 15_cofre_config
adb shell input keyevent KEYCODE_BACK; sleep 2
shot 16_cofre_fotos
$UI tap "Vídeos" 5; sleep 2; shot 17_cofre_videos
$UI tap "clipe_demo" 5; sleep 5; shot 18_cofre_player
adb shell input keyevent KEYCODE_BACK; sleep 2

step "Histórico, configurações e armazenamento"
launch
$UI tapx "Mais" 10; shot 19_mais
$UI tap "Histórico" 10; sleep 2; shot 20_historico
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tapx "Configurações" 10; shot 21_configuracoes
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tapx "Armazenamento" 10; sleep 4; shot 22_armazenamento
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tapx "Músicas" 10; sleep 3; shot 23_musicas_depois

step "Rotação"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 3; shot 24_paisagem
adb shell settings put system user_rotation 0; sleep 2

adb logcat -d > "$OUT/logcat.txt"
grep -n "FATAL EXCEPTION" -A 40 "$OUT/logcat.txt" > "$OUT/crashes.txt" || true
grep -E "MusiBox|musibox|habitoou|yt-dlp|YoutubeDL|DownloadWorker" "$OUT/logcat.txt" | grep -E " [EW] " | tail -60 > "$OUT/app-errors.txt" || true
if [ -s "$OUT/crashes.txt" ]; then echo "CRASH ENCONTRADO"; head -60 "$OUT/crashes.txt"; else echo "Nenhum crash."; fi
echo "--- avisos do app"; tail -30 "$OUT/app-errors.txt"
exit 0
