'use strict';

const jwt = require('jsonwebtoken');
const { OAuth2Client } = require('google-auth-library');
const env = require('../config/env');
const prisma = require('../lib/prisma');
const { HttpError } = require('../utils/httpError');

const googleClient = new OAuth2Client();

let firebaseAdmin = null;
if (env.firebase.projectId && env.firebase.clientEmail && env.firebase.privateKey) {
  // eslint-disable-next-line global-require
  const admin = require('firebase-admin');
  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.cert({
        projectId: env.firebase.projectId,
        clientEmail: env.firebase.clientEmail,
        privateKey: env.firebase.privateKey,
      }),
    });
  }
  firebaseAdmin = admin;
}

/** Valida um ID token emitido diretamente pelo Google Sign-In. */
async function verifyGoogleIdToken(idToken) {
  if (!env.googleClientIds.length) {
    throw new HttpError(500, 'GOOGLE_CLIENT_IDS nao configurado no servidor.');
  }
  const ticket = await googleClient.verifyIdToken({
    idToken,
    audience: env.googleClientIds,
  });
  const payload = ticket.getPayload();
  if (!payload?.email) {
    throw new HttpError(401, 'Token do Google sem e-mail associado.');
  }
  return {
    provider: 'google',
    googleId: payload.sub,
    firebaseUid: null,
    email: payload.email.toLowerCase(),
    name: payload.name || payload.email.split('@')[0],
    avatarUrl: payload.picture || null,
  };
}

/** Valida um ID token emitido pelo Firebase Auth (login Google via Firebase). */
async function verifyFirebaseIdToken(idToken) {
  if (!firebaseAdmin) {
    throw new HttpError(500, 'Firebase Admin nao configurado no servidor.');
  }
  const decoded = await firebaseAdmin.auth().verifyIdToken(idToken);
  if (!decoded.email) {
    throw new HttpError(401, 'Token do Firebase sem e-mail associado.');
  }
  return {
    provider: 'firebase',
    googleId: decoded.firebase?.identities?.['google.com']?.[0] || null,
    firebaseUid: decoded.uid,
    email: decoded.email.toLowerCase(),
    name: decoded.name || decoded.email.split('@')[0],
    avatarUrl: decoded.picture || null,
  };
}

/**
 * Aceita token do Google OU do Firebase. Tenta o provider informado e,
 * se nao vier nenhum, tenta Google e depois Firebase.
 */
async function verifyIdToken(idToken, provider) {
  if (provider === 'google') return verifyGoogleIdToken(idToken);
  if (provider === 'firebase') return verifyFirebaseIdToken(idToken);

  try {
    return await verifyGoogleIdToken(idToken);
  } catch (googleError) {
    if (!firebaseAdmin) {
      throw new HttpError(401, `Token invalido: ${googleError.message}`);
    }
    try {
      return await verifyFirebaseIdToken(idToken);
    } catch (firebaseError) {
      throw new HttpError(401, `Token invalido: ${firebaseError.message}`);
    }
  }
}

/** Cria (ou atualiza) o usuario a partir do perfil autenticado. */
async function upsertUserFromProfile(profile) {
  const existing = await prisma.user.findFirst({
    where: {
      OR: [
        { email: profile.email },
        ...(profile.googleId ? [{ googleId: profile.googleId }] : []),
        ...(profile.firebaseUid ? [{ firebaseUid: profile.firebaseUid }] : []),
      ],
    },
  });

  if (existing) {
    return prisma.user.update({
      where: { id: existing.id },
      data: {
        googleId: profile.googleId ?? existing.googleId,
        firebaseUid: profile.firebaseUid ?? existing.firebaseUid,
        name: existing.name || profile.name,
        avatarUrl: profile.avatarUrl ?? existing.avatarUrl,
      },
    });
  }

  return prisma.user.create({
    data: {
      email: profile.email,
      name: profile.name,
      avatarUrl: profile.avatarUrl,
      googleId: profile.googleId,
      firebaseUid: profile.firebaseUid,
      role: 'BUYER',
    },
  });
}

function signAppToken(user) {
  return jwt.sign({ sub: user.id, email: user.email, role: user.role }, env.jwtSecret, {
    expiresIn: env.jwtExpiresIn,
  });
}

function verifyAppToken(token) {
  return jwt.verify(token, env.jwtSecret);
}

module.exports = {
  verifyIdToken,
  verifyGoogleIdToken,
  verifyFirebaseIdToken,
  upsertUserFromProfile,
  signAppToken,
  verifyAppToken,
};
