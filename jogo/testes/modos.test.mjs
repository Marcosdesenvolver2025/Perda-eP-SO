// Um modo avulso quebra de um jeito que só se descobre jogando trinta vezes:
// a volta que conta duas vezes, a vaga que não cabe no carro que a pessoa
// comprou, o combo que nunca entra no banco, o relógio que não dá tempo nem de
// chegar ao primeiro objetivo. Nada disso aparece numa partida só.
//
// Então a suíte joga por nós: move o carro no papel — mesma conta que a física
// faz — e confere o placar depois de cada passo.

import test from 'node:test';
import assert from 'node:assert/strict';

import {
  LISTA_MODOS, FICHA_DOS_MODOS, descritorDeModo, montarModo, andarModo,
  avaliarModo, alvoDoModo,
} from '../src/jogo/modos.js';
import { pisoEm } from '../src/jogo/mundo.js';
import { criarCarro } from '../src/jogo/fisica.js';
import { caixaDoCarro, caixaDoColisor, sobreposicao, dentroDe } from '../src/jogo/colisao.js';
import { carroPorId, CARROS } from '../src/jogo/carros.js';
import { distanciaPlana, normalizarAngulo } from '../src/nucleo/matematica.js';

const SEMENTES = [4211, 9007, 15733, 28801, 51199, 77003];
const PASSO = 1 / 60;

function montar(modo, semente, modelo = carroPorId('diplomata')) {
  const descritor = descritorDeModo(modo, semente);
  return { descritor, missao: montarModo(descritor, modelo), modelo };
}

/** Uma partida de mentira, com só o que o `andarModo` lê. */
function partidaDe(missao, modelo) {
  const inicio = missao.mundo.inicio;
  const carro = criarCarro(modelo.ficha, { x: inicio.x, z: inicio.z }, inicio.angulo);
  return {
    missao, carro, tempo: 0, piso: 'asfalto',
    estatisticas: { batidas: 0, forcaMaxima: 0, tempoNoAr: 0 },
  };
}

/** Anda `segundos` sem mexer no carro — serve para ver o relógio correr. */
function correr(partida, segundos, aCadaPasso = null) {
  const eventos = [];
  const passos = Math.round(segundos / PASSO);
  for (let i = 0; i < passos; i++) {
    partida.tempo += PASSO;
    if (aCadaPasso) aCadaPasso(partida, i);
    const e = andarModo(partida, PASSO);
    if (e) eventos.push(e);
    partida.carro.forcaImpacto = 0;
    if (partida.missao.concluida || partida.missao.falhou) break;
  }
  return eventos;
}

// ---------------------------------------------------------------------------
// O QUE VALE PARA OS TRÊS
// ---------------------------------------------------------------------------

test('todo modo monta, com relógio e largada válidos', () => {
  for (const modo of LISTA_MODOS) {
    for (const semente of SEMENTES) {
      const { missao } = montar(modo, semente);
      const onde = `${modo}/${semente}`;
      assert.ok(missao.mundo, `${onde}: sem mundo`);
      assert.ok(missao.tempoLimite >= 30 && missao.tempoLimite <= 150,
        `${onde}: relógio de ${missao.tempoLimite}s`);
      assert.equal(missao.pontuacao, 0, `${onde}: já começa com ponto`);
      assert.ok(Number.isFinite(missao.mundo.inicio.x)
        && Number.isFinite(missao.mundo.inicio.angulo), `${onde}: largada inválida`);
    }
  }
});

test('em todo modo o carro larga no asfalto e sem encostar em nada', () => {
  for (const modelo of [carroPorId('pipoca'), carroPorId('boiadeira')]) {
    for (const modo of LISTA_MODOS) {
      for (const semente of SEMENTES) {
        const { missao } = montar(modo, semente, modelo);
        const onde = `${modo}/${semente}/${modelo.id}`;
        const inicio = missao.mundo.inicio;
        assert.equal(pisoEm(missao.mundo, inicio.x, inicio.z), 'asfalto',
          `${onde}: largada fora do asfalto`);

        const carro = criarCarro(modelo.ficha, { x: inicio.x, z: inicio.z }, inicio.angulo);
        const caixa = caixaDoCarro(carro);
        for (const colisor of missao.mundo.colisores) {
          assert.ok(!sobreposicao(caixa, caixaDoColisor(colisor)),
            `${onde}: larga dentro de um obstáculo em ${colisor.x.toFixed(1)},${colisor.z.toFixed(1)}`);
        }
      }
    }
  }
});

test('modo parado até o fim termina em derrota, e sem ponto', () => {
  for (const modo of LISTA_MODOS) {
    const { missao, modelo } = montar(modo, SEMENTES[0]);
    const partida = partidaDe(missao, modelo);
    const eventos = correr(partida, missao.tempoLimite + 2);
    const ultimo = eventos[eventos.length - 1];
    assert.ok(ultimo && ultimo.tipo === 'derrota', `${modo}: ficar parado não encerrou`);
    assert.equal(missao.pontuacao, 0, `${modo}: pontuou parado`);
    assert.ok(missao.falhou, `${modo}: não marcou fracasso`);
  }
});

test('o relógio de cada modo nunca é maior que o do catálogo', () => {
  for (const modo of LISTA_MODOS) {
    const { missao } = montar(modo, SEMENTES[1]);
    assert.equal(missao.tempoLimite, FICHA_DOS_MODOS[modo].tempoInicial,
      `${modo}: relógio inicial fora do catálogo`);
  }
});

// ---------------------------------------------------------------------------
// ESTACIONAMENTO
// ---------------------------------------------------------------------------

test('toda vaga da fila cabe no carro escolhido, em qualquer carro', () => {
  for (const modelo of CARROS) {
    for (const semente of SEMENTES) {
      const { missao } = montar('estacionamento', semente, modelo);
      assert.ok(missao.fila && missao.fila.length >= 3,
        `${modelo.id}/${semente}: fila de ${missao.fila ? missao.fila.length : 0} vagas`);
      for (const vaga of missao.fila) {
        assert.ok(vaga.largura >= modelo.ficha.largura + 0.6,
          `${modelo.id}: vaga de ${vaga.largura.toFixed(2)}m para carro de ${modelo.ficha.largura}m`);
        assert.ok(vaga.comprimento >= modelo.ficha.comprimento + 0.8,
          `${modelo.id}: vaga curta demais`);
        assert.ok(!vaga.ocupada, `${modelo.id}: vaga da fila está ocupada por outro carro`);
      }
    }
  }
});

test('a primeira vaga dá para alcançar dentro do relógio inicial', () => {
  for (const semente of SEMENTES) {
    const { missao } = montar('estacionamento', semente);
    const inicio = missao.mundo.inicio;
    const vaga = missao.vagaAlvo;
    const distancia = distanciaPlana(inicio.x, inicio.z, vaga.x, vaga.z);
    // 4 m/s é um trote de pátio; 12 s é a manobra. Se nem assim dá, a partida
    // já nasce perdida — e perder sem ter errado é o pior que um modo faz.
    const precisa = distancia / 4 + 12;
    assert.ok(precisa < missao.tempoLimite,
      `${semente}: ${distancia.toFixed(0)}m até a primeira vaga, só ${missao.tempoLimite}s`);
  }
});

test('encaixar dá ponto, devolve tempo e aponta a vaga seguinte', () => {
  const { missao, modelo } = montar('estacionamento', SEMENTES[2]);
  const partida = partidaDe(missao, modelo);
  const primeira = missao.vagaAlvo;
  const relogioAntes = missao.tempoLimite;

  // Põe o carro dentro da vaga, alinhado e parado — é o que a pessoa faz.
  partida.carro.x = primeira.x;
  partida.carro.z = primeira.z;
  partida.carro.angulo = primeira.angulo;
  assert.ok(dentroDe(partida.carro, primeira), 'a vaga alvo não comporta o carro');

  const eventos = correr(partida, 1.2);
  assert.equal(missao.contador, 1, 'a vaga não foi contada');
  assert.ok(missao.pontuacao > 500, `pontuou só ${missao.pontuacao}`);
  assert.ok(missao.tempoLimite > relogioAntes, 'encaixar não devolveu tempo');
  assert.ok(eventos.some((e) => e.tipo === 'marco'), 'nenhum aviso de ponto batido');
  assert.notEqual(missao.vagaAlvo, primeira, 'continuou apontando a mesma vaga');
  assert.equal(missao.vagaAlvo.alvo, true, 'a vaga nova não foi marcada');
  assert.equal(primeira.alvo, false, 'a vaga velha continuou marcada');
  assert.equal(alvoDoModo(missao), missao.vagaAlvo, 'a bússola aponta para o lugar errado');
});

test('a mesma vaga não conta duas vezes', () => {
  const { missao, modelo } = montar('estacionamento', SEMENTES[3]);
  const partida = partidaDe(missao, modelo);
  const primeira = missao.vagaAlvo;
  partida.carro.x = primeira.x;
  partida.carro.z = primeira.z;
  partida.carro.angulo = primeira.angulo;
  correr(partida, 6);
  assert.equal(missao.contador, 1, `contou ${missao.contador} vezes a mesma vaga`);
});

test('parado em cima da vaga sem estar dentro não conta', () => {
  const { missao, modelo } = montar('estacionamento', SEMENTES[4]);
  const partida = partidaDe(missao, modelo);
  const vaga = missao.vagaAlvo;
  // Atravessado na vaga: dentro do retângulo, mas de lado.
  partida.carro.x = vaga.x;
  partida.carro.z = vaga.z;
  partida.carro.angulo = vaga.angulo + Math.PI / 2;
  correr(partida, 3);
  assert.equal(missao.contador, 0, 'contou vaga com o carro atravessado');
});

// ---------------------------------------------------------------------------
// RÁPIDO
// ---------------------------------------------------------------------------

/**
 * Põe o carro no metro `s` da volta, opcionalmente deslocado para fora da
 * pista. Anda pelo traçado de verdade, que tem reta e curva — a conta de
 * ângulo em volta do centro não serviria.
 */
function porNaPista(partida, s, foraDaPista = 0) {
  const pista = partida.missao.pista;
  const pontos = pista.pontos;
  const alvo = ((s % pista.comprimento) + pista.comprimento) % pista.comprimento;
  let i = 0;
  while (i < pontos.length - 1 && pontos[i + 1].s <= alvo) i++;
  const p = pontos[i];
  const q = pontos[(i + 1) % pontos.length];
  const trecho = (q.s || pista.comprimento) - p.s;
  const t = trecho > 0 ? (alvo - p.s) / trecho : 0;
  const dx = q.x - p.x, dz = q.z - p.z;
  const l = Math.hypot(dx, dz) || 1;
  partida.carro.x = p.x + dx * t - (dz / l) * foraDaPista;
  partida.carro.z = p.z + dz * t + (dx / l) * foraDaPista;
  partida.carro.angulo = p.angulo;
  partida.carro.vx = 22;
}

test('dar a volta na pista conta uma volta, e só uma', () => {
  const { missao, modelo } = montar('rapido', SEMENTES[0]);
  const partida = partidaDe(missao, modelo);
  const volta = missao.pista.comprimento;
  const relogioAntes = missao.tempoLimite;

  // O carro larga ATRÁS da linha, como no jogo. A primeira passagem pela
  // linha é a largada, não uma volta — quem conta volta ali dá uma de graça.
  // Então são três passagens para duas voltas.
  const passosPorVolta = 400;
  const eventos = [];
  let contadorNaPrimeira = null;
  for (let i = 1; i <= passosPorVolta * 2 + 40; i++) {
    porNaPista(partida, volta - 20 + (i / passosPorVolta) * volta);
    partida.tempo += PASSO;
    const e = andarModo(partida, PASSO);
    if (e) eventos.push(e);
    if (i === 30) contadorNaPrimeira = missao.contador;
  }

  assert.equal(contadorNaPrimeira, 0, 'a passagem de largada foi contada como volta');
  assert.equal(missao.contador, 2, `contou ${missao.contador} voltas em duas`);
  assert.ok(missao.tempoLimite > relogioAntes, 'a volta não devolveu tempo');
  assert.ok(missao.volta.melhor > 0, 'não guardou a melhor volta');
  assert.equal(eventos.filter((e) => e.tipo === 'marco').length, 2, 'avisos de volta demais');
});

test('a pista fecha, tem reta e tem curva', () => {
  for (const semente of SEMENTES) {
    const { missao } = montar('rapido', semente);
    const pista = missao.pista;
    const onde = `rapido/${semente}`;
    assert.ok(pista.comprimento > 300 && pista.comprimento < 2000,
      `${onde}: volta de ${Math.round(pista.comprimento)} m`);

    // Fecha: o último ponto encosta no primeiro.
    const a = pista.pontos[0];
    const b = pista.pontos[pista.pontos.length - 1];
    assert.ok(distanciaPlana(a.x, a.z, b.x, b.z) < 8, `${onde}: o traçado não fecha`);

    // Tem reta e tem curva: a viragem por metro varia muito ao longo da volta.
    const viragens = [];
    for (let i = 0; i < pista.pontos.length; i++) {
      const p = pista.pontos[i];
      const q = pista.pontos[(i + 1) % pista.pontos.length];
      viragens.push(Math.abs(normalizarAngulo(q.angulo - p.angulo)));
    }
    const retas = viragens.filter((v) => v < 0.02).length;
    const curvas = viragens.filter((v) => v > 0.06).length;
    assert.ok(retas > pista.pontos.length * 0.2, `${onde}: só ${retas} trechos retos`);
    assert.ok(curvas > pista.pontos.length * 0.1, `${onde}: só ${curvas} trechos de curva`);

    // A pista cabe no mapa.
    for (const p of pista.pontos) {
      assert.ok(Math.abs(p.x) < missao.mundo.limite && Math.abs(p.z) < missao.mundo.limite,
        `${onde}: a pista sai do mapa em (${p.x.toFixed(0)}, ${p.z.toFixed(0)})`);
    }
  }
});

test('balançar em cima da linha de largada não conta volta', () => {
  const { missao, modelo } = montar('rapido', SEMENTES[1]);
  const partida = partidaDe(missao, modelo);
  for (let i = 0; i < 400; i++) {
    porNaPista(partida, Math.sin(i * 0.6) * 5);
    partida.tempo += PASSO;
    andarModo(partida, PASSO);
  }
  assert.equal(missao.contador, 0, `contou ${missao.contador} voltas parado na linha`);
});

test('cruzar a linha cortando por fora da pista não conta volta', () => {
  const { missao, modelo } = montar('rapido', SEMENTES[2]);
  const partida = partidaDe(missao, modelo);
  const volta = missao.pista.comprimento;
  const passos = 400;
  for (let i = 1; i <= passos; i++) {
    // 30 m fora da pista: está no gramado, do lado de fora do guarda-corpo.
    porNaPista(partida, volta - 20 + (i / passos) * volta, 30);
    partida.tempo += PASSO;
    andarModo(partida, PASSO);
  }
  assert.equal(missao.contador, 0, 'contou volta dada fora da pista');
});

test('andar rende ponto, e bater custa tempo', () => {
  const { missao, modelo } = montar('rapido', SEMENTES[3]);
  const partida = partidaDe(missao, modelo);
  partida.carro.vx = 20;
  correr(partida, 2);
  assert.ok(missao.pontuacao > 30, `andou 2s a 72 km/h e fez ${missao.pontuacao} pontos`);

  const relogio = missao.tempoLimite;
  partida.carro.forcaImpacto = 6;
  partida.tempo += PASSO;
  const evento = andarModo(partida, PASSO);
  assert.ok(evento && evento.tipo === 'aviso', 'bater não avisou');
  assert.ok(missao.tempoLimite < relogio, 'bater não custou tempo');
});

// ---------------------------------------------------------------------------
// DRIFT
// ---------------------------------------------------------------------------

/** Carro a 20 m/s escorregando de lado: é o que a física entrega num drift. */
function atravessar(partida, graus) {
  const rad = (graus * Math.PI) / 180;
  partida.carro.vx = 20 * Math.cos(rad);
  partida.carro.vy = 20 * Math.sin(rad);
}

test('atravessado acumula pendente, e endireitar bota no banco', () => {
  const { missao, modelo } = montar('drift', SEMENTES[0]);
  const partida = partidaDe(missao, modelo);

  correr(partida, 3, (p) => atravessar(p, 28));
  assert.ok(missao.drift.pendente > 0, 'drift de 3s não acumulou nada');
  assert.ok(missao.drift.multiplicador > 1, 'multiplicador não subiu em 3s de drift');
  assert.equal(missao.pontuacao, 0, 'bancou ponto antes de endireitar');

  const pendente = missao.drift.pendente * missao.drift.multiplicador;
  const eventos = correr(partida, 1.2, (p) => atravessar(p, 0));
  assert.ok(missao.pontuacao > 0, 'endireitar não bancou o drift');
  assert.ok(Math.abs(missao.pontuacao - pendente) < pendente * 0.02,
    `bancou ${missao.pontuacao} de ${pendente.toFixed(0)} pendentes`);
  assert.equal(missao.contador, 1, 'não contou o drift');
  assert.ok(eventos.some((e) => e.tipo === 'marco'), 'não avisou o ponto');
  assert.equal(missao.drift.multiplicador, 1, 'o multiplicador não voltou ao chão');
});

test('um retoque curto de volante não quebra o combo', () => {
  const { missao, modelo } = montar('drift', SEMENTES[1]);
  const partida = partidaDe(missao, modelo);
  correr(partida, 2.4, (p) => atravessar(p, 30));
  const multiplicador = missao.drift.multiplicador;
  correr(partida, 0.3, (p) => atravessar(p, 2));        // endireitou meio segundo
  correr(partida, 2.4, (p) => atravessar(p, 30));
  assert.equal(missao.pontuacao, 0, 'bancou no meio do drift');
  assert.ok(missao.drift.multiplicador > multiplicador, 'o combo foi zerado por um retoque');
});

test('curva normal não é drift', () => {
  const { missao, modelo } = montar('drift', SEMENTES[2]);
  const partida = partidaDe(missao, modelo);
  correr(partida, 4, (p) => atravessar(p, 5));
  assert.equal(missao.drift.pendente, 0, 'curva de 5° virou drift');
  assert.equal(missao.pontuacao, 0, 'curva normal pontuou');
});

test('drift fora do asfalto não vale', () => {
  const { missao, modelo } = montar('drift', SEMENTES[3]);
  const partida = partidaDe(missao, modelo);
  partida.piso = 'grama';
  correr(partida, 4, (p) => atravessar(p, 32));
  assert.equal(missao.drift.pendente, 0, 'pontuou derrapando na grama');
});

test('bater no meio do drift derruba o pendente, mas não o que já foi bancado', () => {
  const { missao, modelo } = montar('drift', SEMENTES[4]);
  const partida = partidaDe(missao, modelo);
  correr(partida, 3, (p) => atravessar(p, 30));
  correr(partida, 1.2, (p) => atravessar(p, 0));
  const banco = missao.pontuacao;
  assert.ok(banco > 0, 'não bancou o primeiro drift');

  correr(partida, 2.5, (p) => atravessar(p, 30));
  assert.ok(missao.drift.pendente > 0, 'segundo drift não acumulou');
  partida.carro.forcaImpacto = 5;
  partida.tempo += PASSO;
  const evento = andarModo(partida, PASSO);
  assert.ok(evento && evento.tipo === 'aviso', 'bater não avisou');
  assert.equal(missao.drift.pendente, 0, 'a batida não derrubou o pendente');
  assert.equal(missao.pontuacao, banco, 'a batida comeu o que já estava no banco');
});

// ---------------------------------------------------------------------------
// NOTA
// ---------------------------------------------------------------------------

test('a nota sai coerente e o pagamento acompanha o placar', () => {
  for (const modo of LISTA_MODOS) {
    const { missao, modelo } = montar(modo, SEMENTES[0]);
    const partida = partidaDe(missao, modelo);
    missao.pontuacao = 20000;
    missao.contador = 7;
    if (modo === 'drift') missao.drift.melhor = 9;
    if (modo === 'rapido') { missao.volta.melhor = 24; missao.distancia = 3000; }
    if (modo === 'estacionamento') missao.somaAlinhamento = 6.6;
    missao.concluida = true;
    missao.motivo = 'fim';

    const r = avaliarModo(partida);
    assert.equal(r.estrelas, 3, `${modo}: 20 mil pontos não deram 3 estrelas`);
    assert.ok(r.premio > 0, `${modo}: placar alto pagou nada`);
    assert.equal(r.modo, modo, `${modo}: resultado sem identificação do modo`);
    assert.ok(r.linhas.length >= 3, `${modo}: resultado quase vazio`);
    for (const linha of r.linhas) {
      assert.ok(typeof linha.valor === 'string' && linha.valor.length,
        `${modo}: linha '${linha.rotulo}' sem valor`);
    }
  }
});

test('placar zerado não dá estrela nem paga', () => {
  for (const modo of LISTA_MODOS) {
    const { missao, modelo } = montar(modo, SEMENTES[1]);
    const partida = partidaDe(missao, modelo);
    if (modo === 'rapido') missao.volta.melhor = null;
    const r = avaliarModo(partida);
    assert.equal(r.estrelas, 0, `${modo}: estrela de graça`);
    assert.equal(r.premio, 0, `${modo}: pagou sem placar`);
  }
});
