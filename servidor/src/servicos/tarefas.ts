/**
 * Tarefas periódicas.
 *
 * Rodam dentro do próprio processo para simplificar a operação no começo.
 * Quando houver mais de uma instância do servidor, troque por um agendador
 * externo (cron do provedor) chamando `POST /admin/tarefas/repasses`, senão
 * duas instâncias tentam repassar o mesmo pedido — a chave de idempotência da
 * pagar.me protege o dinheiro, mas o log fica sujo.
 */

import { log } from '../log';
import { confirmarEntregasVencidas } from './entregaDoVendedor';
import { expirarVencidas } from './ofertas';
import { liberarRepassesVencidos } from './repasse';

const UMA_HORA = 60 * 60 * 1000;

export function agendarTarefas() {
  const executar = async () => {
    // a ordem importa: confirmar entregas primeiro faz os pedidos recém
    // confirmados já entrarem na contagem da janela de teste
    try {
      await confirmarEntregasVencidas();
    } catch (erro) {
      log.error({ erro }, 'falha no ciclo de confirmação automática');
    }

    // negociação parada há mais de 3 dias sai da fila das duas partes
    try {
      const expiradas = await expirarVencidas();
      if (expiradas) log.info({ expiradas }, 'ofertas vencidas');
    } catch (erro) {
      log.error({ erro }, 'falha ao expirar ofertas');
    }

    try {
      const resultado = await liberarRepassesVencidos();
      if (resultado.liberados || resultado.falhas) {
        log.info(resultado, 'ciclo de repasses concluído');
      }
    } catch (erro) {
      log.error({ erro }, 'falha no ciclo de repasses');
    }
  };

  // não segura o encerramento do processo
  const temporizador = setInterval(executar, UMA_HORA);
  temporizador.unref();

  void executar();
  log.info('tarefas periódicas agendadas (confirmações e repasses a cada 1h)');
}
