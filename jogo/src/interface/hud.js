// O painel.
//
// Tudo desenhado no mesmo canvas do jogo — sem DOM por cima, para o toque não
// brigar com o arrasto do volante e para funcionar igual em tela cheia.
//
// A peça central é o volante, embaixo à esquerda. Ele é o controle e é o
// retrato do carro: o Besouro tem um aro de marfim de dois raios, a picape tem
// couro costurado, o elétrico tem um manche que nem redondo é.

import { desenharVolante } from '../jogo/volante.js';
import { tonalizar } from '../nucleo/matematica.js';
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
    this.brilhoDeTempo = 0;
    this.ultimoTempoGanho = 0;
  }

  recado(texto, cor = '#ffffff', duracao = 2.4) {
    this.recados.push({ texto, cor, restante: duracao, total: duracao });
    if (this.recados.length > 4) this.recados.shift();
  }

  atualizar(dt) {
    this.piscaAlerta += dt;
    this.brilhoDeTempo = Math.max(0, this.brilhoDeTempo - dt * 0.9);
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
    this.placar(ctx, partida, escala, L, margem);
    this.bussola(ctx, partida, escala, L, A, margem);
    this.minimapa(ctx, partida, escala, L, margem);
    // Na cabine o painel do carro tem velocímetro e marcador de verdade,
    // desenhados junto com o painel; o mostradorzinho aqui seria o segundo.
    if (!arranjo.naCabine) this.painelDoCarro(ctx, partida, escala, arranjo);
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
    // A mão DIREITA no volante, a ESQUERDA nos pedais — que é a arrumação dos
    // jogos de dirigir de celular e a que o dedão alcança sem soltar o
    // aparelho. Na cabine o volante cresce e desce, como quem está sentado
    // atrás dele.
    const naCabine = partida.camera.modo === 'cabine';
    const raioVolante = naCabine
      ? Math.min(178 * escala, Math.min(L * 0.28, A * 0.40))
      : Math.min(132 * escala, Math.min(L * 0.22, A * 0.34));
    // O volante fica no canto e sai um pouco da tela, como num carro de
    // verdade visto de cima — mas só um pouco: cortado demais ele deixa de
    // parecer um aro e vira um risco no canto.
    const volante = {
      raio: raioVolante,
      x: L - margem - raioVolante * 0.55,
      y: A - raioVolante * 0.40,
    };

    const larguraPedal = Math.min(96 * escala, L * 0.10);
    const alturaPedal = Math.min(150 * escala, A * 0.40);
    const pedais = {
      largura: larguraPedal,
      altura: alturaPedal,
      y: A - margem * 0.5 - alturaPedal,
      xFreio: margem * 0.7,
      xAcelerador: margem * 0.7 + larguraPedal + 10 * escala,
    };

    // A alavanca sobe a partir do TOPO do volante, encostada na borda direita.
    // Encavalada no aro ela roubava o toque do volante e ficava ilegível.
    const alturaLetra = Math.min(38 * escala, A * 0.10);
    const larguraCambio = Math.min(46 * escala, L * 0.05);
    const cambio = {
      largura: larguraCambio,
      alturaLetra,
      x: L - margem * 0.6 - larguraCambio,
      // Abaixo dos botões de câmera e pausa, que moram no mesmo canto: o
      // botão é desenhado depois e estava cobrindo o P da alavanca.
      y: Math.max(margem + 215 * escala,
        volante.y - raioVolante - alturaLetra * 4 - 10 * escala),
    };

    const raioMostrador = Math.min(62 * escala, A * 0.155);
    return {
      naCabine,
      volante,
      pedais,
      cambio,
      mostrador: {
        raio: raioMostrador,
        x: pedais.xAcelerador + larguraPedal + raioMostrador + 16 * escala,
        y: A - margem * 0.5 - raioMostrador - 6 * escala,
      },
    };
  }

  // -------------------------------------------------------------------------

  cartaoDaMissao(ctx, partida, escala, margem) {
    const missao = partida.missao;
    const largura = 330 * escala;
    const altura = 92 * escala;
    caixa(ctx, margem, margem, largura, altura, 18 * escala, 'rgba(14,20,30,0.66)');

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
    // Os modos escrevem a própria linha: ela muda a cada quadro (ângulo,
    // multiplicador, tempo de volta) e não cabe numa frase fixa por tipo.
    if (missao.instrucao) return missao.instrucao;
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

    caixa(ctx, x, margem, largura, 50 * escala, 18 * escala,
      apertado ? 'rgba(170,32,26,0.80)' : 'rgba(14,20,30,0.66)');
    ctx.textAlign = 'center';
    ctx.fillStyle = apertado && Math.sin(this.piscaAlerta * 9) > 0 ? '#ff6b5b' : '#ffffff';
    ctx.font = `700 ${Math.round(26 * escala)}px ${FONTE}`;
    ctx.fillText(texto, L / 2, margem + 26 * escala);
  }

  /**
   * O placar dos modos avulsos: o número que a pessoa está tentando fazer
   * subir. Fica logo abaixo do relógio porque relógio e placar são a mesma
   * pergunta vista dos dois lados — quanto tempo sobra, quanto já rendeu.
   */
  placar(ctx, partida, escala, L, margem) {
    const missao = partida.missao;
    if (missao.pontuacao === undefined) return;

    const d = missao.drift;
    const emDrift = !!(d && d.ativo);
    const largura = 186 * escala;
    const altura = 46 * escala;
    const x = L / 2 - largura / 2;
    const y = margem + 56 * escala;

    caixa(ctx, x, y, largura, altura, 15 * escala,
      emDrift ? 'rgba(126,62,10,0.80)' : 'rgba(14,20,30,0.66)');

    // No drift o número mostrado inclui o que ainda está PENDENTE: é isso que
    // faz doer perder o combo — a pessoa viu o número que ia ganhar.
    const pendente = d ? d.pendente * d.multiplicador : 0;
    const total = Math.round(missao.pontuacao + pendente);

    ctx.textAlign = 'center';
    ctx.fillStyle = emDrift ? '#ffd24a' : '#ffffff';
    ctx.font = `700 ${Math.round(22 * escala)}px ${FONTE}`;
    ctx.fillText(total.toLocaleString('pt-BR'), L / 2, y + 18 * escala);

    ctx.fillStyle = 'rgba(255,255,255,0.55)';
    ctx.font = `600 ${Math.round(10 * escala)}px ${FONTE}`;
    const rodape = emDrift
      ? `SEGURANDO  ×${d.multiplicador}`
      : `${missao.contador} ${missao.unidade === 'm' ? 'voltas' : missao.unidade}`;
    ctx.fillText(rodape.toUpperCase(), L / 2, y + 35 * escala);

    // Tempo que acabou de entrar no relógio, subindo e sumindo.
    if (missao.tempoGanho && missao.tempoGanho !== this.ultimoTempoGanho) {
      this.brilhoDeTempo = 1;
      this.ultimoTempoGanho = missao.tempoGanho;
    }
    if (this.brilhoDeTempo > 0) {
      ctx.globalAlpha = this.brilhoDeTempo;
      ctx.fillStyle = '#5ad07a';
      ctx.font = `700 ${Math.round(13 * escala)}px ${FONTE}`;
      ctx.fillText('+ TEMPO', L / 2 + largura * 0.42,
        y + 22 * escala - (1 - this.brilhoDeTempo) * 16 * escala);
      ctx.globalAlpha = 1;
    }
  }

  /**
   * A seta grande de curva, no meio de cima da tela, com a distância embaixo.
   *
   * Nos jogos de dirigir de celular ela é a peça mais importante do painel: é
   * ela que diz para onde ir. Por isso é GRANDE, laranja e fica no meio — não
   * é uma bussolinha discreta de canto. Quando o alvo está mais para o lado do
   * que para a frente, ela vira uma seta CURVA, que é o que se lê como
   * "converta aqui" sem precisar pensar.
   */
  bussola(ctx, partida, escala, L, A, margem) {
    const alvo = alvoAtual(partida.missao);
    if (!alvo) return;
    if (partida.missao.pontuacao !== undefined) margem += 56 * escala;
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
    const cy = margem + Math.min(96 * escala, A * 0.20);
    const tamanho = Math.min(58 * escala, A * 0.14);
    const perto = distancia < 14;
    const cor = perto ? '#4ee07a' : '#f26a1b';

    ctx.save();
    ctx.translate(cx, cy);
    ctx.shadowColor = 'rgba(0,0,0,0.55)';
    ctx.shadowBlur = 10 * escala;
    ctx.shadowOffsetY = 3 * escala;
    ctx.fillStyle = cor;
    ctx.strokeStyle = 'rgba(255,255,255,0.85)';
    ctx.lineWidth = 2.2 * escala;
    ctx.lineJoin = 'round';

    if (Math.abs(angulo) > 0.55) {
      // Curva: cotovelo subindo e virando para o lado do alvo.
      const s = Math.sign(angulo);
      const t = tamanho;
      ctx.scale(s, 1);
      ctx.beginPath();
      ctx.moveTo(-t * 0.30, t * 0.95);
      ctx.lineTo(-t * 0.30, t * 0.10);
      ctx.quadraticCurveTo(-t * 0.30, -t * 0.52, t * 0.32, -t * 0.52);
      ctx.lineTo(t * 0.32, -t * 0.92);
      ctx.lineTo(t * 1.05, -t * 0.18);
      ctx.lineTo(t * 0.32, t * 0.56);
      ctx.lineTo(t * 0.32, t * 0.16);
      ctx.quadraticCurveTo(t * 0.20, t * 0.16, t * 0.20, t * 0.42);
      ctx.lineTo(t * 0.20, t * 0.95);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
    } else {
      // Reto: segue em frente. Inclina de leve para o lado do alvo.
      ctx.rotate(angulo * 0.7);
      const t = tamanho;
      ctx.beginPath();
      ctx.moveTo(0, -t);
      ctx.lineTo(t * 0.72, -t * 0.02);
      ctx.lineTo(t * 0.30, -t * 0.02);
      ctx.lineTo(t * 0.30, t * 0.92);
      ctx.lineTo(-t * 0.30, t * 0.92);
      ctx.lineTo(-t * 0.30, -t * 0.02);
      ctx.lineTo(-t * 0.72, -t * 0.02);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
    }
    ctx.restore();

    // A distância, grande e com contorno preto — como no jogo de referência,
    // ela fica sobre a pista e precisa ser legível contra qualquer asfalto.
    ctx.save();
    ctx.textAlign = 'center';
    ctx.font = `800 ${Math.round(30 * escala)}px ${FONTE}`;
    ctx.lineWidth = 5 * escala;
    ctx.lineJoin = 'round';
    ctx.strokeStyle = 'rgba(0,0,0,0.75)';
    const texto = `${Math.round(distancia)}m`;
    const y = cy + tamanho + 26 * escala;
    ctx.strokeText(texto, cx, y);
    ctx.fillStyle = '#ffffff';
    ctx.fillText(texto, cx, y);
    ctx.restore();
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
    const fundo = ctx.createLinearGradient(0, cy - raio, 0, cy + raio);
    fundo.addColorStop(0, 'rgba(46,56,72,0.92)');
    fundo.addColorStop(1, 'rgba(18,24,34,0.92)');
    ctx.fillStyle = fundo;
    ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.55)';
    ctx.lineWidth = 3 * escala;
    ctx.stroke();

    const inicio = Math.PI * 0.78;
    const fim = Math.PI * 2.22;
    const maxima = 180;
    const kmh = Math.abs(paraKmh(carro.vx));

    // Arco de rotação, por fora do mostrador, com a faixa vermelha marcada.
    const f = carro.ficha;
    const rotacao = limitar(carro.rotacao / f.rotacaoMaxima, 0, 1);
    const ondeTroca = limitar(f.rotacaoTroca / f.rotacaoMaxima, 0, 1);
    ctx.lineWidth = 5 * escala;
    ctx.strokeStyle = 'rgba(255,255,255,0.12)';
    ctx.beginPath();
    ctx.arc(cx, cy, raio - 3 * escala, inicio, fim);
    ctx.stroke();
    // a faixa onde se deve trocar fica desenhada mesmo com o motor parado
    ctx.strokeStyle = 'rgba(224,74,60,0.42)';
    ctx.beginPath();
    ctx.arc(cx, cy, raio - 3 * escala, inicio + (fim - inicio) * ondeTroca, fim);
    ctx.stroke();
    ctx.strokeStyle = rotacao > ondeTroca ? '#ff5b4a' : '#7fd1ff';
    ctx.beginPath();
    ctx.arc(cx, cy, raio - 3 * escala, inicio, inicio + (fim - inicio) * rotacao);
    ctx.stroke();

    // Riscos de velocidade.
    ctx.strokeStyle = 'rgba(255,255,255,0.5)';
    ctx.lineWidth = 1.8 * escala;
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
    ctx.fillText(String(Math.round(kmh)), cx, cy - 6 * escala);
    ctx.fillStyle = 'rgba(255,255,255,0.45)';
    ctx.font = `600 ${Math.round(9 * escala)}px ${FONTE}`;
    ctx.fillText('km/h', cx, cy + 12 * escala);

    // Combustível e lataria: duas tirinhas DENTRO do mostrador, para não
    // brigarem com o carro no meio da tela.
    const missao = partida.missao;
    const tanque = missao.combustivel !== undefined
      ? carro.combustivel / missao.combustivel
      : carro.combustivel / carro.ficha.tanque;
    // As duas tirinhas ficam rente à borda de baixo do mostrador, SEM rótulo:
    // escrito, "COMB" e "LATA" caíam em cima do "km/h" e o miolo do relógio
    // virava uma sopa de letra. A cor já diz qual é qual — verde é tanque,
    // cinza é lataria — e as duas ficam vermelhas quando é hora de olhar.
    const largura = raio * 0.54;
    const altura = 5 * escala;
    const y = cy + raio * 0.60;
    this.tirinha(ctx, cx - raio * 0.60, y, largura, altura,
      limitar(tanque, 0, 1), tanque < 0.2 ? '#e8563a' : '#5ad07a');
    this.tirinha(ctx, cx + raio * 0.06, y, largura, altura,
      1 - carro.dano, carro.dano > 0.66 ? '#e8563a' : '#c9ced6');
  }

  tirinha(ctx, x, y, largura, altura, valor, cor) {
    ctx.fillStyle = 'rgba(255,255,255,0.14)';
    ctx.fillRect(x, y, largura, altura);
    ctx.fillStyle = cor;
    ctx.fillRect(x, y, largura * limitar(valor, 0, 1), altura);
  }

  /** Volante, pedais, alavanca e os botõezinhos. Tudo registrado na entrada. */
  controles(ctx, partida, entrada, escala, L, A, margem, arranjo) {
    const carro = partida.carro;
    const { volante: v, pedais, cambio } = arranjo;

    // --- pedais, à esquerda -------------------------------------------------
    entrada.areaFreio = {
      x: pedais.xFreio, y: pedais.y, largura: pedais.largura, altura: pedais.altura,
    };
    entrada.areaAcelerador = {
      x: pedais.xAcelerador, y: pedais.y, largura: pedais.largura, altura: pedais.altura,
    };
    pedal(ctx, entrada.areaFreio, 'freio', entrada.tocando('freio'), escala);
    pedal(ctx, entrada.areaAcelerador, 'acelerador', entrada.tocando('acelerador'), escala);

    // Freio de mão: um puxador curto acima do pedal do freio.
    const alturaMao = Math.min(42 * escala, A * 0.11);
    entrada.areaMao = {
      x: pedais.xFreio, y: pedais.y - alturaMao - 10 * escala,
      largura: pedais.largura * 2 + 10 * escala, altura: alturaMao,
    };
    botao(ctx, entrada.areaMao, 'FREIO DE MÃO',
      entrada.tocando('mao') || entrada.cambioPosicao === 'P', escala,
      entrada.tocando('mao') ? 0xd0a32e : 0x55606e, 10);

    // --- alavanca P R N D, à direita ---------------------------------------
    this.alavanca(ctx, entrada, carro, escala, cambio);

    // --- volante, à direita -------------------------------------------------
    entrada.areaVolante = { x: v.x, y: v.y, raio: v.raio };
    ctx.save();
    ctx.globalAlpha = 0.25;
    ctx.fillStyle = '#000';
    ctx.beginPath();
    ctx.ellipse(v.x, v.y + v.raio * 0.92, v.raio * 0.85, v.raio * 0.18, 0, 0, TAU);
    ctx.fill();
    ctx.restore();
    desenharVolante(ctx, partida.modelo.volante, {
      x: v.x, y: v.y, raio: v.raio,
      // O ARO gira mais do que o dedo. O curso do controle é curto de
      // propósito (54°), mas um aro que anda 54° no batente parece parado —
      // e volante que parece parado é volante que a pessoa acha quebrado.
      // Multiplicar só o desenho dá o giro que o olho espera sem mexer no
      // controle: 2,2 põe o batente em 120°, quase meia volta.
      angulo: entrada.volanteVisual * 2.2,
      alfa: 0.96,
      destaque: entrada.arrasto ? 1 : 0,
    });

    // --- câmbio manual: duas abas encostadas no volante ---------------------
    const manual = entrada.comandos.cambio === 'manual' && carro.ficha.relacoes.length > 1;
    if (manual) {
      const l = Math.min(84 * escala, L * 0.09);
      const h = Math.min(40 * escala, A * 0.105);
      const x = v.x - v.raio - l - 10 * escala;
      entrada.areaSobeMarcha = { x, y: A - margem * 0.5 - h * 2 - 8 * escala, largura: l, altura: h };
      entrada.areaDesceMarcha = { x, y: A - margem * 0.5 - h, largura: l, altura: h };
      const naHora = carro.rotacao > carro.ficha.rotacaoTroca && carro.sentido > 0;
      const naUltima = carro.marcha >= carro.ficha.relacoes.length;
      botao(ctx, entrada.areaSobeMarcha, '▲',
        naHora && !naUltima, escala, naHora && !naUltima ? 0x35c46a : 0x4a5a70, 17);
      botao(ctx, entrada.areaDesceMarcha, '▼',
        carro.afogando > 0.35, escala, carro.afogando > 0.35 ? 0xe0a02a : 0x4a5a70, 17);
    } else {
      entrada.areaSobeMarcha = null;
      entrada.areaDesceMarcha = null;
    }

    // --- cantinho de cima: câmera e pausa ----------------------------------
    const pequeno = 44 * escala;
    const topo = margem + 150 * escala;
    entrada.areaCamera = { x: L - margem - pequeno, y: topo, largura: pequeno, altura: pequeno };
    entrada.areaPausa = { x: L - margem - pequeno * 2 - 8 * escala, y: topo, largura: pequeno, altura: pequeno };
    botao(ctx, entrada.areaCamera, '◉', false, escala, 0x4a5a70, 16);
    botao(ctx, entrada.areaPausa, '❚❚', false, escala, 0x4a5a70, 13);

    ctx.textAlign = 'center';
    ctx.fillStyle = 'rgba(255,255,255,0.4)';
    ctx.font = `600 ${Math.round(9 * escala)}px ${FONTE}`;
    ctx.fillText(NOMES_DE_MODO[partida.camera.modo] || '',
      L - margem - pequeno / 2, topo + pequeno + 10 * escala);
  }

  /**
   * A coluna P R N D.
   *
   * Uma letra acesa e três apagadas, com o puxador ao lado marcando em qual
   * delas a alavanca está. É informação que se lê de canto de olho, sem tirar
   * o polegar do volante — que é o ponto inteiro de ser uma coluna e não um
   * botão que alterna.
   */
  alavanca(ctx, entrada, carro, escala, cambio) {
    const letras = ['P', 'R', 'N', 'D'];
    const { x, largura, alturaLetra: h } = cambio;
    const alturaTotal = h * letras.length;

    caixa(ctx, x - 4 * escala, cambio.y - 6 * escala,
      largura + 8 * escala, alturaTotal + 12 * escala, 12 * escala, 'rgba(10,14,20,0.72)');

    letras.forEach((letra, i) => {
      const y = cambio.y + i * h;
      entrada.areasCambio[letra] = { x, y, largura, altura: h };
      const ativa = entrada.cambioPosicao === letra;
      if (ativa) {
        ctx.fillStyle = letra === 'R' ? 'rgba(232,90,58,0.30)' : 'rgba(64,208,122,0.26)';
        ctx.beginPath();
        arredondado(ctx, x + 2 * escala, y + 2 * escala, largura - 4 * escala, h - 4 * escala, 8 * escala);
        ctx.fill();
      }
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = `800 ${Math.round(20 * escala)}px ${FONTE}`;
      ctx.fillStyle = ativa
        ? (letra === 'R' ? '#ff8b6b' : '#6ef0a0')
        : 'rgba(255,255,255,0.32)';
      ctx.fillText(letra, x + largura / 2, y + h / 2);
    });

    // O puxador, na altura da letra em que a alavanca está.
    const indice = Math.max(0, letras.indexOf(entrada.cambioPosicao));
    const yPuxador = cambio.y + indice * h + h / 2;
    ctx.fillStyle = 'rgba(255,255,255,0.80)';
    ctx.beginPath();
    ctx.moveTo(x - 5 * escala, yPuxador);
    ctx.lineTo(x - 13 * escala, yPuxador - 7 * escala);
    ctx.lineTo(x - 13 * escala, yPuxador + 7 * escala);
    ctx.closePath();
    ctx.fill();

    // Com câmbio manual a marcha engatada aparece embaixo da coluna.
    if (entrada.comandos.cambio === 'manual' && carro.ficha.relacoes.length > 1
      && entrada.cambioPosicao === 'D') {
      ctx.fillStyle = carro.cortando ? '#ff5b4a' : carro.afogando > 0.35 ? '#e0a02a' : '#7fd1ff';
      ctx.font = `800 ${Math.round(17 * escala)}px ${FONTE}`;
      ctx.fillText(String(carro.marcha), x + largura / 2, cambio.y + alturaTotal + 14 * escala);
    }
  }

  avisos(ctx, partida, escala, L, A) {
    const carro = partida.carro;
    const alertas = [];
    if (carro.dano > 0.7) alertas.push(['LATARIA NO LIMITE', '#e8563a']);
    if (partida.missao.combustivel !== undefined
      && carro.combustivel / partida.missao.combustivel < 0.15) alertas.push(['COMBUSTÍVEL', '#e8b93a']);
    if (partida.foraDaPista > 1.2) alertas.push(['VOLTE PARA A PISTA', '#7fd1ff']);
    if (carro.cortando) alertas.push(['SUBA A MARCHA', '#ff8b5b']);
    else if (carro.afogando > 0.5) alertas.push(['MOTOR AFOGANDO — DESÇA A MARCHA', '#e0a02a']);
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
      ctx.fillText(r.texto, L / 2, A * 0.50 - i * 30 * escala + (1 - t) * 14 * escala);
    });
    ctx.globalAlpha = 1;
  }
}

// ---------------------------------------------------------------------------

export function caixa(ctx, x, y, largura, altura, raio, cor, contorno = 'rgba(255,255,255,0.16)') {
  ctx.fillStyle = cor;
  ctx.beginPath();
  arredondado(ctx, x, y, largura, altura, raio);
  ctx.fill();
  if (contorno) {
    ctx.strokeStyle = contorno;
    ctx.lineWidth = 2;
    ctx.stroke();
  }
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

/**
 * Botão gordo de jogo de celular: cor cheia o tempo todo, brilho na metade de
 * cima, borda branca grossa e texto em caixa alta.
 *
 * Botão que só ganha cor quando é apertado obriga a pessoa a procurar onde
 * ficam os pedais no meio da corrida. Cor sempre visível resolve isso — o
 * toque muda o brilho, não a identidade do botão.
 */
/**
 * Pedal de carro: a placa de borracha preta, comprida, com relevo.
 *
 * Não é um botão colorido de propósito. O acelerador e o freio de um jogo de
 * dirigir têm que parecer o que são — a pessoa acha eles com o dedão sem
 * olhar, porque a forma já diz. O freio é largo e tem furos; o acelerador é
 * estreito e tem estrias. Quando afundado, o pedal desce e escurece.
 */
export function pedal(ctx, area, tipo, ativo, escala) {
  const freio = tipo === 'freio';
  const recuo = ativo ? 4 * escala : 0;
  const x = area.x;
  const y = area.y + recuo;
  const l = area.largura;
  const a = area.altura - recuo;
  const r = Math.min(l, a) * 0.22;

  ctx.save();
  // Sombra: é ela que dá a impressão de que o pedal afunda.
  if (!ativo) {
    ctx.fillStyle = 'rgba(0,0,0,0.45)';
    ctx.beginPath();
    arredondado(ctx, x, y + 5 * escala, l, a, r);
    ctx.fill();
  }

  const g = ctx.createLinearGradient(x, y, x + l, y + a);
  g.addColorStop(0, ativo ? '#2c3037' : '#3d434c');
  g.addColorStop(0.5, ativo ? '#1d2126' : '#2a2e35');
  g.addColorStop(1, ativo ? '#14171b' : '#1b1f24');
  ctx.fillStyle = g;
  ctx.beginPath();
  arredondado(ctx, x, y, l, a, r);
  ctx.fill();
  ctx.strokeStyle = ativo ? 'rgba(255,255,255,0.42)' : 'rgba(255,255,255,0.20)';
  ctx.lineWidth = 2.4 * escala;
  ctx.stroke();

  ctx.save();
  ctx.beginPath();
  arredondado(ctx, x, y, l, a, r);
  ctx.clip();
  if (freio) {
    // Furos em duas colunas, como a borracha de pedal de freio.
    ctx.fillStyle = 'rgba(0,0,0,0.55)';
    const raio = l * 0.11;
    for (let i = 0; i < 4; i++) {
      for (const lado of [-1, 1]) {
        ctx.beginPath();
        ctx.arc(x + l / 2 + lado * l * 0.22, y + a * (0.22 + i * 0.19), raio, 0, TAU);
        ctx.fill();
      }
    }
  } else {
    // Estrias atravessadas, como a borracha do acelerador.
    ctx.fillStyle = 'rgba(0,0,0,0.42)';
    for (let i = 0; i < 7; i++) {
      ctx.fillRect(x + l * 0.16, y + a * (0.14 + i * 0.11), l * 0.68, a * 0.035);
    }
  }
  // Brilho por cima, para a borracha não ficar chapada.
  const brilho = ctx.createLinearGradient(0, y, 0, y + a * 0.45);
  brilho.addColorStop(0, `rgba(255,255,255,${ativo ? 0.06 : 0.14})`);
  brilho.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = brilho;
  ctx.fillRect(x, y, l, a * 0.45);
  ctx.restore();

  // Uma tarja de cor só na beirada de baixo: diz qual é qual sem virar botão.
  ctx.fillStyle = freio
    ? (ativo ? '#ff6a58' : '#b8402f')
    : (ativo ? '#5fe08a' : '#2f8f52');
  ctx.beginPath();
  arredondado(ctx, x + l * 0.18, y + a - 7 * escala, l * 0.64, 4.5 * escala, 2 * escala);
  ctx.fill();
  ctx.restore();
}

export function botao(ctx, area, texto, ativo, escala, cor, tamanhoFonte = 13) {
  ctx.save();
  const r = Math.min(area.largura, area.altura) * 0.32;
  const claro = tonalizar(cor, ativo ? 1.35 : 1.12);
  const escuro = tonalizar(cor, ativo ? 0.95 : 0.72);

  ctx.beginPath();
  arredondado(ctx, area.x, area.y, area.largura, area.altura, r);
  const g = ctx.createLinearGradient(0, area.y, 0, area.y + area.altura);
  g.addColorStop(0, corTexto(claro));
  g.addColorStop(1, corTexto(escuro));
  ctx.fillStyle = g;
  ctx.globalAlpha = ativo ? 1 : 0.9;
  ctx.fill();
  ctx.globalAlpha = 1;

  // Verniz: uma lasca clara na metade de cima, cortada pela própria forma.
  ctx.save();
  ctx.clip();
  const v = ctx.createLinearGradient(0, area.y, 0, area.y + area.altura * 0.55);
  v.addColorStop(0, 'rgba(255,255,255,0.32)');
  v.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = v;
  ctx.fillRect(area.x, area.y, area.largura, area.altura * 0.55);
  ctx.restore();

  ctx.strokeStyle = ativo ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.55)';
  ctx.lineWidth = 3 * escala;
  ctx.beginPath();
  arredondado(ctx, area.x, area.y, area.largura, area.altura, r);
  ctx.stroke();

  ctx.font = `800 ${Math.round(tamanhoFonte * escala)}px ${FONTE}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const cx = area.x + area.largura / 2;
  const cy = area.y + area.altura / 2;
  ctx.fillStyle = 'rgba(0,0,0,0.35)';
  ctx.fillText(texto, cx, cy + 1.6 * escala);
  ctx.fillStyle = '#ffffff';
  ctx.fillText(texto, cx, cy);
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
