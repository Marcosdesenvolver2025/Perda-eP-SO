/**
 * Máquina de estados da entrega.
 *
 * Esta camada é PURA: não conhece banco, HTTP nem pagar.me. Ela só responde
 * "essa transição pode?" e "qual estado o pedido assume?". Assim dá para
 * testar todo o fluxo logístico sem subir infraestrutura, e o serviço que
 * grava no banco fica burro de propósito.
 *
 * IMPORTANTE: nada aqui mexe em dinheiro. A logística só empurra o pedido até
 * ENTREGUE (ou DEVOLVIDO_AO_VENDEDOR); quem cuida de cobrança, split, repasse
 * e estorno continua sendo o financeiro já existente.
 */

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

/** Quem tem o direito de disparar cada transição. */
export type Ator = 'ADMIN' | 'ENTREGADOR' | 'SISTEMA';

export interface Transicao {
  de: EstadoEntrega;
  para: EstadoEntrega;
  /** Quem pode disparar. */
  atores: readonly Ator[];
  /** O que precisa vir junto para a transição valer. */
  exige?: readonly ('fotoPacote' | 'volumes' | 'codigoConfirmacao' | 'motivo' | 'entregador')[];
}

/**
 * O fluxo completo, na ordem do combinado:
 *
 *   AGUARDANDO_ATRIBUICAO
 *     -> ATRIBUIDA            (admin escolhe o entregador)
 *     -> ACEITA | RECUSADA    (entregador responde)
 *   RECUSADA -> AGUARDANDO_ATRIBUICAO   (volta para a fila)
 *   ACEITA
 *     -> A_CAMINHO_DA_COLETA
 *     -> CHEGOU_NA_COLETA
 *     -> PRODUTO_COLETADO     (exige foto do pacote e nº de volumes)
 *     -> EM_ROTA_PARA_ENTREGA
 *     -> CHEGOU_NA_ENTREGA
 *     -> ENTREGUE             (exige código de confirmação)
 */
export const TRANSICOES: readonly Transicao[] = [
  {
    de: 'AGUARDANDO_ATRIBUICAO',
    para: 'ATRIBUIDA',
    atores: ['ADMIN', 'SISTEMA'],
    exige: ['entregador'],
  },
  { de: 'ATRIBUIDA', para: 'ACEITA', atores: ['ENTREGADOR'] },
  { de: 'ATRIBUIDA', para: 'RECUSADA', atores: ['ENTREGADOR'], exige: ['motivo'] },
  // recusou: volta para a fila do administrador
  { de: 'RECUSADA', para: 'AGUARDANDO_ATRIBUICAO', atores: ['SISTEMA', 'ADMIN'] },
  // admin pode retirar a corrida de um entregador que travou
  { de: 'ATRIBUIDA', para: 'AGUARDANDO_ATRIBUICAO', atores: ['ADMIN'] },
  { de: 'ACEITA', para: 'AGUARDANDO_ATRIBUICAO', atores: ['ADMIN'] },

  { de: 'ACEITA', para: 'A_CAMINHO_DA_COLETA', atores: ['ENTREGADOR'] },
  { de: 'A_CAMINHO_DA_COLETA', para: 'CHEGOU_NA_COLETA', atores: ['ENTREGADOR'] },
  {
    de: 'CHEGOU_NA_COLETA',
    para: 'PRODUTO_COLETADO',
    atores: ['ENTREGADOR'],
    exige: ['fotoPacote', 'volumes'],
  },
  { de: 'PRODUTO_COLETADO', para: 'EM_ROTA_PARA_ENTREGA', atores: ['ENTREGADOR'] },
  { de: 'EM_ROTA_PARA_ENTREGA', para: 'CHEGOU_NA_ENTREGA', atores: ['ENTREGADOR'] },
  {
    de: 'CHEGOU_NA_ENTREGA',
    para: 'ENTREGUE',
    atores: ['ENTREGADOR'],
    exige: ['codigoConfirmacao'],
  },

  // cancelamento pelo admin, possível enquanto o produto não foi entregue
  ...(
    [
      'AGUARDANDO_ATRIBUICAO',
      'ATRIBUIDA',
      'RECUSADA',
      'ACEITA',
      'A_CAMINHO_DA_COLETA',
      'CHEGOU_NA_COLETA',
      'PRODUTO_COLETADO',
      'EM_ROTA_PARA_ENTREGA',
      'CHEGOU_NA_ENTREGA',
    ] as const
  ).map((de) => ({ de, para: 'CANCELADA' as const, atores: ['ADMIN'] as const })),
] as const;

/** Estados a partir dos quais nada mais acontece. */
export const ESTADOS_FINAIS: readonly EstadoEntrega[] = ['ENTREGUE', 'CANCELADA'];

export interface DadosDaTransicao {
  fotoPacote?: string | null;
  volumes?: number | null;
  codigoConfirmacao?: string | null;
  motivo?: string | null;
  entregador?: string | null;
}

export interface ResultadoValidacao {
  permitida: boolean;
  motivo?: string;
}

/**
 * Diz se a transição pode acontecer.
 *
 * Erra para o lado seguro: transição desconhecida é recusada. Isso impede que
 * um app desatualizado pule etapas — por exemplo, marcar ENTREGUE sem ter
 * passado pela coleta.
 */
export function podeTransitar(
  de: EstadoEntrega,
  para: EstadoEntrega,
  ator: Ator,
  dados: DadosDaTransicao = {},
): ResultadoValidacao {
  if (ESTADOS_FINAIS.includes(de)) {
    return { permitida: false, motivo: `A entrega já está ${rotulo(de)} e não muda mais.` };
  }

  const candidatas = TRANSICOES.filter((t) => t.de === de && t.para === para);
  if (candidatas.length === 0) {
    return {
      permitida: false,
      motivo: `Não dá para ir de ${rotulo(de)} para ${rotulo(para)}.`,
    };
  }

  const permitidaParaOAtor = candidatas.find((t) => t.atores.includes(ator));
  if (!permitidaParaOAtor) {
    return { permitida: false, motivo: 'Você não pode fazer essa mudança.' };
  }

  for (const exigencia of permitidaParaOAtor.exige ?? []) {
    switch (exigencia) {
      case 'fotoPacote':
        if (!dados.fotoPacote) {
          return { permitida: false, motivo: 'Tire uma foto do pacote antes de concluir a coleta.' };
        }
        break;
      case 'volumes':
        if (!dados.volumes || dados.volumes < 1) {
          return { permitida: false, motivo: 'Informe quantos volumes você está levando.' };
        }
        break;
      case 'codigoConfirmacao':
        if (!dados.codigoConfirmacao) {
          return { permitida: false, motivo: 'Peça o código de confirmação ao comprador.' };
        }
        break;
      case 'motivo':
        if (!dados.motivo || dados.motivo.trim().length < 3) {
          return { permitida: false, motivo: 'Diga o motivo da recusa.' };
        }
        break;
      case 'entregador':
        if (!dados.entregador) {
          return { permitida: false, motivo: 'Escolha um entregador.' };
        }
        break;
    }
  }

  return { permitida: true };
}

/** Próximos passos possíveis para um ator — o app usa para montar os botões. */
export function proximosPassos(de: EstadoEntrega, ator: Ator): EstadoEntrega[] {
  if (ESTADOS_FINAIS.includes(de)) return [];
  return TRANSICOES.filter((t) => t.de === de && t.atores.includes(ator)).map((t) => t.para);
}

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

/**
 * Reflexo da entrega no pedido.
 *
 * Nem toda transição logística mexe no pedido — "cheguei na coleta" é
 * informação do entregador, não muda o que o comprador vê. `null` significa
 * "deixa o pedido como está".
 *
 * Repare que ENTREGUE só aparece na corrida de ida. Na volta (devolução), o
 * pedido vai para DEVOLVIDO_AO_VENDEDOR, que é o gatilho do estorno.
 */
export function estadoDoPedidoPara(
  estadoEntrega: EstadoEntrega,
  tipo: TipoEntrega,
): EstadoPedido | null {
  if (tipo === 'DEVOLUCAO') {
    switch (estadoEntrega) {
      case 'PRODUTO_COLETADO':
      case 'EM_ROTA_PARA_ENTREGA':
        return 'DEVOLUCAO_EM_TRANSITO';
      case 'ENTREGUE':
        return 'DEVOLVIDO_AO_VENDEDOR';
      default:
        return null;
    }
  }

  switch (estadoEntrega) {
    case 'AGUARDANDO_ATRIBUICAO':
    case 'ATRIBUIDA':
    case 'RECUSADA':
    case 'ACEITA':
      return 'AGUARDANDO_AGENDAMENTO_DE_COLETA';
    case 'A_CAMINHO_DA_COLETA':
    case 'CHEGOU_NA_COLETA':
      return 'A_CAMINHO_DA_COLETA';
    case 'PRODUTO_COLETADO':
      return 'PRODUTO_COLETADO';
    case 'EM_ROTA_PARA_ENTREGA':
    case 'CHEGOU_NA_ENTREGA':
      return 'EM_ROTA_PARA_ENTREGA';
    case 'ENTREGUE':
      return 'ENTREGUE';
    default:
      return null;
  }
}

const ROTULOS: Record<EstadoEntrega, string> = {
  AGUARDANDO_ATRIBUICAO: 'aguardando entregador',
  ATRIBUIDA: 'atribuída',
  RECUSADA: 'recusada',
  ACEITA: 'aceita',
  A_CAMINHO_DA_COLETA: 'a caminho da coleta',
  CHEGOU_NA_COLETA: 'no local da coleta',
  PRODUTO_COLETADO: 'produto coletado',
  EM_ROTA_PARA_ENTREGA: 'em rota para entrega',
  CHEGOU_NA_ENTREGA: 'no local da entrega',
  ENTREGUE: 'entregue',
  CANCELADA: 'cancelada',
};

export function rotulo(estado: EstadoEntrega): string {
  return ROTULOS[estado];
}

/** Código de 4 dígitos que o comprador mostra ao entregador. */
export function gerarCodigoConfirmacao(aleatorio: () => number = Math.random): string {
  return String(Math.floor(1000 + aleatorio() * 9000));
}

/**
 * Mascara o telefone para exibição.
 *
 * O entregador só vê o número inteiro depois de aceitar a corrida — antes
 * disso a lista mostra mascarado. Evita que o cadastro de entregador vire
 * uma lista de contatos da cidade.
 */
export function mascararTelefone(telefone: string | null | undefined): string | null {
  if (!telefone) return null;
  const digitos = telefone.replace(/\D/g, '');
  if (digitos.length < 4) return '•••';
  return `(${digitos.slice(0, 2)}) ••••-${digitos.slice(-4)}`;
}
