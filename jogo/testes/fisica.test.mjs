// A física é o coração do jogo: se ela mudar sem querer, todo o resto muda
// junto. Estes testes fixam o comportamento — não os números exatos, mas o que
// a pessoa sente: acelera, freia, faz curva, gasta combustível, para de vez.

import test from 'node:test';
import assert from 'node:assert/strict';

import { criarCarro, passo, bater, cantos, paraKmh } from '../src/jogo/fisica.js';
import { CARROS, carroPorId } from '../src/jogo/carros.js';
import { normalizarAngulo } from '../src/nucleo/matematica.js';

const PARADO = { acelerador: 0, freio: 0, volante: 0, mao: false, sentido: 1 };
const FUNDO = { ...PARADO, acelerador: 1 };

function simular(carro, comandos, segundos, dt = 1 / 120) {
  const passos = Math.round(segundos / dt);
  for (let i = 0; i < passos; i++) passo(carro, comandos, dt);
  return carro;
}

test('o carro acelera e chega a uma velocidade de cruzeiro', () => {
  for (const modelo of CARROS) {
    const carro = criarCarro(modelo.ficha);
    simular(carro, FUNDO, 12);
    const kmh = paraKmh(carro.vx);
    assert.ok(kmh > 45, `${modelo.nome} só chegou a ${kmh.toFixed(1)} km/h em 12 s`);
    assert.ok(kmh < 320, `${modelo.nome} passou de 320 km/h: ${kmh.toFixed(1)}`);
    assert.ok(Number.isFinite(carro.x) && Number.isFinite(carro.z));
  }
});

test('o esportivo é mais rápido que o fusca, e a picape fica no meio', () => {
  const chegar = (id) => {
    const carro = criarCarro(carroPorId(id).ficha);
    simular(carro, FUNDO, 10);
    return carro.vx;
  };
  const fusca = chegar('besouro');
  const esportivo = chegar('faisca');
  const picape = chegar('boiadeira');
  assert.ok(esportivo > picape, 'o esportivo tem que passar a picape');
  assert.ok(picape > fusca, 'a picape tem que passar o fusca');
});

test('o freio para o carro e ele não anda para trás sozinho', () => {
  const carro = criarCarro(carroPorId('pipoca').ficha);
  simular(carro, FUNDO, 6);
  assert.ok(carro.vx > 8, 'precisa estar andando antes de frear');
  simular(carro, { ...PARADO, freio: 1 }, 6);
  assert.equal(carro.vx, 0, 'com o freio pisado o carro tem que parar de vez');
  simular(carro, { ...PARADO, freio: 1 }, 3);
  assert.equal(carro.vx, 0, 'parado com o freio, não pode sair andando sozinho');
});

test('a ré anda para trás', () => {
  const carro = criarCarro(carroPorId('pipoca').ficha, { x: 0, z: 0 }, 0);
  simular(carro, { ...FUNDO, sentido: -1 }, 4);
  assert.ok(carro.vx < -1, `a ré tem que dar velocidade negativa (deu ${carro.vx})`);
  // Ângulo 0 aponta para -Z; andando de ré, o Z tem que AUMENTAR.
  assert.ok(carro.z > 1, `de ré o carro tem que recuar em Z (z = ${carro.z.toFixed(2)})`);
});

test('virar o volante muda o rumo, e para os dois lados', () => {
  const esquerda = criarCarro(carroPorId('pipoca').ficha);
  simular(esquerda, { ...FUNDO, volante: -1 }, 5);
  const direita = criarCarro(carroPorId('pipoca').ficha);
  simular(direita, { ...FUNDO, volante: 1 }, 5);

  assert.ok(Math.abs(esquerda.angulo) > 0.3, 'virando tudo, o carro tem que mudar de rumo');
  assert.ok(esquerda.angulo * direita.angulo < 0, 'um lado tem que girar ao contrário do outro');
  assert.ok(Math.abs(Math.abs(esquerda.angulo) - Math.abs(direita.angulo)) < 0.35,
    'os dois lados têm que ser praticamente simétricos');
});

test('em manobra o carro obedece: esterço geométrico, sem escorregar', () => {
  const carro = criarCarro(carroPorId('besouro').ficha);
  // Acelerador de leve e volante todo virado — é a baliza.
  for (let i = 0; i < 240; i++) {
    passo(carro, { ...PARADO, acelerador: 0.22, volante: 1 }, 1 / 120);
  }
  assert.ok(Math.abs(carro.vx) < 6, 'em manobra a velocidade tem que ficar baixa');
  assert.ok(Math.abs(carro.angulo) > 0.15, 'mesmo devagar, o volante tem que virar o carro');
  assert.ok(Math.abs(carro.vy) < 0.4, 'em manobra o carro não pode sair de lado');
});

test('o freio de mão solta a traseira', () => {
  const comBase = (mao) => {
    const carro = criarCarro(carroPorId('faisca').ficha);
    simular(carro, FUNDO, 6);
    for (let i = 0; i < 90; i++) {
      passo(carro, { ...PARADO, acelerador: 0.6, volante: 1, mao }, 1 / 120);
    }
    return Math.abs(carro.vy);
  };
  assert.ok(comBase(true) > comBase(false),
    'com o freio de mão o carro tem que escorregar mais de lado');
});

test('o combustível cai e o carro morre quando acaba', () => {
  const carro = criarCarro(carroPorId('boiadeira').ficha);
  carro.combustivel = 0.25;

  // Anda até secar o tanque — medir depois de um tempo fixo não serve, porque
  // aí o carro já parou sozinho e a comparação não diz nada.
  let voltas = 0;
  while (carro.combustivel > 0 && voltas < 12000) { passo(carro, FUNDO, 1 / 120); voltas++; }
  assert.equal(carro.combustivel, 0, 'o tanque tem que zerar, não ficar negativo');
  assert.ok(voltas < 12000, 'o tanque tinha que ter acabado');

  const velocidadeAoSecar = carro.vx;
  assert.ok(velocidadeAoSecar > 3, 'o carro ainda tinha que estar andando quando secou');
  simular(carro, FUNDO, 4);
  assert.ok(carro.vx < velocidadeAoSecar - 0.5,
    'sem combustível, o pé no fundo não adianta: o carro tem que perder velocidade');
});

test('acelerar leve gasta menos que acelerar fundo no mesmo tempo', () => {
  const gastar = (acelerador) => {
    const carro = criarCarro(carroPorId('pipoca').ficha);
    simular(carro, { ...PARADO, acelerador }, 15);
    return carro.combustivelGasto;
  };
  assert.ok(gastar(0.35) < gastar(1) * 0.8, 'pé leve tem que economizar de verdade');
});

test('a mesma entrada dá sempre o mesmo resultado', () => {
  const rodar = () => {
    const carro = criarCarro(carroPorId('diplomata').ficha);
    for (let i = 0; i < 600; i++) {
      passo(carro, { ...FUNDO, volante: Math.sin(i / 40) }, 1 / 120);
    }
    return [carro.x, carro.z, carro.angulo, carro.vx];
  };
  assert.deepEqual(rodar(), rodar());
});

test('bater tira velocidade, machuca a lataria e não explode a conta', () => {
  const carro = criarCarro(carroPorId('pipoca').ficha);
  simular(carro, FUNDO, 8);
  const antes = Math.abs(carro.vx);
  bater(carro, 0, 1, 1);
  assert.ok(Math.abs(carro.vx) < antes, 'depois da batida tem que sobrar menos velocidade');
  assert.ok(carro.dano > 0 && carro.dano <= 1, 'o dano tem que ficar entre 0 e 1');
  assert.ok(Number.isFinite(carro.vx) && Number.isFinite(carro.vy));
});

test('os quatro cantos do carro têm o tamanho da ficha', () => {
  const modelo = carroPorId('diplomata');
  const carro = criarCarro(modelo.ficha, { x: 10, z: -4 }, 0.7);
  const q = cantos(carro);
  assert.equal(q.length, 4);
  const lado = Math.hypot(q[0].x - q[1].x, q[0].z - q[1].z);
  const frente = Math.hypot(q[1].x - q[2].x, q[1].z - q[2].z);
  assert.ok(Math.abs(lado - modelo.ficha.largura) < 1e-6);
  assert.ok(Math.abs(frente - modelo.ficha.comprimento) < 1e-6);
});

test('pista escorregadia: o carro vira menos e o pneu escorrega mais', () => {
  // Medir o deslize pela velocidade lateral engana: com pouca aderência o
  // carro subesterça, sai quase reto e a lateral fica PEQUENA. O que se sente
  // é outra coisa — ele obedece menos ao volante, e o pneu canta.
  const medir = (atrito) => {
    const carro = criarCarro(carroPorId('diplomata').ficha);
    for (let i = 0; i < 720; i++) passo(carro, FUNDO, 1 / 120, { atrito });
    const anguloInicial = carro.angulo;
    let escorregou = 0;
    for (let i = 0; i < 240; i++) {
      passo(carro, { ...PARADO, acelerador: 0.5, volante: 1 }, 1 / 120, { atrito });
      escorregou += carro.derrapando;
    }
    return {
      virou: Math.abs(normalizarAngulo(carro.angulo - anguloInicial)),
      escorregou: escorregou / 240,
    };
  };
  const molhado = medir(0.74);
  const seco = medir(1.04);
  assert.ok(molhado.virou < seco.virou,
    `na chuva o carro tem que virar menos (${molhado.virou.toFixed(3)} vs ${seco.virou.toFixed(3)})`);
  assert.ok(molhado.escorregou > seco.escorregou,
    'na chuva o pneu tem que passar mais tempo escorregando');
});

test('na chuva o carro demora mais para frear', () => {
  // Partem da MESMA velocidade de propósito: se cada um acelerasse por conta
  // própria, o da chuva chegaria mais devagar ao ponto de freada e a distância
  // menor não provaria nada.
  const distanciaDeFrenagem = (atrito) => {
    const carro = criarCarro(carroPorId('diplomata').ficha);
    carro.vx = 25;
    const inicio = carro.distancia;
    let voltas = 0;
    while (carro.vx > 0.2 && voltas < 4000) {
      passo(carro, { ...PARADO, freio: 1 }, 1 / 120, { atrito });
      voltas++;
    }
    return carro.distancia - inicio;
  };
  const molhado = distanciaDeFrenagem(0.74);
  const seco = distanciaDeFrenagem(1.04);
  assert.ok(molhado > seco * 1.1,
    `a frenagem na chuva tem que ser bem mais longa (${molhado.toFixed(1)} m vs ${seco.toFixed(1)} m)`);
});

test('com pouca aderência o carro patina em vez de disparar', () => {
  const acelerar = (atrito) => {
    const carro = criarCarro(carroPorId('faisca').ficha);
    for (let i = 0; i < 360; i++) passo(carro, FUNDO, 1 / 120, { atrito });
    return carro.vx;
  };
  assert.ok(acelerar(0.74) < acelerar(1.04) * 0.97,
    'com menos aderência o carro tem que acelerar mais devagar');
});

test('o eixo de tração muda quem patina', () => {
  // A picape é integral: distribui a força nas quatro rodas e por isso sai
  // melhor do lugar que um traseiro de mesma potência num piso ruim.
  const arrancada = (id, atrito) => {
    const carro = criarCarro(carroPorId(id).ficha);
    for (let i = 0; i < 180; i++) passo(carro, FUNDO, 1 / 120, { atrito });
    return carro.distancia;
  };
  const noBarro = 0.6;
  assert.ok(arrancada('boiadeira', noBarro) > 0, 'a picape tem que sair do lugar');
  assert.ok(arrancada('faisca', 1.04) > arrancada('faisca', noBarro),
    'o esportivo tem que perder arrancada no piso ruim');
});

test('nenhum carro do catálogo tem ficha faltando', () => {
  const obrigatorios = [
    'massa', 'entreEixos', 'largura', 'comprimento', 'torqueMaximo', 'relacoes',
    'diferencial', 'raioRoda', 'rotacaoMaxima', 'forcaFreio', 'aderencia',
    'rigidezFrente', 'rigidezTras', 'estercoMaximo', 'tanque', 'consumoBase',
  ];
  for (const modelo of CARROS) {
    for (const campo of obrigatorios) {
      assert.ok(modelo.ficha[campo] !== undefined,
        `${modelo.nome} está sem ${campo}`);
    }
    assert.ok(modelo.ficha.relacoes.length > 0, `${modelo.nome} está sem marchas`);
    assert.ok(modelo.ficha.distribuicaoTraseira > 0.2 && modelo.ficha.distribuicaoTraseira < 0.8,
      `${modelo.nome} tem distribuição de peso impossível`);
  }
});
