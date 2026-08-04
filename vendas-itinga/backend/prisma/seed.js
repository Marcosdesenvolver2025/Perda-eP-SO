'use strict';

// Carrega o .env: o seed roda fora do servidor e precisa da DATABASE_URL.
require('dotenv').config();

const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();

const CATEGORIES = [
  { name: 'Roupas e Calcados', slug: 'roupas-e-calcados', icon: 'shirt-outline' },
  { name: 'Eletronicos', slug: 'eletronicos', icon: 'phone-portrait-outline' },
  { name: 'Casa e Decoracao', slug: 'casa-e-decoracao', icon: 'home-outline' },
  { name: 'Beleza e Perfumaria', slug: 'beleza-e-perfumaria', icon: 'sparkles-outline' },
  { name: 'Infantil', slug: 'infantil', icon: 'balloon-outline' },
  { name: 'Esporte e Lazer', slug: 'esporte-e-lazer', icon: 'football-outline' },
  { name: 'Livros e Papelaria', slug: 'livros-e-papelaria', icon: 'book-outline' },
  { name: 'Ferramentas', slug: 'ferramentas', icon: 'construct-outline' },
  { name: 'Outros', slug: 'outros', icon: 'pricetags-outline' },
];

async function main() {
  for (const category of CATEGORIES) {
    await prisma.category.upsert({
      where: { slug: category.slug },
      create: category,
      update: { name: category.name, icon: category.icon },
    });
  }
  console.log(`Categorias sincronizadas: ${CATEGORIES.length}`);
}

main()
  .catch((error) => {
    console.error(error);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
