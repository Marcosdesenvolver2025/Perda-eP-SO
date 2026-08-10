# Split de pagamento, retenção do repasse e reembolso

Este é o documento mais importante do projeto: é aqui que o dinheiro se move.
Leia inteiro antes de trocar as chaves de teste pelas de produção.

---

## O caminho do dinheiro

```
comprador paga R$ 118,00 (produto R$ 100 + frete R$ 18)
        │
        ▼
┌───────────────────────────────────────────────────────────┐
│  pagar.me cria a cobrança JÁ COM O SPLIT                   │
│                                                            │
│   vendedor    R$ 82,00   (produto − 18% de comissão)       │
│   plataforma  R$ 36,00   (comissão R$ 18 + frete R$ 18)    │
└───────────────────────────────────────────────────────────┘
        │
        │  o dinheiro fica NO SALDO de cada recebedor,
        │  sem sair para a conta bancária, porque criamos os
        │  recebedores com transfer_enabled: false
        ▼
   produto é entregue  ──►  começa a contar 7 dias
        │
        ├─── comprador pede devolução dentro dos 7 dias
        │        └─► estorno INTEGRAL: volta produto + frete
        │            (CDC art. 49); a comissão sai do caixa
        │
        └─── passaram os 7 dias sem devolução
                 └─► saque automático para a conta do vendedor
```

O entregador é pago à parte, assim que confirma a entrega — o serviço dele já
foi prestado e não depende de o comprador aprovar o produto.

---

## Por que o repasse é manual

A pagar.me permite deixar a transferência automática ligada, e o dinheiro cairia
na conta do vendedor no ciclo normal de liquidação (D+30 no cartão, D+1 no Pix).
**Não usamos isso de propósito.**

Se o dinheiro saísse antes dos 7 dias de teste, um pedido de devolução deixaria a
plataforma com um saldo negativo para cobrir — você teria que correr atrás do
vendedor para reaver o valor.

A configuração está em `servidor/src/integracoes/pagarme.ts`:

```ts
transfer_settings: {
  transfer_enabled: false,   // repasse manual, depois da janela de teste
  transfer_interval: 'Daily',
  transfer_day: 0,
}
```

E a liberação em `servidor/src/servicos/repasse.ts`, que roda de hora em hora
(`servidor/src/servicos/tarefas.ts`) e procura pedidos com `prazoTesteAte`
vencido e sem devolução aberta.

---

## As contas, no detalhe

Tudo em centavos, sempre inteiro. Ver `servidor/src/dominio/comissao.ts`.

### Sem os nossos entregadores — comissão de 16%

| Item | Valor |
|---|---|
| Produto | R$ 100,00 |
| Frete | R$ 0,00 |
| **Comprador paga** | **R$ 100,00** |
| Comissão (16%) | R$ 16,00 |
| Vendedor recebe | R$ 84,00 |
| Plataforma recebe | R$ 16,00 |

### Com os nossos entregadores — comissão de 18%

| Item | Valor |
|---|---|
| Produto | R$ 100,00 |
| Frete | R$ 18,00 |
| **Comprador paga** | **R$ 118,00** |
| Comissão (18%) | R$ 18,00 |
| Vendedor recebe | R$ 82,00 |
| Entregador recebe (80% do frete) | R$ 14,40 |
| Plataforma fica com | R$ 21,60 (comissão + 20% do frete) |

### Arredondamento

A comissão sempre arredonda **para baixo** (`Math.floor`), e a sobra de centavo
fica com a plataforma. Isso garante duas coisas: o vendedor nunca perde no
arredondamento, e a soma das partes bate exatamente com o total — a pagar.me
recusa a transação se o split não fechar no centavo.

`montarRegrasSplit()` confere essa soma e lança erro antes de chamar a API.

---

## Reembolso

Ver `servidor/src/dominio/reembolso.ts` e `servidor/src/servicos/reembolso.ts`.

### A regra: devolução integral, como manda a lei

> **Artigo 49 do Código de Defesa do Consumidor** — o consumidor pode desistir
> da compra feita fora do estabelecimento comercial em até **7 dias** contados
> do recebimento. O parágrafo único manda devolver **todos os valores pagos,
> monetariamente atualizados** — e a jurisprudência é firme em incluir o frete.

Por isso, dentro dos 7 dias o comprador recebe **100% do que pagou**, com ou sem
entregador nosso. Quem absorve a comissão e o custo da entrega é a plataforma.

### Exemplo — devolução de um pedido com entregador

| Item | Valor |
|---|---|
| Comprador pagou | R$ 118,00 |
| **Volta para o comprador** | **R$ 118,00** |
| Sai do saldo do vendedor | R$ 82,00 (o que ele tinha recebido) |
| Sai do caixa da plataforma | R$ 36,00 (comissão + frete) |
| Sai do saldo do entregador | R$ 0,00 |

O estorno é feito com `split_rules` explícito, para que cada centavo saia do
saldo certo. Sem isso, a pagar.me distribuiria o estorno proporcionalmente e
debitaria o entregador junto — que não pode ser penalizado por um problema do
produto.

### O que isso custa para a operação

Cada devolução tira do seu caixa a comissão daquela venda **mais** o frete pago
ao entregador. Numa venda de R$ 100 com entrega nossa, uma devolução custa
R$ 36,00 à plataforma.

Isso é o custo de operar dentro da lei, e é normal no setor. O que dá para fazer
para segurar esse número:

- **cobrar a entrega da devolução** de quem devolve por arrependimento puro
  (não por defeito) — o art. 49 cobre o frete da ida, não obriga a bancar
  devoluções em série do mesmo comprador;
- **acompanhar quem devolve demais**: `GET /admin/reembolsos` mostra a fila, e
  a tabela `EventoPedido` guarda o histórico por comprador;
- **exigir foto no pedido de devolução** (o app já envia `fotosUrls`), o que
  reduz muito pedido oportunista.

### Se um dia a política precisar mudar

As chaves existem e `calcularReembolso()` aceita sobrescrever caso a caso —
para uma devolução negociada **fora** do prazo legal, por exemplo:

```ts
// servidor/src/dominio/regras.ts — padrão: false, false
export const RETER_COMISSAO_NO_REEMBOLSO = false;
export const RETER_FRETE_NO_REEMBOLSO = false;

// e, caso a caso:
calcularReembolso(split, modalidade, { reterComissao: true, reterFrete: true });
```

**Não ligue a retenção dentro dos 7 dias sem falar com um advogado.** É o tipo
de economia que vira ação no Procon e custa muito mais do que economizou.

---

## Idempotência: por que não cobra duas vezes

Toda chamada que mexe em dinheiro manda uma `Idempotency-Key`:

| Operação | Chave |
|---|---|
| Criar cobrança | o código do pedido (`VI-XXXXXX`) |
| Estorno | `estorno-{id do reembolso}` |
| Repasse ao vendedor | `repasse-{id do pedido}` |
| Pagamento do entregador | `entregador-{id da entrega}` |

Se a rede cair no meio e o app repetir a chamada, a pagar.me devolve o resultado
da primeira em vez de cobrar de novo. O mesmo vale para o ciclo de repasses, que
pode reprocessar o mesmo pedido depois de uma falha.

---

## Webhooks

A pagar.me avisa em `POST /webhooks/pagarme`. É por aí que o **Pix** vira pedido
pago — no cartão a resposta é imediata, mas no Pix não.

- Todo evento é gravado na tabela `WebhookRecebido` antes de ser processado; se a
  pagar.me reenviar (ela reenvia quando não recebe 2xx), o segundo é ignorado.
- Respondemos 200 na hora e processamos depois: webhook que demora a responder é
  reenviado e vira duplicidade.
- A autenticação básica é conferida em `webhookAutenticado()`, com comparação de
  tempo constante.

---

## Checklist antes de trocar para produção

Faça tudo isso com `sk_test_` / `pk_test_`:

- [ ] Criar recebedor de vendedor e ver o status virar `active`
- [ ] Comprar com **Pix** e ver o webhook mudar o pedido para PAGO
- [ ] Comprar com **cartão** e conferir se o split aparece certo no painel da pagar.me
- [ ] Conferir se o saldo do vendedor fica **retido** (não transferido)
- [ ] Confirmar a entrega e ver o entregador ser pago
- [ ] Adiantar o relógio (ou baixar `DIAS_PARA_TESTAR=0`) e ver o repasse sair
- [ ] Pedir devolução e conferir que volta 100% do que foi pago, frete incluído
- [ ] Aprovar a devolução e ver o estorno no painel da pagar.me
- [ ] Conferir se o saldo do entregador **não** foi debitado no estorno

Só depois de todos esses itens, troque as chaves.

> **Sobre os payloads:** a API v5 da pagar.me evolui, principalmente nos campos de
> `split` e `transfer_settings`. Confira cada corpo de requisição em
> https://docs.pagar.me/ no dia da integração e ajuste
> `servidor/src/integracoes/pagarme.ts` se algo tiver mudado. A lógica de negócio
> (quem recebe quanto) está separada em `servidor/src/dominio/`, coberta por
> testes, e não muda junto.
