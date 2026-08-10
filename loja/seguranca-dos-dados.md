# Segurança dos dados (Data safety) — respostas para o Play Console

O Play Console pergunta, item por item, o que o app coleta e compartilha. Se as
respostas não baterem com o que o app faz de verdade, a Google reprova a versão
— e pode remover o app depois. As respostas abaixo correspondem ao código deste
repositório.

Onde preencher: **Play Console → Política → Conteúdo do app → Segurança dos dados**.

---

## Bloco 1 — Visão geral

| Pergunta | Resposta |
|---|---|
| Seu app coleta ou compartilha algum dos tipos de dados do usuário exigidos? | **Sim** |
| Todos os dados do usuário coletados pelo app são criptografados em trânsito? | **Sim** (HTTPS/TLS em todas as chamadas) |
| Você oferece uma forma de o usuário solicitar a exclusão dos dados? | **Sim** — https://vendasitinga.com.br/excluir-conta |

---

## Bloco 2 — Tipos de dados

### Informações pessoais

| Tipo de dado | Coletado | Compartilhado | Obrigatório | Finalidade |
|---|---|---|---|---|
| Nome | Sim | Sim | Obrigatório | Funcionalidade do app; comunicação entre usuários |
| Endereço de e-mail | Sim | Não | Obrigatório | Gerenciamento de conta |
| Endereço físico | Sim | Sim | Opcional | Funcionalidade do app (entrega) |
| Número de telefone | Sim | Sim | Opcional | Funcionalidade do app (contato do entregador) |
| Outras informações (CPF/CNPJ) | Sim | Sim | Obrigatório | Funcionalidade do app; conformidade legal |

> **Compartilhado = sim** porque os dados vão para a pagar.me (processamento do
> pagamento) e, no contexto de um pedido, para a outra parte e para o entregador.

### Informações financeiras

| Tipo de dado | Coletado | Compartilhado | Obrigatório | Finalidade |
|---|---|---|---|---|
| Informações de pagamento do usuário | **Não** | Não | — | O cartão é tokenizado no aparelho, direto pela pagar.me. O app não recebe nem envia número de cartão. |
| Histórico de compras | Sim | Não | Obrigatório | Funcionalidade do app |
| Outras informações financeiras (dados bancários para recebimento) | Sim | Sim | Opcional | Funcionalidade do app (repasse das vendas) |

### Fotos e vídeos

| Tipo de dado | Coletado | Compartilhado | Obrigatório | Finalidade |
|---|---|---|---|---|
| Fotos | Sim | Sim | Opcional | Funcionalidade do app (fotos dos anúncios, ficam públicas na vitrine) |

### Mensagens

| Tipo de dado | Coletado | Compartilhado | Obrigatório | Finalidade |
|---|---|---|---|---|
| Outras mensagens no app | Sim | Não | Opcional | Funcionalidade do app; prevenção de fraude e suporte |

### Atividade no app

| Tipo de dado | Coletado | Compartilhado | Obrigatório | Finalidade |
|---|---|---|---|---|
| Interações no app | Sim | Não | Obrigatório | Análise; funcionalidade do app |

### Identificadores do dispositivo

| Tipo de dado | Coletado | Compartilhado | Obrigatório | Finalidade |
|---|---|---|---|---|
| ID do dispositivo (token de notificação) | Sim | Não | Opcional | Funcionalidade do app (avisos de pedido) |

### O que responder “não coletamos”

- Localização (aproximada ou precisa)
- Contatos
- Calendário
- Áudio, arquivos e documentos do dispositivo
- Informações de saúde ou condicionamento físico
- Histórico de navegação na web
- Orientação sexual, religião, origem étnica ou qualquer dado sensível

---

## Bloco 3 — Práticas de segurança

| Pergunta | Resposta |
|---|---|
| Os dados são criptografados em trânsito | **Sim** |
| O usuário pode solicitar a exclusão dos dados | **Sim** |
| O app segue a política de Famílias do Google Play | **Não se aplica** (público 18+) |
| Os dados passaram por auditoria independente de segurança | **Não** (a menos que você contrate uma) |

---

## Permissões declaradas e por quê

| Permissão | Por que o app pede | Onde aparece no código |
|---|---|---|
| `INTERNET` | Falar com a API e com a pagar.me | uso geral |
| `CAMERA` | Fotografar o produto ao criar o anúncio | `app/src/telas/NovoAnuncio.tsx` |
| `READ_MEDIA_IMAGES` | Escolher fotos da galeria para o anúncio | `app/src/telas/NovoAnuncio.tsx` |
| `POST_NOTIFICATIONS` | Avisar sobre venda, entrega e fim do prazo de devolução | `servidor/src/rotas/conta.ts` (registro do token) |

Permissões bloqueadas de propósito em `app.json` (`blockedPermissions`), para
nenhuma biblioteca de terceiros incluí-las sem querer: `RECORD_AUDIO`,
`READ_MEDIA_VIDEO`.

---

## Checagem antes de enviar

- [ ] As respostas acima batem com o que o app realmente faz.
- [ ] A política de privacidade publicada descreve os mesmos dados desta tabela.
- [ ] O link de exclusão de conta está no ar e acessível **sem precisar fazer login**.
- [ ] Se você adicionar analytics, publicidade ou chat de terceiros depois,
      **volte aqui e atualize** — cada SDK novo costuma mudar essas respostas.
