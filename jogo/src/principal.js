// O jogo.
//
// Este arquivo é a cola: liga entrada, física, mundo, desenho, som e telas, e
// mantém a máquina de estados (menu → briefing → correndo → resultado).
//
// O laço tem passo FIXO na física (1/120 s) e desenho livre. É o que garante
// que a baliza se comporte igual num celular de 30 quadros e num monitor de
// 144 — física com passo variável é física diferente a cada máquina.

import { criarCamera, seguir, sacudir, MODOS, linhaDoHorizonte } from './motor/camera.js';
import { Ceu } from './motor/ceu.js';
import { Terreno, resolucaoDoChao } from './motor/terreno.js';
import { Cena, desenharSombras, desenharLuzes } from './motor/cena.js';
import { gerarMapa, marcarChao } from './motor/mapa.js';
import { LUZ } from './motor/paleta.js';
import { construirCarro } from './motor/modelos.js';

import { criarCarro, passo as passoFisica, trocarMarcha } from './jogo/fisica.js';
import { carroPorId } from './jogo/carros.js';
import { fichaMelhorada, niveisDe, comprarMelhoria } from './jogo/melhorias.js';
import { clima, atritoDe } from './jogo/clima.js';
import { gerarMundo, pisoEm } from './jogo/mundo.js';
import { resolverColisoes } from './jogo/colisao.js';
import {
  sortearMissao, gerarCarreira, montarMissao, atualizarMissao, avaliarMissao,
} from './jogo/missoes.js';
import { descritorDeModo, FICHA_DOS_MODOS } from './jogo/modos.js';
import {
  criarTransito, atualizarTransito, colisoresDoTransito,
  instanciasDoTransito, empilharCarro,
} from './jogo/transito.js';
import { Som } from './jogo/som.js';
import * as Progresso from './jogo/progresso.js';

import { Entrada } from './nucleo/entrada.js';
import { limitar, criarSorteio, entre, TAU, corTexto } from './nucleo/matematica.js';
import { Hud } from './interface/hud.js';
import { Telas } from './interface/telas.js';

const PASSO_FISICO = 1 / 120;
const MAX_PASSOS = 8;

class Jogo {
  constructor(tela, raizTelas) {
    this.tela = tela;
    this.ctx = tela.getContext('2d', { alpha: false });
    this.entrada = new Entrada(tela);
    this.som = new Som();
    this.hud = new Hud();
    this.telas = new Telas(raizTelas);
    this.progresso = Progresso.carregar();

    this.camera = criarCamera();
    this.ceu = new Ceu();
    this.terreno = new Terreno();
    this.cena = new Cena();

    this.estado = 'menu';
    this.partida = null;
    this.particulas = [];
    this.tempoReal = 0;
    this.acumulado = 0;
    this.ultimo = performance.now();

    this.carreira = gerarCarreira(this.progresso.carreira.semente);
    this.camera.modo = this.progresso.ajustes.camera || 'perseguicao';

    this.ajustarTamanho();
    window.addEventListener('resize', () => this.ajustarTamanho());
    const despertar = () => { if (this.progresso.ajustes.som) this.som.despertar(); this.som.retomar(); };
    window.addEventListener('pointerdown', despertar, { once: false });
    window.addEventListener('keydown', despertar, { once: false });

    this.irParaMenu();
    requestAnimationFrame(() => this.quadro());
  }

  /** Quantos pixels de verdade por pixel de tela. Celular bom tem 3; nesse
   *  caso o chão passaria de dois milhões de pixels calculados um a um por
   *  quadro, e nenhum telefone dá conta disso. O teto acompanha a qualidade. */
  proporcaoDePixels() {
    const teto = { alta: 2, media: 1.5, baixa: 1.25 }[this.progresso.ajustes.qualidade] || 2;
    return Math.min(window.devicePixelRatio || 1, teto);
  }

  ajustarTamanho(forcar = false) {
    const proporcao = this.proporcaoDePixels();
    const largura = Math.round(this.tela.clientWidth * proporcao);
    const altura = Math.round(this.tela.clientHeight * proporcao);
    if (!forcar && largura === this.tela.width && altura === this.tela.height) return;
    this.tela.width = Math.max(320, largura);
    this.tela.height = Math.max(240, altura);
    this.camera.aspecto = this.tela.width / this.tela.height;
    const r = resolucaoDoChao(this.tela.width, this.tela.height, this.progresso.ajustes.qualidade);
    this.terreno.redimensionar(r.largura, r.altura);
  }

  // -------------------------------------------------------------------------
  // TELAS
  // -------------------------------------------------------------------------

  irParaMenu() {
    this.estado = 'menu';
    this.partida = null;
    this.entrada.zerar();
    this.telas.menu(this.progresso, {
      carreira: () => this.abrirBriefing(this.missaoDaCarreira()),
      modos: () => this.abrirModos(),
      avulso: () => this.abrirBriefing(this.missaoAvulsa(), { avulso: true }),
      livre: () => this.comecarRuaLivre(),
      garagem: () => this.abrirGaragem(),
      ajustes: () => this.abrirAjustes(),
    });
  }

  abrirModos() {
    this.estado = 'modos';
    this.partida = null;
    this.entrada.zerar();
    this.telas.modos(this.progresso, {
      voltar: () => this.irParaMenu(),
      jogar: (id) => this.comecarModo(id),
    });
  }

  comecarModo(id) {
    const descritor = descritorDeModo(id);
    this.som.clique();
    this.comecarMissao(descritor);
  }

  missaoDaCarreira() {
    const indice = limitar(this.progresso.carreira.indice, 0, this.carreira.length - 1);
    return this.carreira[indice];
  }

  missaoAvulsa() {
    const nivel = 1 + Math.floor(this.progresso.carreira.indice / 3);
    const d = sortearMissao(Math.floor(Math.random() * 1e9), nivel);
    d.avulso = true;
    return d;
  }

  abrirGaragem() {
    this.estado = 'garagem';
    this.telas.garagem(this.progresso, {
      voltar: () => this.irParaMenu(),
      escolher: (modelo) => {
        this.progresso.carroAtual = modelo.id;
        Progresso.salvar(this.progresso);
        this.som.clique();
        this.abrirGaragem();
      },
      comprar: (modelo) => {
        const r = Progresso.comprar(this.progresso, modelo);
        if (r.ok) this.som.vitoria(); else this.som.derrota();
        this.abrirGaragem();
      },
      melhorar: (modelo, peca) => {
        const r = comprarMelhoria(this.progresso, modelo.id, peca);
        if (r.ok) { Progresso.salvar(this.progresso); this.som.ponto(); }
        else this.som.derrota();
        this.abrirGaragem();
      },
    });
  }

  abrirAjustes() {
    this.estado = 'ajustes';
    this.telas.ajustes(this.progresso, {
      voltar: () => this.irParaMenu(),
      qualidade: (v) => {
        this.progresso.ajustes.qualidade = v;
        Progresso.salvar(this.progresso);
        // `forcar`: a qualidade muda a proporção de pixels, então a tela
        // precisa ser remontada mesmo que o tamanho em CSS não tenha mudado.
        this.ajustarTamanho(true);
        this.abrirAjustes();
      },
      som: (v) => {
        this.progresso.ajustes.som = v;
        if (v) { this.som.despertar(); this.som.ligado = true; if (this.som.mestre) this.som.mestre.gain.value = this.som.volume; }
        else if (this.som.mestre) { this.som.ligado = false; this.som.mestre.gain.value = 0; }
        Progresso.salvar(this.progresso);
        this.abrirAjustes();
      },
      camera: (v) => {
        this.progresso.ajustes.camera = v;
        this.camera.modo = v;
        Progresso.salvar(this.progresso);
        this.abrirAjustes();
      },
      cambio: (v) => {
        this.progresso.ajustes.cambio = v;
        Progresso.salvar(this.progresso);
        this.abrirAjustes();
      },
      apagar: () => {
        this.progresso = Progresso.apagar();
        this.carreira = gerarCarreira(this.progresso.carreira.semente);
        this.irParaMenu();
      },
    });
  }

  abrirBriefing(descritor, extra = {}) {
    this.estado = 'briefing';
    this.descritorPendente = descritor;
    const modelo = carroPorId(this.progresso.carroAtual);
    this.telas.briefing(descritor, modelo, {
      comecar: () => this.comecarMissao(descritor),
      trocar: () => this.abrirBriefing(this.missaoAvulsa(), { avulso: true }),
      voltar: () => this.irParaMenu(),
    }, { rotulo: extra.avulso ? 'serviço avulso' : undefined });
  }

  // -------------------------------------------------------------------------
  // PARTIDA
  // -------------------------------------------------------------------------

  comecarMissao(descritor) {
    this.telas.carregando(descritor.modo && descritor.modo !== 'estacionamento'
      ? 'montando a pista…' : 'montando o bairro…');
    // Dois quadros de respiro para a tela de carregamento aparecer antes de a
    // geração do mapa travar a linha principal.
    requestAnimationFrame(() => requestAnimationFrame(() => {
      this.partida = this.montarPartida(descritor);
      this.hud.recados.length = 0;
      this.telas.esconder();
      this.estado = 'correndo';
      this.entrada.zerar();
      this.ultimo = performance.now();
      this.acumulado = 0;
      this.hud.recado(descritor.titulo.toUpperCase(),
        descritor.modo ? FICHA_DOS_MODOS[descritor.modo].cor : '#7fd1ff', 2.0);
    }));
  }

  /**
   * Começa uma missão escolhida a dedo — `{ tipo, cenario, clima }`. Não tem
   * botão no jogo: serve para testar um tipo sem depender do sorteio.
   * Do console: VOLANTE.comecarEspecifica({ tipo: 'baliza', clima: 'noite' })
   */
  comecarEspecifica(forcar = {}, nivel = 3) {
    const descritor = sortearMissao(Math.floor(Math.random() * 1e9), nivel, forcar);
    descritor.avulso = true;
    this.comecarMissao(descritor);
    return descritor;
  }

  comecarRuaLivre() {
    const descritor = sortearMissao(Math.floor(Math.random() * 1e9), 2);
    descritor.tipo = 'livre';
    descritor.titulo = 'Rua livre';
    descritor.resumo = 'Sem relógio e sem cobrança. Só a rua.';
    descritor.premio = 0;
    this.comecarMissao(descritor);
  }

  montarPartida(descritor) {
    const base = carroPorId(this.progresso.carroAtual);
    // O carro que entra na pista é o de fábrica MAIS o que foi comprado de
    // melhoria. A ficha do catálogo fica intacta: ela é usada também pelo
    // trânsito e pelos carros estacionados, que não são seus.
    const modelo = {
      ...base,
      ficha: fichaMelhorada(base, niveisDe(this.progresso, base.id)),
    };
    const ambiente = { ...clima(descritor.clima) };

    let missao;
    if (descritor.tipo === 'livre') {
      const mundo = gerarMundo(descritor.semente, { cenario: descritor.cenario, metros: 152 });
      missao = {
        descritor, mundo, objetivos: [], indice: 0, pontos: [],
        progresso: 0, concluida: false, falhou: false, motivo: '',
        tempoLimite: 99999, medidor: null, aviso: null, livre: true,
      };
    } else {
      missao = montarMissao(descritor, modelo);
    }

    const mundo = missao.mundo;
    const mapa = gerarMapa(mundo, ambiente, this.progresso.ajustes.qualidade);
    this.ceu.preparar(descritor.semente, mundo.ficha.perfil);

    const carro = criarCarro(modelo.ficha,
      { x: mundo.inicio.x, z: mundo.inicio.z }, mundo.inicio.angulo);
    carro.combustivel = missao.combustivel !== undefined
      ? missao.combustivel
      : modelo.ficha.tanque;

    const transito = criarTransito(mundo, descritor.semente,
      descritor.tipo === 'livre' ? 10 : (descritor.transito || 0));

    this.camera.x = mundo.inicio.x;
    this.camera.z = mundo.inicio.z;
    this.camera.guinada = mundo.inicio.angulo;
    this.camera.y = 3;

    this.particulas.length = 0;

    return {
      descritor, modelo, mundo, mapa, ambiente, missao,
      corpo: construirCarro(modelo),
      carro, transito,
      camera: this.camera,
      tempo: 0,
      foraDaPista: 0,
      piso: 'asfalto',
      cambioPosicao: 'D',
      estatisticas: { batidas: 0, forcaMaxima: 0, tempoNoAr: 0 },
    };
  }

  // -------------------------------------------------------------------------
  // LAÇO
  // -------------------------------------------------------------------------

  quadro() {
    const agora = performance.now();
    let dt = (agora - this.ultimo) / 1000;
    this.ultimo = agora;
    // Aba em segundo plano devolve dt gigante; segurar aqui evita o carro
    // teleportar para dentro de um prédio quando a pessoa volta.
    dt = Math.min(dt, 0.25);
    this.tempoReal += dt;

    if (this.estado === 'correndo') this.atualizarPartida(dt);
    this.desenhar(dt);
    this.medirRitmo(dt);

    requestAnimationFrame(() => this.quadro());
  }

  /**
   * Se o aparelho não está dando conta, o jogo baixa a qualidade sozinho.
   *
   * Isto existe porque o mesmo APK vai rodar num celular de mil reais e num de
   * dez mil, e quem está com o de mil não tem por que descobrir sozinho que
   * existe um menu de ajustes. Só desce, nunca sobe: ficar subindo e descendo
   * daria uma imagem piscando entre duas qualidades, que é pior que as duas.
   */
  medirRitmo(dt) {
    if (this.estado !== 'correndo') { this.ritmo = null; return; }
    if (!this.ritmo) this.ritmo = { tempo: 0, quadros: 0, carencia: 2.5 };
    const r = this.ritmo;

    // Carência no começo da partida: os primeiros quadros sempre engasgam,
    // porque é quando o mapa acabou de ser gerado e nada está aquecido.
    if (r.carencia > 0) { r.carencia -= dt; return; }

    r.tempo += dt;
    r.quadros++;
    if (r.tempo < 3) return;

    const porSegundo = r.quadros / r.tempo;
    r.tempo = 0;
    r.quadros = 0;

    const abaixo = { alta: 'media', media: 'baixa' }[this.progresso.ajustes.qualidade];
    if (porSegundo < 38 && abaixo) {
      this.progresso.ajustes.qualidade = abaixo;
      Progresso.salvar(this.progresso);
      this.ajustarTamanho(true);
      this.hud.recado('qualidade ajustada ao aparelho', '#7fd1ff', 2.6);
    }
  }

  atualizarPartida(dt) {
    const p = this.partida;
    const carro = p.carro;

    if (this.entrada.consumirPausa()) { this.pausar(); return; }
    if (this.entrada.consumirCamera()) {
      const i = MODOS.indexOf(this.camera.modo);
      this.camera.modo = MODOS[(i + 1) % MODOS.length];
      this.progresso.ajustes.camera = this.camera.modo;
      Progresso.salvar(this.progresso);
    }

    // O câmbio escolhido nos ajustes vale para a física inteira.
    this.entrada.comandos.cambio = this.progresso.ajustes.cambio || 'automatico';
    const comandos = this.entrada.atualizar(dt, carro);

    // Trocar de marcha acontece UMA vez por quadro, não a cada passo de física.
    const troca = this.entrada.consumirTrocaDeMarcha();
    if (troca && comandos.cambio === 'manual') {
      const resultado = trocarMarcha(carro, troca);
      if (resultado === 'trocou') this.som.marcha(troca);
    }
    // A posição da alavanca vai junto com a partida: é ela que confirma a
    // vaga no modo estacionamento, e a missão não enxerga a entrada.
    p.cambioPosicao = this.entrada.cambioPosicao;
    p.piso = pisoEm(p.mundo, carro.x, carro.z);
    const foraDoAsfalto = p.piso !== 'asfalto' && p.piso !== 'calcada';
    p.foraDaPista = foraDoAsfalto && Math.abs(carro.vx) > 2
      ? p.foraDaPista + dt : Math.max(0, p.foraDaPista - dt * 2);

    let atrito = atritoDe(p.ambiente, p.piso);
    if (p.piso === 'areia' && carro.ficha.areia) atrito *= carro.ficha.areia;
    const pista = { atrito };

    // Física com passo fixo.
    this.acumulado += dt;
    let passos = 0;
    while (this.acumulado >= PASSO_FISICO && passos < MAX_PASSOS) {
      passoFisica(carro, comandos, PASSO_FISICO, pista);
      this.acumulado -= PASSO_FISICO;
      passos++;
    }
    if (passos === MAX_PASSOS) this.acumulado = 0;

    atualizarTransito(p.transito, dt, carro, this.tempoReal);
    if (p.missao.escolta) this.atualizarEscolta(p.missao.escolta, dt);

    // Colisões contra cenário e contra os outros carros.
    const colisores = p.mundo.colisores.concat(colisoresDoTransito(p.transito));
    if (p.missao.escolta) {
      const e = p.missao.escolta;
      colisores.push({
        x: e.x, z: e.z, guinada: e.angulo,
        largura: e.largura, comprimento: e.comprimento,
        solido: true, altura: 1.5, escolta: true,
      });
    }

    const relatorio = resolverColisoes(carro, colisores, (alvo, forca) => {
      this.som.batida(forca);
      sacudir(this.camera, limitar(forca * 0.11, 0.05, 1.1));
      if (alvo.escolta && forca > 1.5) {
        p.missao.falhou = true;
        p.missao.motivo = 'Bateu no carro escoltado.';
      }
    });
    if (relatorio.batidas) {
      p.estatisticas.batidas += relatorio.batidas;
      p.estatisticas.forcaMaxima = Math.max(p.estatisticas.forcaMaxima, relatorio.forca);
      this.hud.recado('batida', '#ff7a5b', 1.0);
    }
    for (const derrubado of relatorio.derrubados) {
      if (derrubado.prop) derrubado.prop.derrubado = true;
      this.som.nota(180, 0.18, 'square', 0.09);
    }

    p.tempo += dt;
    this.rastroDePneu(p, dt);
    this.atualizarParticulas(dt);

    if (!p.missao.livre) {
      const evento = atualizarMissao(p, dt);
      if (evento) this.tratarEvento(evento);
      if (p.missao.falhou && !p.terminou) this.terminar(false);
    } else if (this.tempoReal % 1 < dt) {
      p.missao.progresso = 0;
    }

    carro.forcaImpacto = 0;
    seguir(this.camera, carro, dt, this.entrada.olharAtras);
    this.som.atualizar(carro, p.ambiente, true);
    this.hud.atualizar(dt);
  }

  atualizarEscolta(alvo, dt) {
    const pontos = alvo.rota.pontos;
    const destino = pontos[alvo.indice];
    const dx = destino.x - alvo.x, dz = destino.z - alvo.z;
    if (Math.hypot(dx, dz) < 5) alvo.indice = (alvo.indice + 1) % pontos.length;

    const desejado = Math.atan2(-dx, -dz);
    let erro = desejado - alvo.angulo;
    while (erro > Math.PI) erro -= TAU;
    while (erro < -Math.PI) erro += TAU;
    const taxa = limitar(erro * 2.0, -1.2, 1.2);
    alvo.esterco = taxa * 0.3;
    alvo.angulo += taxa * dt;

    const alvoVelocidade = alvo.alvoVelocidade * limitar(1 - Math.abs(erro) * 0.9, 0.3, 1);
    const diferenca = alvoVelocidade - alvo.velocidade;
    alvo.freando = diferenca < -0.5;
    alvo.velocidade += limitar(diferenca, -7 * dt, 3 * dt);

    const avanco = alvo.velocidade * dt;
    alvo.x += -Math.sin(alvo.angulo) * avanco;
    alvo.z += -Math.cos(alvo.angulo) * avanco;
    alvo.giroRoda += (alvo.velocidade / alvo.corpo.raioRoda) * dt;
    alvo.distanciaPercorrida += avanco;
  }

  tratarEvento(evento) {
    const p = this.partida;
    if (evento.tipo === 'ponto') {
      this.som.ponto();
      this.hud.recado(evento.restantes > 0 ? `faltam ${evento.restantes}` : 'último!', '#36c96f', 1.4);
    } else if (evento.tipo === 'marco') {
      // Ponto batido num modo: o número que subiu, na cara, e um bipe agudo.
      this.som.ponto();
      this.hud.recado(evento.texto, evento.cor || '#ffd24a', 1.8);
    } else if (evento.tipo === 'aviso') {
      this.som.nota(200, 0.16, 'sawtooth', 0.1);
      this.hud.recado(evento.texto, evento.cor || '#e8563a', 1.2);
    } else if (evento.tipo === 'vitoria') {
      this.terminar(true);
    } else if (evento.tipo === 'derrota') {
      this.terminar(false);
    }
  }

  terminar(sucesso) {
    const p = this.partida;
    if (p.terminou) return;
    p.terminou = true;
    this.estado = 'resultado';
    const resultado = avaliarMissao(p);
    if (sucesso) this.som.vitoria(); else this.som.derrota();
    this.som.atualizar(p.carro, p.ambiente, false);

    let extra = {};
    if (p.descritor.modo) {
      extra = Progresso.registrarModo(this.progresso, p.descritor.modo, resultado, p);
      if (extra.recorde && resultado.pontos > 0) this.som.vitoria();
    } else if (!p.descritor.avulso && p.descritor.indice !== undefined) {
      Progresso.registrarResultado(this.progresso, p.descritor.indice, resultado, p);
    } else if (resultado.sucesso) {
      this.progresso.dinheiro += resultado.premio;
      Progresso.salvar(this.progresso);
    }

    this.telas.resultado(resultado, p, {
      proxima: () => this.abrirBriefing(this.missaoDaCarreira()),
      // Num modo, repetir é uma partida NOVA: semente nova, clima novo. A pista
      // é a mesma — quem repete quer bater o próprio placar, não decorar o mapa.
      repetir: () => (p.descritor.modo
        ? this.comecarModo(p.descritor.modo)
        : this.comecarMissao(p.descritor)),
      modos: () => this.abrirModos(),
      menu: () => this.irParaMenu(),
    }, extra);
  }

  pausar() {
    if (this.partida.missao.livre) {
      this.estado = 'pausa';
      this.telas.pausa(this.partida, {
        continuar: () => this.despausar(),
        recomecar: () => this.comecarRuaLivre(),
        menu: () => this.irParaMenu(),
      });
      return;
    }
    this.estado = 'pausa';
    this.som.atualizar(this.partida.carro, this.partida.ambiente, false);
    const descritor = this.partida.descritor;
    this.telas.pausa(this.partida, {
      continuar: () => this.despausar(),
      recomecar: () => (descritor.modo
        ? this.comecarModo(descritor.modo)
        : this.comecarMissao(descritor)),
      menu: () => (descritor.modo ? this.abrirModos() : this.irParaMenu()),
    });
  }

  despausar() {
    this.telas.esconder();
    this.estado = 'correndo';
    this.entrada.zerar();
    this.ultimo = performance.now();
    this.acumulado = 0;
  }

  // -------------------------------------------------------------------------
  // EFEITOS
  // -------------------------------------------------------------------------

  /** Risca o chão e solta fumaça onde o pneu está escorregando. */
  rastroDePneu(p, dt) {
    const carro = p.carro;
    if (carro.derrapando < 0.25 || Math.abs(carro.vx) < 1.5) return;
    const corpo = p.corpo;
    const cos = Math.cos(carro.angulo), sen = Math.sin(carro.angulo);
    const forca = limitar(carro.derrapando, 0, 1);

    // Na chuva os quatro pneus jogam água; no seco só a traseira fuma. Mas a
    // conta tem que ser contida: a fumaça nasce a dois metros da câmera, e
    // partícula grande e opaca perto do olho tapa o carro inteiro.
    const molhado = p.ambiente.molhado;
    const chance = forca * (molhado ? 0.14 : 0.24);
    for (const roda of corpo.rodas) {
      if (roda.dianteira && !molhado) continue;
      const x = carro.x + cos * roda.x + sen * roda.z;
      const z = carro.z - sen * roda.x + cos * roda.z;
      marcarChao(p.mapa, x, z, 0.14, forca * dt * 14);
      if (Math.random() < chance) {
        this.particulas.push({
          x, y: 0.16, z,
          vx: entre(Math.random, -0.4, 0.4),
          vy: entre(Math.random, 0.3, 1.0),
          vz: entre(Math.random, -0.4, 0.4),
          vida: 0.55, total: 0.55,
          raio: 0.13,
          cor: p.piso === 'areia' ? 0xd8c8a0 : p.piso === 'grama' ? 0x6f7f52 : 0xcfd3d8,
        });
      }
    }
  }

  atualizarParticulas(dt) {
    for (const g of this.particulas) {
      g.vida -= dt;
      g.x += g.vx * dt;
      g.y += g.vy * dt;
      g.z += g.vz * dt;
      g.vy -= 0.6 * dt;
      g.raio += dt * 0.75;
    }
    this.particulas = this.particulas.filter((g) => g.vida > 0);
    if (this.particulas.length > 90) this.particulas.splice(0, this.particulas.length - 90);
  }

  // -------------------------------------------------------------------------
  // DESENHO
  // -------------------------------------------------------------------------

  desenhar(dt) {
    const ctx = this.ctx;
    const L = this.tela.width, A = this.tela.height;

    if (!this.partida) {
      const g = ctx.createLinearGradient(0, 0, 0, A);
      g.addColorStop(0, '#0d1219');
      g.addColorStop(1, '#161d27');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, L, A);
      return;
    }

    const p = this.partida;
    const ambiente = p.ambiente;
    const camera = this.camera;

    this.ceu.desenhar(ctx, camera, ambiente, this.tempoReal);

    const farois = ambiente.farois ? {
      x: p.carro.x, z: p.carro.z,
      fx: -Math.sin(p.carro.angulo), fz: -Math.cos(p.carro.angulo),
      alcance: 34, abertura: 0.30, forca: 0.95,
    } : null;
    this.terreno.desenhar(ctx, camera, p.mapa, ambiente, farois);

    const instancias = [];
    const sombras = [];
    const luzes = [];

    // Peneira antes de montar a cena.
    //
    // Um bairro cheio tem centenas de coisas e a cena transforma vértice por
    // vértice, na CPU. Sem peneira, uma árvore atrás do carro custa igual a uma
    // árvore na frente — e é isso que limitava o mundo a meia dúzia de prédios
    // espalhados. Aqui cai fora o que está atrás, o que está fora da abertura
    // lateral e o que está tão longe que não daria um pixel.
    const tanLateral = camera.tanMeio * camera.aspecto;
    for (const prop of p.mundo.props) {
      if (prop.derrubado) continue;
      let malha = prop.malha;
      {
        const rx = prop.x - camera.x, rz = prop.z - camera.z;
        const raio = prop.raio || 4;
        const adiante = rx * camera.fx + rz * camera.fz;
        if (adiante < -raio - 6) continue;
        if (adiante > raio + 2) {
          if (raio < adiante * 0.0035) continue;
          const lateral = Math.abs(rx * camera.dx + rz * camera.dz);
          if (lateral > adiante * tanLateral + raio + 3) continue;
          // Longe, a versão sem janela contada uma a uma.
          if (prop.malhaLonge && adiante > 62) malha = prop.malhaLonge;
        }
      }
      instancias.push({
        malha,
        x: prop.x, y: prop.y || 0, z: prop.z,
        guinada: prop.guinada || 0,
        raio: prop.raio || 4,
      });
      if (prop.luz && ambiente.postes) {
        luzes.push({ x: prop.x + 1.05 * Math.cos(prop.guinada || 0), y: prop.luz.y,
          z: prop.z - 1.05 * Math.sin(prop.guinada || 0), raio: prop.luz.raio, cor: prop.luz.cor, forca: 0.5 });
      }
      if (prop.raio > 1.5 && !prop.portico) {
        sombras.push({ x: prop.x, z: prop.z, guinada: prop.guinada || 0,
          largura: prop.raio * 0.9, comprimento: prop.raio * 0.9, forca: 0.5 });
      }
    }

    instanciasDoTransito(p.transito, ambiente, instancias, sombras, luzes, camera);
    if (p.missao.escolta) {
      const e = p.missao.escolta;
      empilharCarro(instancias, sombras, luzes, {
        corpo: e.corpo, x: e.x, z: e.z, angulo: e.angulo,
        esterco: e.esterco, giroRoda: e.giroRoda, freando: e.freando,
      }, ambiente, camera);
      // Sinalizador: é ele que você tem que seguir, então precisa ser achável.
      luzes.push({ x: e.x, y: 2.3, z: e.z, raio: 0.7, cor: 0xffa53a, forca: 0.45 });
    }

    // O carro do jogador só aparece quando a câmera está fora dele.
    const dentroDoCarro = camera.modo === 'cabine' || camera.modo === 'capo';
    if (!dentroDoCarro) {
      empilharCarro(instancias, sombras, luzes, {
        corpo: p.corpo, x: p.carro.x, z: p.carro.z, angulo: p.carro.angulo,
        esterco: p.carro.esterco,
        giroRoda: (p.carro.distancia / p.corpo.raioRoda) * (p.carro.sentido >= 0 ? 1 : -1),
        inclinacao: p.carro.inclinacao, rolagem: p.carro.rolagem,
        freando: this.entrada.comandos.freio > 0.1,
      }, ambiente, camera);
    } else if (ambiente.farois) {
      const cos = Math.cos(p.carro.angulo), sen = Math.sin(p.carro.angulo);
      for (const f of p.corpo.farois) {
        luzes.push({ x: p.carro.x + cos * f.x + sen * f.z, y: f.y,
          z: p.carro.z - sen * f.x + cos * f.z, raio: 0.5, cor: 0xfff0c8, forca: 0.7 });
      }
    }

    desenharSombras(ctx, camera, sombras, ambiente, L, A);
    this.cena.montar(camera, instancias, ambiente, L, A);
    this.cena.pintar(ctx);
    this.desenharParticulas(ctx, camera, L, A);
    desenharLuzes(ctx, camera, luzes, L, A);

    if (ambiente.chuva) this.desenharChuva(ctx, L, A, ambiente.chuva);
    if (ambiente.tonalidade) {
      ctx.save();
      ctx.globalAlpha = ambiente.forcaTonalidade || 0.1;
      ctx.fillStyle = corTexto(ambiente.tonalidade);
      ctx.fillRect(0, 0, L, A);
      ctx.restore();
    }
    if (camera.modo === 'cabine') this.desenharCabine(ctx, p, L, A);

    vinheta(ctx, L, A);

    if (this.estado === 'correndo' || this.estado === 'pausa') {
      this.hud.desenhar(ctx, p, this.entrada, this.progresso.ajustes);
    }
  }

  desenharParticulas(ctx, camera, L, A) {
    if (!this.particulas.length) return;
    const meiaL = L / 2, meiaA = A / 2;
    const kx = meiaL / (camera.tanMeio * camera.aspecto);
    const ky = meiaA / camera.tanMeio;
    ctx.save();
    for (const g of this.particulas) {
      const rx = g.x - camera.x, ry = g.y - camera.y, rz = g.z - camera.z;
      const cz = rx * camera.fx + ry * camera.fy + rz * camera.fz;
      // Partícula colada na lente não é fumaça, é um borrão tapando o jogo.
      if (cz <= 2.2 || cz > 90) continue;
      const cx = rx * camera.dx + ry * camera.dy + rz * camera.dz;
      const cy = -(rx * camera.cx + ry * camera.cy + rz * camera.cz);
      const px = meiaL + (cx / cz) * kx;
      const py = meiaA + (cy / cz) * ky;
      const raio = limitar((g.raio * ky) / cz, 1, A * 0.045);
      ctx.globalAlpha = limitar((g.vida / g.total) * 0.26, 0, 1);
      ctx.fillStyle = corTexto(g.cor);
      ctx.beginPath();
      ctx.arc(px, py, raio, 0, TAU);
      ctx.fill();
    }
    ctx.restore();
  }

  desenharChuva(ctx, L, A, forca) {
    const sortear = criarSorteio(Math.floor(this.tempoReal * 60));
    ctx.save();
    ctx.strokeStyle = 'rgba(200,215,230,0.35)';
    ctx.lineWidth = Math.max(1, L / 900);
    ctx.beginPath();
    const quantas = Math.round(160 * forca);
    for (let i = 0; i < quantas; i++) {
      const x = sortear() * L;
      const y = sortear() * A;
      const comprimento = A * 0.035;
      ctx.moveTo(x, y);
      ctx.lineTo(x - comprimento * 0.25, y + comprimento);
    }
    ctx.stroke();
    ctx.restore();
  }

  /** Dentro da cabine: painel, colunas e o vidro. O volante quem desenha é o HUD. */
  desenharCabine(ctx, p, L, A) {
    const horizonte = linhaDoHorizonte(this.camera, A);
    const alturaPainel = A * 0.26;
    const topo = Math.max(horizonte + A * 0.06, A - alturaPainel);
    const cor = p.modelo.corpo.corSecundaria;

    ctx.save();
    const g = ctx.createLinearGradient(0, topo, 0, A);
    g.addColorStop(0, corTexto(cor));
    g.addColorStop(1, '#0b0d10');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.moveTo(0, A);
    ctx.lineTo(0, topo + A * 0.05);
    ctx.quadraticCurveTo(L * 0.5, topo - A * 0.045, L, topo + A * 0.05);
    ctx.lineTo(L, A);
    ctx.closePath();
    ctx.fill();

    // Colunas do pára-brisa. Finas de propósito: coluna grossa é realista e
    // péssima de jogar — come justamente o pedaço da rua que se está olhando.
    ctx.fillStyle = corTexto(cor);
    ctx.beginPath();
    ctx.moveTo(0, 0); ctx.lineTo(L * 0.065, 0); ctx.lineTo(0, A * 0.86); ctx.closePath();
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(L, 0); ctx.lineTo(L * 0.935, 0); ctx.lineTo(L, A * 0.86); ctx.closePath();
    ctx.fill();
    ctx.fillRect(0, 0, L, A * 0.042);

    if (p.ambiente.molhado) {
      const sortear = criarSorteio(p.descritor.semente ^ 0x99);
      ctx.globalAlpha = 0.16;
      ctx.fillStyle = '#cfe0ee';
      for (let i = 0; i < 70; i++) {
        const x = sortear() * L;
        const y = sortear() * topo;
        ctx.beginPath();
        ctx.ellipse(x, y, entre(sortear, 1.5, 5), entre(sortear, 2, 9), 0, 0, TAU);
        ctx.fill();
      }
    }
    ctx.restore();

    this.desenharInstrumentos(ctx, p, L, A, topo);
  }

  /**
   * Os instrumentos do painel, na câmera de dentro do carro.
   *
   * Velocímetro grande no meio, marcador de combustível à direita, as duas
   * setas e o triângulo do pisca-alerta entre eles — a disposição de painel de
   * carro popular, que é o que o carro do jogo é. De fora da cabine nada disso
   * aparece: lá o painel é o mostradorzinho do HUD.
   */
  desenharInstrumentos(ctx, p, L, A, topo) {
    const carro = p.carro;
    const alturaPainel = A - topo;
    if (alturaPainel < A * 0.12) return;

    const cy = topo + alturaPainel * 0.60;
    const raio = Math.min(alturaPainel * 0.40, L * 0.075);
    const cx = L * 0.42;

    // Velocímetro.
    relogioDoPainel(ctx, cx, cy, raio, {
      valor: Math.abs(p.carro.vx) * 3.6,
      maximo: 200,
      passo: 20,
      rotulo: 'km/h',
      corPonteiro: '#e03a2a',
      digitos: true,
    });

    // Combustível, menor e à direita.
    const raioTanque = raio * 0.66;
    const cxTanque = cx + raio + raioTanque + L * 0.045;
    const tanque = p.missao.combustivel !== undefined
      ? carro.combustivel / p.missao.combustivel
      : carro.combustivel / carro.ficha.tanque;
    relogioDoPainel(ctx, cxTanque, cy, raioTanque, {
      valor: limitar(tanque, 0, 1),
      maximo: 1,
      passo: 0.5,
      extremos: ['E', 'F'],
      corPonteiro: tanque < 0.18 ? '#ff5b4a' : '#e03a2a',
    });

    // Setas e pisca-alerta, entre os dois relógios.
    const meio = (cx + raio + cxTanque - raioTanque) / 2;
    const piscando = Math.sin(this.tempoReal * 5.2) > 0;
    const volante = this.entrada.comandos.volante;
    const seta = (x, lado, aceso) => {
      ctx.save();
      ctx.translate(x, cy - raio * 0.12);
      ctx.scale(lado, 1);
      ctx.fillStyle = aceso ? '#4ee07a' : 'rgba(80,96,84,0.55)';
      ctx.beginPath();
      ctx.moveTo(-raio * 0.20, 0);
      ctx.lineTo(0, -raio * 0.20);
      ctx.lineTo(0, -raio * 0.09);
      ctx.lineTo(raio * 0.20, -raio * 0.09);
      ctx.lineTo(raio * 0.20, raio * 0.09);
      ctx.lineTo(0, raio * 0.09);
      ctx.lineTo(0, raio * 0.20);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
    };
    seta(meio - raio * 0.34, -1, piscando && volante < -0.35);
    seta(meio + raio * 0.34, 1, piscando && volante > 0.35);

    // Triângulo do pisca-alerta: acende quando a lataria está no limite.
    const alerta = carro.dano > 0.66;
    ctx.save();
    ctx.strokeStyle = alerta && piscando ? '#ff5b4a' : 'rgba(120,70,60,0.55)';
    ctx.lineWidth = Math.max(2, raio * 0.07);
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(meio, cy + raio * 0.10);
    ctx.lineTo(meio + raio * 0.20, cy + raio * 0.44);
    ctx.lineTo(meio - raio * 0.20, cy + raio * 0.44);
    ctx.closePath();
    ctx.stroke();
    ctx.restore();
  }
}

/**
 * Um relógio de painel: aro escuro, riscos, números e ponteiro.
 *
 * Serve tanto para o velocímetro quanto para o marcador de combustível — o
 * segundo é só menor, com menos riscos e com letra em vez de número nas
 * pontas. Um desenho só para os dois evita que eles fiquem de famílias
 * diferentes no mesmo painel, que é o que mais denuncia painel montado às
 * pressas.
 */
function relogioDoPainel(ctx, cx, cy, raio, opcoes) {
  const inicio = Math.PI * 0.76;
  const fim = Math.PI * 2.24;
  const fracao = limitar(opcoes.valor / opcoes.maximo, 0, 1);

  ctx.save();
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';

  const fundo = ctx.createRadialGradient(cx - raio * 0.3, cy - raio * 0.4, raio * 0.1, cx, cy, raio);
  fundo.addColorStop(0, '#2a2f36');
  fundo.addColorStop(1, '#0d1013');
  ctx.fillStyle = fundo;
  ctx.beginPath();
  ctx.arc(cx, cy, raio, 0, TAU);
  ctx.fill();
  ctx.strokeStyle = 'rgba(210,216,224,0.55)';
  ctx.lineWidth = Math.max(2, raio * 0.055);
  ctx.stroke();

  // Riscos e números.
  const quantos = Math.round(opcoes.maximo / opcoes.passo);
  for (let i = 0; i <= quantos; i++) {
    const t = i / quantos;
    const a = inicio + (fim - inicio) * t;
    const grande = i % 2 === 0;
    ctx.strokeStyle = t > 0.82 ? 'rgba(232,74,58,0.9)' : 'rgba(236,240,246,0.78)';
    ctx.lineWidth = Math.max(1.4, raio * (grande ? 0.045 : 0.028));
    ctx.beginPath();
    ctx.moveTo(cx + Math.cos(a) * raio * 0.84, cy + Math.sin(a) * raio * 0.84);
    ctx.lineTo(cx + Math.cos(a) * raio * (grande ? 0.68 : 0.74),
      cy + Math.sin(a) * raio * (grande ? 0.68 : 0.74));
    ctx.stroke();

    if (opcoes.digitos && grande) {
      ctx.fillStyle = 'rgba(236,240,246,0.85)';
      ctx.font = `600 ${Math.round(raio * 0.17)}px ${FONTE_PAINEL}`;
      ctx.fillText(String(Math.round(opcoes.maximo * t)),
        cx + Math.cos(a) * raio * 0.54, cy + Math.sin(a) * raio * 0.54);
    }
  }
  if (opcoes.extremos) {
    ctx.fillStyle = 'rgba(236,240,246,0.85)';
    ctx.font = `700 ${Math.round(raio * 0.26)}px ${FONTE_PAINEL}`;
    ctx.fillText(opcoes.extremos[0],
      cx + Math.cos(inicio) * raio * 0.52, cy + Math.sin(inicio) * raio * 0.52);
    ctx.fillText(opcoes.extremos[1],
      cx + Math.cos(fim) * raio * 0.52, cy + Math.sin(fim) * raio * 0.52);
  }
  if (opcoes.rotulo) {
    ctx.fillStyle = 'rgba(236,240,246,0.55)';
    ctx.font = `600 ${Math.round(raio * 0.15)}px ${FONTE_PAINEL}`;
    ctx.fillText(opcoes.rotulo, cx, cy + raio * 0.42);
  }

  // Ponteiro, com o eixo por cima.
  const a = inicio + (fim - inicio) * fracao;
  ctx.strokeStyle = opcoes.corPonteiro;
  ctx.lineWidth = Math.max(2, raio * 0.055);
  ctx.lineCap = 'round';
  ctx.beginPath();
  ctx.moveTo(cx - Math.cos(a) * raio * 0.14, cy - Math.sin(a) * raio * 0.14);
  ctx.lineTo(cx + Math.cos(a) * raio * 0.78, cy + Math.sin(a) * raio * 0.78);
  ctx.stroke();
  ctx.fillStyle = '#20242a';
  ctx.beginPath();
  ctx.arc(cx, cy, raio * 0.11, 0, TAU);
  ctx.fill();
  ctx.restore();
}

const FONTE_PAINEL = '"Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

/**
 * Vinheta de leve. O visual que o jogo persegue é de dia claro e ar limpo —
 * vinheta forte escurece o canto da tela e traz de volta o clima "fotográfico"
 * que a paleta está justamente tentando tirar.
 */
function vinheta(ctx, L, A) {
  if (LUZ.vinheta <= 0.01) return;
  const g = ctx.createRadialGradient(L / 2, A / 2, Math.min(L, A) * 0.52, L / 2, A / 2, Math.max(L, A) * 0.82);
  g.addColorStop(0, 'rgba(0,0,0,0)');
  g.addColorStop(1, `rgba(0,0,0,${LUZ.vinheta})`);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, L, A);
}

const tela = document.getElementById('jogo');
const raizTelas = document.getElementById('telas');
window.VOLANTE = new Jogo(tela, raizTelas);
