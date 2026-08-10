/**
 * Erros da aplicação.
 *
 * A mensagem de `ErroDaApi` vai direto para a tela do usuário, então escreva
 * em português claro, sem jargão técnico.
 */

export class ErroDaApi extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly codigo: string = 'erro',
    readonly detalhes?: unknown,
  ) {
    super(message);
    this.name = 'ErroDaApi';
  }
}

export const erroDeValidacao = (mensagem: string, detalhes?: unknown) =>
  new ErroDaApi(422, mensagem, 'validacao', detalhes);

export const naoAutenticado = (mensagem = 'Entre na sua conta para continuar.') =>
  new ErroDaApi(401, mensagem, 'nao_autenticado');

export const semPermissao = (mensagem = 'Você não tem acesso a isso.') =>
  new ErroDaApi(403, mensagem, 'sem_permissao');

export const naoEncontrado = (mensagem = 'Não encontramos o que você procura.') =>
  new ErroDaApi(404, mensagem, 'nao_encontrado');

export const conflito = (mensagem: string) => new ErroDaApi(409, mensagem, 'conflito');

/** Falha ao falar com um serviço externo (pagar.me, Google). */
export class ErroDeIntegracao extends ErroDaApi {
  constructor(
    readonly servico: string,
    readonly statusOriginal: number,
    readonly respostaOriginal: unknown,
  ) {
    super(
      502,
      `Não conseguimos concluir a operação com ${servico}. Tente de novo em instantes.`,
      'integracao',
      { servico, statusOriginal: statusOriginal, resposta: respostaOriginal },
    );
    this.name = 'ErroDeIntegracao';
  }
}
