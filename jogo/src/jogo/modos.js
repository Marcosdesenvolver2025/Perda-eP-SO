// Os modos avulsos: estacionamento, rápido e drift.
//
// A carreira é uma fila de serviços com começo, meio e fim. Estes três não são
// isso: são partidas soltas, que acabam quando o relógio acaba, e cuja única
// nota é o número no alto da tela. É o outro jeito de jogar um jogo de dirigir
// de celular — você não está cumprindo tarefa, está tentando bater a si mesmo.
//
// Os três compartilham o mesmo esqueleto de missão (mundo, objetivos, medidor,
// andamento, avaliação), então tudo que já existe — HUD, colisão, trânsito,
// pausa, resultado — funciona sem saber que existe um modo.
//
// O que muda em cada um é a PERGUNTA que ele faz:
//
//   estacionamento  você consegue encaixar, uma vaga atrás da outra, sem
//                   deixar o relógio zerar? Cada vaga bem feita devolve tempo.
//   rápido          quanto chão você cobre antes do relógio zerar? Voltas
//                   devolvem tempo; bater tira.
//   drift           quanto tempo você segura o carro de lado? Pontos só entram
//                   no banco quando você endireita — bater perde o que está
//                   pendente.

import {
  criarSorteio, entre, escolher, limitar, distanciaPlana, normalizarAngulo,
  formatarTempo, TAU,
} from '../nucleo/matematica.js';
import { gerarMundo, naPista } from './mundo.js';
import { LISTA_CLIMAS } from './clima.js';
import { dentroDe, encaixe } from './colisao.js';
import { CARROS } from './carros.js';
import { construirCarro } from '../motor/modelos.js';

export const LISTA_MODOS = ['estacionamento', 'rapido', 'drift'];

export const FICHA_DOS_MODOS = {
  estacionamento: {
    id: 'estacionamento',
    nome: 'Estacionamento',
    resumo: 'Uma vaga atrás da outra. Cada encaixe devolve tempo no relógio.',
    dica: 'Encaixar bem vale mais que encaixar rápido — o alinhamento é o bônus.',
    unidade: 'vagas',
    cor: '#35c46a',
    tempoInicial: 52,
  },
  rapido: {
    id: 'rapido',
    nome: 'Modo rápido',
    resumo: 'Pista fechada, trânsito no caminho. Ande o máximo que der.',
    dica: 'Cada volta completa devolve tempo. Bater tira. A rotatória é atalho e é armadilha.',
    unidade: 'm',
    cor: '#7fd1ff',
    tempoInicial: 58,
  },
  drift: {
    id: 'drift',
    nome: 'Modo drift',
    resumo: 'Atravessado o maior tempo possível. Endireitou, o ponto entra.',
    dica: 'Freio de mão para começar, acelerador para segurar. Endireite antes de bater.',
    unidade: 'pts',
    cor: '#f0902a',
    tempoInicial: 88,
  },
};

// ---------------------------------------------------------------------------
// DESCRITOR
// ---------------------------------------------------------------------------

/** Um modo descrito, sem nada construído ainda. */
export function descritorDeModo(id, semente = Math.floor(Math.random() * 1e9), opcoes = {}) {
  const ficha = FICHA_DOS_MODOS[id];
  const sortear = criarSorteio(semente ^ 0x3d19);

  // Clima e cenário sorteados: é a mesma pista toda vez, mas nunca a mesma luz.
  // Chão molhado fica de fora — num modo de placar, perder a partida por causa
  // de uma poça que você não escolheu é injustiça, não dificuldade.
  const climasBons = LISTA_CLIMAS.filter((c) => !c.molhado);
  const clima = opcoes.clima || escolher(sortear, climasBons.length ? climasBons : LISTA_CLIMAS).id;
  const cenario = opcoes.cenario
    || (id === 'estacionamento' ? escolher(sortear, ['cidade', 'industrial']) : 'cidade');

  return {
    semente,
    modo: id,
    tipo: id,
    cenario,
    clima,
    nivel: 1,
    dificuldade: 0.7,
    transito: id === 'rapido' ? 11 : id === 'drift' ? 0 : 0,
    titulo: ficha.nome,
    resumo: ficha.resumo,
    dica: ficha.dica,
    premio: 0,
    avulso: true,
  };
}

// ---------------------------------------------------------------------------
// MONTAGEM
// ---------------------------------------------------------------------------

export function montarModo(descritor, modeloCarro) {
  const sortear = criarSorteio(descritor.semente ^ 0x51c7);
  const ficha = FICHA_DOS_MODOS[descritor.modo];

  const mundo = descritor.modo === 'estacionamento'
    ? gerarMundo(descritor.semente, {
      cenario: descritor.cenario,
      estacionamento: { larguraVaga: 2.75, comprimentoVaga: 5.4 },
      metros: 152,
    })
    : gerarMundo(descritor.semente, {
      cenario: descritor.cenario,
      tracado: 'circuito',
      metros: 300,
    });

  const missao = {
    descritor,
    mundo,
    objetivos: [],
    indice: 0,
    pontos: [],
    aviso: null,
    instrucao: '',
    progresso: 0,
    medidor: null,
    escolta: null,
    concluida: false,
    falhou: false,
    motivo: '',
    arcade: true,
    pontuacao: 0,
    contador: 0,          // vagas, voltas — o número que a pessoa acompanha
    unidade: ficha.unidade,
    tempoLimite: ficha.tempoInicial,
    tempoGanho: 0,
  };

  if (descritor.modo === 'estacionamento') montarEstacionamentoArcade(missao, sortear, modeloCarro);
  else if (descritor.modo === 'rapido') montarRapido(missao, sortear);
  else montarDrift(missao, sortear);

  return missao;
}

// --- estacionamento --------------------------------------------------------

function montarEstacionamentoArcade(missao, sortear, modeloCarro) {
  const mundo = missao.mundo;
  if (!mundo.vagas.length) { montarRapido(missao, sortear); return; }

  // Toda vaga cabe no carro que a pessoa escolheu — senão o modo seria
  // impossível justamente com o carro grande, que é o que ela quis comprar.
  for (const vaga of mundo.vagas) {
    vaga.largura = Math.max(modeloCarro.ficha.largura + 0.62, vaga.largura);
    vaga.comprimento = Math.max(modeloCarro.ficha.comprimento + 0.85, vaga.comprimento);
  }

  // Metade das vagas ocupada por carro parado: é o que aperta a manobra e o
  // que dá ao pátio a cara de estacionamento de shopping em vez de quadra vazia.
  for (const vaga of mundo.vagas) {
    if (sortear() > 0.5) continue;
    const modelo = escolher(sortear, CARROS);
    if (modelo.ficha.comprimento > vaga.comprimento - 0.3) continue;
    encherVaga(mundo, vaga, modelo, sortear);
  }

  const livres = mundo.vagas.filter((v) => !v.ocupada);
  missao.fila = embaralharVagas(sortear, livres);
  missao.indice = 0;
  missao.medidor = { rotulo: 'encaixe', valor: 0 };
  apontarProximaVaga(missao);

  // Começa no meio do corredor do pátio, olhando para dentro dele.
  const q = mundo.patio;
  mundo.inicio = {
    x: q.centroX, z: q.centroZ + q.profundidade * 0.34, angulo: 0,
  };
}

function encherVaga(mundo, vaga, modelo, sortear) {
  const cor = escolher(sortear, [0x3f4f63, 0xb03a2e, 0xe8e6e1, 0x4b5d3a, 0xd6a12c, 0x2e6fb7]);
  const peca = construirCarro(modelo, cor);
  const guinada = vaga.angulo + (sortear() > 0.85 ? entre(sortear, -0.13, 0.13) : 0);
  vaga.ocupada = true;
  mundo.props.push({
    tipo: 'carro-parado', malha: peca.carroceria,
    x: vaga.x, z: vaga.z, guinada, raio: modelo.ficha.comprimento * 0.6,
  });
  for (const roda of peca.rodas) {
    const cos = Math.cos(guinada), sen = Math.sin(guinada);
    mundo.props.push({
      tipo: 'roda-parada', malha: peca.roda,
      x: vaga.x + cos * roda.x + sen * roda.z, y: peca.raioRoda,
      z: vaga.z - sen * roda.x + cos * roda.z, guinada, raio: 0.6,
    });
  }
  mundo.colisores.push({
    x: vaga.x, z: vaga.z, guinada,
    largura: modelo.ficha.largura, comprimento: modelo.ficha.comprimento,
    solido: true, altura: 1.5, carroParado: true,
  });
}

/** Ordena as vagas de modo que a próxima nunca seja a vizinha da anterior. */
function embaralharVagas(sortear, vagas) {
  const restantes = vagas.slice();
  const fila = [];
  let anterior = null;
  while (restantes.length) {
    let melhor = 0;
    let melhorNota = -Infinity;
    for (let i = 0; i < restantes.length; i++) {
      const v = restantes[i];
      const longe = anterior ? distanciaPlana(v.x, v.z, anterior.x, anterior.z) : 0;
      const nota = longe + sortear() * 6;
      if (nota > melhorNota) { melhorNota = nota; melhor = i; }
    }
    anterior = restantes[melhor];
    fila.push(anterior);
    restantes.splice(melhor, 1);
  }
  return fila;
}

function apontarProximaVaga(missao) {
  for (const vaga of missao.mundo.vagas) vaga.alvo = false;
  const vaga = missao.fila[missao.indice % missao.fila.length];
  if (!vaga) return;
  vaga.alvo = true;
  missao.vagaAlvo = vaga;
  missao.seguro = 0;
}

// --- rápido ----------------------------------------------------------------

function montarRapido(missao) {
  const mundo = missao.mundo;
  missao.pista = mundo.pista;
  missao.volta = { s: 0, meiaVolta: false, tempo: 0, melhor: null };
  missao.medidor = { rotulo: 'tempo da volta', valor: 1 };
  missao.instrucao = 'Volta 1 · ande o máximo que der';
}

// --- drift -----------------------------------------------------------------

function montarDrift(missao) {
  missao.pista = missao.mundo.pista;
  missao.drift = {
    pendente: 0,       // pontos do drift em curso, ainda não bancados
    multiplicador: 1,
    duracao: 0,        // há quanto tempo está atravessado
    folga: 0,          // quanto tempo já está endireitado (perdoa o retoque)
    melhor: 0,
    ativo: false,
  };
  missao.medidor = { rotulo: 'ângulo', valor: 0 };
  missao.instrucao = 'Freio de mão e acelerador. Atravesse.';
}

// ---------------------------------------------------------------------------
// ANDAMENTO
// ---------------------------------------------------------------------------

/**
 * Um passo de um modo. Devolve o mesmo tipo de evento que a missão comum, para
 * o principal.js não precisar saber a diferença.
 *
 * O relógio é tratado AQUI, e não pela regra geral, porque num modo avulso o
 * tempo acabar não é fracasso: é o fim da partida, e a partida tem nota.
 */
export function andarModo(partida, dt) {
  const missao = partida.missao;
  const carro = partida.carro;

  missao.tempoRestante = missao.tempoLimite - partida.tempo;
  if (missao.tempoRestante <= 0) return fechar(missao, 'Acabou o tempo.');
  if (carro.dano >= 1) return fechar(missao, 'O carro não aguentou.');

  switch (missao.descritor.modo) {
    case 'estacionamento': return andarEstacionamentoArcade(partida, missao, dt);
    case 'rapido': return andarRapido(partida, missao, dt);
    case 'drift': return andarDrift(partida, missao, dt);
    default: return null;
  }
}

/** O fim de uma partida de modo: sempre com nota, nunca com derrota seca. */
function fechar(missao, motivo) {
  // Zero ponto é derrota: não dá para premiar quem não fez nada. Um ponto que
  // seja já é partida jogada, e partida jogada termina em placar.
  const venceu = missao.pontuacao > 0;
  missao.concluida = venceu;
  missao.falhou = !venceu;
  missao.motivo = motivo;
  return { tipo: venceu ? 'vitoria' : 'derrota', motivo };
}

// --- estacionamento --------------------------------------------------------

function andarEstacionamentoArcade(partida, missao, dt) {
  const carro = partida.carro;
  const vaga = missao.vagaAlvo;
  if (!vaga) return null;

  const medida = encaixe(carro, vaga);
  missao.medidor.valor = medida.fracao * 0.7 + medida.alinhamento * 0.3;
  missao.progresso = limitar(missao.indice / Math.max(1, missao.fila.length), 0, 1);

  // De longe o que interessa é onde a vaga está; de perto, o quanto já entrou.
  // Mostrar "30% encaixado" a trinta metros da vaga é informação que mente.
  const distancia = distanciaPlana(carro.x, carro.z, vaga.x, vaga.z);
  missao.instrucao = distancia > 9
    ? `Vaga ${missao.contador + 1} · ${Math.round(distancia)} m`
    : `Vaga ${missao.contador + 1} · ${Math.round(medida.fracao * 100)}% dentro`;

  const parado = Math.abs(carro.vx) < 0.22 && Math.abs(carro.giro) < 0.12;
  const dentro = dentroDe(carro, vaga) && medida.alinhamento > 0.55;

  if (!(dentro && parado)) {
    missao.seguro = 0;
    missao.aviso = dentro ? 'pare o carro' : null;
    return null;
  }

  // Segurar parado é mais curto que na carreira: o modo é de repetição, e
  // esperar um segundo e meio doze vezes seguidas vira espera, não jogo.
  missao.seguro = (missao.seguro || 0) + dt;
  missao.aviso = `segure parado… ${Math.max(0, 0.7 - missao.seguro).toFixed(1)}s`;
  if (missao.seguro < 0.7) return null;

  // Encaixou. O prêmio é em pontos E em tempo — é o tempo que mantém a partida
  // viva, então encaixar bem é literalmente jogar por mais tempo.
  const nota = medida.alinhamento;
  const ganho = Math.round(600 + nota * 900 + missao.contador * 90);
  const tempo = 7 + nota * 5;
  missao.pontuacao += ganho;
  missao.tempoLimite += tempo;
  missao.tempoGanho += tempo;
  missao.contador++;
  missao.melhorAlinhamento = Math.max(missao.melhorAlinhamento || 0, nota);
  missao.somaAlinhamento = (missao.somaAlinhamento || 0) + nota;

  vaga.alvo = false;
  vaga.concluida = true;
  missao.indice++;
  missao.aviso = null;

  if (missao.indice >= missao.fila.length) {
    return fechar(missao, `Pátio inteiro: ${missao.contador} vagas.`);
  }
  apontarProximaVaga(missao);
  return { tipo: 'marco', texto: `+${ganho} · +${Math.round(tempo)}s`, cor: '#35c46a', ganho };
}

// --- rápido ----------------------------------------------------------------

function andarRapido(partida, missao, dt) {
  const carro = partida.carro;
  const pista = missao.pista;
  const v = missao.volta;
  v.tempo += dt;

  // Pontos por chão coberto: é a métrica honesta do modo. Andar rápido rende
  // mais porque em 60 segundos se cobre mais chão, não porque o número da
  // velocidade entra na conta duas vezes.
  const avanco = Math.hypot(carro.vx, carro.vy) * dt;
  missao.pontuacao += avanco;
  missao.distancia = (missao.distancia || 0) + avanco;

  if (pista) {
    // Em que METRO da volta o carro está. Ângulo em volta do centro só serve
    // em pista redonda; num traçado com reta e grampo, o carro passa duas
    // vezes pelo mesmo ângulo e a volta contava errado.
    const onde = naPista(pista, carro.x, carro.z);
    const s = onde.s;
    const dentro = onde.distancia <= pista.largura / 2 + 3;
    const anterior = v.s;
    v.s = s;

    // Cruzar a linha é o metro voltar do fim da volta para o começo, e só
    // depois de ter passado do meio — senão balançar em cima da linha contaria
    // volta atrás de volta.
    if (s > pista.comprimento * 0.45 && s < pista.comprimento * 0.92) v.meiaVolta = true;
    const cruzou = anterior > pista.comprimento * 0.80 && s < pista.comprimento * 0.20;

    if (cruzou && v.meiaVolta && dentro) {
      v.meiaVolta = false;
      missao.contador++;
      const tempoDaVolta = v.tempo;
      v.tempo = 0;
      if (v.melhor === null || tempoDaVolta < v.melhor) v.melhor = tempoDaVolta;

      // A devolução de tempo encolhe a cada volta: a partida tem que acabar.
      const devolve = Math.max(7, 17 - missao.contador * 1.6);
      const bonus = Math.round(900 + Math.max(0, 40 - tempoDaVolta) * 45);
      missao.tempoLimite += devolve;
      missao.tempoGanho += devolve;
      missao.pontuacao += bonus;
      return {
        tipo: 'marco',
        texto: `VOLTA ${missao.contador} · ${formatarTempo(tempoDaVolta)} · +${Math.round(devolve)}s`,
        cor: '#7fd1ff', ganho: bonus,
      };
    }

    missao.medidor.valor = v.melhor ? limitar(v.melhor / Math.max(v.tempo, 0.1), 0, 1) : 0.5;
    missao.progresso = limitar(s / pista.comprimento, 0, 1);
  }

  const kmh = Math.abs(carro.vx) * 3.6;
  missao.instrucao = v.melhor
    ? `Volta ${missao.contador + 1} · melhor ${formatarTempo(v.melhor)}`
    : `Volta ${missao.contador + 1} · ${Math.round(kmh)} km/h`;

  // Bater custa tempo. É a única penalidade do modo, e é a que importa:
  // atravessar o trânsito no talo deixa de ser grátis.
  //
  // Mas a cobrança tem carência. Encostado no guarda-corpo o carro produz um
  // impacto por QUADRO, e sem carência raspar a mureta por dois segundos
  // custava a partida inteira — castigo que não ensina nada, porque quem está
  // raspando já está pagando em velocidade.
  missao.carencia = Math.max(0, (missao.carencia || 0) - dt);
  if (carro.forcaImpacto > 1.2 && missao.carencia <= 0) {
    const perda = limitar(carro.forcaImpacto * 0.30, 0.5, 2.5);
    missao.carencia = 1.2;
    missao.tempoLimite -= perda;
    missao.aviso = `−${perda.toFixed(1)}s`;
    return { tipo: 'aviso', texto: `−${perda.toFixed(1)}s`, cor: '#e8563a' };
  }
  missao.aviso = null;
  return null;
}

// --- drift -----------------------------------------------------------------

const ANGULO_MINIMO = 0.14;    // rad — abaixo disso é curva, não drift
const VELOCIDADE_MINIMA = 5.5; // m/s

function andarDrift(partida, missao, dt) {
  const carro = partida.carro;
  const d = missao.drift;

  // O ângulo de drift é o ângulo entre para onde o carro APONTA e para onde
  // ele está indo de verdade. A física já guarda a velocidade no referencial
  // do carro, então é só o arco-tangente entre lateral e frontal.
  const velocidade = Math.hypot(carro.vx, carro.vy);
  const angulo = velocidade > 0.5 ? Math.abs(Math.atan2(carro.vy, Math.abs(carro.vx))) : 0;
  const noChao = partida.piso === 'asfalto' || partida.piso === 'calcada';
  const valendo = angulo > ANGULO_MINIMO && velocidade > VELOCIDADE_MINIMA
    && noChao && Math.abs(carro.vx) > 0;

  missao.medidor.valor = limitar(angulo / 0.85, 0, 1);

  // Bater derruba o que está pendente. Não zera o placar — o que já foi
  // bancado é seu — mas o drift em curso morre ali.
  if (carro.forcaImpacto > 1.2 && d.pendente > 0) {
    const perdido = Math.round(d.pendente * d.multiplicador);
    d.pendente = 0;
    d.multiplicador = 1;
    d.duracao = 0;
    d.ativo = false;
    missao.aviso = null;
    return { tipo: 'aviso', texto: `BATEU · −${perdido}`, cor: '#e8563a' };
  }

  if (valendo) {
    d.folga = 0;
    d.ativo = true;
    d.duracao += dt;
    // Ângulo alto e velocidade alta rendem mais. Passar de ~50° é rodar, e
    // rodar não rende: a conta cai de propósito depois do ponto ideal.
    const qualidade = Math.sin(limitar(angulo / 0.9, 0, 1) * Math.PI * 0.92);
    d.pendente += velocidade * qualidade * dt * 9;
    d.multiplicador = limitar(1 + Math.floor(d.duracao / 1.8), 1, 8);
    d.melhor = Math.max(d.melhor, d.duracao);
    missao.instrucao = `${Math.round(d.pendente * d.multiplicador)} pts · ×${d.multiplicador}`
      + ` · ${Math.round(angulo * 57)}°`;
    missao.aviso = null;
    return null;
  }

  if (!d.ativo) {
    missao.instrucao = missao.contador
      ? `${Math.round(missao.pontuacao)} pts · ${missao.contador} drifts`
      : 'Freio de mão e acelerador. Atravesse.';
    return null;
  }

  // Endireitou. Uma folga curta perdoa o retoque de volante no meio do drift —
  // sem ela, um drift longo vira três curtos e o multiplicador nunca sobe.
  d.folga += dt;
  missao.instrucao = `soltando… ${Math.round(d.pendente * d.multiplicador)} pts`;
  if (d.folga < 0.55) return null;

  const ganho = Math.round(d.pendente * d.multiplicador);
  d.ativo = false;
  d.pendente = 0;
  d.duracao = 0;
  const multiplicador = d.multiplicador;
  d.multiplicador = 1;
  if (ganho < 15) return null;

  missao.pontuacao += ganho;
  missao.contador++;
  return { tipo: 'marco', texto: `+${ganho} ×${multiplicador}`, cor: '#f0902a', ganho };
}

// ---------------------------------------------------------------------------
// NOTA
// ---------------------------------------------------------------------------

/**
 * As estrelas de um modo saem do placar, não de ter cumprido tarefa: são as
 * marcas de bronze, prata e ouro. Quem quiser três estrelas volta e joga de novo,
 * que é o ponto inteiro de um modo avulso existir.
 */
const MARCAS = {
  estacionamento: [1200, 4500, 9000],
  rapido: [900, 2600, 5200],
  drift: [1500, 6000, 14000],
};

export function avaliarModo(partida) {
  const missao = partida.missao;
  const modo = FICHA_DOS_MODOS[missao.descritor.modo];
  const pontos = Math.round(missao.pontuacao);
  const marcas = MARCAS[missao.descritor.modo];

  let estrelas = 0;
  for (const marca of marcas) if (pontos >= marca) estrelas++;

  const linhas = [{ rotulo: 'pontos', valor: String(pontos), bom: estrelas > 0 }];

  switch (missao.descritor.modo) {
    case 'estacionamento': {
      const media = missao.contador ? (missao.somaAlinhamento || 0) / missao.contador : 0;
      linhas.push({ rotulo: 'vagas', valor: String(missao.contador), bom: missao.contador >= 5 });
      linhas.push({ rotulo: 'alinhamento médio', valor: `${Math.round(media * 100)}%`, bom: media > 0.8 });
      break;
    }
    case 'rapido': {
      linhas.push({ rotulo: 'voltas', valor: String(missao.contador), bom: missao.contador >= 3 });
      linhas.push({
        rotulo: 'melhor volta',
        valor: missao.volta.melhor ? formatarTempo(missao.volta.melhor) : '—',
        bom: !!missao.volta.melhor && missao.volta.melhor < 30,
      });
      linhas.push({ rotulo: 'distância', valor: `${Math.round(missao.distancia || 0)} m`, bom: false });
      break;
    }
    default: {
      linhas.push({ rotulo: 'drifts', valor: String(missao.contador), bom: missao.contador >= 6 });
      linhas.push({
        rotulo: 'mais longo',
        valor: `${(missao.drift.melhor || 0).toFixed(1)}s`,
        bom: (missao.drift.melhor || 0) > 6,
      });
      break;
    }
  }

  linhas.push({ rotulo: 'batidas', valor: String(partida.estatisticas.batidas), bom: partida.estatisticas.batidas === 0 });
  linhas.push({ rotulo: 'tempo ganho', valor: `${Math.round(missao.tempoGanho)}s`, bom: missao.tempoGanho > 20 });

  // O pagamento é uma fração do placar: os modos alimentam a garagem, e é por
  // isso que vale a pena voltar neles depois de a carreira travar num carro caro.
  const premio = Math.round(pontos / 8);

  return {
    estrelas, premio, pontos, sucesso: missao.concluida,
    motivo: missao.motivo || `${pontos} ${modo.unidade === 'm' ? 'pontos' : modo.unidade}`,
    modo: missao.descritor.modo,
    linhas,
  };
}

/**
 * Para onde a bússola aponta num modo.
 *
 * Só o estacionamento tem para onde apontar: no anel o caminho é a própria
 * pista, e uma seta dizendo "a linha de chegada é ali" quando ela está a seis
 * metros não ajuda ninguém — atrapalha, porque tapa a curva.
 */
export function alvoDoModo(missao) {
  if (missao.descritor.modo === 'estacionamento') return missao.vagaAlvo || null;
  return null;
}
