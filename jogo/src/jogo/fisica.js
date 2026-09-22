// A física do carro.
//
// É um modelo de bicicleta: os dois pneus da frente viram um só, os dois de
// trás também. O carro guarda velocidade em coordenadas do PRÓPRIO corpo
// (`vx` para frente, `vy` para o lado) e uma velocidade de giro. A cada passo
// calculamos a força de cada eixo a partir do ângulo de deriva — o ângulo
// entre para onde o pneu aponta e para onde ele está de fato indo — e é daí
// que saem a subesterçagem, o rabo saindo e o carro se firmando ao acelerar.
//
// Duas coisas importam para a sensação ficar parecida com a do jogo que serviu
// de referência:
//
//   1. Em velocidade baixa o modelo de deriva explode (divide por vx≈0), então
//      abaixo de `LIMITE_RASTEJO` trocamos por esterçamento geométrico puro —
//      é justamente a faixa da baliza, onde o carro tem que obedecer redondo.
//   2. O esterço máximo cai com a velocidade. Sem isso, um toque no volante a
//      80 km/h capota o carro e dirigir vira loteria.

import { limitar, misturar, sinal, TAU } from '../nucleo/matematica.js';

const GRAVIDADE = 9.81;
const DENSIDADE_AR = 1.225;
const LIMITE_RASTEJO = 2.2;      // m/s — abaixo disso, esterçamento geométrico
const VELOCIDADE_PARADO = 0.06;  // m/s — abaixo disso consideramos parado

/** Converte m/s para km/h, que é o que o painel mostra. */
export function paraKmh(metrosPorSegundo) {
  return metrosPorSegundo * 3.6;
}

/**
 * Cria o estado de um carro. `ficha` vem do catálogo (carros.js) e descreve o
 * hardware: massa, entre-eixos, potência, aderência.
 */
export function criarCarro(ficha, posicao = { x: 0, z: 0 }, angulo = 0) {
  return {
    ficha,
    x: posicao.x,
    z: posicao.z,
    angulo,                 // guinada, em radianos
    vx: 0,                  // velocidade para frente, no referencial do carro
    vy: 0,                  // velocidade lateral (positiva = escorregando para a direita)
    giro: 0,                // velocidade angular, rad/s
    esterco: 0,             // ângulo real das rodas dianteiras, em radianos
    marcha: 1,
    rotacao: ficha.marchaLenta,
    rotacaoReal: ficha.marchaLenta,  // sem trava: é ela que afoga e que estoura
    afogando: 0,            // 0..1 — marcha alta demais para a velocidade
    cortando: false,        // bateu no limitador: precisa subir a marcha
    trocouMarcha: 0,        // segundos de embreagem pisada ainda por correr
    sentido: 1,             // 1 = drive, -1 = ré
    combustivel: ficha.tanque,
    dano: 0,                // 0..1
    derrapando: 0,          // 0..1, quanto os pneus estão escorregando agora
    travandoFreio: 0,       // 1 quando o freio pede mais do que o chão aguenta
    aceleracaoLateral: 0,   // em g — a carga frágil se importa com isso
    aceleracaoFrontal: 0,
    forcaImpacto: 0,        // pico do último toque, zerado por quem consome
    distancia: 0,           // metros rodados
    combustivelGasto: 0,
    noChao: true,
    inclinacao: 0,          // mergulho do capô (visual)
    rolagem: 0,             // inclinação lateral (visual)
  };
}

/**
 * Um passo de simulação.
 *
 * `comandos` = { acelerador 0..1, freio 0..1, volante -1..1, mao (bool),
 *                sentido 1|-1 }
 * `pista` = { atrito 1.0 normal, menor em chuva/areia }
 */
export function passo(carro, comandos, dt, pista = { atrito: 1 }) {
  const f = carro.ficha;

  // --- volante ---------------------------------------------------------
  // O esterço não é instantâneo: o volante tem inércia, e o limite cai com a
  // velocidade para o carro não ficar nervoso demais na reta.
  const velocidade = Math.hypot(carro.vx, carro.vy);
  const reducao = 1 / (1 + velocidade * f.reducaoEsterco);
  const alvoEsterco = comandos.volante * f.estercoMaximo * Math.max(0.28, reducao);
  const taxa = f.velocidadeVolante * dt;
  carro.esterco += limitar(alvoEsterco - carro.esterco, -taxa, taxa);

  // --- transmissão -----------------------------------------------------
  carro.sentido = comandos.sentido;
  atualizarMarcha(carro, comandos, dt);
  if (carro.trocouMarcha > 0) carro.trocouMarcha = Math.max(0, carro.trocouMarcha - dt);

  // --- carga nos eixos, com transferência de peso -----------------------
  // Acelerar joga peso para trás (a traseira ganha aderência), frear joga para
  // frente. Isso é o que faz o carro girar se você freia no meio da curva.
  // A transferência usa a aceleração do passo ANTERIOR — é uma defasagem de
  // 1/120 s, invisível, e evita resolver um sistema implícito.
  const peso = f.massa * GRAVIDADE;
  const distanciaFrente = f.entreEixos * f.distribuicaoTraseira;
  const distanciaTras = f.entreEixos * (1 - f.distribuicaoTraseira);
  const transferencia = (f.alturaCentroMassa / f.entreEixos) * f.massa * carro.aceleracaoFrontal * GRAVIDADE;

  const cargaFrente = Math.max(peso * 0.12,
    peso * (distanciaTras / f.entreEixos) - transferencia);
  const cargaTras = Math.max(peso * 0.12,
    peso * (distanciaFrente / f.entreEixos) + transferencia);

  const atrito = pista.atrito * f.aderencia;
  let limiteFrente = atrito * cargaFrente;
  let limiteTras = atrito * cargaTras;

  // Freio de mão: a traseira perde quase tudo. É o botão do drift.
  if (comandos.mao) limiteTras *= 0.32;

  // --- forças no eixo longitudinal ------------------------------------
  let forcaMotor = 0;

  const combustivelAcabou = carro.combustivel <= 0;
  const acelerador = combustivelAcabou ? 0 : limitar(comandos.acelerador, 0, 1);

  if (acelerador > 0) {
    // No manual, a marcha errada CUSTA. Três penalidades, todas do mundo real:
    //   afogando  — rotação abaixo da marcha lenta: o motor não tem fôlego
    //   cortando  — bateu no limitador: a injeção corta e a aceleração some
    //   trocando  — durante a troca a embreagem está pisada e nada passa
    let entrega = curvaDeTorque(carro.rotacao, f);
    if (carro.cortando) entrega *= 0.07;
    if (carro.afogando > 0) entrega *= 1 - carro.afogando * 0.82;
    if (carro.trocouMarcha > 0) entrega *= 0.12;

    const torque = entrega * f.torqueMaximo * acelerador;
    const relacao = f.relacoes[carro.marcha - 1] * f.diferencial;
    forcaMotor = (torque * relacao * f.rendimento) / f.raioRoda * carro.sentido;
  }

  // O chão só aguenta o que a aderência do eixo de tração deixa passar. Sem
  // este teto o carro acelera igual na chuva e no seco, e o motor forte deixa
  // de patinar — que é justamente a graça de ter motor forte.
  const cargaDeTracao = f.tracao === 'dianteira' ? cargaFrente
    : f.tracao === 'integral' ? peso
      : cargaTras;
  const limiteTracao = atrito * cargaDeTracao * 1.06;
  const patinando = Math.abs(forcaMotor) > limiteTracao;
  if (patinando) forcaMotor = sinal(forcaMotor) * limiteTracao;

  // Freio motor: solta o acelerador e o carro desacelera sozinho.
  const freioMotor = acelerador > 0.02 ? 0 : f.freioMotor * Math.min(1, Math.abs(carro.vx) / 3);

  const arrasto = 0.5 * DENSIDADE_AR * f.coeficienteAr * f.areaFrontal * carro.vx * Math.abs(carro.vx);
  const rolamento = f.rolamento * peso * Math.tanh(carro.vx * 4);

  let forcaFreio = 0;
  const pedalFreio = limitar(comandos.freio, 0, 1);
  if (pedalFreio > 0.01) {
    // O freio usa as quatro rodas, então o teto é a aderência do carro inteiro.
    const pedido = f.forcaFreio * pedalFreio;
    const travando = pedido > atrito * peso;
    forcaFreio = Math.min(pedido, atrito * peso)
      * Math.tanh(Math.abs(carro.vx) * 6) * sinal(carro.vx || 1);
    if (travando) carro.travandoFreio = 1; else carro.travandoFreio = 0;
  } else {
    carro.travandoFreio = 0;
  }

  const forcaLongitudinal = forcaMotor - arrasto - rolamento - forcaFreio
    - freioMotor * f.massa * Math.tanh(carro.vx);

  // --- forças laterais --------------------------------------------------
  let forcaLateralFrente = 0;
  let forcaLateralTras = 0;
  let momento = 0;

  if (Math.abs(carro.vx) > LIMITE_RASTEJO) {
    const vxAbs = Math.max(Math.abs(carro.vx), 0.6);
    const derivaFrente = Math.atan2(carro.vy + carro.giro * distanciaFrente, vxAbs)
      - carro.esterco * sinal(carro.vx);
    const derivaTras = Math.atan2(carro.vy - carro.giro * distanciaTras, vxAbs);

    forcaLateralFrente = pneu(derivaFrente, f.rigidezFrente, limiteFrente);
    forcaLateralTras = pneu(derivaTras, f.rigidezTras, limiteTras);

    const usoFrente = Math.abs(forcaLateralFrente) / limiteFrente;
    const usoTras = Math.abs(forcaLateralTras) / limiteTras;
    carro.derrapando = limitar(Math.max(usoFrente, usoTras) - 0.82, 0, 1) / 0.18;

    momento = distanciaFrente * forcaLateralFrente * Math.cos(carro.esterco)
      - distanciaTras * forcaLateralTras;
  } else {
    // Manobra: o carro segue a geometria do esterço, sem escorregar.
    // É o comportamento que a baliza precisa — previsível e obediente.
    const raioInverso = Math.tan(carro.esterco) / f.entreEixos;
    const giroAlvo = carro.vx * raioInverso;
    carro.giro = misturar(carro.giro, giroAlvo, Math.min(1, dt * 12));
    carro.vy = misturar(carro.vy, 0, Math.min(1, dt * 10));
    carro.derrapando = comandos.mao && Math.abs(carro.vx) > 0.5 ? 0.5 : 0;
    momento = 0;
  }

  // Patinar na largada e travar o freio também são pneus escorregando: é o que
  // faz o carro chiar quando você pisa fundo na chuva.
  if (patinando) carro.derrapando = Math.max(carro.derrapando, 0.65);
  if (carro.travandoFreio) carro.derrapando = Math.max(carro.derrapando, 0.8);

  // --- integração -------------------------------------------------------
  const aceleracaoX = forcaLongitudinal / f.massa + carro.giro * carro.vy;
  const aceleracaoY = (forcaLateralFrente * Math.cos(carro.esterco) + forcaLateralTras) / f.massa
    - carro.giro * carro.vx;

  carro.vx += aceleracaoX * dt;
  if (Math.abs(carro.vx) > LIMITE_RASTEJO) {
    carro.vy += aceleracaoY * dt;
    carro.giro += (momento / f.inerciaGuinada) * dt;
    // Amortecimento de guinada: sem isso o carro fica rodopiando para sempre.
    carro.giro -= carro.giro * f.amortecimentoGuinada * dt;
  }

  // Freio e atrito não empurram o carro para trás depois que ele parou.
  if (pedalFreio > 0.01 && Math.abs(carro.vx) < 0.25) carro.vx = 0;
  if (acelerador < 0.02 && Math.abs(carro.vx) < VELOCIDADE_PARADO) carro.vx = 0;

  carro.angulo += carro.giro * dt;
  if (carro.angulo > Math.PI) carro.angulo -= TAU;
  if (carro.angulo < -Math.PI) carro.angulo += TAU;

  const seno = Math.sin(carro.angulo);
  const cosseno = Math.cos(carro.angulo);
  // frente = (-sen, -cos); direita = (cos, -sen)
  const dx = (-seno * carro.vx + cosseno * carro.vy) * dt;
  const dz = (-cosseno * carro.vx - seno * carro.vy) * dt;
  carro.x += dx;
  carro.z += dz;
  carro.distancia += Math.hypot(dx, dz);

  // --- leituras para o resto do jogo -----------------------------------
  carro.aceleracaoFrontal = aceleracaoX / GRAVIDADE;
  carro.aceleracaoLateral = aceleracaoY / GRAVIDADE;

  // Mergulho e rolagem: puramente visual, mas é o que dá peso à imagem.
  carro.inclinacao = misturar(carro.inclinacao, limitar(-carro.aceleracaoFrontal * 0.05, -0.05, 0.05), Math.min(1, dt * 8));
  carro.rolagem = misturar(carro.rolagem, limitar(carro.aceleracaoLateral * 0.06, -0.07, 0.07), Math.min(1, dt * 8));

  gastarCombustivel(carro, acelerador, dt);
  return carro;
}

/**
 * Força lateral de um eixo. Cresce quase reto com o ângulo de deriva e satura
 * no limite de aderência — é a parte de cima da curva de Pacejka, aproximada
 * com uma tangente hiperbólica. Depois do pico o pneu não dá mais nada, que é
 * exatamente quando o carro escorrega.
 */
function pneu(deriva, rigidez, limite) {
  return -limite * Math.tanh((rigidez * deriva) / limite);
}

/**
 * Curva de torque normalizada: fraco embaixo, cheio no meio, caindo no fim.
 * Elétrico é outra história — entrega tudo desde parado, e só cai lá em cima.
 */
function curvaDeTorque(rotacao, ficha) {
  const t = limitar((rotacao - ficha.marchaLenta) / (ficha.rotacaoMaxima - ficha.marchaLenta), 0, 1);
  if (ficha.curvaPlana) return 1 - 0.42 * t * t;
  return 0.55 + 0.75 * Math.sin(Math.PI * Math.pow(t, 0.78)) - 0.22 * t * t;
}

/**
 * O câmbio.
 *
 * No automático ele troca sozinho, como antes. No manual quem troca é você — e
 * aí a rotação deixa de ser presa na faixa boa: ela AFUNDA se a marcha for
 * alta demais para a velocidade, e ESTOURA se for baixa demais. É essa conta
 * solta que faz a marcha certa importar.
 *
 * A embreagem protege a saída: parado em primeira ou segunda o motor não
 * afoga, porque na vida real você patina a embreagem. Da terceira para cima,
 * sair do lugar é o castigo que tem que ser.
 */
function atualizarMarcha(carro, comandos, dt) {
  const f = carro.ficha;
  const relacao = f.relacoes[carro.marcha - 1] * f.diferencial;
  const rotacaoDaRoda = (Math.abs(carro.vx) / f.raioRoda) * relacao * 60 / TAU;

  if (carro.sentido < 0) {
    carro.marcha = 1;
    carro.afogando = 0;
    carro.cortando = false;
    carro.rotacaoReal = Math.max(f.marchaLenta, rotacaoDaRoda);
    carro.rotacao = misturar(carro.rotacao, carro.rotacaoReal, Math.min(1, dt * 7));
    return;
  }

  if (comandos.cambio !== 'manual' || f.relacoes.length <= 1) {
    const alvo = Math.max(f.marchaLenta, Math.min(f.rotacaoMaxima, rotacaoDaRoda));
    carro.rotacao = misturar(carro.rotacao, alvo, Math.min(1, dt * 7));
    carro.rotacaoReal = alvo;
    carro.afogando = 0;
    carro.cortando = false;
    if (carro.rotacao > f.rotacaoTroca && carro.marcha < f.relacoes.length) carro.marcha++;
    else if (carro.rotacao < f.rotacaoReduz && carro.marcha > 1) carro.marcha--;
    return;
  }

  carro.rotacaoReal = rotacaoDaRoda;

  const piso = f.marchaLenta * 1.3;
  let afogando = limitar((piso - rotacaoDaRoda) / piso, 0, 1);
  // Embreagem: sair do lugar em primeira ou segunda é trabalho dela, não do
  // motor. Em marcha mais alta, você sente.
  if (carro.marcha <= 2 && Math.abs(carro.vx) < 5) afogando *= 0.12;
  carro.afogando = afogando;
  carro.cortando = rotacaoDaRoda > f.rotacaoMaxima;

  const mostrar = limitar(rotacaoDaRoda, f.marchaLenta * 0.72, f.rotacaoMaxima * 1.06);
  carro.rotacao = misturar(carro.rotacao, mostrar, Math.min(1, dt * 9));
}

/**
 * Sobe ou desce uma marcha. Chame UMA vez por quadro, não por passo de física:
 * trocar de marcha é um ato, não um estado.
 *
 * Devolve o que aconteceu, para o som e o painel reagirem:
 * 'trocou', 'no-limite' (já está na última ou na primeira) ou 'sem-cambio'.
 */
export function trocarMarcha(carro, direcao) {
  const f = carro.ficha;
  if (f.relacoes.length <= 1) return 'sem-cambio';
  if (carro.sentido < 0) return 'sem-cambio';
  const nova = limitar(carro.marcha + direcao, 1, f.relacoes.length);
  if (nova === carro.marcha) return 'no-limite';
  carro.marcha = nova;
  carro.trocouMarcha = 0.22;
  return 'trocou';
}

/**
 * Consumo. Tem uma parte fixa (o motor ligado já bebe) e uma parte que cresce
 * com rotação e acelerador. É o que transforma "chegar" em "chegar direito":
 * dá para vencer a missão dirigindo feito louco e mesmo assim tirar uma
 * estrela só.
 */
function gastarCombustivel(carro, acelerador, dt) {
  if (carro.combustivel <= 0) { carro.combustivel = 0; return; }
  const f = carro.ficha;
  const carga = 0.18 + 0.82 * acelerador;
  const giroRelativo = carro.rotacao / f.rotacaoMaxima;
  const litros = f.consumoBase * (0.25 + carga * (0.4 + giroRelativo * 1.5)) * dt;
  carro.combustivel = Math.max(0, carro.combustivel - litros);
  carro.combustivelGasto += litros;
}

/** Aplica um impacto: perde velocidade, ganha dano, dá um tranco no giro. */
export function bater(carro, normalX, normalZ, severidade) {
  const velocidade = Math.hypot(carro.vx, carro.vy);
  const impacto = velocidade * severidade;
  carro.forcaImpacto = Math.max(carro.forcaImpacto, impacto);
  carro.dano = limitar(carro.dano + impacto * 0.012, 0, 1);

  // Velocidade do carro no mundo, refletida na normal da parede.
  const seno = Math.sin(carro.angulo);
  const cosseno = Math.cos(carro.angulo);
  let mundoX = -seno * carro.vx + cosseno * carro.vy;
  let mundoZ = -cosseno * carro.vx - seno * carro.vy;

  const projecao = mundoX * normalX + mundoZ * normalZ;
  if (projecao < 0) {
    const restituicao = 0.28;
    mundoX -= (1 + restituicao) * projecao * normalX;
    mundoZ -= (1 + restituicao) * projecao * normalZ;
  }
  const perda = 0.62;
  mundoX *= perda;
  mundoZ *= perda;

  // De volta para o referencial do carro.
  carro.vx = -seno * mundoX - cosseno * mundoZ;
  carro.vy = cosseno * mundoX - seno * mundoZ;
  carro.giro *= 0.55;
}

/** Os quatro cantos do carro no mundo — usado por colisão e por sombra. */
export function cantos(carro, folga = 0) {
  const f = carro.ficha;
  const meiaLargura = f.largura / 2 + folga;
  const meioComprimento = f.comprimento / 2 + folga;
  const seno = Math.sin(carro.angulo);
  const cosseno = Math.cos(carro.angulo);
  const frenteX = -seno, frenteZ = -cosseno;
  const direitaX = cosseno, direitaZ = -seno;
  const saida = [];
  for (const [df, dl] of [[1, -1], [1, 1], [-1, 1], [-1, -1]]) {
    saida.push({
      x: carro.x + frenteX * meioComprimento * df + direitaX * meiaLargura * dl,
      z: carro.z + frenteZ * meioComprimento * df + direitaZ * meiaLargura * dl,
    });
  }
  return saida;
}
