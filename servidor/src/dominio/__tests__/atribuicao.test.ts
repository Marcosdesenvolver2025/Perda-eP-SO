import { describe, expect, it } from 'vitest';

import {
  Manual,
  PorDistancia,
  distanciaKm,
  type CandidatoAEntrega,
  type ContextoDaCorrida,
} from '../atribuicao';

function candidato(over: Partial<CandidatoAEntrega> = {}): CandidatoAEntrega {
  return {
    id: 'u1',
    nome: 'Ana',
    disponivel: true,
    corridasAtivas: 0,
    capacidade: 1,
    latitude: null,
    longitude: null,
    jaRecusou: false,
    ...over,
  };
}

const SEM_COORDENADA: ContextoDaCorrida = {
  entregaId: 'e1',
  coletaLatitude: null,
  coletaLongitude: null,
};

describe('atribuição manual (v1)', () => {
  it('tira quem está indisponível', () => {
    const lista = [
      candidato({ id: 'a', nome: 'Ana' }),
      candidato({ id: 'b', nome: 'Bruno', disponivel: false }),
    ];
    expect(Manual.ordenar(lista, SEM_COORDENADA).map((c) => c.id)).toEqual(['a']);
  });

  it('tira quem já recusou esta corrida', () => {
    const lista = [
      candidato({ id: 'a', nome: 'Ana', jaRecusou: true }),
      candidato({ id: 'b', nome: 'Bruno' }),
    ];
    expect(Manual.ordenar(lista, SEM_COORDENADA).map((c) => c.id)).toEqual(['b']);
  });

  it('tira quem está na capacidade máxima', () => {
    const lista = [
      candidato({ id: 'a', nome: 'Ana', corridasAtivas: 1, capacidade: 1 }),
      candidato({ id: 'b', nome: 'Bruno', corridasAtivas: 1, capacidade: 3 }),
    ];
    expect(Manual.ordenar(lista, SEM_COORDENADA).map((c) => c.id)).toEqual(['b']);
  });

  it('sugere primeiro quem está menos carregado', () => {
    const lista = [
      candidato({ id: 'a', nome: 'Ana', corridasAtivas: 2, capacidade: 5 }),
      candidato({ id: 'b', nome: 'Bruno', corridasAtivas: 0, capacidade: 5 }),
      candidato({ id: 'c', nome: 'Carla', corridasAtivas: 1, capacidade: 5 }),
    ];
    expect(Manual.ordenar(lista, SEM_COORDENADA).map((c) => c.id)).toEqual(['b', 'c', 'a']);
  });

  it('não é automática: quem decide é o administrador', () => {
    expect(Manual.automatica).toBe(false);
  });
});

describe('distância', () => {
  it('mede zero para o mesmo ponto', () => {
    expect(distanciaKm(-16.6, -41.77, -16.6, -41.77)).toBe(0);
  });

  it('mede alguns quilômetros dentro da cidade', () => {
    // dois pontos separados por ~1 km
    const d = distanciaKm(-16.6, -41.77, -16.609, -41.77);
    expect(d).toBeGreaterThan(0.9);
    expect(d).toBeLessThan(1.1);
  });
});

describe('atribuição por distância (preparada, ainda desligada)', () => {
  const contexto: ContextoDaCorrida = {
    entregaId: 'e1',
    coletaLatitude: -16.6,
    coletaLongitude: -41.77,
  };

  it('coloca o mais perto na frente', () => {
    const lista = [
      candidato({ id: 'longe', nome: 'Longe', latitude: -16.65, longitude: -41.8 }),
      candidato({ id: 'perto', nome: 'Perto', latitude: -16.601, longitude: -41.771 }),
    ];
    expect(PorDistancia.ordenar(lista, contexto).map((c) => c.id)).toEqual(['perto', 'longe']);
  });

  it('joga para o fim quem não informou posição', () => {
    const lista = [
      candidato({ id: 'sem-gps', nome: 'Sem GPS' }),
      candidato({ id: 'com-gps', nome: 'Com GPS', latitude: -16.61, longitude: -41.78 }),
    ];
    expect(PorDistancia.ordenar(lista, contexto).map((c) => c.id)).toEqual([
      'com-gps',
      'sem-gps',
    ]);
  });

  it('sem coordenada da coleta, cai no critério de carga', () => {
    const lista = [
      candidato({ id: 'a', nome: 'Ana', corridasAtivas: 3, capacidade: 5, latitude: -16.6, longitude: -41.77 }),
      candidato({ id: 'b', nome: 'Bruno', corridasAtivas: 0, capacidade: 5 }),
    ];
    expect(PorDistancia.ordenar(lista, SEM_COORDENADA).map((c) => c.id)).toEqual(['b', 'a']);
  });

  it('respeita os mesmos filtros da manual', () => {
    const lista = [
      candidato({ id: 'recusou', nome: 'Recusou', jaRecusou: true, latitude: -16.6, longitude: -41.77 }),
      candidato({ id: 'ok', nome: 'Ok', latitude: -16.7, longitude: -41.9 }),
    ];
    expect(PorDistancia.ordenar(lista, contexto).map((c) => c.id)).toEqual(['ok']);
  });
});
