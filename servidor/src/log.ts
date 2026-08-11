import pino from 'pino';

import { ambiente, emProducao } from './ambiente';

export const log = pino({
  level: ambiente.NODE_ENV === 'test' ? 'silent' : emProducao ? 'info' : 'debug',
  // nunca deixe dado de pagamento ou token cair no log
  redact: [
    'req.headers.authorization',
    'tokenCartao',
    '*.card_token',
    '*.document',
    'PAGARME_SECRET_KEY',
  ],
  ...(emProducao ? {} : { transport: { target: 'pino/file', options: { destination: 1 } } }),
});
