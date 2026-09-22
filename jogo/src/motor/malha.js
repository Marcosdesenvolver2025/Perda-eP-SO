// Peças 3D.
//
// Tudo no jogo é feito de blocos: caixa, caixa com o topo mais estreito e
// cilindro. É de propósito — é o mesmo tipo de modelo do jogo que serviu de
// referência, e é o que deixa o desenho legível numa tela de celular sem
// depender de textura nenhuma.
//
// Coordenadas locais: X para a direita, Y para cima, Z para trás. A frente do
// carro aponta para -Z.

export class Construtor {
  constructor() {
    this.vertices = [];
    this.faces = [];
  }

  ponto(x, y, z) {
    this.vertices.push(x, y, z);
    return this.vertices.length / 3 - 1;
  }

  face(indices, cor, opcoes = {}) {
    this.faces.push({
      i: indices,
      cor,
      emissiva: opcoes.emissiva || 0,
      semLuz: !!opcoes.semLuz,
      dupla: !!opcoes.dupla,
    });
    return this;
  }

  /** Caixa alinhada, com centro em (cx,cy,cz) e lados (sx,sy,sz). */
  caixa(cx, cy, cz, sx, sy, sz, cor, opcoes = {}) {
    return this.tronco(cx, cy, cz, sx, sy, sz, sx, sz, 0, 0, cor, opcoes);
  }

  /**
   * Bloco com o topo de outro tamanho e deslocado — é assim que saem capô
   * inclinado, cabine afunilada, caçamba e o pára-brisa deitado.
   */
  tronco(cx, cy, cz, sxBaixo, sy, szBaixo, sxCima, szCima, desvioX, desvioZ, cor, opcoes = {}) {
    const hb = sxBaixo / 2, hc = sxCima / 2;
    const pb = szBaixo / 2, pc = szCima / 2;
    const y0 = cy - sy / 2, y1 = cy + sy / 2;
    const dx = cx + desvioX, dz = cz + desvioZ;

    const b0 = this.ponto(cx - hb, y0, cz - pb);
    const b1 = this.ponto(cx + hb, y0, cz - pb);
    const b2 = this.ponto(cx + hb, y0, cz + pb);
    const b3 = this.ponto(cx - hb, y0, cz + pb);
    const t0 = this.ponto(dx - hc, y1, dz - pc);
    const t1 = this.ponto(dx + hc, y1, dz - pc);
    const t2 = this.ponto(dx + hc, y1, dz + pc);
    const t3 = this.ponto(dx - hc, y1, dz + pc);

    // Ordem dos vértices: anti-horária vista DE FORA. É o que permite
    // descartar a face virada para o outro lado sem calcular normal.
    const c = opcoes.cores || {};
    this.face([t3, t2, t1, t0], c.topo ?? cor, opcoes);            // topo (+Y)
    this.face([b0, b1, b2, b3], c.baixo ?? cor, opcoes);           // fundo (-Y)
    this.face([b1, b0, t0, t1], c.frente ?? cor, opcoes);          // frente (-Z)
    this.face([b3, b2, t2, t3], c.tras ?? cor, opcoes);            // trás (+Z)
    this.face([b0, b3, t3, t0], c.esquerda ?? cor, opcoes);        // esquerda (-X)
    this.face([b2, b1, t1, t2], c.direita ?? cor, opcoes);         // direita (+X)
    return this;
  }

  /** Cilindro deitado no eixo X — vira roda. */
  cilindro(cx, cy, cz, raio, comprimento, lados, cor, corLateral = cor) {
    const meio = comprimento / 2;
    const esquerda = [];
    const direita = [];
    for (let i = 0; i < lados; i++) {
      const a = (i / lados) * Math.PI * 2;
      const y = Math.sin(a) * raio;
      const z = Math.cos(a) * raio;
      esquerda.push(this.ponto(cx - meio, cy + y, cz + z));
      direita.push(this.ponto(cx + meio, cy + y, cz + z));
    }
    for (let i = 0; i < lados; i++) {
      const j = (i + 1) % lados;
      this.face([esquerda[j], esquerda[i], direita[i], direita[j]], cor);
    }
    this.face(direita.slice().reverse(), corLateral);
    this.face(esquerda.slice(), corLateral);
    return this;
  }

  /** Placa fina paralela ao chão (sombra de teto, faixa, tampa). */
  placa(cx, cy, cz, sx, sz, cor, opcoes = {}) {
    const hx = sx / 2, hz = sz / 2;
    const a = this.ponto(cx - hx, cy, cz - hz);
    const b = this.ponto(cx + hx, cy, cz - hz);
    const c = this.ponto(cx + hx, cy, cz + hz);
    const d = this.ponto(cx - hx, cy, cz + hz);
    this.face([a, b, c, d], cor, { ...opcoes, dupla: true });
    return this;
  }

  /** Painel vertical de frente para -Z (lanterna, farol, vidro). */
  painel(cx, cy, cz, sx, sy, cor, opcoes = {}) {
    const hx = sx / 2, hy = sy / 2;
    const a = this.ponto(cx - hx, cy - hy, cz);
    const b = this.ponto(cx + hx, cy - hy, cz);
    const c = this.ponto(cx + hx, cy + hy, cz);
    const d = this.ponto(cx - hx, cy + hy, cz);
    this.face([a, b, c, d], cor, { ...opcoes, dupla: true });
    return this;
  }

  terminar() {
    return {
      vertices: Float32Array.from(this.vertices),
      faces: this.faces,
    };
  }
}

/** Junta várias malhas numa só, para não pagar transformação repetida. */
export function juntar(malhas) {
  const c = new Construtor();
  for (const m of malhas) {
    const base = c.vertices.length / 3;
    for (let i = 0; i < m.vertices.length; i++) c.vertices.push(m.vertices[i]);
    for (const f of m.faces) {
      c.faces.push({ ...f, i: f.i.map((k) => k + base) });
    }
  }
  return c.terminar();
}

/** Caixa envolvente, usada por sombra e por colisão. */
export function limites(malha) {
  const v = malha.vertices;
  let minX = Infinity, minY = Infinity, minZ = Infinity;
  let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;
  for (let i = 0; i < v.length; i += 3) {
    if (v[i] < minX) minX = v[i];
    if (v[i] > maxX) maxX = v[i];
    if (v[i + 1] < minY) minY = v[i + 1];
    if (v[i + 1] > maxY) maxY = v[i + 1];
    if (v[i + 2] < minZ) minZ = v[i + 2];
    if (v[i + 2] > maxZ) maxZ = v[i + 2];
  }
  return { minX, minY, minZ, maxX, maxY, maxZ };
}
