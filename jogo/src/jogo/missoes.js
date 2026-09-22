// As missões.
//
// Aqui mora a resposta para o problema de sempre: jogo de dirigir cansa porque
// é a mesma tarefa repetida. A saída foi separar TRÊS coisas que variam
// sozinhas — o que você faz, onde faz e com que tempo faz:
//
//   tipo     baliza, vaga, entrega, economia, slalom, escolta, carga
//   cenário  centro, beira-mar, estrada de terra, zona industrial
//   clima    manhã, fim de tarde, noite, chuva, neblina, sol a pino
//
// São 7 × 4 × 6 = 168 combinações antes de contar o traçado, que é sorteado, e
// o carro, que muda o jeito de tudo. Nenhuma missão passa de dois minutos: é
// curta de propósito, para terminar antes de virar tarefa.

import {
  criarSorteio, entre, escolher, inteiro, limitar, distanciaPlana,
  normalizarAngulo, formatarTempo, TAU,
} from '../nucleo/matematica.js';
import { gerarMundo, pontoNaRua, LISTA_CENARIOS } from './mundo.js';
import { LISTA_CLIMAS } from './clima.js';
import { dentroDe, encaixe } from './colisao.js';
import { paraKmh } from './fisica.js';
import * as modelos from '../motor/modelos.js';
import { CARROS } from './carros.js';
import { construirCarro } from '../motor/modelos.js';
import { montarModo, andarModo, avaliarModo, alvoDoModo } from './modos.js';

export const TIPOS = ['baliza', 'vaga', 'entrega', 'economia', 'slalom', 'escolta', 'carga'];

export const FICHA_DOS_TIPOS = {
  baliza: {
    nome: 'Baliza',
    resumo: 'Encaixar entre dois carros, sem encostar em nenhum.',
    dica: 'Ré, volante todo para um lado, depois desfaz. Devagar.',
  },
  vaga: {
    nome: 'Vaga',
    resumo: 'Estacionar na vaga marcada, dentro das linhas.',
    dica: 'Alinhe antes de entrar. Entrar torto é sempre sair de novo.',
  },
  entrega: {
    nome: 'Entrega',
    resumo: 'Passar por todos os pontos antes do tempo acabar.',
    dica: 'A seta no alto da tela aponta o próximo ponto.',
  },
  economia: {
    nome: 'Economia',
    resumo: 'Chegar com o pouco combustível que te deram.',
    dica: 'Acelerador no meio, sem freada. Quem pisa fundo não chega.',
  },
  slalom: {
    nome: 'Slalom',
    resumo: 'Passar por dentro de cada par de cones, na ordem.',
    dica: 'Olhe o par seguinte, não o que você está atravessando.',
  },
  escolta: {
    nome: 'Escolta',
    resumo: 'Seguir o carro da frente sem grudar e sem perder de vista.',
    dica: 'Fique entre 8 e 30 metros. Bater nele encerra o serviço.',
  },
  carga: {
    nome: 'Carga frágil',
    resumo: 'Levar a carga inteira até o destino.',
    dica: 'Curva rápida derruba. A barra da direita é a carga reclamando.',
  },
};

// ---------------------------------------------------------------------------
// SORTEIO DA MISSÃO
// ---------------------------------------------------------------------------

/**
 * Descreve uma missão sem ainda construir nada. `nivel` sobe a dificuldade:
 * menos tempo, mais trânsito, clima pior.
 */
export function sortearMissao(semente, nivel = 1, forcar = {}) {
  const sortear = criarSorteio(semente);
  const tipo = forcar.tipo || escolher(sortear, TIPOS);
  const cenario = forcar.cenario || escolher(sortear, LISTA_CENARIOS);

  // Clima ruim só aparece depois que a pessoa já sabe dirigir.
  const climasFaceis = LISTA_CLIMAS.filter((c) => !c.farois);
  const climaPossivel = nivel >= 3 ? LISTA_CLIMAS : climasFaceis;
  const clima = forcar.clima || escolher(sortear, climaPossivel).id;

  const dificuldade = limitar(0.25 + nivel * 0.09 + sortear() * 0.2, 0.25, 1.35);

  return {
    semente,
    tipo,
    cenario,
    clima,
    nivel,
    dificuldade,
    transito: Math.round(limitar(2 + nivel * 0.8 + sortear() * 3, 0, 14)),
    titulo: FICHA_DOS_TIPOS[tipo].nome,
    resumo: FICHA_DOS_TIPOS[tipo].resumo,
    dica: FICHA_DOS_TIPOS[tipo].dica,
    premio: Math.round((420 + nivel * 130) * (0.85 + dificuldade * 0.5)),
  };
}

/** A carreira: uma fila de missões que sobe de nível sem repetir o tipo em seguida. */
export function gerarCarreira(semente, quantidade = 40) {
  const sortear = criarSorteio(semente ^ 0x0ca7);
  const lista = [];
  let anterior = null;
  for (let i = 0; i < quantidade; i++) {
    const nivel = 1 + Math.floor(i / 3);
    let descritor;
    let tentativas = 0;
    do {
      descritor = sortearMissao(Math.floor(sortear() * 1e9), nivel);
      tentativas++;
    } while (descritor.tipo === anterior && tentativas < 8);
    anterior = descritor.tipo;
    descritor.indice = i;
    lista.push(descritor);
  }
  return lista;
}

// ---------------------------------------------------------------------------
// MONTAGEM
// ---------------------------------------------------------------------------

/** Constrói o mundo que a missão precisa e cria os objetivos. */
export function montarMissao(descritor, modeloCarro) {
  // Modo avulso é outra coisa: mesma forma de missão, outras regras. Quem
  // monta é o modos.js, e daqui para frente nada mais precisa saber disso.
  if (descritor.modo) return montarModo(descritor, modeloCarro);

  const sortear = criarSorteio(descritor.semente ^ 0x7b31);
  const precisaPatio = descritor.tipo === 'vaga';

  const mundo = gerarMundo(descritor.semente, {
    cenario: descritor.cenario,
    estacionamento: precisaPatio ? { larguraVaga: 2.55 + (1 - descritor.dificuldade) * 0.5 } : null,
    metros: 152,
  });

  const missao = {
    descritor,
    mundo,
    objetivos: [],
    indice: 0,
    pontos: [],
    aviso: null,
    progresso: 0,
    medidor: null,
    escolta: null,
    concluida: false,
    falhou: false,
    motivo: '',
  };

  const construtores = {
    baliza: montarBaliza,
    vaga: montarVaga,
    entrega: montarEntrega,
    economia: montarEconomia,
    slalom: montarSlalom,
    escolta: montarEscolta,
    carga: montarCarga,
  };
  construtores[descritor.tipo](missao, sortear, modeloCarro);

  missao.tempoLimite = missao.tempoLimite || 90;
  return missao;
}

/** Uma vaga junto ao meio-fio, do tamanho pedido. */
function vagaNaRua(mundo, sortear, comprimento, folga) {
  const via = escolher(sortear, mundo.vias);
  const t = entre(sortear, 0.25, 0.75);
  const p = via.de + t * (via.ate - via.de);
  const lado = sortear() > 0.5 ? 1 : -1;
  const desvio = (via.largura / 2 - 1.45) * lado;

  if (via.eixo === 'x') {
    return {
      x: p, z: via.centro + desvio,
      angulo: lado > 0 ? -Math.PI / 2 : Math.PI / 2,
      largura: 2.5, comprimento: comprimento + folga,
      via, eixo: 'x', direcao: { x: 1, z: 0 },
    };
  }
  return {
    x: via.centro + desvio, z: p,
    angulo: lado > 0 ? Math.PI : 0,
    largura: 2.5, comprimento: comprimento + folga,
    via, eixo: 'z', direcao: { x: 0, z: 1 },
  };
}

function montarBaliza(missao, sortear, modeloCarro) {
  const d = missao.descritor.dificuldade;
  const comprimento = modeloCarro.ficha.comprimento;
  // Quanto maior a dificuldade, mais apertado fica o buraco.
  const folga = entre(sortear, 2.4, 3.2) - d * 1.3;
  const vaga = vagaNaRua(missao.mundo, sortear, comprimento, Math.max(1.15, folga));
  vaga.largura = modeloCarro.ficha.largura + 0.75;
  vaga.alvo = true;

  missao.mundo.vagas.push(vaga);
  missao.vagaAlvo = vaga;

  // Os dois carros que fazem o buraco.
  const dir = vaga.eixo === 'x' ? { x: 1, z: 0 } : { x: 0, z: 1 };
  for (const sentido of [-1, 1]) {
    const modelo = escolher(sortear, CARROS.filter((c) => c.id !== 'pao-quente'));
    const distancia = vaga.comprimento / 2 + modelo.ficha.comprimento / 2 + 0.25;
    const x = vaga.x + dir.x * distancia * sentido;
    const z = vaga.z + dir.z * distancia * sentido;
    const cor = escolher(sortear, [0x3f4f63, 0xb03a2e, 0xe8e6e1, 0x4b5d3a, 0xd6a12c]);
    missao.mundo.props.push({
      tipo: 'carro-parado', malha: construirCarro(modelo, cor).carroceria,
      x, z, guinada: vaga.angulo, raio: modelo.ficha.comprimento * 0.6,
    });
    const rodas = construirCarro(modelo, cor);
    for (const roda of rodas.rodas) {
      const cos = Math.cos(vaga.angulo), sen = Math.sin(vaga.angulo);
      missao.mundo.props.push({
        tipo: 'roda-parada', malha: rodas.roda,
        x: x + cos * roda.x + sen * roda.z,
        y: rodas.raioRoda,
        z: z - sen * roda.x + cos * roda.z,
        guinada: vaga.angulo, raio: 0.6,
      });
    }
    missao.mundo.colisores.push({
      x, z, guinada: vaga.angulo,
      largura: modelo.ficha.largura, comprimento: modelo.ficha.comprimento,
      solido: true, altura: 1.5, carroParado: true,
    });
  }

  // Começa ADIANTE da vaga, já no sentido da rua e deslocado para o miolo da
  // pista: é a posição de quem passou o buraco e agora vai entrar de ré. O
  // desvio tem que ser na direção do centro da via — a vaga fica no meio-fio,
  // e somar um valor fixo jogava o carro para cima da calçada metade das vezes.
  // ANTES do buraco, olhando para ele: você vê os dois carros e a brecha entre
  // eles, passa, e só então encaixa de ré. Começar depois do buraco esconde a
  // vaga atrás da câmera e ainda joga um carro parado na cara do jogador.
  const recuo = -(vaga.comprimento / 2 + 13);
  const frenteX = -Math.sin(vaga.angulo);
  const frenteZ = -Math.cos(vaga.angulo);
  const via = vaga.via;
  const paraOCentro = via.eixo === 'x'
    ? { x: 0, z: Math.sign(via.centro - vaga.z) * 2.6 }
    : { x: Math.sign(via.centro - vaga.x) * 2.6, z: 0 };
  missao.mundo.inicio = {
    x: vaga.x + frenteX * recuo + paraOCentro.x,
    z: vaga.z + frenteZ * recuo + paraOCentro.z,
    angulo: vaga.angulo,
  };
  missao.tempoLimite = Math.round(entre(sortear, 80, 100) - d * 22);
  missao.objetivos = [{ tipo: 'estacionar', vaga }];
  missao.medidor = { rotulo: 'encaixe', valor: 0 };
}

function montarVaga(missao, sortear, modeloCarro) {
  const mundo = missao.mundo;
  if (!mundo.vagas.length) { montarBaliza(missao, sortear, modeloCarro); return; }

  const d = missao.descritor.dificuldade;
  const livres = mundo.vagas.filter(() => true);
  const escolhida = escolher(sortear, livres);
  escolhida.alvo = true;
  escolhida.largura = Math.max(modeloCarro.ficha.largura + 0.55, escolhida.largura);
  escolhida.comprimento = Math.max(modeloCarro.ficha.comprimento + 0.7, escolhida.comprimento);
  missao.vagaAlvo = escolhida;
  missao.deRe = d > 0.7 && sortear() > 0.45;

  // Enche parte das outras vagas com carro parado: o pátio fica vivo e estreito.
  for (const vaga of mundo.vagas) {
    if (vaga === escolhida) continue;
    if (sortear() > 0.45 + d * 0.25) continue;
    const modelo = escolher(sortear, CARROS);
    if (modelo.ficha.comprimento > vaga.comprimento - 0.2) continue;
    const cor = escolher(sortear, [0x3f4f63, 0xb03a2e, 0xe8e6e1, 0x4b5d3a, 0xd6a12c, 0x2e6fb7]);
    const peca = construirCarro(modelo, cor);
    const guinada = vaga.angulo + (sortear() > 0.85 ? entre(sortear, -0.14, 0.14) : 0);
    vaga.ocupada = true;
    missao.mundo.props.push({ tipo: 'carro-parado', malha: peca.carroceria, x: vaga.x, z: vaga.z, guinada, raio: modelo.ficha.comprimento * 0.6 });
    for (const roda of peca.rodas) {
      const cos = Math.cos(guinada), sen = Math.sin(guinada);
      missao.mundo.props.push({
        tipo: 'roda-parada', malha: peca.roda,
        x: vaga.x + cos * roda.x + sen * roda.z, y: peca.raioRoda,
        z: vaga.z - sen * roda.x + cos * roda.z, guinada, raio: 0.6,
      });
    }
    missao.mundo.colisores.push({
      x: vaga.x, z: vaga.z, guinada,
      largura: modelo.ficha.largura, comprimento: modelo.ficha.comprimento,
      solido: true, altura: 1.5, carroParado: true,
    });
  }

  missao.tempoLimite = Math.round(entre(sortear, 85, 110) - d * 20);
  missao.objetivos = [{ tipo: 'estacionar', vaga: escolhida }];
  missao.medidor = { rotulo: 'encaixe', valor: 0 };
}

function montarEntrega(missao, sortear) {
  const d = missao.descritor.dificuldade;
  const quantos = inteiro(sortear, 3, 5);
  let anterior = missao.mundo.inicio;
  for (let i = 0; i < quantos; i++) {
    const ponto = pontoNaRua(missao.mundo, sortear, anterior, 32);
    ponto.raio = 4.2;
    missao.pontos.push(ponto);
    anterior = ponto;
  }
  marcarPontos(missao, 0x36c96f);
  const percurso = comprimentoDoPercurso(missao.mundo.inicio, missao.pontos);
  missao.tempoLimite = Math.round(percurso / entre(sortear, 5.5, 7) + 18 - d * 5);
  missao.objetivos = missao.pontos.map((p) => ({ tipo: 'passar', ponto: p }));
}

function montarEconomia(missao, sortear, modeloCarro) {
  const d = missao.descritor.dificuldade;
  const quantos = inteiro(sortear, 2, 3);
  let anterior = missao.mundo.inicio;
  for (let i = 0; i < quantos; i++) {
    const ponto = pontoNaRua(missao.mundo, sortear, anterior, 40);
    ponto.raio = 4.5;
    missao.pontos.push(ponto);
    anterior = ponto;
  }
  marcarPontos(missao, 0x39b8d6);
  const percurso = comprimentoDoPercurso(missao.mundo.inicio, missao.pontos);

  // O orçamento de combustível é calculado a partir do percurso: dá, mas só
  // para quem dirige com o pé leve.
  const consumoIdeal = modeloCarro.ficha.consumoBase * 0.62;
  missao.combustivel = percurso / 9 * consumoIdeal * (1.55 - d * 0.35);
  missao.tempoLimite = Math.round(percurso / 6.5 + 25);
  missao.objetivos = missao.pontos.map((p) => ({ tipo: 'passar', ponto: p }));
  missao.medidor = { rotulo: 'tanque', valor: 1 };
}

function montarSlalom(missao, sortear, modeloCarro) {
  const d = missao.descritor.dificuldade;
  const via = escolher(sortear, missao.mundo.vias);
  const passo = entre(sortear, 13, 18) - d * 3;
  // A largada fica 12 m antes do primeiro portão, então a folga na ponta de
  // trás tem que ser maior que isso — senão o carro nasce fora da rua.
  const folgaInicial = 20;
  const folgaFinal = 14;
  const util = (via.ate - via.de) - folgaInicial - folgaFinal;
  const quantos = limitar(inteiro(sortear, 5, 8), 3, Math.max(3, Math.floor(util / passo)));
  const inicio = via.de + folgaInicial;
  const abertura = modeloCarro.ficha.largura + entre(sortear, 1.5, 2.3) - d * 0.55;
  const cones = [];

  for (let i = 0; i < quantos; i++) {
    const p = inicio + i * passo;
    const lado = (i % 2 === 0 ? 1 : -1) * entre(sortear, 1.2, via.largura / 2 - abertura / 2 - 0.4);
    const centro = via.eixo === 'x'
      ? { x: p, z: via.centro + lado }
      : { x: via.centro + lado, z: p };
    const perpendicular = via.eixo === 'x' ? { x: 0, z: 1 } : { x: 1, z: 0 };

    const portao = {
      x: centro.x, z: centro.z,
      angulo: via.eixo === 'x' ? -Math.PI / 2 : Math.PI,
      abertura,
    };
    missao.pontos.push({ x: centro.x, z: centro.z, raio: abertura / 2 + 0.5 });

    for (const sentido of [-1, 1]) {
      const cx = centro.x + perpendicular.x * (abertura / 2) * sentido;
      const cz = centro.z + perpendicular.z * (abertura / 2) * sentido;
      const cone = {
        x: cx, z: cz, guinada: 0,
        largura: 0.45, comprimento: 0.45, altura: 0.6,
        solido: true, derrubavel: true, derrubado: false,
      };
      missao.mundo.colisores.push(cone);
      missao.mundo.props.push({ tipo: 'cone', malha: modelos.cone(), x: cx, z: cz, guinada: 0, raio: 0.5, colisor: cone });
      cones.push(cone);
    }
    missao.objetivos.push({ tipo: 'portao', portao });
  }

  missao.cones = cones;
  const primeiro = missao.pontos[0];
  const recuo = 12;
  missao.mundo.inicio = via.eixo === 'x'
    ? { x: primeiro.x - recuo, z: via.centro, angulo: -Math.PI / 2 }
    : { x: via.centro, z: primeiro.z - recuo, angulo: Math.PI };
  missao.tempoLimite = Math.round(quantos * passo / 5.5 + 22);
  missao.medidor = { rotulo: 'cones', valor: 1 };
}

function montarEscolta(missao, sortear) {
  const mundo = missao.mundo;
  const rota = escolher(sortear, mundo.rotas);
  const modelo = escolher(sortear, CARROS.filter((c) => c.id !== 'faisca'));
  const cor = 0xe8a13a;
  const corpo = construirCarro(modelo, cor);
  // Começa no SEGUNDO ponto da rota: o primeiro fica na ponta da rua, e o
  // jogador, que nasce atrás do líder, cairia fora do asfalto.
  const p0 = rota.pontos[1];
  const p1 = rota.pontos[2 % rota.pontos.length];

  missao.escolta = {
    modelo, corpo, cor,
    x: p0.x, z: p0.z,
    angulo: Math.atan2(-(p1.x - p0.x), -(p1.z - p0.z)),
    velocidade: 0,
    alvoVelocidade: entre(sortear, 7, 11),
    rota, indice: 2 % rota.pontos.length,
    giroRoda: 0, esterco: 0, freando: false,
    largura: modelo.ficha.largura, comprimento: modelo.ficha.comprimento,
    voltas: 0,
    distanciaPercorrida: 0,
    percursoNecessario: entre(sortear, 190, 300),
  };

  mundo.inicio = {
    x: p0.x + Math.sin(missao.escolta.angulo) * 9,
    z: p0.z + Math.cos(missao.escolta.angulo) * 9,
    angulo: missao.escolta.angulo,
  };
  missao.tempoLimite = Math.round(missao.escolta.percursoNecessario / 5.5 + 20);
  missao.objetivos = [{ tipo: 'escoltar' }];
  missao.medidor = { rotulo: 'distância', valor: 0.5 };
}

function montarCarga(missao, sortear) {
  const d = missao.descritor.dificuldade;
  const quantos = inteiro(sortear, 2, 3);
  let anterior = missao.mundo.inicio;
  for (let i = 0; i < quantos; i++) {
    const ponto = pontoNaRua(missao.mundo, sortear, anterior, 38);
    ponto.raio = 4.5;
    missao.pontos.push(ponto);
    anterior = ponto;
  }
  marcarPontos(missao, 0xe0a33a);
  const percurso = comprimentoDoPercurso(missao.mundo.inicio, missao.pontos);
  missao.tempoLimite = Math.round(percurso / 7.5 + 22);
  missao.objetivos = missao.pontos.map((p) => ({ tipo: 'passar', ponto: p }));
  missao.carga = {
    integridade: 1,
    limiteG: 0.60 - d * 0.10,
  };
  missao.medidor = { rotulo: 'carga', valor: 1 };
}

function marcarPontos(missao, cor) {
  missao.pontos.forEach((ponto, i) => {
    const largura = ponto.via && ponto.via.largura ? ponto.via.largura * 0.7 : 6;
    const guinada = ponto.via && ponto.via.eixo === 'x' ? Math.PI / 2 : 0;
    missao.mundo.props.push({
      tipo: 'portico', malha: modelos.portico(largura, cor),
      x: ponto.x, z: ponto.z, guinada, raio: largura, ponto: i, portico: true,
    });
  });
}

function comprimentoDoPercurso(inicio, pontos) {
  let total = 0;
  let anterior = inicio;
  for (const p of pontos) {
    total += distanciaPlana(anterior.x, anterior.z, p.x, p.z);
    anterior = p;
  }
  return total;
}

// ---------------------------------------------------------------------------
// ANDAMENTO
// ---------------------------------------------------------------------------

/**
 * Um passo da missão. Devolve eventos para o resto do jogo reagir
 * (som de ponto batido, aviso na tela).
 */
export function atualizarMissao(partida, dt) {
  const missao = partida.missao;
  if (missao.concluida || missao.falhou) return null;
  if (missao.descritor.modo) return andarModo(partida, dt);
  const carro = partida.carro;
  let evento = null;

  missao.tempoRestante = missao.tempoLimite - partida.tempo;
  if (missao.tempoRestante <= 0) {
    return terminar(missao, false, 'O tempo acabou.');
  }
  if (missao.combustivel !== undefined && carro.combustivel <= 0) {
    return terminar(missao, false, 'Acabou o combustível.');
  }
  if (carro.dano >= 1) {
    return terminar(missao, false, 'O carro não aguentou.');
  }

  switch (missao.descritor.tipo) {
    case 'baliza':
    case 'vaga': evento = andarEstacionamento(partida, missao, dt); break;
    case 'entrega':
    case 'economia': evento = andarPontos(partida, missao); break;
    case 'slalom': evento = andarSlalom(partida, missao); break;
    case 'escolta': evento = andarEscolta(partida, missao, dt); break;
    case 'carga': evento = andarCarga(partida, missao, dt); break;
    default: break;
  }

  if (missao.combustivel !== undefined) {
    missao.medidor.valor = limitar(carro.combustivel / missao.combustivel, 0, 1);
  }
  return evento;
}

function andarEstacionamento(partida, missao, dt) {
  const carro = partida.carro;
  const vaga = missao.vagaAlvo;
  const medida = encaixe(carro, vaga);
  missao.medidor.valor = medida.fracao * 0.7 + medida.alinhamento * 0.3;
  missao.progresso = missao.medidor.valor;

  const parado = Math.abs(carro.vx) < 0.22 && Math.abs(carro.giro) < 0.12;
  const dentro = dentroDe(carro, vaga) && medida.alinhamento > 0.55;

  if (dentro && parado) {
    missao.seguro = (missao.seguro || 0) + dt;
    missao.aviso = `segure parado… ${(1.2 - missao.seguro).toFixed(1)}s`;
    if (missao.seguro >= 1.2) {
      missao.qualidade = medida.alinhamento;
      return terminar(missao, true, 'Encaixou.');
    }
  } else {
    missao.seguro = 0;
    missao.aviso = dentro ? 'pare o carro' : null;
  }
  return null;
}

function andarPontos(partida, missao) {
  const carro = partida.carro;
  const ponto = missao.pontos[missao.indice];
  if (!ponto) return null;
  if (distanciaPlana(carro.x, carro.z, ponto.x, ponto.z) < ponto.raio) {
    missao.indice++;
    missao.progresso = missao.indice / missao.pontos.length;
    if (missao.indice >= missao.pontos.length) {
      return terminar(missao, true, 'Entregue.');
    }
    return { tipo: 'ponto', restantes: missao.pontos.length - missao.indice };
  }
  return null;
}

function andarSlalom(partida, missao) {
  const carro = partida.carro;
  const derrubados = missao.cones.filter((c) => c.derrubado).length;
  missao.medidor.valor = 1 - derrubados / missao.cones.length;
  if (derrubados > 2) {
    return terminar(missao, false, `${derrubados} cones no chão.`);
  }

  const objetivo = missao.objetivos[missao.indice];
  if (!objetivo) return null;
  const portao = objetivo.portao;
  const dx = carro.x - portao.x;
  const dz = carro.z - portao.z;
  // "frente" do portão: a direção em que ele deve ser atravessado
  const fx = -Math.sin(portao.angulo), fz = -Math.cos(portao.angulo);
  const adiante = dx * fx + dz * fz;
  const lado = Math.abs(-dx * fz + dz * fx);

  if (adiante > 0 && lado < portao.abertura / 2 + 0.6) {
    missao.indice++;
    missao.progresso = missao.indice / missao.objetivos.length;
    if (missao.indice >= missao.objetivos.length) {
      return terminar(missao, true, 'Slalom limpo.');
    }
    return { tipo: 'ponto', restantes: missao.objetivos.length - missao.indice };
  }
  if (adiante > 6) {
    return terminar(missao, false, 'Passou por fora do portão.');
  }
  return null;
}

function andarEscolta(partida, missao, dt) {
  const carro = partida.carro;
  const alvo = missao.escolta;
  const distancia = distanciaPlana(carro.x, carro.z, alvo.x, alvo.z);
  missao.distanciaEscolta = distancia;
  missao.medidor.valor = limitar(1 - Math.abs(distancia - 16) / 16, 0, 1);

  if (distancia > 46) {
    missao.foraDeVista = (missao.foraDeVista || 0) + dt;
    missao.aviso = 'está ficando para trás';
    if (missao.foraDeVista > 4.5) return terminar(missao, false, 'Perdeu o carro de vista.');
  } else {
    missao.foraDeVista = Math.max(0, (missao.foraDeVista || 0) - dt * 2);
    missao.aviso = distancia < 7 ? 'perto demais' : null;
  }

  missao.progresso = limitar(alvo.distanciaPercorrida / alvo.percursoNecessario, 0, 1);
  if (alvo.distanciaPercorrida >= alvo.percursoNecessario) {
    return terminar(missao, true, 'Escolta cumprida.');
  }
  return null;
}

function andarCarga(partida, missao, dt) {
  const carro = partida.carro;
  const carga = missao.carga;
  const excesso = Math.abs(carro.aceleracaoLateral) - carga.limiteG;
  if (excesso > 0) {
    carga.integridade = Math.max(0, carga.integridade - excesso * dt * 0.55);
    missao.aviso = 'a carga está escorregando';
  } else {
    missao.aviso = null;
  }
  if (carro.forcaImpacto > 0) {
    carga.integridade = Math.max(0, carga.integridade - carro.forcaImpacto * 0.032);
  }
  missao.medidor.valor = carga.integridade;
  if (carga.integridade <= 0) {
    return terminar(missao, false, 'A carga se perdeu.');
  }
  return andarPontos(partida, missao);
}

function terminar(missao, sucesso, motivo) {
  missao.concluida = sucesso;
  missao.falhou = !sucesso;
  missao.motivo = motivo;
  return { tipo: sucesso ? 'vitoria' : 'derrota', motivo };
}

// ---------------------------------------------------------------------------
// NOTA
// ---------------------------------------------------------------------------

/**
 * Três estrelas. Terminar já vale uma; as outras duas vêm de como você
 * terminou: sem bater, com tempo de sobra, com combustível sobrando, com a
 * carga inteira. É o que separa "passou" de "passou bem".
 */
export function avaliarMissao(partida) {
  const missao = partida.missao;
  if (missao.descritor.modo) return avaliarModo(partida);
  const carro = partida.carro;
  const e = partida.estatisticas;

  if (!missao.concluida) {
    return {
      estrelas: 0, premio: 0, sucesso: false, motivo: missao.motivo,
      linhas: [
        { rotulo: 'tempo', valor: formatarTempo(partida.tempo) },
        { rotulo: 'batidas', valor: String(e.batidas) },
      ],
    };
  }

  let estrelas = 1;
  const linhas = [];

  const sobraDeTempo = limitar(1 - partida.tempo / missao.tempoLimite, 0, 1);
  linhas.push({ rotulo: 'tempo', valor: formatarTempo(partida.tempo), bom: sobraDeTempo > 0.3 });

  const limpo = e.batidas === 0;
  linhas.push({ rotulo: 'batidas', valor: String(e.batidas), bom: limpo });

  if (limpo) estrelas++;
  else if (e.batidas <= 2 && e.forcaMaxima < 4) estrelas += 0.5;

  switch (missao.descritor.tipo) {
    case 'baliza':
    case 'vaga': {
      const nota = missao.qualidade || 0;
      linhas.push({ rotulo: 'alinhamento', valor: `${Math.round(nota * 100)}%`, bom: nota > 0.82 });
      if (nota > 0.82) estrelas++;
      else if (nota > 0.65) estrelas += 0.5;
      break;
    }
    case 'economia': {
      const sobra = limitar(carro.combustivel / missao.combustivel, 0, 1);
      linhas.push({ rotulo: 'sobrou no tanque', valor: `${Math.round(sobra * 100)}%`, bom: sobra > 0.22 });
      if (sobra > 0.22) estrelas++;
      else if (sobra > 0.08) estrelas += 0.5;
      break;
    }
    case 'slalom': {
      const derrubados = missao.cones.filter((c) => c.derrubado).length;
      linhas.push({ rotulo: 'cones derrubados', valor: String(derrubados), bom: derrubados === 0 });
      if (derrubados === 0) estrelas++;
      else if (derrubados === 1) estrelas += 0.5;
      break;
    }
    case 'carga': {
      const inteira = missao.carga.integridade;
      linhas.push({ rotulo: 'carga intacta', valor: `${Math.round(inteira * 100)}%`, bom: inteira > 0.8 });
      if (inteira > 0.8) estrelas++;
      else if (inteira > 0.5) estrelas += 0.5;
      break;
    }
    case 'escolta': {
      const nota = limitar(1 - (missao.foraDeVista || 0) / 4.5, 0, 1);
      linhas.push({ rotulo: 'no encalço', valor: `${Math.round(nota * 100)}%`, bom: nota > 0.9 });
      if (nota > 0.9) estrelas++;
      else if (nota > 0.7) estrelas += 0.5;
      break;
    }
    default: {
      if (sobraDeTempo > 0.3) estrelas++;
      else if (sobraDeTempo > 0.12) estrelas += 0.5;
      break;
    }
  }

  estrelas = limitar(Math.floor(estrelas), 1, 3);
  const premio = Math.round(missao.descritor.premio * (0.55 + estrelas * 0.25));

  return { estrelas, premio, sucesso: true, motivo: missao.motivo, linhas };
}

/** Para onde a bússola aponta agora. */
export function alvoAtual(missao) {
  if (missao.descritor.modo) return alvoDoModo(missao);
  switch (missao.descritor.tipo) {
    case 'baliza':
    case 'vaga': return missao.vagaAlvo;
    case 'escolta': return missao.escolta;
    case 'slalom': {
      const o = missao.objetivos[missao.indice];
      return o ? o.portao : null;
    }
    default: return missao.pontos[missao.indice] || null;
  }
}

export { paraKmh, normalizarAngulo, TAU };
