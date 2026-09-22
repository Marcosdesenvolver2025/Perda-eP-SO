// O desenhista dos objetos.
//
// Recebe instâncias (uma malha + onde ela está + como está girada), leva tudo
// para o espaço da câmera, joga fora o que está atrás, corta o que atravessa o
// plano de perto, ordena do mais longe para o mais perto e pinta.
//
// Sobre o espaço da câmera usado aqui: guardamos (direita, BAIXO, frente). Com
// o eixo vertical apontando para baixo a base fica destra e — o que interessa
// na prática — a orientação do polígono na tela passa a ser a mesma do 3D.
// Resultado: dá para descartar a face virada para o lado errado olhando só
// para a área com sinal do polígono já projetado, sem calcular normal nenhuma.

import { TAU, corTexto, limitar, tonalizar } from '../nucleo/matematica.js';

const MAX_VERTICES = 20000;

export class Cena {
  constructor() {
    this.cx = new Float32Array(MAX_VERTICES);
    this.cy = new Float32Array(MAX_VERTICES);
    this.cz = new Float32Array(MAX_VERTICES);
    this.sx = new Float32Array(MAX_VERTICES);
    this.sy = new Float32Array(MAX_VERTICES);
    this.poligonos = [];
    this.luzes = [];
  }

  limpar() {
    this.poligonos.length = 0;
    this.luzes.length = 0;
  }

  /**
   * @param instancias [{ malha, x, y, z, guinada, inclinacao, rolagem, escala,
   *                      tinta, opacidade }]
   */
  montar(camera, instancias, ambiente, largura, altura) {
    this.limpar();

    const sol = ambiente.direcaoSol;
    const comprimento = Math.hypot(sol.x, sol.y, sol.z) || 1;
    const solX = sol.x / comprimento, solY = sol.y / comprimento, solZ = sol.z / comprimento;
    // Luz no espaço da câmera (lembrando: o eixo vertical aponta para baixo).
    const lx = solX * camera.dx + solY * camera.dy + solZ * camera.dz;
    const ly = -(solX * camera.cx + solY * camera.cy + solZ * camera.cz);
    const lz = solX * camera.fx + solY * camera.fy + solZ * camera.fz;

    const ambienteLuz = ambiente.ambienteLuz === undefined ? 0.45 : ambiente.ambienteLuz;
    const intensidade = ambiente.luz === undefined ? 1 : ambiente.luz;
    const meiaL = largura / 2, meiaA = altura / 2;
    const kx = meiaL / (camera.tanMeio * camera.aspecto);
    const ky = meiaA / camera.tanMeio;
    const perto = camera.perto;

    for (const inst of instancias) {
      const malha = inst.malha;
      const vertices = malha.vertices;
      const total = vertices.length / 3;
      if (total > MAX_VERTICES) continue;

      const m = matrizDaInstancia(inst, camera);
      const { m00, m01, m02, m10, m11, m12, m20, m21, m22, tx, ty, tz } = m;

      // Corte grosseiro: se o objeto inteiro está atrás, nem transforma.
      const raio = inst.raio || 6;
      if (tz < -raio - 2) continue;

      let algumNaFrente = false;
      for (let i = 0, v = 0; i < total; i++, v += 3) {
        const x = vertices[v], y = vertices[v + 1], z = vertices[v + 2];
        const a = m00 * x + m01 * y + m02 * z + tx;
        const b = m10 * x + m11 * y + m12 * z + ty;
        const c = m20 * x + m21 * y + m22 * z + tz;
        this.cx[i] = a; this.cy[i] = b; this.cz[i] = c;
        if (c > perto) {
          algumNaFrente = true;
          this.sx[i] = meiaL + (a / c) * kx;
          this.sy[i] = meiaA + (b / c) * ky;
        }
      }
      if (!algumNaFrente) continue;

      for (const face of malha.faces) {
        const idx = face.i;
        const n = idx.length;

        let atras = 0;
        let profundidade = 0;
        for (let k = 0; k < n; k++) {
          const c = this.cz[idx[k]];
          if (c <= perto) atras++;
          profundidade += c;
        }
        if (atras === n) continue;
        profundidade /= n;

        let pontos;
        if (atras > 0) {
          pontos = this.recortar(idx, perto, meiaL, meiaA, kx, ky);
          if (!pontos || pontos.length < 6) continue;
        } else {
          pontos = new Float32Array(n * 2);
          for (let k = 0; k < n; k++) {
            pontos[k * 2] = this.sx[idx[k]];
            pontos[k * 2 + 1] = this.sy[idx[k]];
          }
        }

        const area = areaComSinal(pontos);
        if (!face.dupla && area >= 0) continue;
        if (Math.abs(area) < 0.35) continue;

        // Normal no espaço da câmera, para a luz.
        const a0 = idx[0], a1 = idx[1], a2 = idx[2];
        let ex1 = this.cx[a1] - this.cx[a0], ey1 = this.cy[a1] - this.cy[a0], ez1 = this.cz[a1] - this.cz[a0];
        let ex2 = this.cx[a2] - this.cx[a0], ey2 = this.cy[a2] - this.cy[a0], ez2 = this.cz[a2] - this.cz[a0];
        let nx = ey1 * ez2 - ez1 * ey2;
        let ny = ez1 * ex2 - ex1 * ez2;
        let nz = ex1 * ey2 - ey1 * ex2;
        const comp = Math.hypot(nx, ny, nz) || 1;
        nx /= comp; ny /= comp; nz /= comp;
        if (face.dupla && (nx * this.cx[a0] + ny * this.cy[a0] + nz * this.cz[a0]) > 0) {
          nx = -nx; ny = -ny; nz = -nz;
        }

        let brilho;
        if (face.semLuz) {
          brilho = 1;
        } else {
          const difusa = Math.max(0, nx * lx + ny * ly + nz * lz);
          // Um toque de luz vinda de baixo evita que a sombra vire buraco preto.
          const rebote = Math.max(0, -ny) * 0.12;
          brilho = (ambienteLuz + difusa * (1 - ambienteLuz) + rebote) * intensidade;
        }

        let cor = face.cor;
        if (inst.tinta !== undefined) cor = inst.tinta;
        this.poligonos.push({
          pontos,
          profundidade,
          cor: tonalizar(cor, limitar(brilho, 0.06, 1.9)),
          emissiva: face.emissiva,
          opacidade: inst.opacidade === undefined ? 1 : inst.opacidade,
        });
      }
    }

    this.poligonos.sort((a, b) => b.profundidade - a.profundidade);
    return this;
  }

  /** Sutherland-Hodgman contra um plano só: o de perto. */
  recortar(idx, perto, meiaL, meiaA, kx, ky) {
    const saida = [];
    const n = idx.length;
    for (let k = 0; k < n; k++) {
      const atual = idx[k];
      const proximo = idx[(k + 1) % n];
      const za = this.cz[atual], zb = this.cz[proximo];
      const dentroA = za > perto, dentroB = zb > perto;

      if (dentroA) saida.push(this.cx[atual], this.cy[atual], za);
      if (dentroA !== dentroB) {
        const t = (perto - za) / (zb - za);
        saida.push(
          this.cx[atual] + (this.cx[proximo] - this.cx[atual]) * t,
          this.cy[atual] + (this.cy[proximo] - this.cy[atual]) * t,
          perto,
        );
      }
    }
    if (saida.length < 9) return null;
    const pontos = new Float32Array((saida.length / 3) * 2);
    for (let k = 0, p = 0; k < saida.length; k += 3, p += 2) {
      const c = saida[k + 2];
      pontos[p] = meiaL + (saida[k] / c) * kx;
      pontos[p + 1] = meiaA + (saida[k + 1] / c) * ky;
    }
    return pontos;
  }

  pintar(ctx) {
    let opacidadeAtual = 1;
    for (const p of this.poligonos) {
      if (p.opacidade !== opacidadeAtual) {
        ctx.globalAlpha = p.opacidade;
        opacidadeAtual = p.opacidade;
      }
      ctx.fillStyle = corTexto(p.cor);
      const pts = p.pontos;
      ctx.beginPath();
      ctx.moveTo(pts[0], pts[1]);
      for (let i = 2; i < pts.length; i += 2) ctx.lineTo(pts[i], pts[i + 1]);
      ctx.closePath();
      ctx.fill();
      // Um fio da mesma cor fecha a costura entre polígonos vizinhos.
      ctx.strokeStyle = ctx.fillStyle;
      ctx.lineWidth = 0.7;
      ctx.stroke();
    }
    if (opacidadeAtual !== 1) ctx.globalAlpha = 1;
  }
}

function areaComSinal(pontos) {
  let soma = 0;
  const n = pontos.length / 2;
  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n;
    soma += pontos[i * 2] * pontos[j * 2 + 1] - pontos[j * 2] * pontos[i * 2 + 1];
  }
  return soma / 2;
}

/**
 * Matriz que leva o vértice local direto para o espaço da câmera.
 * Gira (guinada, depois inclinação, depois rolagem), escala, translada e já
 * aplica a base da câmera — nove multiplicações por vértice, e acabou.
 */
function matrizDaInstancia(inst, camera) {
  const s = inst.escala === undefined ? 1 : inst.escala;
  const cy = Math.cos(inst.guinada || 0), sy = Math.sin(inst.guinada || 0);
  const cp = Math.cos(inst.inclinacao || 0), sp = Math.sin(inst.inclinacao || 0);
  const cr = Math.cos(inst.rolagem || 0), sr = Math.sin(inst.rolagem || 0);

  // R = Ry * Rx * Rz
  const r00 = (cy * cr + sy * sp * sr) * s;
  const r01 = (-cy * sr + sy * sp * cr) * s;
  const r02 = (sy * cp) * s;
  const r10 = (cp * sr) * s;
  const r11 = (cp * cr) * s;
  const r12 = (-sp) * s;
  const r20 = (-sy * cr + cy * sp * sr) * s;
  const r21 = (sy * sr + cy * sp * cr) * s;
  const r22 = (cy * cp) * s;

  const dx = camera.dx, dy = camera.dy, dz = camera.dz;
  const bx = -camera.cx, by = -camera.cy, bz = -camera.cz;   // "baixo" = -cima
  const fx = camera.fx, fy = camera.fy, fz = camera.fz;

  const px = inst.x - camera.x, py = inst.y - camera.y, pz = inst.z - camera.z;

  return {
    m00: dx * r00 + dy * r10 + dz * r20,
    m01: dx * r01 + dy * r11 + dz * r21,
    m02: dx * r02 + dy * r12 + dz * r22,
    m10: bx * r00 + by * r10 + bz * r20,
    m11: bx * r01 + by * r11 + bz * r21,
    m12: bx * r02 + by * r12 + bz * r22,
    m20: fx * r00 + fy * r10 + fz * r20,
    m21: fx * r01 + fy * r11 + fz * r21,
    m22: fx * r02 + fy * r12 + fz * r22,
    tx: px * dx + py * dy + pz * dz,
    ty: px * bx + py * by + pz * bz,
    tz: px * fx + py * fy + pz * fz,
  };
}

/**
 * Sombras. Não são projeção de verdade: é uma mancha achatada no chão embaixo
 * de cada coisa. Custa quase nada e é o que gruda o objeto no piso — sem ela
 * tudo parece flutuando um palmo acima da rua.
 */
export function desenharSombras(ctx, camera, sombras, ambiente, largura, altura) {
  const forca = ambiente.forcaSombra === undefined ? 0.34 : ambiente.forcaSombra;
  if (forca <= 0.01) return;
  const meiaL = largura / 2, meiaA = altura / 2;
  const kx = meiaL / (camera.tanMeio * camera.aspecto);
  const ky = meiaA / camera.tanMeio;

  ctx.save();
  ctx.fillStyle = '#000';
  for (const s of sombras) {
    const cg = Math.cos(s.guinada), sg = Math.sin(s.guinada);
    const hl = s.largura / 2, hc = s.comprimento / 2;
    let visivel = true;
    const pontos = [];
    for (const [a, b] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
      const ox = cg * (hl * a) + sg * (hc * b) * -1;
      const oz = -sg * (hl * a) + cg * (hc * b) * -1;
      const wx = s.x + ox, wz = s.z + oz;
      const rx = wx - camera.x, ry = 0.02 - camera.y, rz = wz - camera.z;
      const cz = rx * camera.fx + ry * camera.fy + rz * camera.fz;
      if (cz <= camera.perto) { visivel = false; break; }
      const cxx = rx * camera.dx + ry * camera.dy + rz * camera.dz;
      const cyy = -(rx * camera.cx + ry * camera.cy + rz * camera.cz);
      pontos.push(meiaL + (cxx / cz) * kx, meiaA + (cyy / cz) * ky);
    }
    if (!visivel) continue;
    ctx.globalAlpha = forca * (s.forca === undefined ? 1 : s.forca);
    ctx.beginPath();
    ctx.moveTo(pontos[0], pontos[1]);
    for (let i = 2; i < pontos.length; i += 2) ctx.lineTo(pontos[i], pontos[i + 1]);
    ctx.closePath();
    ctx.fill();
  }
  ctx.restore();
}

/** Halos: farol, lanterna, poste. Somados por cima de tudo. */
export function desenharLuzes(ctx, camera, luzes, largura, altura) {
  if (!luzes.length) return;
  const meiaL = largura / 2, meiaA = altura / 2;
  const kx = meiaL / (camera.tanMeio * camera.aspecto);
  const ky = meiaA / camera.tanMeio;

  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  for (const luz of luzes) {
    const rx = luz.x - camera.x, ry = luz.y - camera.y, rz = luz.z - camera.z;
    const cz = rx * camera.fx + ry * camera.fy + rz * camera.fz;
    if (cz <= camera.perto || cz > 220) continue;
    const cxx = rx * camera.dx + ry * camera.dy + rz * camera.dz;
    const cyy = -(rx * camera.cx + ry * camera.cy + rz * camera.cz);
    const px = meiaL + (cxx / cz) * kx;
    const py = meiaA + (cyy / cz) * ky;
    if (px < -200 || px > largura + 200 || py < -200 || py > altura + 200) continue;

    // Duas travas: o halo não pode encher a tela quando a luz está colada na
    // câmera, e some de vez quando está perto demais para fazer sentido.
    const raio = limitar((luz.raio * ky) / cz, 3, altura * 0.20);
    const queda = limitar(1 - cz / 200, 0, 1) * limitar((cz - 1.2) / 2.5, 0, 1);
    const g = ctx.createRadialGradient(px, py, 0, px, py, raio);
    const cor = corTexto(luz.cor);
    g.addColorStop(0, cor);
    g.addColorStop(0.35, cor + '66');
    g.addColorStop(1, cor + '00');
    ctx.globalAlpha = limitar(luz.forca * queda, 0, 1);
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(px, py, raio, 0, TAU);
    ctx.fill();
  }
  ctx.restore();
}
