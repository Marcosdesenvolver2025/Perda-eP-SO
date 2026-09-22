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
    this.nuvens = [];
    for (let i = 0; i < 16; i++) {
      this.nuvens.push({
        azimute: entre(sortear, 0, TAU),
        elevacao: entre(sortear, 0.06, 0.55),
        largura: entre(sortear, 0.10, 0.34),
        altura: entre(sortear, 0.018, 0.055),
        opacidade: entre(sortear, 0.18, 0.62),
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
    const topo = horizonte - altura * 1.25;
    const g = ctx.createLinearGradient(0, topo, 0, horizonte);
    g.addColorStop(0, corTexto(ambiente.corCeuAlto));
    g.addColorStop(0.62, corTexto(misturarCor(ambiente.corCeuAlto, ambiente.corCeuBaixo, 0.6)));
    g.addColorStop(1, corTexto(ambiente.corCeuBaixo));
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, largura, Math.max(0, Math.ceil(horizonte) + 2));

    if (horizonte < 0) return;

    if (ambiente.estrelas) this.desenharEstrelas(ctx, camera, ambiente, largura, altura);
    this.desenharAstro(ctx, camera, ambiente, largura, altura);
    if (!ambiente.semNuvens) this.desenharNuvens(ctx, camera, ambiente, largura, altura, tempo);
    this.desenharSilhueta(ctx, camera, ambiente, largura, altura, horizonte);

    // Faixa de névoa colada no horizonte: é a cola entre o céu e o chão.
    const faixa = altura * 0.10;
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
    ctx.save();
    ctx.fillStyle = corTexto(ambiente.corNuvem || 0xffffff);
    for (const n of this.nuvens) {
      const azimute = n.azimute + tempo * n.deriva;
      const d = direcaoDe(azimute, n.elevacao);
      const p = projetarDirecao(camera, d.x, d.y, d.z, largura, altura);
      if (!p) continue;
      const l = n.largura * altura * 2.4;
      const a = n.altura * altura * 2.4;
      if (p.x < -l * 2 || p.x > largura + l * 2) continue;
      ctx.globalAlpha = n.opacidade * (ambiente.forcaNuvem === undefined ? 1 : ambiente.forcaNuvem);
      ctx.beginPath();
      ctx.ellipse(p.x, p.y, l, a, 0, 0, TAU);
      ctx.fill();
      ctx.beginPath();
      ctx.ellipse(p.x - l * 0.4, p.y + a * 0.35, l * 0.55, a * 0.7, 0, 0, TAU);
      ctx.fill();
      ctx.beginPath();
      ctx.ellipse(p.x + l * 0.45, p.y + a * 0.3, l * 0.5, a * 0.65, 0, 0, TAU);
      ctx.fill();
    }
    ctx.restore();
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
    const alturaDestino = altura * 0.30 * (0.62 / camera.tanMeio);
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
      ctx.drawImage(tira, s1, 0, larguraFonte, tira.height,
        x0, horizonte - alturaDestino, x1 - x0, alturaDestino);
      if (s1 < 0) {
        ctx.drawImage(tira, s1 + tira.width, 0, larguraFonte, tira.height,
          x0, horizonte - alturaDestino, x1 - x0, alturaDestino);
      }
    }
    ctx.restore();
  }
}

/** A tira de 360°: prédios, morros ou palmeiras, conforme o cenário. */
function desenharSilhueta(semente, perfil) {
  const largura = 2048;
  const altura = 220;
  const tela = document.createElement('canvas');
  tela.width = largura;
  tela.height = altura;
  const ctx = tela.getContext('2d');
  const sortear = criarSorteio(semente ^ 0x9d4b);

  const camadas = [
    { cor: 'rgba(12,18,28,0.30)', escala: 0.62, passo: 1.6 },
    { cor: 'rgba(10,15,24,0.50)', escala: 0.82, passo: 1.1 },
    { cor: 'rgba(8,12,20,0.78)', escala: 1.0, passo: 0.8 },
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
        ctx.fillRect(x, altura - h, l, h);
        if (sortear() > 0.72) {
          const antena = entre(sortear, 8, 26);
          ctx.fillRect(x + l * 0.45, altura - h - antena, 2.5, antena);
        }
        x += l + entre(sortear, 2, 26);
      }
    }
  }

  // Janelas acesas — só fazem sentido na cidade.
  if (perfil === 'cidade' || perfil === 'industrial') {
    ctx.fillStyle = 'rgba(255,214,140,0.55)';
    for (let i = 0; i < 900; i++) {
      const x = sortear() * largura;
      const y = altura - sortear() * altura * 0.8;
      if (sortear() > 0.55) ctx.fillRect(x, y, 2.5, 3.5);
    }
  }
  return tela;
}

export function nivelDeLuz(ambiente) {
  return limitar(ambiente.luz === undefined ? 1 : ambiente.luz, 0.1, 1.4);
}
