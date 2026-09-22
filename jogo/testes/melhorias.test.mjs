// Melhoria é a parte que mexe com dinheiro e com a física ao mesmo tempo, e
// por isso erra caro: ou vaza para carro que não é seu, ou cobra sem entregar,
// ou entrega sem cobrar. Aqui nada disso passa.

import test from 'node:test';
import assert from 'node:assert/strict';

import {
  MELHORIAS, NIVEL_MAXIMO, niveisDe, precoDoProximo, fichaMelhorada,
  comprarMelhoria, classeDe, totalDeNiveis,
} from '../src/jogo/melhorias.js';
import { CARROS, carroPorId } from '../src/jogo/carros.js';
import { padrao } from '../src/jogo/progresso.js';
import { criarCarro, passo, paraKmh } from '../src/jogo/fisica.js';

const COMANDOS = { acelerador: 1, freio: 0, volante: 0, mao: false, sentido: 1 };

function simular(carro, comandos, segundos) {
  for (let i = 0; i < Math.round(segundos * 120); i++) passo(carro, comandos, 1 / 120);
  return carro;
}

test('carro sem melhoria nenhuma roda com a ficha de fábrica', () => {
  const progresso = padrao();
  for (const modelo of CARROS) {
    const f = fichaMelhorada(modelo, niveisDe(progresso, modelo.id));
    for (const chave of Object.keys(modelo.ficha)) {
      if (typeof modelo.ficha[chave] !== 'number') continue;
      assert.ok(Math.abs(f[chave] - modelo.ficha[chave]) < 1e-9,
        `${modelo.id}: '${chave}' mudou sem melhoria nenhuma`);
    }
  }
});

/**
 * O erro que mais dói: `fichaMelhorada` devolvendo o mesmo objeto do catálogo.
 * O catálogo é usado também pelo trânsito, pelos carros estacionados e pela
 * prévia da garagem — turbinar o seu carro turbinaria os dos outros junto, e
 * para sempre, porque o objeto é compartilhado.
 */
test('melhorar o seu carro não mexe na ficha do catálogo', () => {
  const progresso = padrao();
  progresso.melhorias = { besouro: { motor: 5, freio: 5, pneu: 5, cambio: 5 } };
  const modelo = carroPorId('besouro');
  const torqueAntes = modelo.ficha.torqueMaximo;
  const aderenciaAntes = modelo.ficha.aderencia;

  const f = fichaMelhorada(modelo, niveisDe(progresso, 'besouro'));
  assert.ok(f.torqueMaximo > torqueAntes, 'motor no 5 não deu torque nenhum');
  assert.equal(modelo.ficha.torqueMaximo, torqueAntes, 'a ficha de fábrica foi alterada');
  assert.equal(modelo.ficha.aderencia, aderenciaAntes, 'a aderência de fábrica foi alterada');
  assert.notEqual(f, modelo.ficha, 'devolveu o próprio objeto do catálogo');
});

test('cada melhoria mexe no que promete, e só nisso', () => {
  const modelo = carroPorId('diplomata');
  const nada = fichaMelhorada(modelo, {});

  const motor = fichaMelhorada(modelo, { motor: 5 });
  assert.ok(motor.torqueMaximo > nada.torqueMaximo * 1.4, 'motor no 5 rendeu pouco');
  assert.equal(motor.forcaFreio, nada.forcaFreio, 'motor mexeu no freio');
  assert.equal(motor.aderencia, nada.aderencia, 'motor mexeu no pneu');

  const freio = fichaMelhorada(modelo, { freio: 5 });
  assert.ok(freio.forcaFreio > nada.forcaFreio * 1.5, 'freio no 5 freou pouco');
  assert.equal(freio.torqueMaximo, nada.torqueMaximo, 'freio mexeu no motor');

  const pneu = fichaMelhorada(modelo, { pneu: 5 });
  assert.ok(pneu.aderencia > nada.aderencia * 1.2, 'pneu no 5 agarrou pouco');
  assert.equal(pneu.torqueMaximo, nada.torqueMaximo, 'pneu mexeu no motor');

  const cambio = fichaMelhorada(modelo, { cambio: 5 });
  assert.ok(cambio.velocidadeVolante > nada.velocidadeVolante, 'câmbio não aliviou a direção');
  assert.ok(cambio.rendimento <= 0.96, 'rendimento passou do fisicamente decente');
});

test('a melhoria se sente dirigindo: mais motor, mais velocidade', () => {
  const modelo = carroPorId('pipoca');
  const zerado = criarCarro(fichaMelhorada(modelo, {}));
  const turbinado = criarCarro(fichaMelhorada(modelo, { motor: 5 }));
  simular(zerado, COMANDOS, 8);
  simular(turbinado, COMANDOS, 8);
  assert.ok(paraKmh(turbinado.vx) > paraKmh(zerado.vx) + 6,
    `motor no 5 só deu ${(paraKmh(turbinado.vx) - paraKmh(zerado.vx)).toFixed(1)} km/h`);
});

test('a melhoria se sente freando: mais freio, para mais curto', () => {
  const modelo = carroPorId('diplomata');
  const parar = (niveis) => {
    const carro = criarCarro(fichaMelhorada(modelo, niveis));
    carro.vx = 30;
    const partiu = carro.distancia;
    for (let i = 0; i < 1200 && carro.vx > 0.2; i++) {
      passo(carro, { ...COMANDOS, acelerador: 0, freio: 1 }, 1 / 120);
    }
    return carro.distancia - partiu;
  };
  const semNada = parar({});
  const comFreio = parar({ freio: 5 });
  assert.ok(comFreio < semNada, `freio no 5 parou em ${comFreio.toFixed(1)} m contra ${semNada.toFixed(1)} m`);
});

test('o preço sobe com o nível, e o último dói mais que o primeiro', () => {
  for (const m of MELHORIAS) {
    let anterior = 0;
    for (let n = 0; n < NIVEL_MAXIMO; n++) {
      const preco = precoDoProximo(m.id, n);
      assert.ok(preco > anterior, `${m.id}: nível ${n + 1} não é mais caro que o anterior`);
      anterior = preco;
    }
    assert.equal(precoDoProximo(m.id, NIVEL_MAXIMO), null,
      `${m.id}: ainda cobra depois do máximo`);
  }
});

test('comprar desconta, sobe um nível, e para no máximo', () => {
  const progresso = padrao();
  progresso.dinheiro = 100000;
  for (let n = 0; n < NIVEL_MAXIMO; n++) {
    const antes = progresso.dinheiro;
    const preco = precoDoProximo('motor', n);
    const r = comprarMelhoria(progresso, 'besouro', 'motor');
    assert.equal(r.ok, true, `compra ${n + 1} recusada`);
    assert.equal(r.nivel, n + 1);
    assert.equal(antes - progresso.dinheiro, preco, 'descontou valor diferente do cobrado');
  }
  const depois = comprarMelhoria(progresso, 'besouro', 'motor');
  assert.equal(depois.ok, false, 'vendeu um sexto nível');
  assert.equal(niveisDe(progresso, 'besouro').motor, NIVEL_MAXIMO);
});

test('sem dinheiro não compra, e não cobra', () => {
  const progresso = padrao();
  progresso.dinheiro = 10;
  const r = comprarMelhoria(progresso, 'besouro', 'pneu');
  assert.equal(r.ok, false);
  assert.equal(progresso.dinheiro, 10, 'cobrou mesmo sem vender');
  assert.equal(niveisDe(progresso, 'besouro').pneu, 0, 'entregou sem cobrar');
});

test('melhorar um carro não melhora o outro', () => {
  const progresso = padrao();
  progresso.dinheiro = 100000;
  comprarMelhoria(progresso, 'besouro', 'motor');
  comprarMelhoria(progresso, 'besouro', 'motor');
  assert.equal(niveisDe(progresso, 'besouro').motor, 2);
  assert.equal(niveisDe(progresso, 'faisca').motor, 0, 'a melhoria vazou para outro carro');
});

test('nível guardado fora da faixa é domado na leitura', () => {
  const progresso = padrao();
  progresso.melhorias = { besouro: { motor: 99, freio: -4, pneu: 2.7 } };
  const n = niveisDe(progresso, 'besouro');
  assert.equal(n.motor, NIVEL_MAXIMO, 'nível salvo acima do máximo passou');
  assert.equal(n.freio, 0, 'nível negativo passou');
  assert.equal(n.pneu, 3, 'nível quebrado não foi arredondado');
  assert.equal(n.cambio, 0, 'melhoria que nunca foi comprada não veio zerada');
});

test('a classe sobe com o carro e com o que foi posto nele', () => {
  const popular = carroPorId('pipoca');
  const esportivo = carroPorId('faisca');
  const zero = { motor: 0, freio: 0, pneu: 0, cambio: 0 };
  const cheio = { motor: 5, freio: 5, pneu: 5, cambio: 5 };

  assert.equal(totalDeNiveis(cheio), MELHORIAS.length * NIVEL_MAXIMO);
  const ordem = ['D', 'C', 'B', 'A', 'S'];
  const sobe = (a, b) => ordem.indexOf(a) < ordem.indexOf(b);

  assert.ok(sobe(classeDe(popular, zero), classeDe(esportivo, zero)),
    'o esportivo de fábrica não é de classe melhor que o popular');
  assert.ok(sobe(classeDe(popular, zero), classeDe(popular, cheio)),
    'encher o popular de melhoria não mudou a classe');
  assert.equal(classeDe(esportivo, cheio), 'S', 'esportivo no talo não chegou à classe S');
});
