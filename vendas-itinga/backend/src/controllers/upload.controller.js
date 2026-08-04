'use strict';

const fs = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const { z } = require('zod');
const env = require('../config/env');
const { HttpError } = require('../utils/httpError');

const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads');
const MAX_BYTES = 8 * 1024 * 1024; // 8 MB por imagem

const EXTENSIONS = {
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
};

const uploadSchema = z.object({
  // data URL: "data:image/jpeg;base64,...."
  image: z.string().startsWith('data:image/', 'Envie uma imagem em data URL.'),
});

/**
 * POST /uploads
 *
 * Armazenamento local, suficiente para desenvolvimento e para o primeiro
 * lancamento. Para escalar, troque este controller por um upload direto
 * ao S3/Cloudinary (URL assinada) - o app so precisa continuar recebendo
 * a URL publica final.
 */
async function uploadImage(req, res, next) {
  try {
    const { image } = req.body;

    const match = /^data:(image\/[a-zA-Z+]+);base64,(.+)$/s.exec(image);
    if (!match) throw new HttpError(422, 'Formato de imagem invalido.');

    const [, mimeType, base64] = match;
    const extension = EXTENSIONS[mimeType];
    if (!extension) throw new HttpError(422, 'Use imagens JPG, PNG ou WEBP.');

    const buffer = Buffer.from(base64, 'base64');
    if (buffer.length > MAX_BYTES) {
      throw new HttpError(413, 'Imagem muito grande. Envie um arquivo de ate 8 MB.');
    }

    await fs.mkdir(UPLOAD_DIR, { recursive: true });

    const fileName = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}.${extension}`;
    await fs.writeFile(path.join(UPLOAD_DIR, fileName), buffer);

    return res.status(201).json({ url: `${env.apiPublicUrl}/uploads/${fileName}` });
  } catch (error) {
    return next(error);
  }
}

module.exports = { uploadSchema, uploadImage, UPLOAD_DIR };
