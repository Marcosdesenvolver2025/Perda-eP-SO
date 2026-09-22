// Colisão é a parte que, quando erra, erra de um jeito que arruína a partida:
// o carro atravessa o muro, ou trava no nada. Aqui os casos são montados à mão,
// com coordenadas conferíveis no papel.

import test from 'node:test';
import assert from 'node:assert/strict';

import {
  caixaDoCarro, caixaDoColisor, sobreposicao, resolverColisoes, dentroDe, encaixe,
} from '../src/jogo/colisao.js';
import { criarCarro } from '../src/jogo/fisica.js';
import { carroPorId } from '../src/jogo/carros.js';

const MODELO = carroPorId('pipoca');   // 3.72 m × 1.60 m

function carroEm(x, z, angulo = 0) {
  return criarCarro(MODELO.ficha, { x, z }, angulo);
}

function bloco(x, z, largura = 2, comprimento = 2, guinada = 0) {
  return { x, z, largura, comprimento, guinada, solido: true, altura: 2 };
}

test('dois retângulos longe não se tocam', () => {
  const a = caixaDoCarro(carroEm(0, 0));
  const b = caixaDoColisor(bloco(30, 30));
  assert.equal(sobreposicao(a, b), null);
});

test('dois retângulos no mesmo lugar se tocam, e a profundidade faz sentido', () => {
  const a = caixaDoCarro(carroEm(0, 0));
  const b = caixaDoColisor(bloco(0, 0));
  const toque = sobreposicao(a, b);
  assert.ok(toque, 'sobrepostos têm que acusar contato');
  assert.ok(toque.profundidade > 0);
  assert.ok(toque.profundidade <= MODELO.ficha.largura / 2 + 1 + 0.001,
    'a menor separação não pode ser maior que a metade do menor lado');
});

test('encostar de raspão é contato; um fio de distância não é', () => {
  const carro = carroEm(0, 0);          // ocupa x em [-0.80, 0.80]
  const quase = caixaDoColisor(bloco(0.80 + 1 - 0.02, 0));   // encosta 2 cm
  const nem = caixaDoColisor(bloco(0.80 + 1 + 0.02, 0));     // falta 2 cm
  assert.ok(sobreposicao(caixaDoCarro(carro), quase), 'deveria encostar');
  assert.equal(sobreposicao(caixaDoCarro(carro), nem), null, 'não deveria encostar');
});

test('o giro do retângulo é levado em conta', () => {
  // Um bloco comprido e fino, de lado, não alcança o carro; girado 90°, alcança.
  const carro = carroEm(0, 0);
  const deitado = caixaDoColisor(bloco(3.0, 0, 0.4, 6, 0));
  const empe = caixaDoColisor(bloco(3.0, 0, 0.4, 6, Math.PI / 2));
  assert.equal(sobreposicao(caixaDoCarro(carro), deitado), null);
  assert.ok(sobreposicao(caixaDoCarro(carro), empe), 'girado, o bloco tem que alcançar');
});

test('resolver a colisão tira o carro de dentro do obstáculo', () => {
  const carro = carroEm(0, 0);
  carro.vx = 10;
  const colisores = [bloco(1.2, 0, 2, 2)];
  resolverColisoes(carro, colisores);
  const ainda = sobreposicao(caixaDoCarro(carro), caixaDoColisor(colisores[0]));
  assert.equal(ainda, null, 'depois de resolver, não pode sobrar sobreposição');
  assert.ok(Math.abs(carro.vx) < 10, 'a batida tem que custar velocidade');
  assert.ok(carro.dano > 0, 'a batida tem que marcar a lataria');
});

test('bater com tudo não atravessa a parede', () => {
  const carro = carroEm(0, 0);
  carro.vx = 40;
  const parede = { ...bloco(4, 0, 0.4, 40), parede: true };
  for (let i = 0; i < 60; i++) {
    carro.x += carro.vx * (1 / 60);
    resolverColisoes(carro, [parede]);
  }
  assert.ok(carro.x < 4, `o carro furou a parede (x = ${carro.x.toFixed(2)})`);
});

test('encostar parado não conta como batida', () => {
  const carro = carroEm(0, 0);
  carro.vx = 0.05;
  const relatorio = resolverColisoes(carro, [bloco(1.2, 0)]);
  assert.equal(relatorio.batidas, 0, 'encostar devagar não é batida');
  assert.equal(carro.dano, 0, 'e não pode machucar a lataria');
});

test('cone é derrubado sem parar o carro', () => {
  const carro = carroEm(0, 0);
  carro.vx = 8;
  const cone = { ...bloco(1.0, 0, 0.45, 0.45), derrubavel: true, derrubado: false };
  const relatorio = resolverColisoes(carro, [cone]);
  assert.equal(relatorio.derrubados.length, 1, 'o cone tinha que cair');
  assert.equal(cone.derrubado, true);
  assert.equal(carro.vx, 8, 'o cone não pode frear o carro');
  assert.equal(carro.dano, 0, 'e nem amassar a lataria');

  const denovo = resolverColisoes(carro, [cone]);
  assert.equal(denovo.derrubados.length, 0, 'o mesmo cone não pode contar duas vezes');
});

test('dentroDe só aceita o carro inteiro dentro da área', () => {
  const area = { x: 0, z: 0, largura: 2.6, comprimento: 5.0, angulo: 0 };
  assert.ok(dentroDe(carroEm(0, 0, 0), area), 'alinhado e centrado tem que caber');
  assert.ok(!dentroDe(carroEm(0, 1.2, 0), area), 'metade para fora não vale');
  assert.ok(!dentroDe(carroEm(0, 0, 0.6), area), 'atravessado não vale');
  assert.ok(!dentroDe(carroEm(9, 9, 0), area), 'longe não vale');
});

test('encaixe dá nota de alinhamento e de quanto entrou', () => {
  const area = { x: 0, z: 0, largura: 2.6, comprimento: 5.0, angulo: 0 };

  const certinho = encaixe(carroEm(0, 0, 0), area);
  assert.equal(certinho.cantosDentro, 4);
  assert.equal(certinho.fracao, 1);
  assert.ok(certinho.alinhamento > 0.99, 'reto na vaga é alinhamento cheio');

  const torto = encaixe(carroEm(0, 0, 0.45), area);
  assert.ok(torto.alinhamento < 0.75, 'torto tem que perder nota');

  const meio = encaixe(carroEm(0, 1.6, 0), area);
  assert.ok(meio.fracao < 1 && meio.fracao >= 0, 'meio fora tem que dar fração parcial');
  assert.ok(meio.folga > 0, 'e tem que acusar o quanto sobrou para fora');
});

test('encaixe trata carro de ré como alinhado', () => {
  // Entrar de ré numa vaga é o carro a 180° dela: continua alinhado.
  const area = { x: 0, z: 0, largura: 2.6, comprimento: 5.0, angulo: 0 };
  const deRe = encaixe(carroEm(0, 0, Math.PI), area);
  assert.ok(deRe.alinhamento > 0.99, 'de ré, encaixado, o alinhamento é o mesmo');
});

test('o carro só acusa contato com o que está perto', () => {
  const carro = carroEm(0, 0);
  carro.vx = 5;
  const longe = Array.from({ length: 200 }, (_, i) => bloco(100 + i, 100 + i));
  const relatorio = resolverColisoes(carro, longe);
  assert.equal(relatorio.batidas, 0);
  assert.equal(carro.x, 0, 'nada longe pode mexer no carro');
});
