import { describe, expect, it } from 'vitest';

import {
  ESTADOS_FINAIS,
  TRANSICOES,
  estadoDoPedidoPara,
  gerarCodigoConfirmacao,
  mascararTelefone,
  podeTransitar,
  proximosPassos,
  type EstadoEntrega,
} from '../logistica';

describe('máquina de estados da entrega', () => {
  it('percorre o fluxo feliz do começo ao fim', () => {
    const caminho: Array<[EstadoEntrega, EstadoEntrega, 'ADMIN' | 'ENTREGADOR']> = [
      ['AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'ADMIN'],
      ['ATRIBUIDA', 'ACEITA', 'ENTREGADOR'],
      ['ACEITA', 'A_CAMINHO_DA_COLETA', 'ENTREGADOR'],
      ['A_CAMINHO_DA_COLETA', 'CHEGOU_NA_COLETA', 'ENTREGADOR'],
      ['CHEGOU_NA_COLETA', 'PRODUTO_COLETADO', 'ENTREGADOR'],
      ['PRODUTO_COLETADO', 'EM_ROTA_PARA_ENTREGA', 'ENTREGADOR'],
      ['EM_ROTA_PARA_ENTREGA', 'CHEGOU_NA_ENTREGA', 'ENTREGADOR'],
      ['CHEGOU_NA_ENTREGA', 'ENTREGUE', 'ENTREGADOR'],
    ];

    for (const [de, para, ator] of caminho) {
      const r = podeTransitar(de, para, ator, {
        entregador: 'u1',
        fotoPacote: 'https://exemplo/foto.jpg',
        volumes: 1,
        codigoConfirmacao: '4821',
      });
      expect(r.permitida, `${de} -> ${para}`).toBe(true);
    }
  });

  it('não deixa pular etapa', () => {
    // marcar entregue sem ter coletado
    expect(podeTransitar('ACEITA', 'ENTREGUE', 'ENTREGADOR').permitida).toBe(false);
    // coletar sem ter aceitado
    expect(podeTransitar('AGUARDANDO_ATRIBUICAO', 'PRODUTO_COLETADO', 'ENTREGADOR').permitida)
      .toBe(false);
    // sair para entrega sem ter o produto
    expect(podeTransitar('CHEGOU_NA_COLETA', 'EM_ROTA_PARA_ENTREGA', 'ENTREGADOR').permitida)
      .toBe(false);
  });

  it('exige foto e volumes para concluir a coleta', () => {
    const semNada = podeTransitar('CHEGOU_NA_COLETA', 'PRODUTO_COLETADO', 'ENTREGADOR');
    expect(semNada.permitida).toBe(false);
    expect(semNada.motivo).toMatch(/foto/i);

    const semVolumes = podeTransitar('CHEGOU_NA_COLETA', 'PRODUTO_COLETADO', 'ENTREGADOR', {
      fotoPacote: 'https://exemplo/foto.jpg',
    });
    expect(semVolumes.permitida).toBe(false);
    expect(semVolumes.motivo).toMatch(/volume/i);

    expect(
      podeTransitar('CHEGOU_NA_COLETA', 'PRODUTO_COLETADO', 'ENTREGADOR', {
        fotoPacote: 'https://exemplo/foto.jpg',
        volumes: 2,
      }).permitida,
    ).toBe(true);
  });

  it('exige código de confirmação para dar como entregue', () => {
    const sem = podeTransitar('CHEGOU_NA_ENTREGA', 'ENTREGUE', 'ENTREGADOR');
    expect(sem.permitida).toBe(false);
    expect(sem.motivo).toMatch(/código/i);
  });

  it('exige motivo na recusa', () => {
    expect(podeTransitar('ATRIBUIDA', 'RECUSADA', 'ENTREGADOR').permitida).toBe(false);
    expect(
      podeTransitar('ATRIBUIDA', 'RECUSADA', 'ENTREGADOR', { motivo: 'longe demais' }).permitida,
    ).toBe(true);
  });

  it('exige entregador na atribuição', () => {
    expect(podeTransitar('AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'ADMIN').permitida).toBe(false);
    expect(
      podeTransitar('AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'ADMIN', { entregador: 'u1' }).permitida,
    ).toBe(true);
  });

  it('recusa devolve a corrida para a fila do administrador', () => {
    expect(podeTransitar('RECUSADA', 'AGUARDANDO_ATRIBUICAO', 'SISTEMA').permitida).toBe(true);
  });

  it('entregador não faz o trabalho do administrador', () => {
    expect(
      podeTransitar('AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'ENTREGADOR', { entregador: 'u1' })
        .permitida,
    ).toBe(false);
    expect(podeTransitar('ACEITA', 'CANCELADA', 'ENTREGADOR').permitida).toBe(false);
  });

  it('administrador cancela enquanto o produto não foi entregue', () => {
    for (const de of [
      'AGUARDANDO_ATRIBUICAO',
      'ATRIBUIDA',
      'ACEITA',
      'PRODUTO_COLETADO',
      'CHEGOU_NA_ENTREGA',
    ] as const) {
      expect(podeTransitar(de, 'CANCELADA', 'ADMIN').permitida, de).toBe(true);
    }
  });

  it('estado final não muda mais', () => {
    for (const final of ESTADOS_FINAIS) {
      expect(podeTransitar(final, 'ACEITA', 'ADMIN').permitida).toBe(false);
      expect(proximosPassos(final, 'ENTREGADOR')).toEqual([]);
    }
  });

  it('lista os próximos passos de cada ator', () => {
    expect(proximosPassos('ATRIBUIDA', 'ENTREGADOR').sort()).toEqual(['ACEITA', 'RECUSADA']);
    expect(proximosPassos('CHEGOU_NA_COLETA', 'ENTREGADOR')).toContain('PRODUTO_COLETADO');
    expect(proximosPassos('AGUARDANDO_ATRIBUICAO', 'ENTREGADOR')).toEqual([]);
  });

  it('toda transição declarada tem pelo menos um ator', () => {
    for (const t of TRANSICOES) {
      expect(t.atores.length, `${t.de} -> ${t.para}`).toBeGreaterThan(0);
    }
  });
});

describe('reflexo da entrega no pedido', () => {
  it('acompanha a corrida de ida', () => {
    expect(estadoDoPedidoPara('AGUARDANDO_ATRIBUICAO', 'ENTREGA'))
      .toBe('AGUARDANDO_AGENDAMENTO_DE_COLETA');
    expect(estadoDoPedidoPara('ACEITA', 'ENTREGA')).toBe('AGUARDANDO_AGENDAMENTO_DE_COLETA');
    expect(estadoDoPedidoPara('A_CAMINHO_DA_COLETA', 'ENTREGA')).toBe('A_CAMINHO_DA_COLETA');
    expect(estadoDoPedidoPara('PRODUTO_COLETADO', 'ENTREGA')).toBe('PRODUTO_COLETADO');
    expect(estadoDoPedidoPara('EM_ROTA_PARA_ENTREGA', 'ENTREGA')).toBe('EM_ROTA_PARA_ENTREGA');
    expect(estadoDoPedidoPara('ENTREGUE', 'ENTREGA')).toBe('ENTREGUE');
  });

  it('na devolução, a entrega ao vendedor é o gatilho do estorno', () => {
    expect(estadoDoPedidoPara('PRODUTO_COLETADO', 'DEVOLUCAO')).toBe('DEVOLUCAO_EM_TRANSITO');
    expect(estadoDoPedidoPara('ENTREGUE', 'DEVOLUCAO')).toBe('DEVOLVIDO_AO_VENDEDOR');
  });

  it('a devolução nunca marca o pedido como ENTREGUE de novo', () => {
    const estados: EstadoEntrega[] = [
      'AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'ACEITA', 'A_CAMINHO_DA_COLETA',
      'CHEGOU_NA_COLETA', 'PRODUTO_COLETADO', 'EM_ROTA_PARA_ENTREGA',
      'CHEGOU_NA_ENTREGA', 'ENTREGUE',
    ];
    for (const e of estados) {
      expect(estadoDoPedidoPara(e, 'DEVOLUCAO')).not.toBe('ENTREGUE');
    }
  });

  it('estado interno do entregador não muda o que o comprador vê', () => {
    expect(estadoDoPedidoPara('CHEGOU_NA_COLETA', 'ENTREGA')).toBe('A_CAMINHO_DA_COLETA');
    expect(estadoDoPedidoPara('CANCELADA', 'ENTREGA')).toBeNull();
  });
});

describe('utilidades', () => {
  it('gera código de 4 dígitos', () => {
    expect(gerarCodigoConfirmacao(() => 0)).toBe('1000');
    expect(gerarCodigoConfirmacao(() => 0.9999)).toBe('9999');
    for (let i = 0; i < 200; i++) {
      expect(gerarCodigoConfirmacao()).toMatch(/^\d{4}$/);
    }
  });

  it('mascara o telefone deixando só o começo e o fim', () => {
    expect(mascararTelefone('33999887766')).toBe('(33) ••••-7766');
    expect(mascararTelefone('(33) 99988-7766')).toBe('(33) ••••-7766');
    expect(mascararTelefone(null)).toBeNull();
    expect(mascararTelefone('12')).toBe('•••');
  });
});
