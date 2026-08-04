'use strict';

const prisma = require('../lib/prisma');
const { verifyAppToken } = require('../services/auth.service');
const { HttpError } = require('../utils/httpError');

/** Exige um Bearer token valido e injeta req.user. */
async function requireAuth(req, _res, next) {
  try {
    const header = req.headers.authorization || '';
    const [scheme, token] = header.split(' ');

    if (scheme !== 'Bearer' || !token) {
      throw new HttpError(401, 'Token de acesso ausente.');
    }

    let payload;
    try {
      payload = verifyAppToken(token);
    } catch {
      throw new HttpError(401, 'Sessao expirada. Entre novamente.');
    }

    const user = await prisma.user.findUnique({ where: { id: payload.sub } });
    if (!user) throw new HttpError(401, 'Usuario nao encontrado.');

    req.user = user;
    return next();
  } catch (error) {
    return next(error);
  }
}

/** Autenticacao opcional: preenche req.user quando houver token valido. */
async function optionalAuth(req, _res, next) {
  const header = req.headers.authorization || '';
  const [scheme, token] = header.split(' ');
  if (scheme !== 'Bearer' || !token) return next();

  try {
    const payload = verifyAppToken(token);
    req.user = await prisma.user.findUnique({ where: { id: payload.sub } });
  } catch {
    req.user = null;
  }
  return next();
}

/** Restringe a rota a determinados papeis. */
function requireRole(...roles) {
  return (req, _res, next) => {
    if (!req.user) return next(new HttpError(401, 'Autenticacao necessaria.'));
    if (!roles.includes(req.user.role)) {
      return next(new HttpError(403, 'Voce nao tem permissao para esta acao.'));
    }
    return next();
  };
}

module.exports = { requireAuth, optionalAuth, requireRole };
