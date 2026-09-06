# Vendas Itinga — o que já foi feito

Documento de estado do projeto. Serve para você saber onde está tudo, o que
já funciona, o que falta e **por que** cada decisão foi tomada do jeito que
foi. Leia este antes dos outros.

Última atualização: setembro de 2026.

---

## 1. O que é o aplicativo

Marketplace de compra e venda de produtos novos e usados **dentro de uma
cidade só**: Itinga-MG. Quem mora ali anuncia o que não usa mais, quem quer
comprar encontra pertinho de casa, e a entrega pode ser feita por entregadores
da própria operação.

A referência de **funcionalidade** foi o Enjoei. A referência de **aparência**
não é ninguém: cor, formas, botões e textos são próprios, de propósito, para
o app não parecer cópia.

| Peça | Onde | Tecnologia |
|---|---|---|
| Aplicativo | `app/` | React Native + Expo (SDK 52), TypeScript |
| API | `servidor/` | Node 20 + Express + Prisma + PostgreSQL |
| Play Store | `loja/` | fichas, políticas e gráficos prontos |
| Documentação | `documentos/` | este e mais cinco |
| Marca | `tools/gerar_marca.py` | gera todos os ícones de um script só |

---

## 2. As regras de dinheiro

São a parte mais importante e a mais testada. Ficam em
`servidor/src/dominio/regras.ts`, com espelho em `app/src/regras/limites.ts`
para o app avisar a pessoa antes de ela perder tempo. **O servidor é quem
manda**; o app só antecipa.

### Comissão e tarifa

| Regra | Valor |
|---|---|
| Comissão da plataforma | **12%** sobre o produto |
| Venda mínima | **R$ 10,00** |
| O que o comprador paga | **só o preço do produto** — não há frete cobrado à parte |

Tarifa fixa, cobrada **só na entrega pela plataforma**:

| Preço do produto | Tarifa |
|---|---|
| até R$ 24,99 | R$ 2,50 |
| até R$ 49,99 | R$ 4,50 |
| até R$ 99,99 | R$ 6,50 |
| até R$ 199,99 | R$ 8,50 |
| até R$ 499,99 | R$ 10,50 |
| a partir de R$ 500,00 | R$ 14,50 |

> **Os degraus são reais e estão medidos.** Toda tabela por faixa cria um
> degrau na virada: um produto de R$ 25,00 rende R$ 2,00 a menos ao vendedor
> que um de R$ 24,99. Em R$ 500,00 o degrau é de **R$ 4,00**. Não é bug, é a
> forma da tabela. Há teste fixando cada degrau — se alguém mexer na tabela e
> criar um degrau grande, a suíte quebra.

### As duas modalidades de entrega

O vendedor escolhe em cada anúncio, e a escolha **fica gravada no pedido**. Se
a regra mudar amanhã, o pedido de ontem mantém a regra com que foi vendido.

| | entrega pela plataforma | entrega pelo vendedor |
|---|---|---|
| Comissão | 12% | 12% |
| Tarifa fixa | por faixa | **não há** |
| Limite | 20 kg · 100 cm largura · 100 cm altura | **sem limite** |
| Quem entrega | entregador nosso | o próprio vendedor |
| Prova de entrega | código digitado pelo entregador | código digitado pelo vendedor |
| Devolução | coleta reversa | combinada, admin confirma |

Quando o pacote não cabe, o app **bloqueia a opção da plataforma**, explica o
motivo em português ("pesa 25 kg e o limite é 20 kg") e cai sozinho para a
entrega pelo vendedor. O bloqueio dispara **no primeiro campo que estoura**,
sem esperar as outras medidas.

### Prazos

| Prazo | Quanto | Onde vale |
|---|---|---|
| Teste e devolução | **7 dias** da entrega | sempre (CDC art. 49) |
| Confirmação automática | **3 dias** da declaração | entrega pelo vendedor |
| Resposta a uma oferta | **3 dias** do último lance | negociação |

### Devolução

Dentro dos 7 dias, **devolução integral**: o comprador recebe 100% do que
pagou, nas duas modalidades. Comissão e tarifa saem do caixa da plataforma.
Isso vem do artigo 49 do Código de Defesa do Consumidor, e as chaves para
reter existem no código mas vêm **desligadas** — ligar dentro do prazo legal é
o tipo de economia que vira ação no Procon.

**O dinheiro só volta depois que o produto volta.** Na modalidade plataforma,
um entregador faz a coleta reversa; na modalidade vendedor, as partes combinam
e o admin confirma o retorno. Sem isso, o comprador poderia ficar com o
produto e com o dinheiro.

---

## 3. O que o aplicativo faz hoje

### As cinco abas

| Aba | O que tem |
|---|---|
| home | vitrine com carrosséis, busca e categorias |
| buscar | busca com filtros de preço e estado |
| vendas | a lojinha, saldo a receber e dicas |
| notificações | negociações e mensagens |
| minha conta | perfil, atalhos e configurações |

### Todas as telas (25)

**Comprar:** Home, Busca, Produto, Loja, Checkout, Pedido (com linha do
tempo), Reembolso, Curtidos, FazerOferta, Ofertas, Avaliar, CodigoDeConfirmacao

**Vender:** Vendas, NovoAnuncio, MinhaLoja, MinhasVendas, EntregaDoVendedor

**Entregar:** AreaDoEntregador (corridas passo a passo), PassoDaEntrega

**Administrar:** PainelAdmin (fila de entregas, atribuição, devoluções)

**Conta:** Entrar, MinhaConta, Configuracoes, DadosPessoais, Enderecos,
ContaDeRecebimento, ComoFunciona, Conversa

### As funcionalidades que vieram do Enjoei

Estas quatro são o que faz o app se comportar como brechó em vez de loja:

**Negociar preço.** O comprador propõe, o vendedor aceita, recusa ou devolve
com outro valor. Regras em `servidor/src/dominio/ofertas.ts`, com 25 testes:

- piso de 50% do preço pedido, nunca abaixo do mínimo de venda
- oferta igual ou acima do preço é recusada, mandando comprar direto
- contraproposta fica **entre** a oferta e o preço do anúncio
- responde quem não deu o último lance; cancela quem está esperando
- 3 dias para responder, contados do último lance

Oferta aceita vira o preço do checkout. Como a tarifa é por faixa, uma oferta
que derruba o valor para outra faixa derruba a tarifa junto.

**Curtidas.** Coração no cartão e no produto, com contador, e a lista de
desejos em "o que eu curti".

**Seguir lojinha.** Botão no perfil do vendedor, com contador de seguidores.

**Avaliações.** Nota de 1 a 5 estrelas com comentário, que só abre com o
pedido **concluído** — avaliar antes do prazo de teste vencer seria avaliar
uma compra que ainda pode virar devolução.

### Logística

Máquina de estados própria, que **não toca em dinheiro**:

```
pago → aguardando atribuição → atribuída → aceita → a caminho da coleta
     → chegou → coletado (exige foto + volumes) → em rota → chegou
     → entregue (exige código de 4 dígitos)
```

- transição desconhecida é **recusada por padrão** — app desatualizado não
  pula etapa
- o telefone do cliente fica **mascarado** até o entregador aceitar a corrida,
  senão o cadastro de entregador viraria uma lista de contatos da cidade
- atribuição manual na v1, com a estratégia **por distância já escrita e
  testada**, desligada por uma linha

### Entrega pelo vendedor

Três portas para o pedido chegar em ENTREGUE, todas passando pela **mesma
função** — nenhum caminho esquece de abrir o prazo de teste:

1. o vendedor digita o código de 4 dígitos do comprador
2. o comprador toca em "já recebi"
3. o vendedor declara sem código, e o comprador tem 3 dias para contestar;
   passado o prazo em silêncio, o sistema confirma sozinho

---

## 4. Como está por dentro

### Regra pura, separada de tudo

`servidor/src/dominio/` não importa banco nem HTTP. Por isso o fluxo inteiro
é testável sem subir nada, e são **85 testes** rodando em menos de 2 segundos.

| Módulo | O que decide |
|---|---|
| `regras.ts` | comissão, tarifa por faixa, limites, prazos |
| `comissao.ts` | como o split é montado |
| `ofertas.ts` | quem pode propor o quê, e qual valor vale |
| `logistica.ts` | a máquina de estados da entrega |
| `reembolso.ts` | quanto volta e de qual saldo |
| `frete.ts` | se o pacote cabe na entrega da plataforma |
| `atribuicao.ts` | qual entregador recebe a corrida |

### Dinheiro é sempre inteiro, em centavos

R$ 129,90 é `12990`. Nunca float. A comissão sempre arredonda **para baixo**
(`Math.floor`) e a sobra fica com a plataforma: o vendedor nunca perde no
arredondamento, e a soma das partes bate no centavo — a pagar.me recusa a
transação se o split não fechar.

### Modo demonstração

Um servidor falso em memória (`app/src/demo/`) responde às mesmas rotas da API.
As telas não sabem que estão em demonstração — quem desvia é o cliente HTTP.

Garantias de isolamento:

- nenhuma tela importa `src/demo/`
- entra por `await import()` **dentro** do `if (MODO_DEMONSTRACAO)`
- não repete regra de negócio: lê `src/regras/limites.ts`, o mesmo módulo das
  telas
- não tem checkout, split, repasse, estorno nem webhook

O estado muda de verdade enquanto se navega: aceitar uma corrida move o
pedido, confirmar o código abre os 7 dias.

### O site é um PWA

Instalável no celular e **funciona sem rede**. Manifesto, ícones e service
worker em `app/public/`. O `finalizar-web.mjs` carimba o hash do bundle como
versão do cache — sem isso o worker antigo continuaria servindo a versão velha
e a atualização nunca chegaria.

---

## 5. A marca

Verde **`#00DF13`** como cor principal, **âmbar `#FF9F1C`** como segunda cor e
**coral** só no coração de curtida.

O âmbar existe para diferenciar: marketplace de usados costuma ser de uma cor
só com branco. Ele marca desconto, oferta em aberto e prazo correndo — coisas
que pedem urgência, onde o verde (que significa "tudo certo") passaria a
mensagem errada.

A casca foi desenhada para **não parecer cópia**:

- botões com canto discreto, não cápsula, e que afundam ao toque
- três níveis de sombra em vez de um — é a sombra, mais que a cor, que separa
  um app que parece caseiro de um que parece caro
- cartão de produto com **preço antes do título**: em vitrine de usados o olho
  procura o número antes do nome
- categorias em chips preenchidos, não abas sublinhadas
- aba ativa com pílula atrás do ícone
- títulos com peso 800 e espaçamento negativo, o que dá impressão de
  tipografia desenhada sem carregar fonte nenhuma

Tudo sai de um script só: `python3 tools/gerar_marca.py`. Para trocar a cor,
mude `ROXO`/`VERDE` no topo e rode de novo.

---

## 6. Pagamento — pronto para encaixar

**A lógica está escrita e testada, mas não foi ligada em produção.** Era o
combinado: estruturar primeiro, ligar depois.

O que já existe:

| Peça | Estado |
|---|---|
| Split na pagar.me, com regras por recebedor | escrito |
| Escrow: recebedor com `transfer_enabled: false` | escrito |
| Repasse manual depois dos 7 dias | escrito |
| Estorno parcial com `split_rules` explícito | escrito |
| `Idempotency-Key` em toda chamada que mexe em dinheiro | escrito |
| Webhook com autenticação e proteção contra duplicata | escrito |
| Pagamento do entregador, fora do split da cobrança | escrito |
| Oferta aceita alimentando o mesmo cálculo | escrito |

Quando você mandar os dados, o que falta é: preencher `.env`, rodar
`npx prisma migrate dev`, e seguir o **checklist de homologação** que está no
fim de `documentos/split-pagarme.md` — comprar com Pix, comprar com cartão,
conferir o saldo retido, adiantar o relógio e ver o repasse sair.

> A chave secreta nunca entrou no repositório. `.env` está no `.gitignore` e
> foi verificado que nenhum `.env` jamais foi commitado no histórico.

---

## 7. O que ainda falta

Em ordem de urgência.

### Bloqueia rodar de verdade

1. **Migration.** O schema é válido mas nunca foi materializado. Precisa de
   `npx prisma migrate dev` com o banco de pé.

### Bloqueia publicar na Play Store

2. **Upload das fotos.** Continua `TODO` em `NovoAnuncio.tsx` e
   `PassoDaEntrega.tsx` — as fotos vão como URI local do celular, que não abre
   em lugar nenhum. Precisa de S3 ou Cloudinary.
3. **Notificação push.** O envio está implementado no servidor; falta o app
   registrar o token com `expo-notifications`.
4. **`[RAZÃO SOCIAL]` e `[SEU CNPJ]`** nos três HTML de `loja/`, e as capturas
   de tela do app rodando.

### Funcionalidade pendente

5. **Editar e pausar anúncio.** A rota `PATCH /anuncios/:id` existe; falta a
   tela.

### Decisão sua

6. **Comprimento sem limite.** Você especificou peso, largura e altura. Deixei
   `COMPRIMENTO_MAXIMO_CM = Infinity`, documentado. Na prática um item de 3 m
   de comprimento passa pela validação. Se a moto não leva, me diga o número.

### Antes de vender de verdade

7. Rodar o checklist de homologação inteiro com chaves de teste.
8. Validar os termos e a política de devolução com advogado — os textos
   aplicam o art. 49 do CDC, mas quem assina é você.

---

## 8. Como rodar

### Ver o app no navegador, sem nada instalado

```bash
cd app
npm install
npx expo start --web
```

Sem `EXPO_PUBLIC_API_URL`, entra sozinho em modo demonstração. A faixa preta
no topo tem o botão **roteiro**, que indexa todas as telas e troca o papel do
usuário entre cliente, entregador e admin.

### A API

```bash
cd servidor
cp .env.exemplo .env      # preencha
npm install
npx prisma migrate dev
npm run dev               # http://localhost:3333
```

### Os testes

```bash
cd servidor && npm test   # 85 testes
```

### Publicar o site

O `netlify.toml` está na raiz. Conectar o repositório à branch `main` não pede
configuração nenhuma no painel. Para desligar o modo demonstração são
**necessárias as duas coisas**: remover `EXPO_PUBLIC_MODO_DEMO` **e** definir
`EXPO_PUBLIC_API_URL`.

### Gerar o APK

```bash
cd app
npm install -g eas-cli && eas login && eas init
npm run build:producao    # AAB para o Play Console
```

---

## 9. Os outros documentos

| Arquivo | Para quê |
|---|---|
| `split-pagarme.md` | **o mais importante**: como o dinheiro anda, com exemplos numéricos e o checklist de homologação |
| `arquitetura-de-pagamento.md` | por que pagar.me, reforma tributária, régua de habitualidade |
| `logistica.md` | estados da entrega, quem pode o quê, devolução |
| `versao-navegavel.md` | o modo demonstração e o PWA |
| `publicar-na-play-store.md` | do zero até o app no ar |

---

## 10. Histórico das decisões

O que foi decidido ao longo do caminho, e por quê. Serve para ninguém refazer
uma discussão já encerrada.

| Decisão | Por quê |
|---|---|
| 7 dias de teste, não 4 | é a lei (CDC art. 49), não é escolha |
| Devolução integral | mesmo motivo; reter dentro do prazo vira ação no Procon |
| Continuar na pagar.me | é a única avaliada em que um vendedor PF se cadastra **sem criar conta própria** — decisivo para marketplace de bairro |
| Bradesco não serve como split | receber tudo na conta do CNPJ colocaria o GMV inteiro como receita tributável e a retenção de 7 dias viraria custódia de dinheiro de terceiro, que exige autorização do Banco Central |
| Modalidade congelada no pedido | mudar a regra amanhã não pode mexer no pedido de ontem |
| Verde, não roxo | o Enjoei é roxo; o verde é o maior diferenciador visual que o app tem |
| Estorno só depois do produto voltar | senão o comprador fica com o produto e com o dinheiro |
| Saque agrupado em carteira | taxa de saque é cobrada do vendedor e não pode ser dividida; por pedido, ele sente 24,7% em vez de 21% |

---

## 11. Números do projeto

| | |
|---|---|
| Arquivos versionados | 148 |
| Telas do aplicativo | 25 |
| Módulos de regra pura | 7 |
| Serviços do servidor | 9 |
| Grupos de rota | 10 |
| Testes automatizados | **85** |
| Anúncios de exemplo na demonstração | 24 |
| Pedidos de exemplo, um por estado | 18 |

Verificação a cada entrega: suíte completa, `tsc` limpo nos dois projetos, e
os fluxos clicados de ponta a ponta em navegador de verdade, com o console
conferido — a régua tem sido **zero erro**.
