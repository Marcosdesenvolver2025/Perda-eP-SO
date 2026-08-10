# Qual split de pagamento usar no Vendas Itinga

Decisão de arquitetura financeira, escrita em agosto de 2026.

**Resposta curta:** fique na **pagar.me**, mas mude **uma coisa** no desenho —
pare de repassar por pedido e passe a creditar uma **carteira**, com saque
agrupado. Com ticket de R$ 80, é isso que separa um modelo saudável de um que
entrega o dinheiro do vendedor picado pela taxa fixa.

---

## Os 7 requisitos, um a um

| # | Requisito | pagar.me atende? | Onde está no código |
|---|---|---|---|
| 1 | Split físico na liquidação | **Sim** | `servidor/src/dominio/comissao.ts` |
| 2 | Retenção programável (escrow) | **Sim**, via `transfer_enabled: false` | `servidor/src/servicos/repasse.ts` |
| 3 | Recebedor PF (CPF) | **Sim, e sem o vendedor criar conta** | `servidor/src/rotas/recebedores.ts` |
| 4 | Estorno parcial + chargeback + split editável | **Sim** (confirmar edição pós-autorização) | `servidor/src/servicos/reembolso.ts` |
| 5 | Custo baixo por transação | **Depende do desenho** — ver abaixo | precisa mudar |
| 6 | Preparo para split payment fiscal (IBS/CBS) | **Sim, e é o provedor que executa** | nada a fazer no código |
| 7 | Corresponsabilidade por NF do vendedor | **Não é do provedor** — é sua | precisa de regra nova |

---

## 1. Split físico — resolvido, e é o ponto mais importante

O split da pagar.me acontece **no momento em que a transação é criada**, e cada
recebedor tem um saldo próprio dentro da pagar.me. O dinheiro do vendedor
**nunca entra na conta do seu CNPJ**.

Isso resolve os dois riscos que você levantou:

- **Imposto sobre o GMV inteiro.** Sua receita tributável é a comissão + a
  margem do frete, não os R$ 80 da venda. Se o valor cheio caísse na sua conta
  e você repassasse depois, o Fisco enxergaria R$ 80 de receita — no Simples ou
  no Presumido isso destrói a operação.
- **Risco regulatório.** Guardar dinheiro de terceiro na sua conta é atividade
  de instituição de pagamento, que exige autorização do Banco Central. Com o
  split físico, quem custodia é a pagar.me (que é autorizada) — você só orquestra.

> **Regra de ouro:** nenhum centavo de venda pode passar pela conta bancária do
> seu CNPJ. Se um dia alguém sugerir "recebe tudo e repassa depois", diga não.

---

## 2. Retenção de 7 dias — resolvido

Os recebedores são criados com `transfer_settings.transfer_enabled: false`. O
dinheiro fica parado no saldo do vendedor até você mandar sacar.

O ciclo horário (`servidor/src/servicos/tarefas.ts`) procura pedidos com
`prazoTesteAte` vencido e sem devolução aberta, e só aí libera.

Por que isso importa: se o dinheiro saísse antes dos 7 dias, uma devolução
deixaria você com saldo negativo para cobrir e correndo atrás do vendedor.

---

## 3. Recebedor PF — e aqui a pagar.me ganha das concorrentes

Este é o critério que **decide** a escolha para o seu caso, e é fácil de passar
batido.

Na pagar.me, **você cria o recebedor pela API em nome do vendedor**. O vizinho
que vai vender uma bicicleta preenche banco, agência e conta dentro do
Vendas Itinga e pronto — ele nunca sai do seu app.

Já a Asaas, por exemplo, exige que **todos os envolvidos tenham conta na Asaas**
para participar do split, e a criação de subcontas é permitida **apenas para
CNPJ** (Resoluções Conjuntas nº 16 e 17 do Banco Central para BaaS). Para um
marketplace B2B isso é aceitável. Para vizinho vendendo em cidade pequena, é
matador: você perderia metade dos vendedores no cadastro.

| Provedor | PF como recebedor | Vendedor precisa criar conta própria? |
|---|---|---|
| **pagar.me** | sim, via API | **não** |
| Asaas | sim como recebedor, mas subconta só PJ | **sim** |
| Iugu | sim | sim |
| Mercado Pago | sim | sim (precisa conectar a conta MP dele) |

Para C2C de bairro, "não precisa criar conta em lugar nenhum" vale mais do que
meio ponto percentual de tarifa.

---

## 4. Estorno, chargeback e split editável

Já implementado:

- **Estorno parcial** com `split_rules` explícito, para cada centavo sair do
  saldo certo — sem isso a pagar.me rateia proporcionalmente e debitaria o
  entregador, que não pode ser penalizado por problema do produto.
- **Chargeback**: a flag `liable` define quem responde. Hoje plataforma e
  vendedor são `liable`; o entregador nunca.
- **Taxa de processamento**: `charge_processing_fee: true` só na plataforma —
  o MDR sai da sua comissão, não do vendedor.

**A confirmar em homologação:** edição do split **depois** da autorização.
A pagar.me evolui esse campo na v5 e a documentação nem sempre acompanha.
Cenário real onde você vai precisar: o vendedor não tem o produto e você troca
por outro de preço diferente sem cancelar a cobrança. Se não der para editar,
o contorno é cancelar e recobrar.

---

## 5. Custo por transação — **é aqui que o desenho atual precisa mudar**

### O problema da taxa fixa de saque

Na pagar.me, a **taxa de saque é cobrada do recebedor que faz a transferência e
não pode ser dividida por regra de split**. Ou seja: ela sai do bolso do
vendedor, não do seu.

Isso parece bom para você, mas com ticket de R$ 80 vira um problema de produto —
o vendedor vê um valor menor cair na conta e conclui que o app "come" o dinheiro
dele.

**Repasse por pedido (o desenho atual):**

| | Valor |
|---|---|
| Venda | R$ 80,00 |
| Comissão 18% | − R$ 14,40 |
| Bruto do vendedor | R$ 65,60 |
| Taxa de saque (≈ R$ 3,67 — **confirme a sua**) | − R$ 3,67 |
| **Vendedor recebe** | **R$ 61,93** |
| Peso real sentido pelo vendedor | **22,6%**, não 18% |

**Carteira com saque agrupado (o desenho que eu recomendo):**

| | Valor |
|---|---|
| 5 vendas de R$ 80 | R$ 400,00 |
| Comissão 18% | − R$ 72,00 |
| Saldo em carteira | R$ 328,00 |
| Uma taxa de saque | − R$ 3,67 |
| **Vendedor recebe** | **R$ 324,33** |
| Peso real | **18,9%** |

Mesma tarifa, mesma comissão. Só muda **quando** o dinheiro sai.

### O que muda no produto

1. O vendedor tem um **saldo** na tela "minhas vendas" (já existe o campo
   `aReceber` — vira `saldoDisponivel`).
2. Cada venda vence os 7 dias e o valor **fica disponível na carteira**, sem
   sacar.
3. O vendedor toca em **"sacar"** quando quiser, com **mínimo de R$ 50**.
4. Alternativa que agrada mais: **saque automático toda sexta-feira**, um por
   semana, agrupando tudo que venceu. Menos toque, mesmo efeito.

É assim que enjoei, OLX e Shopee fazem. Não é economia sua — é economia do
vendedor, e é o que faz ele voltar a anunciar.

### Pix vs cartão — decisão de margem, não de conveniência

O MDR sai da **sua** comissão (`charge_processing_fee: true` na plataforma).
Com ticket baixo isso pesa muito:

| Meio | Custo aprox. | Sobra da sua comissão de R$ 14,40 |
|---|---|---|
| **Pix** (~1%) | R$ 0,80 | **R$ 13,60** |
| Cartão à vista (~3,5–4,5%) | R$ 3,20 | R$ 11,20 |
| Cartão 6x com antecipação | R$ 5,00+ | **R$ 9,40 ou menos** |

Um cartão parcelado em ticket de R$ 80 pode levar **35% da sua comissão**.

**Recomendação:** deixe o **Pix como opção padrão** na tela de pagamento (o app
já abre nele), e considere liberar parcelamento só acima de um valor —
R$ 150, por exemplo. Abaixo disso, cartão à vista ou Pix.

> Confirme suas tarifas reais com o gerente da pagar.me antes de fechar as
> contas acima. Tarifa de marketplace é negociada, e os números aqui são a
> ordem de grandeza pública, não a sua tabela.

---

## 6. Split payment fiscal (IBS/CBS) — você não precisa fazer nada no código

Como funciona: o próprio arranjo de pagamento (banco, instituição de pagamento,
Pix, boleto) segrega e recolhe o IBS e a CBS **no momento da liquidação
financeira**, antes de o líquido chegar na conta do fornecedor. Base legal:
LC 214/2025.

Cronograma:

| Ano | O que vale |
|---|---|
| **2026** | ano de teste, alíquota simbólica de 1% (0,9% CBS + 0,1% IBS), compensável |
| **2027** | CBS cheia; 1ª etapa do split payment é **opcional** e restrita a B2B entre contribuintes do regime regular, em boleto, Pix, TED e TEF |
| a definir | 2ª etapa, **obrigatória**, com **cartões e vendas B2C** — a data ainda será fixada por ato conjunto RFB/CGIBS |

Três coisas para você tirar disso:

1. **Quem executa é a pagar.me**, não você. Sua obrigação é ter o cadastro
   fiscal certo e emitir a nota da sua comissão.
2. **A venda B2C com cartão ainda não tem data.** Não pare o lançamento
   esperando isso.
3. **O que muda para você no dia:** o valor líquido que cai no seu recebedor vem
   menor, já com o imposto retido. Se a sua conciliação assumir que
   "líquido = comissão", ela vai quebrar. O campo `valorPlataforma` do pedido é
   o **bruto**; guarde também o líquido que a pagar.me informar no webhook.

---

## 7. Corresponsabilidade por nota fiscal — **este é o buraco que falta tapar**

Nenhum provedor resolve isso por você. É regra sua.

### A situação de fato

A maior parte dos seus vendedores é **pessoa física vendendo bem usado próprio,
sem habitualidade**. Essa venda não é fato gerador de IBS/CBS e não gera
obrigação de nota. Tudo bem.

O problema aparece quando **um vendedor deixa de ser vizinho e vira comerciante**
— compra para revender, anuncia 40 itens por mês, opera de fato uma loja dentro
do seu app. Aí ele é contribuinte, deve emitir nota, e o marketplace passa a ter
corresponsabilidade se ele não recolher.

### O que eu recomendo implementar

Uma **régua de habitualidade**, com o vendedor sendo avisado antes de bater nela:

| Faixa | O que o app faz |
|---|---|
| até ~R$ 2.000/mês ou ~10 vendas/mês | nada, vende como PF |
| passou da faixa 2 meses seguidos | avisa no app: "você está vendendo com frequência; a partir de X você precisa cadastrar CNPJ" |
| passou do limite anual (defina com o contador) | **exige CNPJ** para continuar anunciando |

Os dados para isso **já existem** no banco: `Pedido.vendedorId`, `valorProduto`
e `criadoEm` dão volume e frequência por vendedor sem nenhuma tabela nova.

E emita **NFS-e da sua comissão e do frete** desde a primeira venda — essa é
receita sua, tributada, com ou sem reforma.

> Leve esta seção para o seu contador **antes** de abrir para o público. É a
> parte do projeto com maior risco de passivo e a mais barata de acertar agora.

---

## Veredito

**Fique na pagar.me.** Ela atende 6 dos 7 requisitos hoje e ganha das
alternativas exatamente no ponto que mais importa para um marketplace de
vizinhos: o vendedor PF entra sem criar conta em lugar nenhum.

**Mude o desenho do repasse** de "transfere a cada pedido" para "credita
carteira, saca agrupado". É o que resolve o requisito 5 sem trocar de provedor.

**Trate o requisito 7 como produto**, não como contabilidade: a régua de
habitualidade precisa estar no app.

### Ordem sugerida

1. Rodar o checklist de homologação de [`split-pagarme.md`](split-pagarme.md).
2. Confirmar com o gerente da pagar.me: taxa de saque, MDR negociado, Pix, e se
   o split é editável após a autorização.
3. Trocar repasse por carteira + saque (mudança de ~1 dia de trabalho).
4. Levar a seção 7 ao contador e definir os números da régua.
5. Lançar com Pix em destaque; medir a divisão Pix/cartão no primeiro mês.

---

## Fontes

- [Split payment IBS CBS: o que é, quem recolhe e quando começa — SimTax](https://simtax.com.br/split-payment-ibs-cbs-como-funciona/)
- [Split Payment: modalidades, etapas e prazos IBS/CBS — TaxUp](https://taxup.com.br/solucoes/reforma-tributaria/split-payment/)
- [Split Payment na Reforma Tributária: impactos, limites e questões em aberto — Contábeis](https://www.contabeis.com.br/artigos/78327/split-payment-na-reforma-tributaria-impactos-limites-e-questoes-em-aberto/)
- [Marketplace | Como funciona o split de pagamentos — pagar.me](https://pagarme.helpjuice.com/pt_BR/p1-funcionalidades/marketplace-como-funciona-o-split-de-pagamentos)
- [Marketplace | Quem arca com as taxas em uma regra de split — pagar.me](https://pagarme.helpjuice.com/pt_BR/p2-funcionalidades/13marketplace-quem-arca-com-as-taxas-em-uma-regra-de-split)
- [Recebedores — documentação pagar.me](https://docs.pagar.me/docs/recebedores-2)
- [Criação de subcontas — documentação Asaas](https://docs.asaas.com/docs/criacao-de-subcontas)
- [Introdução ao Split de pagamento — documentação Asaas](https://docs.asaas.com/docs/split-de-pagamentos)
