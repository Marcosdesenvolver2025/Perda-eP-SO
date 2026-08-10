import { PrismaClient } from '@prisma/client';

import { emProducao } from './ambiente';

export const prisma = new PrismaClient({
  log: emProducao ? ['warn', 'error'] : ['warn', 'error'],
});
