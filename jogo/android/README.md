# O Volante como aplicativo Android

O jogo é uma página web. Este diretório a embrulha num APK — um aplicativo de
verdade, com ícone na gaveta, tela cheia e funcionando sem internet.

```bash
cd jogo/android
./construir.sh          # gera volante.apk aqui do lado
adb install -r volante.apk
```

Sem cabo? Copie o `volante.apk` para o celular e toque nele. O Android vai
pedir para liberar "instalar apps desconhecidos" para o app que está abrindo o
arquivo (o Arquivos, o WhatsApp, o navegador — depende de onde você tocou).

---

## O que tem dentro

| Arquivo | O que é |
|---|---|
| `AndroidManifest.xml` | a ficha do aplicativo: nome, ícone, orientação, nenhuma permissão |
| `java/br/com/volante/TelaDoJogo.java` | a única tela: um WebView e o servidor de arquivos interno |
| `res/mipmap-*/ic_launcher.png` | o ícone, gerado por `icone.html` |
| `icone.html` | desenha o ícone com o MESMO código de volante do jogo |
| `construir.sh` | monta o APK do zero, sem Gradle |

O aplicativo **não pede permissão nenhuma**. O jogo inteiro roda dentro do
aparelho: não vai à rede, não lê arquivo seu, não sabe onde você está. Não há o
que vazar porque não há o que coletar.

---

## As duas decisões que fazem isso funcionar

### 1. O jogo é servido, não aberto de arquivo

O caminho óbvio seria apontar o WebView para `file:///android_asset/index.html`.
Não funciona: o jogo usa módulos ES (`import`), e o navegador **recusa** módulo
carregado de `file://`, por regra de origem. E recusa em silêncio — a tela fica
preta e não se sabe por quê.

Então `TelaDoJogo` intercepta cada pedido (`shouldInterceptRequest`) e entrega
os arquivos de `assets/jogo/` como se viessem de `https://volante.local/`. Esse
endereço não existe no ar; só este aplicativo responde por ele. Com uma origem
`https` de verdade os módulos carregam e o `localStorage` — onde fica o seu
progresso — funciona direito.

Detalhe que derruba tudo se errado: o tipo de conteúdo. Um `.js` entregue como
`text/plain` faz o navegador recusar o módulo, de novo calado.

### 2. Sem Gradle, e sem o SDK do Google

O SDK oficial e o Maven do Google moram em `dl.google.com`, que nem sempre está
acessível. Mas o Debian/Ubuntu empacota um SDK Android **compilado do
código-fonte**, e o dexador está no Maven Central. Com os dois dá para montar o
APK na mão, que é o que o `construir.sh` faz — a mesma sequência que o Gradle
executaria por baixo:

```
javac      .java  → .class
dx         .class → classes.dex
aapt2      res/ + manifesto + assets → APK sem código
zip        junta o classes.dex
zipalign   alinha para o Android mapear direto da memória
apksigner  assina (sem assinatura o Android recusa instalar)
```

O que instalar, uma vez:

```bash
sudo apt-get install -y aapt android-sdk-build-tools android-sdk-platform-23 \
                        apksigner zipalign default-jdk zip
mkdir -p ferramentas && curl -L -o ferramentas/dx.jar \
  https://repo1.maven.org/maven2/com/jakewharton/android/repackaged/dalvik-dx/11.0.0_r3/dalvik-dx-11.0.0_r3.jar
```

Duas armadilhas que já custaram caro e estão resolvidas no script:

- **O dexador não entende lambda.** Lambda de Java 8 vira `invokedynamic`, que
  o `dx` não sabe ler. Por isso o código usa classe anônima onde caberia
  lambda — está comentado no ponto exato.
- **`android.jar` é o da API 23.** Ele não conhece valores mais novos, como
  `density` em `configChanges` (que só existe a partir da 24). O `targetSdk`
  declarado no manifesto é 34 — o que vale para o Android moderno aceitar
  instalar — mas o que se pode *usar* no código é o da 23. Para um WebView isso
  não faz falta nenhuma.

---

## A assinatura

O `construir.sh` cria uma chave local na primeira vez
(`chave-de-teste.keystore`) e assina com ela. Isso é o bastante para instalar e
testar no seu aparelho.

**Não serve para publicar na Play Store.** Para publicar você precisa de uma
chave própria, guardada com cuidado — se perder, não dá para atualizar o app
nunca mais. Quando chegar a hora, o guia de publicação do projeto está em
`documentos/publicar-na-play-store.md`.

A chave de teste **não** vai para o repositório (está no `.gitignore`), e nem o
APK: os dois são resultado de construção, não fonte.

---

## Qualidade automática

O mesmo APK roda num celular de mil reais e num de dez mil. O jogo mede quantos
quadros por segundo está conseguindo e, se estiver abaixo de 38, baixa a
qualidade sozinho — mexendo em duas coisas ao mesmo tempo: quantos pixels de
verdade por pixel de tela, e em que resolução o chão é calculado.

Só desce, nunca sobe. Ficar subindo e descendo daria uma imagem piscando entre
duas qualidades, que é pior que ficar na mais baixa.

Dá para forçar no menu **Ajustes → Qualidade da imagem**.

---

## O que ainda não foi testado

O APK foi verificado aqui do jeito que dava sem um aparelho à mão:

- assinatura conferida (`apksigner verify`): esquemas v1, v2 e v3
- identidade conferida (`aapt dump badging`): `br.com.volante`, mínimo API 21,
  alvo API 34, ícone e atividade de abertura no lugar
- e — o que mais importa — **o jogo foi rodado a partir dos arquivos que estão
  dentro do APK**, não da pasta de trabalho: os assets foram extraídos do
  pacote, servidos, e o jogo jogado num navegador simulando celular deitado com
  toque. Volante, pedal, progresso salvo e 57 quadros por segundo, sem um erro.

O que **não** dá para afirmar daqui: como ele se comporta num aparelho de
verdade — o WebView de cada fabricante tem suas manias, e desempenho real só se
mede no aparelho. Se algo estranho acontecer, ligue o celular no computador e
rode `adb logcat -s Volante:D chromium:E` — os erros de JavaScript do jogo saem
por ali com arquivo e linha.
