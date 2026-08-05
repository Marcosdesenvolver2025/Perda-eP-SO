# Vendas Itinga

Marketplace C2C local com logística própria, inspirado na experiência da Enjoei.
Branco e verde (`#2BCC3E`), foco em fotos grandes e fluxo sem atrito.

```
vendas-itinga/
├── backend/          API Node.js + Express + Prisma + PostgreSQL
│   ├── prisma/       schema, migrations e seed
│   └── src/
│       ├── config/       variáveis de ambiente (nada hardcoded)
│       ├── controllers/  regras de cada recurso
│       ├── services/     Pagar.me, split, frete, autenticação
│       ├── middlewares/  auth JWT, validação Zod, erros
│       ├── jobs/         liberação de repasses (teste de 4 dias)
│       └── routes/       mapa de endpoints
└── mobile/           App React Native (Expo + Expo Router)
    ├── app/          telas (roteamento por arquivos)
    ├── src/          tema, api, contexts, componentes
    ├── assets/       ícone, splash, adaptive icon
    ├── app.json      configuração do build (android.package etc.)
    └── eas.json      perfis de build (production gera o .aab)
```

---

## 1. Arquitetura

### Visão geral

```
┌──────────────────┐        HTTPS/JWT        ┌──────────────────────┐
│  App (Expo RN)   │ ──────────────────────► │  API (Express)       │
│                  │                         │                      │
│ Google Sign-In   │                         │  Prisma ORM          │
│ card_token ──────┼── chave PÚBLICA ──┐     │       │              │
└──────────────────┘                   │     └───────┼──────────────┘
                                       │             │
                                       ▼             ▼
                            ┌────────────────┐  ┌──────────────┐
                            │   Pagar.me     │  │ PostgreSQL   │
                            │  (split v5)    │  │              │
                            └────────┬───────┘  └──────────────┘
                                     │ webhooks
                                     └────────► POST /webhooks/pagarme
```

**Princípio de segurança:** a chave secreta do Pagar.me nunca sai do servidor.
O app tokeniza o cartão no dispositivo com a chave **pública** e envia só o
`card_token` — os dados do cartão não passam pela nossa API.

### Fluxo de uma venda

1. Comprador finaliza o checkout (PIX ou cartão).
2. A API agrupa a sacola **por vendedor** — cada vendedor vira um pedido próprio,
   com frete e split independentes.
3. Cria o pedido no Pagar.me com as regras de split (`type: flat`, em centavos).
4. Pagamento aprovado → produto marcado como vendido, repasse agendado e, se a
   entrega for da frota, a rota de coleta é criada para o entregador.
5. Entrega confirmada → começa o **período de teste de 4 dias** do comprador.
6. Passados os 4 dias sem contestação, o job libera o repasse ao vendedor.

### Fluxo de reembolso (logística reversa)

Ao pedir reembolso dentro dos 4 dias, o sistema cria imediatamente uma tarefa de
coleta do tipo `RETURN` com a **localização do comprador** e destino no endereço
do vendedor. Quando o entregador conclui a coleta, o estorno é processado.

> A comissão da plataforma **permanece devida pelo vendedor**, cobrindo os custos
> operacionais do processamento e da coleta. Está implementado em
> `refund.controller.js` (`platformFeeRetainedCents`).

---

## 2. Regras de negócio implementadas

| Regra | Valor | Onde |
|---|---|---|
| Valor mínimo por produto | R$ 10,00 | `product.controller.js` + `sell.jsx` |
| Peso máximo | 20 kg | `shipping.service.js` |
| Altura máxima | 60 cm | `shipping.service.js` |
| Comissão — entrega pela frota | 20% | `split.service.js` |
| Comissão — entrega própria | 15% | `split.service.js` |
| Período de teste do comprador | 4 dias | `split.service.js` + `releaseFunds.js` |
| Comissão retida mesmo com reembolso | sim | `refund.controller.js` |

Todos são configuráveis por variável de ambiente (veja `backend/.env.example`),
e o app lê os limites de `GET /config/rules` — mudar a regra não exige nova build.

### Como o split é calculado

```js
// Produto R$ 100,00 + frete R$ 9,90, entrega pela frota (20%)
calculateSplit({ itemsTotalCents: 10000, shippingCents: 990, shippingMode: 'PLATFORM' })
// → vendedor: R$ 80,00 | plataforma: R$ 29,90 (20% + frete) | total: R$ 109,90
```

A comissão é arredondada para baixo, então centavos residuais ficam com o
vendedor e a soma do split **sempre** bate com o total cobrado.

---

## 3. Endpoints da API

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/google` | Login com Google (valida o ID token no servidor) |
| GET | `/auth/me` | Sessão atual |
| PATCH | `/users/me` | Perfil e preferências de notificação |
| POST | `/users/me/seller-account` | Cria o **Recebedor** no Pagar.me |
| GET | `/users/me/balance` | Saldo a receber e já recebido |
| GET/POST/PATCH/DELETE | `/addresses` | Endereços salvos |
| GET | `/products` | Feed com busca, filtros e **proximidade geográfica** |
| POST | `/products` | Cadastro de anúncio (valida R$ 10 / 20 kg / 60 cm) |
| PATCH | `/products/:id/status` | Pausar / reativar anúncio |
| POST | `/products/:id/favorite` | Favoritar |
| GET/POST/DELETE | `/cart` | Sacola (multi-vendedor) |
| POST | `/orders/checkout` | **Checkout com split** (cartão ou PIX) |
| GET | `/orders` · `/orders/sales` | Compras · Painel de vendas |
| PATCH | `/orders/:id/status` | Enviado / entregue / cancelado |
| POST | `/orders/:id/confirm` | Comprador aprova antes dos 4 dias |
| POST | `/orders/:id/refund` | Reembolso + **logística reversa** |
| POST | `/orders/:id/refund/review` | Vendedor aprova ou contesta |
| GET | `/deliveries/available` · `/mine` | Rotas do entregador |
| PATCH | `/deliveries/:id` | Avança o status da rota |
| GET/POST | `/conversations` | Chat comprador ↔ vendedor |
| POST | `/uploads` | Upload de imagem |
| POST | `/webhooks/pagarme` | Confirmação de pagamento (essencial no PIX) |

---

## 4. Rodando o projeto

### Backend

```bash
cd vendas-itinga/backend
cp .env.example .env          # preencha DATABASE_URL, PAGARME_API_KEY, etc.
npm install
npx prisma migrate dev --name init
npm run seed                  # cria as categorias
npm run dev                   # http://localhost:3333
```

Teste rápido: `curl http://localhost:3333/health`

### App

```bash
cd vendas-itinga/mobile
cp .env.example .env          # EXPO_PUBLIC_API_URL e chaves públicas
npm install
npx expo start
```

### Qual endereço usar em `EXPO_PUBLIC_API_URL`

`localhost` dentro do celular aponta para o **próprio celular**, nunca para o
seu computador. Use conforme o caso:

| Onde você roda o app | Endereço da API |
|---|---|
| Emulador Android | `http://10.0.2.2:3333` |
| Simulador iOS | `http://localhost:3333` |
| Celular físico (Expo Go) | `http://SEU_IP_LOCAL:3333` — ex.: `http://192.168.0.10:3333` |
| Produção | `https://api.seudominio.com.br` |

Descubra seu IP com `ipconfig` (Windows) ou `ifconfig | grep inet` (Mac/Linux).
O celular precisa estar no **mesmo Wi-Fi** que o computador.

O app normaliza o valor: se você esquecer o `http://` ou deixar uma barra no
final, ele corrige sozinho. Se ainda assim não conectar, a tela de erro mostra
o endereço em uso e um botão para testar a conexão.

---

## Problemas comuns no desenvolvimento

**O app abre a splash e fecha / trava**
Verifique se você não criou um `app/index.jsx`. A rota `/` já é
`app/(tabs)/index.jsx` — pastas entre parênteses não criam segmento de URL,
então os dois arquivos disputariam o mesmo caminho e o roteador entra em laço.

**Login com Google não abre no Expo Go**
Esperado. Desde o SDK 48 o Expo removeu o proxy de autenticação, então o
Google Sign-In precisa de um **development build** — o Expo Go não consegue
registrar o redirecionamento `com.vendasitinga.app`:

```bash
eas build --platform android --profile development
```

Enquanto isso, use o botão "Ver como funciona" na tela de login para navegar
pelo app sem autenticar.

**Notificações push no Expo Go**
Também exigem development build no Android. O app trata a ausência sem quebrar:
a preferência é salva mesmo quando o token não pode ser gerado.

---

## 5. Serviços externos a configurar

### Pagar.me

1. Crie a conta e pegue as chaves em **Configurações → Chaves de API**.
2. Preencha `PAGARME_API_KEY` (secreta, backend) e
   `EXPO_PUBLIC_PAGARME_PUBLIC_KEY` (pública, app).
3. Crie o **recebedor da plataforma** (a conta que recebe a comissão) e coloque
   o id em `PAGARME_PLATFORM_RECIPIENT_ID`.
4. Cadastre o webhook apontando para `https://SEU_DOMINIO/webhooks/pagarme`,
   com Basic Auth igual a `PAGARME_WEBHOOK_USER` / `PAGARME_WEBHOOK_PASSWORD`.
   Eventos: `order.paid`, `charge.paid`, `charge.payment_failed`,
   `charge.refunded`, `charge.chargedback`.

### Google Sign-In

1. No [Google Cloud Console](https://console.cloud.google.com/), crie
   credenciais **OAuth 2.0** do tipo Web, Android e iOS.
2. No Android, informe o pacote `com.vendasitinga.app` e a **impressão digital
   SHA-1** — obtenha com `eas credentials` após o primeiro build.
3. Coloque os três client IDs em `mobile/.env` e a lista deles em
   `GOOGLE_CLIENT_IDS` no backend.

### Firebase (opcional)

Só é necessário se você preferir autenticar via Firebase Auth em vez do Google
direto. Preencha `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL` e
`FIREBASE_PRIVATE_KEY` no backend — o app pode enviar o ID token do Firebase que
a API valida do mesmo jeito.

---

## 6. Publicar na Google Play

O passo a passo completo, com todos os comandos, está em
**[PUBLICACAO.md](./PUBLICACAO.md)**.

Resumo:

```bash
cd vendas-itinga/mobile
npm install -g eas-cli
eas login
eas build:configure
eas build --platform android --profile production   # gera o .aab
```

---

## 7. Notas de produção

- **Upload de imagens**: hoje as fotos são salvas em disco (`backend/uploads`).
  Para escalar, troque `upload.controller.js` por S3/Cloudinary com URL assinada —
  o app só precisa continuar recebendo a URL final.
- **Busca por proximidade**: o filtro geográfico refina em memória sobre um lote
  do banco. Acima de alguns milhares de anúncios ativos, migre para PostGIS
  (`earth_distance` ou `ST_DWithin`).
- **Chat**: usa polling a cada 12s. Para tempo real, troque por WebSocket/SSE no
  backend — a tela do app não muda.
- **Jobs**: com mais de uma instância da API, use `RUN_JOBS=false` e rode
  `npm run jobs:release` em um worker dedicado, para não duplicar liberações.
