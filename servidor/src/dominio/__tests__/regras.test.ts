import { describe, expect, it } from 'vitest';

import { calcularSplit, montarRegrasSplit } from '../comissao';
import { calcularFrete, pesoCubadoG, validarMedidas } from '../frete';
import { TABELA_DE_TARIFAS, VALOR_MINIMO_VENDA, tarifaFixa } from '../regras';
import {
  calcularReembolso,
  dentroDaJanelaDeTeste,
  diasRestantesParaTestar,
  prazoParaTestar,
} from '../reembolso';

const RECEBEDORES = {
  plataforma: 'rp_plataforma',
  vendedor: 'rp_vendedor',
};

describe('tarifa fixa por faixa', () => {
  it('aplica a tarifa de cada faixa da tabela', () => {
    expect(tarifaFixa(1_000)).toBe(250); // R$ 10,00 -> R$ 2,50
    expect(tarifaFixa(2_499)).toBe(250); // R$ 24,99 -> R$ 2,50
    expect(tarifaFixa(2_500)).toBe(450); // R$ 25,00 -> R$ 4,50
    expect(tarifaFixa(4_999)).toBe(450); // R$ 49,99 -> R$ 4,50
    expect(tarifaFixa(5_000)).toBe(650); // R$ 50,00 -> R$ 6,50
    expect(tarifaFixa(9_999)).toBe(650); // R$ 99,99 -> R$ 6,50
    expect(tarifaFixa(10_000)).toBe(850); // R$ 100,00 -> R$ 8,50
    expect(tarifaFixa(19_999)).toBe(850); // R$ 199,99 -> R$ 8,50
    expect(tarifaFixa(20_000)).toBe(1_850); // R$ 200,00 -> R$ 18,50
    expect(tarifaFixa(500_000)).toBe(1_850); // R$ 5.000,00 -> R$ 18,50
  });

  it('cobre qualquer valor: a última faixa é aberta', () => {
    for (const valor of [1_000, 7_777, 123_456, 9_999_999]) {
      expect(tarifaFixa(valor)).toBeGreaterThan(0);
    }
  });

  it('a tabela é crescente e sem buraco entre as faixas', () => {
    for (let i = 1; i < TABELA_DE_TARIFAS.length; i++) {
      const anterior = TABELA_DE_TARIFAS[i - 1]!;
      const atual = TABELA_DE_TARIFAS[i]!;
      expect(atual.ateInclusive).toBeGreaterThan(anterior.ateInclusive);
      expect(atual.tarifa).toBeGreaterThan(anterior.tarifa);
      // o primeiro centavo depois do teto anterior já cai na faixa atual
      expect(tarifaFixa(anterior.ateInclusive + 1)).toBe(atual.tarifa);
    }
  });
});

describe('comissão e split', () => {
  it('cobra 12% mais a tarifa da faixa', () => {
    const s = calcularSplit({
      valorProduto: 10_000, // R$ 100,00
      modalidade: 'ENTREGADOR_PROPRIO',
    });

    expect(s.taxaComissao).toBe(0.12);
    expect(s.comissao).toBe(1_200); // 12% de R$ 100,00
    expect(s.tarifa).toBe(850); // faixa até R$ 199,99
    expect(s.totalDescontado).toBe(2_050);
    expect(s.valorVendedor).toBe(7_950);
    expect(s.valorPlataforma).toBe(2_050);
    expect(s.total).toBe(10_000); // o comprador paga só o preço do produto
  });

  it('cobra o mesmo com ou sem entregador nosso', () => {
    const com = calcularSplit({ valorProduto: 8_000, modalidade: 'ENTREGADOR_PROPRIO' });
    const sem = calcularSplit({ valorProduto: 8_000, modalidade: 'COMBINADO_ENTRE_PARTES' });
    expect(com).toEqual(sem);
  });

  it('funciona na venda mínima de R$ 10,00', () => {
    const s = calcularSplit({ valorProduto: 1_000, modalidade: 'ENTREGADOR_PROPRIO' });

    expect(s.comissao).toBe(120);
    expect(s.tarifa).toBe(250);
    expect(s.valorVendedor).toBe(630);
    expect(s.valorVendedor + s.valorPlataforma).toBe(s.total);
  });

  it('recusa venda abaixo do mínimo', () => {
    expect(() =>
      calcularSplit({ valorProduto: 999, modalidade: 'ENTREGADOR_PROPRIO' }),
    ).toThrow(/mínimo/i);
  });

  it('fecha o split no centavo mesmo com preço quebrado', () => {
    // 12% de R$ 99,99 = 1199,88 centavos -> a sobra fica com a plataforma
    const s = calcularSplit({ valorProduto: 9_999, modalidade: 'ENTREGADOR_PROPRIO' });

    expect(s.comissao).toBe(1_199);
    expect(s.tarifa).toBe(650);
    expect(s.valorVendedor + s.valorPlataforma).toBe(s.total);
  });

  it('o vendedor nunca recebe zero ou negativo, em nenhum preço', () => {
    for (let preco = VALOR_MINIMO_VENDA; preco <= 60_000; preco += 7) {
      const s = calcularSplit({ valorProduto: preco, modalidade: 'ENTREGADOR_PROPRIO' });
      expect(s.valorVendedor).toBeGreaterThan(0);
      expect(s.comissao).toBeLessThanOrEqual(preco * 0.12);
      expect(s.valorVendedor + s.valorPlataforma).toBe(s.total);
    }
  });

  it('gera regras de split que somam o total pago', () => {
    const s = calcularSplit({ valorProduto: 12_345, modalidade: 'ENTREGADOR_PROPRIO' });
    const regras = montarRegrasSplit(s, RECEBEDORES);

    expect(regras).toHaveLength(2); // plataforma e vendedor; entregador fica fora
    expect(regras.reduce((acc, r) => acc + r.amount, 0)).toBe(s.total);
    // só a plataforma paga a taxa de processamento e a sobra
    expect(regras.filter((r) => r.options.charge_processing_fee)).toHaveLength(1);
  });
});

describe('limites de peso e tamanho', () => {
  const ok = { pesoG: 3_000, comprimentoCm: 40, larguraCm: 30, alturaCm: 20 };

  it('aceita pacote dentro de 20 kg e 60 cm', () => {
    expect(validarMedidas(ok).valido).toBe(true);
  });

  it('recusa acima de 20 kg', () => {
    const r = validarMedidas({ ...ok, pesoG: 20_001 });
    expect(r.valido).toBe(false);
    expect(r.erros.join(' ')).toMatch(/20 kg/);
  });

  it('aceita exatamente no limite', () => {
    expect(
      validarMedidas({ pesoG: 20_000, comprimentoCm: 60, larguraCm: 60, alturaCm: 60 })
        .valido,
    ).toBe(true);
  });

  it('recusa quando qualquer lado passa de 60 cm', () => {
    expect(validarMedidas({ ...ok, alturaCm: 61 }).valido).toBe(false);
    expect(validarMedidas({ ...ok, larguraCm: 80 }).valido).toBe(false);
  });

  it('junta todos os erros de uma vez', () => {
    const r = validarMedidas({ pesoG: 30_000, comprimentoCm: 90, larguraCm: 10, alturaCm: 10 });
    expect(r.erros).toHaveLength(2);
  });

  it('calcula o peso cubado para dimensionar a corrida', () => {
    const volumoso = { pesoG: 500, comprimentoCm: 60, larguraCm: 50, alturaCm: 40 };
    expect(pesoCubadoG(volumoso)).toBe(20_000);
    expect(calcularFrete(volumoso)).toBe(2_500); // custo interno, não cobrado do comprador
  });
});

describe('janela de 7 dias para testar (CDC art. 49)', () => {
  const entrega = new Date('2026-03-10T14:00:00Z');

  it('conta 7 dias a partir da data de entrega, não da compra', () => {
    expect(prazoParaTestar(entrega).toISOString()).toBe('2026-03-17T14:00:00.000Z');
  });

  it('aceita pedido de devolução dentro dos 7 dias', () => {
    expect(dentroDaJanelaDeTeste(entrega, new Date('2026-03-17T13:59:00Z'))).toBe(true);
  });

  it('recusa depois do prazo', () => {
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
  it('devolve TUDO que o comprador pagou (CDC art. 49)', () => {
    const s = calcularSplit({ valorProduto: 10_000, modalidade: 'ENTREGADOR_PROPRIO' });
    const r = calcularReembolso(s);

    expect(r.valorRetido).toBe(0);
    expect(r.valorReembolsado).toBe(10_000);
    expect(r.debitoVendedor).toBe(7_950); // devolve o que recebeu
    expect(r.debitoPlataforma).toBe(2_050); // comissão + tarifa saem do caixa
  });

  it('permite reter caso a caso, fora do prazo legal', () => {
    const s = calcularSplit({ valorProduto: 10_000, modalidade: 'ENTREGADOR_PROPRIO' });
    const r = calcularReembolso(s, { reterComissao: true, reterTarifa: true });

    expect(r.valorRetido).toBe(2_050);
    expect(r.valorReembolsado).toBe(7_950);
  });

  it('o que sai dos saldos sempre bate com o que volta para o comprador', () => {
    for (const preco of [1_000, 2_500, 7_777, 19_999, 20_000, 99_999]) {
      const s = calcularSplit({ valorProduto: preco, modalidade: 'ENTREGADOR_PROPRIO' });
      const r = calcularReembolso(s);

      expect(r.debitoVendedor + r.debitoPlataforma).toBe(r.valorReembolsado);
      expect(r.valorReembolsado + r.valorRetido).toBe(s.total);
      expect(r.valorReembolsado).toBe(preco); // devolução integral
    }
  });
});
