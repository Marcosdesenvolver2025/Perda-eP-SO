// Monta o "copia e cola" do Pix (o BR Code do Banco Central) a partir da sua
// chave. É o mesmo texto que vira QR Code na tela.
//
// O padrão é uma sequência de campos no formato ID + tamanho + valor. No fim
// vem um CRC16 que o aplicativo do banco confere: um caractere trocado e o
// código é recusado, então a montagem tem que ser exata.

// Tira acento e símbolo: o BR Code só aceita caracteres simples nos campos
// de nome e cidade, e banco nenhum perdoa um "ç" ali.
function simplificar(texto, limite) {
  return String(texto || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^A-Za-z0-9 ]/g, '')
    .trim()
    .toUpperCase()
    .slice(0, limite);
}

function campo(id, valor) {
  const conteudo = String(valor);
  return `${id}${String(conteudo.length).padStart(2, '0')}${conteudo}`;
}

// CRC16-CCITT (polinômio 0x1021, valor inicial 0xFFFF), como manda o manual
// do BR Code.
function crc16(texto) {
  let crc = 0xffff;
  for (let i = 0; i < texto.length; i++) {
    crc ^= texto.charCodeAt(i) << 8;
    for (let bit = 0; bit < 8; bit++) {
      crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

// Identificador da compra. Vai dentro do próprio Pix, então aparece no seu
// extrato — é por ele que você liga o dinheiro recebido ao pedido.
export function novoIdentificador() {
  const agora = Date.now().toString(36).toUpperCase();
  const sorteio = Math.random().toString(36).slice(2, 6).toUpperCase();
  return `CAP${agora}${sorteio}`.slice(0, 25);
}

export function montarCopiaECola({ chave, nome, cidade, centavos, identificador }) {
  if (!chave) throw new Error('Chave Pix não configurada em conteudo/catalogo.json.');

  const valor = (centavos / 100).toFixed(2);

  const contaDoRecebedor =
    campo('00', 'br.gov.bcb.pix') + campo('01', String(chave).trim());

  const dadosAdicionais = campo('05', identificador || '***');

  const semCrc =
    campo('00', '01') +          // versão do formato
    campo('01', '12') +          // uso único: cada compra tem seu identificador
    campo('26', contaDoRecebedor) +
    campo('52', '0000') +        // categoria do estabelecimento
    campo('53', '986') +         // moeda: real
    campo('54', valor) +
    campo('58', 'BR') +
    campo('59', simplificar(nome, 25) || 'RECEBEDOR') +
    campo('60', simplificar(cidade, 15) || 'BRASIL') +
    campo('62', dadosAdicionais) +
    '6304';                      // cabeçalho do CRC entra no cálculo

  return semCrc + crc16(semCrc);
}

// ---------------------------------------------------------------------------
// Confirmação automática (opcional)
// ---------------------------------------------------------------------------
// Chave Pix avulsa não avisa o site quando o dinheiro cai — quem confere é
// você, no aplicativo do banco. Se um dia essa mesma chave estiver numa conta
// Mercado Pago, defina MP_ACCESS_TOKEN e a conferência passa a ser automática
// pela função `conferir-pagamento`.

export const confirmacaoAutomatica = () => Boolean(process.env.MP_ACCESS_TOKEN);

export async function consultarNoMercadoPago(id) {
  const resposta = await fetch(`https://api.mercadopago.com/v1/payments/${encodeURIComponent(id)}`, {
    headers: { Authorization: `Bearer ${process.env.MP_ACCESS_TOKEN}` },
  });
  const corpo = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(corpo?.message || `Mercado Pago respondeu ${resposta.status}.`);
  }
  return {
    pago: corpo.status === 'approved',
    situacao: corpo.status,
    email: corpo?.payer?.email || null,
  };
}
