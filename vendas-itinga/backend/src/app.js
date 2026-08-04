'use strict';

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');

const env = require('./config/env');
const routes = require('./routes');
const { notFound, errorHandler } = require('./middlewares/error');
const { UPLOAD_DIR } = require('./controllers/upload.controller');

const app = express();

app.set('trust proxy', 1);
// crossOriginResourcePolicy desligado para as imagens dos anuncios
// poderem ser exibidas no app e no site.
app.use(helmet({ crossOriginResourcePolicy: false }));
app.use(cors({ origin: true, credentials: true }));
// Limite maior para aceitar fotos de anuncio em base64 (data URL).
app.use(express.json({ limit: '12mb' }));

// Imagens dos anuncios (armazenamento local - ver upload.controller.js).
app.use('/uploads', express.static(UPLOAD_DIR, { maxAge: '30d' }));
app.use(morgan(env.isProduction ? 'combined' : 'dev'));

app.use(
  rateLimit({
    windowMs: 60 * 1000,
    limit: 120,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    // Webhooks do gateway nao devem ser limitados.
    skip: (req) => req.path.startsWith('/webhooks/'),
    message: { error: 'Muitas requisicoes. Tente novamente em instantes.' },
  })
);

app.use('/', routes);

app.use(notFound);
app.use(errorHandler);

module.exports = app;
