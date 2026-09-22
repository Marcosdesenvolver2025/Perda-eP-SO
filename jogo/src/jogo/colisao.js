// Colisão.
//
// Todo mundo — carro, poste, prédio, muro, cone — é um retângulo com ângulo.
// O teste é o eixo separador: se existe uma direção em que as sombras dos dois
// retângulos não se tocam, eles não se tocam. Se não existe, a MENOR dessas
// sobreposições é exatamente por onde empurrar para separar os dois.

import { bater } from './fisica.js';
import { limitar } from '../nucleo/matematica.js';

function caixaDe(x, z, largura, comprimento, guinada) {
  const c = Math.cos(guinada), s = Math.sin(guinada);
  return {
    x, z,
    // eixos locais: "direita" e "frente"
    ex: c, ez: -s,
    fx: -s, fz: -c,
    meiaLargura: largura / 2,
    meioComprimento: comprimento / 2,
  };
}

export function caixaDoCarro(carro) {
  const f = carro.ficha;
  return caixaDe(carro.x, carro.z, f.largura, f.comprimento, carro.angulo);
}

export function caixaDoColisor(c) {
  return caixaDe(c.x, c.z, c.largura, c.comprimento, c.guinada || 0);
}

/**
 * Sobreposição entre dois retângulos girados. Devolve null quando não há
 * contato, ou { nx, nz, profundidade } — a normal aponta de A para B.
 */
export function sobreposicao(a, b) {
  const eixos = [
    [a.ex, a.ez], [a.fx, a.fz],
    [b.ex, b.ez], [b.fx, b.fz],
  ];
  const dx = b.x - a.x;
  const dz = b.z - a.z;

  let menor = Infinity;
  let nx = 0, nz = 0;

  for (const [ax, az] of eixos) {
    const distancia = Math.abs(dx * ax + dz * az);
    const alcanceA = Math.abs(a.ex * ax + a.ez * az) * a.meiaLargura
      + Math.abs(a.fx * ax + a.fz * az) * a.meioComprimento;
    const alcanceB = Math.abs(b.ex * ax + b.ez * az) * b.meiaLargura
      + Math.abs(b.fx * ax + b.fz * az) * b.meioComprimento;
    const folga = alcanceA + alcanceB - distancia;
    if (folga <= 0) return null;
    if (folga < menor) {
      menor = folga;
      const sentido = (dx * ax + dz * az) < 0 ? -1 : 1;
      nx = ax * sentido;
      nz = az * sentido;
    }
  }
  return { nx, nz, profundidade: menor };
}

/**
 * Resolve tudo que o carro está tocando neste quadro.
 * Devolve um relatório: quantas batidas, a mais forte, e o que foi derrubado.
 */
export function resolverColisoes(carro, colisores, aoBater) {
  const relatorio = { batidas: 0, forca: 0, derrubados: [] };
  const caixaCarro = caixaDoCarro(carro);
  const alcance = carro.ficha.comprimento + 6;

  for (const c of colisores) {
    if (c.removido) continue;
    if (Math.abs(c.x - carro.x) > alcance + c.largura
      || Math.abs(c.z - carro.z) > alcance + c.comprimento) continue;

    const outro = caixaDoColisor(c);
    const toque = sobreposicao(caixaCarro, outro);
    if (!toque) continue;

    const velocidade = Math.hypot(carro.vx, carro.vy);

    if (c.derrubavel) {
      // Cone: não para o carro, mas conta como erro.
      if (!c.derrubado) {
        c.derrubado = true;
        relatorio.derrubados.push(c);
      }
      continue;
    }

    // Empurra o carro para fora, na normal de menor sobreposição.
    carro.x -= toque.nx * (toque.profundidade + 0.005);
    carro.z -= toque.nz * (toque.profundidade + 0.005);
    caixaCarro.x = carro.x;
    caixaCarro.z = carro.z;

    const severidade = c.leve ? 0.35 : c.parede ? 1.0 : 0.8;

    // Batida é velocidade CONTRA a parede, não velocidade no geral.
    //
    // Enquanto isto media a velocidade total, quem encostasse o nariz no
    // guarda-corpo com o pé no acelerador levava uma batida por quadro: o
    // solver empurrava para fora, o motor empurrava de volta, e o carro se
    // destruía sozinho parado ali em doze segundos. Raspar a mureta de lado
    // tem que custar velocidade, não lataria.
    const seno = Math.sin(carro.angulo), cosseno = Math.cos(carro.angulo);
    const mundoX = -seno * carro.vx + cosseno * carro.vy;
    const mundoZ = -cosseno * carro.vx - seno * carro.vy;
    const aproximacao = mundoX * toque.nx + mundoZ * toque.nz;

    // E mesmo uma aproximação de verdade só conta uma vez a cada carência: um
    // toque dura vários quadros, e cada quadro não é uma batida nova.
    if (aproximacao > 0.55 && carro.toqueRecente <= 0) {
      carro.toqueRecente = 0.45;
      relatorio.batidas++;
      relatorio.forca = Math.max(relatorio.forca, aproximacao * severidade);
      bater(carro, -toque.nx, -toque.nz, severidade, aproximacao);
      if (aoBater) aoBater(c, aproximacao * severidade);
    } else {
      // Encostado: perde o que estava indo para dentro da parede e segue.
      if (aproximacao > 0) {
        const perde = aproximacao * 1.02;
        const px = mundoX - perde * toque.nx;
        const pz = mundoZ - perde * toque.nz;
        carro.vx = -seno * px - cosseno * pz;
        carro.vy = cosseno * px - seno * pz;
      }
      carro.vx *= 0.86;
      carro.vy *= 0.86;
    }

    if (c.leve && velocidade > 2.2) {
      c.removido = true;
      relatorio.derrubados.push(c);
    }
  }

  return relatorio;
}

/** O carro está inteiro dentro do retângulo? É o teste da vaga. */
export function dentroDe(carro, area) {
  const caixa = caixaDoCarro(carro);
  const alvo = caixaDe(area.x, area.z, area.largura, area.comprimento, area.angulo || 0);
  const cantos = [[-1, -1], [1, -1], [1, 1], [-1, 1]];
  for (const [a, b] of cantos) {
    const px = caixa.x + caixa.ex * caixa.meiaLargura * a + caixa.fx * caixa.meioComprimento * b;
    const pz = caixa.z + caixa.ez * caixa.meiaLargura * a + caixa.fz * caixa.meioComprimento * b;
    const dx = px - alvo.x, dz = pz - alvo.z;
    const local = dx * alvo.ex + dz * alvo.ez;
    const frente = dx * alvo.fx + dz * alvo.fz;
    if (Math.abs(local) > alvo.meiaLargura || Math.abs(frente) > alvo.meioComprimento) return false;
  }
  return true;
}

/** Quanto do carro está dentro do retângulo, de 0 a 1. Serve para dar nota. */
export function encaixe(carro, area) {
  const caixa = caixaDoCarro(carro);
  const alvo = caixaDe(area.x, area.z, area.largura, area.comprimento, area.angulo || 0);
  const cantos = [[-1, -1], [1, -1], [1, 1], [-1, 1]];
  let dentro = 0;
  let piorFolga = 0;
  for (const [a, b] of cantos) {
    const px = caixa.x + caixa.ex * caixa.meiaLargura * a + caixa.fx * caixa.meioComprimento * b;
    const pz = caixa.z + caixa.ez * caixa.meiaLargura * a + caixa.fz * caixa.meioComprimento * b;
    const dx = px - alvo.x, dz = pz - alvo.z;
    const lateral = Math.abs(dx * alvo.ex + dz * alvo.ez) - alvo.meiaLargura;
    const longitudinal = Math.abs(dx * alvo.fx + dz * alvo.fz) - alvo.meioComprimento;
    const folga = Math.max(lateral, longitudinal);
    if (folga <= 0) dentro++;
    else piorFolga = Math.max(piorFolga, folga);
  }
  return {
    cantosDentro: dentro,
    fracao: dentro / 4,
    folga: piorFolga,
    alinhamento: limitar(1 - Math.abs(anguloRelativo(carro.angulo, area.angulo || 0)) / (Math.PI / 4), 0, 1),
  };
}

function anguloRelativo(a, b) {
  let d = (a - b) % Math.PI;
  if (d > Math.PI / 2) d -= Math.PI;
  if (d < -Math.PI / 2) d += Math.PI;
  return d;
}
