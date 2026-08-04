'use strict';

const app = require('./app');
const env = require('./config/env');
const { startScheduler } = require('./jobs/scheduler');

const server = app.listen(env.port, () => {
  console.log(`\n  Vendas Itinga API`);
  console.log(`  ambiente: ${env.nodeEnv}`);
  console.log(`  ouvindo:  http://localhost:${env.port}\n`);
  startScheduler();
});

function shutdown(signal) {
  console.log(`\n[${signal}] encerrando servidor...`);
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
