// Comandos.
//
// Três jeitos de dirigir o mesmo carro: teclado, dedo na tela e controle.
// Todos desembocam no mesmo lugar — um objeto com acelerador, freio, volante,
// freio de mão e marcha — e é só isso que a física enxerga.
//
// O volante da tela não é um botão de esquerda/direita: ele GIRA. Você põe o
// dedo nele e roda, e o quanto você rodou vira o quanto as rodas viram. É o
// controle principal do jogo, e é diferente em cada carro.

import { limitar, TAU } from './matematica.js';

// Quanto o aro precisa girar na tela para as rodas irem ao batente.
//
// Estava em 2,30 rad — 132°, mais de um terço de volta. Num celular, com o
// volante no canto e o polegar preso, ninguém gira isso: a pessoa rodava o que
// dava, o carro virava pouco, e a sensação era de volante quebrado. 0,95 rad
// são 54°, um giro que cabe num arrasto de polegar e leva a roda ao fim.
const GIRO_MAXIMO = 0.95;

// Quão rápido o aro volta ao centro quando o dedo sai. Em voltas por segundo
// do próprio curso: 2,6 quer dizer que ele atravessa o curso inteiro em pouco
// menos de meio segundo, que é o que um volante de verdade faz sozinho.
const RETORNO_POR_SEGUNDO = 2.6;

export class Entrada {
  constructor(tela) {
    this.tela = tela;
    this.teclas = new Set();
    this.comandos = {
      acelerador: 0, freio: 0, volante: 0, mao: false, sentido: 1,
      neutro: false, cambio: 'automatico',
    };
    // A alavanca P R N D, como a dos jogos de dirigir de celular: uma coluna
    // de letras em que só uma está acesa. É ela que manda o sentido; o botão
    // único de D/R virou uma alavanca de verdade.
    this.cambioPosicao = 'D';
    // Trocar de marcha é um ATO, não um estado: vale uma vez, não a cada
    // passo de física. Fica guardado aqui até alguém consumir.
    this.trocaPendente = 0;
    this.volanteVisual = 0;     // ângulo desenhado, em radianos
    this.volanteAlvo = 0;
    this.areaVolante = { x: 0, y: 0, raio: 80 };
    this.areaAcelerador = null;
    this.areaFreio = null;
    this.areaMao = null;
    this.areaSobeMarcha = null;
    this.areaDesceMarcha = null;
    this.areaCamera = null;
    this.areaPausa = null;
    this.areasCambio = { P: null, R: null, N: null, D: null };

    this.ponteiros = new Map();
    this.arrasto = null;
    this.pedeCamera = false;
    this.pedePausa = false;
    this.olharAtras = false;
    this.tempoFreioParado = 0;

    this.ligar();
  }

  ligar() {
    const tela = this.tela;

    window.addEventListener('keydown', (e) => {
      if (e.repeat) return;
      const k = e.key.toLowerCase();
      this.teclas.add(k);
      if (k === 'c') this.pedeCamera = true;
      if (k === 'escape' || k === 'p') this.pedePausa = true;
      if (k === 'x' || k === 'e') this.trocaPendente = 1;
      if (k === 'z' || k === 'q') this.trocaPendente = -1;
      if ([' ', 'arrowup', 'arrowdown', 'arrowleft', 'arrowright'].includes(k)) e.preventDefault();
    });
    window.addEventListener('keyup', (e) => this.teclas.delete(e.key.toLowerCase()));
    window.addEventListener('blur', () => this.teclas.clear());

    const posicao = (e) => {
      const r = tela.getBoundingClientRect();
      return {
        x: (e.clientX - r.left) * (tela.width / r.width),
        y: (e.clientY - r.top) * (tela.height / r.height),
      };
    };

    tela.addEventListener('pointerdown', (e) => {
      tela.setPointerCapture(e.pointerId);
      const p = posicao(e);
      const alvo = this.ondeCaiu(p);
      this.ponteiros.set(e.pointerId, { ...p, alvo });
      if (alvo === 'volante') {
        this.arrasto = {
          id: e.pointerId,
          anguloInicial: Math.atan2(p.y - this.areaVolante.y, p.x - this.areaVolante.x),
          giroInicial: this.volanteAlvo,
          // Onde o dedo encostou, para o modo de arrasto lateral.
          xInicial: p.x,
          // Encostar bem no meio do aro dá um ângulo instável: um tremor de
          // dedo vira meia volta. Perto do cubo, portanto, o volante passa a
          // obedecer ao arrasto HORIZONTAL, que é o gesto que a pessoa faz
          // sem pensar.
          pelaLateral: Math.hypot(p.x - this.areaVolante.x, p.y - this.areaVolante.y)
            < this.areaVolante.raio * 0.42,
        };
      }
      if (alvo && alvo.startsWith('cambio:')) this.porAlavancaEm(alvo.slice(7), this.carroAtual);
      if (alvo === 'sobe-marcha') this.trocaPendente = 1;
      if (alvo === 'desce-marcha') this.trocaPendente = -1;
      if (alvo === 'camera') this.pedeCamera = true;
      if (alvo === 'pausa') this.pedePausa = true;
      e.preventDefault();
    });

    tela.addEventListener('pointermove', (e) => {
      const registro = this.ponteiros.get(e.pointerId);
      if (!registro) return;
      const p = posicao(e);
      registro.x = p.x; registro.y = p.y;
      if (this.arrasto && this.arrasto.id === e.pointerId) {
        let delta;
        if (this.arrasto.pelaLateral) {
          // Um curso de tela igual ao raio do aro leva ao batente.
          delta = ((p.x - this.arrasto.xInicial) / this.areaVolante.raio) * GIRO_MAXIMO;
        } else {
          const angulo = Math.atan2(p.y - this.areaVolante.y, p.x - this.areaVolante.x);
          delta = angulo - this.arrasto.anguloInicial;
          while (delta > Math.PI) delta -= TAU;
          while (delta < -Math.PI) delta += TAU;
        }
        this.volanteAlvo = limitar(this.arrasto.giroInicial + delta, -GIRO_MAXIMO, GIRO_MAXIMO);
      }
    });

    const soltar = (e) => {
      if (this.arrasto && this.arrasto.id === e.pointerId) this.arrasto = null;
      this.ponteiros.delete(e.pointerId);
    };
    tela.addEventListener('pointerup', soltar);
    tela.addEventListener('pointercancel', soltar);
    tela.addEventListener('contextmenu', (e) => e.preventDefault());
  }

  ondeCaiu(p) {
    const dentro = (area) => area
      && p.x >= area.x && p.x <= area.x + area.largura
      && p.y >= area.y && p.y <= area.y + area.altura;

    // A alavanca vem ANTES do volante: a coluna P R N D fica encostada nele, e
    // o raio generoso do volante engolia o toque no D se viesse depois.
    for (const letra of ['P', 'R', 'N', 'D']) {
      if (dentro(this.areasCambio[letra])) return `cambio:${letra}`;
    }
    const dv = Math.hypot(p.x - this.areaVolante.x, p.y - this.areaVolante.y);
    if (dv <= this.areaVolante.raio * 1.35) return 'volante';
    if (dentro(this.areaAcelerador)) return 'acelerador';
    if (dentro(this.areaFreio)) return 'freio';
    if (dentro(this.areaSobeMarcha)) return 'sobe-marcha';
    if (dentro(this.areaDesceMarcha)) return 'desce-marcha';
    if (dentro(this.areaMao)) return 'mao';
    if (dentro(this.areaCamera)) return 'camera';
    if (dentro(this.areaPausa)) return 'pausa';
    return null;
  }

  tocando(alvo) {
    for (const registro of this.ponteiros.values()) {
      if (registro.alvo === alvo) return true;
    }
    return false;
  }

  /**
   * Põe a alavanca numa posição.
   *
   * P e N não mandam força nenhuma para as rodas; P ainda trava o carro. R e D
   * são o sentido. Trocar para R ou D andando para o outro lado é coisa que
   * quebra câmbio na vida real e aqui só é ignorado — do contrário a pessoa
   * encosta sem querer no D a 80 por hora e o carro dá um tranco.
   */
  porAlavancaEm(posicao, carro) {
    if (!['P', 'R', 'N', 'D'].includes(posicao)) return false;
    const andando = carro ? Math.abs(carro.vx) > 2.2 : false;
    if (andando && (posicao === 'R' || posicao === 'P')) return false;
    this.cambioPosicao = posicao;
    return true;
  }

  /** Devolve -1, 0 ou +1 e esquece — quem chama é quem aplica. */
  consumirTrocaDeMarcha() {
    const v = this.trocaPendente;
    this.trocaPendente = 0;
    return v;
  }

  /** Junta teclado e dedo e entrega um só conjunto de comandos. */
  atualizar(dt, carro) {
    const c = this.comandos;
    const t = this.teclas;
    // Guardado para o toque na alavanca poder consultar a velocidade: o
    // `pointerdown` não recebe o carro.
    this.carroAtual = carro;

    const teclaFrente = t.has('arrowup') || t.has('w');
    const teclaTras = t.has('arrowdown') || t.has('s');
    const teclaEsquerda = t.has('arrowleft') || t.has('a');
    const teclaDireita = t.has('arrowright') || t.has('d');
    this.olharAtras = t.has('shift');

    // Volante: o dedo manda; sem dedo, o teclado gira o mesmo volante.
    if (!this.arrasto) {
      const alvoTeclado = (teclaDireita ? 1 : 0) - (teclaEsquerda ? 1 : 0);
      if (alvoTeclado !== 0) {
        this.volanteAlvo = limitar(this.volanteAlvo + alvoTeclado * 5.2 * dt, -GIRO_MAXIMO, GIRO_MAXIMO);
      } else {
        // Dedo fora do aro: ele volta ao centro sozinho, como um volante de
        // verdade. A volta é em fração do CURSO, não em radianos soltos —
        // assim ela continua a mesma se o curso mudar. Andando, volta mais
        // rápido, que é o auto-alinhamento do carro em movimento.
        const retorno = GIRO_MAXIMO
          * (RETORNO_POR_SEGUNDO + Math.abs(carro ? carro.vx : 0) * 0.14) * dt;
        this.volanteAlvo -= limitar(this.volanteAlvo, -retorno, retorno);
        if (Math.abs(this.volanteAlvo) < 0.004) this.volanteAlvo = 0;
      }
    }
    this.volanteVisual += (this.volanteAlvo - this.volanteVisual) * Math.min(1, dt * 18);
    // Positivo = direita, que é para onde o aro girou na tela (o y da tela
    // aponta para baixo, então ângulo crescente é sentido horário). Quem
    // converte isso em esterço é a física.
    c.volante = limitar(this.volanteAlvo / GIRO_MAXIMO, -1, 1);

    const acelerando = teclaFrente || this.tocando('acelerador');
    const freando = teclaTras || this.tocando('freio');
    c.acelerador = acelerando ? 1 : 0;
    c.freio = freando ? 1 : 0;

    // Troca automática de sentido: parado com o freio afundado, engata a ré;
    // parado na ré com o pé no acelerador, volta para frente. A alavanca anda
    // junto, senão a coluna P R N D mostra D com o carro indo para trás.
    const parado = !carro || Math.abs(carro.vx) < 0.4;
    if (parado && freando && !acelerando) {
      this.tempoFreioParado += dt;
      if (this.tempoFreioParado > 0.45 && this.cambioPosicao === 'D') {
        this.cambioPosicao = 'R';
        this.tempoFreioParado = -1.2;
      }
    } else {
      this.tempoFreioParado = 0;
    }
    if (parado && acelerando && this.cambioPosicao === 'R' && !freando) {
      this.cambioPosicao = 'D';
    }
    // Sair do P ou do N basta pisar no acelerador: ninguém quer descobrir
    // sozinho que o carro não anda porque a alavanca ficou no N.
    if (acelerando && (this.cambioPosicao === 'P' || this.cambioPosicao === 'N')) {
      this.cambioPosicao = 'D';
    }

    c.sentido = this.cambioPosicao === 'R' ? -1 : 1;
    c.neutro = this.cambioPosicao === 'N' || this.cambioPosicao === 'P';
    c.mao = t.has(' ') || this.tocando('mao') || this.cambioPosicao === 'P';

    // Na ré quem empurra continua sendo o acelerador — quem inverte o sentido
    // da força é a física, olhando `sentido`. Aqui nada muda.
    return c;
  }

  consumirCamera() {
    const v = this.pedeCamera;
    this.pedeCamera = false;
    return v;
  }

  consumirPausa() {
    const v = this.pedePausa;
    this.pedePausa = false;
    return v;
  }

  zerar() {
    this.volanteAlvo = 0;
    this.volanteVisual = 0;
    this.comandos.acelerador = 0;
    this.comandos.freio = 0;
    this.comandos.volante = 0;
    this.comandos.sentido = 1;
    this.comandos.neutro = false;
    this.cambioPosicao = 'D';
    this.trocaPendente = 0;
    this.ponteiros.clear();
    this.arrasto = null;
  }
}

export { GIRO_MAXIMO };
