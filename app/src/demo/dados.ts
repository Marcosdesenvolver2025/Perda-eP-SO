/**
 * Dados do modo demonstração.
 *
 * ISOLADO DO CÓDIGO DE PRODUÇÃO: nada daqui é importado por tela nenhuma.
 * Só `src/demo/servidor.ts` lê este arquivo, e só quando a flag está ligada.
 *
 * As contas (comissão, tarifa, líquido do vendedor) NÃO são repetidas aqui —
 * saem de `src/regras/limites.ts`, o mesmo módulo que as telas usam. Se a
 * regra mudar, a demonstração muda junto.
 */

import type {
  Anuncio,
  Avaliacao,
  Corrida,
  EstadoEntrega,
  EstadoPedido,
  Foto,
  ModalidadeEntrega,
  Oferta,
  Pedido,
  SolicitacaoDeDevolucao,
  Usuario,
} from '../api/tipos';
import { calcularDescontos } from '../regras/limites';

// ---------------------------------------------------------------------------
// Fotos: SVG embutido, sem rede
// ---------------------------------------------------------------------------

/**
 * Uma "foto" de produto desenhada na hora. Evita depender de servidor de
 * imagem para a demonstração abrir em qualquer lugar, inclusive offline.
 *
 * O SVG vai **cru**, sem percent-encoding. O react-native-web reconhece o
 * prefixo `data:image/svg+xml;utf8,` e aplica `encodeURIComponent` ele mesmo
 * antes de montar o `background-image` — se a gente já mandasse codificado,
 * ele codificaria de novo (`%20` vira `%2520`) e a imagem sumiria calada.
 */
function foto(id: string, rotulo: string, cor: string, ordem = 0): Foto {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600">` +
    `<rect width="600" height="600" fill="${cor}" fill-opacity="0.18"/>` +
    `<rect y="420" width="600" height="180" fill="${cor}" fill-opacity="0.28"/>` +
    `<text x="300" y="345" font-size="210" text-anchor="middle">${rotulo}</text>` +
    `</svg>`;
  return { id, url: `data:image/svg+xml;utf8,${svg}`, ordem };
}

const dias = (n: number) => new Date(Date.now() + n * 86_400_000).toISOString();
const horas = (n: number) => new Date(Date.now() + n * 3_600_000).toISOString();

// ---------------------------------------------------------------------------
// Pessoas
// ---------------------------------------------------------------------------

export const usuarioDemo: Usuario = {
  id: 'u-voce',
  nome: 'Marcos',
  email: 'voce@exemplo.com',
  fotoUrl: null,
  telefone: '(33) 98888-0000',
  papel: 'CLIENTE',
  apelidoLoja: 'minha lojinha',
  bioLoja: 'vendo o que não uso mais. entrego no centro.',
  bairro: 'Centro',
  cidade: 'Itinga',
  recebedor: 'ATIVO',
  precisaCompletarCadastro: false,
};

const vendedores = {
  rubia: { id: 'v-rubia', nome: 'Rubia', apelidoLoja: 'rubia store', bairro: 'Centro' },
  rondson: { id: 'v-rondson', nome: 'Rondson', apelidoLoja: 'seu rondson', bairro: 'Vila Nova' },
  cleide: { id: 'v-cleide', nome: 'Cleide', apelidoLoja: 'cantinho da cleide', bairro: 'Bairro Novo' },
  voce: { id: 'u-voce', nome: 'Marcos', apelidoLoja: 'minha lojinha', bairro: 'Centro' },
};

export const entregadoresDemo = [
  { id: 'e-joao', nome: 'João', disponivel: true, corridasAtivas: 1, capacidade: 4 },
  { id: 'e-pedro', nome: 'Pedro', disponivel: true, corridasAtivas: 0, capacidade: 4 },
  { id: 'e-lucia', nome: 'Lúcia', disponivel: true, corridasAtivas: 3, capacidade: 4 },
  { id: 'e-tiao', nome: 'Tião', disponivel: false, corridasAtivas: 0, capacidade: 4 },
];

// ---------------------------------------------------------------------------
// Anúncios
// ---------------------------------------------------------------------------

interface Molde {
  id: string;
  titulo: string;
  preco: number;
  emoji: string;
  cor: string;
  vendedor?: keyof typeof vendedores;
  modalidade?: ModalidadeEntrega;
  condicao?: Anuncio['condicao'];
  pesoG?: number;
  comprimentoCm?: number;
  larguraCm?: number;
  alturaCm?: number;
  precoOriginal?: number;
  descricao?: string;
  categoria?: { slug: string; nome: string };
  /** Marca os pares de fronteira da tabela de tarifas. */
  nota?: string;
}

function montar(m: Molde): Anuncio {
  const modalidade = m.modalidade ?? 'PLATAFORMA';
  const d = calcularDescontos(m.preco, modalidade);
  return {
    id: m.id,
    titulo: m.titulo,
    descricao:
      m.descricao ??
      'Produto em bom estado, pouco usado. Pode ver antes de fechar. Qualquer dúvida, chama no chat.',
    preco: m.preco,
    precoOriginal: m.precoOriginal ?? null,
    condicao: m.condicao ?? 'USADO',
    marca: null,
    tamanho: null,
    cor: null,
    pesoG: m.pesoG ?? 1_500,
    comprimentoCm: m.comprimentoCm ?? 30,
    larguraCm: m.larguraCm ?? 20,
    alturaCm: m.alturaCm ?? 12,
    modalidadeEntrega: modalidade,
    fotos: [foto(`f-${m.id}`, m.emoji, m.cor)],
    criadoEm: dias(-(1 + (m.id.length % 9))),
    vendedor: vendedores[m.vendedor ?? 'rubia'],
    categoria: m.categoria ?? null,
    entrega: { modalidade, diasParaTestar: 7, entregaInclusa: modalidade === 'PLATAFORMA' },
    taxas: { comissao: d.comissao, tarifa: d.tarifa },
  };
}

/**
 * Os seis pares de fronteira da tabela de tarifas, lado a lado.
 * Existem para o degrau aparecer na tela, não só no teste.
 */
const fronteiras: Molde[] = [
  {
    id: 'faixa-2499',
    titulo: 'caneca de porcelana',
    preco: 2_499,
    emoji: '☕',
    cor: '#00DF13',
    condicao: 'NOVO',
    pesoG: 600,
    nota: 'último centavo da 1ª faixa — tarifa R$ 2,50',
  },
  {
    id: 'faixa-2500',
    titulo: 'caneca de porcelana (par)',
    preco: 2_500,
    emoji: '☕',
    cor: '#0aa',
    condicao: 'NOVO',
    pesoG: 1_100,
    nota: 'um centavo acima — tarifa sobe para R$ 4,50',
  },
  {
    id: 'faixa-4999',
    titulo: 'luminária de mesa',
    preco: 4_999,
    emoji: '💡',
    cor: '#f59e0b',
    pesoG: 900,
    nota: 'fim da 2ª faixa — tarifa R$ 4,50',
  },
  {
    id: 'faixa-5000',
    titulo: 'luminária de mesa com cúpula',
    preco: 5_000,
    emoji: '💡',
    cor: '#d97706',
    pesoG: 1_200,
    nota: 'tarifa sobe para R$ 6,50',
  },
  {
    id: 'faixa-9999',
    titulo: 'fone bluetooth',
    preco: 9_999,
    emoji: '🎧',
    cor: '#6366f1',
    condicao: 'NOVO',
    precoOriginal: 14_900,
    pesoG: 300,
    nota: 'fim da 3ª faixa — tarifa R$ 6,50',
  },
  {
    id: 'faixa-10000',
    titulo: 'fone bluetooth com estojo',
    preco: 10_000,
    emoji: '🎧',
    cor: '#4f46e5',
    condicao: 'NOVO',
    pesoG: 400,
    nota: 'tarifa sobe para R$ 8,50',
  },
  {
    id: 'faixa-19999',
    titulo: 'cadeira de escritório',
    preco: 19_999,
    emoji: '🪑',
    cor: '#0ea5e9',
    pesoG: 9_000,
    larguraCm: 60,
    alturaCm: 95,
    nota: 'fim da 4ª faixa — tarifa R$ 8,50',
  },
  {
    id: 'faixa-20000',
    titulo: 'cadeira de escritório com braço',
    preco: 20_000,
    emoji: '🪑',
    cor: '#0284c7',
    pesoG: 11_000,
    larguraCm: 65,
    alturaCm: 98,
    nota: 'tarifa sobe para R$ 10,50',
  },
  {
    id: 'faixa-49999',
    titulo: 'bicicleta aro 26',
    preco: 49_999,
    emoji: '🚲',
    cor: '#22c55e',
    condicao: 'SEMINOVO',
    pesoG: 14_000,
    comprimentoCm: 170,
    larguraCm: 60,
    alturaCm: 95,
    nota: 'último centavo antes do maior degrau — tarifa R$ 10,50',
  },
  {
    id: 'faixa-50000',
    titulo: 'bicicleta aro 29',
    preco: 50_000,
    emoji: '🚲',
    cor: '#15803d',
    condicao: 'SEMINOVO',
    pesoG: 15_000,
    comprimentoCm: 180,
    larguraCm: 65,
    alturaCm: 100,
    nota: 'um centavo acima e a tarifa salta R$ 4,00 — vai para R$ 14,50',
  },
];

const variados: Molde[] = [
  {
    id: 'a-celular',
    titulo: 'celular samsung a15 128gb',
    preco: 78_000,
    emoji: '📱',
    cor: '#334155',
    condicao: 'SEMINOVO',
    precoOriginal: 99_000,
    vendedor: 'rondson',
    pesoG: 400,
    categoria: { slug: 'eletronicos', nome: 'eletrônicos' },
    descricao: 'Comprado ano passado, sem risco na tela. Vai com carregador e capinha.',
  },
  {
    id: 'a-jaqueta',
    titulo: 'jaqueta de couro tam M',
    preco: 12_000,
    emoji: '🧥',
    cor: '#78350f',
    vendedor: 'cleide',
    pesoG: 1_200,
    categoria: { slug: 'moda', nome: 'moda' },
  },
  {
    id: 'a-tenis',
    titulo: 'tênis nike tam 40',
    preco: 15_000,
    emoji: '👟',
    cor: '#ef4444',
    pesoG: 900,
    categoria: { slug: 'moda', nome: 'moda' },
  },
  {
    id: 'a-panela',
    titulo: 'panela de pressão 4,5L',
    preco: 6_000,
    emoji: '🍲',
    cor: '#64748b',
    condicao: 'NOVO',
    vendedor: 'cleide',
    pesoG: 2_000,
    categoria: { slug: 'casa', nome: 'casa' },
  },
  {
    id: 'a-ventilador',
    titulo: 'ventilador 40cm',
    preco: 8_500,
    emoji: '🌀',
    cor: '#38bdf8',
    pesoG: 4_000,
    larguraCm: 45,
    alturaCm: 45,
    categoria: { slug: 'casa', nome: 'casa' },
  },
  {
    id: 'a-carrinho',
    titulo: 'carrinho de bebê',
    preco: 18_000,
    emoji: '👶',
    cor: '#f472b6',
    vendedor: 'rondson',
    pesoG: 9_000,
    larguraCm: 60,
    alturaCm: 100,
    categoria: { slug: 'infantil', nome: 'infantil' },
  },
  {
    id: 'a-livros',
    titulo: 'coleção de livros (12 títulos)',
    preco: 4_500,
    emoji: '📚',
    cor: '#a855f7',
    pesoG: 5_000,
  },
  {
    id: 'a-violao',
    titulo: 'violão nylon com capa',
    preco: 26_000,
    emoji: '🎸',
    cor: '#b45309',
    condicao: 'SEMINOVO',
    vendedor: 'voce',
    pesoG: 3_000,
    comprimentoCm: 105,
    larguraCm: 40,
    alturaCm: 15,
  },
  {
    id: 'a-monitor',
    titulo: 'monitor 24 polegadas',
    preco: 42_000,
    emoji: '🖥️',
    cor: '#1e293b',
    condicao: 'SEMINOVO',
    vendedor: 'rondson',
    pesoG: 4_500,
    larguraCm: 56,
    alturaCm: 42,
    categoria: { slug: 'eletronicos', nome: 'eletrônicos' },
  },
  {
    id: 'a-bola',
    titulo: 'bola de futebol society',
    preco: 3_500,
    emoji: '⚽',
    cor: '#16a34a',
    pesoG: 450,
    categoria: { slug: 'esporte', nome: 'esporte' },
  },
];

/** Itens que NÃO cabem na entrega da plataforma — só modo vendedor. */
const grandes: Molde[] = [
  {
    id: 'a-geladeira',
    titulo: 'geladeira frost free 375L',
    preco: 145_000,
    emoji: '🧊',
    cor: '#94a3b8',
    condicao: 'SEMINOVO',
    modalidade: 'VENDEDOR',
    vendedor: 'rondson',
    pesoG: 62_000,
    comprimentoCm: 70,
    larguraCm: 70,
    alturaCm: 175,
    descricao:
      'Funcionando perfeitamente. Não entra na entrega do app pelo peso e pela altura — eu mesmo levo, ou você busca.',
    categoria: { slug: 'casa', nome: 'casa' },
  },
  {
    id: 'a-sofa',
    titulo: 'sofá 3 lugares',
    preco: 55_000,
    emoji: '🛋️',
    cor: '#7c3aed',
    modalidade: 'VENDEDOR',
    vendedor: 'cleide',
    pesoG: 38_000,
    comprimentoCm: 200,
    larguraCm: 190,
    alturaCm: 85,
    descricao: 'Tecido suede, sem rasgo. Entrego na cidade com o carro do meu irmão.',
    categoria: { slug: 'casa', nome: 'casa' },
  },
  {
    id: 'a-mesa',
    titulo: 'mesa de jantar 6 lugares',
    preco: 38_000,
    emoji: '🪟',
    cor: '#92400e',
    modalidade: 'VENDEDOR',
    pesoG: 32_000,
    comprimentoCm: 160,
    larguraCm: 90,
    alturaCm: 78,
    categoria: { slug: 'casa', nome: 'casa' },
  },
  {
    id: 'a-guardaroupa',
    titulo: 'guarda-roupa 6 portas',
    preco: 62_000,
    emoji: '🚪',
    cor: '#57534e',
    modalidade: 'VENDEDOR',
    vendedor: 'cleide',
    pesoG: 85_000,
    comprimentoCm: 60,
    larguraCm: 250,
    alturaCm: 220,
    descricao: 'Desmontado, pronto pra levar. Combino a entrega dentro de Itinga.',
  },
];

export const anunciosSemente: Anuncio[] = [...fronteiras, ...variados, ...grandes].map(montar);

/** Mapa id → nota didática sobre a faixa. Só a demonstração usa. */
export const notasDeFaixa: Record<string, string> = Object.fromEntries(
  [...fronteiras, ...variados, ...grandes]
    .filter((m) => m.nota)
    .map((m) => [m.id, m.nota!]),
);

// ---------------------------------------------------------------------------
// Pedidos — um em cada estado da máquina
// ---------------------------------------------------------------------------

interface MoldePedido {
  id: string;
  codigo: string;
  estado: EstadoPedido;
  anuncioId: string;
  modalidade: ModalidadeEntrega;
  /** 'compra' = você comprou; 'venda' = você vendeu. */
  lado: 'compra' | 'venda';
  entregueHaDias?: number;
  codigoConfirmacao?: string;
  estadoEntrega?: EstadoEntrega;
  prazoConfirmacaoAte?: string | null;
  confirmadaPor?: Pedido['confirmadaPor'];
  repassado?: boolean;
}

const moldesDePedido: MoldePedido[] = [
  {
    id: 'ped-aguardando',
    codigo: 'VI-1A2B3C',
    estado: 'AGUARDANDO_PAGAMENTO',
    anuncioId: 'a-bola',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
  },
  {
    id: 'ped-pago',
    codigo: 'VI-4D5E6F',
    estado: 'PAGO',
    anuncioId: 'a-panela',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    estadoEntrega: 'AGUARDANDO_ATRIBUICAO',
    codigoConfirmacao: '4821',
  },
  {
    id: 'ped-agendamento',
    codigo: 'VI-7G8H9J',
    estado: 'AGUARDANDO_AGENDAMENTO_DE_COLETA',
    anuncioId: 'a-ventilador',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    estadoEntrega: 'ATRIBUIDA',
    codigoConfirmacao: '1907',
  },
  {
    id: 'ped-coleta',
    codigo: 'VI-2K3L4M',
    estado: 'A_CAMINHO_DA_COLETA',
    anuncioId: 'a-tenis',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    estadoEntrega: 'A_CAMINHO_DA_COLETA',
    codigoConfirmacao: '3345',
  },
  {
    id: 'ped-coletado',
    codigo: 'VI-5N6P7Q',
    estado: 'PRODUTO_COLETADO',
    anuncioId: 'a-livros',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    estadoEntrega: 'PRODUTO_COLETADO',
    codigoConfirmacao: '8812',
  },
  {
    id: 'ped-rota',
    codigo: 'VI-9XB4TR',
    estado: 'EM_ROTA_PARA_ENTREGA',
    anuncioId: 'a-celular',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    estadoEntrega: 'EM_ROTA_PARA_ENTREGA',
    codigoConfirmacao: '9137',
  },
  {
    id: 'ped-entregue',
    codigo: 'VI-7K3QM2',
    estado: 'ENTREGUE',
    anuncioId: 'a-jaqueta',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    entregueHaDias: 2,
    estadoEntrega: 'ENTREGUE',
    codigoConfirmacao: '4821',
    confirmadaPor: 'codigo',
  },
  {
    id: 'ped-concluido',
    codigo: 'VI-8R9S1T',
    estado: 'CONCLUIDO',
    anuncioId: 'faixa-9999',
    modalidade: 'PLATAFORMA',
    lado: 'venda',
    entregueHaDias: 12,
    estadoEntrega: 'ENTREGUE',
    confirmadaPor: 'codigo',
    repassado: true,
  },
  {
    id: 'ped-concluido-compra',
    codigo: 'VI-9W8V7U',
    estado: 'CONCLUIDO',
    anuncioId: 'a-panela',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    entregueHaDias: 15,
    estadoEntrega: 'ENTREGUE',
    confirmadaPor: 'codigo',
  },
  // ---- modalidade VENDEDOR ----
  {
    id: 'ped-vendedor-aguardando',
    codigo: 'VI-3U4V5W',
    estado: 'AGUARDANDO_ENTREGA_DO_VENDEDOR',
    anuncioId: 'a-sofa',
    modalidade: 'VENDEDOR',
    lado: 'compra',
    codigoConfirmacao: '6274',
  },
  {
    id: 'ped-vendedor-vendendo',
    codigo: 'VI-6X7Y8Z',
    estado: 'AGUARDANDO_ENTREGA_DO_VENDEDOR',
    anuncioId: 'a-mesa',
    modalidade: 'VENDEDOR',
    lado: 'venda',
    codigoConfirmacao: '5130',
  },
  {
    id: 'ped-vendedor-declarada',
    codigo: 'VI-1B2C3D',
    estado: 'ENTREGA_DECLARADA',
    anuncioId: 'a-guardaroupa',
    modalidade: 'VENDEDOR',
    lado: 'compra',
    codigoConfirmacao: '7749',
    prazoConfirmacaoAte: dias(2),
  },
  {
    id: 'ped-vendedor-entregue',
    codigo: 'VI-4E5F6G',
    estado: 'ENTREGUE',
    anuncioId: 'a-violao',
    modalidade: 'VENDEDOR',
    lado: 'venda',
    entregueHaDias: 1,
    confirmadaPor: 'automatica',
  },
  // ---- devoluções ----
  {
    id: 'ped-dev-solicitada',
    codigo: 'VI-7H8I9J',
    estado: 'DEVOLUCAO_SOLICITADA',
    anuncioId: 'a-monitor',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    entregueHaDias: 3,
    estadoEntrega: 'ENTREGUE',
    confirmadaPor: 'codigo',
  },
  {
    id: 'ped-dev-aprovada',
    codigo: 'VI-2K3L4N',
    estado: 'DEVOLUCAO_APROVADA',
    anuncioId: 'faixa-19999',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    entregueHaDias: 4,
    estadoEntrega: 'ENTREGUE',
    confirmadaPor: 'codigo',
  },
  {
    id: 'ped-dev-transito',
    codigo: 'VI-5P6Q7R',
    estado: 'DEVOLUCAO_EM_TRANSITO',
    anuncioId: 'faixa-2500',
    modalidade: 'PLATAFORMA',
    lado: 'venda',
    entregueHaDias: 5,
    estadoEntrega: 'ENTREGUE',
    confirmadaPor: 'codigo',
  },
  {
    id: 'ped-dev-combinada',
    codigo: 'VI-8S9T1U',
    estado: 'DEVOLUCAO_COMBINADA',
    anuncioId: 'a-geladeira',
    modalidade: 'VENDEDOR',
    lado: 'compra',
    entregueHaDias: 2,
    confirmadaPor: 'comprador',
  },
  {
    id: 'ped-reembolsado',
    codigo: 'VI-3V4W5X',
    estado: 'REEMBOLSADO',
    anuncioId: 'faixa-50000',
    modalidade: 'PLATAFORMA',
    lado: 'compra',
    entregueHaDias: 9,
    estadoEntrega: 'ENTREGUE',
    confirmadaPor: 'codigo',
  },
];

function montarPedido(m: MoldePedido, anuncios: Anuncio[]): Pedido {
  const a = anuncios.find((x) => x.id === m.anuncioId)!;
  const d = calcularDescontos(a.preco, m.modalidade);
  const entregueEm = m.entregueHaDias == null ? null : dias(-m.entregueHaDias);
  const prazoTesteAte = m.entregueHaDias == null ? null : dias(7 - m.entregueHaDias);
  const restantes = m.entregueHaDias == null ? null : Math.max(0, 7 - m.entregueHaDias);

  return {
    id: m.id,
    codigo: m.codigo,
    estado: m.estado,
    modalidade: m.modalidade,
    valorProduto: a.preco,
    valorTotal: a.preco,
    valorComissao: d.comissao,
    valorTarifa: d.tarifa,
    taxaComissao: 0.12,
    valorVendedor: d.vendedor,
    criadoEm: dias(-((m.entregueHaDias ?? 1) + 1)),
    pagoEm: m.estado === 'AGUARDANDO_PAGAMENTO' ? null : dias(-((m.entregueHaDias ?? 1) + 1)),
    entregueEm,
    prazoTesteAte,
    codigoConfirmacao: m.modalidade === 'VENDEDOR' ? (m.codigoConfirmacao ?? null) : null,
    prazoConfirmacaoAte: m.prazoConfirmacaoAte ?? null,
    confirmadaPor: m.confirmadaPor ?? null,
    anuncio: { titulo: a.titulo, fotos: a.fotos },
    vendedor:
      m.lado === 'compra'
        ? { nome: a.vendedor.nome, apelidoLoja: a.vendedor.apelidoLoja }
        : { nome: usuarioDemo.nome, apelidoLoja: usuarioDemo.apelidoLoja },
    comprador: m.lado === 'compra' ? { nome: usuarioDemo.nome } : { nome: 'Ana Paula' },
    entrega:
      m.modalidade === 'PLATAFORMA' && m.estadoEntrega
        ? {
            id: `ent-${m.id}`,
            estado: m.estadoEntrega,
            codigoConfirmacao: m.codigoConfirmacao ?? null,
            entregador:
              m.estadoEntrega === 'AGUARDANDO_ATRIBUICAO'
                ? null
                : { nome: 'João', telefone: '(33) 98877-1234' },
          }
        : null,
    entregaDevolucao:
      m.estado === 'DEVOLUCAO_EM_TRANSITO'
        ? { id: `dev-${m.id}`, estado: 'PRODUTO_COLETADO' }
        : null,
    reembolso:
      m.estado === 'REEMBOLSADO'
        ? { estado: 'CONCLUIDO', valorReembolsado: a.preco }
        : m.estado.startsWith('DEVOLUCAO')
          ? { estado: 'APROVADO', valorReembolsado: a.preco }
          : null,
    podePedirReembolso:
      (m.estado === 'ENTREGUE' || m.estado === 'ENTREGA_DECLARADA') &&
      (restantes ?? 0) > 0 &&
      m.lado === 'compra',
    // avaliação só depois que o prazo de teste passou e o pedido fechou
    podeAvaliar: m.estado === 'CONCLUIDO' && m.lado === 'compra',
    diasRestantesParaTestar: restantes,
    eventos: [
      { id: `${m.id}-1`, tipo: 'PEDIDO_CRIADO', criadoEm: dias(-((m.entregueHaDias ?? 1) + 1)) },
      ...(m.estado === 'AGUARDANDO_PAGAMENTO'
        ? []
        : [{ id: `${m.id}-2`, tipo: 'PAGAMENTO_CONFIRMADO', criadoEm: dias(-((m.entregueHaDias ?? 1) + 1)) }]),
      ...(entregueEm ? [{ id: `${m.id}-3`, tipo: 'ENTREGUE', criadoEm: entregueEm }] : []),
    ],
    ...(m.repassado ? { repassadoEm: dias(-2) } : {}),
  } as Pedido;
}

export function pedidosSemente(anuncios: Anuncio[]): Array<Pedido & { lado: 'compra' | 'venda' }> {
  return moldesDePedido.map((m) => ({
    ...montarPedido(m, anuncios),
    lado: m.lado,
  }));
}

// ---------------------------------------------------------------------------
// Corridas do entregador
// ---------------------------------------------------------------------------

export function corridasSemente(anuncios: Anuncio[]): Corrida[] {
  const buscar = (id: string) => anuncios.find((a) => a.id === id)!;

  const base = (
    id: string,
    estado: EstadoEntrega,
    anuncioId: string,
    codigoPedido: string,
    extras: Partial<Corrida> = {},
  ): Corrida => {
    const a = buscar(anuncioId);
    const aceita = !['AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'RECUSADA'].includes(estado);
    return {
      id,
      tipo: 'ENTREGA',
      estado,
      valorEntregador: 500,
      coletaEndereco: 'Rua das Flores, 120, Centro, Itinga/MG',
      coletaReferencia: 'Portão azul, ao lado da padaria',
      coletaContato: a.vendedor.nome,
      coletaTelefone: aceita ? '(33) 98877-2211' : '(33) ••••-2211',
      entregaEndereco: 'Av. Principal, 45, Bairro Novo, Itinga/MG',
      entregaReferencia: 'Casa de esquina, muro verde',
      entregaContato: 'Ana Paula',
      entregaTelefone: aceita ? '(33) 98866-3344' : '(33) ••••-3344',
      telefoneLiberado: aceita,
      criadoEm: horas(-6),
      entregador: aceita ? { id: 'e-joao', nome: 'João' } : null,
      pedido: {
        codigo: codigoPedido,
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
      ...extras,
    };
  };

  return [
    base('cor-1', 'ATRIBUIDA', 'a-ventilador', 'VI-7G8H9J'),
    base('cor-2', 'AGUARDANDO_ATRIBUICAO', 'a-panela', 'VI-4D5E6F', { entregador: null }),
    base('cor-3', 'A_CAMINHO_DA_COLETA', 'a-tenis', 'VI-2K3L4M'),
    base('cor-4', 'PRODUTO_COLETADO', 'a-livros', 'VI-5N6P7Q', {
      volumes: 2,
      fotoPacoteUrl: 'demo://foto-do-pacote',
    }),
    base('cor-5', 'EM_ROTA_PARA_ENTREGA', 'a-celular', 'VI-9XB4TR', {
      volumes: 1,
      fotoPacoteUrl: 'demo://foto-do-pacote',
    }),
    base('cor-6', 'CHEGOU_NA_ENTREGA', 'faixa-9999', 'VI-8R9S1T', { volumes: 1 }),
    base('cor-7', 'ENTREGUE', 'a-jaqueta', 'VI-7K3QM2', { criadoEm: dias(-2), volumes: 1 }),
    base('cor-8', 'ENTREGUE', 'a-bola', 'VI-1A2B3C', { criadoEm: dias(-4), volumes: 1 }),
    {
      ...base('cor-9', 'ATRIBUIDA', 'faixa-19999', 'VI-2K3L4N'),
      tipo: 'DEVOLUCAO',
      observacoes: 'DEVOLUÇÃO: buscar no comprador e devolver ao vendedor.',
      coletaEndereco: 'Av. Principal, 45, Bairro Novo, Itinga/MG',
      coletaContato: 'Ana Paula',
      entregaEndereco: 'Rua das Flores, 120, Centro, Itinga/MG',
      entregaContato: 'Rubia',
    },
    {
      ...base('cor-10', 'PRODUTO_COLETADO', 'faixa-2500', 'VI-5P6Q7R'),
      tipo: 'DEVOLUCAO',
      observacoes: 'DEVOLUÇÃO: buscar no comprador e devolver ao vendedor.',
      volumes: 1,
    },
  ];
}

// ---------------------------------------------------------------------------
// Devoluções na fila do admin
// ---------------------------------------------------------------------------

export function devolucoesSemente(anuncios: Anuncio[]): SolicitacaoDeDevolucao[] {
  const de = (id: string) => anuncios.find((a) => a.id === id)!;
  return [
    {
      id: 'dev-1',
      estado: 'SOLICITADO',
      motivo: 'chegou com defeito',
      descricao: 'A tela fica piscando depois de uns minutos ligada.',
      valorReembolsado: de('a-monitor').preco,
      solicitadoEm: horas(-5),
      pedido: {
        id: 'ped-dev-solicitada',
        codigo: 'VI-7H8I9J',
        valorTotal: de('a-monitor').preco,
        modalidade: 'PLATAFORMA',
        comprador: { nome: 'Marcos', telefone: '(33) 98888-0000' },
        vendedor: { nome: 'Rondson', telefone: '(33) 98877-2211' },
        anuncio: { titulo: de('a-monitor').titulo },
      },
    },
    {
      id: 'dev-2',
      estado: 'APROVADO',
      motivo: 'não é o que estava no anúncio',
      descricao: 'O anúncio dizia 6 portas e vieram 4.',
      valorReembolsado: de('a-geladeira').preco,
      solicitadoEm: dias(-1),
      pedido: {
        id: 'ped-dev-combinada',
        codigo: 'VI-8S9T1U',
        valorTotal: de('a-geladeira').preco,
        modalidade: 'VENDEDOR',
        comprador: { nome: 'Marcos', telefone: '(33) 98888-0000' },
        vendedor: { nome: 'Rondson', telefone: '(33) 98877-2211' },
        anuncio: { titulo: de('a-geladeira').titulo },
      },
    },
    {
      id: 'dev-3',
      estado: 'SOLICITADO',
      motivo: 'mudei de ideia',
      descricao: null,
      valorReembolsado: de('faixa-19999').preco,
      solicitadoEm: horas(-30),
      pedido: {
        id: 'ped-dev-aprovada',
        codigo: 'VI-2K3L4N',
        valorTotal: de('faixa-19999').preco,
        modalidade: 'PLATAFORMA',
        comprador: { nome: 'Marcos', telefone: '(33) 98888-0000' },
        vendedor: { nome: 'Rubia', telefone: '(33) 98855-7788' },
        anuncio: { titulo: de('faixa-19999').titulo },
      },
    },
  ];
}

// ---------------------------------------------------------------------------
// Cadastros auxiliares
// ---------------------------------------------------------------------------

export interface EnderecoDemo {
  id: string;
  apelido: string | null;
  cep: string;
  logradouro: string;
  numero: string;
  complemento: string | null;
  bairro: string;
  referencia: string | null;
  principal: boolean;
}

export const enderecosSemente: EnderecoDemo[] = [
  {
    id: 'end-1',
    apelido: 'casa',
    cep: '39610-000',
    logradouro: 'Rua das Flores',
    numero: '120',
    complemento: null,
    bairro: 'Centro',
    referencia: 'Portão azul, ao lado da padaria',
    principal: true,
  },
  {
    id: 'end-2',
    apelido: 'trabalho',
    cep: '39610-000',
    logradouro: 'Av. Principal',
    numero: '45',
    complemento: 'Sala 2',
    bairro: 'Bairro Novo',
    referencia: null,
    principal: false,
  },
];

export const conversasSemente = [
  {
    id: 'conv-1',
    texto: 'Boa tarde! A bicicleta ainda está disponível?',
    criadoEm: horas(-2),
    souOAutor: false,
    naoLida: true,
    autor: { nome: 'Ana Paula', fotoUrl: null },
    pedido: null,
    anuncioId: 'faixa-49999',
    mensagens: [
      { id: 'm1', texto: 'Boa tarde! A bicicleta ainda está disponível?', autorId: 'v-outro', criadoEm: horas(-2), autor: { id: 'v-outro', nome: 'Ana Paula' } },
      { id: 'm2', texto: 'Boa tarde! Está sim, pode vir ver.', autorId: 'u-voce', criadoEm: horas(-1.5), autor: { id: 'u-voce', nome: 'Marcos' } },
      { id: 'm3', texto: 'Aceita R$ 480?', autorId: 'v-outro', criadoEm: horas(-1), autor: { id: 'v-outro', nome: 'Ana Paula' } },
    ],
  },
  {
    id: 'conv-2',
    texto: 'Combinado, te espero às 18h no centro.',
    criadoEm: horas(-20),
    souOAutor: true,
    naoLida: false,
    autor: { nome: 'Rondson', fotoUrl: null },
    pedido: { id: 'ped-vendedor-vendendo', codigo: 'VI-6X7Y8Z', anuncio: { titulo: 'mesa de jantar 6 lugares' } },
    anuncioId: 'a-mesa',
    mensagens: [
      { id: 'm4', texto: 'Oi! Que horas posso buscar a mesa?', autorId: 'v-rondson', criadoEm: horas(-22), autor: { id: 'v-rondson', nome: 'Rondson' } },
      { id: 'm5', texto: 'Combinado, te espero às 18h no centro.', autorId: 'u-voce', criadoEm: horas(-20), autor: { id: 'u-voce', nome: 'Marcos' } },
    ],
  },
];

// ---------------------------------------------------------------------------
// Curtidas, seguidores, ofertas e avaliações
// ---------------------------------------------------------------------------

/** O que eu curti, e quantas curtidas cada anúncio tem no total. */
export const curtidasSemente = {
  minhas: ['a-celular', 'faixa-49999', 'a-jaqueta'],
  porAnuncio: {
    'a-celular': 14,
    'faixa-49999': 9,
    'a-jaqueta': 6,
    'a-monitor': 11,
    'a-tenis': 4,
    'a-violao': 7,
    'a-sofa': 3,
    'faixa-9999': 22,
    'a-carrinho': 5,
    'a-geladeira': 2,
  } as Record<string, number>,
};

/** Quem eu sigo, e quantos seguidores cada lojinha tem. */
export const seguidoresSemente = {
  sigo: ['v-rubia'],
  porVendedor: {
    'v-rubia': 38,
    'v-rondson': 21,
    'v-cleide': 15,
    'u-voce': 7,
  } as Record<string, number>,
};

/**
 * Negociações em andamento, uma em cada estado que a tela precisa mostrar.
 * Os dois lados aparecem: umas em que eu comprei, outras em que eu vendo.
 */
export function ofertasSemente(anuncios: Anuncio[]): Oferta[] {
  const de = (id: string) => anuncios.find((a) => a.id === id)!;

  const negociacao = (
    id: string,
    anuncioId: string,
    estado: Oferta['estado'],
    valorAtual: number,
    ultimoLancePor: Oferta['ultimoLancePor'],
    lado: 'compra' | 'venda',
    horasAtras: number,
    recado?: string,
  ): Oferta => {
    const a = de(anuncioId);
    const lanceEm = horas(-horasAtras);
    const euSouOComprador = lado === 'compra';
    return {
      id,
      estado,
      precoAnunciado: a.preco,
      valorAtual,
      ultimoLancePor,
      recado: recado ?? null,
      lanceEm,
      prazoAte: new Date(new Date(lanceEm).getTime() + 3 * 86_400_000).toISOString(),
      criadoEm: horas(-horasAtras - 2),
      meuPapel: euSouOComprador ? 'COMPRADOR' : 'VENDEDOR',
      minhaVez:
        (estado === 'ABERTA' && !euSouOComprador) ||
        (estado === 'CONTRAPROPOSTA' && euSouOComprador),
      anuncio: { id: a.id, titulo: a.titulo, preco: a.preco, fotos: a.fotos },
      comprador: euSouOComprador
        ? { id: usuarioDemo.id, nome: usuarioDemo.nome, fotoUrl: null }
        : { id: 'v-ana', nome: 'Ana Paula', fotoUrl: null },
      vendedor: euSouOComprador
        ? { id: a.vendedor.id, nome: a.vendedor.nome, apelidoLoja: a.vendedor.apelidoLoja }
        : { id: usuarioDemo.id, nome: usuarioDemo.nome, apelidoLoja: usuarioDemo.apelidoLoja },
      lances: [
        {
          id: `${id}-l1`,
          por: 'COMPRADOR',
          valor: estado === 'CONTRAPROPOSTA' ? Math.round(valorAtual * 0.85) : valorAtual,
          recado: recado ?? null,
          criadoEm: horas(-horasAtras - 2),
        },
        ...(estado === 'CONTRAPROPOSTA'
          ? [
              {
                id: `${id}-l2`,
                por: 'VENDEDOR' as const,
                valor: valorAtual,
                recado: 'consigo fazer por esse valor, fechamos?',
                criadoEm: lanceEm,
              },
            ]
          : []),
      ],
    };
  };

  return [
    // eu vendo o violão e recebi uma proposta — é a minha vez de responder
    negociacao('of-1', 'a-violao', 'ABERTA', 21_000, 'COMPRADOR', 'venda', 5,
      'levo hoje se você fizer por esse valor'),
    // eu ofereci na bicicleta e o vendedor devolveu com outro valor
    negociacao('of-2', 'faixa-49999', 'CONTRAPROPOSTA', 46_000, 'VENDEDOR', 'compra', 20),
    // minha oferta no celular, esperando o vendedor
    negociacao('of-3', 'a-celular', 'ABERTA', 68_000, 'COMPRADOR', 'compra', 30,
      'tenho interesse, aceita?'),
    // fechada: virou o preço que vou pagar
    negociacao('of-4', 'a-monitor', 'ACEITA', 37_000, 'VENDEDOR', 'compra', 48),
    // recusada
    negociacao('of-5', 'a-geladeira', 'RECUSADA', 80_000, 'COMPRADOR', 'compra', 72),
  ];
}

/** Avaliações que a lojinha já recebeu. */
export function avaliacoesSemente(): Avaliacao[] {
  return [
    {
      id: 'av-1',
      nota: 5,
      comentario: 'produto igualzinho ao anúncio, entrega rápida. recomendo!',
      criadoEm: dias(-4),
      autor: { nome: 'Ana Paula', fotoUrl: null },
      pedido: { codigo: 'VI-8R9S1T', anuncio: { titulo: 'fone bluetooth' } },
    },
    {
      id: 'av-2',
      nota: 5,
      comentario: 'atendeu no chat na hora e combinou a entrega direitinho.',
      criadoEm: dias(-11),
      autor: { nome: 'Rondson', fotoUrl: null },
      pedido: { codigo: 'VI-4E5F6G', anuncio: { titulo: 'violão nylon com capa' } },
    },
    {
      id: 'av-3',
      nota: 4,
      comentario: 'tudo certo, só demorou um pouco pra separar o produto.',
      criadoEm: dias(-19),
      autor: { nome: 'Cleide', fotoUrl: null },
      pedido: { codigo: 'VI-2K3L4M', anuncio: { titulo: 'tênis nike tam 40' } },
    },
  ];
}
