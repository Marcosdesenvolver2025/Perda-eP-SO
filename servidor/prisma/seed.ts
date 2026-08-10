/**
 * Popula o banco para desenvolvimento: categorias e alguns anúncios.
 *
 * Rode com:  npm run seed
 * É seguro rodar mais de uma vez (usa upsert).
 */

import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

const categorias = [
  { slug: 'moda', nome: 'moda', emoji: '👕', ordem: 1 },
  { slug: 'eletronicos', nome: 'eletrônicos', emoji: '📱', ordem: 2 },
  { slug: 'casa', nome: 'casa', emoji: '🛋️', ordem: 3 },
  { slug: 'infantil', nome: 'infantil', emoji: '🧸', ordem: 4 },
  { slug: 'esporte', nome: 'esporte', emoji: '⚽', ordem: 5 },
  { slug: 'ferramentas', nome: 'ferramentas', emoji: '🔧', ordem: 6 },
  { slug: 'livros', nome: 'livros', emoji: '📚', ordem: 7 },
  { slug: 'outros', nome: 'outros', emoji: '📦', ordem: 8 },
];

async function main() {
  console.log('criando categorias...');
  for (const categoria of categorias) {
    await prisma.categoria.upsert({
      where: { slug: categoria.slug },
      update: categoria,
      create: categoria,
    });
  }

  console.log('criando vendedor de exemplo...');
  const vendedor = await prisma.usuario.upsert({
    where: { googleId: 'seed-vendedor' },
    update: {},
    create: {
      googleId: 'seed-vendedor',
      email: 'vendedor@exemplo.test',
      nome: 'Rubia',
      apelidoLoja: 'rubia store',
      bairro: 'Centro',
      cpf: '00000000191',
      telefone: '99999990000',
    },
  });

  const moda = await prisma.categoria.findUniqueOrThrow({ where: { slug: 'moda' } });
  const eletronicos = await prisma.categoria.findUniqueOrThrow({
    where: { slug: 'eletronicos' },
  });

  const anuncios = [
    {
      titulo: 'jaqueta de couro',
      descricao: 'Jaqueta de couro legítimo, tamanho M. Usada poucas vezes, sem rasgo.',
      preco: 12_000,
      condicao: 'USADO' as const,
      categoriaId: moda.id,
      pesoG: 1_200,
      comprimentoCm: 40,
      larguraCm: 30,
      alturaCm: 10,
    },
    {
      titulo: 'celular samsung a15',
      descricao: '128 GB, com carregador e capinha. Bateria segurando bem o dia todo.',
      preco: 78_000,
      precoOriginal: 99_000,
      condicao: 'SEMINOVO' as const,
      categoriaId: eletronicos.id,
      pesoG: 400,
      comprimentoCm: 20,
      larguraCm: 12,
      alturaCm: 6,
    },
    {
      titulo: 'ventilador 40cm',
      descricao: 'Ventilador de coluna, três velocidades. Funcionando perfeitamente.',
      preco: 8_500,
      condicao: 'USADO' as const,
      pesoG: 4_000,
      comprimentoCm: 45,
      larguraCm: 45,
      alturaCm: 20,
    },
  ];

  console.log('criando anúncios...');
  for (const anuncio of anuncios) {
    const existente = await prisma.anuncio.findFirst({
      where: { titulo: anuncio.titulo, vendedorId: vendedor.id },
    });
    if (existente) continue;

    await prisma.anuncio.create({
      data: { ...anuncio, vendedorId: vendedor.id, estado: 'ATIVO' },
    });
  }

  console.log('pronto.');
}

main()
  .catch((erro) => {
    console.error(erro);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
