# Checklist de publicação

Marque cada item antes de apertar "Enviar para análise". A ordem importa.

---

## 1. Contas e acessos

- [ ] Conta de desenvolvedor Google Play criada e **verificada** (US$ 25)
- [ ] Verificação de identidade e endereço aprovada pela Google
- [ ] Conta Expo (EAS) criada e `eas login` funcionando
- [ ] Projeto criado no Google Cloud, com tela de permissão OAuth configurada
- [ ] Conta pagar.me ativa, com CNPJ aprovado

## 2. Infraestrutura

- [ ] Banco PostgreSQL criado e acessível
- [ ] API publicada e respondendo em `https://api.vendasitinga.com.br/saude`
- [ ] `npx prisma migrate deploy` rodado em produção
- [ ] Domínio apontado e HTTPS funcionando
- [ ] Variáveis do `.env` preenchidas no servidor de produção
- [ ] Webhook da pagar.me apontando para `/webhooks/pagarme`, com autenticação

## 3. Configuração do app

- [ ] `app/app.json` → `extra.eas.projectId` preenchido pelo `eas init`
- [ ] `app/app.json` → `android.package` confirmado (`br.com.vendasitinga.app`)
- [ ] `app/app.json` → `version` e `versionCode` corretos
- [ ] `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` preenchido
- [ ] `EXPO_PUBLIC_PAGARME_CHAVE_PUBLICA` preenchido
- [ ] SHA-1 da **chave de upload** cadastrado no Google Cloud
- [ ] SHA-1 da **chave de assinatura do app** (Play App Signing) também cadastrado

## 4. Páginas web no ar

- [ ] `https://vendasitinga.com.br/privacidade` abre e carrega
- [ ] `https://vendasitinga.com.br/termos` abre e carrega
- [ ] `https://vendasitinga.com.br/excluir-conta` abre **sem exigir login**
- [ ] `[RAZÃO SOCIAL]`, `[SEU CNPJ]` e endereço substituídos nos três arquivos
- [ ] E-mails `suporte@` e `privacidade@` recebendo mensagem de verdade

## 5. Testes no aparelho (build de teste)

- [ ] Entrar com a conta Google
- [ ] Completar o cadastro (CPF e telefone)
- [ ] Cadastrar endereço
- [ ] Publicar um anúncio com fotos nas **duas modalidades** de entrega
- [ ] Conferir o comparativo de taxas na tela de anúncio (com e sem tarifa fixa)
- [ ] Com **entrega pela plataforma**, tentar publicar com **21 kg** → o app precisa
      recusar a modalidade e explicar o motivo
- [ ] Idem com **101 cm de largura** e com **101 cm de altura**
- [ ] Com **entrega pelo vendedor**, publicar os mesmos 21 kg → precisa **aceitar**
- [ ] Comprar com **Pix** (chave de teste) e ver o pedido virar PAGO
- [ ] Comprar com **cartão** de teste e ver o split no painel da pagar.me
- [ ] Cadastrar conta de recebimento e ver o recebedor ficar ativo
- [ ] Aceitar, coletar e confirmar uma entrega pela área do entregador
- [ ] Na entrega pelo vendedor: confirmar com o **código de 4 dígitos** do comprador
- [ ] Na entrega pelo vendedor: declarar sem código e ver a confirmação automática
      em **3 dias** (ou baixando `DIAS_PARA_CONFIRMACAO_AUTOMATICA=0`)
- [ ] Ver o prazo de 7 dias aparecer no pedido depois da entrega
- [ ] Pedir devolução e conferir que a prévia mostra **devolução integral**
- [ ] Aprovar a devolução e ver o estorno na pagar.me
- [ ] Na entrega pelo vendedor: confirmar o retorno pelo painel admin e só então
      ver o estorno disparar
- [ ] Excluir a conta pelo app e conferir que os dados sumiram

## 6. Ficha da loja

- [ ] Nome: **Vendas Itinga**
- [ ] Descrição breve preenchida (máx. 80 caracteres)
- [ ] Descrição completa preenchida
- [ ] Ícone 512×512 enviado (`loja/graficos/icone-512.png`)
- [ ] Gráfico de destaque 1024×500 enviado (`loja/graficos/capa-1024x500.png`)
- [ ] **Pelo menos 2 capturas de tela** do celular enviadas (ideal: 6)
- [ ] Categoria: Compras
- [ ] E-mail de contato preenchido

## 7. Conteúdo do app

- [ ] URL da política de privacidade informada
- [ ] **Acesso ao app**: conta de teste criada e informada para os revisores
- [ ] Anúncios: "não contém anúncios"
- [ ] Classificação de conteúdo: questionário respondido
- [ ] Público-alvo: 18 anos ou mais
- [ ] **Segurança dos dados**: formulário preenchido conforme `seguranca-dos-dados.md`
- [ ] **Apps financeiros**: declarado que processa pagamentos, com o CNPJ
- [ ] URL de exclusão de conta informada

## 8. Versão

- [ ] `.aab` de produção gerado (`npm run build:producao`)
- [ ] Versão publicada primeiro no **teste interno**
- [ ] Instalada pela Play Store e testada de ponta a ponta
- [ ] Notas da versão escritas em pt-BR
- [ ] Promovida para produção

## 9. Depois de aprovado

- [ ] Chaves da pagar.me trocadas para produção (`sk_live_` / `pk_live_`)
- [ ] Novo build gerado com as chaves de produção
- [ ] **Compra real de valor baixo**, acompanhada até o repasse cair na conta
- [ ] Alguém de olho na fila de devoluções (`GET /admin/reembolsos`)
- [ ] Alertas configurados para falha no ciclo de repasses

---

## O que mais reprova, em uma frase cada

1. **Revisor não conseguiu entrar** — sempre forneça conta de teste.
2. **Segurança dos dados incompleta ou inconsistente** — use o arquivo pronto.
3. **Link de exclusão de conta ausente ou atrás de login** — precisa abrir direto.
4. **Política de privacidade genérica ou copiada** — a nossa descreve o app real.
5. **`targetSdkVersion` abaixo do exigido no ano** — confira o mínimo vigente.
