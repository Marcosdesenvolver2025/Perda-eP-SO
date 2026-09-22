// O trânsito.
//
// Os outros carros não usam a física completa — seria caro e ninguém ia notar.
// Eles seguem o miolo da rua, olham para frente, e freiam quando alguém entra
// na frente. O que importa é que existam: uma rua vazia não tem tensão nenhuma,
// e é a rua vazia que faz o jogo cansar.

import { CARROS } from './carros.js';
import { construirCarro } from '../motor/modelos.js';
import {
  criarSorteio, entre, escolher, inteiro, limitar, normalizarAngulo, distanciaPlana,
} from '../nucleo/matematica.js';

const CORES = [
  0xd94f2b, 0x2e6fb7, 0xe6b422, 0xf2f2ef, 0x4a4f55, 0x3f8b5c,
  0x8e44ad, 0xc0392b, 0x16a085, 0x2c3e50, 0xdf7401,
];

export function criarTransito(mundo, semente, quantidade, opcoes = {}) {
  const sortear = criarSorteio(semente ^ 0x4f21);
  const carros = [];
  const rotas = mundo.rotas;
  if (!rotas.length) return carros;

  for (let i = 0; i < quantidade; i++) {
    const rota = rotas[i % rotas.length];
    const indice = inteiro(sortear, 0, rota.pontos.length - 1);
    const alvo = rota.pontos[indice];
    const modelo = escolher(sortear, CARROS.filter((c) => c.id !== 'faisca'));
    const pintura = escolher(sortear, CORES);
    const corpo = construirCarro(modelo, pintura);

    carros.push({
      modelo, corpo,
      cor: pintura,
      x: alvo.x, z: alvo.z,
      angulo: anguloPara(alvo, rota.pontos[(indice + 1) % rota.pontos.length]),
      velocidade: entre(sortear, 3, 7),
      alvoVelocidade: entre(sortear, opcoes.minimo || 4, opcoes.maximo || 9),
      rota, indice: (indice + 1) % rota.pontos.length,
      giroRoda: 0,
      esterco: 0,
      freando: false,
      pisca: 0,
      largura: modelo.ficha.largura,
      comprimento: modelo.ficha.comprimento,
    });
  }
  return carros;
}

function anguloPara(de, para) {
  const dx = para.x - de.x;
  const dz = para.z - de.z;
  return Math.atan2(-dx, -dz);
}

export function atualizarTransito(carros, dt, jogador, tempo) {
  for (const c of carros) {
    const alvo = c.rota.pontos[c.indice];
    if (distanciaPlana(c.x, c.z, alvo.x, alvo.z) < 5) {
      c.indice = (c.indice + 1) % c.rota.pontos.length;
    }

    const desejado = anguloPara(c, c.rota.pontos[c.indice]);
    const erro = normalizarAngulo(desejado - c.angulo);
    const taxa = limitar(erro * 2.2, -1.4, 1.4);
    c.esterco = taxa * 0.35;
    c.angulo += taxa * dt;

    // Olha para frente: o que estiver no caminho manda frear.
    const frenteX = -Math.sin(c.angulo), frenteZ = -Math.cos(c.angulo);
    let alvoVelocidade = c.alvoVelocidade;
    const obstaculos = jogador ? [jogador, ...carros] : carros;
    for (const outro of obstaculos) {
      if (outro === c) continue;
      const dx = outro.x - c.x, dz = outro.z - c.z;
      const adiante = dx * frenteX + dz * frenteZ;
      if (adiante < 0.5 || adiante > 16) continue;
      const lado = Math.abs(-dx * frenteZ + dz * frenteX);
      if (lado > 2.4) continue;
      alvoVelocidade = Math.min(alvoVelocidade, Math.max(0, (adiante - 5.5) * 1.5));
    }
    // Curva fechada pede pé no freio.
    alvoVelocidade *= limitar(1 - Math.abs(erro) * 0.8, 0.25, 1);

    const diferenca = alvoVelocidade - c.velocidade;
    c.freando = diferenca < -0.6;
    c.velocidade += limitar(diferenca, -9 * dt, 3.4 * dt);
    c.velocidade = Math.max(0, c.velocidade);

    c.x += frenteX * c.velocidade * dt;
    c.z += frenteZ * c.velocidade * dt;
    c.giroRoda += (c.velocidade / c.corpo.raioRoda) * dt;
    c.pisca = Math.abs(erro) > 0.25 ? (Math.sin(tempo * 9) > 0 ? 1 : 0) : 0;
  }
}

/** Os carros da IA também são parede para o jogador. */
export function colisoresDoTransito(carros) {
  return carros.map((c) => ({
    x: c.x, z: c.z, guinada: c.angulo,
    largura: c.largura, comprimento: c.comprimento,
    solido: true, altura: 1.5, transito: c,
  }));
}

export function instanciasDoTransito(carros, ambiente, instancias, sombras, luzes, camera) {
  for (const c of carros) {
    empilharCarro(instancias, sombras, luzes, {
      corpo: c.corpo, x: c.x, z: c.z, angulo: c.angulo,
      esterco: c.esterco, giroRoda: c.giroRoda,
      inclinacao: 0, rolagem: 0, freando: c.freando,
    }, ambiente, camera);
  }
}

/**
 * Transforma um carro (do jogador ou da IA) nas instâncias que a cena desenha:
 * carroceria, quatro rodas, a sombra e as luzes.
 */
export function empilharCarro(instancias, sombras, luzes, estado, ambiente, camera) {
  const corpo = estado.corpo;
  const cos = Math.cos(estado.angulo), sen = Math.sin(estado.angulo);

  // De onde a câmera olha o carro. Farol só ofusca quem está na frente dele;
  // sem esta conta o brilho dos faróis vaza através da própria lataria e vira
  // uma bola branca em cima do carro visto de trás.
  let deFrente = 1, deTras = 1;
  if (camera) {
    const px = camera.x - estado.x, pz = camera.z - estado.z;
    const distancia = Math.hypot(px, pz) || 1;
    const alinhamento = (px * -sen + pz * -cos) / distancia;
    deFrente = Math.max(0, alinhamento);
    deTras = Math.max(0, -alinhamento);
  }

  instancias.push({
    malha: corpo.carroceria,
    x: estado.x, y: 0, z: estado.z,
    guinada: estado.angulo,
    inclinacao: estado.inclinacao || 0,
    rolagem: estado.rolagem || 0,
    raio: corpo.raio,
  });

  for (const roda of corpo.rodas) {
    // O deslocamento local roda junto com o carro.
    const ox = cos * roda.x + sen * roda.z;
    const oz = -sen * roda.x + cos * roda.z;
    instancias.push({
      malha: corpo.roda,
      x: estado.x + ox,
      y: corpo.raioRoda,
      z: estado.z + oz,
      guinada: estado.angulo + (roda.dianteira ? estado.esterco || 0 : 0),
      inclinacao: estado.giroRoda || 0,
      rolagem: 0,
      raio: corpo.raioRoda * 1.6,
    });
  }

  sombras.push({
    x: estado.x, z: estado.z, guinada: estado.angulo,
    largura: corpo.dimensoes.largura * 1.06,
    comprimento: corpo.dimensoes.comprimento * 0.96,
  });

  const acender = (pontos, raio, cor, forca) => {
    if (forca < 0.06) return;
    for (const f of pontos) {
      luzes.push({
        x: estado.x + cos * f.x + sen * f.z,
        y: f.y,
        z: estado.z - sen * f.x + cos * f.z,
        raio, cor, forca,
      });
    }
  };

  if (ambiente.farois) acender(corpo.farois, 0.5, 0xfff0c8, 0.85 * deFrente);
  if (estado.freando) acender(corpo.lanternas, 0.45, 0xff3b2e, 0.95 * deTras);
  else if (ambiente.farois) acender(corpo.lanternas, 0.3, 0xb02018, 0.55 * deTras);
}

export { CORES as CORES_DE_CARRO };
