/**
 * Dados de exemplo.
 *
 * Servem só para o app abrir e navegar sem servidor (modo demonstração).
 * Assim que `EXPO_PUBLIC_API_URL` estiver configurado, nada daqui é usado.
 */

import type { Anuncio, Corrida, Pedido, Usuario } from '../api/tipos';

export const usuarioDemo: Usuario = {
  id: 'demo-1',
  nome: 'Marcos',
  email: 'voce@exemplo.com',
  fotoUrl: null,
  papel: 'CLIENTE',
  apelidoLoja: 'minha lojinha',
  bairro: 'Centro',
  cidade: 'Itinga',
  recebedor: null,
  precisaCompletarCadastro: true,
};

function anuncio(
  id: string,
  titulo: string,
  preco: number,
  extras: Partial<Anuncio> = {},
): Anuncio {
  return {
    id,
    titulo,
    descricao:
      'Produto em bom estado, pouco usado. Retirada no centro ou entrega pelo Vendas Itinga.',
    preco,
    condicao: 'USADO',
    pesoG: 1500,
    comprimentoCm: 30,
    larguraCm: 20,
    alturaCm: 12,
    fotos: [],
    criadoEm: new Date(Date.now() - 3 * 86_400_000).toISOString(),
    vendedor: { id: 'v1', nome: 'Rubia', apelidoLoja: 'rubia store', bairro: 'Centro' },
    modalidadeEntrega: 'PLATAFORMA',
    entrega: { modalidade: 'PLATAFORMA', diasParaTestar: 7, entregaInclusa: true },
    ...extras,
  };
}

export const anunciosDemo: Anuncio[] = [
  anuncio('a1', 'bicicleta aro 26', 45_000, { condicao: 'SEMINOVO', pesoG: 14_000 }),
  anuncio('a2', 'celular samsung a15', 78_000, { condicao: 'SEMINOVO', precoOriginal: 99_000 }),
  anuncio('a3', 'jaqueta de couro', 12_000),
  anuncio('a4', 'ventilador 40cm', 8_500, { pesoG: 4_000 }),
  anuncio('a5', 'tênis nike tam 40', 15_000, { condicao: 'USADO' }),
  anuncio('a6', 'panela de pressão', 6_000, { condicao: 'NOVO' }),
  anuncio('a7', 'mesa de escritório', 22_000, { pesoG: 18_000, comprimentoCm: 60 }),
  anuncio('a8', 'coleção de livros', 4_500),
  anuncio('a9', 'fone bluetooth', 9_900, { condicao: 'NOVO', precoOriginal: 14_900 }),
  anuncio('a10', 'carrinho de bebê', 18_000, { pesoG: 9_000 }),
];

export const pedidosDemo: Pedido[] = [
  {
    id: 'p1',
    codigo: 'VI-7K3QM2',
    estado: 'ENTREGUE',
    modalidade: 'PLATAFORMA',
    valorProduto: 12_000,
    valorTotal: 12_000,
    valorComissao: 1_440,
    valorTarifa: 850,
    taxaComissao: 0.12,
    valorVendedor: 9_710,
    criadoEm: new Date(Date.now() - 5 * 86_400_000).toISOString(),
    entregueEm: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    prazoTesteAte: new Date(Date.now() + 2 * 86_400_000).toISOString(),
    anuncio: { titulo: 'jaqueta de couro', fotos: [] },
    vendedor: { nome: 'Rubia', apelidoLoja: 'rubia store' },
    entrega: { id: 'e1', estado: 'ENTREGUE', codigoConfirmacao: '4821' },
    podePedirReembolso: true,
    diasRestantesParaTestar: 2,
  },
  {
    id: 'p2',
    codigo: 'VI-9XB4TR',
    estado: 'EM_ROTA_PARA_ENTREGA',
    modalidade: 'PLATAFORMA',
    valorProduto: 78_000,
    valorTotal: 78_000,
    valorComissao: 9_360,
    valorTarifa: 1_850,
    taxaComissao: 0.12,
    valorVendedor: 66_790,
    criadoEm: new Date(Date.now() - 86_400_000).toISOString(),
    anuncio: { titulo: 'celular samsung a15', fotos: [] },
    vendedor: { nome: 'Rondson' },
    entrega: { id: 'e2', estado: 'EM_ROTA_PARA_ENTREGA', codigoConfirmacao: '9137' },
    podePedirReembolso: false,
    diasRestantesParaTestar: null,
  },
];

export const corridasDemo: Corrida[] = [
  {
    id: 'c1',
    tipo: 'ENTREGA',
    estado: 'ATRIBUIDA',
    valorEntregador: 500,
    coletaEndereco: 'Rua das Flores, 120, Centro, Itinga/MG',
    coletaReferencia: 'Portão azul, ao lado da padaria',
    coletaContato: 'Carlos',
    coletaTelefone: '(33) ••••-0000',
    entregaEndereco: 'Av. Principal, 45, Bairro Novo, Itinga/MG',
    entregaContato: 'Ana',
    entregaTelefone: '(33) ••••-1111',
    telefoneLiberado: false,
    criadoEm: new Date().toISOString(),
    pedido: {
      codigo: 'VI-2LP8ZK',
      valorProduto: 8_500,
      anuncio: {
        titulo: 'ventilador 40cm',
        pesoG: 4_000,
        comprimentoCm: 45,
        larguraCm: 45,
        alturaCm: 20,
      },
    },
  },
  {
    id: 'c2',
    tipo: 'ENTREGA',
    estado: 'CHEGOU_NA_COLETA',
    valorEntregador: 500,
    coletaEndereco: 'Rua do Comércio, 8, Centro, Itinga/MG',
    coletaContato: 'Marcos',
    coletaTelefone: '33988880000',
    entregaEndereco: 'Rua São José, 210, Vila Nova, Itinga/MG',
    entregaContato: 'Rubia',
    entregaTelefone: '33977770000',
    telefoneLiberado: true,
    criadoEm: new Date(Date.now() - 3_600_000).toISOString(),
    pedido: {
      codigo: 'VI-5HJ1WQ',
      anuncio: { titulo: 'panela de pressão', pesoG: 2_000, comprimentoCm: 30, larguraCm: 30, alturaCm: 25 },
    },
  },
  {
    id: 'c3',
    tipo: 'DEVOLUCAO',
    estado: 'ENTREGUE',
    valorEntregador: 500,
    coletaEndereco: 'Rua São José, 210, Vila Nova, Itinga/MG',
    coletaContato: 'Rubia',
    entregaEndereco: 'Rua do Comércio, 8, Centro, Itinga/MG',
    entregaContato: 'Marcos',
    observacoes: 'DEVOLUÇÃO: buscar no comprador e devolver ao vendedor.',
    telefoneLiberado: true,
    criadoEm: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    pedido: {
      codigo: 'VI-3RT9PL',
      anuncio: { titulo: 'fone bluetooth' },
    },
  },
];

export const categoriasDemo = [
  { chave: 'todos', rotulo: 'pra você' },
  { chave: 'novidades', rotulo: 'novidades' },
  { chave: 'moda', rotulo: 'moda' },
  { chave: 'eletronicos', rotulo: 'eletrônicos' },
  { chave: 'casa', rotulo: 'casa' },
  { chave: 'infantil', rotulo: 'infantil' },
  { chave: 'esporte', rotulo: 'esporte' },
];
