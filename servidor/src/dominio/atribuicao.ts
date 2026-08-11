/**
 * Escolha de quem faz a corrida — regra pura, sem banco.
 *
 * Na versão 1 a atribuição é MANUAL: o administrador olha a fila e escolhe.
 * A interface `EstrategiaDeAtribuicao` existe para que a automática entre
 * depois sem mexer em rota, serviço nem app — basta trocar a estratégia
 * registrada em `estrategiaAtiva()`.
 *
 * `PorDistancia` já está escrita e testável; só não está ligada porque
 * depende de os endereços terem latitude e longitude preenchidas, o que só
 * acontece quando houver geocodificação no cadastro.
 */

export interface CandidatoAEntrega {
  id: string;
  nome: string;
  disponivel: boolean;
  /** Corridas em aberto no momento. */
  corridasAtivas: number;
  capacidade: number;
  latitude: number | null;
  longitude: number | null;
  /** Já recusou esta corrida antes. */
  jaRecusou: boolean;
}

export interface ContextoDaCorrida {
  entregaId: string;
  coletaLatitude: number | null;
  coletaLongitude: number | null;
}

export interface EstrategiaDeAtribuicao {
  readonly nome: string;
  /**
   * Ordena os candidatos do melhor para o pior. Devolver lista vazia significa
   * "ninguém serve agora" — a corrida fica na fila.
   */
  ordenar(
    candidatos: CandidatoAEntrega[],
    contexto: ContextoDaCorrida,
  ): CandidatoAEntrega[];
  /** Se true, o sistema atribui sozinho ao primeiro da lista. */
  readonly automatica: boolean;
}

/** Só filtra quem pode pegar; a escolha final é do administrador. */
export const Manual: EstrategiaDeAtribuicao = {
  nome: 'manual',
  automatica: false,
  ordenar(candidatos) {
    return candidatos
      .filter((c) => c.disponivel && !c.jaRecusou && c.corridasAtivas < c.capacidade)
      // menos carregado primeiro, só para sugerir uma ordem ao admin
      .sort((a, b) => a.corridasAtivas - b.corridasAtivas || a.nome.localeCompare(b.nome));
  },
};

/** Distância em quilômetros entre dois pontos (fórmula de Haversine). */
export function distanciaKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const R = 6371; // raio da Terra em km
  const rad = (g: number) => (g * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/**
 * Mais perto do ponto de coleta primeiro, desempatando por carga.
 *
 * Ainda NÃO está ativa. Para ligar:
 *  1. preencher `latitude`/`longitude` em `Endereco` (geocodificação no cadastro);
 *  2. o app do entregador enviar a posição em `POST /entregas/posicao`;
 *  3. trocar o retorno de `estrategiaAtiva()` para `PorDistancia`.
 */
export const PorDistancia: EstrategiaDeAtribuicao = {
  nome: 'por-distancia',
  automatica: true,
  ordenar(candidatos, contexto) {
    const aptos = Manual.ordenar(candidatos, contexto);
    if (contexto.coletaLatitude == null || contexto.coletaLongitude == null) {
      // sem coordenada da coleta não dá para medir: cai no critério de carga
      return aptos;
    }

    return aptos
      .map((c) => ({
        candidato: c,
        distancia:
          c.latitude == null || c.longitude == null
            ? Number.POSITIVE_INFINITY
            : distanciaKm(c.latitude, c.longitude, contexto.coletaLatitude!, contexto.coletaLongitude!),
      }))
      .sort(
        (a, b) =>
          a.distancia - b.distancia ||
          a.candidato.corridasAtivas - b.candidato.corridasAtivas,
      )
      .map((x) => x.candidato);
  },
};

export function estrategiaAtiva(): EstrategiaDeAtribuicao {
  return Manual;
}
