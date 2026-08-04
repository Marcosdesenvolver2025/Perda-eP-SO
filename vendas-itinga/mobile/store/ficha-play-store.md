# Ficha da loja — Google Play

Textos prontos para copiar no Play Console. Ajuste o que quiser antes de enviar.

---

## Nome do app (até 30 caracteres)

```
Vendas Itinga
```

## Descrição breve (até 80 caracteres)

```
Compre e venda pertinho de você, com entrega rápida e compra protegida.
```

## Descrição completa (até 4000 caracteres)

```
O Vendas Itinga é o marketplace da nossa cidade. Aqui você vende o que não usa
mais e encontra achados incríveis a poucos quarteirões de casa — com entrega
própria e pagamento seguro.

COMPRAR É SIMPLES
• Feed com fotos grandes e produtos perto de você
• Busca com filtros de categoria, preço, condição, tamanho e distância
• Converse com o vendedor pelo chat antes de fechar negócio
• Pague com PIX ou cartão de crédito em até 12x
• Acompanhe o pedido do pagamento até a entrega

COMPRA PROTEGIDA DE VERDADE
Você tem 4 dias após receber para conferir o produto com calma. Se algo não for
como o anunciado, é só pedir o reembolso pelo app: um entregador vai até a sua
casa buscar o item e o dinheiro volta para você.

VENDER LEVA MINUTOS
• Fotografe, descreva e publique — o anúncio vai pro ar na hora
• Escolha entre a entrega da nossa frota ou entregar você mesmo
• Painel de vendas com status de cada pedido e saldo a receber
• O valor cai na sua conta automaticamente, sem burocracia

ENTREGA DA NOSSA GENTE
Nossa frota local coleta com o vendedor e entrega ao comprador. Sem correio,
sem espera de semanas, sem frete caro.

Baixe agora e descubra o que a vizinhança tem pra oferecer.
```

---

## Recursos gráficos

| Item | Tamanho | Arquivo |
|---|---|---|
| Ícone do app | 512 × 512 PNG | redimensione `assets/icon.png` |
| Gráfico de destaque | 1024 × 500 PNG | `store/feature-graphic.png` ✅ |
| Capturas de tela (mín. 2) | 1080 × 1920 ou maior | tire do app rodando |

Para gerar o ícone de 512:

```bash
cd vendas-itinga/mobile
python3 -c "from PIL import Image; im=Image.open('assets/icon.png'); im.resize((512,512)).save('store/icon-512.png')"
```

Capturas de tela sugeridas (nesta ordem):
1. Feed com produtos
2. Página de um produto
3. Tela de anunciar
4. Checkout / PIX
5. Painel de vendas

---

## Categorização

- **Categoria:** Compras
- **Tags:** marketplace, brechó, classificados, usados, venda
- **Público-alvo:** 18 anos ou mais
- **Contém anúncios:** Não
- **Compras no app:** Não *(os pagamentos são de produtos entre usuários,
  processados pelo Pagar.me — não são compras digitais do Google)*

---

## Segurança dos dados (declaração)

Dados coletados e por quê:

| Dado | Coletado | Finalidade | Obrigatório |
|---|---|---|---|
| Nome e e-mail | Sim | Conta e comunicação sobre pedidos | Sim |
| Foto de perfil | Sim | Identificação no app | Não |
| Endereço | Sim | Entrega dos pedidos | Sim (para comprar) |
| Localização aproximada | Sim | Mostrar produtos próximos e calcular frete | Não |
| Fotos | Sim | Imagens dos anúncios | Só para vender |
| Dados bancários | Sim | Repasse das vendas (via Pagar.me) | Só para vender |
| Histórico de compras | Sim | Pedidos e suporte | Sim |

- Dados criptografados em trânsito: **Sim** (HTTPS)
- Usuário pode pedir exclusão da conta: **Sim**
- Dados do cartão: **não são coletados nem armazenados** — vão direto ao
  Pagar.me, tokenizados no dispositivo
