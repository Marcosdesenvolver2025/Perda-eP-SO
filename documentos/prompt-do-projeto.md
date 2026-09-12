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
ali anuncia o que não usa mais, quem quer comprar encontra pertinho de casa, e
a entrega pode ser feita por entregadores da própria operação.

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

A plataforma tem **quatro fontes de receita**, todas copiadas da mecânica do
Enjoei: comissão, tarifa fixa, taxa de entrega e taxa de saque.

### 1. Comissão — paga pelo vendedor

Dois tipos de anúncio, escolhidos pelo vendedor na hora de publicar:

| Anúncio | Comissão | O que ganha |
|---|---|---|
| **Clássico** (padrão) | **12%** | publicação normal |
| **Turbinado** | **18%** | aparece em destaque na vitrine, no topo da busca e nos carrosséis |

Venda mínima: **R$ 10,00**. A comissão vale nas duas modalidades de entrega.

O turbinado é escolha do vendedor, anúncio por anúncio, e pode ser ligado
depois de publicado. Mostre na tela quanto ele recebe em cada opção, lado a
lado, calculado ao vivo — a diferença tem que ficar óbvia antes de ele decidir.

### 2. Tarifa fixa por faixa de preço — paga pelo vendedor

Cobrada **só na entrega pela plataforma** — ela paga a operação da entrega.

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

### 3. Taxa de entrega — paga pelo COMPRADOR

**R$ 9,00, valor único dentro de Itinga**, cobrada no checkout **só quando a
entrega é pela plataforma**. Aparece separada do preço do produto, como no
Enjoei: "produto R$ 50,00 + entrega R$ 9,00 = R$ 59,00".

Na entrega pelo vendedor **não há taxa nenhuma** — não há entregador para pagar.

> **Por que o comprador paga, e não a plataforma.** É a diferença entre ter e
> não ter margem. O entregador recebe R$ 7,00 por corrida. Se esse custo saísse
> da comissão, toda venda abaixo de R$ 20,84 daria prejuízo — e contando a taxa
> do meio de pagamento, a faixa inteira até R$ 24,99 ficaria negativa. Com a
> taxa cobrada do comprador, **nenhuma faixa dá prejuízo**: sobram R$ 4,94 numa
> venda de R$ 10,00 e R$ 18,14 numa de R$ 100,00.

É valor único porque a cidade é uma só: calcular por distância dentro de Itinga
custaria mais em complexidade do que a diferença que geraria.

### 4. Taxa de saque — paga pelo vendedor

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

### O entregador

Recebe **R$ 7,00 por corrida concluída**, pago assim que confirma a entrega. O
serviço dele já foi prestado e não depende de o comprador aprovar o produto.
Sai da taxa de entrega cobrada do comprador, e fica **fora do split da
cobrança** — é um pagamento da plataforma para o entregador, não uma parte da
venda.

### Resumo de quem paga o quê, numa venda de R$ 100 pela plataforma

| | |
|---|---|
| Comprador paga | R$ 109,00 (produto + entrega) |
| Vendedor recebe | R$ 79,50 (produto − 12% − tarifa R$ 8,50) |
| Entregador recebe | R$ 7,00 |
| Meio de pagamento | ~R$ 4,36 |
| **Plataforma fica com** | **~R$ 18,14** |

---

## 3. As duas modalidades de entrega

O vendedor escolhe em cada anúncio, e a escolha **fica gravada no pedido**. Se
a regra mudar amanhã, o pedido de ontem mantém a regra com que foi vendido.

| | entrega pela plataforma | entrega pelo vendedor |
|---|---|---|
| Comissão | 12% (ou 18% turbinado) | 12% (ou 18% turbinado) |
| Tarifa fixa | por faixa | **não há** |
| Taxa de entrega (comprador) | **R$ 9,00** | **não há** |
| Limite | 20 kg · 100 cm largura · 100 cm altura | **sem limite** |
| Quem entrega | entregador nosso | o próprio vendedor |
| Prova de entrega | código digitado pelo entregador | código digitado pelo vendedor |
| Devolução | coleta reversa | combinada, admin confirma |

Quando o pacote não cabe, o app **bloqueia a opção da plataforma**, explica o
motivo em português ("pesa 25 kg e o limite é 20 kg") e cai sozinho para a
entrega pelo vendedor. O bloqueio dispara **no primeiro campo que estoura**,
sem esperar as outras medidas serem preenchidas — campo em branco vale zero e
zero nunca estoura limite.

O **comprimento** fica sem limite por padrão (`Infinity`), mas a constante
existe e está documentada: para barrar um cano de 3 m numa moto, basta trocar
por um número.

Na tela de novo anúncio, mostre as duas modalidades **lado a lado**, com o
valor que o vendedor recebe em cada uma, calculado ao vivo — e avise que na
entrega pela plataforma o comprador paga R$ 9,00 a mais, porque isso muda a
chance de a peça vender.

No checkout, a taxa de entrega aparece em **linha separada**, nunca somada
escondida no preço: "produto R$ 50,00 · entrega R$ 9,00 · total R$ 59,00".

---

## 4. Prazos e devolução

| Prazo | Quanto | Onde vale |
|---|---|---|
| **Entrega prometida ao comprador** | **4 dias** do pagamento, **pulando sábado e domingo** | entrega pela plataforma |
| **Cancelamento automático por atraso** | **7 dias corridos** do pagamento | sempre |
| Teste e devolução | **7 dias corridos** da entrega | sempre |
| Confirmação automática | **3 dias** da declaração | entrega pelo vendedor |
| Resposta a uma oferta | **3 dias** do último lance | negociação |

> **São três relógios diferentes. Não os misture.**
>
> | Relógio | Conta de | Dono | Estourou? |
> |---|---|---|---|
> | Promessa de entrega — 4 dias | pagamento | plataforma | atrasou, mas o pedido segue |
> | Cancelamento — 7 dias | pagamento | vendedor | pedido morre, comprador reembolsado |
> | Teste e devolução — 7 dias | **entrega** | comprador | acabou o direito de arrependimento |
>
> Os dois primeiros correm juntos, do mesmo ponto de partida. O terceiro só
> começa quando os outros dois já acabaram. **Prometer 4 e cancelar em 7 é de
> propósito:** são 3 dias de folga entre quebrar a promessa e matar o pedido,
> tempo de um entregador resolver um imprevisto sem o comprador perder a compra.

### Por que 7 dias, e por que integral

É o **artigo 49 do Código de Defesa do Consumidor**: compra feita fora do
estabelecimento comercial dá ao consumidor 7 dias corridos para desistir, com
devolução de **todos** os valores pagos. Não é escolha de produto, é lei.

Dentro dos 7 dias a devolução é **integral**: o comprador recebe 100% do que
pagou, nas duas modalidades — **incluindo a taxa de entrega de R$ 9,00**.
Comissão e tarifa **não** são descontadas. Quem absorve esse custo é a
plataforma, que ainda paga o entregador da ida e o da coleta reversa. Deixe as
chaves de retenção existindo no código, mas **desligadas**; reter dentro do
prazo legal é o tipo de economia que vira ação no Procon.

> O parágrafo único do art. 49 é explícito: voltam **quaisquer valores pagos, a
> qualquer título**, durante o prazo de reflexão. O frete é um desses valores.
> Devolver só o produto e segurar a entrega é ilegal, por mais que doa.

### A ordem importa

**O dinheiro só volta depois que o produto volta.** Na modalidade plataforma,
um entregador faz a coleta reversa; na modalidade vendedor, as partes combinam
e o admin confirma o retorno. Sem essa ordem, o comprador ficaria com o produto
e com o dinheiro.

---

## 5. A prova de entrega

Um **código de 4 dígitos** (a "senha da entrega") é gerado quando o pedido é
pago e aparece só para o comprador. Quem entrega pede a senha na mão do
comprador e digita no app. **Enquanto a senha não é digitada, o pedido não está
entregue e o relógio dos 7 dias não começa.** É o mesmo mecanismo que o Mercado
Livre usa.

Senha errada **não** conclui a entrega: o app diz que não confere e deixa
tentar de novo. Só a senha certa muda o estado do pedido.

### Entrega feita pelo próprio vendedor

O vendedor precisa comprovar **as duas coisas juntas**, na mesma tela:

1. **a senha de 4 dígitos** que o comprador informar; e
2. **uma foto** do produto entregue, tirada na hora pelo app.

Sem a foto o botão de concluir não habilita, mesmo com a senha certa. A foto
fica anexada ao pedido e é o que a plataforma tem para mediar se o comprador
disser depois que não recebeu.

Confirmada a senha, o pedido vira ENTREGUE e **começam os 7 dias de teste e
devolução que a lei exige**.

### As três portas até ENTREGUE

Todas passam pela **mesma função interna** — nenhum caminho pode esquecer de
abrir o prazo de teste:

| | Caminho | Exige |
|---|---|---|
| 1 | o vendedor digita a senha do comprador | **senha + foto** |
| 2 | o comprador toca em "já recebi" | nada — quem confirma é o dono do dinheiro |
| 3 | o vendedor declara sem a senha | **foto obrigatória**, e não conclui na hora |

A porta 3 existe só para o pedido não travar quando o comprador some. Ela
**não** entrega o pedido imediatamente: abre um aviso ao comprador, que tem
**3 dias** para confirmar ou abrir devolução. Passado o prazo em silêncio, o
sistema confirma sozinho. É deliberadamente o caminho mais lento e o único que
depende da foto como prova — porque é o único em que o comprador não participou.

---

## 6. O entregador da plataforma

A entrega pela plataforma é feita por **entregadores da nossa própria empresa**,
dentro de Itinga. O comprador não fala com transportadora nem com Correios: é
gente nossa que busca e leva.

### O endereço de coleta

Todo anúncio com entrega pela plataforma precisa de um **endereço de coleta**,
informado pelo vendedor. É de onde o entregador tira o produto.

- o vendedor cadastra o endereço uma vez no perfil e ele vem preenchido nos
  próximos anúncios, podendo ser trocado por anúncio;
- **sem endereço de coleta, a opção "entrega pela plataforma" não fica
  disponível** — o anúncio só pode ser publicado com entrega pelo vendedor;
- o endereço tem rua, número, bairro, ponto de referência e um campo de
  observação ("portão azul", "falar com a Dona Maria"). Numa cidade pequena o
  ponto de referência vale mais que o CEP;
- **o endereço de coleta só aparece para o entregador depois que ele aceita a
  corrida.** Antes disso fica mascarado, pela mesma razão do telefone.

### Como o entregador pega a corrida

O entregador abre o app e vê o **mural**: a lista das entregas disponíveis, cada
uma com bairro de coleta, bairro de entrega, distância aproximada e o valor da
corrida. Toca em **"pegar essa entrega"** e ela é dele.

**A partir daí a corrida some do mural de todo mundo.** Nenhum outro entregador
consegue pegar, abrir ou concluir aquela entrega. Cada corrida tem um dono só.

> **A armadilha aqui é uma corrida de dois cliques simultâneos**, e ela vai
> acontecer: dois entregadores tocam no mesmo item no mesmo segundo. Se o código
> for "leio se está livre, depois gravo meu nome", os dois leem livre e os dois
> gravam — o segundo sobrescreve o primeiro e a bagunça está feita.
>
> **Faça a tomada em uma operação só, condicional**, do tipo:
>
> ```sql
> UPDATE corrida SET entregador_id = :eu, estado = 'ATRIBUIDA'
>  WHERE id = :corrida AND entregador_id IS NULL
> ```
>
> e **confira quantas linhas mudaram**. Uma linha: a corrida é sua. Zero linhas:
> outro chegou antes, e a tela responde *"essa entrega já foi pega por outro
> entregador"* e some com o item da lista. Nunca leia-e-depois-grave.
>
> Escreva um teste que dispara duas tomadas ao mesmo tempo na mesma corrida e
> exige que **exatamente uma** vença.

Depois de pegar, só esse entregador vê os endereços completos e só ele consegue
mover a corrida. Se não puder mais fazer, ele **devolve a corrida** — ela volta
ao mural e o motivo fica registrado. Devolver depois de já ter coletado o
produto não é possível pelo app: aí é caso para o dono resolver na mão.

### O trajeto

```
vendedor (endereço de coleta) ──► entregador ──► comprador (endereço de entrega)
```

O entregador vê os dois endereços na tela da corrida, em sequência: primeiro
busca no vendedor, depois despacha para o comprador. Um passo por vez, sem
mostrar a etapa seguinte antes de a atual estar concluída.

### O prazo prometido ao comprador — "chega até"

Como as outras plataformas fazem, o comprador vê uma **data de entrega** desde
antes de comprar:

| Onde | O que mostra |
|---|---|
| Tela do produto | "chega até **<data>**" — estimativa, calculada como hoje + 4 dias |
| Checkout | a mesma data, agora como promessa |
| Pedido | "chega até **<data>**", contando de quando o pagamento foi aprovado |

**A data é pagamento + 4 dias, pulando sábado e domingo.** Dentro de uma cidade
a entrega real leva um ou dois dias — prometer 4 dá folga para um imprevisto sem
soar lento. É melhor entregar antes do prometido do que explicar atraso. Não
invente cálculo por distância.

### Sábado e domingo

**A entrega no fim de semana não é obrigatória, mas pode acontecer.**

Deixe isso escrito na tela, com estas palavras ou parecidas:

> *não garantimos entrega aos sábados e domingos, mas se rolar entregador
> disponível, seu pedido pode chegar antes.*

O aviso aparece no checkout, junto da data prometida, e na tela do pedido.

O que isso significa no código:

- **a contagem dos 4 dias da promessa pula sábado e domingo.** Comprou na
  quinta, a data prometida cai na quarta seguinte, não na segunda. Prometer
  dia que a operação não roda é quebrar a promessa de propósito;
- **o entregador pode aceitar e concluir corrida em qualquer dia**, inclusive
  fim de semana e feriado. Nada no sistema bloqueia isso — a entrega de sábado
  é bem-vinda, só não é prometida;
- **a entrega pelo vendedor não tem essa restrição**: ele combina direto com o
  comprador e entrega no dia que os dois quiserem.

> **Por que a promessa pula o fim de semana e o cancelamento não.** São coisas
> diferentes: a promessa é o que a plataforma se compromete a cumprir, então
> conta só os dias em que ela opera. O cancelamento é o teto de quanto tempo o
> dinheiro do comprador pode ficar preso, e para ele todo dia conta igual —
> 7 dias corridos sempre contêm pelo menos 5 dias úteis, tempo de sobra para o
> vendedor ter entregado o produto.

> Guarde a data prometida **no pedido**, não a recalcule na tela. Se a regra
> mudar amanhã, o pedido de ontem tem que continuar mostrando a data com que
> foi vendido — mesma lógica da modalidade congelada.

### Cancelamento automático em 7 dias

**Se o produto não sair do vendedor em 7 dias corridos contados do pagamento, o
pedido é cancelado sozinho e o comprador recebe tudo de volta.**

Vale nas duas modalidades, e o que conta é diferente em cada uma:

| Modalidade | O que precisa acontecer em 7 dias |
|---|---|
| Entrega pela plataforma | o entregador ter **coletado** o produto com o vendedor |
| Entrega pelo vendedor | o pedido ter chegado em **ENTREGUE** |

O que o cancelamento faz:

1. estorna **100%** do que o comprador pagou, **incluindo a taxa de entrega**;
2. libera o anúncio de volta (ou o mantém pausado, se o vendedor sumiu);
3. registra o motivo no pedido como `CANCELADO_POR_ATRASO_DO_VENDEDOR`, para o
   histórico do vendedor;
4. avisa as duas partes.

> **Por que esse estorno é simples.** O dinheiro nunca saiu da conta da
> plataforma — o `Transfer` para o vendedor só é criado depois dos 7 dias de
> teste, que nem chegaram a começar. Cancelar antes da entrega é só um `Refund`,
> sem nada para reverter. É a vantagem de reter ser o estado padrão.

Avise antes de cancelar, não só depois: no **5º dia** o vendedor recebe uma
notificação de que faltam 2 dias e o pedido será cancelado. Cancelar sem avisar
é como se perde vendedor.

O job que faz isso roda de hora em hora, junto com o de repasse.

---

## 7. A máquina de estados da entrega

Uma máquina de estados própria, que **não toca em dinheiro**:

```
pago → aguardando atribuição → atribuída → aceita → a caminho da coleta
     → chegou → coletado (exige foto + volumes) → em rota → chegou
     → entregue (exige senha de 4 dígitos)
```

De qualquer estado anterior a **coletado**, o pedido pode ir para
**cancelado por atraso** — é a única transição que o relógio dispara sozinho.

Três regras que precisam estar no código, não só no documento:

- **transição desconhecida é recusada por padrão.** Um app desatualizado não
  pode inventar um movimento novo nem pular etapa;
- **guarda de ator**: cada transição só é permitida a quem tem o papel certo;
- o **telefone do cliente fica mascarado** até o entregador aceitar a corrida —
  senão o cadastro de entregador vira uma lista de contatos da cidade.

Atribuição de corrida por **estratégia** (padrão Strategy): manual na v1, com a
estratégia **por distância escrita e testada**, desligada por uma linha.

---

## 8. Contas, papéis e quem enxerga o quê

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

### Os três papéis

| Papel | Quem é | Como vira |
|---|---|---|
| **Usuário** | comprador e vendedor, a mesma conta | entrou com o Google |
| **Entregador** | parceiro que faz as corridas | o dono autoriza o e-mail dele |
| **Dono (admin)** | o dono da operação | e-mail na variável de ambiente do servidor |

Entregador e admin **não se cadastram sozinhos**, e **não existe nenhuma tela
que promova alguém a qualquer um dos dois**. Se existir, é buraco de segurança,
não funcionalidade.

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

### Como um entregador é autorizado

Ninguém vira entregador por conta própria. **Quem cadastra é o dono, com a
pessoa na frente dele.** O caminho é um só:

1. no painel, o dono abre **"entregadores"** e toca em **autorizar**;
2. preenche a ficha da pessoa que ele contratou;
3. o e-mail entra na lista de autorizados, com estado **pendente**;
4. quando essa pessoa entra no app com aquele mesmo Google, o servidor vê o
   e-mail verificado na lista e a conta ganha o papel de entregador;
5. só a partir daí ela enxerga o mural de corridas.

### A ficha do entregador

Preenchida pelo dono, no painel, na hora de autorizar:

| Campo | Obrigatório | Para quê |
|---|---|---|
| **Foto do rosto** | sim | tirada na hora pelo dono. É o que identifica quem está na porta do cliente |
| **Nome completo** | sim | identificação |
| **CPF** | sim | é quem responde por um produto que sumir |
| **Telefone** | sim | contato da operação |
| **E-mail Google** | sim | é a chave da autorização: tem que ser o mesmo com que a pessoa vai entrar |

Sem os cinco preenchidos, **não autoriza**. Meia ficha é ficha que não serve no
dia em que precisar.

O entregador aceita as **normas da empresa** na primeira vez que abre o app.
Fica registrada a data do aceite e a versão do texto aceito.

> **Esses dados são dados pessoais e precisam ser tratados como tais** (LGPD).
> Regras mínimas na implementação:
>
> - **só o dono lê a ficha completa.** Não existe rota que devolva CPF, telefone
>   ou foto de um entregador para outro entregador, para vendedor ou para
>   comprador. Nem "por engano" dentro de um objeto maior — cheque o que a API
>   devolve, não só o que a tela mostra;
> - a **foto vai para armazenamento privado**, com URL assinada de validade
>   curta. Nunca num endereço público que qualquer um adivinha;
> - o **CPF fica cifrado no banco** e aparece mascarado na tela
>   (`***.456.789-**`), com o valor inteiro só quando o dono pede para ver;
> - o comprador **nunca** recebe CPF nem telefone do entregador. O que ele vê é
>   o primeiro nome e a foto do rosto — o suficiente para saber quem bateu na
>   porta dele, e nada além;
> - entregador removido tem a ficha **arquivada, não apagada**: você precisa
>   saber quem fez as entregas de seis meses atrás. Mas a foto e o CPF saem de
>   circulação assim que ele deixa de ser entregador.

Enquanto o dono não autorizar, a pessoa é usuário comum: compra e vende
normalmente, e **o mural de corridas simplesmente não existe para ela** — nem a
tela, nem as rotas.

### A chave Pix do entregador

O entregador recebe as corridas dele por **Pix**, numa chave que **ele mesmo
cadastra e pode trocar**, na área dele do app.

**A chave não é digitada — é escolhida.** O app mostra as três opções, já
preenchidas com o que está na ficha dele, e ele marca qual quer usar:

| Opção | Vem de |
|---|---|
| **CPF** | o CPF da ficha |
| **Telefone** | o telefone da ficha |
| **E-mail** | o Gmail com que ele entra no app |

Como funciona:

1. na primeira vez que ele entra, o app pede que escolha uma das três — **sem
   chave escolhida ele até pega corrida, mas não tem como receber**, e o app
   avisa isso na tela;
2. antes de salvar, a tela repete em letra grande qual chave vai ser gravada;
3. pode trocar entre as três quando quiser, refazendo a confirmação.

> **Escolher em vez de digitar resolve dois problemas de uma vez.**
>
> O primeiro é o erro de digitação: Pix mandado para chave errada cai na conta
> de um desconhecido e não volta sozinho. Se a chave só pode ser um dos três
> valores já conferidos, não existe dígito trocado.
>
> O segundo é fraude. Trocar chave Pix é o caminho preferido de quem rouba
> conta de entregador: toma o acesso, aponta o Pix para si e passa a receber no
> lugar da pessoa. **Aqui isso não funciona** — as três opções são dados que o
> dono cadastrou na ficha, então não há para onde apontar o dinheiro. Para
> desviar o pagamento seria preciso mudar a ficha, e ficha só o dono muda.
>
> Mesmo assim, mantenha duas travas: **toda troca avisa na hora o entregador e
> o dono**, e fica registrado **o histórico** — qual chave era, qual virou e
> quando.

Se o entregador quiser receber numa chave que não é nenhuma das três, o caminho
é falar com o dono e **atualizar a ficha** — não existe campo livre. É de
propósito: o dinheiro só vai para um dado que a empresa conferiu.

### Como o entregador é pago

O que ele ganha **acumula numa carteira** dentro do app: R$ 7,00 por corrida
concluída, creditado assim que ele confirma a entrega.

| Onde | O que mostra |
|---|---|
| App do entregador | quanto ele já juntou, corrida por corrida, e o que já foi pago |
| Painel do dono | quanto deve a cada entregador, com a chave Pix de cada um |

O dono paga por Pix e **marca como pago no painel**, com data e valor. O
sistema não dispara Pix sozinho: o dono aperta, confere e paga.

> **Acumule em vez de pagar corrida por corrida.** Mandar um Pix de R$ 7,00 por
> entrega é operação demais para valor de menos. Pague por semana, ou quando o
> entregador pedir a partir de um mínimo. O que importa é a carteira estar
> sempre certa e visível para os dois lados — o entregador precisa poder
> conferir a conta dele sem perguntar nada a ninguém.

**O pagamento do entregador não passa pela Stripe.** É Pix da conta da empresa
para a conta dele, e o app é só o caderno que registra o que foi feito.

### Recados para os entregadores

O dono fala com a equipe **de dentro da plataforma**, sem depender de aplicativo
de terceiro. Dois canais:

**1. Mural da equipe** — o grupo. O dono publica um recado e **todos os
entregadores autorizados veem**, com notificação:

> *"hoje tem feira no centro, rua principal fechada até as 14h"*
> *"a partir de segunda, foto do produto obrigatória também na coleta"*

Os entregadores podem responder no mural, e uns veem as respostas dos outros —
é um grupo, e é assim que ele serve para combinar as coisas.

**2. Conversa individual** — dono e um entregador, só os dois. Para o que não é
assunto de todo mundo: um atraso, uma cobrança, um acerto.

Regras dos dois canais:

- **quem abre conversa é sempre o dono.** O entregador responde, não inicia;
- entregador **não fala com outro entregador** em particular pela plataforma —
  só no mural, à vista de todos;
- entregador **suspenso perde os dois canais na hora**, e o histórico fica
  guardado para o dono;
- o mural **não é o lugar de dado de cliente**. Endereço e telefone de comprador
  aparecem na corrida, para quem pegou aquela corrida — nunca num recado que a
  equipe inteira lê.

### Suspender e remover entregador

O dono **suspende ou remove com um toque**, e vale na hora:

| O que acontece | Efeito imediato |
|---|---|
| Perde o mural | não vê nem consegue pegar corrida nova |
| Perde as rotas | qualquer chamada de entregador passa a ser recusada pelo servidor |
| Corrida já pega, ainda não coletada | volta sozinha para o mural |
| Corrida com produto já coletado | **fica marcada no painel para o dono resolver na mão** — tem produto de gente com essa pessoa, e isso não se resolve sozinho |

> Suspender é reversível e remover não deveria apagar histórico: as corridas
> que a pessoa fez continuam registradas, senão você perde o rastro de quem
> entregou o quê.

### Registro de quem fez o quê

**Toda mudança de estado de uma corrida grava quem fez e quando.** Sem isso, o
dia em que um produto sumir não há como saber por onde ele passou. O painel
mostra esse histórico em cada pedido, em ordem.

### Quem enxerga o quê

Esta tabela é regra de autorização, não sugestão de interface. **Cheque no
servidor, em toda rota.** Esconder o botão na tela não é proteger o dado.

| Informação | Usuário | Entregador | Dono |
|---|---|---|---|
| Anúncios, vitrine, busca | ✅ | ✅ | ✅ |
| Os próprios pedidos e vendas | ✅ | — | ✅ |
| Mural de corridas disponíveis | ❌ | ✅ | ✅ |
| Endereço completo de coleta e entrega | ❌ | **só da corrida que ele pegou** | ✅ |
| Telefone do cliente | ❌ | **só depois de aceitar a corrida** | ✅ |
| **Qual entregador está com qual pedido** | ❌ | só o dele | ✅ **só você** |
| **Onde está cada produto, no mapa da operação** | ❌ | só o dele | ✅ **só você** |
| Painel com a fila inteira de entregas | ❌ | ❌ | ✅ |
| Devoluções, estornos e repasses | ❌ | ❌ | ✅ |
| Faturamento e comissões | ❌ | ❌ | ✅ |
| Entregas concluídas, o histórico inteiro | ❌ | só as dele | ✅ |
| **Ficha do entregador: CPF, telefone, foto** | ❌ | só a própria | ✅ **só você** |
| Chave Pix do entregador | ❌ | **só a própria** — cadastra e troca | ✅ vê a de todos, para pagar |
| Carteira do entregador: quanto ele tem a receber | ❌ | só a própria | ✅ de todos |
| Cadastrar, suspender e remover entregador | ❌ | ❌ | ✅ |
| Mural da equipe de entregadores | ❌ | ✅ ler e responder | ✅ publicar |

### O painel do dono

Só você abre. É onde a operação inteira aparece de uma vez:

- **a fila de entregas**, com o estado de cada uma e há quanto tempo está parada;
- **qual entregador está com qual pedido**, e onde o produto está no trajeto;
- as corridas paradas há tempo demais, em destaque;
- os pedidos perto do cancelamento automático;
- as devoluções esperando decisão;
- o dinheiro: retido, a repassar, repassado.

> **O que o comprador vê do entregador.** Depois que a corrida é pega, o
> comprador vê no seu pedido **o primeiro nome do entregador e o estado da
> entrega** — é o que outros apps fazem e evita a ligação de "cadê meu produto".
> O que ele **não** vê é o resto da operação: quantos entregadores existem, o
> que cada um está levando, ou o endereço de coleta do vendedor. O quadro
> completo é seu.

---

## 9. As funcionalidades que vêm do Enjoei

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

## 10. As telas

### As cinco abas

| Aba | O que tem |
|---|---|
| home | vitrine com carrosséis, busca e categorias |
| buscar | busca com filtros de preço e estado |
| vendas | a lojinha, saldo a receber e dicas |
| notificações | negociações e mensagens |
| minha conta | perfil, atalhos e configurações |

### As 25 telas

**Comprar:** Home, Busca, Produto, Loja, Checkout, Pedido (com linha do tempo),
Reembolso, Curtidos, FazerOferta, Ofertas, Avaliar, CodigoDeConfirmacao

**Vender:** Vendas, NovoAnuncio, MinhaLoja, MinhasVendas, EntregaDoVendedor

**Entregar:** AreaDoEntregador (corridas passo a passo), PassoDaEntrega

**Administrar:** PainelAdmin (fila de entregas, atribuição, devoluções)

**Conta:** Entrar, MinhaConta, Configuracoes, DadosPessoais, Enderecos,
ContaDeRecebimento, ComoFunciona, Conversa

Login com **Google**. Cadastro de conta de recebimento do vendedor dentro do
app.

---

## 11. A identidade visual — como não parecer cópia

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

## 12. Pagamento — deixe pronto, não ligue

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
| Cobrança | `PaymentIntent` na conta da plataforma, valor = produto + taxa de entrega |
| Escrow | o dinheiro **fica na plataforma**; nenhum `Transfer` é criado antes dos 7 dias |
| Repasse | `Transfer` para o connected account, disparado por job de hora em hora que procura pedido com prazo vencido e sem devolução aberta |
| Taxa de entrega | fica **inteira com a plataforma**, nunca entra no `Transfer` do vendedor; o entregador é pago à parte |
| Estorno | `Refund` do `PaymentIntent`. Como ainda não houve `Transfer` dentro dos 7 dias, **não há transferência para reverter** — é só estornar. Se por algum motivo o repasse já saiu, use `reverse_transfer` |
| Idempotência | header `Idempotency-Key` em **toda** chamada que mexe em dinheiro |
| Webhook | verificação de assinatura com `Stripe-Signature` e o webhook secret (`stripe.webhooks.constructEvent`), **nunca** confiando no corpo sem validar; guarde o `event.id` e ignore repetido |
| Eventos a tratar | `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`, `transfer.created`, `account.updated` |
| Pagamento do entregador | `Transfer` separado, fora do fluxo da venda |
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
do repasse já deixa claro o que é receita da plataforma (comissão, tarifa e
taxa de entrega) e o que é apenas dinheiro de passagem do vendedor.

---

## 13. Como o código tem que estar organizado

### Regra pura, separada de tudo

`servidor/src/dominio/` **não importa banco nem HTTP**. Por isso o fluxo inteiro
é testável sem subir nada.

| Módulo | O que decide |
|---|---|
| `regras.ts` | comissão, tarifa por faixa, limites, prazos |
| `comissao.ts` | como o split é montado |
| `ofertas.ts` | quem pode propor o quê, e qual valor vale |
| `logistica.ts` | a máquina de estados da entrega |
| `reembolso.ts` | quanto volta e de qual saldo |
| `frete.ts` | se o pacote cabe na entrega da plataforma |
| `atribuicao.ts` | qual entregador recebe a corrida |

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

## 14. Publicação

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

## 15. O que fica pendente do lado do dono

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

## 16. Restrições

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

## 17. Decisões já encerradas

Para ninguém refazer discussão já resolvida.

| Decisão | Por quê |
|---|---|
| 7 dias de teste, não 4 | é a lei (CDC art. 49), não é escolha |
| Devolução integral | mesmo motivo; reter dentro do prazo vira ação no Procon |
| 12% + tarifa por faixa, não 16/18% fixo | percentual alto assusta em produto caro; a tarifa cobre a operação no produto barato |
| **Comprador paga a entrega, não a plataforma** | **revertido em setembro/2026.** A ideia original era embutir o frete e mostrar preço final. A conta provou que não fecha: com o entregador custando R$ 5,00 na época, toda venda abaixo de R$ 20,84 dava prejuízo, e a faixa inteira até R$ 24,99 ficava negativa contando o meio de pagamento. É a mecânica do Enjoei, e é o que dá margem em toda faixa |
| Taxa de entrega única de R$ 9,00 | a cidade é uma só; calcular por distância dentro de Itinga custa mais em complexidade do que a diferença que geraria |
| Anúncio turbinado a 18% | mesma mecânica do Enjoei: quem quer aparecer mais paga mais, e é escolha do vendedor |
| Taxa de saque de R$ 3,00, primeiro grátis no mês | copiada do Enjoei; quem saca uma vez por mês não sente taxa nenhuma |
| 100 cm de largura e altura, não 60 | 60 cm barrava item comum de casa |
| Verde, não roxo | o Enjoei é roxo; o verde é o maior diferenciador visual que o app tem |
| **Stripe no lugar da pagar.me** | **decidido em setembro/2026 pelo dono.** O padrão passa a ser *separate charges and transfers*, que dá o escrow dos 7 dias sem depender de configuração de recebedor — reter vira o estado padrão. Falta confirmar que o Connect aceita vendedor PF com CPF |
| Modalidade congelada no pedido | mudar a regra amanhã não pode mexer no pedido de ontem |
| Estorno só depois do produto voltar | senão o comprador fica com o produto e com o dinheiro |
| Senha de 4 dígitos + foto na entrega do vendedor | mesmo mecanismo do Mercado Livre; a senha prova que o comprador estava lá, a foto prova o que foi entregue. Sem as duas, a plataforma não tem como mediar um "eu não recebi" |
| Prazo prometido fixo de 4 dias, sem cálculo por distância | dentro de uma cidade a entrega leva um ou dois dias; prometer 4 dá folga para imprevisto sem soar lento, e entregar antes do combinado não gera reclamação |
| Prometer em 4 mas só cancelar em 7 | 3 dias de folga entre quebrar a promessa e matar o pedido; um imprevisto de entregador não pode custar a venda ao vendedor nem a compra ao comprador |
| Fim de semana não é obrigatório, mas é permitido | a operação é pequena e não dá para garantir escala de sábado; travar a entrega no sistema seria pior, porque perderia a corrida que o entregador aceitaria de bom grado |
| A promessa pula o fim de semana, o cancelamento não | a promessa conta só os dias em que a plataforma opera, senão ela nasce quebrada; o cancelamento é teto de quanto tempo o dinheiro do comprador fica preso, e para isso todo dia conta igual |
| Cancelamento automático em 7 dias sem coleta | vendedor que some deixa o dinheiro do comprador preso; o relógio resolve sozinho, sem ninguém precisar abrir chamado |
| Aviso ao vendedor no 5º dia | cancelar sem avisar é como se perde vendedor; 2 dias é tempo de reagir |
| Corrida tomada por `UPDATE` condicional, não por ler-e-gravar | dois entregadores tocando no mesmo segundo é questão de tempo, e ler-e-gravar deixa os dois vencerem; a condição no `WHERE` é o que garante um dono só |
| Uma conta que compra e vende, sem cadastro de vendedor | é o modelo do Enjoei; cadastro grande no começo é onde a pessoa desiste, então os dados de vendedor são pedidos só na hora em que fazem falta |
| Entregador e admin não se cadastram sozinhos | o dono habilita quem contratou; tela que promove alguém a admin é buraco de segurança, não funcionalidade |
| Dono reconhecido por e-mail em variável de ambiente | virar dono passa a exigir acesso ao servidor, não um clique no app; e como a comparação é com o e-mail que o Google confirmou, não dá para forjar |
| Mais de um e-mail de dono aceito desde o começo | conta perdida sem reserva cadastrada significa painel inacessível até alguém mexer no servidor |
| Entregador autorizado por e-mail, com estado pendente | o dono autoriza antes de a pessoa entrar; enquanto isso ela é usuário comum e o mural não existe para ela |
| Corrida já coletada não volta sozinha na suspensão | tem produto de gente com a pessoa; isso é caso para o dono resolver na mão, não para o sistema decidir |
| Ficha do entregador preenchida pelo dono, com a pessoa presente | foto tirada na hora e CPF conferido pessoalmente valem mais que formulário preenchido de casa; é quem responde por um produto que sumir |
| CPF cifrado, foto em armazenamento privado, ficha só do dono | são dados pessoais de terceiro sob a guarda da empresa; vazar CPF de entregador é problema da plataforma, não dele |
| Ficha arquivada em vez de apagada | é preciso saber quem entregou o quê seis meses atrás, mesmo que a pessoa não trabalhe mais |
| Recados dentro da plataforma, não em app de terceiro | o histórico fica com a empresa, e entregador suspenso perde o canal na hora — coisa que grupo de mensagem externo não faz |
| Entregador não inicia conversa nem fala com outro em particular | quem abre canal é o dono; conversa paralela entre entregadores fora da vista vira combinação que a operação não enxerga |
| Chave Pix escolhida entre CPF, telefone e Gmail da ficha, sem campo livre | mata o erro de digitação e mata o golpe de trocar a chave para receber no lugar do entregador: não há para onde apontar o dinheiro, porque os três valores são os que o dono conferiu |
| Entregador recebe R$ 7,00 e o comprador paga R$ 9,00 de entrega | sobram R$ 2,00 por corrida para a plataforma; paga-se melhor o entregador do que a margem por entrega pediria, porque entregador que some é problema pior que margem menor |
| Pagamento do entregador acumulado, pago por Pix fora da Stripe | Pix de R$ 7,00 por corrida é operação demais para valor de menos; e o pagamento do entregador não é parte da venda |
| O sistema não dispara Pix sozinho | o dono confere e paga; automatizar saída de dinheiro sem conferência é o tipo de facilidade que custa caro uma vez só |
| O quadro completo da operação é só do dono | o comprador vê o primeiro nome de quem leva o pedido dele e mais nada; quem está com o quê, e onde, é informação da operação |
| Repasse manual, não automático | devolução antes do repasse deixaria a plataforma no prejuízo |
| Saque agrupado em carteira | taxa de saque é do vendedor e não pode ser dividida |
