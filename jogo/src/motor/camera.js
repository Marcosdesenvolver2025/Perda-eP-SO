// A câmera.
//
// É uma câmera pinhole comum: posição, guinada, inclinação e abertura. O
// importante é que ela tem UMA base (direita, cima, frente) e todo o resto do
// motor usa essa mesma base — o chão desenhado em perspectiva e os objetos 3D
// saem exatamente alinhados porque usam a mesma conta.

import { GRAU, misturarAngulo, misturar, limitar } from '../nucleo/matematica.js';

export const MODOS = ['perseguicao', 'capo', 'cabine', 'alto'];

export const NOMES_DE_MODO = {
  perseguicao: 'atrás',
  capo: 'capô',
  cabine: 'cabine',
  alto: 'de cima',
};

export function criarCamera() {
  const c = {
    x: 0, y: 3, z: 10,
    guinada: 0,
    inclinacao: -0.16,
    fov: 64 * GRAU,
    aspecto: 16 / 9,
    perto: 0.25,
    modo: 'perseguicao',
    tremor: 0,
    // base, recalculada a cada quadro
    dx: 1, dy: 0, dz: 0,
    cx: 0, cy: 1, cz: 0,
    fx: 0, fy: 0, fz: -1,
    tanMeio: 0.6,
  };
  atualizarBase(c);
  return c;
}

export function atualizarBase(camera) {
  const sg = Math.sin(camera.guinada), cg = Math.cos(camera.guinada);
  const si = Math.sin(camera.inclinacao), ci = Math.cos(camera.inclinacao);

  // frente
  camera.fx = -ci * sg; camera.fy = si; camera.fz = -ci * cg;
  // direita
  camera.dx = cg; camera.dy = 0; camera.dz = -sg;
  // cima = direita × frente
  camera.cx = si * sg; camera.cy = ci; camera.cz = si * cg;

  camera.tanMeio = Math.tan(camera.fov / 2);
  return camera;
}

/**
 * Projeta um ponto do mundo na tela.
 * Devolve `z` = profundidade em metros; quem chama descarta o que está atrás.
 */
export function projetar(camera, px, py, pz, largura, altura, saida = {}) {
  const rx = px - camera.x, ry = py - camera.y, rz = pz - camera.z;
  const cz = rx * camera.fx + ry * camera.fy + rz * camera.fz;
  saida.z = cz;
  if (cz <= camera.perto) { saida.visivel = false; return saida; }
  const cx = rx * camera.dx + ry * camera.dy + rz * camera.dz;
  const cy = rx * camera.cx + ry * camera.cy + rz * camera.cz;
  const meiaL = largura / 2, meiaA = altura / 2;
  saida.x = meiaL + (cx / cz) * (meiaL / (camera.tanMeio * camera.aspecto));
  saida.y = meiaA - (cy / cz) * (meiaA / camera.tanMeio);
  saida.visivel = true;
  return saida;
}

/** Onde o horizonte cai na tela, em pixels. É a costura entre céu e chão. */
export function linhaDoHorizonte(camera, altura) {
  const ndc = -Math.tan(camera.inclinacao) / camera.tanMeio;
  return (altura / 2) * (1 - ndc);
}

const AJUSTES = {
  perseguicao: { distancia: 6.9, altura: 2.85, inclinacao: -0.185, mira: 1.0, suavidade: 6.5 },
  capo: { distancia: -1.2, altura: 1.28, inclinacao: -0.055, mira: 0.9, suavidade: 14 },
  cabine: { distancia: 0.35, altura: 1.20, inclinacao: -0.045, mira: 0.9, suavidade: 16 },
  alto: { distancia: 5.0, altura: 13.0, inclinacao: -0.86, mira: 0, suavidade: 5 },
};

/**
 * Cola a câmera no carro. O rabo de guinada é de propósito: a câmera demora um
 * instante para acompanhar o carro, e é esse atraso que dá a sensação de peso.
 */
export function seguir(camera, carro, dt, olharAtras = false) {
  const ajuste = AJUSTES[camera.modo] || AJUSTES.perseguicao;
  const velocidade = Math.abs(carro.vx);

  // Os números acima foram achados num sedã de 4,86 m por 1,42 m de altura.
  // Repetidos num caminhão de 5,90 m por 2,55 m, a câmera fica encostada no
  // baú e não se vê mais a rua; num esportivo rasteiro, fica longe demais.
  // Então tudo escala com o carro, ancorado nesse sedã.
  const f = carro.ficha || {};
  const comprimento = f.comprimento || 4.86;
  const alturaCarro = f.altura || 1.42;
  const puxada = camera.modo === 'perseguicao' ? Math.min(velocidade * 0.055, 1.5) : 0;

  let distancia;
  let altura;
  if (camera.modo === 'capo') {
    distancia = -comprimento * 0.247;
    altura = alturaCarro * 0.90;
  } else if (camera.modo === 'cabine') {
    distancia = comprimento * 0.072;
    altura = alturaCarro * 0.86;
  } else if (camera.modo === 'alto') {
    distancia = ajuste.distancia + comprimento * 0.2;
    altura = ajuste.altura + alturaCarro * 1.2;
  } else {
    distancia = comprimento * 1.42 + puxada;
    altura = alturaCarro * 1.35 + 0.93 + puxada * 0.18;
  }

  const alvoGuinada = carro.angulo + (olharAtras ? Math.PI : 0);
  const fator = 1 - Math.exp(-ajuste.suavidade * dt);
  if (camera.modo === 'perseguicao' || camera.modo === 'alto') {
    camera.guinada = misturarAngulo(camera.guinada, alvoGuinada, fator);
  } else {
    camera.guinada = alvoGuinada;
  }

  const sg = Math.sin(camera.guinada), cg = Math.cos(camera.guinada);
  // frente do ângulo da câmera: (-sen, -cos)
  const alvoX = carro.x + sg * distancia;
  const alvoZ = carro.z + cg * distancia;

  if (camera.modo === 'cabine' || camera.modo === 'capo') {
    // Dentro do carro a câmera é rígida — balançar aqui embrulha o estômago.
    camera.x = alvoX; camera.z = alvoZ;
    camera.y = altura;
  } else {
    const k = 1 - Math.exp(-ajuste.suavidade * dt);
    camera.x = misturar(camera.x, alvoX, k);
    camera.z = misturar(camera.z, alvoZ, k);
    camera.y = misturar(camera.y, altura, k);
  }

  let inclinacao = ajuste.inclinacao;
  if (camera.modo === 'perseguicao') {
    // Câmera mais alta tem que olhar mais para baixo, senão mira o céu por
    // cima do carro. A conta é a mesma que o olho faz sozinho.
    inclinacao -= Math.max(0, (altura - ajuste.altura) / Math.max(1, distancia)) * 0.55;
    inclinacao -= carro.inclinacao * 0.35;
  }
  if (camera.modo === 'cabine' || camera.modo === 'capo') inclinacao -= carro.inclinacao * 0.8;

  // Tremor: bate em algo e a imagem sacode.
  if (camera.tremor > 0.001) {
    const t = camera.tremor;
    camera.x += (Math.random() - 0.5) * t * 0.35;
    camera.y += (Math.random() - 0.5) * t * 0.25;
    inclinacao += (Math.random() - 0.5) * t * 0.03;
    camera.guinada += (Math.random() - 0.5) * t * 0.02;
    camera.tremor = Math.max(0, t - dt * 3.2);
  }

  camera.inclinacao = limitar(inclinacao, -1.35, 0.4);
  atualizarBase(camera);
  return camera;
}

export function sacudir(camera, forca) {
  camera.tremor = Math.min(1.6, camera.tremor + forca);
}
