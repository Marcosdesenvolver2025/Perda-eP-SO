# Publicar o Vendas Itinga na Google Play Store

Passo a passo completo, do zero até o app na loja. Todos os comandos são
executados dentro de `vendas-itinga/mobile`.

---

## Antes de começar

Você vai precisar de:

- [ ] **Node.js 18+** instalado (`node -v`)
- [ ] Conta **Expo** gratuita — https://expo.dev/signup
- [ ] Conta de **desenvolvedor Google Play** (US$ 25, pagamento único) —
      https://play.google.com/console/signup
- [ ] O **backend publicado** em um domínio com HTTPS (Render, Railway, Fly.io,
      VPS...). O app da loja não pode apontar para `localhost`.
- [ ] Chaves do **Pagar.me** e do **Google Sign-In** configuradas

> **Importante:** publique o backend primeiro. O endereço dele entra no
> `eas.json` antes de gerar o build de produção.

---

## Passo 1 — Instalar o EAS CLI e entrar na conta

```bash
npm install -g eas-cli
eas login
```

## Passo 2 — Preparar o projeto

```bash
cd vendas-itinga/mobile
npm install
```

## Passo 3 — Apontar o app para a sua API

Abra `eas.json` e troque a URL do perfil `production` pelo domínio real do seu
backend:

```json
"production": {
  "env": {
    "EXPO_PUBLIC_API_URL": "https://api.seudominio.com.br"
  }
}
```

Faça o mesmo com as chaves públicas, preferindo **secrets** (não vão para o Git):

```bash
eas secret:create --scope project --name EXPO_PUBLIC_PAGARME_PUBLIC_KEY --value SUA_CHAVE_PUBLICA_PAGARME
eas secret:create --scope project --name EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID --value 000000-web.apps.googleusercontent.com
eas secret:create --scope project --name EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID --value 000000-android.apps.googleusercontent.com
```

## Passo 4 — Vincular o projeto ao EAS

```bash
eas build:configure
```

Escolha **Android**. Isso preenche automaticamente o `extra.eas.projectId` no
`app.json` (hoje ele está com um valor de exemplo `00000000-...`).

## Passo 5 — Build de teste (APK)

Antes do build de loja, gere um APK e instale no seu celular para conferir que
o login, o feed e o pagamento funcionam com a API de produção:

```bash
eas build --platform android --profile preview
```

Ao terminar, o EAS mostra um link de download e um QR Code.

## Passo 6 — Build de produção (o arquivo .aab da loja)

```bash
eas build --platform android --profile production
```

Na primeira execução o EAS pergunta:

> *Generate a new Android Keystore?* → responda **Yes**

O EAS cria e guarda a chave de assinatura na sua conta. **Nunca perca essa
chave** — sem ela você não consegue atualizar o app depois. Para fazer backup:

```bash
eas credentials
# Android → production → Keystore → Download
```

O build leva de 10 a 25 minutos. Ao final você recebe o link do arquivo
**`.aab`** (Android App Bundle) — é ele que a Play Store aceita.

## Passo 7 — Criar o app no Play Console

1. Acesse https://play.google.com/console
2. **Criar app** e preencha:
   - Nome: `Vendas Itinga`
   - Idioma padrão: Português (Brasil)
   - Tipo: **App**
   - Gratuito
3. Complete o painel **Configurar o app**:
   - Política de privacidade → use o texto de `store/politica-de-privacidade.md`
     (hospede em uma URL pública, ex.: GitHub Pages)
   - Acesso ao app → informe um login de teste, se necessário
   - Classificação de conteúdo → responda o questionário
   - Público-alvo → 18+ (marketplace com pagamentos)
   - Segurança de dados → declare: e-mail, nome, foto de perfil, localização
     aproximada e dados de compra
   - App de finanças? → **não** (você não é a instituição; o Pagar.me processa)

## Passo 8 — Ficha da loja

Use os textos prontos em **`store/ficha-play-store.md`**. Você vai precisar de:

- Ícone 512×512 → gere com `npm run assets` (use `assets/icon.png` redimensionado)
- Gráfico de destaque 1024×500 → já pronto em `store/feature-graphic.png`
- No mínimo 2 capturas de tela do celular → tire com o app rodando
  (Android Studio: `Ctrl+S` no emulador)

## Passo 9 — Enviar o .aab

**Opção A — pelo terminal** (precisa da chave de serviço do Google):

```bash
eas submit --platform android --profile production
```

**Opção B — manual** (mais simples na primeira vez):

1. Baixe o `.aab` do link que o EAS gerou
2. No Play Console: **Versões → Teste interno → Criar nova versão**
3. Faça upload do `.aab`, escreva as notas da versão e envie para revisão

Comece sempre pelo **teste interno**, valide com algumas pessoas e só então
promova para **Produção**.

---

## Publicando atualizações

```bash
eas build --platform android --profile production
eas submit --platform android --profile production
```

O `versionCode` sobe sozinho — o perfil de produção usa `"autoIncrement": true`
com `"appVersionSource": "remote"`. Para mudar a versão visível (ex.: 1.0.0 →
1.1.0), edite `version` no `app.json`.

---

## Problemas comuns

**"Package name already exists"**
Outro app já usa `com.vendasitinga.app`. Troque `android.package` no `app.json`
(ex.: `com.suaempresa.vendasitinga`) e refaça o build.

**Login do Google não funciona no APK, mas funcionava no Expo Go**
Falta cadastrar o SHA-1 do build no Google Cloud Console. Rode `eas credentials`,
copie o SHA-1 e adicione na credencial OAuth do tipo Android.

**"You uploaded an APK, Play Store requires an AAB"**
Você usou o perfil `preview`. Use `--profile production`, que tem
`"buildType": "app-bundle"`.

**App abre e fica na tela branca**
Quase sempre é a API inacessível. Confirme que `EXPO_PUBLIC_API_URL` aponta para
uma URL **https** pública e que ela responde em `/health`.

**Build falha em "Gradle build failed"**
Rode `npx expo-doctor` e corrija o que ele apontar, depois
`npx expo install --fix` para alinhar as versões das bibliotecas.
