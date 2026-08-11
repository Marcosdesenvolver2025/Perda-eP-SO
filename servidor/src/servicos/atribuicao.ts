/**
 * Monta a lista de candidatos a uma corrida, consultando o banco e aplicando
 * a estratégia ativa (ver `dominio/atribuicao.ts`).
 */

import {
  estrategiaAtiva,
  type CandidatoAEntrega,
} from '../dominio/atribuicao';
import { prisma } from '../prisma';

export { estrategiaAtiva } from '../dominio/atribuicao';

const ESTADOS_ATIVOS = [
  'ATRIBUIDA',
  'ACEITA',
  'A_CAMINHO_DA_COLETA',
  'CHEGOU_NA_COLETA',
  'PRODUTO_COLETADO',
  'EM_ROTA_PARA_ENTREGA',
  'CHEGOU_NA_ENTREGA',
] as const;

/**
 * Monta a lista de candidatos para uma corrida, já ordenada pela estratégia
 * ativa. É o que o painel do administrador mostra.
 */
export async function candidatosPara(entregaId: string): Promise<CandidatoAEntrega[]> {
  const entrega = await prisma.entrega.findUnique({
    where: { id: entregaId },
    include: { recusas: { select: { entregadorId: true } } },
  });
  if (!entrega) return [];

  const recusaram = new Set(entrega.recusas.map((r) => r.entregadorId));

  const entregadores = await prisma.usuario.findMany({
    where: { papel: 'ENTREGADOR', excluidoEm: null },
    select: {
      id: true,
      nome: true,
      entregadorDisponivel: true,
      entregadorCapacidade: true,
      entregadorLat: true,
      entregadorLng: true,
      _count: {
        select: { entregas: { where: { estado: { in: [...ESTADOS_ATIVOS] } } } },
      },
    },
  });

  const candidatos: CandidatoAEntrega[] = entregadores.map((e) => ({
    id: e.id,
    nome: e.nome,
    disponivel: e.entregadorDisponivel,
    corridasAtivas: e._count.entregas,
    capacidade: e.entregadorCapacidade,
    latitude: e.entregadorLat,
    longitude: e.entregadorLng,
    jaRecusou: recusaram.has(e.id),
  }));

  return estrategiaAtiva().ordenar(candidatos, {
    entregaId,
    coletaLatitude: null,
    coletaLongitude: null,
  });
}
