// O som.
//
// Não há arquivo de áudio nenhum no jogo: tudo é sintetizado na hora. O motor
// são dois osciladores dente-de-serra afinados pela rotação, passando por um
// filtro que abre conforme o acelerador; o pneu é ruído branco num passa-faixa
// estreito; a batida é um estouro curto de ruído.
//
// O navegador só deixa tocar depois de um toque ou uma tecla — por isso nada é
// criado no carregamento, só quando `despertar()` é chamado.

import { limitar, misturar } from '../nucleo/matematica.js';

export class Som {
  constructor() {
    this.ctx = null;
    this.ligado = true;
    this.pronto = false;
    this.volume = 0.7;
  }

  despertar() {
    if (this.ctx || !this.ligado) return;
    const Contexto = window.AudioContext || window.webkitAudioContext;
    if (!Contexto) { this.ligado = false; return; }
    this.ctx = new Contexto();
    this.mestre = this.ctx.createGain();
    this.mestre.gain.value = this.volume;
    this.mestre.connect(this.ctx.destination);
    this.montarMotor();
    this.montarPneus();
    this.montarChuva();
    this.pronto = true;
  }

  retomar() {
    if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume();
  }

  montarMotor() {
    const ctx = this.ctx;
    this.motor = {
      ganho: ctx.createGain(),
      filtro: ctx.createBiquadFilter(),
      osciladores: [],
      ganhos: [],
    };
    this.motor.ganho.gain.value = 0;
    this.motor.filtro.type = 'lowpass';
    this.motor.filtro.frequency.value = 700;
    this.motor.filtro.Q.value = 1.4;

    // Três harmônicas: a fundamental dá o corpo, as outras dão o ronco.
    for (const [tipo, multiplicador, nivel] of [
      ['sawtooth', 0.5, 0.55], ['square', 1, 0.30], ['sawtooth', 2, 0.14],
    ]) {
      const osc = ctx.createOscillator();
      osc.type = tipo;
      const g = ctx.createGain();
      g.gain.value = nivel;
      osc.connect(g).connect(this.motor.filtro);
      osc.start();
      this.motor.osciladores.push({ osc, multiplicador });
      this.motor.ganhos.push(g);
    }
    this.motor.filtro.connect(this.motor.ganho).connect(this.mestre);
  }

  montarPneus() {
    const ctx = this.ctx;
    const ruido = ctx.createBufferSource();
    ruido.buffer = this.bufferDeRuido(2);
    ruido.loop = true;
    const filtro = ctx.createBiquadFilter();
    filtro.type = 'bandpass';
    filtro.frequency.value = 1500;
    filtro.Q.value = 5.5;
    const ganho = ctx.createGain();
    ganho.gain.value = 0;
    ruido.connect(filtro).connect(ganho).connect(this.mestre);
    ruido.start();
    this.pneus = { ganho, filtro };
  }

  montarChuva() {
    const ctx = this.ctx;
    const ruido = ctx.createBufferSource();
    ruido.buffer = this.bufferDeRuido(3);
    ruido.loop = true;
    const filtro = ctx.createBiquadFilter();
    filtro.type = 'highpass';
    filtro.frequency.value = 2400;
    const ganho = ctx.createGain();
    ganho.gain.value = 0;
    ruido.connect(filtro).connect(ganho).connect(this.mestre);
    ruido.start();
    this.chuva = { ganho };
  }

  bufferDeRuido(segundos) {
    const taxa = this.ctx.sampleRate;
    const buffer = this.ctx.createBuffer(1, taxa * segundos, taxa);
    const dados = buffer.getChannelData(0);
    for (let i = 0; i < dados.length; i++) dados[i] = Math.random() * 2 - 1;
    return buffer;
  }

  /** Chamado a cada quadro com o estado do carro. */
  atualizar(carro, ambiente, dentroDoJogo) {
    if (!this.pronto) return;
    const agora = this.ctx.currentTime;
    const suave = 0.06;

    if (!dentroDoJogo) {
      this.motor.ganho.gain.setTargetAtTime(0, agora, 0.1);
      this.pneus.ganho.gain.setTargetAtTime(0, agora, 0.1);
      this.chuva.ganho.gain.setTargetAtTime(0, agora, 0.2);
      return;
    }

    const f = carro.ficha;
    const relativa = limitar(carro.rotacao / Math.max(1, f.rotacaoMaxima), 0, 1);
    const eletrico = f.eletrico;

    // Elétrico: apito agudo e fino; combustão: ronco grave que sobe de tom.
    const base = eletrico ? 220 + relativa * 900 : 38 + relativa * 150;
    for (const { osc, multiplicador } of this.motor.osciladores) {
      osc.frequency.setTargetAtTime(base * multiplicador, agora, suave);
    }
    const acelerador = carro.rotacao > f.marchaLenta * 1.2 ? 1 : 0.35;
    this.motor.filtro.frequency.setTargetAtTime(
      (eletrico ? 2200 : 420) + relativa * (eletrico ? 2500 : 2600) * acelerador, agora, suave);
    this.motor.ganho.gain.setTargetAtTime(
      (eletrico ? 0.05 : 0.11) + relativa * (eletrico ? 0.06 : 0.14), agora, suave);

    const chiado = limitar(carro.derrapando, 0, 1) * limitar(Math.abs(carro.vx) / 5, 0, 1);
    this.pneus.ganho.gain.setTargetAtTime(chiado * 0.2, agora, 0.05);
    this.pneus.filtro.frequency.setTargetAtTime(misturar(900, 2400, chiado), agora, 0.08);

    this.chuva.ganho.gain.setTargetAtTime((ambiente.chuva || 0) * 0.055, agora, 0.4);
  }

  /** Estouro curto: bateu em alguma coisa. */
  batida(forca) {
    if (!this.pronto) return;
    const ctx = this.ctx;
    const agora = ctx.currentTime;
    const intensidade = limitar(forca / 12, 0.08, 1);

    const fonte = ctx.createBufferSource();
    fonte.buffer = this.bufferDeRuido(0.4);
    const filtro = ctx.createBiquadFilter();
    filtro.type = 'lowpass';
    filtro.frequency.setValueAtTime(1800, agora);
    filtro.frequency.exponentialRampToValueAtTime(150, agora + 0.25);
    const ganho = ctx.createGain();
    ganho.gain.setValueAtTime(intensidade * 0.5, agora);
    ganho.gain.exponentialRampToValueAtTime(0.001, agora + 0.32);
    fonte.connect(filtro).connect(ganho).connect(this.mestre);
    fonte.start(agora);
    fonte.stop(agora + 0.35);
  }

  /** Bipe de ponto batido, resultado, botão. */
  nota(frequencia, duracao = 0.12, tipo = 'triangle', volume = 0.16) {
    if (!this.pronto) return;
    const ctx = this.ctx;
    const agora = ctx.currentTime;
    const osc = ctx.createOscillator();
    osc.type = tipo;
    osc.frequency.setValueAtTime(frequencia, agora);
    const ganho = ctx.createGain();
    ganho.gain.setValueAtTime(0, agora);
    ganho.gain.linearRampToValueAtTime(volume, agora + 0.012);
    ganho.gain.exponentialRampToValueAtTime(0.001, agora + duracao);
    osc.connect(ganho).connect(this.mestre);
    osc.start(agora);
    osc.stop(agora + duracao + 0.02);
  }

  ponto() { this.nota(880, 0.14); this.nota(1320, 0.18, 'triangle', 0.1); }
  vitoria() {
    [660, 880, 1100, 1320].forEach((f, i) => setTimeout(() => this.nota(f, 0.22, 'triangle', 0.16), i * 110));
  }
  derrota() {
    [440, 370, 294].forEach((f, i) => setTimeout(() => this.nota(f, 0.3, 'sawtooth', 0.12), i * 150));
  }
  clique() { this.nota(520, 0.06, 'square', 0.07); }

  alternar() {
    this.ligado = !this.ligado;
    if (this.mestre) this.mestre.gain.value = this.ligado ? this.volume : 0;
    return this.ligado;
  }
}
