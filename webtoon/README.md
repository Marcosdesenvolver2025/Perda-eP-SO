# Site de capítulos com acesso pago

Site de leitura de história em quadrinhos adulta: **11 capítulos**, os cinco
primeiros abertos para atrair o leitor e do **6º ao 11º** liberados só depois
de um Pix de **R$ 10,00** — pagamento único, sem mensalidade.

É um site separado do aplicativo Vendas Itinga que mora na raiz deste
repositório. Um não mexe no outro.

---

## O que já está pronto

| Parte | Onde está |
|---|---|
| Página inicial com capa, sinopse e os 11 capítulos | `public/index.html` |
| Leitor de capítulo, com carregamento sob rolagem | `public/capitulo.html` |
| Tela de compra: gera o Pix e o QR Code | `public/comprar.html` |
| Tela "já comprei", que abre os capítulos com o código | `public/liberar.html` |
| Confirmação de 18 anos antes de qualquer coisa | `public/js/comum.js` |
| Termos e aviso legal (rascunho para completar) | `public/termos.html` |
| Cadeado do conteúdo pago | `netlify/functions/` |
| Teste do cadeado | `tools/testar.mjs` |

As páginas que estão lá hoje são **retângulos de exemplo**, não arte. Elas
existem para o site nascer navegável e você trocar pelo seu material.

---

## O cadeado, e por que ele segura

A parte que a maioria dos sites erra é deixar o capítulo pago dentro da pasta
publicada, apenas escondido por JavaScript. Aí basta abrir o endereço da
imagem para ler de graça. Aqui a separação é física:

```
public/paginas/cap-01..05/   capítulos GRÁTIS — publicados, qualquer um baixa
conteudo/cap-06..11/         capítulos PAGOS  — NÃO publicados
```

A pasta `conteudo/` não vai para o site: ela viaja junto com as funções
(`included_files` no `netlify.toml`). A única saída dessas imagens é a função
`pagina`, e ela só entrega depois de conferir o crachá do leitor. Sem crachá,
a resposta é 402 — não existe endereço que devolva a imagem.

O **crachá** é um texto assinado com HMAC-SHA256 usando o `SEGREDO_ACESSO`.
O navegador guarda e devolve a cada página. Como a assinatura depende de um
segredo que só existe no servidor, ninguém fabrica um crachá pelo console.

O **código de acesso** do comprador é a assinatura do e-mail dele. Por isso
funciona sem banco de dados, e por isso o código de um e-mail não abre nada
para outro.

Rode `node tools/testar.mjs` para ver as 20 verificações disso passando.

---

## Como o dinheiro entra

Sua chave Pix (`68124592000197`) é uma chave avulsa: ela **não avisa o site**
quando o dinheiro cai. Quem confere é você. O caminho completo:

1. O comprador digita o e-mail e clica em *Gerar o Pix*.
2. O site monta o "copia e cola" com a sua chave, R$ 10,00 e um número de
   pedido (`CAP...`), e **registra o pedido no painel do Netlify** (aba
   *Forms* → `pedidos`), com o e-mail e esse número. É assim que você sabe
   para quem mandar o código — sem isso, o Pix cai no banco sem nome.
3. Ele paga. Se você tiver contato configurado, o site ainda oferece o botão
   de mandar o comprovante.
4. Você compara o número do pedido com o Pix recebido e roda:

   ```bash
   cd webtoon
   node tools/gerar-codigo.mjs email-do-comprador@exemplo.com
   ```

5. Manda o código. Ele entra em `/liberar.html`, digita e-mail + código, e os
   capítulos 6 a 11 abrem — naquele e em qualquer outro aparelho.

Vale ligar o aviso por e-mail em *Forms* → *Form notifications*, para cada
pedido novo chegar na sua caixa.

**Confirmação automática (opcional).** Se um dia essa chave estiver dentro de
uma conta Mercado Pago, defina `MP_ACCESS_TOKEN` e a função
`conferir-pagamento` passa a consultar o pagamento sozinha, dispensando os
passos 3 a 5.

---

## Publicar

Há dois caminhos. Os dois deixam o cadeado rodando no servidor.

### Caminho 1 — arrastar o pacote pronto (mais rápido)

O `pacote.zip` sai do empacotador com o site inteiro construído: páginas,
funções e capítulos pagos embutidos no código da função. Não precisa de build,
de CLI nem de configurar nada.

```bash
cd webtoon
node tools/empacotar.mjs      # gera pacote/ e pacote.zip
```

No painel: abra o projeto → aba **Deploys** → arraste o `pacote.zip` na área
de publicação manual.

Arraste **no projeto que já existe** (`capitulos-online`), não em
`netlify.com/drop`: o projeto já tem o `SEGREDO_ACESSO` cadastrado, e sem essa
variável as funções recusam tudo.

Limite: as páginas pagas viajam dentro do código da função, que não passa de
~50 MB. Serve bem para exemplo e para arte leve; para arte pesada, use o
caminho 2.

### Caminho 2 — publicar a partir do repositório (para o dia a dia)

Aqui o Netlify constrói sozinho a cada `git push`, e a arte vai como arquivo,
sem limite de tamanho de função.

1. No projeto → *Build & deploy* → *Link repository* → `Perda-eP-SO`.
2. Preencha:

   | Campo | Valor |
   |---|---|
   | Branch to deploy | `claude/chapters-paywall-site-12u5mi` |
   | **Base directory** | **`webtoon`** |
   | Build command | em branco |
   | Publish directory | em branco |

   A base directory é o que faz o Netlify ler o `webtoon/netlify.toml` em vez
   do da raiz, que é do outro site.

### A variável obrigatória

| Variável | Valor |
|---|---|
| `SEGREDO_ACESSO` | um segredo longo, gerado com `openssl rand -hex 32` |

Já está cadastrada no projeto `capitulos-online`. Guarde-a: trocá-la invalida
**todos** os códigos já vendidos. Para o `gerar-codigo.mjs` produzir códigos
que o site aceite, grave o mesmo valor em `webtoon/.env` (ignorado pelo git).

---

## Trocar o exemplo pela sua obra

1. **Textos e preço** — `conteudo/catalogo.json`: título, autor, sinopse,
   nome de cada capítulo, valor, e o bloco `pix` (nome que aparece no
   aplicativo do banco, cidade, WhatsApp e e-mail de contato).

2. **As páginas** — apague os arquivos de exemplo e ponha os seus:

   ```
   capítulos 1 a 5   ->  public/paginas/cap-01/001.jpg, 002.jpg, ...
   capítulos 6 a 11  ->  conteudo/cap-06/001.jpg, 002.jpg, ...
   capa              ->  public/paginas/capa.jpg  (ajuste o caminho no catálogo)
   ```

   Numeração de três dígitos. Valem `.jpg`, `.png`, `.webp`, `.avif` e `.svg`.
   Se a contagem de páginas mudar, atualize o campo `paginas` no catálogo.

   **Não troque as pastas de lugar.** Página paga em `public/` fica pública, e
   o cadeado deixa de existir.

3. **Termos** — `public/termos.html` tem os trechos entre colchetes para você
   preencher: quem publica, CNPJ, contato, devolução em 7 dias.

Para voltar aos exemplos a qualquer momento:
`node tools/gerar-paginas-exemplo.mjs`.

---

## Sobre o conteúdo

O site é o motor; a obra é sua. Publique só arte e roteiro **de sua autoria
ou licenciados** — republicar capítulos de terceiros (é o que os sites de
"manga grátis" fazem) é violação de direito autoral, e cobrar por isso agrava
a situação. Personagens adultos, e a declaração disso nos termos.

Duas medidas que valem a pena quando a obra é sua:

- **Marca d'água discreta** com o e-mail do comprador em cada página paga —
  se o material vazar, dá para saber por onde saiu.
- **Rotação do segredo** (`SEGREDO_ACESSO`) se um código vazar em massa. Todos
  os crachás caem e os compradores legítimos liberam de novo com o mesmo
  código.

Nada impede alguém de tirar print do que pagou para ver. O cadeado impede o
que dá para impedir: leitura sem pagar e download direto do arquivo.
