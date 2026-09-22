// A base: número, ângulo, sorteio e as malhas 3D. São as peças que todo o
// resto usa, então um erro aqui aparece em lugares que não têm nada a ver.

import test from 'node:test';
import assert from 'node:assert/strict';

import {
  limitar, misturar, aproximar, normalizarAngulo, misturarAngulo, sinal,
  criarSorteio, entre, inteiro, escolher, embaralhar, distanciaPlana,
  frente, direita, tonalizar, misturarCor, corTexto, formatarTempo,
  formatarDinheiro, TAU,
} from '../src/nucleo/matematica.js';
import { Construtor, juntar, limites } from '../src/motor/malha.js';
import { construirCarro } from '../src/motor/modelos.js';
import { CARROS } from '../src/jogo/carros.js';

test('limitar e misturar fazem o óbvio', () => {
  assert.equal(limitar(5, 0, 1), 1);
  assert.equal(limitar(-5, 0, 1), 0);
  assert.equal(limitar(0.4, 0, 1), 0.4);
  assert.equal(misturar(0, 10, 0.25), 2.5);
  assert.equal(aproximar(0, 10, 3), 3);
  assert.equal(aproximar(10, 0, 3), 7);
  assert.equal(aproximar(0, 1, 5), 1, 'não pode passar do alvo');
  assert.equal(sinal(-3), -1);
  assert.equal(sinal(0), 0);
});

test('ângulo normalizado cai sempre em (-PI, PI]', () => {
  for (let v = -20; v <= 20; v += 0.37) {
    const n = normalizarAngulo(v);
    assert.ok(n > -Math.PI - 1e-9 && n <= Math.PI + 1e-9, `${v} virou ${n}`);
    assert.ok(Math.abs(Math.sin(n) - Math.sin(v)) < 1e-9, 'tem que ser o mesmo ângulo');
    assert.ok(Math.abs(Math.cos(n) - Math.cos(v)) < 1e-9);
  }
});

test('interpolar ângulo pega o caminho curto', () => {
  // De 170° para -170° são 20° passando por 180°, não 340° pelo outro lado.
  const a = 170 * Math.PI / 180;
  const b = -170 * Math.PI / 180;
  const meio = normalizarAngulo(misturarAngulo(a, b, 0.5));
  assert.ok(Math.abs(Math.abs(meio) - Math.PI) < 0.02,
    `o meio do caminho tinha que ser perto de 180°, deu ${(meio * 180 / Math.PI).toFixed(1)}°`);
});

test('frente e direita são perpendiculares e unitárias', () => {
  for (let a = -3; a < 3; a += 0.31) {
    const f = frente(a);
    const d = direita(a);
    assert.ok(Math.abs(Math.hypot(f.x, f.z) - 1) < 1e-12, 'frente tem que ser unitária');
    assert.ok(Math.abs(Math.hypot(d.x, d.z) - 1) < 1e-12, 'direita tem que ser unitária');
    assert.ok(Math.abs(f.x * d.x + f.z * d.z) < 1e-12, 'têm que ser perpendiculares');
  }
  // Ângulo 0 aponta para -Z, com a direita em +X.
  const f0 = frente(0);
  assert.ok(Math.abs(f0.x) < 1e-12 && Math.abs(f0.z + 1) < 1e-12);
  const d0 = direita(0);
  assert.ok(Math.abs(d0.x - 1) < 1e-12 && Math.abs(d0.z) < 1e-12);
});

test('o sorteio com semente repete e se espalha', () => {
  const a = criarSorteio(12345);
  const b = criarSorteio(12345);
  const c = criarSorteio(12346);
  const listaA = Array.from({ length: 200 }, () => a());
  const listaB = Array.from({ length: 200 }, () => b());
  const listaC = Array.from({ length: 200 }, () => c());
  assert.deepEqual(listaA, listaB, 'a mesma semente tem que repetir');
  assert.notDeepEqual(listaA, listaC, 'sementes diferentes têm que divergir');

  for (const v of listaA) assert.ok(v >= 0 && v < 1, `sorteio fora da faixa: ${v}`);
  const media = listaA.reduce((s, v) => s + v, 0) / listaA.length;
  assert.ok(Math.abs(media - 0.5) < 0.09, `média torta demais: ${media.toFixed(3)}`);

  // Precisa espalhar pelos dez décimos, não se concentrar num canto.
  const baldes = new Array(10).fill(0);
  for (const v of listaA) baldes[Math.floor(v * 10)]++;
  assert.ok(baldes.every((n) => n > 0), 'o sorteio deixou faixas inteiras vazias');
});

test('entre, inteiro, escolher e embaralhar respeitam os limites', () => {
  const sortear = criarSorteio(7);
  for (let i = 0; i < 500; i++) {
    const v = entre(sortear, -3, 8);
    assert.ok(v >= -3 && v <= 8);
    const n = inteiro(sortear, 2, 5);
    assert.ok(Number.isInteger(n) && n >= 2 && n <= 5, `inteiro fora: ${n}`);
    assert.ok(['a', 'b', 'c'].includes(escolher(sortear, ['a', 'b', 'c'])));
  }
  const original = [1, 2, 3, 4, 5, 6, 7, 8];
  const misturado = embaralhar(sortear, original);
  assert.deepEqual(original, [1, 2, 3, 4, 5, 6, 7, 8], 'embaralhar não pode mexer no original');
  assert.deepEqual([...misturado].sort((x, y) => x - y), original, 'não pode perder nem inventar item');
});

test('cores: tonalizar e misturar ficam dentro do byte', () => {
  assert.equal(tonalizar(0x808080, 2), 0xffffff, 'não pode estourar');
  assert.equal(tonalizar(0x808080, 0), 0x000000);
  assert.equal(misturarCor(0x000000, 0xffffff, 0.5), 0x808080);
  assert.equal(corTexto(0x0a0b0c), '#0a0b0c', 'tem que completar com zero à esquerda');
  for (const cor of [0x000000, 0xffffff, 0x123456, 0xabcdef]) {
    for (const fator of [0, 0.5, 1, 1.9, 3]) {
      const t = tonalizar(cor, fator);
      assert.ok(t >= 0 && t <= 0xffffff, `estourou: ${t}`);
    }
  }
});

test('formatação de tempo e dinheiro', () => {
  assert.equal(formatarTempo(0), '0:00');
  assert.equal(formatarTempo(9), '0:09');
  assert.equal(formatarTempo(61), '1:01');
  assert.equal(formatarTempo(-5), '0:00', 'tempo negativo vira zero');
  assert.ok(formatarDinheiro(1500).startsWith('R$'));
});

test('distanciaPlana ignora a altura', () => {
  assert.equal(distanciaPlana(0, 0, 3, 4), 5);
  assert.equal(distanciaPlana(-1, -1, -1, -1), 0);
});

// ---------------------------------------------------------------------------

test('a caixa tem 8 vértices, 6 faces e o tamanho pedido', () => {
  const m = new Construtor().caixa(0, 0, 0, 2, 4, 6, 0xff0000).terminar();
  assert.equal(m.vertices.length / 3, 8);
  assert.equal(m.faces.length, 6);
  const b = limites(m);
  assert.equal(b.maxX - b.minX, 2);
  assert.equal(b.maxY - b.minY, 4);
  assert.equal(b.maxZ - b.minZ, 6);
});

test('as faces da caixa apontam todas para fora', () => {
  // Com os vértices na ordem anti-horária vista de fora, o produto vetorial
  // das duas primeiras arestas tem que apontar para longe do centro.
  const m = new Construtor().caixa(0, 0, 0, 2, 2, 2, 0x808080).terminar();
  const v = m.vertices;
  const ponto = (i) => ({ x: v[i * 3], y: v[i * 3 + 1], z: v[i * 3 + 2] });

  for (const face of m.faces) {
    const p0 = ponto(face.i[0]), p1 = ponto(face.i[1]), p2 = ponto(face.i[2]);
    const e1 = { x: p1.x - p0.x, y: p1.y - p0.y, z: p1.z - p0.z };
    const e2 = { x: p2.x - p0.x, y: p2.y - p0.y, z: p2.z - p0.z };
    const n = {
      x: e1.y * e2.z - e1.z * e2.y,
      y: e1.z * e2.x - e1.x * e2.z,
      z: e1.x * e2.y - e1.y * e2.x,
    };
    // centro da face, que a partir da origem já é a direção "para fora"
    const centro = face.i.reduce((soma, i) => {
      const p = ponto(i);
      return { x: soma.x + p.x / 4, y: soma.y + p.y / 4, z: soma.z + p.z / 4 };
    }, { x: 0, y: 0, z: 0 });
    const produto = n.x * centro.x + n.y * centro.y + n.z * centro.z;
    assert.ok(produto > 0,
      `face virada para dentro (produto ${produto.toFixed(2)})`);
  }
});

test('juntar preserva vértices e faces, com os índices certos', () => {
  const a = new Construtor().caixa(0, 0, 0, 1, 1, 1, 1).terminar();
  const b = new Construtor().caixa(5, 0, 0, 1, 1, 1, 2).terminar();
  const junto = juntar([a, b]);
  assert.equal(junto.vertices.length, a.vertices.length + b.vertices.length);
  assert.equal(junto.faces.length, a.faces.length + b.faces.length);
  const maior = Math.max(...junto.faces.flatMap((f) => f.i));
  assert.equal(maior, junto.vertices.length / 3 - 1, 'índice apontando para fora da lista');
  const caixa = limites(junto);
  assert.equal(caixa.maxX, 5.5);
});

test('todo carro do catálogo vira um modelo 3D coerente', () => {
  for (const modelo of CARROS) {
    const peca = construirCarro(modelo);
    assert.ok(peca.carroceria.faces.length > 20, `${modelo.nome}: carroceria pobre demais`);
    assert.equal(peca.rodas.length, 4, `${modelo.nome}: não tem quatro rodas`);
    assert.equal(peca.rodas.filter((r) => r.dianteira).length, 2,
      `${modelo.nome}: rodas dianteiras erradas`);
    assert.equal(peca.farois.length, 2);
    assert.equal(peca.lanternas.length, 2);

    const b = limites(peca.carroceria);
    assert.ok(b.minY >= -0.01, `${modelo.nome}: a carroceria afunda no chão`);
    assert.ok(b.maxY < modelo.ficha.altura + 0.9,
      `${modelo.nome}: mais alto (${b.maxY.toFixed(2)}) do que a ficha diz`);
    assert.ok(b.maxX - b.minX <= modelo.ficha.largura + 0.35,
      `${modelo.nome}: mais largo do que a ficha diz`);
    assert.ok(b.maxZ - b.minZ <= modelo.ficha.comprimento + 0.05,
      `${modelo.nome}: mais comprido do que a ficha diz`);

    // As rodas ficam dentro da largura e em cima do eixo.
    for (const roda of peca.rodas) {
      assert.ok(Math.abs(roda.x) < modelo.ficha.largura / 2 + 0.01,
        `${modelo.nome}: roda para fora da carroceria`);
      assert.ok(Math.abs(Math.abs(roda.z) - modelo.ficha.entreEixos / 2) < 1e-9,
        `${modelo.nome}: roda fora do entre-eixos`);
    }
  }
});

test('trocar a cor gera outro modelo, e o mesmo pedido reaproveita', () => {
  const modelo = CARROS[0];
  const padrao = construirCarro(modelo);
  const vermelho = construirCarro(modelo, 0xff0000);
  assert.notEqual(padrao, vermelho, 'cores diferentes têm que dar malhas diferentes');
  assert.equal(construirCarro(modelo, 0xff0000), vermelho, 'a mesma cor tem que reaproveitar');
  assert.equal(padrao.carroceria.faces.length, vermelho.carroceria.faces.length);
});

test('TAU é uma volta inteira', () => {
  assert.equal(TAU, Math.PI * 2);
});
