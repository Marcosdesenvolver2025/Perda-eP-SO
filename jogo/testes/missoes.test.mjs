// Uma missão que nasce impossível não dá para descobrir jogando: você só sente
// que "aquela fase era injusta". Então a suíte monta centenas delas e confere o
// que precisa valer sempre — dá para começar, dá para ganhar, e o objetivo
// existe de verdade.

import test from 'node:test';
import assert from 'node:assert/strict';

import {
  TIPOS, FICHA_DOS_TIPOS, sortearMissao, gerarCarreira,
  montarMissao, atualizarMissao, avaliarMissao, alvoAtual,
} from '../src/jogo/missoes.js';
import { pisoEm, LISTA_CENARIOS } from '../src/jogo/mundo.js';
import { CLIMAS } from '../src/jogo/clima.js';
import { criarCarro } from '../src/jogo/fisica.js';
import { caixaDoCarro, caixaDoColisor, sobreposicao, dentroDe } from '../src/jogo/colisao.js';
import { CARROS, carroPorId } from '../src/jogo/carros.js';
import { distanciaPlana } from '../src/nucleo/matematica.js';

const SEMENTES = Array.from({ length: 12 }, (_, i) => 7000 + i * 613);

function montarTodas(visitar, modelo = carroPorId('diplomata')) {
  for (const semente of SEMENTES) {
    for (const tipo of TIPOS) {
      for (const cenario of LISTA_CENARIOS) {
        const descritor = sortearMissao(semente, 4, { tipo, cenario });
        visitar(montarMissao(descritor, modelo), descritor, modelo);
      }
    }
  }
}

test('toda missão monta, tem objetivo e cabe no relógio', () => {
  montarTodas((missao, d) => {
    const onde = `${d.tipo}/${d.cenario}/${d.semente}`;
    assert.ok(missao.objetivos.length >= 1, `${onde}: missão sem objetivo`);
    assert.ok(missao.tempoLimite >= 20, `${onde}: só ${missao.tempoLimite}s de prazo`);
    assert.ok(missao.tempoLimite <= 200, `${onde}: prazo absurdo de ${missao.tempoLimite}s`);
    assert.ok(Number.isFinite(missao.mundo.inicio.x), `${onde}: largada inválida`);
  });
});

test('em toda missão o carro larga no asfalto', () => {
  montarTodas((missao, d) => {
    const inicio = missao.mundo.inicio;
    const piso = pisoEm(missao.mundo, inicio.x, inicio.z);
    assert.ok(piso === 'asfalto' || piso === 'terra',
      `${d.tipo}/${d.cenario}/${d.semente}: largada em '${piso}'`);
  });
});

test('em toda missão o carro larga sem estar encostado em nada', () => {
  for (const modelo of [carroPorId('pipoca'), carroPorId('boiadeira')]) {
    montarTodas((missao, d) => {
      const inicio = missao.mundo.inicio;
      const carro = criarCarro(modelo.ficha, { x: inicio.x, z: inicio.z }, inicio.angulo);
      const caixa = caixaDoCarro(carro);
      for (const colisor of missao.mundo.colisores) {
        if (colisor.derrubavel) continue;
        assert.ok(!sobreposicao(caixa, caixaDoColisor(colisor)),
          `${d.tipo}/${d.cenario}/${d.semente}: ${modelo.nome} largou encostado em algo`);
      }
    }, modelo);
  }
});

test('a vaga da baliza cabe o carro, e o carro começa perto dela', () => {
  for (const modelo of CARROS) {
    for (const semente of SEMENTES) {
      const d = sortearMissao(semente, 5, { tipo: 'baliza', cenario: 'cidade' });
      const missao = montarMissao(d, modelo);
      const vaga = missao.vagaAlvo;
      assert.ok(vaga, 'baliza sem vaga');
      assert.ok(vaga.comprimento > modelo.ficha.comprimento + 0.9,
        `${modelo.nome}: a vaga (${vaga.comprimento.toFixed(2)} m) não cabe o carro `
        + `(${modelo.ficha.comprimento} m)`);
      assert.ok(vaga.largura > modelo.ficha.largura + 0.4,
        `${modelo.nome}: a vaga é estreita demais`);
      const distancia = distanciaPlana(
        missao.mundo.inicio.x, missao.mundo.inicio.z, vaga.x, vaga.z);
      assert.ok(distancia < 30, `${modelo.nome}: largada a ${distancia.toFixed(1)} m da vaga`);
    }
  }
});

test('a vaga marcada cabe o carro e está no asfalto', () => {
  for (const modelo of CARROS) {
    for (const semente of SEMENTES) {
      const d = sortearMissao(semente, 5, { tipo: 'vaga', cenario: 'cidade' });
      const missao = montarMissao(d, modelo);
      const vaga = missao.vagaAlvo;
      assert.ok(vaga, 'missão de vaga sem vaga alvo');
      assert.ok(vaga.comprimento >= modelo.ficha.comprimento + 0.6,
        `${modelo.nome}: vaga curta demais`);
      assert.equal(pisoEm(missao.mundo, vaga.x, vaga.z), 'asfalto', 'vaga fora do asfalto');
    }
  }
});

test('a vaga alvo nunca tem um carro parado dentro', () => {
  for (const semente of SEMENTES) {
    for (const tipo of ['baliza', 'vaga']) {
      const d = sortearMissao(semente, 5, { tipo, cenario: 'cidade' });
      const missao = montarMissao(d, carroPorId('pipoca'));
      const vaga = missao.vagaAlvo;
      const dentro = missao.mundo.colisores.filter((c) => c.carroParado
        && Math.abs(c.x - vaga.x) < vaga.comprimento / 2
        && Math.abs(c.z - vaga.z) < vaga.comprimento / 2
        && distanciaPlana(c.x, c.z, vaga.x, vaga.z) < 1.5);
      assert.equal(dentro.length, 0, `${tipo}/${semente}: a vaga alvo está ocupada`);
    }
  }
});

test('parar dentro da vaga alvo conclui a missão de baliza', () => {
  const modelo = carroPorId('pipoca');
  const d = sortearMissao(4321, 3, { tipo: 'baliza', cenario: 'cidade' });
  const missao = montarMissao(d, modelo);
  const vaga = missao.vagaAlvo;

  // Teletransporta o carro para dentro da vaga, parado e alinhado.
  const carro = criarCarro(modelo.ficha, { x: vaga.x, z: vaga.z }, vaga.angulo);
  assert.ok(dentroDe(carro, vaga), 'o carro alinhado tinha que caber na vaga');

  const partida = {
    carro, missao, tempo: 1,
    estatisticas: { batidas: 0, forcaMaxima: 0 },
  };
  let evento = null;
  for (let i = 0; i < 200 && !evento; i++) evento = atualizarMissao(partida, 1 / 60);
  assert.ok(evento, 'segurar parado na vaga tinha que terminar a missão');
  assert.equal(evento.tipo, 'vitoria');

  const nota = avaliarMissao(partida);
  assert.ok(nota.sucesso);
  assert.ok(nota.estrelas >= 1 && nota.estrelas <= 3);
  assert.ok(nota.premio > 0, 'missão cumprida tem que pagar');
});

test('passar pelos pontos na ordem conclui a entrega', () => {
  const modelo = carroPorId('pipoca');
  const d = sortearMissao(555, 3, { tipo: 'entrega', cenario: 'cidade' });
  const missao = montarMissao(d, modelo);
  const carro = criarCarro(modelo.ficha);
  const partida = { carro, missao, tempo: 1, estatisticas: { batidas: 0, forcaMaxima: 0 } };

  let ultimo = null;
  for (const ponto of missao.pontos) {
    carro.x = ponto.x;
    carro.z = ponto.z;
    ultimo = atualizarMissao(partida, 1 / 60);
    assert.ok(ultimo, `chegar no ponto tinha que gerar evento`);
  }
  assert.equal(ultimo.tipo, 'vitoria');
  assert.equal(missao.indice, missao.pontos.length);
});

test('o tempo estourado derruba a missão', () => {
  const modelo = carroPorId('pipoca');
  const d = sortearMissao(88, 3, { tipo: 'entrega', cenario: 'cidade' });
  const missao = montarMissao(d, modelo);
  const partida = {
    carro: criarCarro(modelo.ficha), missao,
    tempo: missao.tempoLimite + 1,
    estatisticas: { batidas: 0, forcaMaxima: 0 },
  };
  const evento = atualizarMissao(partida, 1 / 60);
  assert.equal(evento.tipo, 'derrota');
  const nota = avaliarMissao(partida);
  assert.equal(nota.estrelas, 0);
  assert.equal(nota.premio, 0);
});

test('o slalom cabe na rua e tem cones dos dois lados de cada portão', () => {
  for (const semente of SEMENTES) {
    const d = sortearMissao(semente, 5, { tipo: 'slalom', cenario: 'cidade' });
    const missao = montarMissao(d, carroPorId('pipoca'));
    assert.ok(missao.objetivos.length >= 3, 'slalom curto demais');
    assert.equal(missao.cones.length, missao.objetivos.length * 2,
      'cada portão precisa de dois cones');
    for (const ponto of missao.pontos) {
      assert.equal(pisoEm(missao.mundo, ponto.x, ponto.z), 'asfalto',
        `semente ${semente}: portão fora da rua`);
    }
  }
});

test('a escolta larga com o líder logo à frente, na mesma rua', () => {
  for (const semente of SEMENTES) {
    const d = sortearMissao(semente, 4, { tipo: 'escolta', cenario: 'cidade' });
    const missao = montarMissao(d, carroPorId('pipoca'));
    const alvo = missao.escolta;
    assert.ok(alvo, 'escolta sem carro para escoltar');
    const distancia = distanciaPlana(
      missao.mundo.inicio.x, missao.mundo.inicio.z, alvo.x, alvo.z);
    assert.ok(distancia > 5 && distancia < 20,
      `semente ${semente}: o líder largou a ${distancia.toFixed(1)} m`);
    assert.equal(pisoEm(missao.mundo, alvo.x, alvo.z), 'asfalto',
      `semente ${semente}: o líder largou fora da rua`);
  }
});

test('a missão de economia dá combustível suficiente para o percurso', () => {
  for (const modelo of CARROS) {
    for (const semente of SEMENTES.slice(0, 6)) {
      const d = sortearMissao(semente, 4, { tipo: 'economia', cenario: 'cidade' });
      const missao = montarMissao(d, modelo);
      assert.ok(missao.combustivel > 0, `${modelo.nome}: sem combustível nenhum`);
      // O gasto num percurso a pé leve tem que caber no que foi dado.
      const percurso = missao.pontos.reduce((total, ponto, i) => {
        const antes = i === 0 ? missao.mundo.inicio : missao.pontos[i - 1];
        return total + distanciaPlana(antes.x, antes.z, ponto.x, ponto.z);
      }, 0);
      const gastoEstimado = (percurso / 9) * modelo.ficha.consumoBase * 0.62;
      assert.ok(missao.combustivel >= gastoEstimado,
        `${modelo.nome}/${semente}: deram menos combustível do que o mínimo do percurso`);
    }
  }
});

test('a bússola sempre tem para onde apontar', () => {
  montarTodas((missao, d) => {
    const alvo = alvoAtual(missao);
    assert.ok(alvo, `${d.tipo}/${d.cenario}: sem alvo para a bússola`);
    assert.ok(Number.isFinite(alvo.x) && Number.isFinite(alvo.z),
      `${d.tipo}: alvo com coordenada inválida`);
  });
});

test('a carreira sobe de nível e não repete o tipo em seguida', () => {
  const carreira = gerarCarreira(20250922, 40);
  assert.equal(carreira.length, 40);
  for (let i = 1; i < carreira.length; i++) {
    assert.notEqual(carreira[i].tipo, carreira[i - 1].tipo,
      `serviços ${i} e ${i + 1} são do mesmo tipo`);
  }
  assert.ok(carreira[39].dificuldade > carreira[0].dificuldade, 'a carreira tem que apertar');
  assert.ok(carreira[39].premio > carreira[0].premio, 'e tem que pagar mais no fim');
});

test('no começo da carreira não vem noite nem chuva', () => {
  for (let tentativa = 0; tentativa < 200; tentativa++) {
    const d = sortearMissao(tentativa * 31 + 7, 1);
    assert.ok(!CLIMAS[d.clima].farois,
      `nível 1 sorteou '${d.clima}', que já acende farol`);
  }
});

test('todo tipo de missão tem nome, resumo e dica escritos', () => {
  for (const tipo of TIPOS) {
    const ficha = FICHA_DOS_TIPOS[tipo];
    assert.ok(ficha, `${tipo} não tem ficha`);
    assert.ok(ficha.nome && ficha.nome.length >= 4, `${tipo}: sem nome`);
    for (const campo of ['resumo', 'dica']) {
      assert.ok(ficha[campo] && ficha[campo].length > 24,
        `${tipo}: ${campo} vazio ou curto demais para explicar a tarefa`);
    }
  }
});
