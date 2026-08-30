/**
 * Servidor falso do modo demonstração.
 *
 * Responde às mesmas rotas da API de verdade, guardando tudo em memória.
 * Serve para abrir o app e clicar nas telas sem banco, sem migration e sem
 * pagar.me — o estado muda de verdade enquanto você navega, e some quando a
 * página recarrega.
 *
 * ISOLAMENTO
 * ----------
 * - Nenhuma tela importa este arquivo. Quem chama é `src/api/cliente.ts`, e
 *   só dentro do `if (MODO_DEMONSTRACAO)`, por `import()` preguiçoso.
 * - Nada aqui decide regra de negócio: comissão, tarifa e limites saem de
 *   `src/regras/limites.ts`, o mesmo módulo das telas.
 * - Não existe checkout, split, repasse, estorno nem webhook aqui dentro.
 *   Onde a API de verdade cobraria, a demonstração só muda o estado local.
 */

import type {
  Anuncio,
  AtorDaOferta,
  Avaliacao,
  Corrida,
  EstadoEntrega,
  EstadoOferta,
  Oferta,
  Pedido,
  SolicitacaoDeDevolucao,
  Usuario,
} from '../api/tipos';
import {
  calcularDescontos,
  COMISSAO,
  DIAS_PARA_CONFIRMACAO_AUTOMATICA,
  DIAS_PARA_RESPONDER_OFERTA,
  validarProposta,
  valorMinimoDaOferta,
} from '../regras/limites';
import {
  anunciosSemente,
  avaliacoesSemente,
  conversasSemente,
  corridasSemente,
  curtidasSemente,
  devolucoesSemente,
  enderecosSemente,
  entregadoresDemo,
  ofertasSemente,
  pedidosSemente,
  seguidoresSemente,
  usuarioDemo,
} from './dados';

type PedidoDemo = Pedido & { lado: 'compra' | 'venda'; repassadoEm?: string | null };

interface Estado {
  usuario: Usuario;
  anuncios: Anuncio[];
  pedidos: PedidoDemo[];
  corridas: Corrida[];
  devolucoes: SolicitacaoDeDevolucao[];
  enderecos: typeof enderecosSemente;
  conversas: typeof conversasSemente;
  entregadorDisponivel: boolean;
  /** ids de anúncio que eu curti. */
  curtidas: Set<string>;
  /** quantas curtidas cada anúncio tem, de todo mundo. */
  curtidasPorAnuncio: Record<string, number>;
  /** ids de vendedor que eu sigo. */
  seguindo: Set<string>;
  seguidoresPorVendedor: Record<string, number>;
  ofertas: Oferta[];
  avaliacoes: Avaliacao[];
}

function semear(): Estado {
  const anuncios = anunciosSemente.map((a) => ({ ...a }));
  return {
    usuario: { ...usuarioDemo },
    anuncios,
    pedidos: pedidosSemente(anuncios) as PedidoDemo[],
    corridas: corridasSemente(anuncios),
    devolucoes: devolucoesSemente(anuncios),
    enderecos: enderecosSemente.map((e) => ({ ...e })),
    conversas: conversasSemente.map((c) => ({ ...c, mensagens: [...c.mensagens] })),
    entregadorDisponivel: true,
    curtidas: new Set(curtidasSemente.minhas),
    curtidasPorAnuncio: { ...curtidasSemente.porAnuncio },
    seguindo: new Set(seguidoresSemente.sigo),
    seguidoresPorVendedor: { ...seguidoresSemente.porVendedor },
    ofertas: ofertasSemente(anuncios),
    avaliacoes: avaliacoesSemente(),
  };
}

let estado = semear();

/** Volta tudo ao começo. Botão "recomeçar" da faixa de demonstração. */
export function reiniciarDemonstracao() {
  estado = semear();
}

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------

class ErroDemo extends Error {
  constructor(
    readonly status: number,
    mensagem: string,
  ) {
    super(mensagem);
  }
}

const agora = () => new Date().toISOString();
const emDias = (n: number) => new Date(Date.now() + n * 86_400_000).toISOString();

function pedido(id: string): PedidoDemo {
  const p = estado.pedidos.find((x) => x.id === id);
  if (!p) throw new ErroDemo(404, 'Pedido não encontrado nesta demonstração.');
  return p;
}

function corrida(id: string): Corrida {
  const c = estado.corridas.find((x) => x.id === id);
  if (!c) throw new ErroDemo(404, 'Corrida não encontrada nesta demonstração.');
  return c;
}

function codigoNovo(): string {
  return String(Math.floor(1000 + Math.random() * 9000));
}

/** Reflexo da corrida no pedido, espelhando `dominio/logistica.ts`. */
const ESTADO_DO_PEDIDO: Partial<Record<EstadoEntrega, Pedido['estado']>> = {
  AGUARDANDO_ATRIBUICAO: 'AGUARDANDO_AGENDAMENTO_DE_COLETA',
  ATRIBUIDA: 'AGUARDANDO_AGENDAMENTO_DE_COLETA',
  ACEITA: 'AGUARDANDO_AGENDAMENTO_DE_COLETA',
  A_CAMINHO_DA_COLETA: 'A_CAMINHO_DA_COLETA',
  CHEGOU_NA_COLETA: 'A_CAMINHO_DA_COLETA',
  PRODUTO_COLETADO: 'PRODUTO_COLETADO',
  EM_ROTA_PARA_ENTREGA: 'EM_ROTA_PARA_ENTREGA',
  CHEGOU_NA_ENTREGA: 'EM_ROTA_PARA_ENTREGA',
  ENTREGUE: 'ENTREGUE',
};

function refletirNoPedido(c: Corrida, novo: EstadoEntrega) {
  const p = estado.pedidos.find((x) => x.codigo === c.pedido.codigo);
  if (!p) return;

  if (c.tipo === 'DEVOLUCAO') {
    if (novo === 'ENTREGUE') {
      p.estado = 'REEMBOLSADO';
      p.reembolso = { estado: 'CONCLUIDO', valorReembolsado: p.valorTotal };
    } else if (novo === 'PRODUTO_COLETADO') {
      p.estado = 'DEVOLUCAO_EM_TRANSITO';
    }
    return;
  }

  const destino = ESTADO_DO_PEDIDO[novo];
  if (destino) p.estado = destino;

  if (novo === 'ENTREGUE') {
    p.entregueEm = agora();
    p.prazoTesteAte = emDias(7);
    p.diasRestantesParaTestar = 7;
    p.confirmadaPor = 'codigo';
    p.podePedirReembolso = p.lado === 'compra';
  }
  if (p.entrega) p.entrega.estado = novo;
}

/** Marca a entrega do vendedor como feita. Única porta para ENTREGUE. */
function marcarEntregueDoVendedor(p: PedidoDemo, por: NonNullable<Pedido['confirmadaPor']>) {
  p.estado = 'ENTREGUE';
  p.entregueEm = agora();
  p.prazoTesteAte = emDias(7);
  p.diasRestantesParaTestar = 7;
  p.prazoConfirmacaoAte = null;
  p.confirmadaPor = por;
  p.podePedirReembolso = p.lado === 'compra';
}

function resumoDaCompra(
  a: Anuncio,
  modalidade: Anuncio['modalidadeEntrega'],
  valorCombinado?: number,
) {
  const preco = valorCombinado ?? a.preco;
  const d = calcularDescontos(preco, modalidade);
  return {
    valorProduto: preco,
    valorTotal: preco,
    taxaComissao: COMISSAO,
    valorComissao: d.comissao,
    valorTarifa: d.tarifa,
    valorVendedor: d.vendedor,
    modalidade,
  };
}

/**
 * Enriquece o anúncio com o que depende de quem está olhando: curtidas,
 * se eu curti, e se tenho negociação em aberto nele.
 */
function comContexto(a: Anuncio): Anuncio {
  const minha = estado.ofertas.find(
    (o) => o.anuncio.id === a.id && o.meuPapel === 'COMPRADOR' && emAberto(o.estado),
  );
  return {
    ...a,
    curtidas: estado.curtidasPorAnuncio[a.id] ?? 0,
    curtido: estado.curtidas.has(a.id),
    // quem vende não negocia consigo mesmo
    aceitaOferta: a.vendedor.id !== estado.usuario.id,
    minhaOferta: minha
      ? { id: minha.id, estado: minha.estado, valorAtual: minha.valorAtual }
      : null,
  };
}

function emAberto(e: EstadoOferta): boolean {
  return e === 'ABERTA' || e === 'CONTRAPROPOSTA';
}

function oferta(id: string): Oferta {
  const o = estado.ofertas.find((x) => x.id === id);
  if (!o) throw new ErroDemo(404, 'Oferta não encontrada nesta demonstração.');
  return o;
}

/** Registra o lance e passa a vez para o outro lado. */
function registrarLance(o: Oferta, por: AtorDaOferta, valor: number, recado?: string) {
  o.valorAtual = valor;
  o.ultimoLancePor = por;
  o.lanceEm = agora();
  o.prazoAte = emDias(DIAS_PARA_RESPONDER_OFERTA);
  o.recado = recado ?? null;
  o.lances = [
    ...(o.lances ?? []),
    { id: `l-${Date.now()}`, por, valor, recado: recado ?? null, criadoEm: agora() },
  ];
  o.minhaVez = deQuemEAVezDemo(o) === o.meuPapel;
}

function deQuemEAVezDemo(o: Oferta): AtorDaOferta | null {
  if (o.estado === 'ABERTA') return 'VENDEDOR';
  if (o.estado === 'CONTRAPROPOSTA') return 'COMPRADOR';
  return null;
}

/** Encerra a negociação e tira a vez de todo mundo. */
function encerrar(o: Oferta, estadoFinal: EstadoOferta) {
  o.estado = estadoFinal;
  o.minhaVez = false;
}

/** Média das notas recebidas, com uma casa. */
function notaMedia(): number | null {
  if (estado.avaliacoes.length === 0) return null;
  const soma = estado.avaliacoes.reduce((s, a) => s + a.nota, 0);
  return Math.round((soma / estado.avaliacoes.length) * 10) / 10;
}

// ---------------------------------------------------------------------------
// Roteador
// ---------------------------------------------------------------------------

interface Requisicao {
  metodo: string;
  caminho: string;
  busca: URLSearchParams;
  corpo: Record<string, unknown>;
  partes: string[];
}

type Manipulador = (r: Requisicao) => unknown;

/** Rotas com parâmetro viram regex: `/pedidos/:id/codigo`. */
const rotas: Array<[string, string, Manipulador]> = [
  // ----- sessão e conta -----
  ['GET', '/auth/eu', () => estado.usuario],
  ['POST', '/auth/google', () => ({ token: 'demo', usuario: estado.usuario })],
  [
    'PATCH',
    '/auth/eu',
    (r) => {
      Object.assign(estado.usuario, r.corpo);
      return estado.usuario;
    },
  ],
  [
    'PATCH',
    '/conta',
    (r) => {
      Object.assign(estado.usuario, r.corpo);
      return estado.usuario;
    },
  ],
  ['GET', '/conta/enderecos', () => ({ itens: estado.enderecos })],
  [
    'POST',
    '/conta/enderecos',
    (r) => {
      const novo = {
        id: `end-${estado.enderecos.length + 1}`,
        apelido: (r.corpo.apelido as string) ?? null,
        cep: (r.corpo.cep as string) ?? '',
        logradouro: (r.corpo.logradouro as string) ?? '',
        numero: (r.corpo.numero as string) ?? '',
        complemento: (r.corpo.complemento as string) ?? null,
        bairro: (r.corpo.bairro as string) ?? '',
        referencia: (r.corpo.referencia as string) ?? null,
        principal: estado.enderecos.length === 0,
      };
      estado.enderecos.push(novo);
      return novo;
    },
  ],
  [
    'POST',
    '/recebedores',
    () => {
      estado.usuario.recebedor = 'ATIVO';
      return { estado: 'ATIVO' };
    },
  ],

  // ----- vitrine -----
  [
    'GET',
    '/anuncios',
    (r) => {
      const termo = (r.busca.get('busca') ?? '').trim().toLowerCase();
      const vendedor = r.busca.get('vendedor');
      const condicao = r.busca.get('condicao');
      const categoria = r.busca.get('categoria');
      const ordem = r.busca.get('ordem');

      const itens = estado.anuncios
        .filter(
          (a) =>
            (!termo || a.titulo.toLowerCase().includes(termo)) &&
            (!vendedor || a.vendedor.id === vendedor) &&
            (!condicao || a.condicao === condicao) &&
            (!categoria || categoria === 'todos' || a.categoria?.slug === categoria),
        )
        .sort((a, b) => {
          if (ordem === 'menor_preco') return a.preco - b.preco;
          if (ordem === 'maior_preco') return b.preco - a.preco;
          return b.criadoEm.localeCompare(a.criadoEm);
        });

      return { itens: itens.map(comContexto), total: itens.length };
    },
  ],
  [
    'POST',
    '/anuncios',
    (r) => {
      const preco = Number(r.corpo.preco ?? 0);
      const modalidade = (r.corpo.modalidadeEntrega as Anuncio['modalidadeEntrega']) ?? 'PLATAFORMA';
      const d = calcularDescontos(preco, modalidade);
      const novo: Anuncio = {
        id: `novo-${Date.now()}`,
        titulo: (r.corpo.titulo as string) ?? 'sem título',
        descricao: (r.corpo.descricao as string) ?? '',
        preco,
        precoOriginal: null,
        condicao: (r.corpo.condicao as Anuncio['condicao']) ?? 'USADO',
        marca: null,
        tamanho: null,
        cor: null,
        pesoG: Number(r.corpo.pesoG ?? 1000),
        comprimentoCm: Number(r.corpo.comprimentoCm ?? 20),
        larguraCm: Number(r.corpo.larguraCm ?? 20),
        alturaCm: Number(r.corpo.alturaCm ?? 20),
        modalidadeEntrega: modalidade,
        // as URIs vêm do seletor de arquivos do navegador; servem para ver o
        // anúncio recém-criado na vitrine, não substituem o upload real
        fotos: ((r.corpo.fotos as string[]) ?? []).map((url, i) => ({
          id: `f-nova-${i}`,
          url,
          ordem: i,
        })),
        criadoEm: agora(),
        vendedor: {
          id: estado.usuario.id,
          nome: estado.usuario.nome,
          apelidoLoja: estado.usuario.apelidoLoja,
          bairro: estado.usuario.bairro,
        },
        categoria: null,
        entrega: { modalidade, diasParaTestar: 7, entregaInclusa: modalidade === 'PLATAFORMA' },
        taxas: { comissao: d.comissao, tarifa: d.tarifa },
      };
      estado.anuncios.unshift(novo);
      return novo;
    },
  ],
  [
    'GET',
    '/anuncios/meus/lista',
    () => ({
      itens: estado.anuncios
        .filter((a) => a.vendedor.id === estado.usuario.id)
        .map(comContexto),
    }),
  ],
  /** Minha lista de desejos. Vem antes de /anuncios/:id para não ser engolida. */
  [
    'GET',
    '/anuncios/curtidos',
    () => ({
      itens: estado.anuncios.filter((a) => estado.curtidas.has(a.id)).map(comContexto),
    }),
  ],
  [
    'GET',
    '/anuncios/:id',
    (r) => {
      const a = estado.anuncios.find((x) => x.id === r.partes[1]);
      if (!a) throw new ErroDemo(404, 'Anúncio não encontrado nesta demonstração.');
      return comContexto(a);
    },
  ],
  [
    'PATCH',
    '/anuncios/:id',
    (r) => {
      const a = estado.anuncios.find((x) => x.id === r.partes[1]);
      if (!a) throw new ErroDemo(404, 'Anúncio não encontrado.');
      if (a.vendedor.id !== estado.usuario.id) {
        throw new ErroDemo(403, 'Este anúncio não é seu.');
      }
      if (typeof r.corpo.titulo === 'string') a.titulo = r.corpo.titulo;
      if (typeof r.corpo.descricao === 'string') a.descricao = r.corpo.descricao;
      if (typeof r.corpo.preco === 'number') a.preco = r.corpo.preco;
      if (typeof r.corpo.condicao === 'string') {
        a.condicao = r.corpo.condicao as Anuncio['condicao'];
      }
      // preço mudou: as taxas mostradas mudam junto
      const d = calcularDescontos(a.preco, a.modalidadeEntrega);
      a.taxas = { comissao: d.comissao, tarifa: d.tarifa };
      return comContexto(a);
    },
  ],
  [
    'POST',
    '/anuncios/:id/curtir',
    (r) => {
      const id = r.partes[1]!;
      const jaCurtido = estado.curtidas.has(id);
      const atual = estado.curtidasPorAnuncio[id] ?? 0;
      if (jaCurtido) {
        estado.curtidas.delete(id);
        estado.curtidasPorAnuncio[id] = Math.max(0, atual - 1);
      } else {
        estado.curtidas.add(id);
        estado.curtidasPorAnuncio[id] = atual + 1;
      }
      return { curtido: !jaCurtido, curtidas: estado.curtidasPorAnuncio[id] };
    },
  ],

  // ----- seguir lojinha -----
  [
    'POST',
    '/vendedores/:id/seguir',
    (r) => {
      const id = r.partes[1]!;
      const jaSigo = estado.seguindo.has(id);
      const atual = estado.seguidoresPorVendedor[id] ?? 0;
      if (jaSigo) {
        estado.seguindo.delete(id);
        estado.seguidoresPorVendedor[id] = Math.max(0, atual - 1);
      } else {
        estado.seguindo.add(id);
        estado.seguidoresPorVendedor[id] = atual + 1;
      }
      return { seguindo: !jaSigo, seguidores: estado.seguidoresPorVendedor[id] };
    },
  ],
  [
    'GET',
    '/vendedores/:id',
    (r) => {
      const id = r.partes[1]!;
      const anuncio = estado.anuncios.find((a) => a.vendedor.id === id);
      const v = anuncio?.vendedor;
      return {
        id,
        nome: v?.nome ?? 'vendedor',
        apelidoLoja: v?.apelidoLoja ?? null,
        bairro: v?.bairro ?? null,
        cidade: 'Itinga',
        seguidores: estado.seguidoresPorVendedor[id] ?? 0,
        seguindo: estado.seguindo.has(id),
        notaMedia: notaMedia(),
        totalAvaliacoes: estado.avaliacoes.length,
      };
    },
  ],
  [
    'GET',
    '/vendedores/:id/avaliacoes',
    () => ({ itens: estado.avaliacoes, notaMedia: notaMedia() }),
  ],

  // ----- ofertas -----
  [
    'GET',
    '/ofertas',
    () => ({
      itens: [...estado.ofertas].sort((a, b) => b.lanceEm.localeCompare(a.lanceEm)),
    }),
  ],
  ['GET', '/ofertas/:id', (r) => oferta(r.partes[1]!)],
  [
    'POST',
    '/anuncios/:id/ofertas',
    (r) => {
      const a = estado.anuncios.find((x) => x.id === r.partes[1]);
      if (!a) throw new ErroDemo(404, 'Anúncio não encontrado.');
      if (a.vendedor.id === estado.usuario.id) {
        throw new ErroDemo(400, 'Você não pode fazer oferta no seu próprio anúncio.');
      }

      const valor = Number(r.corpo.valor ?? 0);
      const validacao = validarProposta(valor, a.preco);
      if (!validacao.valido) throw new ErroDemo(400, validacao.motivo);

      // uma negociação em aberto por anúncio: a nova substitui a anterior
      const anterior = estado.ofertas.find(
        (o) => o.anuncio.id === a.id && o.meuPapel === 'COMPRADOR' && emAberto(o.estado),
      );
      if (anterior) encerrar(anterior, 'CANCELADA');

      const nova: Oferta = {
        id: `of-${Date.now()}`,
        estado: 'ABERTA',
        precoAnunciado: a.preco,
        valorAtual: valor,
        ultimoLancePor: 'COMPRADOR',
        recado: (r.corpo.recado as string) ?? null,
        lanceEm: agora(),
        prazoAte: emDias(DIAS_PARA_RESPONDER_OFERTA),
        criadoEm: agora(),
        meuPapel: 'COMPRADOR',
        minhaVez: false,
        anuncio: { id: a.id, titulo: a.titulo, preco: a.preco, fotos: a.fotos },
        comprador: { id: estado.usuario.id, nome: estado.usuario.nome, fotoUrl: null },
        vendedor: {
          id: a.vendedor.id,
          nome: a.vendedor.nome,
          apelidoLoja: a.vendedor.apelidoLoja,
        },
        lances: [
          {
            id: `l-${Date.now()}`,
            por: 'COMPRADOR',
            valor,
            recado: (r.corpo.recado as string) ?? null,
            criadoEm: agora(),
          },
        ],
      };
      estado.ofertas.unshift(nova);
      return nova;
    },
  ],
  [
    'POST',
    '/ofertas/:id/aceitar',
    (r) => {
      const o = oferta(r.partes[1]!);
      if (!emAberto(o.estado)) throw new ErroDemo(400, 'Esta negociação já foi encerrada.');
      if (deQuemEAVezDemo(o) !== o.meuPapel) {
        throw new ErroDemo(400, 'Agora é a vez da outra pessoa responder.');
      }
      encerrar(o, 'ACEITA');
      return o;
    },
  ],
  [
    'POST',
    '/ofertas/:id/recusar',
    (r) => {
      const o = oferta(r.partes[1]!);
      if (!emAberto(o.estado)) throw new ErroDemo(400, 'Esta negociação já foi encerrada.');
      if (deQuemEAVezDemo(o) !== o.meuPapel) {
        throw new ErroDemo(400, 'Agora é a vez da outra pessoa responder.');
      }
      encerrar(o, 'RECUSADA');
      return o;
    },
  ],
  [
    'POST',
    '/ofertas/:id/cancelar',
    (r) => {
      const o = oferta(r.partes[1]!);
      if (!emAberto(o.estado)) throw new ErroDemo(400, 'Esta negociação já foi encerrada.');
      if (deQuemEAVezDemo(o) === o.meuPapel) {
        throw new ErroDemo(400, 'Você não pode cancelar: a proposta em aberto é da outra pessoa.');
      }
      encerrar(o, 'CANCELADA');
      return o;
    },
  ],
  [
    'POST',
    '/ofertas/:id/contrapropor',
    (r) => {
      const o = oferta(r.partes[1]!);
      if (o.estado !== 'ABERTA' || o.meuPapel !== 'VENDEDOR') {
        throw new ErroDemo(400, 'Só o vendedor contrapropõe, e só em proposta aberta.');
      }
      const valor = Number(r.corpo.valor ?? 0);
      if (valor <= o.valorAtual) {
        throw new ErroDemo(400, 'A contraproposta precisa ser maior que a oferta recebida.');
      }
      if (valor >= o.precoAnunciado) {
        throw new ErroDemo(400, 'A contraproposta precisa ser menor que o preço do anúncio.');
      }
      o.estado = 'CONTRAPROPOSTA';
      registrarLance(o, 'VENDEDOR', valor, r.corpo.recado as string | undefined);
      return o;
    },
  ],

  // ----- avaliações -----
  [
    'POST',
    '/pedidos/:id/avaliar',
    (r) => {
      const p = pedido(r.partes[1]!);
      const nota = Number(r.corpo.nota ?? 0);
      if (!Number.isInteger(nota) || nota < 1 || nota > 5) {
        throw new ErroDemo(400, 'A nota vai de 1 a 5 estrelas.');
      }
      estado.avaliacoes.unshift({
        id: `av-${Date.now()}`,
        nota,
        comentario: (r.corpo.comentario as string) ?? null,
        criadoEm: agora(),
        autor: { nome: estado.usuario.nome, fotoUrl: null },
        pedido: { codigo: p.codigo, anuncio: { titulo: p.anuncio.titulo } },
      });
      p.podeAvaliar = false;
      return { ok: true, notaMedia: notaMedia() };
    },
  ],

  // ----- pedidos -----
  [
    'GET',
    '/pedidos/simular',
    (r) => {
      const a = estado.anuncios.find((x) => x.id === r.busca.get('anuncio'));
      if (!a) throw new ErroDemo(404, 'Anúncio não encontrado.');
      const modalidade =
        (r.busca.get('modalidade') as Anuncio['modalidadeEntrega']) ?? a.modalidadeEntrega;

      const combinada = estado.ofertas.find(
        (o) =>
          o.id === r.busca.get('oferta') &&
          o.estado === 'ACEITA' &&
          o.meuPapel === 'COMPRADOR' &&
          o.anuncio.id === a.id,
      );
      return resumoDaCompra(a, modalidade, combinada?.valorAtual);
    },
  ],
  [
    'POST',
    '/pedidos',
    (r) => {
      const a = estado.anuncios.find((x) => x.id === r.corpo.anuncioId);
      if (!a) throw new ErroDemo(404, 'Anúncio não encontrado.');
      const modalidade = a.modalidadeEntrega;

      // oferta aceita minha, deste anúncio: vale o valor combinado
      const combinada = estado.ofertas.find(
        (o) =>
          o.id === r.corpo.ofertaId &&
          o.estado === 'ACEITA' &&
          o.meuPapel === 'COMPRADOR' &&
          o.anuncio.id === a.id,
      );
      const preco = combinada?.valorAtual ?? a.preco;
      const d = calcularDescontos(preco, modalidade);
      const id = `ped-${Date.now()}`;
      const novo: PedidoDemo = {
        id,
        codigo: `VI-${String(Date.now()).slice(-6)}`,
        estado: modalidade === 'VENDEDOR' ? 'AGUARDANDO_ENTREGA_DO_VENDEDOR' : 'PAGO',
        modalidade,
        valorProduto: preco,
        valorTotal: preco,
        valorComissao: d.comissao,
        valorTarifa: d.tarifa,
        taxaComissao: COMISSAO,
        valorVendedor: d.vendedor,
        criadoEm: agora(),
        pagoEm: agora(),
        entregueEm: null,
        prazoTesteAte: null,
        prazoConfirmacaoAte: null,
        confirmadaPor: null,
        anuncio: { titulo: a.titulo, fotos: a.fotos },
        vendedor: { nome: a.vendedor.nome, apelidoLoja: a.vendedor.apelidoLoja },
        comprador: { nome: estado.usuario.nome },
        entrega:
          modalidade === 'PLATAFORMA'
            ? { id: `ent-${id}`, estado: 'AGUARDANDO_ATRIBUICAO', codigoConfirmacao: codigoNovo(), entregador: null }
            : null,
        entregaDevolucao: null,
        reembolso: null,
        podePedirReembolso: false,
        diasRestantesParaTestar: null,
        eventos: [{ id: `${id}-1`, tipo: 'PEDIDO_CRIADO', criadoEm: agora() }],
        lado: 'compra',
      };
      if (modalidade === 'VENDEDOR') novo.codigoConfirmacao = codigoNovo();
      estado.pedidos.unshift(novo);

      if (modalidade === 'PLATAFORMA') {
        estado.corridas.unshift({
          id: `cor-${Date.now()}`,
          tipo: 'ENTREGA',
          estado: 'AGUARDANDO_ATRIBUICAO',
          valorEntregador: 500,
          coletaEndereco: 'Rua das Flores, 120, Centro, Itinga/MG',
          coletaContato: a.vendedor.nome,
          coletaTelefone: '(33) ••••-2211',
          entregaEndereco: 'Av. Principal, 45, Bairro Novo, Itinga/MG',
          entregaContato: estado.usuario.nome,
          entregaTelefone: '(33) ••••-0000',
          telefoneLiberado: false,
          criadoEm: agora(),
          entregador: null,
          pedido: {
            codigo: novo.codigo,
            valorProduto: a.preco,
            anuncio: {
              titulo: a.titulo,
              pesoG: a.pesoG,
              comprimentoCm: a.comprimentoCm,
              larguraCm: a.larguraCm,
              alturaCm: a.alturaCm,
              fotos: a.fotos,
            },
          },
        });
      }
      return { pedido: { id }, pix: { qrCode: '00020126DEMONSTRACAO...5204000053039865802BR' } };
    },
  ],
  [
    'GET',
    '/pedidos/compras',
    () => ({ itens: estado.pedidos.filter((p) => p.lado === 'compra') }),
  ],
  [
    'GET',
    '/pedidos/vendas',
    () => {
      const vendas = estado.pedidos.filter((p) => p.lado === 'venda');
      const aReceber = vendas
        .filter((p) => !p.repassadoEm && !['REEMBOLSADO', 'CANCELADO'].includes(p.estado))
        .reduce((s, p) => s + p.valorVendedor, 0);
      const recebido = vendas
        .filter((p) => p.repassadoEm)
        .reduce((s, p) => s + p.valorVendedor, 0);
      return { aReceber, recebido, diasParaLiberar: 7, pedidos: vendas };
    },
  ],
  [
    'GET',
    '/pedidos/:id',
    (r) => pedido(r.partes[1]!),
  ],
  [
    'GET',
    '/pedidos/:id/codigo',
    (r) => {
      const p = pedido(r.partes[1]!);
      return {
        codigo: p.codigoConfirmacao ?? p.entrega?.codigoConfirmacao ?? null,
        modalidade: p.modalidade,
        estado: p.estado,
        paraQuem:
          p.modalidade === 'VENDEDOR'
            ? `informe ao ${p.vendedor?.nome ?? 'vendedor'} na hora de receber`
            : 'informe ao entregador na hora de receber',
      };
    },
  ],
  [
    'POST',
    '/pedidos/:id/recebi',
    (r) => {
      const p = pedido(r.partes[1]!);
      marcarEntregueDoVendedor(p, 'comprador');
      return p;
    },
  ],
  [
    'POST',
    '/pedidos/:id/entreguei',
    (r) => {
      const p = pedido(r.partes[1]!);
      // o corpo é `{ codigo }`, igual ao da API de verdade (rotas/pedidos.ts)
      const informado = String(r.corpo.codigo ?? '').trim();
      if (!informado || informado !== p.codigoConfirmacao) {
        throw new ErroDemo(400, 'Código não confere. Peça de novo ao comprador.');
      }
      marcarEntregueDoVendedor(p, 'codigo');
      return p;
    },
  ],
  [
    'POST',
    '/pedidos/:id/declarar-entrega',
    (r) => {
      const p = pedido(r.partes[1]!);
      p.estado = 'ENTREGA_DECLARADA';
      p.prazoConfirmacaoAte = emDias(DIAS_PARA_CONFIRMACAO_AUTOMATICA);
      return p;
    },
  ],
  [
    'GET',
    '/pedidos/:id/reembolso/previa',
    (r) => {
      const p = pedido(r.partes[1]!);
      return {
        valorPago: p.valorTotal,
        valorReembolsado: p.valorTotal,
        valorRetido: 0,
        explicacao:
          'Dentro dos 7 dias a devolução é integral (CDC art. 49). A comissão e a tarifa saem do caixa da plataforma, não do seu bolso.',
        prazoTesteAte: p.prazoTesteAte ?? null,
      };
    },
  ],
  [
    'POST',
    '/pedidos/:id/reembolso',
    (r) => {
      const p = pedido(r.partes[1]!);
      p.estado = 'DEVOLUCAO_SOLICITADA';
      p.podePedirReembolso = false;
      p.reembolso = { estado: 'SOLICITADO', valorReembolsado: p.valorTotal };
      estado.devolucoes.unshift({
        id: `dev-${Date.now()}`,
        estado: 'SOLICITADO',
        motivo: (r.corpo.motivo as string) ?? 'outro motivo',
        descricao: (r.corpo.descricao as string) ?? null,
        valorReembolsado: p.valorTotal,
        solicitadoEm: agora(),
        pedido: {
          id: p.id,
          codigo: p.codigo,
          valorTotal: p.valorTotal,
          modalidade: p.modalidade,
          comprador: { nome: estado.usuario.nome, telefone: estado.usuario.telefone },
          vendedor: { nome: p.vendedor?.nome ?? 'vendedor', telefone: '(33) 98877-2211' },
          anuncio: { titulo: p.anuncio.titulo },
        },
      });
      return p;
    },
  ],

  // ----- mensagens -----
  [
    'GET',
    '/mensagens',
    () => ({
      itens: estado.conversas.map(({ mensagens: _m, anuncioId: _a, ...resto }) => resto),
    }),
  ],
  [
    'POST',
    '/mensagens',
    (r) => {
      const c = estado.conversas.find(
        (x) => x.id === r.corpo.conversaId || x.anuncioId === r.corpo.anuncioId,
      );
      const nova = {
        id: `m-${Date.now()}`,
        texto: (r.corpo.texto as string) ?? '',
        autorId: estado.usuario.id,
        criadoEm: agora(),
        autor: { id: estado.usuario.id, nome: estado.usuario.nome },
      };
      if (c) {
        c.mensagens.push(nova);
        c.texto = nova.texto;
        c.criadoEm = nova.criadoEm;
        c.souOAutor = true;
        c.naoLida = false;
      }
      return nova;
    },
  ],
  [
    'GET',
    '/mensagens/pedido/:id',
    (r) => {
      const c = estado.conversas.find((x) => x.pedido?.id === r.partes[2]);
      return { itens: c?.mensagens ?? [] };
    },
  ],
  [
    'GET',
    '/mensagens/:id',
    (r) => {
      const c = estado.conversas.find((x) => x.id === r.partes[1] || x.anuncioId === r.partes[1]);
      return { itens: c?.mensagens ?? [] };
    },
  ],

  // ----- área do entregador -----
  [
    'GET',
    '/entregas/oferecidas',
    () => ({ itens: estado.corridas.filter((c) => c.estado === 'ATRIBUIDA') }),
  ],
  [
    'GET',
    '/entregas/minhas',
    () => ({
      itens: estado.corridas.filter((c) =>
        ['ACEITA', 'A_CAMINHO_DA_COLETA', 'CHEGOU_NA_COLETA', 'PRODUTO_COLETADO', 'EM_ROTA_PARA_ENTREGA', 'CHEGOU_NA_ENTREGA'].includes(
          c.estado,
        ),
      ),
    }),
  ],
  [
    'GET',
    '/entregas/historico',
    () => {
      const itens = estado.corridas.filter((c) => c.estado === 'ENTREGUE');
      return {
        itens,
        extrato: {
          ganhoHoje: itens.filter((c) => c.criadoEm > emDias(-1)).length * 500,
          ganhoTotal: itens.length * 500,
        },
      };
    },
  ],
  [
    'POST',
    '/entregas/disponibilidade',
    (r) => {
      estado.entregadorDisponivel = Boolean(r.corpo.disponivel);
      return { disponivel: estado.entregadorDisponivel };
    },
  ],
  ['POST', '/entregas/posicao', () => ({ ok: true })],
  [
    'POST',
    '/entregas/:id/aceitar',
    (r) => {
      const c = corrida(r.partes[1]!);
      c.estado = 'ACEITA';
      c.telefoneLiberado = true;
      c.coletaTelefone = '(33) 98877-2211';
      c.entregaTelefone = '(33) 98866-3344';
      c.entregador = { id: 'e-joao', nome: 'João' };
      refletirNoPedido(c, 'ACEITA');
      return c;
    },
  ],
  [
    'POST',
    '/entregas/:id/recusar',
    (r) => {
      const c = corrida(r.partes[1]!);
      c.estado = 'AGUARDANDO_ATRIBUICAO';
      c.entregador = null;
      c.recusas = [
        ...(c.recusas ?? []),
        {
          motivo: (r.corpo.motivo as string) ?? 'sem motivo',
          criadoEm: agora(),
          entregador: { nome: 'João' },
        },
      ];
      return c;
    },
  ],
  [
    'POST',
    '/entregas/:id/coletei',
    (r) => {
      const c = corrida(r.partes[1]!);
      c.estado = 'PRODUTO_COLETADO';
      c.volumes = Number(r.corpo.volumes ?? 1);
      c.fotoPacoteUrl = (r.corpo.fotoPacote as string) ?? 'demo://foto-do-pacote';
      refletirNoPedido(c, 'PRODUTO_COLETADO');
      return c;
    },
  ],
  [
    'POST',
    '/entregas/:id/entreguei',
    (r) => {
      const c = corrida(r.partes[1]!);
      const p = estado.pedidos.find((x) => x.codigo === c.pedido.codigo);
      const esperado = p?.entrega?.codigoConfirmacao;
      const informado = String(r.corpo.codigoConfirmacao ?? '').trim();
      if (c.tipo === 'ENTREGA' && esperado && informado !== esperado) {
        throw new ErroDemo(400, 'Código não confere. Peça de novo ao comprador.');
      }
      c.estado = 'ENTREGUE';
      refletirNoPedido(c, 'ENTREGUE');
      return c;
    },
  ],

  // passos simples da rua
  ...(
    [
      ['a-caminho', 'A_CAMINHO_DA_COLETA'],
      ['cheguei-na-coleta', 'CHEGOU_NA_COLETA'],
      ['sair-para-entrega', 'EM_ROTA_PARA_ENTREGA'],
      ['cheguei-na-entrega', 'CHEGOU_NA_ENTREGA'],
    ] as Array<[string, EstadoEntrega]>
  ).map(
    ([caminho, novo]) =>
      [
        'POST',
        `/entregas/:id/${caminho}`,
        (r: Requisicao) => {
          const c = corrida(r.partes[1]!);
          c.estado = novo;
          refletirNoPedido(c, novo);
          return c;
        },
      ] as [string, string, Manipulador],
  ),

  // ----- painel do admin -----
  [
    'GET',
    '/admin/resumo',
    () => {
      const pagos = estado.pedidos.filter((p) => p.estado !== 'AGUARDANDO_PAGAMENTO');
      const comissao = pagos.reduce((s, p) => s + p.valorComissao, 0);
      const tarifas = pagos.reduce((s, p) => s + p.valorTarifa, 0);
      const custo = estado.corridas.filter((c) => c.estado === 'ENTREGUE').length * 500;
      return {
        anunciosAtivos: estado.anuncios.length,
        pedidosEmAndamento: pagos.filter(
          (p) => !['CONCLUIDO', 'REEMBOLSADO', 'CANCELADO'].includes(p.estado),
        ).length,
        aguardandoAtribuicao: estado.corridas.filter((c) => c.estado === 'AGUARDANDO_ATRIBUICAO')
          .length,
        corridasEmRota: estado.corridas.filter((c) =>
          ['EM_ROTA_PARA_ENTREGA', 'CHEGOU_NA_ENTREGA', 'PRODUTO_COLETADO'].includes(c.estado),
        ).length,
        devolucoesAbertas: estado.devolucoes.filter((d) =>
          ['SOLICITADO', 'EM_ANALISE', 'APROVADO'].includes(d.estado),
        ).length,
        comissaoAcumulada: comissao,
        tarifasAcumuladas: tarifas,
        custoComEntregas: custo,
        receitaLiquida: comissao + tarifas - custo,
      };
    },
  ],
  [
    'GET',
    '/admin/entregas/fila',
    () => ({
      itens: estado.corridas.filter((c) =>
        ['AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA'].includes(c.estado),
      ),
    }),
  ],
  [
    'GET',
    '/admin/entregas/:id/candidatos',
    () => ({ itens: entregadoresDemo }),
  ],
  [
    'POST',
    '/admin/entregas/:id/atribuir',
    (r) => {
      const c = corrida(r.partes[2]!);
      const escolhido =
        entregadoresDemo.find((e) => e.id === r.corpo.entregadorId) ?? entregadoresDemo[0]!;
      c.estado = 'ATRIBUIDA';
      c.entregador = { id: escolhido.id, nome: escolhido.nome };
      refletirNoPedido(c, 'ATRIBUIDA');
      return c;
    },
  ],
  [
    'POST',
    '/admin/entregas/:id/devolver-para-fila',
    (r) => {
      const c = corrida(r.partes[2]!);
      c.estado = 'AGUARDANDO_ATRIBUICAO';
      c.entregador = null;
      c.telefoneLiberado = false;
      return c;
    },
  ],
  [
    'POST',
    '/admin/entregas/:id/cancelar',
    (r) => {
      const c = corrida(r.partes[2]!);
      c.estado = 'CANCELADA';
      return c;
    },
  ],
  ['GET', '/admin/entregadores', () => ({ itens: entregadoresDemo })],
  ['GET', '/admin/reembolsos', () => ({ itens: estado.devolucoes })],
  [
    'POST',
    '/admin/reembolsos/:id/aprovar',
    (r) => {
      const d = estado.devolucoes.find((x) => x.id === r.partes[2]);
      if (!d) throw new ErroDemo(404, 'Devolução não encontrada.');
      d.estado = 'APROVADO';
      const p = estado.pedidos.find((x) => x.id === d.pedido.id);
      if (p) {
        // plataforma abre a coleta reversa; vendedor combina entre as partes
        p.estado = p.modalidade === 'PLATAFORMA' ? 'DEVOLUCAO_APROVADA' : 'DEVOLUCAO_COMBINADA';
        p.reembolso = { estado: 'APROVADO', valorReembolsado: p.valorTotal };
        if (p.modalidade === 'PLATAFORMA') {
          estado.corridas.unshift({
            id: `cor-dev-${Date.now()}`,
            tipo: 'DEVOLUCAO',
            estado: 'AGUARDANDO_ATRIBUICAO',
            valorEntregador: 500,
            coletaEndereco: 'Av. Principal, 45, Bairro Novo, Itinga/MG',
            coletaContato: estado.usuario.nome,
            coletaTelefone: '(33) ••••-0000',
            entregaEndereco: 'Rua das Flores, 120, Centro, Itinga/MG',
            entregaContato: p.vendedor?.nome ?? 'vendedor',
            entregaTelefone: '(33) ••••-2211',
            observacoes: 'DEVOLUÇÃO: buscar no comprador e devolver ao vendedor.',
            telefoneLiberado: false,
            criadoEm: agora(),
            entregador: null,
            pedido: { codigo: p.codigo, valorProduto: p.valorProduto, anuncio: { titulo: p.anuncio.titulo } },
          });
        }
      }
      return d;
    },
  ],
  [
    'POST',
    '/admin/reembolsos/:id/recusar',
    (r) => {
      const d = estado.devolucoes.find((x) => x.id === r.partes[2]);
      if (!d) throw new ErroDemo(404, 'Devolução não encontrada.');
      d.estado = 'RECUSADO';
      const p = estado.pedidos.find((x) => x.id === d.pedido.id);
      if (p) {
        p.estado = 'ENTREGUE';
        p.reembolso = null;
      }
      return d;
    },
  ],
  [
    'POST',
    '/admin/reembolsos/:id/confirmar-retorno',
    (r) => {
      const d = estado.devolucoes.find((x) => x.id === r.partes[2]);
      if (!d) throw new ErroDemo(404, 'Devolução não encontrada.');
      d.estado = 'CONCLUIDO';
      const p = estado.pedidos.find((x) => x.id === d.pedido.id);
      if (p) {
        p.estado = 'REEMBOLSADO';
        p.reembolso = { estado: 'CONCLUIDO', valorReembolsado: p.valorTotal };
      }
      return d;
    },
  ],
];

function combina(padrao: string, partes: string[]): boolean {
  const p = padrao.split('/').filter(Boolean);
  if (p.length !== partes.length) return false;
  return p.every((seg, i) => seg.startsWith(':') || seg === partes[i]);
}

/**
 * Ponto de entrada. `src/api/cliente.ts` chama isto no lugar do `fetch`.
 * Devolve uma Promise para o app se comportar igual ao caso real (loading,
 * erro, etc.), com um atraso curto para os estados de carregamento aparecerem.
 */
export async function responderDemo<T>(
  caminho: string,
  metodo: string,
  corpo: unknown,
): Promise<T> {
  const [semBusca, consulta = ''] = caminho.split('?');
  const partes = semBusca!.split('/').filter(Boolean);

  const requisicao: Requisicao = {
    metodo,
    caminho: semBusca!,
    busca: new URLSearchParams(consulta),
    corpo: (corpo ?? {}) as Record<string, unknown>,
    partes,
  };

  await new Promise((r) => setTimeout(r, 120));

  const rota = rotas.find(([m, padrao]) => m === metodo && combina(padrao, partes));
  if (!rota) {
    throw new ErroDemo(
      404,
      `Esta ação não existe no modo demonstração (${metodo} ${semBusca}).`,
    );
  }

  return rota[2](requisicao) as T;
}

export { ErroDemo };
