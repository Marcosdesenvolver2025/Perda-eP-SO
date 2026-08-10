/** Tipos compartilhados entre o app e a API. Dinheiro em centavos. */

export type Papel = 'CLIENTE' | 'ENTREGADOR' | 'ADMIN';

export type CondicaoProduto = 'NOVO' | 'SEMINOVO' | 'USADO';

export type ModalidadeEntrega = 'ENTREGADOR_PROPRIO' | 'COMBINADO_ENTRE_PARTES';

export type EstadoPedido =
  | 'AGUARDANDO_PAGAMENTO'
  | 'PAGO'
  | 'EM_SEPARACAO'
  | 'A_CAMINHO'
  | 'ENTREGUE'
  | 'CONCLUIDO'
  | 'EM_DEVOLUCAO'
  | 'REEMBOLSADO'
  | 'CANCELADO';

export type EstadoEntrega =
  | 'AGUARDANDO_ENTREGADOR'
  | 'ACEITA'
  | 'COLETADA'
  | 'ENTREGUE'
  | 'DEVOLVENDO'
  | 'DEVOLVIDA'
  | 'CANCELADA';

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
    valorFrete: number | null;
    diasParaTestar: number;
  };
}

export interface ResumoDaCompra {
  valorProduto: number;
  valorFrete: number;
  valorTotal: number;
  taxaComissao: number;
  valorComissao: number;
  valorVendedor: number;
  modalidade: ModalidadeEntrega;
}

export interface Pedido {
  id: string;
  codigo: string;
  estado: EstadoPedido;
  modalidade: ModalidadeEntrega;
  valorProduto: number;
  valorFrete: number;
  valorTotal: number;
  valorComissao: number;
  taxaComissao: number;
  valorVendedor: number;
  criadoEm: string;
  pagoEm?: string | null;
  entregueEm?: string | null;
  prazoTesteAte?: string | null;
  anuncio: { titulo: string; fotos: Foto[] };
  vendedor?: { nome: string; apelidoLoja?: string | null };
  comprador?: { nome: string };
  entrega?: { estado: EstadoEntrega; codigoConfirmacao?: string | null } | null;
  reembolso?: { estado: string; valorReembolsado: number } | null;
  podePedirReembolso?: boolean;
  diasRestantesParaTestar?: number | null;
  eventos?: Array<{ id: string; tipo: string; criadoEm: string }>;
}

export interface Entrega {
  id: string;
  estado: EstadoEntrega;
  valorEntregador: number;
  coletaEndereco: string;
  entregaEndereco: string;
  observacoes?: string | null;
  codigoConfirmacao?: string | null;
  criadoEm: string;
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
    comprador?: { nome: string; telefone?: string | null };
    vendedor?: { nome: string; telefone?: string | null };
  };
}

export interface Configuracoes {
  cidade: string;
  uf: string;
  comissaoSemEntregador: number;
  comissaoComEntregador: number;
  diasParaTestar: number;
  pesoMaximoG: number;
  dimensaoMaximaCm: number;
}
