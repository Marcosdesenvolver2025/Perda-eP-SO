# Módulo de logística

Como a entrega funciona por dentro: estados, quem pode o quê, e onde ela
encosta no financeiro (spoiler: quase em lugar nenhum).

---

## A fronteira com o financeiro

A regra que vale para todo o módulo:

> A logística **não cria cobrança, não altera split, não repassa e não estorna**.
> Ela só empurra o pedido até `ENTREGUE` (ida) ou `DEVOLVIDO_AO_VENDEDOR` (volta).

Quem cuida do dinheiro continua sendo `checkout.ts`, `repasse.ts` e
`reembolso.ts`. A logística chama esses serviços em exatamente **dois** pontos,
e ambos são chamadas para código que já existia:

| Quando | O que acontece | Quem faz |
|---|---|---|
| corrida concluída | paga o entregador | `repasse.ts#pagarEntregador` |
| corrida de DEVOLUÇÃO concluída | dispara o estorno | `reembolso.ts#concluirAposDevolucao` |

---

## Os estados da corrida

```
AGUARDANDO_ATRIBUICAO
    │  admin escolhe o entregador
    ▼
ATRIBUIDA ──────── recusa (com motivo) ──────► RECUSADA
    │                                             │
    │ aceita                        volta para a fila, sem
    ▼                               reoferecer a quem recusou
ACEITA                                            │
    │                                             ▼
    ▼                                  AGUARDANDO_ATRIBUICAO
A_CAMINHO_DA_COLETA
    ▼
CHEGOU_NA_COLETA
    │  exige FOTO DO PACOTE + Nº DE VOLUMES
    ▼
PRODUTO_COLETADO
    ▼
EM_ROTA_PARA_ENTREGA
    ▼
CHEGOU_NA_ENTREGA
    │  exige CÓDIGO DE CONFIRMAÇÃO (4 dígitos)
    ▼
ENTREGUE
```

O admin pode cancelar em qualquer ponto antes de `ENTREGUE`.

A máquina vive em `servidor/src/dominio/logistica.ts` e é **pura** — sem banco,
sem HTTP. Por isso dá para testar o fluxo inteiro sem subir nada, e é o que os
18 testes de `__tests__/logistica.test.ts` fazem.

### O que cada ator pode

| Transição | Admin | Entregador |
|---|---|---|
| atribuir | ✅ | ❌ |
| aceitar / recusar | ❌ | ✅ |
| passos da rua (a caminho, cheguei, coletei…) | ❌ | ✅ |
| tirar da fila / recolocar | ✅ | ❌ |
| cancelar | ✅ | ❌ |

Transição desconhecida é **recusada por padrão**. Um app desatualizado não
consegue pular etapa — marcar `ENTREGUE` sem ter coletado, por exemplo.

---

## Reflexo no pedido

Nem toda transição muda o que o comprador vê. "Cheguei na coleta" é informação
do entregador, não do comprador.

| Estado da corrida | Estado do pedido (ida) | Estado do pedido (devolução) |
|---|---|---|
| AGUARDANDO_ATRIBUICAO / ATRIBUIDA / ACEITA | AGUARDANDO_AGENDAMENTO_DE_COLETA | — |
| A_CAMINHO_DA_COLETA / CHEGOU_NA_COLETA | A_CAMINHO_DA_COLETA | — |
| PRODUTO_COLETADO | PRODUTO_COLETADO | DEVOLUCAO_EM_TRANSITO |
| EM_ROTA_PARA_ENTREGA / CHEGOU_NA_ENTREGA | EM_ROTA_PARA_ENTREGA | DEVOLUCAO_EM_TRANSITO |
| ENTREGUE | **ENTREGUE** (abre os 7 dias) | **DEVOLVIDO_AO_VENDEDOR** (dispara o estorno) |

Repare que a devolução **nunca** marca o pedido como ENTREGUE de novo — há um
teste só para garantir isso.

---

## Atribuição

Na v1 é **manual**: o admin abre a fila, vê os candidatos e escolhe.

A arquitetura já está pronta para a automática. `dominio/atribuicao.ts` define
a interface `EstrategiaDeAtribuicao` e traz duas implementações:

- **`Manual`** (ativa) — filtra quem pode pegar e ordena por quem está menos
  carregado, só para sugerir uma ordem ao admin;
- **`PorDistancia`** (escrita e testada, **desligada**) — ordena por distância
  em linha reta até o ponto de coleta, desempatando por carga.

Os dois filtram igual: fora quem está indisponível, quem já recusou aquela
corrida e quem está na capacidade máxima.

### Para ligar a automática

1. preencher `latitude`/`longitude` em `Endereco` (geocodificação no cadastro);
2. o app do entregador enviar posição em `POST /entregas/posicao` (a rota já
   existe e já grava);
3. trocar o retorno de `estrategiaAtiva()` para `PorDistancia`.

Nenhuma rota, serviço ou tela muda.

---

## Proteção do telefone

O entregador vê o telefone **mascarado** (`(33) ••••-7766`) enquanto a corrida
está em `AGUARDANDO_ATRIBUICAO`, `ATRIBUIDA` ou `RECUSADA`. O número inteiro só
aparece depois que ele aceita.

Sem isso, o cadastro de entregador viraria uma lista de contatos da cidade. O
admin sempre vê o número, porque precisa para resolver problema.

---

## Devolução com coleta reversa

```
comprador pede (dentro dos 7 dias)   → DEVOLUCAO_SOLICITADA
admin aprova                         → DEVOLUCAO_APROVADA + corrida reversa na fila
entregador busca no comprador        → DEVOLUCAO_EM_TRANSITO
entregador entrega ao vendedor       → DEVOLVIDO_AO_VENDEDOR
                                     → estorno na pagar.me → REEMBOLSADO
```

**Por que o estorno só sai no fim:** se o dinheiro voltasse na aprovação, o
comprador poderia ficar com o produto e com o valor. Amarrando o estorno à
entrega ao vendedor, isso não acontece.

O cálculo do estorno não mudou: devolução integral, comissão e tarifa saem do
caixa da plataforma, entregador nunca é debitado — ele fez as duas corridas e é
pago pelas duas.

---

## Notificações

Cada transição relevante dispara um push (`servicos/notificacoes.ts`, via
serviço da Expo):

| Momento | Quem recebe | O quê |
|---|---|---|
| corrida atribuída | entregador | "nova entrega pra você" |
| a caminho da coleta | vendedor (ida) / comprador (volta) | "separe o produto" |
| produto coletado | comprador | "saiu para entrega" |
| chegou na entrega | comprador | "o entregador está na porta, seu código é 1234" |
| entregue | comprador e vendedor | prazo de devolução / valor a receber |
| devolução aprovada | comprador | "um entregador vai buscar" |

Falha de push nunca derruba operação: é registrada e engolida. Token de
aparelho desinstalado é apagado do banco automaticamente.

---

## Endpoints

### Entregador (`/entregas`, papel ENTREGADOR)

| Método | Rota | O que faz |
|---|---|---|
| GET | `/oferecidas` | corridas atribuídas a mim, esperando resposta |
| GET | `/minhas` | corridas em andamento |
| GET | `/historico` | concluídas + extrato de ganhos |
| POST | `/disponibilidade` | liga/desliga recebimento de corridas |
| POST | `/posicao` | última posição (para a atribuição automática) |
| POST | `/:id/aceitar` | aceita |
| POST | `/:id/recusar` | recusa, exige `motivo` |
| POST | `/:id/a-caminho` | saiu para a coleta |
| POST | `/:id/cheguei-na-coleta` | chegou no vendedor |
| POST | `/:id/coletei` | exige `fotoPacote` e `volumes` |
| POST | `/:id/sair-para-entrega` | saiu para entregar |
| POST | `/:id/cheguei-na-entrega` | chegou no comprador |
| POST | `/:id/entreguei` | exige `codigoConfirmacao`, aceita `fotoEntrega` |

### Administrador (`/admin`, papel ADMIN)

| Método | Rota | O que faz |
|---|---|---|
| GET | `/entregas/fila` | corridas sem entregador ou sem aceite |
| GET | `/entregas/:id/candidatos` | entregadores elegíveis, já ordenados |
| POST | `/entregas/:id/atribuir` | escolhe o entregador |
| POST | `/entregas/:id/devolver-para-fila` | tira de quem travou |
| POST | `/entregas/:id/cancelar` | cancela a corrida |
| GET | `/entregadores` | quem está na operação e carga de cada um |
| GET | `/reembolsos` | fila de devoluções |
| POST | `/reembolsos/:id/aprovar` | aprova e abre a coleta reversa |
| POST | `/reembolsos/:id/recusar` | recusa, exige motivo |
| GET | `/resumo` | números do dia, incluindo receita líquida |

---

## O que falta ligar na sua infra

- **Upload das fotos.** A foto do pacote e a da entrega hoje vão como URI local.
  Precisa subir para um storage (S3, Cloudinary) e mandar a URL. Os pontos estão
  marcados com `TODO` em `app/src/telas/PassoDaEntrega.tsx` e `NovoAnuncio.tsx`.
- **Push de verdade.** O envio já está implementado; falta o app registrar o
  token com `expo-notifications` e chamar `POST /conta/dispositivos`.
- **Geocodificação**, se e quando quiser a atribuição automática.
