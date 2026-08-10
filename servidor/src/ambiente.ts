/**
 * Configuração por variável de ambiente.
 *
 * O servidor não sobe com configuração faltando ou inválida: é melhor falhar
 * no deploy do que descobrir em produção que a chave da pagar.me estava vazia.
 */

import { z } from 'zod';

import {
  CIDADE,
  COMISSAO_COM_ENTREGADOR,
  COMISSAO_SEM_ENTREGADOR,
  DIAS_PARA_TESTAR,
  REPASSE_ENTREGADOR,
  UF,
} from './dominio/regras';

const porcentagem = z.coerce.number().min(0).max(1);

const esquema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORTA: z.coerce.number().int().positive().default(3333),

  DATABASE_URL: z.string().min(1, 'defina a URL do PostgreSQL'),

  /** Segredo para assinar o token de sessão do app. Gere com `openssl rand -hex 32`. */
  JWT_SEGREDO: z.string().min(32, 'use um segredo de pelo menos 32 caracteres'),
  JWT_EXPIRACAO: z.string().default('30d'),

  /**
   * Client IDs do Google Sign-In. O `WEB` é o que valida o id_token no servidor;
   * o `ANDROID` é o que o app usa. Ambos saem do mesmo projeto no Google Cloud.
   */
  GOOGLE_CLIENT_ID_WEB: z.string().min(1),
  GOOGLE_CLIENT_ID_ANDROID: z.string().optional(),

  /** Chave secreta da sua conta pagar.me (sk_test_... em homologação). */
  PAGARME_CHAVE_SECRETA: z.string().min(1),
  PAGARME_URL_BASE: z.string().url().default('https://api.pagar.me/core/v5'),
  /** recipient_id da SUA conta: é para onde vai a comissão. */
  PAGARME_RECEBEDOR_PLATAFORMA: z.string().min(1),
  /** Segredo configurado no painel da pagar.me para assinar os webhooks. */
  PAGARME_WEBHOOK_USUARIO: z.string().optional(),
  PAGARME_WEBHOOK_SENHA: z.string().optional(),

  /** Regras de negócio (valores padrão vêm de src/dominio/regras.ts). */
  COMISSAO_SEM_ENTREGADOR: porcentagem.default(COMISSAO_SEM_ENTREGADOR),
  COMISSAO_COM_ENTREGADOR: porcentagem.default(COMISSAO_COM_ENTREGADOR),
  REPASSE_ENTREGADOR: porcentagem.default(REPASSE_ENTREGADOR),
  DIAS_PARA_TESTAR: z.coerce.number().int().positive().default(DIAS_PARA_TESTAR),

  CIDADE: z.string().default(CIDADE),
  UF: z.string().length(2).default(UF),

  /** Origens liberadas no CORS, separadas por vírgula. */
  ORIGENS_PERMITIDAS: z.string().default('*'),
});

const analise = esquema.safeParse(process.env);

if (!analise.success) {
  const problemas = analise.error.issues
    .map((i) => `  - ${i.path.join('.')}: ${i.message}`)
    .join('\n');
  throw new Error(`Configuração inválida:\n${problemas}\n\nVeja .env.exemplo.`);
}

export const ambiente = analise.data;

export const emProducao = ambiente.NODE_ENV === 'production';
