// O painel.
//
// Tudo desenhado no mesmo canvas do jogo — sem DOM por cima, para o toque não
// brigar com o arrasto do volante e para funcionar igual em tela cheia.
//
// A peça central é o volante, embaixo à esquerda. Ele é o controle e é o
// retrato do carro: o Besouro tem um aro de marfim de dois raios, a picape tem
// couro costurado, o elétrico tem um manche que nem redondo é.

import { desenharVolante } from '../jogo/volante.js';
import { paraKmh } from '../jogo/fisica.js';
import {
  TAU, limitar, corTexto, formatarTempo, formatarDinheiro,
  distanciaPlana, misturar,
} from '../nucleo/matematica.js';
import { alvoAtual } from '../jogo/missoes.js';
import { NOMES_DE_MODO } from '../motor/camera.js';

const FONTE = '"Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

export class Hud {
  constructor() {
    this.recados = [];
    this.piscaAlerta = 0;
  }

  recado(texto, cor = '#ffffff', duracao = 2.4) {
    this.recados.push({ texto, cor, restante: duracao, total: duracao });
    if (this.recados.length > 4) this.recados.shift();
  }

  atualizar(dt) {
    this.piscaAlerta += dt;
    for (const r of this.recados) r.restante -= dt;
    this.recados = this.recados.filter((r) => r.restante > 0);
  }

  desenhar(ctx, partida, entrada, ajustes) {
    const L = ctx.canvas.width;
    const A = ctx.canvas.height;
    const escala = Math.min(L, A * 1.55) / 900;
    const margem = Math.round(22 * escala);

    ctx.save();
    ctx.textBaseline = 'middle';
    ctx.lineJoin = 'round';

    const arranjo = this.arranjo(partida, escala, L, A, margem);

    this.cartaoDaMissao(ctx, partida, escala, margem);
    this.relogio(ctx, partida, escala, L, margem);
    this.bussola(ctx, partida, escala, L, margem);
    this.minimapa(ctx, partida, escala, L, margem);
    this.painelDoCarro(ctx, partida, escala, arranjo);
    this.controles(ctx, partida, entrada, escala, L, A, margem, arranjo);
    this.avisos(ctx, partida, escala, L, A);
    this.desenharRecados(ctx, escala, L, A);

    ctx.restore();
  }

  /**
   * Onde cada peça do painel fica. O volante e o mostrador andam juntos: são
   * um conjunto de instrumentos, e o carro no meio da tela precisa ficar livre.
   */
  arranjo(partida, escala, L, A, margem) {
    const naCabine = partida.camera.modo === 'cabine';
    const raioVolante = naCabine
      ? Math.min(190 * escala, Math.min(L * 0.3, A * 0.30))
      : Math.min(118 * escala, Math.min(L, A) * 0.19);
    const volante = {
      raio: raioVolante,
      x: naCabine ? L * 0.33 : margem + raioVolante + 12 * escala,
      y: naCabine ? A - raioVolante * 0.38 : A - margem - raioVolante - 8 * escala,
    };
    const raioMostrador = Math.min(56 * escala, raioVolante * 0.5);
    return {
      naCabine,
      volante,
      mostrador: {
        raio: raioMostrador,
        x: volante.x + raioVolante + raioMostrador + 26 * escala,
        y: A - margem - raioMostrador - 14 * escala,
      },
    };
  }

  // -------------------------------------------------------------------------

  cartaoDaMissao(ctx, partida, escala, margem) {
    const missao = partida.missao;
    const largura = 330 * escala;
    const altura = 92 * escala;
    caixa(ctx, margem, margem, largura, altura, 14 * escala, 'rgba(12,16,22,0.62)');

    ctx.fillStyle = '#7fd1ff';
    ctx.font = `600 ${Math.round(13 * escala)}px ${FONTE}`;
    ctx.textAlign = 'left';
    const d = missao.descritor;
    ctx.fillText(`${d.titulo.toUpperCase()} · ${partida.mundo.ficha.nome} · ${partida.ambiente.nome}`,
      margem + 16 * escala, margem + 20 * escala);

    ctx.fillStyle = '#ffffff';
    ctx.font = `500 ${Math.round(16 * escala)}px ${FONTE}`;
    const instrucao = this.instrucao(partida);
    ctx.fillText(recortar(ctx, instrucao, largura - 32 * escala),
      margem + 16 * escala, margem + 44 * escala);

    // Barra de andamento.
    const barraY = margem + altura - 22 * escala;
    const barraL = largura - 32 * escala;
    ctx.fillStyle = 'rgba(255,255,255,0.14)';
    ctx.fillRect(margem + 16 * escala, barraY, barraL, 6 * escala);
    ctx.fillStyle = '#36c96f';
    ctx.fillRect(margem + 16 * escala, barraY, barraL * limitar(missao.progresso, 0, 1), 6 * escala);

    if (missao.medidor) {
      const valor = limitar(missao.medidor.valor, 0, 1);
      const cor = valor > 0.6 ? '#7fd1ff' : valor > 0.3 ? '#e8b93a' : '#e8563a';
      ctx.fillStyle = 'rgba(255,255,255,0.45)';
      ctx.font = `500 ${Math.round(11 * escala)}px ${FONTE}`;
      ctx.textAlign = 'right';
      ctx.fillText(missao.medidor.rotulo, margem + largura - 16 * escala, barraY - 10 * escala);
      ctx.textAlign = 'left';
      ctx.fillStyle = 'rgba(255,255,255,0.14)';
      ctx.fillRect(margem + 16 * escala, barraY + 10 * escala, barraL, 4 * escala);
      ctx.fillStyle = cor;
      ctx.fillRect(margem + 16 * escala, barraY + 10 * escala, barraL * valor, 4 * escala);
    }
  }

  instrucao(partida) {
    const missao = partida.missao;
    if (missao.livre) return 'Sem relógio e sem cobrança. Ande por aí.';
    if (missao.aviso) return missao.aviso;
    switch (missao.descritor.tipo) {
      case 'baliza': return 'Encaixe o carro na vaga marcada e pare.';
      case 'vaga': return missao.deRe ? 'Entre de ré na vaga verde.' : 'Estacione na vaga verde.';
      case 'slalom': return `Portão ${missao.indice + 1} de ${missao.objetivos.length}.`;
      case 'escolta': {
        const d = missao.distanciaEscolta || 0;
        return `Siga o carro laranja · ${Math.round(d)} m`;
      }
      case 'economia': return `Ponto ${missao.indice + 1} de ${missao.pontos.length} · pé leve`;
      case 'carga': return `Ponto ${missao.indice + 1} de ${missao.pontos.length} · sem sacolejo`;
      default: return `Ponto ${missao.indice + 1} de ${missao.pontos.length}`;
    }
  }

  relogio(ctx, partida, escala, L, margem) {
    // Na rua livre não há contagem regressiva: o relógio vira cronômetro.
    const livre = partida.missao.livre;
    const restante = livre
      ? partida.tempo
      : Math.max(0, partida.missao.tempoLimite - partida.tempo);
    const apertado = !livre && restante < 10;
    const texto = formatarTempo(restante);
    const largura = 118 * escala;
    const x = L / 2 - largura / 2;

    caixa(ctx, x, margem, largura, 46 * escala, 12 * escala,
      apertado ? 'rgba(120,20,20,0.72)' : 'rgba(12,16,22,0.62)');
    ctx.textAlign = 'center';
    ctx.fillStyle = apertado && Math.sin(this.piscaAlerta * 9) > 0 ? '#ff6b5b' : '#ffffff';
    ctx.font = `700 ${Math.round(26 * escala)}px ${FONTE}`;
    ctx.fillText(texto, L / 2, margem + 24 * escala);
  }

  /** A seta que aponta o próximo objetivo, presa acima do relógio. */
  bussola(ctx, partida, escala, L, margem) {
    const alvo = alvoAtual(partida.missao);
    if (!alvo) return;
    const carro = partida.carro;
    const distancia = distanciaPlana(carro.x, carro.z, alvo.x, alvo.z);

    // Ângulo do alvo em relação à frente da CÂMERA — é o que a pessoa vê.
    const dx = alvo.x - carro.x;
    const dz = alvo.z - carro.z;
    const frenteX = -Math.sin(partida.camera.guinada);
    const frenteZ = -Math.cos(partida.camera.guinada);
    const adiante = dx * frenteX + dz * frenteZ;
    const lado = -dx * frenteZ + dz * frenteX;
    const angulo = Math.atan2(lado, adiante);

    const cx = L / 2;
    const cy = margem + 78 * escala;
    const raio = 20 * escala;

    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(angulo);
    ctx.fillStyle = distancia < 12 ? '#36c96f' : '#7fd1ff';
    ctx.beginPath();
    ctx.moveTo(0, -raio);
    ctx.lineTo(raio * 0.62, raio * 0.55);
    ctx.lineTo(0, raio * 0.22);
    ctx.lineTo(-raio * 0.62, raio * 0.55);
    ctx.closePath();
    ctx.fill();
    ctx.restore();

    ctx.textAlign = 'center';
    ctx.fillStyle = 'rgba(255,255,255,0.82)';
    ctx.font = `600 ${Math.round(13 * escala)}px ${FONTE}`;
    ctx.fillText(`${Math.round(distancia)} m`, cx, cy + raio + 12 * escala);
  }

  minimapa(ctx, partida, escala, L, margem) {
    const lado = 132 * escala;
    const x = L - margem - lado;
    const y = margem;
    const mapa = partida.mapa;
    const carro = partida.carro;

    ctx.save();
    ctx.beginPath();
    arredondado(ctx, x, y, lado, lado, 12 * escala);
    ctx.clip();
    ctx.fillStyle = '#10141a';
    ctx.fillRect(x, y, lado, lado);

    // Só um pedaço do mapa em volta do carro — o bairro inteiro fica ilegível.
    const alcance = 62;
    const fatia = (alcance * 2 / mapa.metros) * mapa.pixels;
    const centroU = (carro.x / mapa.metros + 0.5) * mapa.pixels;
    const centroV = (carro.z / mapa.metros + 0.5) * mapa.pixels;
    ctx.globalAlpha = 0.85;
    ctx.drawImage(mapa.tela, centroU - fatia / 2, centroV - fatia / 2, fatia, fatia, x, y, lado, lado);
    ctx.globalAlpha = 1;

    // A imagem do chão é pintada de dia; à noite o minimapa precisa escurecer
    // junto, senão vira um retângulo de sol no meio da tela escura.
    const luz = partida.ambiente.luz === undefined ? 1 : partida.ambiente.luz;
    if (luz < 0.85) {
      ctx.fillStyle = `rgba(6,10,18,${(0.85 - luz).toFixed(2)})`;
      ctx.fillRect(x, y, lado, lado);
    }

    const paraTela = (wx, wz) => ({
      x: x + lado / 2 + ((wx - carro.x) / alcance) * (lado / 2),
      y: y + lado / 2 + ((wz - carro.z) / alcance) * (lado / 2),
    });

    const alvo = alvoAtual(partida.missao);
    if (alvo) {
      const p = paraTela(alvo.x, alvo.z);
      ctx.fillStyle = '#36c96f';
      ctx.beginPath();
      ctx.arc(limitar(p.x, x + 6, x + lado - 6), limitar(p.y, y + 6, y + lado - 6), 5 * escala, 0, TAU);
      ctx.fill();
    }

    for (const outro of partida.transito) {
      const p = paraTela(outro.x, outro.z);
      if (p.x < x || p.x > x + lado || p.y < y || p.y > y + lado) continue;
      ctx.fillStyle = 'rgba(255,255,255,0.55)';
      ctx.fillRect(p.x - 2 * escala, p.y - 2 * escala, 4 * escala, 4 * escala);
    }

    // O carro, sempre no centro, virado para onde está indo.
    ctx.save();
    ctx.translate(x + lado / 2, y + lado / 2);
    ctx.rotate(-carro.angulo + Math.PI);
    ctx.fillStyle = '#ffd24a';
    ctx.beginPath();
    ctx.moveTo(0, -7 * escala);
    ctx.lineTo(5 * escala, 6 * escala);
    ctx.lineTo(-5 * escala, 6 * escala);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
    ctx.restore();

    ctx.strokeStyle = 'rgba(255,255,255,0.18)';
    ctx.lineWidth = 1.5 * escala;
    ctx.beginPath();
    arredondado(ctx, x, y, lado, lado, 12 * escala);
    ctx.stroke();
  }

  painelDoCarro(ctx, partida, escala, arranjo) {
    const carro = partida.carro;
    const raio = arranjo.mostrador.raio;
    const cx = arranjo.mostrador.x;
    const cy = arranjo.mostrador.y;

    ctx.beginPath();
    ctx.arc(cx, cy, raio, 0, TAU);
    ctx.fillStyle = 'rgba(10,13,18,0.72)';
    ctx.fill();

    const inicio = Math.PI * 0.78;
    const fim = Math.PI * 2.22;
    const maxima = 180;
    const kmh = Math.abs(paraKmh(carro.vx));

    // Arco de rotação, por fora do mostrador.
    const rotacao = limitar(carro.rotacao / carro.ficha.rotacaoMaxima, 0, 1);
    ctx.lineWidth = 5 * escala;
    ctx.strokeStyle = 'rgba(255,255,255,0.12)';
    ctx.beginPath();
    ctx.arc(cx, cy, raio - 3 * escala, inicio, fim);
    ctx.stroke();
    ctx.strokeStyle = rotacao > 0.88 ? '#ff5b4a' : '#7fd1ff';
    ctx.beginPath();
    ctx.arc(cx, cy, raio - 3 * escala, inicio, inicio + (fim - inicio) * rotacao);
    ctx.stroke();

    // Riscos de velocidade.
    ctx.strokeStyle = 'rgba(255,255,255,0.32)';
    ctx.lineWidth = 1.5 * escala;
    for (let v = 0; v <= maxima; v += 20) {
      const a = inicio + (fim - inicio) * (v / maxima);
      const r1 = raio - 10 * escala;
      const r2 = raio - (v % 40 === 0 ? 18 : 14) * escala;
      ctx.beginPath();
      ctx.moveTo(cx + Math.cos(a) * r1, cy + Math.sin(a) * r1);
      ctx.lineTo(cx + Math.cos(a) * r2, cy + Math.sin(a) * r2);
      ctx.stroke();
    }

    // Ponteiro.
    const a = inicio + (fim - inicio) * limitar(kmh / maxima, 0, 1);
    ctx.strokeStyle = '#ffd24a';
    ctx.lineWidth = 2.8 * escala;
    ctx.beginPath();
    ctx.moveTo(cx - Math.cos(a) * 7 * escala, cy - Math.sin(a) * 7 * escala);
    ctx.lineTo(cx + Math.cos(a) * (raio - 20 * escala), cy + Math.sin(a) * (raio - 20 * escala));
    ctx.stroke();

    ctx.textAlign = 'center';
    ctx.fillStyle = '#ffffff';
    ctx.font = `700 ${Math.round(25 * escala)}px ${FONTE}`;
    ctx.fillText(String(Math.round(kmh)), cx, cy - 2 * escala);
    ctx.fillStyle = 'rgba(255,255,255,0.45)';
    ctx.font = `600 ${Math.round(9 * escala)}px ${FONTE}`;
    ctx.fillText('km/h', cx, cy + 14 * escala);

    // Marcha, no alto do mostrador.
    const marcha = carro.sentido < 0 ? 'R' : carro.ficha.eletrico ? 'D' : String(carro.marcha);
    ctx.fillStyle = carro.sentido < 0 ? '#ff8b5b' : '#7fd1ff';
    ctx.font = `700 ${Math.round(15 * escala)}px ${FONTE}`;
    ctx.fillText(marcha, cx, cy - 24 * escala);

    // Combustível e lataria: duas tirinhas DENTRO do mostrador, para não
    // brigarem com o carro no meio da tela.
    const missao = partida.missao;
    const tanque = missao.combustivel !== undefined
      ? carro.combustivel / missao.combustivel
      : carro.combustivel / carro.ficha.tanque;
    const largura = raio * 0.52;
    const altura = 4.5 * escala;
    const y = cy + raio * 0.47;
    this.tirinha(ctx, cx - raio * 0.58, y, largura, altura,
      limitar(tanque, 0, 1), tanque < 0.2 ? '#e8563a' : '#5ad07a');
    this.tirinha(ctx, cx + raio * 0.06, y, largura, altura,
      1 - carro.dano, carro.dano > 0.66 ? '#e8563a' : '#c9ced6');
    ctx.fillStyle = 'rgba(255,255,255,0.38)';
    ctx.font = `600 ${Math.round(7.5 * escala)}px ${FONTE}`;
    ctx.fillText('COMB', cx - raio * 0.32, y - 6 * escala);
    ctx.fillText('LATA', cx + raio * 0.32, y - 6 * escala);
  }

  tirinha(ctx, x, y, largura, altura, valor, cor) {
    ctx.fillStyle = 'rgba(255,255,255,0.14)';
    ctx.fillRect(x, y, largura, altura);
    ctx.fillStyle = cor;
    ctx.fillRect(x, y, largura * limitar(valor, 0, 1), altura);
  }

  /** Volante, pedais, marcha e os botõezinhos. Tudo registrado na entrada. */
  controles(ctx, partida, entrada, escala, L, A, margem, arranjo) {
    // Na cabine o volante é O volante: fica maior e mais ao centro, como quem
    // está sentado atrás dele. Nas outras câmeras ele vira controle de canto.
    const raioVolante = arranjo.volante.raio;
    const vx = arranjo.volante.x;
    const vy = arranjo.volante.y;

    entrada.areaVolante = { x: vx, y: vy, raio: raioVolante };

    // Sombra no chão do volante, para ele não parecer colado na tela.
    ctx.save();
    ctx.globalAlpha = 0.25;
    ctx.fillStyle = '#000';
    ctx.beginPath();
    ctx.ellipse(vx, vy + raioVolante * 0.92, raioVolante * 0.85, raioVolante * 0.18, 0, 0, TAU);
    ctx.fill();
    ctx.restore();

    desenharVolante(ctx, partida.modelo.volante, {
      x: vx, y: vy, raio: raioVolante,
      angulo: entrada.volanteVisual,
      alfa: 0.96,
      destaque: entrada.arrasto ? 1 : 0,
    });

    // Pedais.
    const largura = 104 * escala;
    const altura = 78 * escala;
    const px = L - margem - largura;
    const pyAcelerador = A - margem - altura;
    const pyFreio = pyAcelerador - altura - 10 * escala;

    entrada.areaAcelerador = { x: px, y: pyAcelerador, largura, altura };
    entrada.areaFreio = { x: px - largura - 10 * escala, y: pyAcelerador, largura, altura };
    entrada.areaMarcha = { x: px, y: pyFreio, largura, altura: altura * 0.62 };
    entrada.areaMao = { x: px - largura - 10 * escala, y: pyFreio, largura, altura: altura * 0.62 };

    botao(ctx, entrada.areaAcelerador, 'ACELERA', entrada.tocando('acelerador'), escala, '#3fae62');
    botao(ctx, entrada.areaFreio, 'FREIO', entrada.tocando('freio'), escala, '#b4433a');
    botao(ctx, entrada.areaMarcha, partida.carro.sentido < 0 ? 'RÉ' : 'DRIVE',
      partida.carro.sentido < 0, escala, '#4a6b96');
    botao(ctx, entrada.areaMao, 'MÃO', entrada.tocando('mao'), escala, '#8a6b2f');

    // Cantinho de cima: câmera e pausa.
    const pequeno = 40 * escala;
    entrada.areaCamera = { x: L - margem - pequeno, y: margem + 144 * escala, largura: pequeno, altura: pequeno };
    entrada.areaPausa = { x: L - margem - pequeno * 2 - 8 * escala, y: margem + 144 * escala, largura: pequeno, altura: pequeno };
    botao(ctx, entrada.areaCamera, '◉', false, escala, '#2b3340', 15);
    botao(ctx, entrada.areaPausa, '❚❚', false, escala, '#2b3340', 13);

    ctx.textAlign = 'center';
    ctx.fillStyle = 'rgba(255,255,255,0.4)';
    ctx.font = `600 ${Math.round(9 * escala)}px ${FONTE}`;
    ctx.fillText(NOMES_DE_MODO[partida.camera.modo] || '', L - margem - pequeno / 2, margem + 144 * escala + pequeno + 9 * escala);
  }

  avisos(ctx, partida, escala, L, A) {
    const carro = partida.carro;
    const alertas = [];
    if (carro.dano > 0.7) alertas.push(['LATARIA NO LIMITE', '#e8563a']);
    if (partida.missao.combustivel !== undefined
      && carro.combustivel / partida.missao.combustivel < 0.15) alertas.push(['COMBUSTÍVEL', '#e8b93a']);
    if (partida.foraDaPista > 1.2) alertas.push(['VOLTE PARA A PISTA', '#7fd1ff']);
    if (!alertas.length) return;

    const piscar = Math.sin(this.piscaAlerta * 7) > -0.2;
    if (!piscar) return;
    ctx.textAlign = 'center';
    ctx.font = `700 ${Math.round(15 * escala)}px ${FONTE}`;
    alertas.forEach(([texto, cor], i) => {
      ctx.fillStyle = cor;
      ctx.fillText(texto, L / 2, A * 0.22 + i * 22 * escala);
    });
  }

  desenharRecados(ctx, escala, L, A) {
    ctx.textAlign = 'center';
    this.recados.forEach((r, i) => {
      const t = r.restante / r.total;
      ctx.globalAlpha = limitar(t * 3, 0, 1);
      ctx.fillStyle = r.cor;
      ctx.font = `700 ${Math.round(24 * escala)}px ${FONTE}`;
      ctx.fillText(r.texto, L / 2, A * 0.34 - i * 30 * escala + (1 - t) * 14 * escala);
    });
    ctx.globalAlpha = 1;
  }
}

// ---------------------------------------------------------------------------

export function caixa(ctx, x, y, largura, altura, raio, cor) {
  ctx.fillStyle = cor;
  ctx.beginPath();
  arredondado(ctx, x, y, largura, altura, raio);
  ctx.fill();
}

export function arredondado(ctx, x, y, largura, altura, raio) {
  const r = Math.min(raio, largura / 2, altura / 2);
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + largura - r, y);
  ctx.quadraticCurveTo(x + largura, y, x + largura, y + r);
  ctx.lineTo(x + largura, y + altura - r);
  ctx.quadraticCurveTo(x + largura, y + altura, x + largura - r, y + altura);
  ctx.lineTo(x + r, y + altura);
  ctx.quadraticCurveTo(x, y + altura, x, y + altura - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
}

export function botao(ctx, area, texto, ativo, escala, cor, tamanhoFonte = 13) {
  ctx.save();
  ctx.fillStyle = ativo ? cor : 'rgba(18,22,30,0.62)';
  ctx.beginPath();
  arredondado(ctx, area.x, area.y, area.largura, area.altura, 12 * escala);
  ctx.fill();
  ctx.strokeStyle = ativo ? 'rgba(255,255,255,0.7)' : 'rgba(255,255,255,0.22)';
  ctx.lineWidth = 1.8 * escala;
  ctx.stroke();
  ctx.fillStyle = ativo ? '#ffffff' : 'rgba(255,255,255,0.78)';
  ctx.font = `700 ${Math.round(tamanhoFonte * escala)}px ${FONTE}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(texto, area.x + area.largura / 2, area.y + area.altura / 2);
  ctx.restore();
}

function recortar(ctx, texto, largura) {
  if (ctx.measureText(texto).width <= largura) return texto;
  let corte = texto;
  while (corte.length > 4 && ctx.measureText(corte + '…').width > largura) {
    corte = corte.slice(0, -1);
  }
  return corte + '…';
}

export { FONTE, corTexto, formatarDinheiro, misturar };
