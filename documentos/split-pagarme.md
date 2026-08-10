# Split de pagamento, retenção e reembolso

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
   produto é entregue  ──►  começa a contar 4 dias
        │
        ├─── comprador pede devolução dentro dos 4 dias
        │        └─► estorno parcial: volta o produto,
        │            a comissão e o frete ficam retidos
        │
        └─── passaram os 4 dias sem devolução
                 └─► saque automático para a conta do vendedor
```

O entregador é pago à parte, assim que confirma a entrega — o serviço dele já
foi prestado e não depende de o comprador aprovar o produto.

---

## Por que o repasse é manual

A pagar.me permite deixar a transferência automática ligada, e o dinheiro cairia
na conta do vendedor no ciclo normal de liquidação (D+30 no cartão, D+1 no Pix).
**Não usamos isso de propósito.**

Se o dinheiro saísse antes dos 4 dias de teste, um pedido de devolução deixaria a
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

### A regra combinada

> Na devolução de um pedido entregue pelos nossos entregadores, a taxa é cobrada
> do mesmo jeito: a comissão e o frete não voltam para o comprador, porque a
> intermediação e a entrega já foram prestadas.

Em pedido sem os nossos entregadores não há retenção — o comprador recebe tudo
de volta.

### Exemplo — devolução de um pedido com entregador

| Item | Valor |
|---|---|
| Comprador pagou | R$ 118,00 |
| Comissão retida | − R$ 18,00 |
| Frete retido | − R$ 18,00 |
| **Volta para o comprador** | **R$ 82,00** |
| Sai do saldo do vendedor | R$ 82,00 |
| Sai do saldo do entregador | R$ 0,00 |

O estorno é feito com `split_rules` explícito, para que cada centavo saia do
saldo certo. Sem isso, a pagar.me distribuiria o estorno proporcionalmente e a
comissão voltaria junto.

### ⚠️ Ponto jurídico que você precisa decidir

O Código de Defesa do Consumidor, no **artigo 49**, dá ao consumidor **7 dias**
para desistir de compra feita fora do estabelecimento comercial, com devolução
**integral** dos valores pagos — incluindo o frete.

O prazo de 4 dias com retenção de taxa funciona bem para venda entre pessoas
físicas (o vendedor não é fornecedor profissional). Mas quando o vendedor for
uma empresa, ou vender com habitualidade, o art. 49 tende a prevalecer.

O código já está preparado para os dois cenários:

```ts
// servidor/src/dominio/regras.ts
export const RETER_COMISSAO_NO_REEMBOLSO = true;
export const RETER_FRETE_NO_REEMBOLSO = true;
```

e `calcularReembolso()` aceita sobrescrever caso a caso:

```ts
calcularReembolso(split, modalidade, { reterComissao: false, reterFrete: false });
```

**Recomendação:** converse com um advogado antes do lançamento e considere
tratar o pedido feito nos 7 primeiros dias como desistência com devolução
integral, mantendo a retenção só depois desse prazo. É barato de implementar
agora e caro de resolver depois de uma ação no Procon.

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
- [ ] Pedir devolução e conferir os valores: quanto volta, quanto fica retido
- [ ] Aprovar a devolução e ver o estorno no painel da pagar.me
- [ ] Conferir se o saldo do entregador **não** foi debitado no estorno

Só depois de todos esses itens, troque as chaves.

> **Sobre os payloads:** a API v5 da pagar.me evolui, principalmente nos campos de
> `split` e `transfer_settings`. Confira cada corpo de requisição em
> https://docs.pagar.me/ no dia da integração e ajuste
> `servidor/src/integracoes/pagarme.ts` se algo tiver mudado. A lógica de negócio
> (quem recebe quanto) está separada em `servidor/src/dominio/`, coberta por
> testes, e não muda junto.
