import { describe, expect, it } from 'vitest';

import {
  DIAS_PARA_RESPONDER_OFERTA,
  deQuemEAVez,
  estaEmAberto,
  estaVencida,
  podeAgir,
  prazoDeResposta,
  validarContraproposta,
  validarProposta,
  valorMinimoDaOferta,
  valorParaCobrar,
  type EstadoOferta,
} from '../ofertas';
import { VALOR_MINIMO_VENDA } from '../regras';

describe('valor mínimo da oferta', () => {
  it('é metade do preço pedido quando isso passa do mínimo de venda', () => {
    expect(valorMinimoDaOferta(10_000)).toBe(5_000); // R$ 100 -> R$ 50
    expect(valorMinimoDaOferta(50_000)).toBe(25_000);
  });

  it('nunca fica abaixo do valor mínimo de venda da plataforma', () => {
    // metade de R$ 12,00 seria R$ 6,00, mas o pedido nem pode ser criado
    expect(valorMinimoDaOferta(1_200)).toBe(VALOR_MINIMO_VENDA);
    expect(valorMinimoDaOferta(1_000)).toBe(VALOR_MINIMO_VENDA);
  });

  it('arredonda para cima, para não cair um centavo abaixo da metade', () => {
    expect(valorMinimoDaOferta(2_499)).toBe(1_250);
  });
});

describe('proposta do comprador', () => {
  it('aceita um valor no meio da faixa', () => {
    expect(validarProposta(8_000, 10_000).valido).toBe(true);
  });

  it('aceita exatamente o mínimo', () => {
    const preco = 10_000;
    expect(validarProposta(valorMinimoDaOferta(preco), preco).valido).toBe(true);
  });

  it('recusa um centavo abaixo do mínimo', () => {
    const preco = 10_000;
    const r = validarProposta(valorMinimoDaOferta(preco) - 1, preco);
    expect(r.valido).toBe(false);
    expect(r.motivo).toContain('R$ 50,00');
  });

  it('recusa oferta igual ou acima do preço, mandando comprar direto', () => {
    expect(validarProposta(10_000, 10_000).valido).toBe(false);
    expect(validarProposta(12_000, 10_000).motivo).toContain('comprar direto');
  });

  it('recusa valor zerado, negativo ou quebrado', () => {
    expect(validarProposta(0, 10_000).valido).toBe(false);
    expect(validarProposta(-500, 10_000).valido).toBe(false);
    expect(validarProposta(50.5, 10_000).valido).toBe(false);
  });
});

describe('contraproposta do vendedor', () => {
  const preco = 10_000;
  const oferecido = 6_000;

  it('aceita um valor entre a oferta e o preço', () => {
    expect(validarContraproposta(8_000, oferecido, preco).valido).toBe(true);
  });

  it('recusa valor menor ou igual ao que já foi oferecido', () => {
    expect(validarContraproposta(6_000, oferecido, preco).valido).toBe(false);
    expect(validarContraproposta(5_000, oferecido, preco).motivo).toContain('aceite a oferta');
  });

  it('recusa contraproposta que chega no preço do anúncio', () => {
    expect(validarContraproposta(10_000, oferecido, preco).valido).toBe(false);
  });

  it('respeita o valor mínimo de venda', () => {
    // anúncio barato, oferta ainda mais baixa: a contraproposta não pode
    // cair abaixo do mínimo com que o pedido pode ser criado
    const r = validarContraproposta(VALOR_MINIMO_VENDA - 1, 500, 5_000);
    expect(r.valido).toBe(false);
  });
});

describe('de quem é a vez', () => {
  it('proposta aberta espera o vendedor', () => {
    expect(deQuemEAVez('ABERTA')).toBe('VENDEDOR');
  });

  it('contraproposta espera o comprador', () => {
    expect(deQuemEAVez('CONTRAPROPOSTA')).toBe('COMPRADOR');
  });

  it('negociação encerrada não espera ninguém', () => {
    const fechados: EstadoOferta[] = ['ACEITA', 'RECUSADA', 'EXPIRADA', 'CANCELADA'];
    for (const estado of fechados) {
      expect(deQuemEAVez(estado)).toBeNull();
      expect(estaEmAberto(estado)).toBe(false);
    }
  });
});

describe('quem pode agir', () => {
  it('o vendedor responde a proposta aberta; o comprador não', () => {
    expect(podeAgir('ABERTA', 'VENDEDOR', 'ACEITAR')).toBe(true);
    expect(podeAgir('ABERTA', 'VENDEDOR', 'RECUSAR')).toBe(true);
    expect(podeAgir('ABERTA', 'VENDEDOR', 'CONTRAPROPOR')).toBe(true);
    expect(podeAgir('ABERTA', 'COMPRADOR', 'ACEITAR')).toBe(false);
  });

  it('o comprador responde a contraproposta; o vendedor não', () => {
    expect(podeAgir('CONTRAPROPOSTA', 'COMPRADOR', 'ACEITAR')).toBe(true);
    expect(podeAgir('CONTRAPROPOSTA', 'VENDEDOR', 'ACEITAR')).toBe(false);
  });

  it('só quem está esperando resposta pode cancelar a própria proposta', () => {
    expect(podeAgir('ABERTA', 'COMPRADOR', 'CANCELAR')).toBe(true);
    expect(podeAgir('ABERTA', 'VENDEDOR', 'CANCELAR')).toBe(false);
    expect(podeAgir('CONTRAPROPOSTA', 'VENDEDOR', 'CANCELAR')).toBe(true);
    expect(podeAgir('CONTRAPROPOSTA', 'COMPRADOR', 'CANCELAR')).toBe(false);
  });

  it('o comprador nunca contrapropõe — ele faz outra oferta', () => {
    expect(podeAgir('CONTRAPROPOSTA', 'COMPRADOR', 'CONTRAPROPOR')).toBe(false);
    expect(podeAgir('ABERTA', 'COMPRADOR', 'CONTRAPROPOR')).toBe(false);
  });

  it('ação desconhecida é recusada, mesmo sendo a vez do ator', () => {
    // um app desatualizado não pode inventar um movimento novo
    expect(podeAgir('ABERTA', 'VENDEDOR', 'RESPONDER' as never)).toBe(false);
    expect(podeAgir('CONTRAPROPOSTA', 'COMPRADOR', '' as never)).toBe(false);
  });

  it('negociação encerrada não aceita mais nada de ninguém', () => {
    const fechados: EstadoOferta[] = ['ACEITA', 'RECUSADA', 'EXPIRADA', 'CANCELADA'];
    for (const estado of fechados) {
      expect(podeAgir(estado, 'COMPRADOR', 'ACEITAR')).toBe(false);
      expect(podeAgir(estado, 'VENDEDOR', 'ACEITAR')).toBe(false);
      expect(podeAgir(estado, 'COMPRADOR', 'CANCELAR')).toBe(false);
    }
  });
});

describe('prazo de resposta', () => {
  const feitaEm = new Date('2026-03-10T12:00:00Z');

  it('vence em 3 dias corridos', () => {
    expect(prazoDeResposta(feitaEm).toISOString()).toBe('2026-03-13T12:00:00.000Z');
    expect(DIAS_PARA_RESPONDER_OFERTA).toBe(3);
  });

  it('não está vencida um minuto antes, e está um minuto depois', () => {
    expect(estaVencida(feitaEm, new Date('2026-03-13T11:59:00Z'))).toBe(false);
    expect(estaVencida(feitaEm, new Date('2026-03-13T12:01:00Z'))).toBe(true);
  });
});

describe('valor a cobrar no checkout', () => {
  it('só existe quando a oferta foi aceita', () => {
    expect(valorParaCobrar('ACEITA', 7_500)).toBe(7_500);
  });

  it('é nulo em qualquer estado que não seja aceita', () => {
    const outros: EstadoOferta[] = [
      'ABERTA',
      'CONTRAPROPOSTA',
      'RECUSADA',
      'EXPIRADA',
      'CANCELADA',
    ];
    for (const estado of outros) {
      expect(valorParaCobrar(estado, 7_500)).toBeNull();
    }
  });
});
