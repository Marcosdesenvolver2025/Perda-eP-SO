// Céu, sol e a cidade lá no fundo.
//
// Nada aqui é 3D de verdade: tudo está infinitamente longe, então só o ÂNGULO
// importa. Uma estrela, o sol e uma torre do outro lado da cidade são
// desenhados a partir da direção em que estão, projetada pela mesma câmera que
// desenha o resto — por isso eles acompanham o giro do carro sem deslizar.

import { TAU, corTexto, criarSorteio, entre, limitar, misturarCor } from '../nucleo/matematica.js';
import { linhaDoHorizonte } from './camera.js';

/** Projeta uma DIREÇÃO (ponto no infinito). Devolve null se estiver atrás. */
export function projetarDirecao(camera, dx, dy, dz, largura, altura) {
  const cz = dx * camera.fx + dy * camera.fy + dz * camera.fz;
  if (cz <= 0.0001) return null;
  const cx = dx * camera.dx + dy * camera.dy + dz * camera.dz;
  const cy = dx * camera.cx + dy * camera.cy + dz * camera.cz;
  const meiaL = largura / 2, meiaA = altura / 2;
  return {
    x: meiaL + (cx / cz) * (meiaL / (camera.tanMeio * camera.aspecto)),
    y: meiaA - (cy / cz) * (meiaA / camera.tanMeio),
    z: cz,
  };
}

function direcaoDe(azimute, elevacao) {
  const ce = Math.cos(elevacao);
  return { x: -ce * Math.sin(azimute), y: Math.sin(elevacao), z: -ce * Math.cos(azimute) };
}

export class Ceu {
  constructor() {
    this.silhueta = null;
    this.semente = 0;
    this.estrelas = null;
    this.nuvens = null;
  }

  /** Redesenha a cidade do fundo. Só precisa rodar quando a missão muda. */
  preparar(semente, perfil) {
    if (this.semente === semente && this.silhueta && this.perfil === perfil) return;
    this.semente = semente;
    this.perfil = perfil;
    this.silhueta = desenharSilhueta(semente, perfil);

    const sortear = criarSorteio(semente ^ 0x51ed);
    this.estrelas = [];
    for (let i = 0; i < 220; i++) {
      this.estrelas.push({
        azimute: entre(sortear, 0, TAU),
        elevacao: entre(sortear, 0.04, 1.4),
        brilho: entre(sortear, 0.25, 1),
        tamanho: entre(sortear, 0.7, 2.1),
      });
    }
    // Nuvem.
    //
    // Três elipses brancas e chapadas davam um borrão do tamanho da tela
    // atravessado na cidade. Nuvem de verdade tem BORDA MACIA e barriga
    // cinza — e nada disso sai de uma elipse com fillStyle sólido. Então
    // cada nuvem é um carimbo desenhado uma vez, com vários bolos de
    // degradê, e depois só esticado na tela: fica macia e não custa nada.
    this.carimbos = [];
    for (let i = 0; i < 5; i++) this.carimbos.push(carimboDeNuvem(sortear));
    this.nuvens = [];
    for (let i = 0; i < 22; i++) {
      this.nuvens.push({
        carimbo: (sortear() * this.carimbos.length) | 0,
        azimute: entre(sortear, 0, TAU),
        elevacao: entre(sortear, 0.16, 0.90),
        largura: entre(sortear, 0.13, 0.34),
        opacidade: entre(sortear, 0.55, 0.95),
        deriva: entre(sortear, -0.004, 0.004),
      });
    }
  }

  desenhar(ctx, camera, ambiente, tempo) {
    const largura = ctx.canvas.width;
    const altura = ctx.canvas.height;
    const horizonte = linhaDoHorizonte(camera, altura);

    // Degradê do céu. A âncora é o horizonte, não o topo da tela: assim o céu
    // não "escorrega" quando a câmera olha para baixo.
    //
    // A altura do degradê acompanha a faixa de céu que REALMENTE aparece. Com
    // um valor fixo e grande, o azul forte do topo cai fora da tela e sobra só
    // a parte pálida de baixo — o céu fica leitoso o tempo todo.
    const alturaDoCeu = Math.max(horizonte * 1.15, altura * 0.42);
    const topo = horizonte - alturaDoCeu;
    const g = ctx.createLinearGradient(0, topo, 0, horizonte);
    g.addColorStop(0, corTexto(ambiente.corCeuAlto));
    g.addColorStop(0.55, corTexto(misturarCor(ambiente.corCeuAlto, ambiente.corCeuBaixo, 0.55)));
    g.addColorStop(1, corTexto(ambiente.corCeuBaixo));
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, largura, Math.max(0, Math.ceil(horizonte) + 2));

    if (horizonte < 0) return;

    if (ambiente.estrelas) this.desenharEstrelas(ctx, camera, ambiente, largura, altura);
    this.desenharAstro(ctx, camera, ambiente, largura, altura);
    this.desenharSilhueta(ctx, camera, ambiente, largura, altura, horizonte);
    if (!ambiente.semNuvens) this.desenharNuvens(ctx, camera, ambiente, largura, altura, tempo);

    // Faixa de névoa colada no horizonte: é a cola entre o céu e o chão.
    const faixa = altura * 0.065;
    const n = ctx.createLinearGradient(0, horizonte - faixa, 0, horizonte + 1);
    n.addColorStop(0, corTexto(ambiente.corHorizonte) + '00');
    n.addColorStop(1, corTexto(ambiente.corHorizonte));
    ctx.fillStyle = n;
    ctx.fillRect(0, Math.max(0, horizonte - faixa), largura, Math.min(faixa, horizonte) + 1);
  }

  desenharEstrelas(ctx, camera, ambiente, largura, altura) {
    ctx.save();
    for (const e of this.estrelas) {
      const d = direcaoDe(e.azimute, e.elevacao);
      const p = projetarDirecao(camera, d.x, d.y, d.z, largura, altura);
      if (!p || p.x < -10 || p.x > largura + 10 || p.y < -10) continue;
      ctx.globalAlpha = e.brilho * ambiente.estrelas;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(p.x, p.y, e.tamanho, e.tamanho);
    }
    ctx.restore();
  }

  desenharAstro(ctx, camera, ambiente, largura, altura) {
    const astro = ambiente.astro;
    if (!astro) return;
    const d = direcaoDe(astro.azimute, astro.elevacao);
    const p = projetarDirecao(camera, d.x, d.y, d.z, largura, altura);
    if (!p) return;
    const raio = altura * astro.tamanho;

    ctx.save();
    const brilho = ctx.createRadialGradient(p.x, p.y, raio * 0.2, p.x, p.y, raio * 6);
    brilho.addColorStop(0, corTexto(astro.cor) + 'cc');
    brilho.addColorStop(0.25, corTexto(astro.cor) + '44');
    brilho.addColorStop(1, corTexto(astro.cor) + '00');
    ctx.fillStyle = brilho;
    ctx.fillRect(p.x - raio * 6, p.y - raio * 6, raio * 12, raio * 12);

    ctx.fillStyle = corTexto(astro.corNucleo || astro.cor);
    ctx.beginPath();
    ctx.arc(p.x, p.y, raio, 0, TAU);
    ctx.fill();

    if (astro.crateras) {
      ctx.globalAlpha = 0.22;
      ctx.fillStyle = '#7f8899';
      for (const [cx, cy, cr] of [[-0.3, -0.2, 0.22], [0.25, 0.1, 0.3], [0.05, -0.45, 0.16]]) {
        ctx.beginPath();
        ctx.arc(p.x + cx * raio, p.y + cy * raio, cr * raio, 0, TAU);
        ctx.fill();
      }
    }
    ctx.restore();
  }

  desenharNuvens(ctx, camera, ambiente, largura, altura, tempo) {
    if (!this.carimbos || !this.carimbos.length) return;
    ctx.save();
    const forca = ambiente.forcaNuvem === undefined ? 1 : ambiente.forcaNuvem;
    const carimbos = this.carimbosTingidos(ambiente.corNuvem);
    for (const n of this.nuvens) {
      const azimute = n.azimute + tempo * n.deriva;
      const d = direcaoDe(azimute, n.elevacao);
      const p = projetarDirecao(camera, d.x, d.y, d.z, largura, altura);
      if (!p) continue;
      const carimbo = carimbos[n.carimbo];
      const l = n.largura * altura * 1.5;
      const a = l * (carimbo.height / carimbo.width);
      if (p.x < -l || p.x > largura + l) continue;
      ctx.globalAlpha = n.opacidade * forca;
      ctx.drawImage(carimbo, p.x - l / 2, p.y - a / 2, l, a);
    }
    ctx.restore();
  }

  /** Nuvem de fim de tarde não é branca. Tinge o carimbo uma vez e guarda. */
  carimbosTingidos(cor) {
    if (cor === undefined || cor === 0xffffff) return this.carimbos;
    if (this.tintaGuardada === cor && this.carimbosDaTinta) return this.carimbosDaTinta;
    this.tintaGuardada = cor;
    this.carimbosDaTinta = this.carimbos.map((base) => {
      const tela = document.createElement('canvas');
      tela.width = base.width;
      tela.height = base.height;
      const c = tela.getContext('2d');
      c.drawImage(base, 0, 0);
      c.globalCompositeOperation = 'source-atop';
      c.globalAlpha = 0.55;
      c.fillStyle = corTexto(cor);
      c.fillRect(0, 0, tela.width, tela.height);
      return tela;
    });
    return this.carimbosDaTinta;
  }

  /**
   * A cidade do fundo mora numa tira que dá a volta nos 360°. Ela é recortada
   * em fatias verticais porque a tela é plana e a tira é um cilindro: sem as
   * fatias, os prédios da borda esticariam.
   */
  desenharSilhueta(ctx, camera, ambiente, largura, altura, horizonte) {
    const tira = this.silhueta;
    if (!tira) return;
    const fatias = 40;
    const alturaDestino = altura * 0.20 * (0.62 / camera.tanMeio);
    const tanX = camera.tanMeio * camera.aspecto;

    ctx.save();
    ctx.globalAlpha = ambiente.forcaSilhueta === undefined ? 1 : ambiente.forcaSilhueta;
    for (let i = 0; i < fatias; i++) {
      const x0 = (i / fatias) * largura;
      const x1 = ((i + 1) / fatias) * largura;
      const u0 = ((x0 / largura) * 2 - 1) * tanX;
      const u1 = ((x1 / largura) * 2 - 1) * tanX;
      const a0 = camera.guinada - Math.atan(u0);
      const a1 = camera.guinada - Math.atan(u1);
      let s0 = (((a0 / TAU) % 1) + 1) % 1 * tira.width;
      let s1 = (((a1 / TAU) % 1) + 1) % 1 * tira.width;
      // A fatia vai da esquerda para a direita na tela = ângulo decrescente.
      if (s1 > s0) s1 -= tira.width;
      const larguraFonte = s0 - s1;
      if (larguraFonte <= 0) continue;

      // A EMENDA DA TIRA.
      //
      // Quando a fatia cai em cima do ponto onde a tira de 360° fecha, ela
      // pega um pedaço do fim e um pedaço do começo. Antes, o segundo pedaço
      // era desenhado por cima da fatia INTEIRA — e o resultado era a cidade
      // do fundo escorregando sozinha toda vez que o carro apontava para
      // aquele rumo. Agora o destino é cortado na mesma proporção da fonte:
      // cada pedaço ocupa exatamente a fração da fatia que lhe cabe.
      const destinoY = horizonte - alturaDestino;
      const destinoL = x1 - x0;
      if (s1 < 0) {
        const daPonta = -s1;                       // pedaço do FIM da tira
        const fracao = daPonta / larguraFonte;
        ctx.drawImage(tira, tira.width - daPonta, 0, daPonta, tira.height,
          x0, destinoY, destinoL * fracao, alturaDestino);
        ctx.drawImage(tira, 0, 0, s0, tira.height,
          x0 + destinoL * fracao, destinoY, destinoL * (1 - fracao), alturaDestino);
      } else {
        ctx.drawImage(tira, s1, 0, larguraFonte, tira.height,
          x0, destinoY, destinoL, alturaDestino);
      }
    }
    ctx.restore();
  }
}

/**
 * Um carimbo de nuvem: bolos de degradê empilhados numa base, com a barriga
 * puxada para o cinza-azulado e o topo estourado de branco. O segredo é o
 * degradê radial — é ele que dá a borda macia que a elipse chapada não tem.
 */
function carimboDeNuvem(sortear) {
  const largura = 320;
  const altura = 160;
  const tela = document.createElement('canvas');
  tela.width = largura;
  tela.height = altura;
  const ctx = tela.getContext('2d');

  const bolos = [];
  const quantos = 7 + ((sortear() * 5) | 0);
  const base = altura * 0.70;
  for (let i = 0; i < quantos; i++) {
    const u = (i + entre(sortear, 0.1, 0.9)) / quantos;
    // Perfil de nuvem: gorda no meio, fina nas pontas.
    const massa = Math.sin(u * Math.PI) ** 0.7;
    const r = entre(sortear, 0.16, 0.30) * altura * (0.45 + massa * 0.9);
    bolos.push({
      x: largura * (0.08 + u * 0.84),
      y: base - massa * altura * entre(sortear, 0.16, 0.34) - r * 0.25,
      r,
    });
  }

  // 1. A barriga: mesma forma, deslocada para baixo e cinza-azulada.
  for (const b of bolos) {
    const s = ctx.createRadialGradient(b.x, b.y + b.r * 0.34, 0, b.x, b.y + b.r * 0.34, b.r);
    s.addColorStop(0, 'rgba(176,196,218,0.85)');
    s.addColorStop(0.62, 'rgba(190,208,228,0.55)');
    s.addColorStop(1, 'rgba(198,214,232,0)');
    ctx.fillStyle = s;
    ctx.beginPath();
    ctx.arc(b.x, b.y + b.r * 0.34, b.r, 0, TAU);
    ctx.fill();
  }

  // 2. O corpo branco por cima, um pouco mais alto: sobra só um fio de cinza
  //    embaixo, que é exatamente o que se vê numa nuvem de meio-dia.
  for (const b of bolos) {
    const s = ctx.createRadialGradient(b.x, b.y - b.r * 0.10, 0, b.x, b.y - b.r * 0.10, b.r);
    s.addColorStop(0, 'rgba(255,255,255,1)');
    s.addColorStop(0.55, 'rgba(255,255,255,0.94)');
    s.addColorStop(0.82, 'rgba(252,253,255,0.45)');
    s.addColorStop(1, 'rgba(250,252,255,0)');
    ctx.fillStyle = s;
    ctx.beginPath();
    ctx.arc(b.x, b.y - b.r * 0.10, b.r * 0.95, 0, TAU);
    ctx.fill();
  }

  // 3. Estouro de luz no topo do lado do sol.
  for (const b of bolos) {
    if (sortear() > 0.55) continue;
    const s = ctx.createRadialGradient(
      b.x - b.r * 0.2, b.y - b.r * 0.45, 0, b.x - b.r * 0.2, b.y - b.r * 0.45, b.r * 0.7);
    s.addColorStop(0, 'rgba(255,255,255,0.9)');
    s.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = s;
    ctx.beginPath();
    ctx.arc(b.x - b.r * 0.2, b.y - b.r * 0.45, b.r * 0.7, 0, TAU);
    ctx.fill();
  }
  return tela;
}

/** A tira de 360°: prédios, morros ou palmeiras, conforme o cenário. */
function desenharSilhueta(semente, perfil) {
  // Tira maior: o fundo é a coisa que mais se olha e a que mais denuncia
  // pobreza de desenho. Dobrar a resolução custa uma vez, no carregamento.
  const largura = 4096;
  const altura = 320;
  const tela = document.createElement('canvas');
  tela.width = largura;
  tela.height = altura;
  const ctx = tela.getContext('2d');
  const sortear = criarSorteio(semente ^ 0x9d4b);

  // Camadas claras e pouco opacas: a cidade do fundo tem que parecer longe e
  // ensolarada, não uma parede preta recortada contra o céu.
  const camadas = [
    { cor: 'rgba(104,134,166,0.22)', escala: 0.52, passo: 2.1, detalhe: 0 },
    { cor: 'rgba(92,122,156,0.30)', escala: 0.68, passo: 1.5, detalhe: 0 },
    { cor: 'rgba(78,108,142,0.42)', escala: 0.86, passo: 1.1, detalhe: 1 },
    { cor: 'rgba(60,90,124,0.58)', escala: 1.0, passo: 0.8, detalhe: 2 },
  ];

  for (const camada of camadas) {
    ctx.fillStyle = camada.cor;
    let x = 0;
    while (x < largura) {
      if (perfil === 'praia') {
        const l = entre(sortear, 14, 34) * camada.passo;
        const h = entre(sortear, 0.22, 0.6) * altura * camada.escala;
        // palmeira: tronco fino e uma coroa
        ctx.fillRect(x + l * 0.42, altura - h, l * 0.14, h);
        ctx.beginPath();
        ctx.ellipse(x + l * 0.49, altura - h, l * 0.52, h * 0.16, 0, 0, TAU);
        ctx.fill();
        x += l + entre(sortear, 20, 90);
      } else if (perfil === 'campo') {
        const l = entre(sortear, 90, 260) * camada.passo;
        const h = entre(sortear, 0.18, 0.52) * altura * camada.escala;
        ctx.beginPath();
        ctx.moveTo(x, altura);
        ctx.quadraticCurveTo(x + l * 0.5, altura - h, x + l, altura);
        ctx.closePath();
        ctx.fill();
        x += l * entre(sortear, 0.5, 0.85);
      } else {
        const l = entre(sortear, 26, 88) * camada.passo;
        const h = entre(sortear, 0.24, 1.0) * altura * camada.escala;
        const topo = altura - h;
        ctx.fillRect(x, topo, l, h);

        // Silhueta só de retângulo lê como cerca, não como cidade. O que faz
        // ela virar cidade é o RECORTE do topo: caixa d'água, platibanda,
        // recuo de andar alto, antena. Só na camada da frente, que é a única
        // em que esse tamanho de detalhe ainda se enxerga.
        if (camada.detalhe > 0) {
          const sorte = sortear();
          if (sorte < 0.22) {
            // recuo: um bloco mais estreito em cima
            const lr = l * entre(sortear, 0.4, 0.7);
            const hr = h * entre(sortear, 0.10, 0.26);
            ctx.fillRect(x + (l - lr) / 2, topo - hr, lr, hr);
          } else if (sorte < 0.40) {
            // caixa d'água em pé
            const lc = l * entre(sortear, 0.16, 0.3);
            const hc = h * entre(sortear, 0.06, 0.14);
            ctx.fillRect(x + l * entre(sortear, 0.15, 0.6), topo - hc, lc, hc);
          } else if (sorte < 0.52) {
            // platibanda: uma tampa fininha que passa da largura
            ctx.fillRect(x - 2, topo - 3, l + 4, 4);
          }
        }
        if (camada.detalhe > 1 && sortear() > 0.70) {
          const antena = entre(sortear, 12, 42);
          ctx.fillRect(x + l * 0.45, topo - antena, 2.5, antena);
          if (sortear() > 0.6) ctx.fillRect(x + l * 0.45 - 4, topo - antena * 0.7, 10, 2);
        }
        x += l + entre(sortear, 2, 26);
      }
    }
  }

  // Janelas acesas — só fazem sentido na cidade. Em FILEIRA, não espalhadas:
  // ponto solto no meio da silhueta parece sujeira; fileira parece andar.
  if (perfil === 'cidade' || perfil === 'industrial') {
    ctx.fillStyle = 'rgba(255,224,160,0.5)';
    for (let bloco = 0; bloco < 260; bloco++) {
      const x0 = sortear() * largura;
      const base = altura - entre(sortear, altura * 0.08, altura * 0.72);
      const colunas = Math.round(entre(sortear, 2, 6));
      const andares = Math.round(entre(sortear, 2, 7));
      for (let c = 0; c < colunas; c++) {
        for (let a = 0; a < andares; a++) {
          if (sortear() > 0.62) continue;
          ctx.fillRect(x0 + c * 6, base - a * 7, 2.6, 3.6);
        }
      }
    }
    // Uma névoa clara subindo do horizonte: é ela que empurra a cidade para
    // longe e tira o ar de adesivo colado no céu.
    const nevoa = ctx.createLinearGradient(0, altura * 0.35, 0, altura);
    nevoa.addColorStop(0, 'rgba(214,232,246,0)');
    nevoa.addColorStop(1, 'rgba(214,232,246,0.52)');
    ctx.fillStyle = nevoa;
    ctx.fillRect(0, 0, largura, altura);
  }
  return tela;
}

export function nivelDeLuz(ambiente) {
  return limitar(ambiente.luz === undefined ? 1 : ambiente.luz, 0.1, 1.4);
}
