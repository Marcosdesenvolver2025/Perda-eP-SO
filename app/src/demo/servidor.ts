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
  Corrida,
  EstadoEntrega,
  Pedido,
  SolicitacaoDeDevolucao,
  Usuario,
} from '../api/tipos';
import { calcularDescontos, COMISSAO, DIAS_PARA_CONFIRMACAO_AUTOMATICA } from '../regras/limites';
import {
  anunciosSemente,
  conversasSemente,
  corridasSemente,
  devolucoesSemente,
  enderecosSemente,
  entregadoresDemo,
  pedidosSemente,
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

function resumoDaCompra(a: Anuncio, modalidade: Anuncio['modalidadeEntrega']) {
  const d = calcularDescontos(a.preco, modalidade);
  return {
    valorProduto: a.preco,
    valorTotal: a.preco,
    taxaComissao: COMISSAO,
    valorComissao: d.comissao,
    valorTarifa: d.tarifa,
    valorVendedor: d.vendedor,
    modalidade,
  };
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

      return { itens, total: itens.length };
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
    () => ({ itens: estado.anuncios.filter((a) => a.vendedor.id === estado.usuario.id) }),
  ],
  [
    'GET',
    '/anuncios/:id',
    (r) => {
      const a = estado.anuncios.find((x) => x.id === r.partes[1]);
      if (!a) throw new ErroDemo(404, 'Anúncio não encontrado nesta demonstração.');
      return a;
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
      return resumoDaCompra(a, modalidade);
    },
  ],
  [
    'POST',
    '/pedidos',
    (r) => {
      const a = estado.anuncios.find((x) => x.id === r.corpo.anuncioId);
      if (!a) throw new ErroDemo(404, 'Anúncio não encontrado.');
      const modalidade = a.modalidadeEntrega;
      const d = calcularDescontos(a.preco, modalidade);
      const id = `ped-${Date.now()}`;
      const novo: PedidoDemo = {
        id,
        codigo: `VI-${String(Date.now()).slice(-6)}`,
        estado: modalidade === 'VENDEDOR' ? 'AGUARDANDO_ENTREGA_DO_VENDEDOR' : 'PAGO',
        modalidade,
        valorProduto: a.preco,
        valorTotal: a.preco,
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
