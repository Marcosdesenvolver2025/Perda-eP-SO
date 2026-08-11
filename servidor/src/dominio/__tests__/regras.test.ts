import { describe, expect, it } from 'vitest';

import { calcularSplit, montarRegrasSplit } from '../comissao';
import { cabeNaEntregaDaPlataforma, pesoCubadoG, validarMedidas } from '../frete';
import {
  ALTURA_MAXIMA_CM,
  LARGURA_MAXIMA_CM,
  PESO_MAXIMO_G,
  TABELA_DE_TARIFAS,
  VALOR_MINIMO_VENDA,
  tarifaFixa,
} from '../regras';
import {
  calcularReembolso,
  dentroDaJanelaDeTeste,
  diasRestantesParaTestar,
  prazoParaTestar,
} from '../reembolso';

const RECEBEDORES = { plataforma: 'rp_plataforma', vendedor: 'rp_vendedor' };

describe('tarifa fixa por faixa (modalidade PLATAFORMA)', () => {
  it('aplica a tarifa de cada faixa da tabela', () => {
    expect(tarifaFixa(1_000, 'PLATAFORMA')).toBe(250); // R$ 10,00
    expect(tarifaFixa(2_499, 'PLATAFORMA')).toBe(250); // R$ 24,99
    expect(tarifaFixa(4_999, 'PLATAFORMA')).toBe(450); // R$ 49,99
    expect(tarifaFixa(9_999, 'PLATAFORMA')).toBe(650); // R$ 99,99
    expect(tarifaFixa(19_999, 'PLATAFORMA')).toBe(850); // R$ 199,99
    expect(tarifaFixa(49_999, 'PLATAFORMA')).toBe(1_050); // R$ 499,99
    expect(tarifaFixa(50_000, 'PLATAFORMA')).toBe(1_450); // R$ 500,00
  });

  it('vira de faixa no primeiro centavo depois do teto', () => {
    const fronteiras: Array<[number, number, number]> = [
      // [teto da faixa, tarifa da faixa, tarifa da faixa seguinte]
      [2_499, 250, 450], // R$ 24,99 -> R$ 25,00
      [4_999, 450, 650], // R$ 49,99 -> R$ 50,00
      [9_999, 650, 850], // R$ 99,99 -> R$ 100,00
      [19_999, 850, 1_050], // R$ 199,99 -> R$ 200,00
      [49_999, 1_050, 1_450], // R$ 499,99 -> R$ 500,00
    ];

    for (const [teto, aqui, depois] of fronteiras) {
      expect(tarifaFixa(teto, 'PLATAFORMA'), `teto ${teto}`).toBe(aqui);
      expect(tarifaFixa(teto + 1, 'PLATAFORMA'), `teto+1 ${teto + 1}`).toBe(depois);
    }
  });

  /**
   * Qualquer tabela de tarifa em faixas cria um degrau na virada: um centavo a
   * mais no preço cai na faixa seguinte e o vendedor recebe MENOS.
   *
   * A tabela nova reduziu muito o degrau da faixa de R$ 200 (era R$ 10,00),
   * mas não zerou os degraus. Estes testes fixam o tamanho de cada um: se
   * alguém mexer na tabela e reintroduzir um degrau grande, quebram aqui.
   */
  it('o degrau da faixa de R$ 200 caiu de R$ 10,00 para R$ 2,00', () => {
    const antes = calcularSplit({ valorProduto: 19_999, modalidade: 'PLATAFORMA' });
    const depois = calcularSplit({ valorProduto: 20_000, modalidade: 'PLATAFORMA' });

    expect(depois.tarifa - antes.tarifa).toBe(200);
    // o vendedor ainda perde 2 reais menos 1 centavo ao passar de R$ 199,99
    expect(antes.valorVendedor - depois.valorVendedor).toBe(200);
  });

  it('nenhum degrau passa de R$ 4,00', () => {
    const degrausEsperados: Array<[number, number]> = [
      [2_499, 200], //  R$ 24,99  -> R$ 25,00  : perde R$ 2,00
      [4_999, 200], //  R$ 49,99  -> R$ 50,00  : perde R$ 2,00
      [9_999, 200], //  R$ 99,99  -> R$ 100,00 : perde R$ 2,00
      [19_999, 200], // R$ 199,99 -> R$ 200,00 : perde R$ 2,00
      [49_999, 400], // R$ 499,99 -> R$ 500,00 : perde R$ 4,00
    ];

    for (const [teto, degrauEsperado] of degrausEsperados) {
      const antes = calcularSplit({ valorProduto: teto, modalidade: 'PLATAFORMA' });
      const depois = calcularSplit({ valorProduto: teto + 1, modalidade: 'PLATAFORMA' });
      const degrau = antes.valorVendedor - depois.valorVendedor;

      expect(degrau, `fronteira ${teto}`).toBe(degrauEsperado);
      expect(degrau, `fronteira ${teto}`).toBeLessThanOrEqual(400);
    }
  });

  it('fora das fronteiras, subir de preço sempre rende mais ao vendedor', () => {
    const fronteiras = new Set(TABELA_DE_TARIFAS.map((f) => f.ateInclusive));

    let anterior = -1;
    for (let preco = VALOR_MINIMO_VENDA; preco <= 120_000; preco++) {
      const { valorVendedor } = calcularSplit({ valorProduto: preco, modalidade: 'PLATAFORMA' });
      // a virada de faixa é o único ponto em que o valor cai
      if (!fronteiras.has(preco - 1)) {
        expect(valorVendedor, `preço ${preco}`).toBeGreaterThanOrEqual(anterior);
      }
      anterior = valorVendedor;
    }
  });

  it('na modalidade VENDEDOR não existe degrau nenhum', () => {
    let anterior = -1;
    for (let preco = VALOR_MINIMO_VENDA; preco <= 120_000; preco += 7) {
      const { valorVendedor } = calcularSplit({ valorProduto: preco, modalidade: 'VENDEDOR' });
      expect(valorVendedor, `preço ${preco}`).toBeGreaterThan(anterior);
      anterior = valorVendedor;
    }
  });

  it('não cobra tarifa quando quem entrega é o vendedor', () => {
    for (const preco of [1_000, 2_500, 19_999, 50_000, 999_999]) {
      expect(tarifaFixa(preco, 'VENDEDOR')).toBe(0);
    }
  });

  it('a tabela é crescente e sem buraco entre as faixas', () => {
    for (let i = 1; i < TABELA_DE_TARIFAS.length; i++) {
      const anterior = TABELA_DE_TARIFAS[i - 1]!;
      const atual = TABELA_DE_TARIFAS[i]!;
      expect(atual.ateInclusive).toBeGreaterThan(anterior.ateInclusive);
      expect(atual.tarifa).toBeGreaterThan(anterior.tarifa);
    }
  });
});

describe('comissão e split', () => {
  it('PLATAFORMA: cobra 12% mais a tarifa da faixa', () => {
    const s = calcularSplit({ valorProduto: 10_000, modalidade: 'PLATAFORMA' });

    expect(s.taxaComissao).toBe(0.12);
    expect(s.comissao).toBe(1_200);
    expect(s.tarifa).toBe(850);
    expect(s.totalDescontado).toBe(2_050);
    expect(s.valorVendedor).toBe(7_950);
    expect(s.total).toBe(10_000); // o comprador paga só o produto
  });

  it('VENDEDOR: cobra só os 12%', () => {
    const s = calcularSplit({ valorProduto: 10_000, modalidade: 'VENDEDOR' });

    expect(s.comissao).toBe(1_200);
    expect(s.tarifa).toBe(0);
    expect(s.totalDescontado).toBe(1_200);
    expect(s.valorVendedor).toBe(8_800);
    expect(s.total).toBe(10_000);
  });

  it('entregar por conta própria rende mais ao vendedor, em qualquer preço', () => {
    for (const preco of [1_000, 4_999, 19_999, 50_000, 200_000]) {
      const plataforma = calcularSplit({ valorProduto: preco, modalidade: 'PLATAFORMA' });
      const vendedor = calcularSplit({ valorProduto: preco, modalidade: 'VENDEDOR' });

      expect(vendedor.valorVendedor).toBeGreaterThan(plataforma.valorVendedor);
      // a diferença é exatamente a tarifa da faixa
      expect(vendedor.valorVendedor - plataforma.valorVendedor).toBe(plataforma.tarifa);
    }
  });

  it('congela a modalidade no resultado, para o pedido guardar', () => {
    expect(calcularSplit({ valorProduto: 5_000, modalidade: 'VENDEDOR' }).modalidade)
      .toBe('VENDEDOR');
    expect(calcularSplit({ valorProduto: 5_000, modalidade: 'PLATAFORMA' }).modalidade)
      .toBe('PLATAFORMA');
  });

  it('funciona na venda mínima de R$ 10,00 nas duas modalidades', () => {
    const p = calcularSplit({ valorProduto: 1_000, modalidade: 'PLATAFORMA' });
    expect(p.valorVendedor).toBe(630); // 1000 - 120 - 250

    const v = calcularSplit({ valorProduto: 1_000, modalidade: 'VENDEDOR' });
    expect(v.valorVendedor).toBe(880); // 1000 - 120
  });

  it('recusa venda abaixo do mínimo', () => {
    expect(() => calcularSplit({ valorProduto: 999, modalidade: 'PLATAFORMA' }))
      .toThrow(/mínimo/i);
    expect(() => calcularSplit({ valorProduto: 999, modalidade: 'VENDEDOR' }))
      .toThrow(/mínimo/i);
  });

  it('fecha o split no centavo mesmo com preço quebrado', () => {
    const s = calcularSplit({ valorProduto: 9_999, modalidade: 'PLATAFORMA' });
    expect(s.comissao).toBe(1_199); // 12% de 99,99 arredondado para baixo
    expect(s.valorVendedor + s.valorPlataforma).toBe(s.total);
  });

  it('o vendedor nunca recebe zero ou negativo, em nenhum preço', () => {
    for (const modalidade of ['PLATAFORMA', 'VENDEDOR'] as const) {
      for (let preco = VALOR_MINIMO_VENDA; preco <= 60_000; preco += 7) {
        const s = calcularSplit({ valorProduto: preco, modalidade });
        expect(s.valorVendedor, `${modalidade} ${preco}`).toBeGreaterThan(0);
        expect(s.valorVendedor + s.valorPlataforma).toBe(s.total);
      }
    }
  });

  it('gera regras de split que somam o total pago', () => {
    for (const modalidade of ['PLATAFORMA', 'VENDEDOR'] as const) {
      const s = calcularSplit({ valorProduto: 12_345, modalidade });
      const regras = montarRegrasSplit(s, RECEBEDORES);

      expect(regras).toHaveLength(2);
      expect(regras.reduce((acc, r) => acc + r.amount, 0)).toBe(s.total);
      expect(regras.filter((r) => r.options.charge_processing_fee)).toHaveLength(1);
    }
  });
});

describe('limites do pacote', () => {
  const ok = { pesoG: 3_000, comprimentoCm: 40, larguraCm: 30, alturaCm: 20 };

  it('PLATAFORMA aceita pacote dentro de 20 kg, 100 cm de largura e 100 de altura', () => {
    expect(cabeNaEntregaDaPlataforma(ok).valido).toBe(true);
    expect(
      cabeNaEntregaDaPlataforma({
        pesoG: PESO_MAXIMO_G,
        comprimentoCm: 150,
        larguraCm: LARGURA_MAXIMA_CM,
        alturaCm: ALTURA_MAXIMA_CM,
      }).valido,
    ).toBe(true);
  });

  it('PLATAFORMA recusa acima de 20 kg', () => {
    const r = cabeNaEntregaDaPlataforma({ ...ok, pesoG: 20_001 });
    expect(r.valido).toBe(false);
    expect(r.erros.join(' ')).toMatch(/20 kg/);
  });

  it('PLATAFORMA recusa largura ou altura acima de 100 cm', () => {
    expect(cabeNaEntregaDaPlataforma({ ...ok, larguraCm: 101 }).valido).toBe(false);
    expect(cabeNaEntregaDaPlataforma({ ...ok, alturaCm: 101 }).valido).toBe(false);
  });

  it('PLATAFORMA junta todos os problemas de uma vez', () => {
    const r = cabeNaEntregaDaPlataforma({
      pesoG: 30_000,
      comprimentoCm: 40,
      larguraCm: 120,
      alturaCm: 130,
    });
    expect(r.valido).toBe(false);
    expect(r.erros).toHaveLength(3); // peso, largura e altura
  });

  it('VENDEDOR não tem limite de peso nem de tamanho', () => {
    const gigante = { pesoG: 90_000, comprimentoCm: 300, larguraCm: 250, alturaCm: 200 };
    expect(validarMedidas(gigante, 'VENDEDOR').valido).toBe(true);
    expect(validarMedidas(gigante, 'PLATAFORMA').valido).toBe(false);
  });

  it('VENDEDOR ainda exige que os números façam sentido', () => {
    expect(validarMedidas({ ...ok, pesoG: 0 }, 'VENDEDOR').valido).toBe(false);
    expect(validarMedidas({ ...ok, alturaCm: 0 }, 'VENDEDOR').valido).toBe(false);
  });

  it('calcula o peso cubado para dimensionar a corrida', () => {
    expect(pesoCubadoG({ pesoG: 500, comprimentoCm: 60, larguraCm: 50, alturaCm: 40 }))
      .toBe(20_000);
  });
});

describe('janela de 7 dias para testar (CDC art. 49)', () => {
  const entrega = new Date('2026-03-10T14:00:00Z');

  it('conta 7 dias a partir da data de entrega', () => {
    expect(prazoParaTestar(entrega).toISOString()).toBe('2026-03-17T14:00:00.000Z');
  });

  it('aceita dentro do prazo e recusa depois', () => {
    expect(dentroDaJanelaDeTeste(entrega, new Date('2026-03-17T13:59:00Z'))).toBe(true);
    expect(dentroDaJanelaDeTeste(entrega, new Date('2026-03-17T14:00:01Z'))).toBe(false);
  });

  it('não abre janela para pedido ainda não entregue', () => {
    expect(dentroDaJanelaDeTeste(null, new Date())).toBe(false);
  });

  it('mostra quantos dias faltam', () => {
    expect(diasRestantesParaTestar(entrega, new Date('2026-03-10T14:00:00Z'))).toBe(7);
    expect(diasRestantesParaTestar(entrega, new Date('2026-03-15T10:00:00Z'))).toBe(3);
    expect(diasRestantesParaTestar(entrega, new Date('2026-03-25T10:00:00Z'))).toBe(0);
  });
});

describe('reembolso', () => {
  it('devolve tudo que o comprador pagou, nas duas modalidades', () => {
    for (const modalidade of ['PLATAFORMA', 'VENDEDOR'] as const) {
      const s = calcularSplit({ valorProduto: 10_000, modalidade });
      const r = calcularReembolso(s);

      expect(r.valorRetido).toBe(0);
      expect(r.valorReembolsado).toBe(10_000);
      expect(r.debitoVendedor).toBe(s.valorVendedor);
      expect(r.debitoPlataforma).toBe(s.totalDescontado);
    }
  });

  it('permite reter caso a caso, fora do prazo legal', () => {
    const s = calcularSplit({ valorProduto: 10_000, modalidade: 'PLATAFORMA' });
    const r = calcularReembolso(s, { reterComissao: true, reterTarifa: true });

    expect(r.valorRetido).toBe(2_050);
    expect(r.valorReembolsado).toBe(7_950);
  });

  it('o que sai dos saldos sempre bate com o que volta ao comprador', () => {
    for (const modalidade of ['PLATAFORMA', 'VENDEDOR'] as const) {
      for (const preco of [1_000, 2_500, 7_777, 49_999, 50_000, 99_999]) {
        const s = calcularSplit({ valorProduto: preco, modalidade });
        const r = calcularReembolso(s);

        expect(r.debitoVendedor + r.debitoPlataforma).toBe(r.valorReembolsado);
        expect(r.valorReembolsado).toBe(preco);
      }
    }
  });
});
