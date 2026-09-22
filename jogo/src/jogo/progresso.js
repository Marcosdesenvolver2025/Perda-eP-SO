// O que fica guardado entre uma sessão e outra.
//
// Tudo no localStorage, num objeto só. Se der qualquer problema na leitura
// (navegador em modo privado, chave corrompida), voltamos ao começo em vez de
// quebrar o jogo — perder o progresso é ruim, não abrir é pior.

const CHAVE = 'volante:progresso:1';

export function padrao() {
  return {
    versao: 1,
    dinheiro: 1500,
    garagem: ['besouro'],
    carroAtual: 'besouro',
    carreira: { semente: Math.floor(Math.random() * 1e9), indice: 0 },
    estrelas: {},
    // O melhor placar de cada modo avulso. É o que faz voltar nele.
    recordes: { estacionamento: 0, rapido: 0, drift: 0 },
    // Melhorias por carro: { besouro: { motor: 3, freio: 1, pneu: 0, cambio: 2 } }.
    melhorias: {},
    ajustes: { qualidade: 'alta', som: true, camera: 'perseguicao', cambio: 'automatico' },
    numeros: { missoes: 0, metros: 0, batidas: 0, estrelas: 0 },
  };
}

export function carregar() {
  try {
    const bruto = localStorage.getItem(CHAVE);
    if (!bruto) return padrao();
    const dados = JSON.parse(bruto);
    if (!dados || dados.versao !== 1) return padrao();
    const base = padrao();
    return {
      ...base,
      ...dados,
      carreira: { ...base.carreira, ...(dados.carreira || {}) },
      ajustes: { ...base.ajustes, ...(dados.ajustes || {}) },
      numeros: { ...base.numeros, ...(dados.numeros || {}) },
      recordes: { ...base.recordes, ...(dados.recordes || {}) },
      melhorias: dados.melhorias || {},
      garagem: Array.isArray(dados.garagem) && dados.garagem.length ? dados.garagem : base.garagem,
      estrelas: dados.estrelas || {},
    };
  } catch {
    return padrao();
  }
}

export function salvar(progresso) {
  try {
    localStorage.setItem(CHAVE, JSON.stringify(progresso));
    return true;
  } catch {
    return false;
  }
}

export function apagar() {
  try { localStorage.removeItem(CHAVE); } catch { /* sem drama */ }
  return padrao();
}

export function temCarro(progresso, id) {
  return progresso.garagem.includes(id);
}

export function comprar(progresso, modelo) {
  if (temCarro(progresso, modelo.id)) return { ok: false, motivo: 'já é seu' };
  if (progresso.dinheiro < modelo.preco) return { ok: false, motivo: 'falta dinheiro' };
  progresso.dinheiro -= modelo.preco;
  progresso.garagem.push(modelo.id);
  progresso.carroAtual = modelo.id;
  salvar(progresso);
  return { ok: true };
}

export function registrarResultado(progresso, indiceMissao, resultado, partida) {
  const chave = String(indiceMissao);
  const anterior = progresso.estrelas[chave] || 0;

  if (resultado.sucesso) {
    progresso.dinheiro += resultado.premio;
    progresso.numeros.missoes++;
    if (resultado.estrelas > anterior) {
      progresso.numeros.estrelas += resultado.estrelas - anterior;
      progresso.estrelas[chave] = resultado.estrelas;
    }
    if (indiceMissao >= progresso.carreira.indice) {
      progresso.carreira.indice = indiceMissao + 1;
    }
  }
  progresso.numeros.metros += Math.round(partida.carro.distancia);
  progresso.numeros.batidas += partida.estatisticas.batidas;
  salvar(progresso);
  return progresso;
}

/**
 * Guarda o resultado de uma partida de modo avulso. Devolve se o placar é
 * recorde — é essa informação que a tela de resultado precisa mostrar, e ela
 * tem que ser lida ANTES de o recorde novo ser gravado.
 */
export function registrarModo(progresso, modo, resultado, partida) {
  const anterior = progresso.recordes[modo] || 0;
  const recorde = resultado.pontos > anterior;
  if (recorde) progresso.recordes[modo] = resultado.pontos;
  progresso.dinheiro += resultado.premio;
  progresso.numeros.metros += Math.round(partida.carro.distancia);
  progresso.numeros.batidas += partida.estatisticas.batidas;
  salvar(progresso);
  return { recorde, anterior };
}

export function totalDeEstrelas(progresso) {
  return Object.values(progresso.estrelas).reduce((a, b) => a + b, 0);
}
