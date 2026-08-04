'use strict';

const env = require('../config/env');

function notFound(req, res) {
  res.status(404).json({ error: `Rota nao encontrada: ${req.method} ${req.originalUrl}` });
}

// eslint-disable-next-line no-unused-vars
function errorHandler(error, req, res, _next) {
  const status = error.status || 500;

  if (status >= 500) {
    console.error('[erro]', error);
  }

  res.status(status).json({
    error: status >= 500 && env.isProduction ? 'Erro interno no servidor.' : error.message,
    ...(error.details ? { details: error.details } : {}),
  });
}

module.exports = { notFound, errorHandler };
