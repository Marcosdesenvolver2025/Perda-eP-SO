// Desenho do volante.
//
// Todo volante do jogo sai daqui, a partir da ficha que cada carro traz em
// carros.js. Nenhum é igual a outro: muda o formato do aro (tem um que nem
// redondo é), o número e o feitio dos raios, a cor, a costura, o furo, o
// emblema no cubo. É o volante que você arrasta para dirigir, então ele é ao
// mesmo tempo a peça mais visível do carro e o controle do jogo.

import { TAU, corTexto, tonalizar } from '../nucleo/matematica.js';

const G = Math.PI / 180;

/**
 * Desenha um volante centrado em (x, y).
 *
 * `angulo` é a rotação em radianos — quanto o motorista virou.
 * `destaque` de 0 a 1 acende o volante (usado quando o dedo está em cima).
 */
export function desenharVolante(ctx, ficha, { x, y, raio, angulo = 0, alfa = 1, destaque = 0 }) {
  ctx.save();
  ctx.globalAlpha = alfa;
  ctx.translate(x, y);
  ctx.rotate(angulo);

  const pintor = ESTILOS[ficha.estilo] || ESTILOS.classico;
  pintor(ctx, ficha, raio * (ficha.raio || 1), destaque);

  ctx.restore();
}

/** Gradiente que dá volume ao aro: claro em cima à esquerda, escuro embaixo. */
function verniz(ctx, cor, raio, destaque) {
  const g = ctx.createLinearGradient(-raio, -raio, raio, raio);
  g.addColorStop(0, corTexto(tonalizar(cor, 1.45 + destaque * 0.5)));
  g.addColorStop(0.45, corTexto(tonalizar(cor, 1.05 + destaque * 0.3)));
  g.addColorStop(1, corTexto(tonalizar(cor, 0.62)));
  return g;
}

function aroCompleto(ctx, raio, espessura, cor, destaque) {
  ctx.lineWidth = espessura;
  ctx.strokeStyle = verniz(ctx, cor, raio, destaque);
  ctx.beginPath();
  ctx.arc(0, 0, raio, 0, TAU);
  ctx.stroke();
  contorno(ctx, raio, espessura, cor);
}

function contorno(ctx, raio, espessura, cor) {
  ctx.lineWidth = Math.max(1, espessura * 0.16);
  ctx.strokeStyle = corTexto(tonalizar(cor, 0.42));
  ctx.beginPath();
  ctx.arc(0, 0, raio + espessura / 2, 0, TAU);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(0, 0, raio - espessura / 2, 0, TAU);
  ctx.stroke();
}

/** Aro com o fundo cortado reto — o corte dos esportivos. */
function aroFundoReto(ctx, raio, espessura, cor, destaque, aberturaGraus = 52) {
  const a = aberturaGraus * G;
  ctx.lineWidth = espessura;
  ctx.lineCap = 'round';
  ctx.strokeStyle = verniz(ctx, cor, raio, destaque);
  ctx.beginPath();
  ctx.arc(0, 0, raio, (180 - aberturaGraus) * G, a + TAU);
  const px = Math.cos(a) * raio;
  const py = Math.sin(a) * raio;
  ctx.lineTo(-px, py);
  ctx.stroke();
  ctx.lineCap = 'butt';
}

function raiosRetos(ctx, quantidade, anguloInicial, raio, espessura, cor, largura) {
  ctx.fillStyle = cor;
  for (let i = 0; i < quantidade; i++) {
    const a = anguloInicial + (i * TAU) / quantidade;
    ctx.save();
    ctx.rotate(a);
    ctx.beginPath();
    ctx.moveTo(0, -largura / 2);
    ctx.lineTo(raio - espessura * 0.3, -largura * 0.34);
    ctx.lineTo(raio - espessura * 0.3, largura * 0.34);
    ctx.lineTo(0, largura / 2);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }
}

function cubo(ctx, ficha, raio) {
  const r = raio * 0.27;
  const g = ctx.createRadialGradient(-r * 0.3, -r * 0.35, r * 0.1, 0, 0, r);
  g.addColorStop(0, corTexto(tonalizar(ficha.corCubo, 1.7)));
  g.addColorStop(1, corTexto(tonalizar(ficha.corCubo, 0.7)));
  ctx.fillStyle = g;
  ctx.beginPath();
  ctx.arc(0, 0, r, 0, TAU);
  ctx.fill();
  ctx.lineWidth = Math.max(1, raio * 0.018);
  ctx.strokeStyle = corTexto(tonalizar(ficha.corCubo, 0.45));
  ctx.stroke();
  desenharEmblema(ctx, ficha.emblema, r * 0.62, ficha.corDetalhe);
}

function costurar(ctx, raio, espessura, cor, passos = 44) {
  ctx.strokeStyle = corTexto(cor);
  ctx.lineWidth = Math.max(1, espessura * 0.09);
  for (let i = 0; i < passos; i++) {
    const a = (i / passos) * TAU;
    const r1 = raio - espessura * 0.16;
    const r2 = raio + espessura * 0.16;
    ctx.beginPath();
    ctx.moveTo(Math.cos(a) * r1, Math.sin(a) * r1);
    ctx.lineTo(Math.cos(a) * r2, Math.sin(a) * r2);
    ctx.stroke();
  }
}

/** Os calos de apoio para o polegar, às dez e às duas. */
function apoios(ctx, raio, espessura, cor) {
  ctx.fillStyle = corTexto(tonalizar(cor, 0.78));
  for (const a of [-142 * G, -38 * G]) {
    ctx.save();
    ctx.rotate(a);
    ctx.beginPath();
    ctx.ellipse(raio, 0, espessura * 0.95, espessura * 0.62, 0, 0, TAU);
    ctx.fill();
    ctx.restore();
  }
}

function furosNoRaio(ctx, quantidade, anguloInicial, raio, cor) {
  ctx.fillStyle = corTexto(tonalizar(cor, 0.3));
  for (let i = 0; i < quantidade; i++) {
    const a = anguloInicial + (i * TAU) / quantidade;
    for (const d of [0.46, 0.64, 0.82]) {
      ctx.beginPath();
      ctx.arc(Math.cos(a) * raio * d, Math.sin(a) * raio * d, raio * 0.045, 0, TAU);
      ctx.fill();
    }
  }
}

const ESTILOS = {
  // Fusca: aro fino de marfim, dois raios deitados e o anel cromado da buzina.
  classico(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    aroCompleto(ctx, raio, esp, ficha.corAro, destaque);
    raiosRetos(ctx, 2, 0, raio, esp, corTexto(ficha.corRaio), raio * 0.14);
    if (ficha.buzinaAnel) {
      ctx.lineWidth = raio * 0.05;
      ctx.strokeStyle = corTexto(tonalizar(ficha.corCubo, 1.3));
      ctx.beginPath();
      ctx.arc(0, 0, raio * 0.52, 200 * G, 340 * G);
      ctx.stroke();
    }
    cubo(ctx, ficha, raio);
  },

  // Popular: plástico preto, três raios e calo para o polegar.
  'tres-raios'(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    aroCompleto(ctx, raio, esp, ficha.corAro, destaque);
    raiosRetos(ctx, 3, -90 * G, raio, esp, corTexto(ficha.corRaio), raio * 0.17);
    if (ficha.aperto) apoios(ctx, raio, esp, ficha.corAro);
    cubo(ctx, ficha, raio);
  },

  // Sedã: aro de madeira em duas faixas, quatro raios claros.
  'quatro-raios'(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    aroCompleto(ctx, raio, esp, ficha.corAro, destaque);
    if (ficha.madeira) {
      ctx.lineWidth = esp * 0.3;
      ctx.strokeStyle = corTexto(tonalizar(ficha.corAro, 1.5));
      ctx.beginPath();
      ctx.arc(0, 0, raio + esp * 0.22, 0, TAU);
      ctx.stroke();
      ctx.strokeStyle = corTexto(tonalizar(ficha.corAro, 0.65));
      ctx.beginPath();
      ctx.arc(0, 0, raio - esp * 0.24, 0, TAU);
      ctx.stroke();
    }
    raiosRetos(ctx, 4, -90 * G, raio, esp, corTexto(ficha.corRaio), raio * 0.11);
    cubo(ctx, ficha, raio);
  },

  // Picape: couro grosso, costura à vista, raios largos.
  'off-road'(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    aroCompleto(ctx, raio, esp, ficha.corAro, destaque);
    if (ficha.costura) costurar(ctx, raio, esp, ficha.corCostura, 52);
    raiosRetos(ctx, 3, -90 * G, raio, esp, corTexto(ficha.corRaio), raio * 0.21);
    if (ficha.aperto) apoios(ctx, raio, esp, ficha.corAro);
    cubo(ctx, ficha, raio);
  },

  // Esportivo: fundo chato, marca vermelha no topo, camurça costurada.
  esportivo(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    aroFundoReto(ctx, raio, esp, ficha.corAro, destaque, 54);
    if (ficha.costura) {
      ctx.strokeStyle = corTexto(ficha.corCostura);
      ctx.lineWidth = Math.max(1, esp * 0.08);
      for (let i = 0; i < 40; i++) {
        const a = -160 * G + (i / 40) * 320 * G;
        ctx.beginPath();
        ctx.moveTo(Math.cos(a) * (raio - esp * 0.18), Math.sin(a) * (raio - esp * 0.18));
        ctx.lineTo(Math.cos(a) * (raio + esp * 0.18), Math.sin(a) * (raio + esp * 0.18));
        ctx.stroke();
      }
    }
    if (ficha.marcador) {
      ctx.strokeStyle = corTexto(ficha.corMarcador);
      ctx.lineWidth = esp * 0.85;
      ctx.beginPath();
      ctx.arc(0, 0, raio, -99 * G, -81 * G);
      ctx.stroke();
    }
    raiosRetos(ctx, 2, 0, raio, esp, corTexto(ficha.corRaio), raio * 0.2);
    ctx.save();
    ctx.rotate(90 * G);
    ctx.fillStyle = corTexto(ficha.corRaio);
    ctx.beginPath();
    ctx.moveTo(0, -raio * 0.1);
    ctx.lineTo(raio * 0.72, -raio * 0.07);
    ctx.lineTo(raio * 0.72, raio * 0.07);
    ctx.lineTo(0, raio * 0.1);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
    if (ficha.aperto) apoios(ctx, raio, esp, ficha.corAro);
    cubo(ctx, ficha, raio);
  },

  // Furgão: prato grande e fino de ônibus, cinco raios vazados.
  onibus(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    aroCompleto(ctx, raio, esp, ficha.corAro, destaque);
    raiosRetos(ctx, 5, -90 * G, raio, esp, corTexto(ficha.corRaio), raio * 0.095);
    if (ficha.furos) furosNoRaio(ctx, 5, -90 * G, raio, ficha.corCubo);
    cubo(ctx, ficha, raio);
  },

  // Buggy: tubo amarelo de verdade — duas linhas, porque é um cano.
  duplo(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    aroCompleto(ctx, raio, esp, ficha.corAro, destaque);
    if (ficha.tubular) {
      ctx.lineWidth = Math.max(1, esp * 0.12);
      ctx.strokeStyle = corTexto(tonalizar(ficha.corAro, 1.7));
      ctx.beginPath();
      ctx.arc(0, 0, raio - esp * 0.2, 0, TAU);
      ctx.stroke();
      ctx.strokeStyle = corTexto(tonalizar(ficha.corAro, 0.55));
      ctx.beginPath();
      ctx.arc(0, 0, raio + esp * 0.2, 0, TAU);
      ctx.stroke();
    }
    raiosRetos(ctx, 4, -45 * G, raio, esp, corTexto(ficha.corRaio), raio * 0.13);
    if (ficha.furos) furosNoRaio(ctx, 4, -45 * G, raio, ficha.corAro);
    cubo(ctx, ficha, raio);
  },

  // Elétrico: manche. Nem é um círculo — tem barra em cima e fundo reto.
  yoke(ctx, ficha, raio, destaque) {
    const esp = raio * ficha.espessura * 2;
    const largura = raio * 1.02;
    const altura = raio * 0.80;
    const canto = raio * 0.36;

    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.lineWidth = esp;
    ctx.strokeStyle = verniz(ctx, ficha.corAro, raio, destaque);

    // Corpo em U: sai do topo, contorna pela esquerda, fundo reto, sobe à direita.
    ctx.beginPath();
    ctx.moveTo(-largura * 0.42, -altura);
    ctx.lineTo(-largura + canto, -altura);
    ctx.quadraticCurveTo(-largura, -altura, -largura, -altura + canto);
    ctx.lineTo(-largura, altura - canto);
    ctx.quadraticCurveTo(-largura, altura, -largura + canto, altura);
    ctx.lineTo(largura - canto, altura);
    ctx.quadraticCurveTo(largura, altura, largura, altura - canto);
    ctx.lineTo(largura, -altura + canto);
    ctx.quadraticCurveTo(largura, -altura, largura - canto, -altura);
    ctx.lineTo(largura * 0.42, -altura);
    ctx.stroke();

    // A barra fina que fecha o topo.
    ctx.lineWidth = esp * 0.42;
    ctx.beginPath();
    ctx.moveTo(-largura * 0.42, -altura);
    ctx.lineTo(largura * 0.42, -altura);
    ctx.stroke();

    // Braços até o cubo.
    ctx.lineWidth = esp * 0.9;
    ctx.strokeStyle = corTexto(ficha.corRaio);
    ctx.beginPath();
    ctx.moveTo(-largura * 0.92, 0);
    ctx.lineTo(largura * 0.92, 0);
    ctx.stroke();

    // Fita de luz: o detalhe que entrega que o carro é elétrico.
    ctx.lineWidth = Math.max(1.5, esp * 0.16);
    ctx.strokeStyle = corTexto(ficha.corDetalhe);
    ctx.beginPath();
    ctx.moveTo(-largura * 0.34, -altura + esp * 0.02);
    ctx.lineTo(largura * 0.34, -altura + esp * 0.02);
    ctx.stroke();

    if (ficha.aperto) {
      ctx.fillStyle = corTexto(tonalizar(ficha.corAro, 0.62));
      for (const lado of [-1, 1]) {
        ctx.beginPath();
        ctx.ellipse(lado * largura, -altura * 0.18, esp * 0.62, altura * 0.42, 0, 0, TAU);
        ctx.fill();
      }
    }
    ctx.lineCap = 'butt';
    cubo(ctx, ficha, raio);
  },
};

function desenharEmblema(ctx, tipo, r, cor) {
  ctx.save();
  ctx.fillStyle = corTexto(cor);
  ctx.strokeStyle = corTexto(cor);
  ctx.lineWidth = Math.max(1, r * 0.16);
  ctx.lineJoin = 'round';

  switch (tipo) {
    case 'besouro': // uma casquinha com duas antenas
      ctx.beginPath();
      ctx.ellipse(0, r * 0.12, r * 0.78, r * 0.6, 0, 0, TAU);
      ctx.fill();
      ctx.beginPath();
      ctx.moveTo(-r * 0.3, -r * 0.42); ctx.lineTo(-r * 0.62, -r * 0.92);
      ctx.moveTo(r * 0.3, -r * 0.42); ctx.lineTo(r * 0.62, -r * 0.92);
      ctx.stroke();
      break;
    case 'milho':
      ctx.beginPath();
      ctx.ellipse(0, 0, r * 0.42, r * 0.85, 0, 0, TAU);
      ctx.fill();
      break;
    case 'escudo':
      ctx.beginPath();
      ctx.moveTo(0, -r);
      ctx.lineTo(r * 0.8, -r * 0.45);
      ctx.lineTo(r * 0.55, r * 0.85);
      ctx.lineTo(0, r);
      ctx.lineTo(-r * 0.55, r * 0.85);
      ctx.lineTo(-r * 0.8, -r * 0.45);
      ctx.closePath();
      ctx.fill();
      break;
    case 'chifre':
      ctx.beginPath();
      ctx.moveTo(-r, -r * 0.1);
      ctx.quadraticCurveTo(-r * 0.5, -r * 0.95, 0, -r * 0.3);
      ctx.quadraticCurveTo(r * 0.5, -r * 0.95, r, -r * 0.1);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(0, r * 0.35, r * 0.34, 0, TAU);
      ctx.fill();
      break;
    case 'raio':
      ctx.beginPath();
      ctx.moveTo(r * 0.28, -r);
      ctx.lineTo(-r * 0.52, r * 0.16);
      ctx.lineTo(r * 0.02, r * 0.16);
      ctx.lineTo(-r * 0.24, r);
      ctx.lineTo(r * 0.6, -r * 0.2);
      ctx.lineTo(r * 0.04, -r * 0.2);
      ctx.closePath();
      ctx.fill();
      break;
    case 'pao':
      ctx.beginPath();
      ctx.ellipse(0, r * 0.1, r * 0.92, r * 0.5, 0, 0, TAU);
      ctx.fill();
      ctx.strokeStyle = corTexto(0x000000);
      ctx.globalAlpha = 0.35;
      for (const d of [-0.4, 0, 0.4]) {
        ctx.beginPath();
        ctx.moveTo(d * r - r * 0.16, -r * 0.16);
        ctx.lineTo(d * r + r * 0.16, r * 0.1);
        ctx.stroke();
      }
      break;
    case 'duna':
      ctx.beginPath();
      ctx.moveTo(-r, r * 0.5);
      ctx.quadraticCurveTo(-r * 0.3, -r * 0.7, r * 0.2, r * 0.2);
      ctx.quadraticCurveTo(r * 0.6, r * 0.6, r, -r * 0.1);
      ctx.lineTo(r, r * 0.6);
      ctx.lineTo(-r, r * 0.6);
      ctx.closePath();
      ctx.fill();
      break;
    case 'onda':
    default:
      ctx.beginPath();
      for (let i = 0; i <= 24; i++) {
        const t = i / 24;
        const px = -r + t * 2 * r;
        const py = Math.sin(t * Math.PI * 2) * r * 0.45;
        if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
      }
      ctx.stroke();
      break;
  }
  ctx.restore();
}

/** Miniatura para a garagem: o volante sozinho, sem contexto. */
export function miniaturaVolante(ficha, tamanho = 96) {
  const c = document.createElement('canvas');
  c.width = tamanho;
  c.height = tamanho;
  const ctx = c.getContext('2d');
  desenharVolante(ctx, ficha, { x: tamanho / 2, y: tamanho / 2, raio: tamanho * 0.40 });
  return c;
}
