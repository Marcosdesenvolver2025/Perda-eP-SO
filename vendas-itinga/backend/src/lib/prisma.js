'use strict';

const { PrismaClient } = require('@prisma/client');

const prisma = global.__vendasItingaPrisma || new PrismaClient({
  log: process.env.NODE_ENV === 'production' ? ['error'] : ['warn', 'error'],
});

if (process.env.NODE_ENV !== 'production') {
  global.__vendasItingaPrisma = prisma;
}

module.exports = prisma;
