/**
 * Notificações push, via serviço de push da Expo.
 *
 * Não usa SDK: é um POST simples. Se o envio falhar, a falha é registrada e
 * engolida — notificação nunca pode derrubar uma operação de negócio. O
 * entregador que perdeu o push ainda vê a corrida ao abrir o app.
 */

import { log } from '../log';
import { prisma } from '../prisma';

const URL_EXPO = 'https://exp.host/--/api/v2/push/send';

export interface Aviso {
  titulo: string;
  corpo: string;
  /** Para o app abrir direto na tela certa ao tocar. */
  dados?: Record<string, string>;
}

async function enviarParaTokens(tokens: string[], aviso: Aviso): Promise<void> {
  if (tokens.length === 0) return;

  const mensagens = tokens.map((to) => ({
    to,
    sound: 'default',
    title: aviso.titulo,
    body: aviso.corpo,
    data: aviso.dados ?? {},
    priority: 'high',
    channelId: 'default',
  }));

  try {
    const resposta = await fetch(URL_EXPO, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(mensagens),
    });

    if (!resposta.ok) {
      log.warn({ status: resposta.status }, 'push recusado pela Expo');
      return;
    }

    const corpo = (await resposta.json()) as {
      data?: Array<{ status: string; details?: { error?: string } }>;
    };

    // token de aparelho desinstalado precisa sair do banco, senão a fila fica
    // cheia de destinatário morto
    const mortos: string[] = [];
    corpo.data?.forEach((item, i) => {
      if (item.status === 'error' && item.details?.error === 'DeviceNotRegistered') {
        const token = tokens[i];
        if (token) mortos.push(token);
      }
    });

    if (mortos.length) {
      await prisma.dispositivo.deleteMany({ where: { token: { in: mortos } } });
      log.info({ quantidade: mortos.length }, 'tokens de push removidos');
    }
  } catch (erro) {
    log.error({ erro }, 'falha ao enviar push');
  }
}

/** Avisa uma pessoa em todos os aparelhos dela. */
export async function avisar(usuarioId: string, aviso: Aviso): Promise<void> {
  const dispositivos = await prisma.dispositivo.findMany({
    where: { usuarioId },
    select: { token: true },
  });
  await enviarParaTokens(
    dispositivos.map((d) => d.token),
    aviso,
  );
}

/** Avisa todo mundo de um papel — usado para chamar entregadores. */
export async function avisarPapel(
  papel: 'ENTREGADOR' | 'ADMIN',
  aviso: Aviso,
): Promise<void> {
  const dispositivos = await prisma.dispositivo.findMany({
    where: { usuario: { papel, excluidoEm: null } },
    select: { token: true },
  });
  await enviarParaTokens(
    dispositivos.map((d) => d.token),
    aviso,
  );
}

// ---------------------------------------------------------------------------
// Textos das notificações do fluxo logístico, em um lugar só
// ---------------------------------------------------------------------------

export const avisos = {
  novaCorridaParaVoce: (pedido: string, bairro: string | null): Aviso => ({
    titulo: 'nova entrega pra você',
    corpo: bairro
      ? `Coleta no ${bairro}. Pedido ${pedido}. Toque para aceitar.`
      : `Pedido ${pedido}. Toque para aceitar.`,
    dados: { tela: 'AreaDoEntregador' },
  }),

  corridaNaFila: (pedido: string): Aviso => ({
    titulo: 'entrega esperando entregador',
    corpo: `O pedido ${pedido} está na fila e ainda não tem quem leve.`,
    dados: { tela: 'PainelAdmin' },
  }),

  produtoColetado: (produto: string): Aviso => ({
    titulo: 'seu produto foi coletado',
    corpo: `${produto} saiu para entrega e chega logo.`,
    dados: { tela: 'MinhasCompras' },
  }),

  entregadorACaminho: (produto: string): Aviso => ({
    titulo: 'o entregador está indo buscar',
    corpo: `Separe o ${produto} — o entregador está a caminho.`,
    dados: { tela: 'MinhasVendas' },
  }),

  chegouParaEntregar: (codigo: string): Aviso => ({
    titulo: 'seu pedido chegou',
    corpo: `O entregador está na porta. Seu código é ${codigo}.`,
    dados: { tela: 'MinhasCompras' },
  }),

  entregue: (dias: number): Aviso => ({
    titulo: 'pedido entregue',
    corpo: `Você tem ${dias} dias para testar e pedir devolução se não gostar.`,
    dados: { tela: 'MinhasCompras' },
  }),

  vendaConcluida: (valor: string): Aviso => ({
    titulo: 'sua venda foi entregue',
    corpo: `Assim que passar o prazo de devolução, ${valor} vai para a sua conta.`,
    dados: { tela: 'MinhasVendas' },
  }),

  devolucaoAprovada: (): Aviso => ({
    titulo: 'devolução aprovada',
    corpo: 'Um entregador vai buscar o produto no seu endereço.',
    dados: { tela: 'MinhasCompras' },
  }),

  devolucaoACaminhoDoVendedor: (): Aviso => ({
    titulo: 'produto voltando pra você',
    corpo: 'A devolução foi coletada e está a caminho do seu endereço.',
    dados: { tela: 'MinhasVendas' },
  }),
};
