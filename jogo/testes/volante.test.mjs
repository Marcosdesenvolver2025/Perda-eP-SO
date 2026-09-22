// O volante é a assinatura do jogo: a promessa é que cada carro tenha o seu, e
// promessa de jogo se quebra em silêncio — ninguém abre um relatório de erro
// porque dois carros têm o mesmo volante. Então é teste.
//
// Aqui só se olha a FICHA do volante (formato, cor, raios, emblema); o desenho
// em si precisa de canvas e é conferido no navegador.

import test from 'node:test';
import assert from 'node:assert/strict';

import { CARROS, carroPorId } from '../src/jogo/carros.js';

const VOLANTES = CARROS.map((c) => ({ carro: c.nome, id: c.id, ...c.volante }));

test('todo carro tem volante, com estilo e apelido próprios', () => {
  for (const v of VOLANTES) {
    assert.ok(v.estilo, `${v.carro}: volante sem estilo`);
    assert.ok(v.apelido && v.apelido.length > 6, `${v.carro}: volante sem apelido`);
    assert.ok(v.raio > 0.5 && v.raio <= 1.3, `${v.carro}: raio estranho (${v.raio})`);
    assert.ok(v.espessura > 0.02 && v.espessura < 0.2, `${v.carro}: aro de espessura estranha`);
    assert.ok(v.raios >= 2 && v.raios <= 6, `${v.carro}: ${v.raios} raios?`);
    for (const campo of ['corAro', 'corRaio', 'corCubo', 'corDetalhe']) {
      assert.equal(typeof v[campo], 'number', `${v.carro}: ${campo} não é cor`);
      assert.ok(v[campo] >= 0 && v[campo] <= 0xffffff, `${v.carro}: ${campo} fora da faixa`);
    }
  }
});

test('nenhum volante é igual a outro', () => {
  const assinaturas = new Map();
  for (const v of VOLANTES) {
    const assinatura = [
      v.estilo, v.raios, v.emblema, v.corAro, v.corRaio, v.corCubo,
      !!v.aroBaixoReto, !!v.furos, !!v.costura, !!v.madeira, !!v.tubular,
    ].join('|');
    const jaTem = assinaturas.get(assinatura);
    assert.ok(!jaTem, `${v.carro} tem o mesmo volante de ${jaTem}`);
    assinaturas.set(assinatura, v.carro);
  }
  assert.equal(assinaturas.size, CARROS.length);
});

test('cada estilo de volante aparece uma vez só', () => {
  const estilos = VOLANTES.map((v) => v.estilo);
  assert.equal(new Set(estilos).size, estilos.length,
    `estilo repetido entre: ${estilos.join(', ')}`);
});

test('cada emblema de cubo é de um carro só', () => {
  const emblemas = VOLANTES.map((v) => v.emblema);
  assert.equal(new Set(emblemas).size, emblemas.length,
    `emblema repetido entre: ${emblemas.join(', ')}`);
});

test('os apelidos descrevem coisas diferentes', () => {
  const apelidos = VOLANTES.map((v) => v.apelido.toLowerCase());
  assert.equal(new Set(apelidos).size, apelidos.length, 'apelido de volante repetido');
});

test('o volante do elétrico não é redondo, e é o único assim', () => {
  const eletrico = carroPorId('silencio');
  assert.equal(eletrico.volante.estilo, 'yoke');
  const outros = VOLANTES.filter((v) => v.id !== 'silencio');
  assert.ok(outros.every((v) => v.estilo !== 'yoke'),
    'o manche tinha que ser exclusividade do elétrico');
});

test('o primeiro carro é de graça e os outros custam mais a cada classe', () => {
  assert.equal(CARROS[0].preco, 0, 'o carro inicial tem que ser de graça');
  for (const carro of CARROS.slice(1)) {
    assert.ok(carro.preco > 0, `${carro.nome} está de graça sem ser o inicial`);
  }
  const precos = CARROS.map((c) => c.preco);
  assert.deepEqual(precos, [...precos].sort((a, b) => a - b),
    'o catálogo tem que estar em ordem de preço');
});

test('os carros são realmente diferentes uns dos outros', () => {
  const nomes = CARROS.map((c) => c.nome);
  assert.equal(new Set(nomes).size, nomes.length, 'nome de carro repetido');
  const ids = CARROS.map((c) => c.id);
  assert.equal(new Set(ids).size, ids.length, 'id de carro repetido');
  const classes = CARROS.map((c) => c.classe);
  assert.equal(new Set(classes).size, classes.length, 'duas classes iguais no catálogo');

  // E diferentes onde importa: massa, potência e aderência bem espalhadas.
  const massas = CARROS.map((c) => c.ficha.massa);
  assert.ok(Math.max(...massas) / Math.min(...massas) > 2,
    'os carros precisam ter pesos bem diferentes');
  const tracoes = new Set(CARROS.map((c) => c.ficha.tracao));
  assert.ok(tracoes.size >= 3, 'o catálogo tem que ter tração dianteira, traseira e integral');
});

test('carroPorId devolve o carro certo, e nunca nada', () => {
  for (const carro of CARROS) {
    assert.equal(carroPorId(carro.id), carro);
  }
  assert.equal(carroPorId('nao-existe'), CARROS[0], 'id desconhecido tem que cair no inicial');
  assert.equal(carroPorId(undefined), CARROS[0]);
});
