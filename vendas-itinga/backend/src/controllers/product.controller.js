'use strict';

const { z } = require('zod');
const prisma = require('../lib/prisma');
const env = require('../config/env');
const { HttpError } = require('../utils/httpError');
const { validateDimensions, distanceKm } = require('../services/shipping.service');
const { formatBRL } = require('../utils/money');

const MIN_PRICE = env.rules.minProductPriceCents;

const productSchema = z.object({
  title: z.string().min(3).max(100),
  description: z.string().min(10).max(2000),
  categoryId: z.string().optional(),
  condition: z.enum(['NEW', 'LIKE_NEW', 'USED']).default('USED'),
  size: z.string().max(20).optional(),
  brand: z.string().max(50).optional(),

  // Regra de negocio: valor minimo de R$ 10,00 por produto.
  priceCents: z
    .number()
    .int('O preco deve ser informado em centavos.')
    .min(MIN_PRICE, `O valor minimo do produto e ${formatBRL(MIN_PRICE)}.`),

  // Restricoes fisicas obrigatorias.
  weightGrams: z
    .number()
    .int()
    .positive()
    .max(env.rules.maxProductWeightGrams, `Peso maximo: ${env.rules.maxProductWeightGrams / 1000} kg.`),
  heightCm: z
    .number()
    .int()
    .positive()
    .max(env.rules.maxProductHeightCm, `Altura maxima: ${env.rules.maxProductHeightCm} cm.`),
  widthCm: z.number().int().positive().max(200),
  lengthCm: z.number().int().positive().max(200),

  shippingMode: z.enum(['PLATFORM', 'SELLER']).default('PLATFORM'),

  latitude: z.number().optional(),
  longitude: z.number().optional(),
  city: z.string().max(60).optional(),
  state: z.string().length(2).optional(),

  images: z.array(z.string().url()).min(1, 'Envie pelo menos 1 foto.').max(10),
});

const listQuerySchema = z.object({
  q: z.string().optional(),
  categoryId: z.string().optional(),
  condition: z.enum(['NEW', 'LIKE_NEW', 'USED']).optional(),
  size: z.string().optional(),
  minPrice: z.coerce.number().int().optional(),
  maxPrice: z.coerce.number().int().optional(),
  sellerId: z.string().optional(),
  lat: z.coerce.number().optional(),
  lng: z.coerce.number().optional(),
  radiusKm: z.coerce.number().min(1).max(500).optional(),
  sort: z.enum(['recent', 'price_asc', 'price_desc', 'popular', 'nearest']).default('recent'),
  page: z.coerce.number().int().min(1).default(1),
  perPage: z.coerce.number().int().min(1).max(50).default(20),
});

function serialize(product, { userId, lat, lng } = {}) {
  return {
    id: product.id,
    title: product.title,
    description: product.description,
    priceCents: product.priceCents,
    condition: product.condition,
    size: product.size,
    brand: product.brand,
    status: product.status,
    shippingMode: product.shippingMode,
    weightGrams: product.weightGrams,
    dimensions: {
      heightCm: product.heightCm,
      widthCm: product.widthCm,
      lengthCm: product.lengthCm,
    },
    city: product.city,
    state: product.state,
    images: (product.images || []).map((img) => img.url),
    favoriteCount: product.favoriteCount,
    isFavorite: userId ? (product.favorites || []).length > 0 : false,
    distanceKm:
      lat != null && lng != null && product.latitude != null && product.longitude != null
        ? Number(distanceKm(lat, lng, product.latitude, product.longitude)?.toFixed(1))
        : null,
    seller: product.seller
      ? {
          id: product.seller.id,
          name: product.seller.name,
          avatarUrl: product.seller.avatarUrl,
          ratingAvg: product.seller.ratingAvg,
          ratingCount: product.seller.ratingCount,
        }
      : undefined,
    category: product.category || undefined,
    createdAt: product.createdAt,
  };
}

/** GET /products - feed com busca, filtros e proximidade geografica. */
async function list(req, res, next) {
  try {
    const { q, categoryId, condition, size, minPrice, maxPrice, sellerId, lat, lng, radiusKm, sort, page, perPage } =
      req.query;

    const where = {
      status: 'ACTIVE',
      ...(categoryId ? { categoryId } : {}),
      ...(condition ? { condition } : {}),
      ...(size ? { size } : {}),
      ...(sellerId ? { sellerId } : {}),
      ...(minPrice || maxPrice
        ? { priceCents: { ...(minPrice ? { gte: minPrice } : {}), ...(maxPrice ? { lte: maxPrice } : {}) } }
        : {}),
      ...(q
        ? {
            OR: [
              { title: { contains: q, mode: 'insensitive' } },
              { description: { contains: q, mode: 'insensitive' } },
              { brand: { contains: q, mode: 'insensitive' } },
            ],
          }
        : {}),
    };

    const orderBy =
      sort === 'price_asc'
        ? { priceCents: 'asc' }
        : sort === 'price_desc'
          ? { priceCents: 'desc' }
          : sort === 'popular'
            ? { favoriteCount: 'desc' }
            : { createdAt: 'desc' };

    const geoFilter = lat != null && lng != null;
    // Com filtro geografico buscamos um lote maior e refinamos por distancia
    // em memoria (PostGIS seria o passo seguinte para escala maior).
    const take = geoFilter ? Math.min(perPage * 10, 300) : perPage;
    const skip = geoFilter ? 0 : (page - 1) * perPage;

    const [rows, total] = await Promise.all([
      prisma.product.findMany({
        where,
        orderBy,
        take,
        skip,
        include: {
          images: { orderBy: { position: 'asc' }, take: 1 },
          seller: { select: { id: true, name: true, avatarUrl: true, ratingAvg: true, ratingCount: true } },
          category: { select: { id: true, name: true, slug: true } },
          ...(req.user ? { favorites: { where: { userId: req.user.id }, select: { id: true } } } : {}),
        },
      }),
      prisma.product.count({ where }),
    ]);

    let items = rows.map((p) => serialize(p, { userId: req.user?.id, lat, lng }));

    if (geoFilter) {
      const limit = radiusKm ?? env.shipping.maxRadiusKm;
      items = items.filter((p) => p.distanceKm != null && p.distanceKm <= limit);
      if (sort === 'nearest') items.sort((a, b) => a.distanceKm - b.distanceKm);
      const start = (page - 1) * perPage;
      items = items.slice(start, start + perPage);
    }

    return res.json({
      items,
      page,
      perPage,
      total: geoFilter ? items.length : total,
      hasMore: geoFilter ? items.length === perPage : page * perPage < total,
    });
  } catch (error) {
    return next(error);
  }
}

/** GET /products/:id */
async function detail(req, res, next) {
  try {
    const product = await prisma.product.findUnique({
      where: { id: req.params.id },
      include: {
        images: { orderBy: { position: 'asc' } },
        seller: { select: { id: true, name: true, avatarUrl: true, ratingAvg: true, ratingCount: true } },
        category: { select: { id: true, name: true, slug: true } },
        ...(req.user ? { favorites: { where: { userId: req.user.id }, select: { id: true } } } : {}),
      },
    });

    if (!product || product.status === 'DELETED') {
      throw new HttpError(404, 'Produto nao encontrado.');
    }

    await prisma.product.update({
      where: { id: product.id },
      data: { viewCount: { increment: 1 } },
    });

    return res.json(serialize(product, { userId: req.user?.id }));
  } catch (error) {
    return next(error);
  }
}

/** POST /products - cadastro de anuncio (fluxo do vendedor). */
async function create(req, res, next) {
  try {
    if (!req.user.pagarmeRecipientId) {
      throw new HttpError(
        400,
        'Cadastre sua conta de vendedor (dados bancarios) antes de anunciar.'
      );
    }

    const { images, ...data } = req.body;

    const dimensionErrors = validateDimensions(data);
    if (dimensionErrors.length) {
      throw new HttpError(422, 'Produto fora das restricoes de envio.', dimensionErrors);
    }

    const product = await prisma.product.create({
      data: {
        ...data,
        sellerId: req.user.id,
        images: {
          create: images.map((url, position) => ({ url, position })),
        },
      },
      include: { images: { orderBy: { position: 'asc' } } },
    });

    return res.status(201).json(serialize(product));
  } catch (error) {
    return next(error);
  }
}

/** PATCH /products/:id */
async function update(req, res, next) {
  try {
    const existing = await prisma.product.findFirst({
      where: { id: req.params.id, sellerId: req.user.id },
    });
    if (!existing) throw new HttpError(404, 'Anuncio nao encontrado.');

    const { images, ...data } = req.body;

    if (data.weightGrams || data.heightCm) {
      const errors = validateDimensions({
        weightGrams: data.weightGrams ?? existing.weightGrams,
        heightCm: data.heightCm ?? existing.heightCm,
      });
      if (errors.length) throw new HttpError(422, 'Produto fora das restricoes de envio.', errors);
    }

    if (data.priceCents && data.priceCents < MIN_PRICE) {
      throw new HttpError(422, `O valor minimo do produto e ${formatBRL(MIN_PRICE)}.`);
    }

    const product = await prisma.product.update({
      where: { id: existing.id },
      data: {
        ...data,
        ...(images
          ? {
              images: {
                deleteMany: {},
                create: images.map((url, position) => ({ url, position })),
              },
            }
          : {}),
      },
      include: { images: { orderBy: { position: 'asc' } } },
    });

    return res.json(serialize(product));
  } catch (error) {
    return next(error);
  }
}

/** PATCH /products/:id/status - pausar / reativar anuncio. */
async function changeStatus(req, res, next) {
  try {
    const { status } = req.body;
    const existing = await prisma.product.findFirst({
      where: { id: req.params.id, sellerId: req.user.id },
    });
    if (!existing) throw new HttpError(404, 'Anuncio nao encontrado.');

    const product = await prisma.product.update({
      where: { id: existing.id },
      data: { status },
    });
    return res.json({ id: product.id, status: product.status });
  } catch (error) {
    return next(error);
  }
}

/** DELETE /products/:id - exclusao logica (preserva historico de pedidos). */
async function remove(req, res, next) {
  try {
    const existing = await prisma.product.findFirst({
      where: { id: req.params.id, sellerId: req.user.id },
    });
    if (!existing) throw new HttpError(404, 'Anuncio nao encontrado.');

    await prisma.product.update({ where: { id: existing.id }, data: { status: 'DELETED' } });
    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
}

// ------------------------------ Favoritos ------------------------------

async function listFavorites(req, res, next) {
  try {
    const favorites = await prisma.favorite.findMany({
      where: { userId: req.user.id },
      orderBy: { createdAt: 'desc' },
      include: {
        product: {
          include: {
            images: { orderBy: { position: 'asc' }, take: 1 },
            seller: { select: { id: true, name: true, avatarUrl: true, ratingAvg: true, ratingCount: true } },
          },
        },
      },
    });
    return res.json(
      favorites
        .filter((f) => f.product.status !== 'DELETED')
        .map((f) => ({ ...serialize(f.product), isFavorite: true }))
    );
  } catch (error) {
    return next(error);
  }
}

async function toggleFavorite(req, res, next) {
  try {
    const productId = req.params.id;
    const existing = await prisma.favorite.findUnique({
      where: { userId_productId: { userId: req.user.id, productId } },
    });

    if (existing) {
      await prisma.$transaction([
        prisma.favorite.delete({ where: { id: existing.id } }),
        prisma.product.update({ where: { id: productId }, data: { favoriteCount: { decrement: 1 } } }),
      ]);
      return res.json({ isFavorite: false });
    }

    await prisma.$transaction([
      prisma.favorite.create({ data: { userId: req.user.id, productId } }),
      prisma.product.update({ where: { id: productId }, data: { favoriteCount: { increment: 1 } } }),
    ]);
    return res.json({ isFavorite: true });
  } catch (error) {
    return next(error);
  }
}

/** GET /categories */
async function listCategories(_req, res, next) {
  try {
    const categories = await prisma.category.findMany({
      where: { parentId: null },
      orderBy: { name: 'asc' },
      include: { children: { orderBy: { name: 'asc' } } },
    });
    return res.json(categories);
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  productSchema,
  listQuerySchema,
  list,
  detail,
  create,
  update,
  changeStatus,
  remove,
  listFavorites,
  toggleFavorite,
  listCategories,
  serialize,
};
