import type { NavigatorScreenParams } from '@react-navigation/native';

/** Abas de baixo: as mesmas cinco do vídeo. */
export type ParametrosAbas = {
  Home: undefined;
  Buscar: undefined;
  Vendas: undefined;
  Notificacoes: undefined;
  MinhaConta: undefined;
};

export type ParametrosApp = {
  Abas: NavigatorScreenParams<ParametrosAbas>;
  Entrar: undefined;
  Busca: { termo?: string };
  Produto: { id: string };
  Loja: { vendedorId: string };
  Checkout: { anuncioId: string; enderecoId?: string };
  Pedido: { id: string; pixQrCode?: string };
  Reembolso: { pedidoId: string };
  NovoAnuncio: undefined;
  MinhaLoja: undefined;
  MinhasCompras: undefined;
  MinhasVendas: undefined;
  Conversa: { pedidoId: string };
  Configuracoes: undefined;
  DadosPessoais: undefined;
  Enderecos: undefined;
  ContaDeRecebimento: undefined;
  AreaDoEntregador: undefined;
  ConfirmarEntrega: { entregaId: string };
  ComoFunciona: undefined;
};

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace ReactNavigation {
    // deixa `navigation.navigate` tipado em qualquer lugar do app
    interface RootParamList extends ParametrosApp {}
  }
}
