# Versão navegável no navegador

Uma build do app que roda no browser, com dados de exemplo, para clicar nas
telas antes de existir banco, API ou chave da pagar.me.

---

## Rodar na sua máquina

```bash
cd app
npm install
npx expo start --web        # abre em http://localhost:8081
```

Sem `EXPO_PUBLIC_API_URL`, o app entra sozinho em modo demonstração.

Para gerar os arquivos estáticos (o que vai para o Netlify):

```bash
EXPO_PUBLIC_MODO_DEMO=1 npx expo export --platform web
# resultado em app/dist
```

---

## O que é o modo demonstração

Um **servidor falso em memória** que responde às mesmas rotas da API de
verdade. As telas não sabem que estão em demonstração: elas chamam `api()`
como sempre, e quem desvia é o cliente HTTP.

```
tela  →  api('/pedidos/123')  →  src/api/cliente.ts
                                      │
                        MODO_DEMONSTRACAO ligado?
                          │                    │
                         sim                  não
                          ▼                    ▼
              src/demo/servidor.ts        fetch de verdade
              (memória do navegador)      (a API do projeto)
```

### Como fica isolado do código de produção

| Garantia | Como |
|---|---|
| Nenhuma tela importa a demonstração | `src/demo/` só é referenciado por `cliente.ts`, `App.tsx` e `navegacao/index.tsx` |
| Não entra no caminho de produção | `cliente.ts` usa `await import('../demo/servidor')` **dentro** do `if (MODO_DEMONSTRACAO)` |
| Não inventa regra de negócio | comissão, tarifa e limites saem de `src/regras/limites.ts`, o mesmo módulo das telas |
| Não toca em dinheiro | não há checkout, split, repasse, estorno nem webhook em `src/demo/` |
| A tela do roteiro some em produção | registrada atrás de `MODO_DEMONSTRACAO` em `navegacao/index.tsx` |

A flag liga sozinha quando `EXPO_PUBLIC_API_URL` está vazio, e pode ser
forçada com `EXPO_PUBLIC_MODO_DEMO=1`.

### O que o servidor falso cobre

Todas as rotas que o app chama: vitrine e busca, anúncio, simulação de taxas,
criação de pedido, compras e vendas, código de confirmação, entrega do
vendedor, devolução, corridas do entregador (a máquina de estados inteira),
painel do admin, endereços e mensagens.

O estado muda de verdade enquanto você clica — aceitar uma corrida move o
pedido, confirmar o código abre os 7 dias — e volta ao começo quando a página
recarrega ou quando você toca em **recomeçar a demonstração**.

---

## O roteiro

A faixa preta no topo tem o botão **roteiro**, que abre o índice das telas.
Ele existe porque três coisas não estão a um toque de distância na navegação
normal:

1. **o papel do usuário** — a área do entregador e o painel do admin só
   aparecem no menu da conta para quem tem o papel certo. O roteiro troca o
   papel na hora;
2. **as telas da entrega do vendedor** — elas dependem de um pedido em um
   estado específico; o roteiro acha esse pedido e leva direto;
3. **os degraus da tarifa** — a tabela aparece calculada, e os produtos que
   custam um centavo de diferença estão na vitrine.

---

## Os dados de exemplo

Em `src/demo/dados.ts`.

- **24 anúncios**, sendo **10 nos pares de fronteira** da tabela de tarifas
  (R$ 24,99 / R$ 25,00, R$ 49,99 / R$ 50,00, R$ 99,99 / R$ 100,00,
  R$ 199,99 / R$ 200,00, R$ 499,99 / R$ 500,00);
- **4 itens grandes demais** para a entrega da plataforma (geladeira de 62 kg,
  sofá de 1,90 m, guarda-roupa de 2,20 m de altura), anunciados no modo
  vendedor;
- **17 pedidos**, um em cada estado da máquina, nos dois modos de entrega,
  incluindo os três estados novos (`AGUARDANDO_ENTREGA_DO_VENDEDOR`,
  `ENTREGA_DECLARADA`, `DEVOLUCAO_COMBINADA`);
- **10 corridas** cobrindo a máquina de estados inteira, de ida e de volta;
- **3 devoluções** na fila do admin, uma delas no modo vendedor, que é a que
  exige a confirmação do retorno antes do estorno.

As fotos são SVG desenhados na hora, sem rede — a demonstração abre offline.

---

## O que não funciona no navegador

Por limitação da plataforma, não por falta de implementação. Em todos os casos
a tela mostra um aviso e o fluxo continua.

| Recurso | No navegador | No aparelho |
|---|---|---|
| Câmera (foto do pacote) | abre o seletor de arquivos, ou avisa que não dá | câmera de verdade |
| Galeria (fotos do anúncio) | seletor de arquivos | galeria do sistema |
| Upload das fotos | nenhum dos dois — o storage ainda não está ligado (`TODO`) | idem |
| Notificação push | não existe | falta registrar o token com `expo-notifications` |
| Login com Google | o SDK é nativo; a demonstração entra direto | SDK do Google |
| `Alert.alert` | trocado por `window.confirm` em `src/util/dialogo.ts` | diálogo nativo |

---

## O site é um PWA

Instalável no celular e funciona sem rede.

| Peça | Arquivo | O que faz |
|---|---|---|
| manifesto | `app/public/manifest.webmanifest` | nome, ícones, `display: standalone`, tema `#00DF13` |
| ícones | `app/public/icone-{192,512}.png`, `apple-touch-icon.png` | tela de início do Android e do iPhone |
| service worker | `app/public/sw.js` | instalabilidade e funcionamento offline |
| registro | `app/public/index.html` | registra o worker e recarrega quando chega versão nova |
| carimbo de versão | `app/scripts/finalizar-web.mjs` | roda depois do export |

### Como o cache se comporta

| Pedido | Estratégia | Por quê |
|---|---|---|
| abrir / recarregar | rede primeiro, cache como reserva | fica atualizado com rede, abre sem ela |
| `/_expo/static/**`, `/assets/**` | cache primeiro | têm hash no nome, o conteúdo nunca muda |
| o resto | rede, cache como reserva | — |

**Por que existe o `finalizar-web.mjs`:** o nome do cache precisa mudar a cada
deploy, senão o worker antigo continua servindo a versão velha e a atualização
nunca chega. O script carimba o hash do bundle dentro do `sw.js` e monta a
lista de arquivos pré-carregados. Por isso o comando de build é
`npm run build:web`, não `expo export` direto — o export sozinho deixaria os
marcadores `__VERSAO__` e `__BUNDLES__` sem preencher.

O `netlify.toml` também manda `sw.js` e `index.html` com
`max-age=0, must-revalidate`. Sem isso a CDN seguraria o worker antigo e o
app travaria na versão publicada anteriormente.

### Verificado

Service worker registrado, ativado e controlando a página; 11 arquivos na
casca; a página recarregada **em modo offline** abre completa, com vitrine,
preços e navegação. Zero erros de console.

---

## Publicar no Netlify

O `netlify.toml` está na **raiz do repositório**, que é onde o Netlify procura.
Com ele lá, conectar o repositório não pede configuração nenhuma no painel:

| Campo | Valor | De onde vem |
|---|---|---|
| base | `app` | `netlify.toml` |
| comando | `npx expo export --platform web` | idem |
| publicação | `dist` (relativo à base, ou seja `app/dist`) | idem |
| Node | 20 | idem |
| `EXPO_PUBLIC_MODO_DEMO` | `1` | idem |

Tem também o redirect de todas as rotas para o `index.html` — o app usa
react-navigation, não rotas de arquivo, então sem isso qualquer link direto
daria 404.

### Conectando o repositório

1. <https://app.netlify.com> → **Add new site** → **Import an existing project**
2. escolha GitHub e o repositório `Perda-eP-SO`
3. em **Branch to deploy**, escolha `claude/vendas-itinga-app-19ctbw`
4. não mexa em build command nem em publish directory: o `netlify.toml` manda
5. **Deploy**

Cada push na branch republica sozinho.

### Ou pela linha de comando

```bash
cd app
npx expo export --platform web
npx netlify-cli deploy --dir dist --prod
```

Sem instalar nada: gere a pasta `dist` e arraste para
<https://app.netlify.com/drop>.

### Desligar o modo demonstração

São **necessárias as duas coisas** em `[build.environment]` do `netlify.toml`:

1. **remover** `EXPO_PUBLIC_MODO_DEMO`
2. **definir** `EXPO_PUBLIC_API_URL = "https://sua-api.com.br"`

Por quê as duas, e não só uma:

```ts
// app/src/api/cliente.ts
const FORCAR_DEMONSTRACAO = process.env.EXPO_PUBLIC_MODO_DEMO === '1';
export const URL_API = process.env.EXPO_PUBLIC_API_URL ?? '';
export const MODO_DEMONSTRACAO = FORCAR_DEMONSTRACAO || URL_API === '';
```

| O que você faz | Resultado |
|---|---|
| só remove a flag | URL vazia → `URL_API === ''` → **demonstração continua ligada** |
| só define a URL | flag ainda vale `1` e tem precedência → **demonstração continua ligada** |
| remove a flag **e** define a URL | **produção**: o app chama a API de verdade |

É de propósito: as duas travas existem para o site nunca subir apontando para
produção por engano, nem ficar apontando para lugar nenhum.

> As variáveis são lidas em **tempo de build** — o Expo embute o valor no
> bundle. Depois de mudar o `netlify.toml`, é preciso um novo deploy para o
> site mudar de comportamento; trocar a variável sem rebuildar não faz nada.
