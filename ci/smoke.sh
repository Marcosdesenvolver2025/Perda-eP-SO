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
# Sem animações do sistema: o uiautomator só lê a tela quando ela fica parada.
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0
adb install -r -g "$APK" || { echo "FALHA AO INSTALAR"; exit 1; }

step "Arquivos de teste"
# Músicas em pastas diferentes (Music, Download e pasta de outro app), como num celular real
adb shell mkdir -p "/sdcard/snaptube/download/SnapTube\ Audio"
i=0
for dest in "/sdcard/Music/teste1.mp3" "/sdcard/Music/teste2.mp3" "/sdcard/Download/teste3.mp3" "/sdcard/snaptube/download/SnapTube Audio/teste4.m4a"; do
  i=$((i+1))
  ext="${dest##*.}"
  ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=$((300*i)):duration=40" -metadata title="Faixa de Teste $i" \
    -metadata artist="Artista Demo $i" -metadata album="Album Demo" -b:a 128k "/tmp/teste$i.$ext"
  adb push "/tmp/teste$i.$ext" "$dest" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://${dest// /%20}" >/dev/null
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
$UI tapx "Pular" 20; sleep 2
shot 02_conta
$UI tap "Continuar sem conta" 30
$UI wait "músicas encontradas" 40; shot 03_indexacao
$UI tap "Ir para o início"
sleep 3; shot 04_inicio

step "Biblioteca e player"
$UI tap "Faixa de Teste 1" 20; sleep 4; shot 06_tocando
# Deslizar para o lado troca de tela (Início -> Navegador -> Início)
adb shell input swipe 950 1300 120 1300 250; sleep 3; shot 05_configurar_navegador
$UI wait "Configurar seu navegador" 10
adb shell input swipe 900 900 200 900 250; sleep 2; shot 05a_configurar_papel
$UI tapx "Começar a navegar" 10; sleep 6; shot 05b_navegador_inicio
adb shell input swipe 120 1300 950 1300 250; sleep 2; shot 05c_deslize_inicio
$UI tapx "Cantores" 5; sleep 2; shot 05d_cantores
$UI tapx "Áudios" 5; sleep 2; shot 05e_audios
$UI tapx "Todas" 5; sleep 1
launch --ez open_player true; sleep 3; shot 07_player
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell cmd statusbar expand-notifications; sleep 3; shot 08_notificacao
adb shell cmd statusbar collapse; sleep 1
launch

step "Navegador (página inicial, notícias, privada e site)"
launch; $UI tapx "Navegador" 10; sleep 6; shot 09_navegador_inicio
adb shell input swipe 540 1700 540 600 400; sleep 3; shot 10_navegador_noticias
adb shell input swipe 540 600 540 1700 400; sleep 1
$UI tap "Privada" 10; sleep 8; shot 11_navegacao_privada
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tapx "Fechar navegação privada" 3 || adb shell input keyevent KEYCODE_BACK; sleep 2
$UI tapx "YouTube" 10; sleep 12; shot 12_site_youtube
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2

step "Papéis de parede e Pró"
launch; $UI tapx "Mais" 10; sleep 2
$UI tap "Papéis de parede" 10; sleep 4; shot 13_papeis_loja
adb shell input swipe 540 1700 540 500 400; sleep 3; shot 13b_papeis_loja_rolada
$UI tap "Supernova" 10; sleep 3; shot 14_papel_supernova
$UI tap "Comprar" 5; sleep 3; shot 14b_comprar_sem_play
$UI tap "Navegador" 5; sleep 2; shot 14c_previa_navegador
adb shell input keyevent KEYCODE_BACK; sleep 2
$UI tap "Névoa Verde" 10; sleep 3
$UI tap "Usar no app" 5; sleep 2; shot 14d_usar_no_app
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2
$UI tapx "Início" 10; sleep 3; shot 14e_inicio_com_tema
$UI tapx "Mais" 10; sleep 2
$UI tap "MusiBox Pró" 10; sleep 3; shot 14f_pro
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tap "Equalizador" 10; sleep 3; shot 14g_equalizador
adb shell input keyevent KEYCODE_BACK; sleep 1
launch; $UI tapx "Navegador" 10; sleep 3
$UI tap "Personalizar navegador" 10; sleep 2; shot 14h_menu_navegador
adb shell input keyevent KEYCODE_BACK; sleep 1

step "Cofre: PIN, código de recuperação e recuperação"
launch
$UI tapx "Cofre" 10; sleep 2
$UI tap "Criar PIN" 5; sleep 1
for d in 1 2 3 4; do $UI tapx "$d" 3 >/dev/null; done
sleep 1
for d in 1 2 3 4; do $UI tapx "$d" 3 >/dev/null; done
sleep 3
$UI tap "Criar código de recuperação" 10; sleep 2
CODE=$($UI text '[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}' | tail -1)
echo "codigo de recuperacao: ${CODE:+(lido)}"
$UI tap "Anotei o código" 5
$UI tap "Concluir" 5; sleep 2
$UI tap "Configurações do cofre" 10
$UI tap "Bloquear capturas de tela" 10
shot 15_cofre_config
adb shell input swipe 540 1700 540 700 300; sleep 2; shot 15a_cofre_config_pro
$UI tap "Recuperação do PIN" 10; sleep 2; shot 15b_recuperacao
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2
# Guarda o vídeo de teste no cofre pelo menu Compartilhar
VID=$(adb shell content query --uri content://media/external/video/media --projection _id 2>/dev/null | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
echo "video id: $VID"
adb shell am start -n "$PKG/com.musibox.app.share.VaultShareAlias" -a android.intent.action.SEND -t video/mp4 \
  --eu android.intent.extra.STREAM "content://media/external/video/media/$VID" >/dev/null 2>&1
$UI wait "Salvar no cofre" 20; shot 15c_compartilhar_cofre
$UI tap "Copiar para o cofre" 10; sleep 5
launch; $UI tapx "Cofre" 10; sleep 2
shot 16_cofre_fotos
$UI tap "Vídeos" 5; sleep 2; shot 17_cofre_videos
$UI tap "clipe_demo" 5; sleep 5; shot 18_cofre_player
adb shell input keyevent KEYCODE_BACK; sleep 2
# Sai do cofre (bloqueia) e recupera o acesso com o código
$UI tapx "Início" 10; sleep 2
$UI tapx "Cofre" 10; sleep 2; shot 18a_cofre_bloqueado
$UI tap "Esqueci o PIN" 10; sleep 1; shot 18b_esqueci_pin
$UI tap "Usar código de recuperação" 10; sleep 2
adb shell input text "$CODE"; sleep 1; shot 18b2_codigo_digitado
$UI tapx "Confirmar" 10; sleep 3
for d in 4 3 2 1; do $UI tapx "$d" 3 >/dev/null; done
sleep 1
for d in 4 3 2 1; do $UI tapx "$d" 3 >/dev/null; done
sleep 3; shot 18c_cofre_recuperado

step "Histórico, configurações e armazenamento"
launch
$UI tapx "Mais" 10; shot 19_mais
$UI tap "Histórico" 10; sleep 2; shot 20_historico
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tapx "Configurações" 10; shot 21_configuracoes
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tapx "Armazenamento" 10; sleep 4; shot 22_armazenamento
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tap "Biblioteca completa" 10; sleep 3; shot 23_biblioteca

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
