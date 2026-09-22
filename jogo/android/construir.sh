#!/usr/bin/env bash
#
# Constrói o APK do Volante sem Gradle e sem o SDK oficial do Google.
#
# Por que não Gradle: o SDK oficial e o Maven do Google moram em
# dl.google.com, que nem sempre está acessível. O Debian/Ubuntu empacota um
# SDK Android compilado do código-fonte, e com ele mais o dexador do Maven
# Central dá para montar o APK na mão — que é o que este script faz.
#
# A sequência é a mesma que o Gradle executaria por baixo:
#
#   javac      .java  → .class
#   dx         .class → classes.dex
#   aapt2      res/ + manifesto + assets → APK sem código
#   zip        junta o classes.dex no APK
#   zipalign   alinha para o Android conseguir mapear direto da memória
#   apksigner  assina (sem assinatura, o Android recusa instalar)
#
# Uso:  ./construir.sh          → APK de teste, assinado com chave local
#       ./construir.sh --limpo  → apaga o que foi construído antes

set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JOGO="$(dirname "$AQUI")"
OBRA="$AQUI/obra"
SAIDA="$AQUI/volante.apk"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DEXADOR="${DEXADOR:-$AQUI/ferramentas/dx.jar}"
CHAVE="$AQUI/chave-de-teste.keystore"
SENHA="volante"

azul() { printf '\033[1;36m%s\033[0m\n' "$*"; }
erro() { printf '\033[1;31m%s\033[0m\n' "$*" >&2; exit 1; }

if [ "${1:-}" = "--limpo" ]; then
  rm -rf "$OBRA" "$SAIDA"
  azul "limpo."
  exit 0
fi

# --- 1. conferir as ferramentas --------------------------------------------
for f in javac aapt2 zipalign apksigner keytool zip java; do
  command -v "$f" >/dev/null || erro "falta '$f' no caminho.
  No Debian/Ubuntu:  sudo apt-get install -y aapt android-sdk-build-tools \\
                       android-sdk-platform-23 apksigner zipalign default-jdk zip"
done
[ -f "$ANDROID_JAR" ] || erro "não achei o android.jar em $ANDROID_JAR
  Instale:  sudo apt-get install -y android-sdk-platform-23
  Ou aponte outro:  ANDROID_JAR=/caminho/android.jar ./construir.sh"
[ -f "$DEXADOR" ] || erro "não achei o dexador em $DEXADOR
  Baixe uma vez:  mkdir -p '$AQUI/ferramentas' && curl -L -o '$DEXADOR' \\
    https://repo1.maven.org/maven2/com/jakewharton/android/repackaged/dalvik-dx/11.0.0_r3/dalvik-dx-11.0.0_r3.jar"

rm -rf "$OBRA"
mkdir -p "$OBRA/classes" "$OBRA/assets/jogo"

# --- 2. o jogo vira conteúdo do aplicativo ----------------------------------
azul "==> copiando o jogo para dentro do aplicativo"
cp "$JOGO/index.html" "$JOGO/estilo.css" "$OBRA/assets/jogo/"
cp -r "$JOGO/src" "$OBRA/assets/jogo/src"
QUANTOS=$(find "$OBRA/assets/jogo" -type f | wc -l)
PESO=$(du -sh "$OBRA/assets/jogo" | cut -f1)
echo "    $QUANTOS arquivos, $PESO"

# --- 3. Java → classes → dex ------------------------------------------------
azul "==> compilando o Java"
# -source/-target 8 porque o dexador não entende bytecode mais novo, e sem
# lambda no código pelo mesmo motivo (lambda vira invokedynamic).
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OBRA/classes" \
  $(find "$AQUI/java" -name '*.java') 2>&1 | grep -v 'obsolete\|Picked up\|^$' || true

azul "==> gerando o classes.dex"
java -cp "$DEXADOR" com.android.dx.command.Main \
  --dex --min-sdk-version=21 --output="$OBRA/classes.dex" "$OBRA/classes" \
  2>&1 | grep -v '^Picked up' || true
[ -f "$OBRA/classes.dex" ] || erro "o dexador não produziu classes.dex"

# --- 4. recursos + manifesto + assets --------------------------------------
azul "==> empacotando recursos, manifesto e assets"
aapt2 compile --dir "$AQUI/res" -o "$OBRA/recursos.zip" >/dev/null
aapt2 link \
  -o "$OBRA/sem-codigo.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$AQUI/AndroidManifest.xml" \
  -A "$OBRA/assets" \
  --auto-add-overlay \
  "$OBRA/recursos.zip"

# --- 5. juntar o código -----------------------------------------------------
azul "==> juntando o código ao pacote"
cp "$OBRA/sem-codigo.apk" "$OBRA/completo.apk"
( cd "$OBRA" && zip -q -u completo.apk classes.dex )

# --- 6. alinhar -------------------------------------------------------------
azul "==> alinhando"
zipalign -f -p 4 "$OBRA/completo.apk" "$OBRA/alinhado.apk"

# --- 7. assinar -------------------------------------------------------------
# Sem assinatura o Android recusa instalar. Esta é uma chave LOCAL, de teste:
# serve para instalar no seu aparelho e não serve para publicar em loja.
if [ ! -f "$CHAVE" ]; then
  azul "==> criando a chave de teste (só desta máquina)"
  keytool -genkeypair -v \
    -keystore "$CHAVE" -alias volante \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$SENHA" -keypass "$SENHA" \
    -dname "CN=Volante, OU=Teste, O=Volante, L=Itinga, ST=MG, C=BR" \
    2>&1 | grep -v '^Picked up' || true
fi

azul "==> assinando"
apksigner sign \
  --ks "$CHAVE" --ks-pass "pass:$SENHA" --key-pass "pass:$SENHA" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$SAIDA" "$OBRA/alinhado.apk" 2>&1 | grep -v '^Picked up' || true

apksigner verify --print-certs "$SAIDA" 2>&1 | grep -v '^Picked up\|WARNING' | head -4

echo
azul "==> pronto: $SAIDA  ($(du -h "$SAIDA" | cut -f1))"
echo "    instale com:  adb install -r '$SAIDA'"
echo "    ou copie o arquivo para o celular e toque nele"
