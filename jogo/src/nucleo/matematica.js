// Números, ângulos e sorteio. Nada aqui sabe que existe um jogo.

export const TAU = Math.PI * 2;
export const GRAU = Math.PI / 180;

export function limitar(v, minimo, maximo) {
  return v < minimo ? minimo : v > maximo ? maximo : v;
}

export function misturar(a, b, t) {
  return a + (b - a) * t;
}

/** Caminha de `atual` para `alvo` no máximo `passo` por chamada. */
export function aproximar(atual, alvo, passo) {
  if (atual < alvo) return Math.min(atual + passo, alvo);
  return Math.max(atual - passo, alvo);
}

export function suavizar(t) {
  const x = limitar(t, 0, 1);
  return x * x * (3 - 2 * x);
}

/** Traz o ângulo para a faixa (-PI, PI]. */
export function normalizarAngulo(a) {
  let r = (a + Math.PI) % TAU;
  if (r < 0) r += TAU;
  return r - Math.PI;
}

/** Interpola ângulos pelo caminho curto — sem dar a volta ao passar de PI. */
export function misturarAngulo(a, b, t) {
  return a + normalizarAngulo(b - a) * t;
}

export function sinal(v) {
  return v > 0 ? 1 : v < 0 ? -1 : 0;
}

/**
 * Sorteio com semente (xorshift32). A mesma semente devolve sempre a mesma
 * sequência: é assim que uma missão consegue ser sorteada e mesmo assim
 * poder ser repetida igualzinha.
 */
export function criarSorteio(semente) {
  let s = (semente >>> 0) || 0x9e3779b9;
  return function sortear() {
    s ^= s << 13; s >>>= 0;
    s ^= s >>> 17;
    s ^= s << 5; s >>>= 0;
    return s / 4294967296;
  };
}

export function entre(sortear, a, b) {
  return a + sortear() * (b - a);
}

export function inteiro(sortear, a, b) {
  return Math.floor(a + sortear() * (b - a + 1));
}

export function escolher(sortear, lista) {
  return lista[Math.floor(sortear() * lista.length) % lista.length];
}

/** Embaralha uma cópia da lista. */
export function embaralhar(sortear, lista) {
  const copia = lista.slice();
  for (let i = copia.length - 1; i > 0; i--) {
    const j = Math.floor(sortear() * (i + 1));
    [copia[i], copia[j]] = [copia[j], copia[i]];
  }
  return copia;
}

/** Distância no plano do chão (o Y não entra). */
export function distanciaPlana(ax, az, bx, bz) {
  const dx = ax - bx;
  const dz = az - bz;
  return Math.sqrt(dx * dx + dz * dz);
}

/**
 * Direção para onde o carro (ou a câmera) aponta, dado o ângulo de guinada.
 * Convenção usada no projeto inteiro: ângulo 0 aponta para -Z, e ângulo
 * crescente gira para a esquerda.
 */
export function frente(angulo) {
  return { x: -Math.sin(angulo), y: 0, z: -Math.cos(angulo) };
}

export function direita(angulo) {
  return { x: Math.cos(angulo), y: 0, z: -Math.sin(angulo) };
}

/** Componente de cor em hexadecimal, clareada ou escurecida por um fator. */
export function tonalizar(cor, fator) {
  const r = limitar(Math.round(((cor >> 16) & 255) * fator), 0, 255);
  const g = limitar(Math.round(((cor >> 8) & 255) * fator), 0, 255);
  const b = limitar(Math.round((cor & 255) * fator), 0, 255);
  return (r << 16) | (g << 8) | b;
}

export function misturarCor(a, b, t) {
  const ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
  const br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
  const r = Math.round(ar + (br - ar) * t);
  const g = Math.round(ag + (bg - ag) * t);
  const c = Math.round(ab + (bb - ab) * t);
  return (r << 16) | (g << 8) | c;
}

export function corTexto(cor) {
  return '#' + (cor & 0xffffff).toString(16).padStart(6, '0');
}

/** Ruído de valor em duas dimensões, suave e barato. Serve para manchas. */
export function ruido(x, y, semente = 0) {
  const xi = Math.floor(x), yi = Math.floor(y);
  const xf = x - xi, yf = y - yi;
  const s = suavizar(xf), t = suavizar(yf);
  const a = ruidoInteiro(xi, yi, semente);
  const b = ruidoInteiro(xi + 1, yi, semente);
  const c = ruidoInteiro(xi, yi + 1, semente);
  const d = ruidoInteiro(xi + 1, yi + 1, semente);
  return misturar(misturar(a, b, s), misturar(c, d, s), t);
}

function ruidoInteiro(x, y, semente) {
  let h = x * 374761393 + y * 668265263 + semente * 2147483647;
  h = (h ^ (h >>> 13)) >>> 0;
  h = Math.imul(h, 1274126177) >>> 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

export function formatarTempo(segundos) {
  const s = Math.max(0, segundos);
  const m = Math.floor(s / 60);
  const r = Math.floor(s % 60);
  return `${m}:${String(r).padStart(2, '0')}`;
}

export function formatarDinheiro(valor) {
  return 'R$ ' + Math.round(valor).toLocaleString('pt-BR');
}
