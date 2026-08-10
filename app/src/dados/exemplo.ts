/**
 * Dados de exemplo.
 *
 * Servem só para o app abrir e navegar sem servidor (modo demonstração).
 * Assim que `EXPO_PUBLIC_API_URL` estiver configurado, nada daqui é usado.
 */

import type { Anuncio, Entrega, Pedido, Usuario } from '../api/tipos';

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
    aceitaEntregador: true,
    aceitaCombinado: true,
    fotos: [],
    criadoEm: new Date(Date.now() - 3 * 86_400_000).toISOString(),
    vendedor: { id: 'v1', nome: 'Rubia', apelidoLoja: 'rubia store', bairro: 'Centro' },
    entrega: { valorFrete: 800, diasParaTestar: 4 },
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
    modalidade: 'ENTREGADOR_PROPRIO',
    valorProduto: 12_000,
    valorFrete: 800,
    valorTotal: 12_800,
    valorComissao: 2_160,
    taxaComissao: 0.18,
    valorVendedor: 9_840,
    criadoEm: new Date(Date.now() - 5 * 86_400_000).toISOString(),
    entregueEm: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    prazoTesteAte: new Date(Date.now() + 2 * 86_400_000).toISOString(),
    anuncio: { titulo: 'jaqueta de couro', fotos: [] },
    vendedor: { nome: 'Rubia', apelidoLoja: 'rubia store' },
    entrega: { estado: 'ENTREGUE', codigoConfirmacao: '4821' },
    podePedirReembolso: true,
    diasRestantesParaTestar: 2,
  },
  {
    id: 'p2',
    codigo: 'VI-9XB4TR',
    estado: 'A_CAMINHO',
    modalidade: 'ENTREGADOR_PROPRIO',
    valorProduto: 78_000,
    valorFrete: 1_100,
    valorTotal: 79_100,
    valorComissao: 14_040,
    taxaComissao: 0.18,
    valorVendedor: 63_960,
    criadoEm: new Date(Date.now() - 86_400_000).toISOString(),
    anuncio: { titulo: 'celular samsung a15', fotos: [] },
    vendedor: { nome: 'Rondson' },
    entrega: { estado: 'COLETADA' },
    podePedirReembolso: false,
    diasRestantesParaTestar: null,
  },
];

export const entregasDemo: Entrega[] = [
  {
    id: 'e1',
    estado: 'AGUARDANDO_ENTREGADOR',
    valorEntregador: 640,
    coletaEndereco: 'Rua das Flores, 120 — Centro',
    entregaEndereco: 'Av. Principal, 45 — Bairro Novo',
    observacoes: 'Casa de portão azul',
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
      comprador: { nome: 'Ana', telefone: '99 99999-0000' },
      vendedor: { nome: 'Carlos', telefone: '99 98888-0000' },
    },
  },
  {
    id: 'e2',
    estado: 'ACEITA',
    valorEntregador: 800,
    coletaEndereco: 'Rua do Comércio, 8 — Centro',
    entregaEndereco: 'Rua São José, 210 — Vila Nova',
    criadoEm: new Date(Date.now() - 3_600_000).toISOString(),
    pedido: {
      codigo: 'VI-5HJ1WQ',
      anuncio: { titulo: 'panela de pressão' },
      comprador: { nome: 'Rubia' },
      vendedor: { nome: 'Marcos' },
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
