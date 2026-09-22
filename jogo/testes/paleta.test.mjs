// A paleta decide se dá para JOGAR, não só se fica bonito.
//
// Faixa que não contrasta com o asfalto some no meio da pista; janela da mesma
// cor da fachada apaga o prédio; carro da cor do chão vira invisível. São erros
// que a gente comete retocando cor, um tom de cada vez, sem perceber.
//
// São DUAS medidas aqui, porque são dois problemas diferentes:
//
//   Pintura no chão e janela na fachada funcionam por CLARIDADE — a faixa
//   branca aparece por ser mais clara que o asfalto. Para esses, contraste de
//   luminância da WCAG é a conta certa.
//
//   Carro e cone aparecem por COR. Um carro vermelho sobre asfalto cinza tem
//   quase a mesma claridade (contraste 1.13:1, que a WCAG reprovaria) e mesmo
//   assim salta aos olhos, porque o que difere é o matiz. Medir isso com
//   luminância reprova a paleta inteira por um motivo que não existe. Para
//   esses a conta é a distância perceptual em Lab (ΔE), que enxerga cor.

import test from 'node:test';
import assert from 'node:assert/strict';

import {
  CEU, TERRENO, VIA, FACHADAS, FACHADAS_INDUSTRIAIS, PREDIO,
  VEGETACAO, PINTURAS, CARRO, CENA, LUZ,
} from '../src/motor/paleta.js';
import { CENARIOS } from '../src/jogo/mundo.js';
import { CLIMAS } from '../src/jogo/clima.js';
import { CARROS } from '../src/jogo/carros.js';

function canal(v) {
  const c = v / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function luminancia(cor) {
  return 0.2126 * canal((cor >> 16) & 255)
    + 0.7152 * canal((cor >> 8) & 255)
    + 0.0722 * canal(cor & 255);
}

/** Razão de contraste da WCAG: 1 = idênticas, 21 = preto contra branco. */
function contraste(a, b) {
  const la = luminancia(a), lb = luminancia(b);
  const claro = Math.max(la, lb), escuro = Math.min(la, lb);
  return (claro + 0.05) / (escuro + 0.05);
}

/** sRGB → Lab (D65). É o espaço em que distância parece distância para o olho. */
function paraLab(cor) {
  const linear = (v) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const r = linear((cor >> 16) & 255);
  const g = linear((cor >> 8) & 255);
  const b = linear(cor & 255);
  const x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
  const y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b);
  const z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
  const f = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  const fx = f(x), fy = f(y), fz = f(z);
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}

/** ΔE76. Abaixo de ~2 o olho nem nota; acima de ~20 são cores francamente outras. */
function distanciaDeCor(a, b) {
  const p = paraLab(a), q = paraLab(b);
  return Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]);
}

function todasAsCores() {
  const saida = [];
  const colher = (objeto, caminho) => {
    for (const [chave, valor] of Object.entries(objeto)) {
      if (typeof valor === 'number' && Number.isInteger(valor) && valor > 255) {
        saida.push([`${caminho}.${chave}`, valor]);
      }
    }
  };
  colher(CEU, 'CEU');
  colher(TERRENO, 'TERRENO');
  colher(VIA, 'VIA');
  colher(PREDIO, 'PREDIO');
  colher(VEGETACAO, 'VEGETACAO');
  colher(CARRO, 'CARRO');
  colher(CENA, 'CENA');
  FACHADAS.forEach((c, i) => saida.push([`FACHADAS[${i}]`, c]));
  FACHADAS_INDUSTRIAIS.forEach((c, i) => saida.push([`FACHADAS_INDUSTRIAIS[${i}]`, c]));
  PINTURAS.forEach((c, i) => saida.push([`PINTURAS[${i}]`, c]));
  return saida;
}

test('toda cor da paleta é um RGB válido', () => {
  for (const [nome, cor] of todasAsCores()) {
    assert.ok(Number.isInteger(cor) && cor >= 0 && cor <= 0xffffff,
      `${nome} não é uma cor: ${cor}`);
  }
});

test('a pintura da pista salta do asfalto', () => {
  // 3:1 é o mínimo da WCAG para elemento gráfico. Abaixo disso a faixa começa
  // a sumir no asfalto — e a faixa é o que diz onde fica a pista.
  const comFaixa = contraste(VIA.faixa, VIA.asfalto);
  assert.ok(comFaixa >= 3,
    `faixa branca contra asfalto: ${comFaixa.toFixed(2)}:1, precisa de 3:1`);

  const comAmarela = contraste(VIA.faixaAmarela, VIA.asfalto);
  assert.ok(comAmarela >= 3,
    `faixa amarela contra asfalto: ${comAmarela.toFixed(2)}:1`);

  const naTerra = contraste(VIA.faixa, VIA.terra);
  assert.ok(naTerra >= 1.8, `faixa contra estrada de terra: ${naTerra.toFixed(2)}:1`);
});

test('o asfalto é cinza-médio, não preto nem branco', () => {
  // Preto engole a pintura; claro demais e o carro escuro some. A faixa do meio
  // é o que dá o visual de jogo de celular em vez de simulador sombrio.
  for (const nome of ['asfalto', 'asfaltoClaro']) {
    const l = luminancia(VIA[nome]);
    assert.ok(l > 0.10 && l < 0.45,
      `VIA.${nome} tem luminância ${l.toFixed(3)}, fora da faixa 0.10–0.45`);
  }
});

test('meio-fio e calçada se distinguem do asfalto', () => {
  assert.ok(contraste(VIA.calcada, VIA.asfalto) >= 1.7, 'calçada confunde com a rua');
  assert.ok(contraste(VIA.meioFio, VIA.asfalto) >= 1.4, 'meio-fio confunde com a rua');
});

test('a janela aparece na fachada, em toda fachada', () => {
  for (const [i, fachada] of FACHADAS.entries()) {
    const c = contraste(PREDIO.janela, fachada);
    assert.ok(c >= 2, `FACHADAS[${i}]: janela com contraste ${c.toFixed(2)}:1`);
  }
  for (const [i, fachada] of FACHADAS_INDUSTRIAIS.entries()) {
    const c = contraste(PREDIO.janela, fachada);
    assert.ok(c >= 1.8, `FACHADAS_INDUSTRIAIS[${i}]: janela com contraste ${c.toFixed(2)}:1`);
  }
});

test('nenhuma tinta de trânsito se confunde com a pista', () => {
  // Contra o chão em que esses carros de fato andam: asfalto e terra.
  for (const [i, tinta] of PINTURAS.entries()) {
    for (const [nome, chao] of [['asfalto', VIA.asfalto], ['terra', VIA.terra]]) {
      const d = distanciaDeCor(tinta, chao);
      assert.ok(d >= 22,
        `PINTURAS[${i}] (#${tinta.toString(16)}) quase igual ao ${nome}: ΔE ${d.toFixed(1)}`);
    }
  }
});

test('a cor de cada carro do catálogo se destaca do asfalto', () => {
  for (const modelo of CARROS) {
    const d = distanciaDeCor(modelo.corpo.cor, VIA.asfalto);
    assert.ok(d >= 30, `${modelo.nome} (#${modelo.corpo.cor.toString(16)}) `
      + `quase some no asfalto: ΔE ${d.toFixed(1)}`);
  }
});

test('o cone laranja grita contra o asfalto', () => {
  const d = distanciaDeCor(CENA.cone, VIA.asfalto);
  assert.ok(d >= 45, `cone contra asfalto: ΔE ${d.toFixed(1)} — o slalom ficaria às cegas`);
});

test('a grama é mais clara que o asfalto: a pista tem que se recortar do mato', () => {
  assert.ok(luminancia(TERRENO.grama) > luminancia(VIA.asfalto),
    'grama mais escura que a rua deixa o traçado ilegível de longe');
  assert.ok(contraste(TERRENO.grama, VIA.asfalto) >= 1.5,
    'grama e asfalto quase iguais: não dá para ver onde a rua acaba');
});

test('todo cenário usa cores de verdade e tem fachadas', () => {
  for (const [id, cenario] of Object.entries(CENARIOS)) {
    for (const campo of ['base', 'corAsfalto', 'corCalcada']) {
      assert.ok(Number.isInteger(cenario[campo]) && cenario[campo] <= 0xffffff,
        `cenário ${id}: ${campo} inválido`);
    }
    assert.ok(Array.isArray(cenario.fachadas) && cenario.fachadas.length >= 3,
      `cenário ${id}: sem fachadas suficientes`);
  }
});

test('todo clima tem céu, luz e alcance de névoa coerentes', () => {
  for (const [id, c] of Object.entries(CLIMAS)) {
    for (const campo of ['corCeuAlto', 'corCeuBaixo', 'corHorizonte']) {
      assert.ok(Number.isInteger(c[campo]) && c[campo] <= 0xffffff,
        `clima ${id}: ${campo} inválido`);
    }
    assert.ok(c.luz > 0.1 && c.luz <= 1.4, `clima ${id}: luz ${c.luz} fora de faixa`);
    assert.ok(c.ambienteLuz >= 0.25 && c.ambienteLuz <= 0.9,
      `clima ${id}: ambiente ${c.ambienteLuz} fora de faixa`);
    assert.ok(c.alcanceNevoa >= 30, `clima ${id}: névoa curta demais`);
    assert.ok(c.atrito > 0.5 && c.atrito <= 1.1, `clima ${id}: atrito irreal`);
  }
});

test('o céu é mais claro embaixo que em cima', () => {
  for (const [id, c] of Object.entries(CLIMAS)) {
    assert.ok(luminancia(c.corCeuBaixo) > luminancia(c.corCeuAlto),
      `clima ${id}: o céu está mais escuro no horizonte que no zênite`);
  }
});

test('a luz padrão é chapada, que é o que dá o visual de brinquedo', () => {
  assert.ok(LUZ.ambiente >= 0.6,
    `luz ambiente ${LUZ.ambiente} baixa demais: a sombra volta a ser dura`);
  assert.ok(LUZ.vinheta <= 0.2, 'vinheta pesada traz de volta o clima fotográfico');
  assert.ok(LUZ.nevoa >= 150, 'névoa curta demais para um dia claro');
});
