'use strict';

const { HttpError } = require('../utils/httpError');

/** Valida req[source] com um schema Zod e substitui pelo valor tipado. */
function validate(schema, source = 'body') {
  return (req, _res, next) => {
    const result = schema.safeParse(req[source]);
    if (!result.success) {
      const details = result.error.issues.map((issue) => ({
        field: issue.path.join('.'),
        message: issue.message,
      }));
      return next(new HttpError(422, 'Dados invalidos.', details));
    }
    req[source] = result.data;
    return next();
  };
}

module.exports = { validate };
