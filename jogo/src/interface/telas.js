// As telas de fora do jogo: menu, garagem, briefing, resultado, pausa.
//
// Estas são HTML de verdade, não desenho no canvas. Botão de menu quer ser
// botão: dá foco, responde ao teclado, o leitor de tela lê. O canvas fica só
// com o que é jogo.

import { CARROS, carroPorId } from '../jogo/carros.js';
import { miniaturaVolante } from '../jogo/volante.js';
import { construirCarro } from '../motor/modelos.js';
import { Cena } from '../motor/cena.js';
import { criarCamera, atualizarBase } from '../motor/camera.js';
import { CLIMAS } from '../jogo/clima.js';
import { CENARIOS } from '../jogo/mundo.js';
import { FICHA_DOS_TIPOS } from '../jogo/missoes.js';
import { formatarDinheiro, formatarTempo, limitar } from '../nucleo/matematica.js';
import { temCarro } from '../jogo/progresso.js';

export class Telas {
  constructor(raiz) {
    this.raiz = raiz;
    this.previas = [];
    this.aoFechar = null;
  }

  limpar() {
    for (const p of this.previas) p.parar();
    this.previas = [];
    this.raiz.innerHTML = '';
    this.raiz.classList.remove('aberto');
  }

  esconder() {
    this.limpar();
  }

  abrir(no) {
    this.limpar();
    this.raiz.appendChild(no);
    this.raiz.classList.add('aberto');
    const primeiro = no.querySelector('button');
    if (primeiro) primeiro.focus();
  }

  // -------------------------------------------------------------------------

  menu(progresso, acoes) {
    const no = elemento('div', 'tela tela--menu');
    no.innerHTML = `
      <div class="marca">
        <span class="marca__nome">VOLANTE</span>
        <span class="marca__linha">cada carro, um volante</span>
      </div>
      <p class="menu__saldo">${formatarDinheiro(progresso.dinheiro)} ·
        ${progresso.numeros.estrelas} ⭐ · ${progresso.numeros.missoes} serviços</p>
    `;
    const lista = elemento('div', 'menu__botoes');
    const proxima = progresso.carreira.indice + 1;
    lista.append(
      botao(`Continuar carreira · serviço ${proxima}`, 'principal', acoes.carreira),
      botao('Serviço avulso', '', acoes.avulso),
      botao('Rua livre', '', acoes.livre),
      botao('Garagem', '', acoes.garagem),
      botao('Ajustes', '', acoes.ajustes),
    );
    no.appendChild(lista);
    no.appendChild(rodape());
    this.abrir(no);
  }

  garagem(progresso, acoes) {
    const no = elemento('div', 'tela tela--larga');
    no.appendChild(cabecalho('Garagem', formatarDinheiro(progresso.dinheiro), acoes.voltar));

    const grade = elemento('div', 'grade');
    for (const modelo of CARROS) {
      const meu = temCarro(progresso, modelo.id);
      const atual = progresso.carroAtual === modelo.id;
      const cartao = elemento('article', `cartao${atual ? ' cartao--atual' : ''}${meu ? '' : ' cartao--bloqueado'}`);

      const palco = elemento('div', 'cartao__palco');
      const previa = new PreviaDeCarro(modelo, 300, 150);
      palco.appendChild(previa.tela);
      this.previas.push(previa);
      previa.comecar();

      const volanteTela = miniaturaVolante(modelo.volante, 78);
      volanteTela.className = 'cartao__volante';
      volanteTela.title = modelo.volante.apelido;
      palco.appendChild(volanteTela);
      cartao.appendChild(palco);

      cartao.insertAdjacentHTML('beforeend', `
        <h3>${modelo.nome}</h3>
        <p class="cartao__classe">${modelo.classe}</p>
        <p class="cartao__texto">${modelo.descricao}</p>
        <p class="cartao__volante-nome">Volante: <strong>${modelo.volante.apelido}</strong></p>
        ${barrasDeFicha(modelo)}
      `);

      const rodapeCartao = elemento('div', 'cartao__acoes');
      if (atual) {
        rodapeCartao.appendChild(etiqueta('em uso'));
      } else if (meu) {
        rodapeCartao.appendChild(botao('Usar este', 'principal', () => acoes.escolher(modelo)));
      } else {
        const podeComprar = progresso.dinheiro >= modelo.preco;
        const b = botao(`Comprar · ${formatarDinheiro(modelo.preco)}`,
          podeComprar ? 'principal' : 'desabilitado',
          podeComprar ? () => acoes.comprar(modelo) : null);
        if (!podeComprar) b.disabled = true;
        rodapeCartao.appendChild(b);
      }
      cartao.appendChild(rodapeCartao);
      grade.appendChild(cartao);
    }
    no.appendChild(grade);
    this.abrir(no);
  }

  briefing(descritor, modelo, acoes, extra = {}) {
    const no = elemento('div', 'tela');
    const ficha = FICHA_DOS_TIPOS[descritor.tipo];
    const clima = CLIMAS[descritor.clima];
    const cenario = CENARIOS[descritor.cenario];

    no.innerHTML = `
      <p class="olho">${extra.rotulo || `Serviço ${descritor.indice !== undefined ? descritor.indice + 1 : ''}`}</p>
      <h2>${ficha.nome}</h2>
      <p class="briefing__resumo">${ficha.resumo}</p>
      <ul class="briefing__itens">
        <li><span>Onde</span><strong>${cenario.nome}</strong></li>
        <li><span>Quando</span><strong>${clima.nome}</strong></li>
        <li><span>Carro</span><strong>${modelo.nome}</strong></li>
        <li><span>Volante</span><strong>${modelo.volante.apelido}</strong></li>
        <li><span>Pagamento</span><strong>${formatarDinheiro(descritor.premio)}</strong></li>
      </ul>
      <p class="briefing__dica">💡 ${ficha.dica}</p>
    `;
    const acoesNo = elemento('div', 'acoes');
    acoesNo.append(
      botao('Pegar o serviço', 'principal', acoes.comecar),
      botao('Outro serviço', '', acoes.trocar),
      botao('Voltar', '', acoes.voltar),
    );
    no.appendChild(acoesNo);
    this.abrir(no);
  }

  resultado(resultado, partida, acoes) {
    const no = elemento('div', 'tela');
    const estrelas = [0, 1, 2].map((i) => `<span class="${i < resultado.estrelas ? 'cheia' : ''}">★</span>`).join('');

    no.innerHTML = `
      <p class="olho">${resultado.sucesso ? 'serviço entregue' : 'não deu'}</p>
      <h2>${resultado.motivo}</h2>
      <div class="estrelas">${estrelas}</div>
      <ul class="placar">
        ${resultado.linhas.map((l) => `
          <li class="${l.bom ? 'bom' : ''}"><span>${l.rotulo}</span><strong>${l.valor}</strong></li>
        `).join('')}
        <li class="destaque"><span>pagamento</span><strong>${formatarDinheiro(resultado.premio)}</strong></li>
      </ul>
    `;
    const acoesNo = elemento('div', 'acoes');
    if (resultado.sucesso) acoesNo.appendChild(botao('Próximo serviço', 'principal', acoes.proxima));
    acoesNo.appendChild(botao(resultado.sucesso ? 'Repetir' : 'Tentar de novo',
      resultado.sucesso ? '' : 'principal', acoes.repetir));
    acoesNo.appendChild(botao('Menu', '', acoes.menu));
    no.appendChild(acoesNo);
    this.abrir(no);
  }

  pausa(partida, acoes) {
    const no = elemento('div', 'tela tela--estreita');
    no.innerHTML = `
      <p class="olho">pausado</p>
      <h2>${partida.missao.descritor.titulo}</h2>
      <p class="briefing__resumo">${formatarTempo(Math.max(0, partida.missao.tempoLimite - partida.tempo))} restantes</p>
    `;
    const acoesNo = elemento('div', 'acoes');
    acoesNo.append(
      botao('Continuar', 'principal', acoes.continuar),
      botao('Recomeçar', '', acoes.recomecar),
      botao('Sair para o menu', '', acoes.menu),
    );
    no.appendChild(acoesNo);
    this.abrir(no);
  }

  ajustes(progresso, acoes) {
    const no = elemento('div', 'tela tela--estreita');
    no.appendChild(cabecalho('Ajustes', '', acoes.voltar));
    const lista = elemento('div', 'ajustes');

    lista.appendChild(escolha('Qualidade da imagem',
      [['alta', 'alta'], ['media', 'média'], ['baixa', 'baixa']],
      progresso.ajustes.qualidade, acoes.qualidade));

    lista.appendChild(escolha('Som',
      [[true, 'ligado'], [false, 'desligado']],
      progresso.ajustes.som, acoes.som));

    lista.appendChild(escolha('Câmera',
      [['perseguicao', 'atrás'], ['capo', 'capô'], ['cabine', 'cabine'], ['alto', 'de cima']],
      progresso.ajustes.camera, acoes.camera));

    no.appendChild(lista);

    const perigo = elemento('div', 'acoes');
    perigo.appendChild(botao('Apagar progresso', 'perigo', acoes.apagar));
    no.appendChild(perigo);

    no.insertAdjacentHTML('beforeend', `
      <div class="ajuda">
        <h4>Teclado</h4>
        <p><kbd>W</kbd><kbd>S</kbd> acelera e freia · <kbd>A</kbd><kbd>D</kbd> volante ·
        <kbd>espaço</kbd> freio de mão · <kbd>C</kbd> câmera · <kbd>shift</kbd> olhar para trás ·
        <kbd>Esc</kbd> pausa</p>
        <h4>No dedo</h4>
        <p>Gire o volante no canto de baixo. Ele é o controle — e é diferente em cada carro.
        Parado com o freio afundado, engata a ré sozinho.</p>
      </div>
    `);
    this.abrir(no);
  }

  carregando(texto = 'montando o bairro…') {
    const no = elemento('div', 'tela tela--carregando');
    no.innerHTML = `<div class="girando"></div><p>${texto}</p>`;
    this.abrir(no);
  }
}

// ---------------------------------------------------------------------------
// PRÉVIA 3D DO CARRO
// ---------------------------------------------------------------------------

/** O carro girando devagar na garagem, com o mesmo motor do jogo. */
class PreviaDeCarro {
  constructor(modelo, largura, altura) {
    this.tela = document.createElement('canvas');
    this.tela.width = largura * 2;
    this.tela.height = altura * 2;
    this.tela.style.width = '100%';
    this.tela.style.height = 'auto';
    this.ctx = this.tela.getContext('2d');
    this.corpo = construirCarro(modelo);
    this.cena = new Cena();
    this.camera = criarCamera();
    this.camera.aspecto = largura / altura;
    this.camera.fov = 0.5;
    this.angulo = 0.6;
    this.rodando = false;
    this.ambiente = {
      direcaoSol: { x: -0.5, y: 0.75, z: -0.42 },
      ambienteLuz: 0.5, luz: 1.05,
    };
  }

  comecar() {
    if (this.rodando) return;
    this.rodando = true;
    const quadro = () => {
      if (!this.rodando) return;
      this.desenhar();
      this.pedido = requestAnimationFrame(quadro);
    };
    quadro();
  }

  parar() {
    this.rodando = false;
    if (this.pedido) cancelAnimationFrame(this.pedido);
  }

  desenhar() {
    this.angulo += 0.006;
    const L = this.tela.width, A = this.tela.height;
    const ctx = this.ctx;

    const g = ctx.createLinearGradient(0, 0, 0, A);
    g.addColorStop(0, '#20262f');
    g.addColorStop(1, '#0e1116');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, L, A);

    const distancia = this.corpo.dimensoes.comprimento * 2.05;
    this.camera.x = Math.sin(this.angulo) * distancia;
    this.camera.z = Math.cos(this.angulo) * distancia;
    this.camera.y = 2.1;
    // A guinada é a MESMA do ângulo da órbita: a posição já está "atrás" nesse
    // ângulo, e frente(guinada) aponta de volta para a origem. Somar PI aqui
    // vira a câmera de costas para o carro.
    this.camera.guinada = this.angulo;
    this.camera.inclinacao = -0.20;
    atualizarBase(this.camera);

    const instancias = [{
      malha: this.corpo.carroceria, x: 0, y: 0, z: 0,
      guinada: 0, raio: this.corpo.raio,
    }];
    for (const roda of this.corpo.rodas) {
      instancias.push({
        malha: this.corpo.roda, x: roda.x, y: this.corpo.raioRoda, z: roda.z,
        guinada: 0, raio: this.corpo.raioRoda * 1.6,
      });
    }

    // Um chão redondo de cortesia, só para o carro ter onde pousar.
    ctx.save();
    ctx.globalAlpha = 0.3;
    ctx.fillStyle = '#000';
    ctx.beginPath();
    ctx.ellipse(L / 2, A * 0.68, L * 0.24, A * 0.045, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    this.cena.montar(this.camera, instancias, this.ambiente, L, A);
    this.cena.pintar(ctx);
  }
}

// ---------------------------------------------------------------------------

function elemento(tag, classe) {
  const e = document.createElement(tag);
  if (classe) e.className = classe;
  return e;
}

function botao(texto, classe, aoClicar) {
  const b = elemento('button', `botao${classe ? ' botao--' + classe : ''}`);
  b.type = 'button';
  b.textContent = texto;
  if (aoClicar) b.addEventListener('click', aoClicar);
  return b;
}

function etiqueta(texto) {
  const e = elemento('span', 'etiqueta');
  e.textContent = texto;
  return e;
}

function cabecalho(titulo, direita, aoVoltar) {
  const c = elemento('header', 'cabecalho');
  const b = botao('‹ voltar', 'discreto', aoVoltar);
  const h = elemento('h2');
  h.textContent = titulo;
  c.append(b, h);
  if (direita) {
    const d = elemento('span', 'cabecalho__saldo');
    d.textContent = direita;
    c.appendChild(d);
  }
  return c;
}

function escolha(rotulo, opcoes, atual, aoEscolher) {
  const linha = elemento('div', 'ajuste');
  const t = elemento('span', 'ajuste__rotulo');
  t.textContent = rotulo;
  linha.appendChild(t);
  const grupo = elemento('div', 'ajuste__opcoes');
  for (const [valor, texto] of opcoes) {
    const b = botao(texto, valor === atual ? 'ativo' : '', () => aoEscolher(valor));
    grupo.appendChild(b);
  }
  linha.appendChild(grupo);
  return linha;
}

/** Quatro barrinhas comparando os carros. Números crus não dizem nada. */
function barrasDeFicha(modelo) {
  const f = modelo.ficha;
  const potencia = limitar((f.torqueMaximo / f.massa) * 4.2, 0, 1);
  const agilidade = limitar((f.estercoMaximo * 1.5) / (f.entreEixos / 2.6), 0, 1);
  const aderencia = limitar((f.aderencia - 0.85) / 0.45, 0, 1);
  const estabilidade = limitar(1 - (f.alturaCentroMassa - 0.4) / 0.6, 0, 1);
  const linhas = [
    ['força', potencia], ['manobra', agilidade],
    ['aderência', aderencia], ['estabilidade', estabilidade],
  ];
  return `<div class="ficha">${linhas.map(([nome, v]) => `
    <div class="ficha__linha"><span>${nome}</span>
      <div class="ficha__barra"><i style="width:${Math.round(v * 100)}%"></i></div>
    </div>`).join('')}</div>`;
}

function rodape() {
  const r = elemento('p', 'rodape');
  r.innerHTML = 'Inspirado nos jogos de dirigir de celular — chão pintado, carro de bloco, '
    + 'baliza que não perdoa. Só que o volante é seu.';
  return r;
}
