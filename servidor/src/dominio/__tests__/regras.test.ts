import { describe, expect, it } from 'vitest';

import { calcularSplit, montarRegrasSplit } from '../comissao';
import { calcularFrete, pesoCubadoG, validarMedidas } from '../frete';
import {
  calcularReembolso,
  dentroDaJanelaDeTeste,
  diasRestantesParaTestar,
  prazoParaTestar,
} from '../reembolso';

const RECEBEDORES = {
  plataforma: 'rp_plataforma',
  vendedor: 'rp_vendedor',
  entregador: 'rp_entregador',
};

describe('comissão e split', () => {
  it('cobra 16% quando o vendedor entrega por conta própria', () => {
    const s = calcularSplit({
      valorProduto: 10_000, // R$ 100,00
      valorFrete: 0,
      modalidade: 'COMBINADO_ENTRE_PARTES',
    });

    expect(s.taxaComissao).toBe(0.16);
    expect(s.comissao).toBe(1_600);
    expect(s.valorVendedor).toBe(8_400);
    expect(s.valorEntregador).toBe(0);
    expect(s.valorPlataforma).toBe(1_600);
    expect(s.total).toBe(10_000);
  });

  it('cobra 18% quando a entrega é feita pelo nosso entregador', () => {
    const s = calcularSplit({
      valorProduto: 10_000,
      valorFrete: 1_000, // R$ 10,00 de frete
      modalidade: 'ENTREGADOR_PROPRIO',
    });

    expect(s.taxaComissao).toBe(0.18);
    expect(s.comissao).toBe(1_800);
    expect(s.valorVendedor).toBe(8_200);
    expect(s.valorEntregador).toBe(800); // 80% do frete
    expect(s.valorPlataforma).toBe(2_000); // comissão + 20% do frete
    expect(s.total).toBe(11_000);
  });

  it('fecha o split no centavo mesmo com preço quebrado', () => {
    // 18% de R$ 99,99 = 1799,82 centavos -> a sobra fica com a plataforma
    const s = calcularSplit({
      valorProduto: 9_999,
      valorFrete: 999,
      modalidade: 'ENTREGADOR_PROPRIO',
    });

    expect(s.comissao).toBe(1_799);
    expect(s.valorVendedor + s.valorEntregador + s.valorPlataforma).toBe(s.total);
  });

  it('nunca deixa o vendedor receber menos que o arredondamento devido', () => {
    for (let preco = 1; preco <= 2_000; preco++) {
      const s = calcularSplit({
        valorProduto: preco,
        valorFrete: 0,
        modalidade: 'COMBINADO_ENTRE_PARTES',
      });
      expect(s.comissao).toBeLessThanOrEqual(preco * 0.16);
      expect(s.valorVendedor + s.valorPlataforma).toBe(s.total);
    }
  });

  it('recusa frete em pedido sem o nosso entregador', () => {
    expect(() =>
      calcularSplit({
        valorProduto: 10_000,
        valorFrete: 1_000,
        modalidade: 'COMBINADO_ENTRE_PARTES',
      }),
    ).toThrow(/frete/i);
  });

  it('gera regras de split que somam o total pago', () => {
    const s = calcularSplit({
      valorProduto: 12_345,
      valorFrete: 1_234,
      modalidade: 'ENTREGADOR_PROPRIO',
    });
    const regras = montarRegrasSplit(s, RECEBEDORES);

    expect(regras).toHaveLength(3);
    expect(regras.reduce((acc, r) => acc + r.amount, 0)).toBe(s.total);
    // só a plataforma paga a taxa de processamento e a sobra
    expect(regras.filter((r) => r.options.charge_processing_fee)).toHaveLength(1);
    // o entregador não responde por chargeback
    expect(regras.find((r) => r.recipient_id === 'rp_entregador')!.options.liable)
      .toBe(false);
  });

  it('não monta split com entregador sem recebedor cadastrado', () => {
    const s = calcularSplit({
      valorProduto: 10_000,
      valorFrete: 1_000,
      modalidade: 'ENTREGADOR_PROPRIO',
    });
    expect(() =>
      montarRegrasSplit(s, { plataforma: 'rp_1', vendedor: 'rp_2', entregador: null }),
    ).toThrow(/entregador/i);
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

  it('cobra pelo peso cubado quando o volume manda mais que a balança', () => {
    const volumoso = { pesoG: 500, comprimentoCm: 60, larguraCm: 50, alturaCm: 40 };
    expect(pesoCubadoG(volumoso)).toBe(20_000);
    expect(calcularFrete(volumoso)).toBe(2_500); // bate no teto
  });

  it('cobra o frete base para pacote leve', () => {
    expect(calcularFrete({ pesoG: 800, comprimentoCm: 20, larguraCm: 15, alturaCm: 5 }))
      .toBe(800);
  });
});

describe('janela de 4 dias para testar', () => {
  const entrega = new Date('2026-03-10T14:00:00Z');

  it('conta a partir da data de entrega, não da compra', () => {
    expect(prazoParaTestar(entrega).toISOString()).toBe('2026-03-14T14:00:00.000Z');
  });

  it('aceita pedido de reembolso dentro dos 4 dias', () => {
    expect(dentroDaJanelaDeTeste(entrega, new Date('2026-03-14T13:59:00Z'))).toBe(true);
  });

  it('recusa depois do prazo', () => {
    expect(dentroDaJanelaDeTeste(entrega, new Date('2026-03-14T14:00:01Z'))).toBe(false);
  });

  it('não abre janela para pedido ainda não entregue', () => {
    expect(dentroDaJanelaDeTeste(null, new Date())).toBe(false);
  });

  it('mostra quantos dias faltam', () => {
    expect(diasRestantesParaTestar(entrega, new Date('2026-03-10T14:00:00Z'))).toBe(4);
    expect(diasRestantesParaTestar(entrega, new Date('2026-03-13T10:00:00Z'))).toBe(2);
    expect(diasRestantesParaTestar(entrega, new Date('2026-03-20T10:00:00Z'))).toBe(0);
  });
});

describe('reembolso', () => {
  it('retém comissão e frete quando a entrega foi feita pelo nosso entregador', () => {
    const s = calcularSplit({
      valorProduto: 10_000,
      valorFrete: 1_000,
      modalidade: 'ENTREGADOR_PROPRIO',
    });
    const r = calcularReembolso(s, 'ENTREGADOR_PROPRIO');

    expect(r.valorRetido).toBe(2_800); // 1.800 de comissão + 1.000 de frete
    expect(r.valorReembolsado).toBe(8_200); // total 11.000 - 2.800
    expect(r.debitoVendedor).toBe(8_200); // sai do vendedor
    expect(r.debitoPlataforma).toBe(0);
    expect(r.debitoEntregador).toBe(0); // entregador não devolve nada
  });

  it('devolve tudo quando a entrega foi combinada entre as partes', () => {
    const s = calcularSplit({
      valorProduto: 10_000,
      valorFrete: 0,
      modalidade: 'COMBINADO_ENTRE_PARTES',
    });
    const r = calcularReembolso(s, 'COMBINADO_ENTRE_PARTES');

    expect(r.valorRetido).toBe(0);
    expect(r.valorReembolsado).toBe(10_000);
    expect(r.debitoVendedor).toBe(8_400);
    expect(r.debitoPlataforma).toBe(1_600); // a plataforma devolve a comissão
  });

  it('permite desligar a retenção caso a política mude', () => {
    const s = calcularSplit({
      valorProduto: 10_000,
      valorFrete: 1_000,
      modalidade: 'ENTREGADOR_PROPRIO',
    });
    const r = calcularReembolso(s, 'ENTREGADOR_PROPRIO', {
      reterComissao: false,
      reterFrete: false,
    });

    expect(r.valorRetido).toBe(0);
    expect(r.valorReembolsado).toBe(11_000);
  });

  it('o que sai dos saldos sempre bate com o que volta para o comprador', () => {
    const s = calcularSplit({
      valorProduto: 7_777,
      valorFrete: 1_111,
      modalidade: 'ENTREGADOR_PROPRIO',
    });
    const r = calcularReembolso(s, 'ENTREGADOR_PROPRIO');

    expect(r.debitoVendedor + r.debitoPlataforma + r.debitoEntregador).toBe(
      r.valorReembolsado,
    );
    expect(r.valorReembolsado + r.valorRetido).toBe(s.total);
  });
});
