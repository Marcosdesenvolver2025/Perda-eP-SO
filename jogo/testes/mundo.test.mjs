// O mundo é sorteado, então não dá para conferir um bairro só: estes testes
// varrem dezenas de sementes procurando o caso que quebra. Bairro sem rua,
// carro nascendo dentro de um prédio, vaga em cima da calçada — é o tipo de
// defeito que só aparece numa semente entre cinquenta, e que estraga a partida
// inteira de quem tirou aquela.

import test from 'node:test';
import assert from 'node:assert/strict';

import { gerarMundo, pisoEm, pontoNaRua, LISTA_CENARIOS } from '../src/jogo/mundo.js';
import { criarCarro } from '../src/jogo/fisica.js';
import { caixaDoCarro, caixaDoColisor, sobreposicao } from '../src/jogo/colisao.js';
import { carroPorId } from '../src/jogo/carros.js';
import { criarSorteio } from '../src/nucleo/matematica.js';

const SEMENTES = Array.from({ length: 40 }, (_, i) => 1000 + i * 977);

function paraCadaMundo(visitar, opcoes = {}) {
  for (const semente of SEMENTES) {
    for (const cenario of LISTA_CENARIOS) {
      visitar(gerarMundo(semente, { cenario, ...opcoes }), semente, cenario);
    }
  }
}

test('todo bairro tem rua, quarteirão e muro em volta', () => {
  paraCadaMundo((mundo, semente, cenario) => {
    const onde = `${cenario}/${semente}`;
    assert.ok(mundo.vias.length >= 4, `${onde}: poucas ruas`);
    assert.ok(mundo.quadras.length >= 1, `${onde}: nenhum quarteirão`);
    assert.ok(mundo.colisores.some((c) => c.parede), `${onde}: o mundo não está cercado`);
    assert.ok(mundo.rotas.length >= 8, `${onde}: o trânsito não teria por onde andar`);
  });
});

test('o carro sempre nasce no asfalto, e dentro do mundo', () => {
  paraCadaMundo((mundo, semente, cenario) => {
    const onde = `${cenario}/${semente}`;
    const piso = pisoEm(mundo, mundo.inicio.x, mundo.inicio.z);
    assert.ok(piso === 'asfalto' || piso === 'terra',
      `${onde}: o carro nasceu em '${piso}'`);
    assert.ok(Math.abs(mundo.inicio.x) < mundo.limite, `${onde}: nasceu fora em X`);
    assert.ok(Math.abs(mundo.inicio.z) < mundo.limite, `${onde}: nasceu fora em Z`);
    assert.ok(Number.isFinite(mundo.inicio.angulo), `${onde}: ângulo inicial inválido`);
  });
});

test('o carro nunca nasce dentro de alguma coisa', () => {
  const modelo = carroPorId('diplomata');   // o mais comprido dos comuns
  paraCadaMundo((mundo, semente, cenario) => {
    const carro = criarCarro(modelo.ficha,
      { x: mundo.inicio.x, z: mundo.inicio.z }, mundo.inicio.angulo);
    const caixa = caixaDoCarro(carro);
    for (const colisor of mundo.colisores) {
      const toque = sobreposicao(caixa, caixaDoColisor(colisor));
      assert.ok(!toque,
        `${cenario}/${semente}: o carro nasceu dentro de um obstáculo em `
        + `(${colisor.x.toFixed(1)}, ${colisor.z.toFixed(1)})`);
    }
  });
});

test('nenhum obstáculo fica no meio da pista', () => {
  paraCadaMundo((mundo, semente, cenario) => {
    for (const colisor of mundo.colisores) {
      if (colisor.parede || colisor.carroParado || colisor.derrubavel) continue;
      const piso = pisoEm(mundo, colisor.x, colisor.z);
      assert.notEqual(piso, 'asfalto',
        `${cenario}/${semente}: obstáculo plantado no asfalto em `
        + `(${colisor.x.toFixed(1)}, ${colisor.z.toFixed(1)})`);
    }
  });
});

test('nada nasce em cima de outra coisa', () => {
  paraCadaMundo((mundo, semente, cenario) => {
    const solidos = mundo.colisores.filter((c) => !c.parede);
    for (let i = 0; i < solidos.length; i++) {
      for (let j = i + 1; j < solidos.length; j++) {
        const toque = sobreposicao(caixaDoColisor(solidos[i]), caixaDoColisor(solidos[j]));
        if (toque && toque.profundidade > 0.6) {
          assert.fail(`${cenario}/${semente}: dois obstáculos ocupam o mesmo lugar em `
            + `(${solidos[i].x.toFixed(1)}, ${solidos[i].z.toFixed(1)})`);
        }
      }
    }
  });
});

test('a mesma semente devolve sempre o mesmo bairro', () => {
  const a = gerarMundo(4242, { cenario: 'cidade' });
  const b = gerarMundo(4242, { cenario: 'cidade' });
  assert.equal(a.props.length, b.props.length);
  assert.equal(a.vias.length, b.vias.length);
  assert.deepEqual(a.inicio, b.inicio);
  assert.deepEqual(
    a.props.map((p) => [p.tipo, p.x.toFixed(4), p.z.toFixed(4)]),
    b.props.map((p) => [p.tipo, p.x.toFixed(4), p.z.toFixed(4)]),
  );
});

test('sementes diferentes devolvem bairros diferentes', () => {
  const a = gerarMundo(1, { cenario: 'cidade' });
  const b = gerarMundo(2, { cenario: 'cidade' });
  const iguais = a.vias.length === b.vias.length
    && a.vias.every((v, i) => v.centro === b.vias[i].centro);
  assert.ok(!iguais, 'duas sementes deram exatamente o mesmo traçado');
});

test('o pátio de estacionamento tem vagas de tamanho utilizável', () => {
  for (const semente of SEMENTES.slice(0, 16)) {
    const mundo = gerarMundo(semente, {
      cenario: 'cidade',
      estacionamento: { larguraVaga: 2.6 },
    });
    if (!mundo.vagas.length) continue;
    assert.ok(mundo.patio, `semente ${semente}: há vagas mas não há pátio`);
    for (const vaga of mundo.vagas) {
      assert.ok(vaga.largura >= 2.4, `vaga estreita demais: ${vaga.largura}`);
      assert.ok(vaga.comprimento >= 4.8, `vaga curta demais: ${vaga.comprimento}`);
      assert.equal(pisoEm(mundo, vaga.x, vaga.z), 'asfalto', 'vaga fora do asfalto');
    }
  }
});

test('pontoNaRua devolve sempre um ponto no asfalto', () => {
  for (const semente of SEMENTES.slice(0, 20)) {
    const mundo = gerarMundo(semente, { cenario: 'cidade' });
    const sortear = criarSorteio(semente ^ 0xbeef);
    for (let i = 0; i < 30; i++) {
      const ponto = pontoNaRua(mundo, sortear);
      assert.equal(pisoEm(mundo, ponto.x, ponto.z), 'asfalto',
        `semente ${semente}: ponto de objetivo fora da rua`);
    }
  }
});

test('pontoNaRua respeita a distância mínima que foi pedida', () => {
  const mundo = gerarMundo(777, { cenario: 'cidade' });
  const sortear = criarSorteio(99);
  const origem = { x: mundo.inicio.x, z: mundo.inicio.z };
  for (let i = 0; i < 40; i++) {
    const ponto = pontoNaRua(mundo, sortear, origem, 30);
    const distancia = Math.hypot(ponto.x - origem.x, ponto.z - origem.z);
    assert.ok(distancia >= 30 || distancia === 0,
      `ponto a ${distancia.toFixed(1)} m quando o mínimo era 30`);
  }
});

test('o piso combina com o cenário', () => {
  const campo = gerarMundo(11, { cenario: 'campo' });
  assert.equal(pisoEm(campo, campo.inicio.x, campo.inicio.z), 'terra',
    'estrada de terra tem que ter piso de terra');
  const praia = gerarMundo(11, { cenario: 'praia' });
  assert.equal(praia.ficha.piso, 'areia');
});
