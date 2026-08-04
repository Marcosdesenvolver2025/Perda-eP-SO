'use strict';

const cron = require('node-cron');
const { releaseFunds } = require('./releaseFunds');

/**
 * Agenda as rotinas internas. Em ambientes com varias instancias,
 * prefira desligar isso (RUN_JOBS=false) e usar um worker dedicado
 * chamando `npm run jobs:release`.
 */
function startScheduler() {
  if (process.env.RUN_JOBS === 'false') {
    console.log('[scheduler] desativado por RUN_JOBS=false');
    return;
  }

  // Todo dia as 03:00 (horario de Sao Paulo).
  cron.schedule(
    '0 3 * * *',
    () => {
      releaseFunds().catch((error) => console.error('[scheduler] releaseFunds:', error));
    },
    { timezone: 'America/Sao_Paulo' }
  );

  console.log('[scheduler] rotinas agendadas (liberacao de repasses as 03:00).');
}

module.exports = { startScheduler };
