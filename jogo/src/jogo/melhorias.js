// As melhorias do carro: motor, freio, pneu e câmbio.
//
// É a outra metade da garagem. Comprar carro é caro e acontece de vez em
// quando; melhorar o que você já tem é barato e acontece toda partida — e é o
// que dá para fazer com o dinheiro que sobra de um serviço.
//
// Cada melhoria tem cinco níveis e mexe num punhado de números da ficha do
// carro. A ficha original NUNCA é alterada: `fichaMelhorada()` devolve uma
// cópia. O catálogo em carros.js é a verdade sobre como o carro sai de fábrica,
// e um carro vendido não pode ficar diferente porque outro foi melhorado.

import { limitar } from '../nucleo/matematica.js';

export const NIVEL_MAXIMO = 5;

export const MELHORIAS = [
  {
    id: 'motor',
    nome: 'Motor',
    resumo: 'Mais torque e mais giro. O carro sai da curva puxando.',
    icone: 'motor',
    cor: '#e8563a',
    preco: 320,
  },
  {
    id: 'freio',
    nome: 'Freio',
    resumo: 'Para mais curto e trava menos. É o que ganha tempo de verdade.',
    icone: 'freio',
    cor: '#7fd1ff',
    preco: 260,
  },
  {
    id: 'pneu',
    nome: 'Pneu',
    resumo: 'Mais aderência: entra mais rápido e escorrega menos na chuva.',
    icone: 'pneu',
    cor: '#5ad07a',
    preco: 380,
  },
  {
    id: 'cambio',
    nome: 'Câmbio',
    resumo: 'Troca mais rápida e direção mais leve. O carro obedece antes.',
    icone: 'cambio',
    cor: '#e0a02a',
    preco: 300,
  },
];

export const POR_ID = new Map(MELHORIAS.map((m) => [m.id, m]));

/**
 * Quanto custa subir do nível que está para o próximo.
 *
 * O preço sobe junto com o nível: o primeiro ponto é quase de graça e o
 * último dói. É o que evita que o jogador maximize tudo no primeiro serviço e
 * fique sem nada para querer depois.
 */
export function precoDoProximo(melhoria, nivelAtual) {
  if (nivelAtual >= NIVEL_MAXIMO) return null;
  const ficha = POR_ID.get(melhoria);
  if (!ficha) return null;
  return Math.round(ficha.preco * (1 + nivelAtual * 1.15));
}

/** Os níveis de um carro, sempre completos e sempre dentro da faixa. */
export function niveisDe(progresso, idDoCarro) {
  const guardado = (progresso.melhorias && progresso.melhorias[idDoCarro]) || {};
  const saida = {};
  for (const m of MELHORIAS) {
    saida[m.id] = limitar(Math.round(guardado[m.id] || 0), 0, NIVEL_MAXIMO);
  }
  return saida;
}

/** Soma dos níveis, de 0 a 20 — é o número que a garagem mostra como "nível". */
export function totalDeNiveis(niveis) {
  return MELHORIAS.reduce((soma, m) => soma + (niveis[m.id] || 0), 0);
}

/**
 * A classe do carro, à moda do jogo de referência: uma letra que resume o
 * conjunto. Sai do preço de fábrica somado às melhorias, porque um popular
 * todo turbinado deixa de ser popular.
 */
export function classeDe(modelo, niveis) {
  const pontos = modelo.preco / 6000 + totalDeNiveis(niveis) * 0.55;
  if (pontos >= 11) return 'S';
  if (pontos >= 7.5) return 'A';
  if (pontos >= 4.5) return 'B';
  if (pontos >= 2) return 'C';
  return 'D';
}

/**
 * A ficha do carro com as melhorias aplicadas.
 *
 * Devolve uma CÓPIA. Mexer na ficha do catálogo faria a melhoria vazar para o
 * trânsito, para os carros estacionados e para a prévia da garagem — todos
 * usam o mesmo objeto.
 */
export function fichaMelhorada(modelo, niveis) {
  const f = { ...modelo.ficha };
  const n = niveis || {};
  const motor = n.motor || 0;
  const freio = n.freio || 0;
  const pneu = n.pneu || 0;
  const cambio = n.cambio || 0;

  // Cada bloco só roda se houve compra. Nível zero tem que devolver a ficha de
  // fábrica IGUALZINHA, e um `Math.min` de segurança aplicado de graça já
  // quebrou isso uma vez: no elétrico, que nunca troca de marcha, o ponto de
  // troca é um número enorme de propósito, e o teto o derrubava para dentro da
  // faixa — dando marcha a um carro que não tem marcha.
  if (motor > 0) {
    // 12% por nível é o bastante para sentir sem virar outro carro.
    f.torqueMaximo = f.torqueMaximo * (1 + motor * 0.12);
    f.rotacaoMaxima = f.rotacaoMaxima * (1 + motor * 0.035);
    if (f.relacoes.length > 1) {
      f.rotacaoTroca = Math.min(f.rotacaoTroca * (1 + motor * 0.035), f.rotacaoMaxima * 0.95);
    }
    f.consumoBase = f.consumoBase * (1 + motor * 0.05);   // potência bebe
  }

  if (freio > 0) {
    f.forcaFreio = f.forcaFreio * (1 + freio * 0.15);
    f.freioMotor = f.freioMotor * (1 + freio * 0.06);
  }

  if (pneu > 0) {
    // Os dois eixos juntos, mantendo o equilíbrio: mexer só num mudaria o
    // caráter do carro, e o caráter é do carro, não da loja.
    f.aderencia = f.aderencia * (1 + pneu * 0.058);
    f.rigidezFrente = f.rigidezFrente * (1 + pneu * 0.05);
    f.rigidezTras = f.rigidezTras * (1 + pneu * 0.05);
    f.rolamento = f.rolamento * (1 - pneu * 0.03);
  }

  if (cambio > 0) {
    f.trocaMaisRapida = cambio * 0.14;
    f.velocidadeVolante = f.velocidadeVolante * (1 + cambio * 0.07);
    f.rendimento = Math.min(0.96, f.rendimento * (1 + cambio * 0.018));
  }

  return f;
}

/**
 * Compra um ponto de melhoria. Devolve o que aconteceu, para a tela dizer.
 */
export function comprarMelhoria(progresso, idDoCarro, idDaMelhoria) {
  if (!POR_ID.has(idDaMelhoria)) return { ok: false, motivo: 'não existe' };
  const niveis = niveisDe(progresso, idDoCarro);
  const atual = niveis[idDaMelhoria];
  if (atual >= NIVEL_MAXIMO) return { ok: false, motivo: 'já está no máximo' };

  const preco = precoDoProximo(idDaMelhoria, atual);
  if (progresso.dinheiro < preco) return { ok: false, motivo: 'falta dinheiro', preco };

  progresso.dinheiro -= preco;
  if (!progresso.melhorias) progresso.melhorias = {};
  if (!progresso.melhorias[idDoCarro]) progresso.melhorias[idDoCarro] = {};
  progresso.melhorias[idDoCarro][idDaMelhoria] = atual + 1;
  return { ok: true, nivel: atual + 1, preco };
}
