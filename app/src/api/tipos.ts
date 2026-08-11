/** Tipos compartilhados entre o app e a API. Dinheiro em centavos. */

export type Papel = 'CLIENTE' | 'ENTREGADOR' | 'ADMIN';

export type CondicaoProduto = 'NOVO' | 'SEMINOVO' | 'USADO';

export type ModalidadeEntrega = 'ENTREGADOR_PROPRIO' | 'COMBINADO_ENTRE_PARTES';

export type EstadoPedido =
  | 'AGUARDANDO_PAGAMENTO'
  | 'PAGO'
  | 'AGUARDANDO_AGENDAMENTO_DE_COLETA'
  | 'A_CAMINHO_DA_COLETA'
  | 'PRODUTO_COLETADO'
  | 'EM_ROTA_PARA_ENTREGA'
  | 'ENTREGUE'
  | 'CONCLUIDO'
  | 'DEVOLUCAO_SOLICITADA'
  | 'DEVOLUCAO_APROVADA'
  | 'DEVOLUCAO_EM_TRANSITO'
  | 'DEVOLVIDO_AO_VENDEDOR'
  | 'REEMBOLSADO'
  | 'CANCELADO';

export type EstadoEntrega =
  | 'AGUARDANDO_ATRIBUICAO'
  | 'ATRIBUIDA'
  | 'RECUSADA'
  | 'ACEITA'
  | 'A_CAMINHO_DA_COLETA'
  | 'CHEGOU_NA_COLETA'
  | 'PRODUTO_COLETADO'
  | 'EM_ROTA_PARA_ENTREGA'
  | 'CHEGOU_NA_ENTREGA'
  | 'ENTREGUE'
  | 'CANCELADA';

export type TipoEntrega = 'ENTREGA' | 'DEVOLUCAO';

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  fotoUrl?: string | null;
  telefone?: string | null;
  papel: Papel;
  apelidoLoja?: string | null;
  bioLoja?: string | null;
  bairro?: string | null;
  cidade?: string;
  recebedor?: 'PENDENTE' | 'ATIVO' | 'RECUSADO' | 'BLOQUEADO' | null;
  precisaCompletarCadastro?: boolean;
}

export interface Foto {
  id: string;
  url: string;
  ordem: number;
}

export interface Anuncio {
  id: string;
  titulo: string;
  descricao: string;
  preco: number;
  precoOriginal?: number | null;
  condicao: CondicaoProduto;
  marca?: string | null;
  tamanho?: string | null;
  cor?: string | null;
  pesoG: number;
  comprimentoCm: number;
  larguraCm: number;
  alturaCm: number;
  aceitaEntregador: boolean;
  aceitaCombinado: boolean;
  fotos: Foto[];
  criadoEm: string;
  vendedor: {
    id: string;
    nome: string;
    apelidoLoja?: string | null;
    fotoUrl?: string | null;
    bairro?: string | null;
    notaMedia?: number | null;
    totalAvaliacoes?: number;
  };
  categoria?: { slug: string; nome: string } | null;
  entrega?: {
    diasParaTestar: number;
    entregaInclusa: boolean;
  };
  /** O que a plataforma desconta desta venda. */
  taxas?: { comissao: number; tarifa: number };
}

export interface ResumoDaCompra {
  valorProduto: number;
  valorTotal: number;
  taxaComissao: number;
  valorComissao: number;
  valorTarifa: number;
  valorVendedor: number;
  modalidade: ModalidadeEntrega;
}

export interface Pedido {
  id: string;
  codigo: string;
  estado: EstadoPedido;
  modalidade: ModalidadeEntrega;
  valorProduto: number;
  valorTotal: number;
  valorComissao: number;
  valorTarifa: number;
  taxaComissao: number;
  valorVendedor: number;
  criadoEm: string;
  pagoEm?: string | null;
  entregueEm?: string | null;
  prazoTesteAte?: string | null;
  anuncio: { titulo: string; fotos: Foto[] };
  vendedor?: { nome: string; apelidoLoja?: string | null };
  comprador?: { nome: string };
  entrega?: {
    id: string;
    estado: EstadoEntrega;
    codigoConfirmacao?: string | null;
    entregador?: { nome: string; telefone?: string | null } | null;
  } | null;
  entregaDevolucao?: { id: string; estado: EstadoEntrega } | null;
  reembolso?: { estado: string; valorReembolsado: number } | null;
  podePedirReembolso?: boolean;
  diasRestantesParaTestar?: number | null;
  eventos?: Array<{ id: string; tipo: string; criadoEm: string }>;
}

/** Uma corrida do entregador: ida (ENTREGA) ou volta (DEVOLUCAO). */
export interface Corrida {
  id: string;
  tipo: TipoEntrega;
  estado: EstadoEntrega;
  valorEntregador: number;
  coletaEndereco: string;
  coletaReferencia?: string | null;
  coletaContato?: string | null;
  coletaTelefone?: string | null;
  entregaEndereco: string;
  entregaReferencia?: string | null;
  entregaContato?: string | null;
  entregaTelefone?: string | null;
  observacoes?: string | null;
  volumes?: number | null;
  fotoPacoteUrl?: string | null;
  /** false enquanto o entregador não aceitou: os telefones vêm mascarados. */
  telefoneLiberado?: boolean;
  criadoEm: string;
  proximosPassos?: EstadoEntrega[];
  entregador?: { id: string; nome: string } | null;
  recusas?: Array<{ motivo: string; criadoEm: string; entregador: { nome: string } }>;
  pedido: {
    codigo: string;
    valorProduto?: number;
    anuncio: {
      titulo: string;
      pesoG?: number;
      comprimentoCm?: number;
      larguraCm?: number;
      alturaCm?: number;
      fotos?: Foto[];
    };
  };
}

/** Entregador candidato a uma corrida, na tela de escolha do admin. */
export interface Candidato {
  id: string;
  nome: string;
  disponivel: boolean;
  corridasAtivas: number;
  capacidade: number;
}

export interface ResumoAdmin {
  anunciosAtivos: number;
  pedidosEmAndamento: number;
  aguardandoAtribuicao: number;
  corridasEmRota: number;
  devolucoesAbertas: number;
  comissaoAcumulada: number;
  tarifasAcumuladas: number;
  custoComEntregas: number;
  receitaLiquida: number;
}

export interface SolicitacaoDeDevolucao {
  id: string;
  estado: 'SOLICITADO' | 'EM_ANALISE' | 'APROVADO' | 'RECUSADO' | 'CONCLUIDO';
  motivo: string;
  descricao?: string | null;
  valorReembolsado: number;
  solicitadoEm: string;
  pedido: {
    id: string;
    codigo: string;
    valorTotal: number;
    comprador: { nome: string; telefone?: string | null };
    vendedor: { nome: string; telefone?: string | null };
    anuncio: { titulo: string };
  };
}

export interface Configuracoes {
  cidade: string;
  uf: string;
  comissao: number;
  valorMinimoVenda: number;
  /** `ateInclusive: null` é a última faixa, aberta. */
  tarifas: Array<{ ateInclusive: number | null; tarifa: number }>;
  diasParaTestar: number;
  pesoMaximoG: number;
  dimensaoMaximaCm: number;
}
