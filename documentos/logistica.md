# Módulo de logística

Como a entrega funciona por dentro: estados, quem pode o quê, e onde ela
encosta no financeiro (spoiler: quase em lugar nenhum).

---

## Antes de tudo: qual modalidade

Cada anúncio nasce com uma `modalidadeEntrega`, escolhida pelo vendedor, e o
pedido **congela** essa escolha no momento da compra (`Pedido.modalidade`). Se a
regra mudar amanhã, o pedido de ontem continua valendo com a regra de ontem.

| | `PLATAFORMA` | `VENDEDOR` |
|---|---|---|
| Quem leva | entregador nosso | o próprio vendedor |
| Taxa | 12% + tarifa da faixa | só 12% |
| Limite de peso/medida | 20 kg · 100 cm largura · 100 cm altura | nenhum |
| Corrida na fila | sim | **não** |
| Máquina de estados da corrida | sim | **não** |
| Prova de entrega | código digitado pelo entregador | código digitado pelo vendedor |
| Devolução | coleta reversa | combinada entre as partes, admin confirma |

**Todo o resto deste documento fala da modalidade `PLATAFORMA`.** A `VENDEDOR`
tem uma seção própria mais abaixo, e ela não entra na fila, não gera `Entrega`
e não passa pela máquina de estados.

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

## Entrega pelo vendedor

Sem entregador, sem corrida, sem fila. O que sobra é **provar que o produto
chegou**, porque é a entrega que abre os 7 dias e o relógio do repasse.

Tudo isso vive em `servidor/src/servicos/entregaDoVendedor.ts`, fora do módulo
de corridas.

```
pedido pago  →  AGUARDANDO_ENTREGA_DO_VENDEDOR
                (gera um código de 4 dígitos, visível só ao comprador)
      │
      ├── caminho normal: vendedor entrega e digita o código
      │      └─► ENTREGUE  (confirmadaPor = 'codigo')
      │
      ├── comprador toca em "já recebi" no app
      │      └─► ENTREGUE  (confirmadaPor = 'comprador')
      │
      └── comprador sumiu: vendedor declara a entrega sem código
             └─► ENTREGA_DECLARADA + prazoConfirmacaoAte = agora + 3 dias
                   ├── comprador confirma ou abre devolução dentro dos 3 dias
                   └── passaram os 3 dias em silêncio
                          └─► ENTREGUE  (confirmadaPor = 'automatica')
```

Chegando em `ENTREGUE`, o pedido volta para o fluxo comum: começam os 7 dias de
teste e, vencidos sem devolução, o repasse sai como sempre.

### Detalhes que importam

- **Só existe um caminho para `ENTREGUE`.** As três portas acima chamam a mesma
  função privada `marcarEntregue()`, que grava quem confirmou e a data. Não há
  como um fluxo esquecer de abrir o prazo de teste.
- **O código nunca aparece para o vendedor.** `GET /pedidos/:id/codigo` só
  responde ao comprador. O vendedor digita em `POST /pedidos/:id/entreguei` e o
  servidor compara — errou, não passa.
- **A declaração sem código é registrada.** `ENTREGA_DECLARADA` é um estado
  visível: o comprador recebe push e vê no app que o vendedor declarou a
  entrega e que ele tem 3 dias para contestar.
- **A confirmação automática é um job**, não um cálculo na leitura:
  `confirmarEntregasVencidas()` roda junto com o ciclo de repasses
  (`servicos/tarefas.ts`).

### Devolução na entrega pelo vendedor

Não há coleta reversa — as partes moram na mesma cidade e combinam entre si.
O que o sistema garante é que **o dinheiro não volta antes do produto**:

```
comprador pede (dentro dos 7 dias)  → DEVOLUCAO_SOLICITADA
admin aprova                        → DEVOLUCAO_COMBINADA
                                      (as partes se acertam; os telefones são
                                       liberados um para o outro)
vendedor recebe o produto e o admin confirma no painel
                                    → DEVOLVIDO_AO_VENDEDOR
                                    → estorno na pagar.me → REEMBOLSADO
```

A confirmação do admin é `POST /admin/reembolsos/:id/confirmar-retorno`, e ela
chama exatamente o mesmo `reembolso.ts#concluirAposDevolucao` que a corrida
reversa chama. O cálculo do estorno é o mesmo: **devolução integral**.

> É o único ponto do fluxo que depende de julgamento humano. Foi de propósito:
> sem entregador nosso no meio, ninguém além das duas partes sabe se o produto
> voltou, e liberar estorno na palavra de um dos lados é convite a fraude.

### Telas

| Quem | Tela | O que faz |
|---|---|---|
| comprador | `CodigoDeConfirmacao.tsx` | mostra o código em tamanho grande, avisa para só informar ao receber, e tem o atalho "já recebi" |
| vendedor | `EntregaDoVendedor.tsx` | digita o código; se o comprador não responder, declara a entrega (com confirmação em Alert) |
| comprador | `PedidoDetalhe.tsx` → devolução | pede a devolução; depois de aprovada, mostra o contato do vendedor e o aviso de que o estorno sai quando o admin confirmar o retorno |
| vendedor | `NovoAnuncio.tsx` | compara as duas modalidades lado a lado, com o valor líquido de cada uma, antes de publicar |

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
| devolução aprovada (plataforma) | comprador | "um entregador vai buscar" |
| devolução aprovada (vendedor) | comprador e vendedor | "combinem a devolução; o estorno sai quando o produto voltar" |
| pedido pago (vendedor) | vendedor | "combine a entrega com o comprador" |
| entrega declarada | comprador | "o vendedor marcou como entregue; confirme em 3 dias" |

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
| POST | `/reembolsos/:id/aprovar` | aprova; abre a coleta reversa (plataforma) ou marca `DEVOLUCAO_COMBINADA` (vendedor) |
| POST | `/reembolsos/:id/recusar` | recusa, exige motivo |
| POST | `/reembolsos/:id/confirmar-retorno` | só na modalidade VENDEDOR: confirma que o produto voltou e dispara o estorno |
| GET | `/resumo` | números do dia, incluindo receita líquida |

### Entrega pelo vendedor (`/pedidos`, comprador ou vendedor do pedido)

| Método | Rota | Quem | O que faz |
|---|---|---|---|
| GET | `/:id/codigo` | comprador | mostra o código de 4 dígitos |
| POST | `/:id/recebi` | comprador | confirma o recebimento na mão |
| POST | `/:id/entreguei` | vendedor | confirma com o código digitado |
| POST | `/:id/declarar-entrega` | vendedor | declara sem código, abrindo os 3 dias |

---

## O que falta ligar na sua infra

- **Upload das fotos.** A foto do pacote e a da entrega hoje vão como URI local.
  Precisa subir para um storage (S3, Cloudinary) e mandar a URL. Os pontos estão
  marcados com `TODO` em `app/src/telas/PassoDaEntrega.tsx` e `NovoAnuncio.tsx`.
- **Push de verdade.** O envio já está implementado; falta o app registrar o
  token com `expo-notifications` e chamar `POST /conta/dispositivos`.
- **Geocodificação**, se e quando quiser a atribuição automática.
