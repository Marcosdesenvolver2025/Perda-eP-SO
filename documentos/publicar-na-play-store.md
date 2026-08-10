# Publicar o Vendas Itinga na Google Play

Guia do começo ao fim. Faça na ordem — cada etapa depende da anterior.
Tempo total, sem contar a análise da Google: cerca de um dia de trabalho.

---

## Antes de começar, você vai precisar de

| Item | Onde consegue | Custo |
|---|---|---|
| Conta de desenvolvedor Google Play | https://play.google.com/console/signup | US$ 25, uma vez só |
| Conta Expo (EAS) | https://expo.dev | grátis para começar |
| Conta pagar.me com CNPJ | você já tem ✅ | — |
| Projeto no Google Cloud (login com Google) | https://console.cloud.google.com | grátis |
| Servidor para a API | Railway, Render, Fly.io, VPS… | a partir de ~R$ 30/mês |
| Banco PostgreSQL | junto com o servidor acima | incluso, em geral |
| Domínio (vendasitinga.com.br) | Registro.br | ~R$ 40/ano |

> **Sobre a verificação da conta de desenvolvedor:** contas criadas do zero para
> publicar app de organização passam por verificação de identidade e de endereço.
> Comece por aí, porque essa etapa pode levar alguns dias e trava todo o resto.

---

## Etapa 1 — Subir a API

O app não funciona sem servidor. Faça isso primeiro.

```bash
cd servidor
cp .env.exemplo .env      # preencha os valores
npm install
npx prisma migrate deploy # cria as tabelas
npm run build
npm start
```

Preencha o `.env` com:

- `DATABASE_URL` — string do seu PostgreSQL;
- `JWT_SEGREDO` — gere com `openssl rand -hex 32`;
- `PAGARME_CHAVE_SECRETA` — comece com a de teste (`sk_test_...`);
- `PAGARME_RECEBEDOR_PLATAFORMA` — o `recipient_id` da sua conta pagar.me;
- `GOOGLE_CLIENT_ID_WEB` — sai da etapa 2.

No fim desta etapa você deve conseguir abrir `https://api.vendasitinga.com.br/saude`
no navegador e ver `{"ok":true}`.

---

## Etapa 2 — Login com Google

1. Acesse https://console.cloud.google.com e crie um projeto chamado **Vendas Itinga**.
2. Vá em **APIs e serviços → Tela de permissão OAuth**:
   - Tipo: **Externo**
   - Nome do app: Vendas Itinga
   - E-mail de suporte e e-mail do desenvolvedor: os seus
   - Escopos: `email`, `profile`, `openid`
3. Vá em **Credenciais → Criar credenciais → ID do cliente OAuth** e crie **dois**:

   **a) Tipo "Aplicativo da Web"** — é o que o servidor usa para validar o login.
   Guarde o Client ID; ele vai em `GOOGLE_CLIENT_ID_WEB` (servidor) e em
   `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` (app).

   **b) Tipo "Android"** — precisa do nome do pacote e da impressão digital SHA-1:
   - Nome do pacote: `br.com.vendasitinga.app`
   - SHA-1: pegue com `eas credentials` (veja a etapa 4) ou, depois do primeiro
     envio, no Play Console em **Configuração → Assinatura de apps**.

> ⚠️ Se o app subir na Play Store com a assinatura da Google (Play App Signing) e
> você só tiver cadastrado o SHA-1 da chave de upload, **o login vai falhar em
> produção e funcionar no teste**. Cadastre os dois SHA-1: o de upload e o da
> chave de assinatura do app que aparece no Play Console.

---

## Etapa 3 — pagar.me

1. No painel da pagar.me, pegue as chaves em **Configurações → Chaves**:
   - `pk_test_...` → vai em `EXPO_PUBLIC_PAGARME_CHAVE_PUBLICA` (app)
   - `sk_test_...` → vai em `PAGARME_CHAVE_SECRETA` (servidor)
2. Anote o `recipient_id` da sua própria conta (é para lá que vai a comissão) e
   coloque em `PAGARME_RECEBEDOR_PLATAFORMA`.
3. Configure o webhook em **Configurações → Webhooks**:
   - URL: `https://api.vendasitinga.com.br/webhooks/pagarme`
   - Eventos: `order.paid`, `charge.paid`, `charge.payment_failed`,
     `charge.refunded`, `order.canceled`
   - Ative a autenticação básica e coloque usuário e senha em
     `PAGARME_WEBHOOK_USUARIO` / `PAGARME_WEBHOOK_SENHA`.
4. **Teste tudo em homologação antes de trocar para as chaves de produção.**
   Leia `documentos/split-pagarme.md` antes desta etapa.

---

## Etapa 4 — Gerar o app

```bash
cd app
cp .env.exemplo .env      # preencha
npm install
npm install -g eas-cli
eas login
eas init                  # cria o projeto e preenche o projectId em app.json
```

Antes de compilar, ajuste em `app/app.json`:

- `extra.eas.projectId` — preenchido pelo `eas init`;
- o `iosUrlScheme` do plugin do Google Sign-In (só se for lançar no iPhone também).

### Build de teste (APK, instala direto no celular)

```bash
npm run build:teste
```

Ao final o EAS devolve um link. Baixe o APK no seu celular, instale e teste
**tudo**: login, anunciar, comprar com Pix de teste, entregar, pedir devolução.

### Build de produção (AAB, é o que a Play Store aceita)

```bash
npm run build:producao
```

O arquivo `.aab` gerado é o que você envia para o Play Console.

> A Play Store **não aceita mais APK** para apps novos, só o formato Android App
> Bundle (`.aab`). O perfil `producao` do `eas.json` já está configurado assim.

---

## Etapa 5 — Publicar as páginas web

A Google exige que a política de privacidade esteja em uma URL pública e que a
exclusão de conta possa ser pedida **sem instalar o app**. O jeito mais barato é
GitHub Pages:

1. Crie um repositório público, por exemplo `vendasitinga-site`.
2. Copie para lá:
   - `loja/politica-de-privacidade.html` → `privacidade/index.html`
   - `loja/termos-de-uso.html` → `termos/index.html`
   - `loja/exclusao-de-conta.html` → `excluir-conta/index.html`
3. Ative o GitHub Pages nas configurações do repositório.
4. Aponte o domínio `vendasitinga.com.br` para o Pages (ou use a URL do
   `github.io` mesmo — funciona).

**Antes de publicar, troque nos três arquivos:** `[RAZÃO SOCIAL]`, `[SEU CNPJ]` e
`[SEU ENDEREÇO COMPLETO]`. A Google confere se os dados da empresa batem com os
da conta de desenvolvedor.

---

## Etapa 6 — Play Console

### 6.1. Criar o app

**Todos os apps → Criar app**

| Campo | Valor |
|---|---|
| Nome | Vendas Itinga |
| Idioma padrão | Português (Brasil) |
| App ou jogo | App |
| Gratuito ou pago | Gratuito |

### 6.2. Preencher a ficha da loja

Use os textos prontos em `loja/ficha-da-loja.md` e os gráficos em `loja/graficos/`.
Faltam só as capturas de tela — instruções no mesmo arquivo.

### 6.3. Conteúdo do app (a parte que mais reprova)

| Seção | O que responder |
|---|---|
| **Política de privacidade** | `https://vendasitinga.com.br/privacidade` |
| **Acesso ao app** | Marque que exige login e **crie uma conta de teste** para os revisores (e-mail e senha, ou explique o login com Google). Sem isso a análise é reprovada por “não conseguimos acessar”. |
| **Anúncios** | Não contém anúncios |
| **Classificação de conteúdo** | Preencha o questionário. Ver `loja/classificacao-de-conteudo.md` |
| **Público-alvo** | 18 anos ou mais |
| **Segurança dos dados** | Copie as respostas de `loja/seguranca-dos-dados.md` |
| **Apps financeiros** | **Sim, o app processa pagamentos.** Declare que usa a pagar.me (Stone) como instituição de pagamento e tenha o CNPJ à mão |
| **Exclusão de conta** | `https://vendasitinga.com.br/excluir-conta` |
| **Governo / saúde / COVID** | Não |

### 6.4. Enviar a primeira versão

Comece pelo **teste interno** — libera na hora, sem análise, e você instala pela
Play Store de verdade:

**Testes → Teste interno → Criar versão → subir o `.aab`**

Depois de testar com calma, promova para produção:

**Produção → Criar versão → selecionar a versão testada → Enviar para análise**

---

## Etapa 7 — Depois de enviar

- A análise costuma levar de **1 a 7 dias** na primeira versão.
- App novo com pagamento costuma receber uma checagem extra. Se pedirem
  documento da empresa, responda rápido — o relógio para enquanto aguardam.
- Reprovou? A Google diz o motivo em **Painel de políticas**. Corrija e reenvie;
  não custa nada e não tem limite de tentativas.

### Motivos mais comuns de reprovação neste tipo de app

| Motivo | Como evitar |
|---|---|
| Revisor não conseguiu entrar | Forneça conta de teste em **Acesso ao app** |
| Segurança dos dados incompleta | Use `loja/seguranca-dos-dados.md` |
| Falta link de exclusão de conta | Publique `exclusao-de-conta.html` e informe a URL |
| Política de privacidade genérica | A nossa já descreve os dados reais — só preencha os `[COLCHETES]` |
| `targetSdkVersion` desatualizado | Já está em 36 no `app.json`. Confira o mínimo exigido no ano do envio |
| Descrição prometendo o que o app não faz | Os textos prontos descrevem só o que existe |

---

## Etapa 8 — Virar a chave para produção

Depois que a Google aprovar e você testar tudo em homologação:

1. Troque as chaves da pagar.me de `sk_test_`/`pk_test_` para as de produção.
2. Aponte `EXPO_PUBLIC_API_URL` para a API de produção (já está no perfil
   `producao` do `eas.json`).
3. Gere um novo build de produção e envie.
4. **Faça uma compra de verdade, de valor baixo, com o seu próprio cartão**, e
   acompanhe o dinheiro: cobrança → split → retenção de 7 dias → repasse.
   É o único jeito de ter certeza de que o fluxo de dinheiro está correto.

---

## Atualizações futuras

```bash
cd app
# suba a versão em app.json: "version": "1.0.1"
npm run build:producao
npm run enviar:play
```

O `versionCode` é incrementado sozinho (`autoIncrement: true` no `eas.json`).
Toda atualização passa por análise, mas as seguintes costumam sair em horas.
