/**
 * API do Vendas Itinga.
 *
 * Sobe em `PORTA` (padrão 3333). Antes de rodar:
 *   cp .env.exemplo .env   (e preencha)
 *   npm run prisma:migrate
 *   npm run dev
 */

import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import pinoHttp from 'pino-http';

import { ambiente } from './ambiente';
import { log } from './log';
import { rotaNaoEncontrada, tratarErros } from './middlewares/erros';
import { rotasAdmin } from './rotas/admin';
import { rotasAnuncios } from './rotas/anuncios';
import { rotasAutenticacao } from './rotas/autenticacao';
import { rotasConta } from './rotas/conta';
import { rotasEntregas } from './rotas/entregas';
import { rotasMensagens } from './rotas/mensagens';
import { rotasPedidos } from './rotas/pedidos';
import { rotasOfertas, rotasVendedores } from './rotas/social';
import { rotasRecebedores } from './rotas/recebedores';
import { rotasWebhooks } from './rotas/webhooks';
import { agendarTarefas } from './servicos/tarefas';
import {
  ALTURA_MAXIMA_CM,
  LARGURA_MAXIMA_CM,
  PESO_MAXIMO_G,
  TABELA_DE_TARIFAS,
} from './dominio/regras';

const app = express();

app.set('trust proxy', 1);
app.use(helmet());
app.use(
  cors({
    origin:
      ambiente.ORIGENS_PERMITIDAS === '*'
        ? true
        : ambiente.ORIGENS_PERMITIDAS.split(',').map((o) => o.trim()),
  }),
);
app.use(express.json({ limit: '1mb' }));
app.use(pinoHttp({ logger: log }));

/** Sonda de saúde para o provedor de hospedagem. */
app.get('/saude', (_req, res) => res.json({ ok: true, versao: '1.0.0' }));

/**
 * GET /configuracoes
 * O app lê isto ao abrir, então uma mudança de comissão ou de limite entra em
 * vigor sem precisar de nova versão na Play Store.
 */
app.get('/configuracoes', (_req, res) =>
  res.json({
    cidade: ambiente.CIDADE,
    uf: ambiente.UF,
    comissao: ambiente.COMISSAO,
    valorMinimoVenda: ambiente.VALOR_MINIMO_VENDA,
    // a última faixa é aberta; o app mostra "a partir de"
    tarifas: TABELA_DE_TARIFAS.map((f) => ({
      ateInclusive: Number.isFinite(f.ateInclusive) ? f.ateInclusive : null,
      tarifa: f.tarifa,
    })),
    diasParaTestar: ambiente.DIAS_PARA_TESTAR,
    diasParaConfirmacaoAutomatica: ambiente.DIAS_PARA_CONFIRMACAO_AUTOMATICA,
    // limites valem só para a modalidade PLATAFORMA
    pesoMaximoG: PESO_MAXIMO_G,
    larguraMaximaCm: LARGURA_MAXIMA_CM,
    alturaMaximaCm: ALTURA_MAXIMA_CM,
  }),
);

app.use('/auth', rotasAutenticacao);
app.use('/anuncios', rotasAnuncios);
app.use('/pedidos', rotasPedidos);
app.use('/ofertas', rotasOfertas);
app.use('/vendedores', rotasVendedores);
app.use('/entregas', rotasEntregas);
app.use('/recebedores', rotasRecebedores);
app.use('/mensagens', rotasMensagens);
app.use('/conta', rotasConta);
app.use('/admin', rotasAdmin);
app.use('/webhooks', rotasWebhooks);

app.use(rotaNaoEncontrada);
app.use(tratarErros);

const servidor = app.listen(ambiente.PORTA, () => {
  log.info(
    { porta: ambiente.PORTA, ambiente: ambiente.NODE_ENV, cidade: ambiente.CIDADE },
    'Vendas Itinga no ar',
  );
  agendarTarefas();
});

// encerramento limpo: espera as requisições em andamento terminarem
for (const sinal of ['SIGTERM', 'SIGINT'] as const) {
  process.on(sinal, () => {
    log.info({ sinal }, 'encerrando');
    servidor.close(() => process.exit(0));
  });
}

export { app };
