# Prompt do projeto — Vendas Itinga

> **Para o agente de IA que vai construir isto.**
>
> Este documento é a especificação completa do aplicativo. Construa exatamente
> o que está escrito aqui, do começo ao fim, sem pedir confirmação a cada
> passo.
>
> Três instruções que valem para o trabalho inteiro:
>
> 1. **Onde houver um número, use o número.** Comissão, tarifas, prazos e
>    limites não são sugestões nem valores de exemplo.
> 2. **Onde houver uma justificativa em bloco de citação, leia antes de
>    "melhorar".** Ela existe porque aquela regra já foi discutida e decidida.
>    Várias parecem detalhe e são o oposto.
> 3. **Entregue funcionando e testado.** Regras de dinheiro e de estado vão
>    para módulos puros, com testes automatizados. Não deixe TODO em caminho
>    de dinheiro.
>
> Se algo aqui for genuinamente impossível ou contraditório, construa todo o
> resto e diga no final o que ficou de fora e por quê. Não trave o trabalho
> inteiro numa dúvida.

Última atualização: setembro de 2026.

---

## 1. O que é

Construa o **Vendas Itinga**: um marketplace de compra e venda de produtos
novos e usados **dentro de uma cidade só — Itinga, Minas Gerais**. Quem mora
ali anuncia o que não usa mais, e quem quer comprar encontra pertinho de casa.
**A plataforma não entrega nada**: o vendedor leva, ou o comprador retira. A
empresa cuida do dinheiro, da prova de entrega e da mediação.

**A referência de funcionalidade é o Enjoei.** Use o comportamento dele como
cérebro: negociação de preço, curtidas, seguir lojinha, avaliações, o jeito de
anunciar, o tom informal em caixa baixa.

**A referência de aparência não é ninguém.** Cor, formas, botões, abas e textos
são próprios, de propósito, para o app não parecer cópia. Por dentro Enjoei,
por fora Vendas Itinga.

Tudo em **português do Brasil**, inclusive os nomes no código.

### Plataformas

| Peça | Pasta | Tecnologia |
|---|---|---|
| Aplicativo | `app/` | React Native + Expo SDK 52, TypeScript |
| API | `servidor/` | Node 20 + Express + Prisma + PostgreSQL |
| Play Store | `loja/` | fichas, políticas e gráficos |
| Documentação | `documentos/` | as decisões e o porquê delas |
| Marca | `tools/gerar_marca.py` | gera todos os ícones de um script só |

O mesmo código vira **app Android** (Expo/EAS) e **site PWA instalável**
(react-native-web).

---

## 2. As regras de dinheiro — a parte que não se negocia

Ficam em `servidor/src/dominio/regras.ts`, com espelho em
`app/src/regras/limites.ts` para o app avisar a pessoa antes de ela perder
tempo. **O servidor é quem manda**; o app só antecipa.

A plataforma tem **três fontes de receita**, todas copiadas da mecânica do
Enjoei: comissão, tarifa fixa e taxa de saque. **Não há cobrança de frete** —
a empresa não entrega, então não tem o que cobrar por isso.

### 1. Comissão — paga pelo vendedor

Dois tipos de anúncio, escolhidos pelo vendedor na hora de publicar:

| Anúncio | Comissão | O que ganha |
|---|---|---|
| **Clássico** (padrão) | **12%** | publicação normal |
| **Turbinado** | **18%** | aparece em destaque na vitrine, no topo da busca e nos carrosséis |

Venda mínima: **R$ 10,00**. A comissão vale nas duas formas de entrega.

O turbinado é escolha do vendedor, anúncio por anúncio, e pode ser ligado
depois de publicado. Mostre na tela quanto ele recebe em cada opção, lado a
lado, calculado ao vivo — a diferença tem que ficar óbvia antes de ele decidir.

### 2. Tarifa fixa por faixa de preço — paga pelo vendedor

Cobrada em **toda venda**, nas duas formas de entrega. É a tarifa de uso da
plataforma: pagamento, retenção, prova de entrega, mediação e suporte.

> **É exatamente o modelo do Enjoei**, onde a tarifa fixa é cobrada do vendedor
> em toda venda e não tem relação com quem leva o produto. Como o Vendas Itinga
> não cobra frete de ninguém, **a tarifa é o que mantém a operação de pé** — sem
> ela, a receita seria só a comissão, e uma venda de R$ 50,00 renderia R$ 4,00
> à plataforma, antes de qualquer custo.

| Preço do produto | Tarifa |
|---|---|
| até R$ 24,99 | R$ 2,50 |
| até R$ 49,99 | R$ 4,50 |
| até R$ 99,99 | R$ 6,50 |
| até R$ 199,99 | R$ 8,50 |
| até R$ 499,99 | R$ 10,50 |
| a partir de R$ 500,00 | R$ 14,50 |

> **Os degraus são reais e precisam estar medidos em teste.** Toda tabela por
> faixa cria um degrau na virada: um produto de R$ 25,00 rende R$ 2,00 a menos
> ao vendedor que um de R$ 24,99. Em R$ 500,00 o degrau é de R$ 4,00. Não é
> bug, é a forma da tabela. Escreva um teste fixando **cada** degrau: se alguém
> mexer na tabela e criar um degrau grande, a suíte tem que quebrar.

### 3. Taxa de saque — paga pelo vendedor

**R$ 3,00 por saque, com o primeiro saque de cada mês grátis.** O dinheiro fica
na carteira do vendedor dentro do app; ele saca quando quiser.

Essa taxa é cobrada do vendedor e **não pode ser dividida no split** — por isso
o saque é agrupado por carteira, não por pedido. Se fosse por pedido, o vendedor
sentiria uma mordida de 24,7% em vez de 21%. O primeiro grátis existe para quem
saca uma vez por mês não sentir taxa nenhuma.

### Como o dinheiro é representado

**Todo valor monetário é inteiro, em centavos.** R$ 129,90 é `12990`. Nunca
float, em lugar nenhum — nem no app, nem no banco, nem na integração.

A comissão **sempre arredonda para baixo** (`Math.floor`) e a sobra do
arredondamento fica com a plataforma: o vendedor nunca perde no arredondamento.
A soma das partes tem que bater no centavo: o que vai para o vendedor mais o
que fica com a plataforma tem que dar exatamente o valor cobrado, sem sobra
nem falta.

### Resumo de quem paga o quê, numa venda de R$ 100

Vale igual nas duas formas de entrega — é o que torna a conta simples de
explicar a um vendedor.

| | |
|---|---|
| Comprador paga | **R$ 100,00** — o preço do anúncio, e nada além |
| Comissão (12%) | R$ 12,00 |
| Tarifa fixa | R$ 8,50 |
| **Vendedor recebe** | **R$ 79,50** |
| Meio de pagamento | ~R$ 4,00 |
| **Plataforma fica com** | **~R$ 16,50** |

**O comprador nunca paga nada além do preço do produto.** Não existe frete,
taxa de serviço nem acréscimo de nenhum tipo no checkout. O que está na
vitrine é o que ele paga — e isso é argumento de venda, não detalhe.

---

## 3. As duas formas de receber o produto

**A plataforma não faz entrega.** Não existe entregador da empresa, não existe
frete cobrado do comprador e não existe limite de peso ou tamanho. O produto vai
do vendedor para o comprador de uma de duas formas, e o vendedor escolhe quais
aceita em cada anúncio.

| | **Entrega pelo vendedor** | **Retirada no local** |
|---|---|---|
| Quem se desloca | o vendedor | o comprador |
| Onde | endereço do comprador | ponto escolhido pelo vendedor |
| Comissão | 12% (ou 18% turbinado) | 12% (ou 18% turbinado) |
| Tarifa fixa | por faixa | por faixa |
| Custo de frete | **não há** | **não há** |
| Limite de peso/tamanho | **não há** | **não há** |
| Prova | senha do comprador + foto | **QR Code escaneado** pelo vendedor + foto |

O vendedor pode marcar **as duas** no mesmo anúncio. Aí quem escolhe é o
comprador, na hora de comprar — e a escolha **fica gravada no pedido**.

> **Por que não existe mais entrega própria.** A operação de entregadores foi
> desenhada e descartada antes de existir: cadastro de entregador, pagamento por
> corrida, coleta reversa na devolução e responsabilidade sobre produto de
> terceiro em trânsito. É muita máquina e muito risco para uma cidade onde as
> pessoas se encontram na rua. Numa cidade pequena, deixar as duas partes
> combinarem resolve o mesmo problema sem a empresa carregar o produto de
> ninguém. **Não reintroduza entregador, frete ou corrida.**

### Retirada no local — o ponto de retirada

Quem escolhe "retirada no local" informa **onde** o comprador vai buscar:

- rua, número, bairro, **ponto de referência** e um campo de observação
  ("portão azul", "falar com a Dona Maria"). Numa cidade pequena o ponto de
  referência vale mais que o CEP;
- **os dias e horários** em que ele atende ("seg a sex, 8h às 18h");
- o vendedor cadastra uma vez no perfil e reaproveita nos próximos anúncios.

**Antes da compra, o comprador vê só o bairro e o horário.** O endereço completo
só aparece **depois do pagamento**, na tela do pedido. Endereço de casa em
anúncio aberto é convite que ninguém precisa fazer.

> Sugira ao vendedor, com um aviso discreto na tela, que use um **ponto
> movimentado** — o comércio onde ele trabalha, uma praça, a frente de casa em
> horário de movimento. Não bloqueie nada: é sugestão, não regra.

### Na tela de novo anúncio

Mostre as duas formas **lado a lado**, com o valor que o vendedor recebe —
que é o mesmo nas duas, porque nenhuma tem custo de frete. O que muda é só
quem se desloca.

---

## 4. Prazos e devolução

| Prazo | Quanto | Conta a partir de |
|---|---|---|
| **Cancelamento automático por atraso** | **7 dias corridos** | do pagamento |
| Teste e devolução | **7 dias corridos** | **da entrega** |
| Tentativas de entrega | **5 no total** | entrega pelo vendedor |
| Resposta a uma oferta | **3 dias** | do último lance |

> **São dois "7 dias" diferentes e eles nunca correm juntos.**
>
> | Relógio | Conta de | Dono | Estourou? |
> |---|---|---|---|
> | Cancelamento — 7 dias | pagamento | vendedor | pedido morre, comprador reembolsado |
> | Teste e devolução — 7 dias | **entrega** | comprador | acabou o direito de arrependimento |
>
> O segundo só começa quando o primeiro já acabou. Confundir os dois é o erro
> mais fácil de cometer neste documento.

### Cancelamento automático em 7 dias

**Se o produto não trocar de mãos em 7 dias corridos contados do pagamento, o
pedido é cancelado sozinho e o comprador recebe tudo de volta.**

Vale nas duas formas — o que conta é o pedido ter chegado em **ENTREGUE**, seja
porque o vendedor levou, seja porque o comprador foi buscar.

O que o cancelamento faz:

1. estorna **100%** do que o comprador pagou;
2. libera o anúncio de volta;
3. registra o motivo no pedido como `CANCELADO_POR_ATRASO`, no histórico das
   duas partes — porque a culpa pode ser de qualquer um dos dois: vendedor que
   sumiu, ou comprador que nunca foi buscar;
4. avisa as duas partes.

Avise antes de cancelar, não só depois: no **5º dia**, as duas partes recebem
uma notificação de que faltam 2 dias. Cancelar sem avisar é como se perde
usuário.

O job que faz isso roda de hora em hora.

### Por que 7 dias de teste, e por que integral

É o **artigo 49 do Código de Defesa do Consumidor**: compra feita fora do
estabelecimento comercial dá ao consumidor 7 dias corridos para desistir, com
devolução de **todos** os valores pagos. Não é escolha de produto, é lei.

Dentro dos 7 dias a devolução é **integral**: o comprador recebe 100% do que
pagou. Comissão e tarifa **não** são descontadas — quem absorve esse custo é a
plataforma. Deixe as chaves de retenção existindo no código, mas
**desligadas**; reter dentro do prazo legal é o tipo de economia que vira ação
no Procon.

### A ordem importa

**O dinheiro só volta depois que o produto volta.** As duas partes combinam a
devolução entre si, pelo chat do pedido, e **o dono confirma no painel** que o
produto voltou. Só então o estorno é disparado. Sem essa ordem, o comprador
ficaria com o produto e com o dinheiro.

> **Devolução aqui quase não custa nada à plataforma**, porque não há entregador
> para pagar na ida nem na volta. O que se perde é a taxa do meio de pagamento,
> que normalmente não volta num estorno — alguns reais por pedido.

---

## 5. A senha de segurança

É o coração da confiança do aplicativo. **Sem senha conferida, nada é
entregue** — e o relógio dos 7 dias não começa.

**Só existe um jeito de um pedido virar ENTREGUE: senha conferida mais foto.**
Não há declaração, não há botão de atalho, não há confirmação automática por
tempo. Sem os dois, o pedido não anda.

### Como a senha é formada

**4 caracteres, sorteados pelo servidor, misturando letras, números e
símbolos.** Não é PIN numérico.

```
exemplos:  K7#m    9$Qz    P2@w
```

Regras de geração:

- sorteada com **gerador criptográfico** (`crypto.randomBytes`), nunca com
  `Math.random()`, e **nunca derivada** do número do pedido, da data, do CPF ou
  de qualquer coisa que se adivinhe de fora;
- **fora do alfabeto**: `0 O o 1 l I` e qualquer par que se confunda ao ser
  falado em voz alta. A senha vai ser lida por uma pessoa para outra, na rua,
  às vezes com pressa;
- a tela mostra a senha **em letra grande, com espaço entre os caracteres**, e
  diz o nome de cada símbolo por extenso embaixo ("cerquilha", "arroba",
  "cifrão"). Sem isso, símbolo vira discussão no meio da entrega.

> **Por que caractere de símbolo e não só número.** Com 4 dígitos existem dez
> mil combinações; com letras, números e símbolos passam de vinte milhões.
> Combinado com o bloqueio por tentativa abaixo, adivinhar deixa de ser um
> caminho possível.

### Regras que valem para qualquer senha

- **cada senha aparece só para o dono dela**, em lugar nenhum mais do app. Se o
  outro lado conseguisse ver, concluiria sozinho e a prova não valeria nada;
- **senha errada não conclui**: o app diz que não confere e deixa tentar de
  novo;
- **limite de tentativas**: 5 erros seguidos travam a conferência por 30
  minutos e avisam o dono;
- a conferência é **sempre no servidor**. A senha não é enviada para o
  aplicativo do outro lado nem para comparar, nem escondida, nem cifrada;
- **foto obrigatória**, tirada na hora pelo app — não vale escolher da galeria.
  A foto fica anexada ao pedido;
- fica registrado **quem conferiu, quando e de qual conta**.

### Forma 1 — o vendedor entrega

O vendedor vai até o comprador. Na mesma tela, ele precisa de **duas coisas**:

1. a **senha** que o comprador falar; e
2. uma **foto** do produto sendo entregue.

Sem a foto o botão de concluir não habilita, mesmo com a senha certa. Conferida
a senha, o pedido vira **ENTREGUE** e começam os 7 dias.

#### Quando o comprador não está — as 5 tentativas

O vendedor chegou e não conseguiu a senha: o comprador não estava, não atendeu,
ou pediu para voltar outro dia. **Ele não declara nada.** Registra uma
**tentativa de entrega**:

- **foto obrigatória** do local, tirada na hora, com data e hora;
- um motivo em uma linha ("ninguém atendeu", "pediu para voltar sábado").

São **5 tentativas no total** — a primeira e mais quatro. A cada uma, o
comprador é avisado na hora: *"o vendedor tentou entregar hoje às 14h. Combine
um horário pelo chat do pedido."*

**As tentativas não entregam o pedido e não liberam dinheiro.** Elas são
registro, e existem para duas coisas: o comprador saber que o vendedor foi
até lá, e o pedido chegar ao **painel do dono** marcado como
`ENTREGA_NAO_CONCLUIDA`, com as 5 fotos e os 5 horários em ordem.

No painel você vê o que aconteceu e **fala com as duas partes** — o que quase
sempre destrava: o comprador combina um horário e a entrega acontece, com a
senha, do jeito normal. Se não destravar, o pedido segue para o cancelamento
e o comprador é reembolsado.

| O que você vê | O que costuma ser |
|---|---|
| 5 fotos do mesmo portão, em dias e horários diferentes | o vendedor tentou de verdade; o comprador é que sumiu |
| 5 fotos iguais, tiradas no mesmo minuto | ninguém tentou nada |
| fotos de lugares diferentes | endereço errado, e aí é conversa |

> **Por que as tentativas não confirmam a entrega, nem mesmo com você
> aprovando.** Tentativa prova que o vendedor **foi**, não que o comprador
> **recebeu**. São coisas diferentes, e só a segunda pode liberar dinheiro.
> Nem o dono conclui um pedido no lugar da senha — se isso fosse possível, a
> senha deixaria de ser a regra e passaria a ser a primeira tentativa.

O relógio do cancelamento automático de 7 dias **continua correndo durante as
tentativas** — elas não esticam prazo nenhum. O que elas dão é tempo de alguém
perceber e resolver antes de o prazo acabar.

### Forma 2 — o comprador retira no local: **QR Code**

Na retirada **não se digita senha nenhuma**. O comprador mostra um **QR Code na
tela do celular** e o vendedor **escaneia com a câmera do app**. Um gesto só,
feito uma vez, e o pedido está entregue.

```
   comprador                          vendedor
  ┌───────────┐                    ┌───────────┐
  │  ▄▄▄▄▄▄▄  │                    │    [◉]    │
  │  █ ▄▄▄ █  │   ──── escaneia ──►│  câmera   │
  │  █▄▄▄▄▄█  │                    │           │
  │  ⏱ 18s    │                    │           │
  └───────────┘                    └───────────┘
        │                                │
        └────────► servidor ◄────────────┘
              ENTREGUE, para os dois
```

> **Por que isto é melhor que trocar senhas digitadas, e não é questão de
> conforto.**
>
> Na troca de senhas alguém precisa ir primeiro, e **quem vai depois pode
> simplesmente não ir**. O comprador passa a senha dele, o vendedor não passa a
> dele, e o comprador fica sem a confirmação — ou o contrário. O problema não
> está na senha: está em ser uma troca em dois tempos.
>
> **O QR resolve porque não tem dois tempos.** É uma ação só: o código está na
> tela de um e a câmera é do outro. Quando o servidor recebe o escaneamento,
> ele já sabe que **as duas pessoas estavam no mesmo lugar, no mesmo segundo** —
> não existe "primeiro" nem "segundo" para alguém abandonar no meio.
>
> E fecha um golpe que a senha digitada não fechava por completo: **QR não se
> lê por telefone**. Não dá para pedir "me passa o código" e receber por
> WhatsApp — a câmera tem que estar apontada para a tela, na frente da pessoa.

### Como o QR funciona

1. o comprador abre o pedido e toca em **"estou retirando"**. A tela mostra o
   QR grande, com um **contador regressivo**;
2. o vendedor abre o pedido dele e toca em **"escanear para entregar"**;
3. escaneou, os **dois celulares mostram a confirmação ao mesmo tempo**. O
   pedido vira **ENTREGUE** e começam os 7 dias;
4. o vendedor tira a **foto** do produto entregue, que fica anexada ao pedido.

Regras de segurança do código, todas obrigatórias:

- o QR carrega um **token sorteado pelo servidor** (`crypto.randomBytes`),
  ligado àquele pedido e àquela sessão do comprador. Não é o número do pedido,
  não é nada derivável;
- **vale 20 segundos e se renova sozinho** na tela. Print de tela mandado por
  mensagem chega morto;
- **uso único**: escaneado uma vez, aquele token morre. Não dá para reusar nem
  para entregar duas coisas com o mesmo código;
- quem escaneia tem que ser **o vendedor daquele pedido**, autenticado. Scan de
  terceiro é recusado;
- a validação é **toda no servidor**. O aplicativo do vendedor não decide nada:
  ele manda o token e recebe sim ou não;
- **tudo registrado**: quem escaneou, quando, e de qual conta.

### Se a câmera não funcionar

Celular velho, câmera quebrada, permissão negada, tela rachada. Nesse caso — e
só nele — o app oferece **"não consigo escanear"**, que cai na **troca de
senhas digitadas**: cada um digita a do outro, como na forma 1.

É o mesmo nível de prova (os dois presentes), só mais lento e com o problema de
quem vai primeiro. **Deixe esse caminho escondido atrás do botão**, nunca lado
a lado com o QR: quem tem câmera deve usar a câmera.

### A ordem no encontro vale igual

> **escaneie com o produto na mão.**
> *comprador: só mostre o QR com a peça já com você.*
> *vendedor: só deixe escanear se estiver entregando agora.*

Com o QR isso fica fácil de seguir, porque é um gesto só — não há como fazer
metade e parar no meio.

### Os únicos dois caminhos até ENTREGUE

**Senha conferida. Só isso. Em nenhuma outra circunstância um pedido vira
ENTREGUE.**

| | Caminho | Exige | Vale em |
|---|---|---|---|
| 1 | o vendedor digita a senha do comprador | **senha + foto** | entrega pelo vendedor |
| 2 | **o vendedor escaneia o QR do comprador** | **scan válido + foto** | retirada no local |

Os dois passam pela **mesma função interna** — nenhum caminho pode esquecer de
abrir o prazo de teste.

> **NÃO CRIE UM TERCEIRO CAMINHO.** Nem botão de "já recebi", nem "declarar
> entrega", nem confirmação automática por tempo, nem liberação por decisão do
> dono. Cada um deles parece resolver um caso difícil e todos têm o mesmo
> defeito: permitem que um pedido seja concluído **sem que as duas pessoas
> estejam frente a frente**. É exatamente isso que a senha existe para provar.
>
> Um botão de "já recebi" tocado por engano entrega o pedido e começa a contar
> o prazo de devolução sem o comprador ter recebido nada. Uma declaração do
> vendedor aceita por silêncio paga quem não entregou. **A regra única é o que
> dá segurança: o pedido só anda quando as senhas são trocadas na hora, no
> lugar, entre as duas pessoas.**

### A ordem no encontro: senha primeiro, produto depois

Escreva isso na tela, no momento da entrega, para os dois lados:

> **troque as senhas antes de entregar o produto.**
> *vendedor: só solte a peça depois que as duas senhas estiverem conferidas.*
> *comprador: só passe sua senha quando o produto estiver na sua mão.*

Parece detalhe e é a regra inteira. Feita nessa ordem, **nenhum dos dois
consegue prejudicar o outro**: o comprador não leva a peça sem confirmar, e o
vendedor não confirma sem entregar. Feita ao contrário, alguém fica na mão — e
não há tela que conserte isso depois.

O app mostra esse aviso:

- na notificação do pagamento, para os dois;
- em **faixa destacada no topo do pedido**, enquanto a senha não for conferida;
- na própria tela de conferir a senha, em cima do campo.

### Impedir que a senha seja esquecida

- **lembretes em 24h e em 48h** para os dois, enquanto a senha não for
  conferida;
- a senha **não vence**. Se ninguém digitou na hora, o vendedor pede pelo chat
  do pedido depois e digita — o caminho normal continua aberto até o fim do
  prazo de 7 dias;
- **se só uma das senhas for conferida** na retirada, o pedido fica em "retirada
  pela metade" e **avisa o dono depois de 24 horas**. Ele não conclui nem
  cancela sozinho: metade da troca é sinal de que algo saiu do roteiro.

### Se a senha nunca for conferida

O pedido **cancela no prazo de 7 dias e o comprador é reembolsado**. É a
consequência de não ter caminho alternativo, e é assumida de propósito.

Por isso a ordem "senha primeiro, produto depois" é martelada na tela: quem
seguir a ordem nunca chega nesse caso. Quem entregar o produto antes de
conferir a senha está fazendo um acordo de confiança por fora do aplicativo, e
o aplicativo não tem como cobrir isso sem abrir a porta que ele fecha.

---

## 6. A máquina de estados do pedido

Enxuta, e **separada do dinheiro**: mudar de estado nunca move valor sozinho.
Quem move dinheiro são as rotinas de repasse e de estorno, que leem o estado.

```
              ┌──────────────────────────────────────────┐
              │                                          │
  pago ──► aguardando ──► ENTREGUE ──► em teste ──► concluído
              │           (senha)      (7 dias)     (repasse)
              │                            │
              │                            ▼
              │                      devolução pedida
              │                            │
              ▼                            ▼
       cancelado por atraso          produto devolvido
       (7 dias sem entrega)                │
              │                            ▼
              └────────────► estorno integral
```

Três regras que precisam estar no código, não só no documento:

- **transição desconhecida é recusada por padrão.** Um app desatualizado não
  pode inventar um movimento novo nem pular etapa;
- **guarda de ator**: cada transição só é permitida a quem tem o papel certo.
  Comprador não marca como entregue sem ter recebido; vendedor não conclui sem
  a senha; só o dono confirma que um produto devolvido voltou;
- **o telefone e o endereço completo só aparecem depois do pagamento**, para as
  duas partes daquele pedido e mais ninguém.

Escreva testes para as transições **inválidas**, não só para as válidas: o
valor da máquina está no que ela recusa.

---

## 7. Contas, papéis e quem enxerga o quê

### Uma conta só, que compra e vende ao mesmo tempo

Como no Enjoei: a pessoa **entra com a conta Google** e pronto. Não existe
"cadastro de vendedor" separado, nem tela de escolher se você é comprador ou
vendedor. **Toda conta já é as duas coisas.**

- entrou com o Google, já pode comprar;
- tocou em "vender", já pode anunciar;
- a mesma pessoa compra hoje e vende amanhã, sem trocar de conta nem de perfil.

Os dados extras de vendedor são pedidos **só quando ele precisa deles, na hora
que precisa** — nunca num cadastro grande no começo, que é o que faz a pessoa
desistir:

| Quando | O que pede |
|---|---|
| Ao publicar o primeiro anúncio | endereço de coleta (se escolher entrega pela plataforma) |
| Ao fazer a primeira venda | conta de recebimento |
| Ao comprar pela primeira vez | endereço de entrega |

### Os dois papéis

| Papel | Quem é | Como vira |
|---|---|---|
| **Usuário** | comprador e vendedor, a mesma conta | entrou com o Google |
| **Dono (admin)** | o dono da operação | e-mail na variável de ambiente do servidor |

São **dois papéis, só isso**. Não existe entregador, não existe moderador, não
existe operador. E **não existe nenhuma tela que promova alguém a dono** — se
existir, é buraco de segurança, não funcionalidade.

### Como a conta de dono é reconhecida

O dono entra pelo Google como qualquer pessoa. O que o torna dono é o servidor
reconhecer o e-mail dele:

```
EMAILS_DO_DONO="marcosmartins7799@gmail.com,marcosaluno7799@gmail.com"
```

Uma variável de ambiente no `.env` do servidor, aceitando uma lista separada
por vírgula. **São essas duas contas e mais nenhuma.**

A primeira é a conta de uso diário e a segunda é a reserva, mas isso é
organização do dono, não regra do sistema: **as duas têm exatamente o mesmo
poder**. A ordem na lista não cria hierarquia — não implemente "dono principal"
e "dono secundário", nem dê permissão diferente para uma ou outra. Quem está na
lista é dono, ponto. No login, **depois** de validar o token do Google, o servidor
compara o e-mail **verificado** que veio do Google com essa lista. Bateu, a
sessão tem papel de dono; não bateu, é usuário comum.

Regras que precisam ser respeitadas na implementação:

- compare com o e-mail que **o Google confirmou** (`email_verified`), nunca com
  um campo enviado pelo aplicativo. O app pode mentir; o token do Google, não;
- a comparação ignora maiúsculas e espaços em volta;
- **o papel é decidido no servidor, a cada requisição**, a partir da sessão.
  Nunca confie num "sou admin" que chega do cliente;
- se a variável estiver vazia ou não existir, **ninguém é dono** — o sistema
  fica sem admin em vez de eleger alguém por engano;
- mudar quem é dono exige acesso ao servidor e um novo deploy. É de propósito:
  ninguém vira dono sem passar por você.

> **Aceite mais de um e-mail nessa lista mesmo que hoje só exista um.** Se a
> conta do dono for perdida — Google recuperando conta, celular roubado, senha
> esquecida — sem um segundo e-mail cadastrado o painel fica inacessível até
> alguém mexer no servidor. Cadastrar um e-mail reserva desde o primeiro dia
> custa nada e evita ficar trancado do lado de fora da própria operação.

### Registro de quem fez o quê

**Toda mudança de estado de um pedido grava quem fez, quando e de qual conta.**
Sem isso, o dia em que alguém disser "não recebi" não há o que olhar. O painel
mostra esse histórico em cada pedido, em ordem, junto com as fotos e a hora em
que a senha foi conferida.

### Quem enxerga o quê

Esta tabela é regra de autorização, não sugestão de interface. **Cheque no
servidor, em toda rota.** Esconder o botão na tela não é proteger o dado.

| Informação | Usuário | Dono |
|---|---|---|
| Anúncios, vitrine, busca | ✅ | ✅ |
| Os próprios pedidos e vendas | ✅ | ✅ |
| Endereço e telefone da outra parte | **só do próprio pedido, depois de pago** | ✅ |
| Senha de 4 dígitos | **só o dono dela, só no pedido dele** | ✅ |
| Fotos da entrega | as duas partes daquele pedido | ✅ |
| **A fila inteira de pedidos** | ❌ | ✅ **só você** |
| Pedidos perto do cancelamento automático | ❌ | ✅ |
| Devoluções, estornos e repasses | ❌ | ✅ |
| Faturamento e comissões | ❌ | ✅ |
| Histórico completo de vendas concluídas | só as próprias | ✅ |
| Contador de devoluções por pessoa | ❌ | ✅ |

### O painel do dono

Só você abre. É onde a operação inteira aparece de uma vez:

- **a fila de pedidos**, com o estado de cada um e há quanto tempo está parado;
- os pedidos **perto do cancelamento automático**, em destaque;
- as **devoluções** esperando sua decisão, com as fotos das duas partes lado a
  lado;
- o **dinheiro**: retido, a repassar, repassado;
- o **contador de devoluções** por vendedor e por comprador — é o que mostra
  quem descreve mal o produto e quem devolve demais;
- o histórico de quem fez o quê, em cada pedido.

---

## 8. As funcionalidades que vêm do Enjoei

São o que faz o app se comportar como brechó em vez de loja.

### Negociar preço (ofertas)

O comprador propõe, o vendedor aceita, recusa ou devolve com outro valor.

- piso de **50%** do preço pedido, e nunca abaixo do mínimo de venda de
  R$ 10,00 (metade de um anúncio de R$ 12,00 daria R$ 6,00, abaixo do valor com
  que o pedido nem pode ser criado);
- oferta **igual ou acima** do preço é recusada, mandando comprar direto — se a
  pessoa quer pagar o valor cheio, o caminho é o botão de comprar, não uma
  negociação que ainda depende de resposta;
- a contraproposta fica **entre** a oferta e o preço do anúncio: abaixo seria o
  vendedor pedindo menos do que já lhe ofereceram, acima seria aumentar o preço
  no meio da conversa;
- **responde quem não deu o último lance; cancela quem está esperando**;
- só o vendedor contrapropõe — o comprador refaz a oferta;
- **3 dias** para responder, contados do último lance; a contraproposta
  reinicia o relógio;
- a oferta vence **na leitura**, não só no job: quem abre a tela precisa ver o
  estado certo agora.

**Oferta aceita vira o preço do checkout.** Ela não move dinheiro sozinha:
grava o valor combinado, e o checkout de sempre passa esse valor para o mesmo
`calcularSplit`. Consequência correta e desejada: como a tarifa é por faixa,
uma oferta que derruba o valor para outra faixa derruba a tarifa junto
(R$ 105,00 fechado em R$ 95,00 sai da tarifa de R$ 8,50 para a de R$ 6,50).
Uma oferta aceita vale por **uma compra só**.

### Curtidas

Coração no cartão e na tela do produto, com contador, e a lista de desejos em
"o que eu curti". Atualização otimista na interface, com rollback se o servidor
recusar.

### Seguir lojinha

Botão no perfil do vendedor, com contador de seguidores. Ninguém segue a si
mesmo.

### Avaliações

Nota de 1 a 5 estrelas com comentário, que **só abre com o pedido concluído** —
avaliar antes de o prazo de teste vencer seria avaliar uma compra que ainda
pode virar devolução.

---

## 9. As telas

### As cinco abas

| Aba | O que tem |
|---|---|
| home | vitrine com carrosséis, busca e categorias |
| buscar | busca com filtros de preço e estado |
| vendas | a lojinha, saldo a receber e dicas |
| notificações | negociações e mensagens |
| minha conta | perfil, atalhos e configurações |

### As telas

**Comprar:** Home, Busca, Produto, Loja, Checkout, Pedido (com linha do tempo),
Reembolso, Curtidos, FazerOferta, Ofertas, Avaliar, **MinhaSenha** (a senha do
comprador, em letra grande), **MeuQRCode** (o código da retirada, com contador)

**Vender:** Vendas, NovoAnuncio, MinhaLoja, MinhasVendas, **ConfirmarEntrega**
(digitar a senha do comprador + foto), **EscanearRetirada** (câmera + foto),
**PontoDeRetirada** (endereço e horários)

**Administrar:** PainelAdmin (fila de pedidos, devoluções, faturamento)

**Conta:** Entrar, MinhaConta, Configuracoes, DadosPessoais, Enderecos,
ContaDeRecebimento, ComoFunciona, Conversa

Login com **Google**. Cadastro de conta de recebimento do vendedor dentro do
app.

---

## 10. A identidade visual — como não parecer cópia

### Paleta

| Cor | Código | Onde |
|---|---|---|
| Verde | `#00DF13` | cor da marca: botão, aba ativa, destaque |
| Verde escuro | `#00990D` | texto e ícone sobre branco |
| Âmbar | `#FF9F1C` | desconto, oferta em aberto, prazo correndo |
| Coral | `#FF3D68` | só o coração de curtida e o contador |

**O verde é inegociável e é o maior diferenciador que o app tem** — o Enjoei é
roxo. O verde puro não tem contraste para texto sobre branco, então texto e
ícone usam o verde escuro e o verde fica para preenchimento.

O **âmbar** existe para diferenciar: marketplace de usados costuma ser de uma
cor só com branco. Ele marca o que pede urgência, onde o verde (que significa
"tudo certo") passaria a mensagem errada. Nunca como fundo de bloco grande: é
tempero, não base.

### As formas

- **botões com canto discreto (12 px), não cápsula**, e que **afundam ao
  toque** (`scale: 0.97`), com um halo da própria cor por baixo — truque barato
  que faz o verde parecer aceso em vez de chapado;
- **três níveis de sombra**, não um só: cartão apoiado, elemento flutuante,
  barra de ação. A diferença entre um app que parece caseiro e um que parece
  caro está mais na sombra do que na cor — sombra única e dura achata tudo no
  mesmo plano;
- **cartão de produto com o preço ANTES do título**: em vitrine de usados o
  olho procura o número antes do nome;
- foto do produto mais alta que larga (proporção 1,15) e canto de 18 px;
- **categorias em chips preenchidos**, não abas sublinhadas;
- aba ativa com **pílula atrás do ícone**;
- títulos com **peso 800 e espaçamento negativo** — dá impressão de tipografia
  desenhada sem carregar fonte nenhuma;
- texto todo em **caixa baixa**, tom informal, de vizinho vendendo pro vizinho.

Tudo sai de um script só: `python3 tools/gerar_marca.py` gera todos os ícones.

---

## 11. Pagamento — deixe pronto, não ligue

**Escreva e teste toda a lógica, mas não ligue em produção.** O combinado é:
estruturar primeiro, ligar depois, quando as credenciais chegarem.

### Provedor: Stripe (Connect)

O vendedor é um **connected account** do tipo Express, criado pelo próprio app
com o onboarding hospedado da Stripe. A plataforma é a conta principal.

> **Verifique antes de codar:** confirme que o Stripe Connect da sua conta
> brasileira aceita cadastrar vendedor **pessoa física com CPF** e pagar em
> conta dele, e que **Pix** está habilitado como método de cobrança. Isso é o
> que decide se o modelo funciona num marketplace de bairro, onde quase todo
> vendedor é PF e informal. Se o cadastro exigir CNPJ, o modelo não fecha e
> precisa ser reavaliado antes de qualquer linha de código.

Um banco comum não serve no lugar disso: receber tudo na conta do CNPJ
colocaria o GMV inteiro como receita tributável, e a retenção de 7 dias viraria
custódia de dinheiro de terceiro, que exige autorização do Banco Central.

### O padrão a usar: separate charges and transfers

**Não use destination charges.** O modelo certo aqui é *separate charges and
transfers*: a cobrança inteira cai na conta da plataforma, e a transferência
para o vendedor só é criada **depois** dos 7 dias. É o que dá o escrow de
graça, sem depender de configuração de payout.

```
1. PaymentIntent na conta da plataforma  → comprador paga produto + entrega
2. dinheiro fica retido no saldo da plataforma
3. entrega confirmada → começam os 7 dias
4. prazo vencido sem devolução → Transfer para o connected account do vendedor
5. o vendedor saca da carteira quando quiser (taxa de R$ 3,00, 1ª do mês grátis)
```

### O que precisa estar escrito

| Peça | Detalhe |
|---|---|
| Cobrança | `PaymentIntent` na conta da plataforma, valor = **só o preço do produto** |
| Escrow | o dinheiro **fica na plataforma**; nenhum `Transfer` é criado antes dos 7 dias |
| Repasse | `Transfer` para o connected account, disparado por job de hora em hora que procura pedido com prazo vencido e sem devolução aberta |
| Estorno | `Refund` do `PaymentIntent`. Como ainda não houve `Transfer` dentro dos 7 dias, **não há transferência para reverter** — é só estornar. Se por algum motivo o repasse já saiu, use `reverse_transfer` |
| Idempotência | header `Idempotency-Key` em **toda** chamada que mexe em dinheiro |
| Webhook | verificação de assinatura com `Stripe-Signature` e o webhook secret (`stripe.webhooks.constructEvent`), **nunca** confiando no corpo sem validar; guarde o `event.id` e ignore repetido |
| Eventos a tratar | `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`, `transfer.created`, `account.updated` |
| Oferta aceita | alimentando o mesmo cálculo de sempre |
| Saque agrupado em carteira | a taxa de saque é cobrada do vendedor e não pode ser dividida; por pedido, ele sentiria 24,7% em vez de 21% |

> **Vantagem de ter migrado:** com *separate charges and transfers*, o escrow
> deixa de ser uma configuração de recebedor e passa a ser simplesmente "ainda
> não transferi". É mais difícil de errar: esquecer de reter é impossível,
> porque reter é o estado padrão.

### Por que o repasse é manual

Se o dinheiro saísse antes dos 7 dias de teste, um pedido de devolução deixaria
a plataforma com saldo negativo para cobrir, e alguém teria que correr atrás do
vendedor para reaver o valor.

### Segurança da credencial

A chave secreta vive **só** no `.env` do servidor, na variável
`STRIPE_SECRET_KEY`, lida por `process.env`. O webhook secret vai em
`STRIPE_WEBHOOK_SECRET`, na mesma regra. O `.env` fica no `.gitignore` e
**nunca** é commitado. Só a publishable key (`pk_...`) pode ficar no app.

Deixe pronto também o **checklist de homologação**, com as chaves de teste:
comprar com Pix, comprar com cartão, conferir que o dinheiro ficou no saldo da
plataforma e que nenhum `Transfer` foi criado, adiantar o relógio e ver o
repasse sair, pedir devolução e conferir o estorno integral incluindo a taxa
de entrega.

Prepare o terreno para a **reforma tributária (IBS/CBS)**: separar a cobrança
do repasse já deixa claro o que é receita da plataforma (comissão e tarifa) e
o que é apenas dinheiro de passagem do vendedor.

---

## 12. Como o código tem que estar organizado

### Regra pura, separada de tudo

`servidor/src/dominio/` **não importa banco nem HTTP**. Por isso o fluxo inteiro
é testável sem subir nada.

| Módulo | O que decide |
|---|---|
| `regras.ts` | comissão, tarifa por faixa, limites, prazos |
| `comissao.ts` | como o split é montado |
| `ofertas.ts` | quem pode propor o quê, e qual valor vale |
| `pedido.ts` | a máquina de estados do pedido |
| `reembolso.ts` | quanto volta e de qual saldo |

### Testes

Suíte em Vitest cobrindo, no mínimo: cada degrau da tabela de tarifa, o split
fechando no centavo, as transições válidas e inválidas da máquina de estados, o
piso e o teto das ofertas, os três caminhos até ENTREGUE, e o estorno integral.
A régua é **zero erro**, `tsc` limpo nos dois projetos.

### Modo demonstração isolado

Um servidor falso em memória (`app/src/demo/`) responde às mesmas rotas da API,
para o app rodar no navegador sem banco e sem Stripe. Garantias de
isolamento:

- **nenhuma tela importa `src/demo/`** — quem desvia é o cliente HTTP;
- entra por `await import()` **dentro** do `if (MODO_DEMONSTRACAO)`;
- **não repete regra de negócio**: lê o mesmo módulo de limites que as telas;
- **não tem checkout, split, repasse, estorno nem webhook**.

Os dados de exemplo precisam incluir produtos **nas viradas de faixa**
(R$ 24,99 e R$ 25,00, R$ 499,99 e R$ 500,00) para os degraus serem vistos na
prática, e um pedido em **cada** estado da máquina.

Onde o navegador não dá conta (upload de foto, push, câmera), **mostre um aviso
na tela em vez de quebrar**.

---

## 13. Publicação

### Site PWA

Instalável no celular e **funciona sem rede**: manifesto, ícones maskable e
service worker com três estratégias (navegação = rede primeiro, asset com hash
= cache primeiro, o resto = rede com cache de reserva).

O script de finalização **carimba o hash do bundle como versão do cache** — sem
isso o worker antigo continua servindo a versão velha e a atualização nunca
chega. O `sw.js` e o `index.html` **não podem ser cacheados** pela CDN.

Publicação por `netlify.toml` na raiz, com `base = "app"` e `publish = "dist"`,
para conectar o repositório não exigir configuração no painel.

**Para desligar o modo demonstração são necessárias as DUAS coisas:** remover
`EXPO_PUBLIC_MODO_DEMO` **e** definir `EXPO_PUBLIC_API_URL`. Só tirar a flag não
basta, porque a regra é
`MODO_DEMONSTRACAO = (flag === '1') || (URL_API === '')` — com a URL vazia o
modo demonstração religa sozinho. As variáveis são lidas em tempo de **build**,
então mudar exige novo deploy.

### Play Store

Pacote `br.com.vendasitinga.app`, build AAB por EAS. Deixe prontos: ficha da
loja, classificação de conteúdo, formulário de segurança dos dados, política de
privacidade, termos de uso e página de exclusão de conta.

---

## 14. O que fica pendente do lado do dono

Deixe explícito e não invente valor no lugar:

1. rodar `npx prisma migrate dev` com o banco de pé;
2. upload das fotos para storage (S3 ou Cloudinary) — hoje vai como URI local;
3. registrar o token de push com `expo-notifications` (o envio já existe no
   servidor);
4. preencher razão social e CNPJ nos HTML da pasta `loja/`, e tirar as capturas
   de tela;
5. tela de **editar e pausar anúncio** (a rota `PATCH /anuncios/:id` já existe);
6. decidir se o comprimento máximo ganha um número;
7. validar os termos com advogado — os textos aplicam o art. 49 do CDC, mas
   quem assina é o dono;
8. mandar as credenciais da Stripe para o pagamento ser ligado, e confirmar
   que o Connect aceita vendedor pessoa física com CPF.

---

## 15. Restrições

**Não remova, reescreva, duplique ou altere a lógica financeira existente.**
Preserve integralmente os fluxos atuais de checkout, cobrança, split, taxas,
repasses, estornos e webhooks da Stripe.

Mudança visual é mudança **de casca**: botão, cor, sombra, espaçamento,
tipografia, layout. Não toque em regra de negócio, estrutura nem no cérebro do
app para deixar algo mais bonito.

Não simplifique, por parecerem detalhe:

- o arredondamento da comissão para baixo;
- o split fechando no centavo;
- a ordem produto-antes-do-dinheiro na devolução;
- a modalidade congelada no pedido;
- a transição desconhecida recusada por padrão;
- a devolução integral dentro dos 7 dias.

Cada uma dessas existe por um motivo escrito acima. São as que mais parecem
economia e as que mais custam caro.

---

## 16. Decisões já encerradas

Para ninguém refazer discussão já resolvida.

| Decisão | Por quê |
|---|---|
| 7 dias de teste, não 4 | é a lei (CDC art. 49), não é escolha |
| Devolução integral | mesmo motivo; reter dentro do prazo vira ação no Procon |
| 12% + tarifa por faixa, não 16/18% fixo | percentual alto assusta em produto caro; a tarifa cobre a operação no produto barato |
| Anúncio turbinado a 18% | mesma mecânica do Enjoei: quem quer aparecer mais paga mais, e é escolha do vendedor |
| Taxa de saque de R$ 3,00, primeiro grátis no mês | copiada do Enjoei; quem saca uma vez por mês não sente taxa nenhuma |
| Verde, não roxo | o Enjoei é roxo; o verde é o maior diferenciador visual que o app tem |
| **Stripe no lugar da pagar.me** | **decidido em setembro/2026 pelo dono.** O padrão passa a ser *separate charges and transfers*, que dá o escrow dos 7 dias sem depender de configuração de recebedor — reter vira o estado padrão. Falta confirmar que o Connect aceita vendedor PF com CPF |
| Modalidade congelada no pedido | mudar a regra amanhã não pode mexer no pedido de ontem |
| Estorno só depois do produto voltar | senão o comprador fica com o produto e com o dinheiro |
| **A plataforma não entrega nada** | **decidido em setembro/2026.** A operação de entregadores foi desenhada inteira e descartada antes de existir: cadastro com CPF e foto, pagamento por corrida, coleta reversa na devolução e responsabilidade sobre produto de terceiro em trânsito. É muita máquina e muito risco para uma cidade onde as pessoas se encontram na rua |
| Tarifa fixa passa a valer em toda venda | é o modelo do Enjoei, onde a tarifa não tem relação com quem leva o produto; sem ela a receita seria só a comissão, e uma venda de R$ 50,00 renderia R$ 4,00 à plataforma |
| Comprador não paga nada além do preço do anúncio | sem entrega própria não há frete a cobrar; preço de vitrine igual a preço final é o maior argumento de venda que o app tem |
| Retirada no local com **troca de senhas** | cada um precisa de um número que só existe no celular do outro, então ninguém conclui sozinho e o encontro presencial passa a ser exigido pela mecânica. Fecha o golpe de pedir o código por telefone e marcar como entregue sem entregar |
| Senha do comprador digitada pelo vendedor, e vice-versa | senha que o próprio dono digita não prova nada; o valor está em ela atravessar de uma pessoa para a outra |
| Endereço de retirada só depois do pagamento | endereço de casa em anúncio aberto é convite que ninguém precisa fazer |
| Limite de tentativas na senha | senha de 4 dígitos é curta; sem limite alguém tenta as dez mil |
| Cancelamento automático culpa os dois lados | sem entregador, o pedido pode travar tanto por vendedor que sumiu quanto por comprador que nunca foi buscar |
| Senha + foto, e **nenhuma outra porta** | a senha prova que o comprador estava lá, a foto prova o que foi entregue. Qualquer atalho que dispense a senha vira o caminho preferido de quem quer receber sem entregar |
| Senha de 4 caracteres com letras, números e símbolos | mais de vinte milhões de combinações em vez de dez mil; com o bloqueio por tentativa, adivinhar deixa de ser caminho |
| Alfabeto sem `0 O o 1 l I` e símbolos nomeados na tela | a senha é lida em voz alta, na rua, com pressa; caractere ambíguo vira discussão no meio da entrega |
| 5 tentativas de entrega, com foto em cada uma | tentativa prova que o vendedor **foi**, não que o comprador **recebeu** — por isso ela alimenta a decisão do dono, e nunca libera dinheiro sozinha |
| Esgotadas as tentativas, quem decide é o dono | o caso do comprador sumido é raro e ambíguo demais para relógio resolver; com as 5 fotos em ordem, uma pessoa decide em trinta segundos |
| **Sem confirmação automática por tempo** | era a última porta dos fundos: bastava declarar e esperar para receber por entrega que nunca aconteceu, e quem não visse a notificação descobriria tarde demais |
| **Senha ou QR é o único caminho, sem exceção nenhuma** | toda exceção examinada — botão de "já recebi", declaração do vendedor, confirmação por tempo, liberação pelo dono — permitia concluir um pedido sem as duas pessoas frente a frente, que é justamente o que a prova existe para mostrar. Uma regra única e sem brecha protege mais que um conjunto de saídas, mesmo que cada saída pareça razoável sozinha |
| **QR na retirada, em vez de trocar senhas digitadas** | trocar senhas é uma operação em dois tempos, e quem vai depois pode simplesmente não ir — um lado entrega a prova dele e fica sem a do outro. O scan é uma ação só: quando o servidor o recebe, já sabe que as duas pessoas estavam juntas no mesmo segundo. Não há primeiro nem segundo para abandonar no meio |
| Token do QR de 20 segundos, uso único | print de tela mandado por mensagem chega morto, e QR não se lê por telefone — fecha o golpe de pedir o código à distância, que a senha falada só fechava em parte |
| Senha digitada fica como saída para quem não tem câmera | mesmo nível de prova, só mais lenta; escondida atrás de um botão, para quem tem câmera usar a câmera |
| "Senha primeiro, produto depois" escrito na tela | feita nessa ordem, a troca impede que qualquer um dos dois prejudique o outro; é a regra inteira em cinco palavras, e nenhuma tela conserta depois quem fez ao contrário |
| Senha nunca conferida cancela e reembolsa | é a consequência assumida de não ter caminho alternativo; cobrir esse caso exigiria abrir exatamente a porta que a regra única fecha |
| Lembretes em 24h e 48h enquanto a senha não é conferida | senha esquecida é o começo de quase todo problema deste capítulo, e insistir custa menos que arbitrar depois |
| A senha não vence | o vendedor que esqueceu de digitar na hora pede pelo chat depois; fechar essa porta criaria um problema onde não havia |
| Uma conta que compra e vende, sem cadastro de vendedor | é o modelo do Enjoei; cadastro grande no começo é onde a pessoa desiste, então os dados de vendedor são pedidos só na hora em que fazem falta |
| Dono reconhecido por e-mail em variável de ambiente | virar dono passa a exigir acesso ao servidor, não um clique no app; e como a comparação é com o e-mail que o Google confirmou, não dá para forjar |
| Mais de um e-mail de dono aceito desde o começo | conta perdida sem reserva cadastrada significa painel inacessível até alguém mexer no servidor |
| Repasse manual, não automático | devolução antes do repasse deixaria a plataforma no prejuízo |
| Saque agrupado em carteira | taxa de saque é do vendedor e não pode ser dividida |
