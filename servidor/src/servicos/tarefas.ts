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
import { liberarRepassesVencidos } from './repasse';

const UMA_HORA = 60 * 60 * 1000;

export function agendarTarefas() {
  const executar = async () => {
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
  log.info('tarefas periódicas agendadas (repasses a cada 1h)');
}
