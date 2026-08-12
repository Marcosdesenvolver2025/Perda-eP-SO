/**
 * Telas de cadastro: dados pessoais, endereços, conta de recebimento,
 * confirmação de entrega (Android) e a central de ajuda.
 */

import React, { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api } from '../api/cliente';
import { Aviso, Botao, Campo, Cartao, Separador, TelaVazia } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import {
  ALTURA_MAXIMA_CM,
  COMISSAO,
  DIAS_PARA_CONFIRMACAO_AUTOMATICA,
  DIAS_PARA_TESTAR,
  LARGURA_MAXIMA_CM,
  PESO_MAXIMO_G,
  TABELA_DE_TARIFAS,
  VALOR_MINIMO_VENDA,
} from '../regras/limites';

function Cabecalho({ titulo, aoVoltar }: { titulo: string; aoVoltar: () => void }) {
  return (
    <View style={e.topo}>
      <Pressable onPress={aoVoltar} hitSlop={12} accessibilityLabel="voltar">
        <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
      </Pressable>
      <Text style={[fonte.secao, { marginLeft: espaco.md }]}>{titulo}</Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// dados pessoais
// ---------------------------------------------------------------------------

export function TelaDadosPessoais({
  navigation,
}: NativeStackScreenProps<ParametrosApp, 'DadosPessoais'>) {
  const { usuario, atualizarPerfil } = useAutenticacao();
  const [nome, setNome] = useState(usuario?.nome ?? '');
  const [telefone, setTelefone] = useState(usuario?.telefone ?? '');
  const [cpf, setCpf] = useState('');
  const [bairro, setBairro] = useState(usuario?.bairro ?? '');
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [salvo, setSalvo] = useState(false);

  async function salvar() {
    setErro(null);
    setSalvo(false);
    setSalvando(true);
    try {
      await atualizarPerfil({
        nome: nome.trim(),
        telefone: telefone.replace(/\D/g, '') || undefined,
        bairro: bairro.trim() || undefined,
        // o CPF é exigido pela pagar.me para cobrar e para repassar
        ...(cpf.replace(/\D/g, '').length === 11 ? { cpf: cpf.replace(/\D/g, '') } : {}),
      } as never);
      setSalvo(true);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setSalvando(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Cabecalho titulo="dados pessoais" aoVoltar={navigation.goBack} />
      <ScrollView contentContainerStyle={{ padding: espaco.lg }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}
        {salvo ? <Aviso texto="tudo salvo!" tom="sucesso" /> : null}

        <Campo rotulo="nome" valor={nome} aoMudar={setNome} dica="seu nome" />
        <Campo
          rotulo="e-mail"
          valor={usuario?.email ?? ''}
          aoMudar={() => undefined}
          ajuda="vem da sua conta google e não pode ser alterado por aqui"
        />
        <Campo
          rotulo="celular"
          valor={telefone}
          aoMudar={setTelefone}
          dica="(99) 99999-9999"
          teclado="phone-pad"
          ajuda="o entregador usa pra falar com você"
        />
        <Campo
          rotulo="cpf"
          valor={cpf}
          aoMudar={setCpf}
          dica="000.000.000-00"
          teclado="numeric"
          ajuda="obrigatório para comprar e para receber suas vendas"
        />
        <Campo rotulo="bairro" valor={bairro} aoMudar={setBairro} dica="centro" />

        <Botao titulo="salvar" aoTocar={salvar} carregando={salvando} />
      </ScrollView>
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------
// endereços
// ---------------------------------------------------------------------------

interface Endereco {
  id: string;
  apelido?: string | null;
  cep: string;
  logradouro: string;
  numero: string;
  complemento?: string | null;
  bairro: string;
  referencia?: string | null;
  principal: boolean;
}

export function TelaEnderecos({
  navigation,
}: NativeStackScreenProps<ParametrosApp, 'Enderecos'>) {
  const [enderecos, setEnderecos] = useState<Endereco[]>([]);
  const [criando, setCriando] = useState(false);
  const [cep, setCep] = useState('');
  const [logradouro, setLogradouro] = useState('');
  const [numero, setNumero] = useState('');
  const [complemento, setComplemento] = useState('');
  const [bairro, setBairro] = useState('');
  const [referencia, setReferencia] = useState('');
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    try {
      const resposta = await api<{ itens: Endereco[] }>('/conta/enderecos');
      setEnderecos(resposta.itens);
    } catch {
      setEnderecos([]);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void carregar();
    }, [carregar]),
  );

  async function salvar() {
    setErro(null);
    try {
      await api('/conta/enderecos', {
        metodo: 'POST',
        corpo: {
          cep: cep.replace(/\D/g, ''),
          logradouro: logradouro.trim(),
          numero: numero.trim(),
          complemento: complemento.trim() || undefined,
          bairro: bairro.trim(),
          referencia: referencia.trim() || undefined,
          principal: enderecos.length === 0,
        },
      });
      setCriando(false);
      setCep('');
      setLogradouro('');
      setNumero('');
      setComplemento('');
      setBairro('');
      setReferencia('');
      await carregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Cabecalho titulo="endereços" aoVoltar={navigation.goBack} />

      <ScrollView contentContainerStyle={{ padding: espaco.lg, flexGrow: 1 }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {criando ? (
          <View>
            <Campo rotulo="cep" valor={cep} aoMudar={setCep} dica="00000-000" teclado="numeric" />
            <Campo rotulo="rua" valor={logradouro} aoMudar={setLogradouro} dica="rua das flores" />
            <View style={{ flexDirection: 'row', gap: espaco.md }}>
              <View style={{ flex: 1 }}>
                <Campo rotulo="número" valor={numero} aoMudar={setNumero} dica="123" />
              </View>
              <View style={{ flex: 2 }}>
                <Campo
                  rotulo="complemento"
                  valor={complemento}
                  aoMudar={setComplemento}
                  dica="apto, bloco..."
                />
              </View>
            </View>
            <Campo rotulo="bairro" valor={bairro} aoMudar={setBairro} dica="centro" />
            <Campo
              rotulo="ponto de referência"
              valor={referencia}
              aoMudar={setReferencia}
              dica="perto da praça, portão azul..."
              ajuda="ajuda muito o entregador a te achar"
            />

            <Botao titulo="salvar endereço" aoTocar={salvar} />
            <Botao titulo="cancelar" variante="texto" aoTocar={() => setCriando(false)} />
          </View>
        ) : enderecos.length === 0 ? (
          <TelaVazia
            icone="location-outline"
            titulo="nenhum endereço salvo"
            descricao="cadastre onde você quer receber suas compras."
            acao={{ titulo: 'adicionar endereço', aoTocar: () => setCriando(true) }}
          />
        ) : (
          <>
            {enderecos.map((endereco) => (
              <Cartao key={endereco.id} estilo={{ marginBottom: espaco.md }}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Ionicons name="location" size={20} color={cores.verdeEscuro} />
                  <Text style={[fonte.rotulo, { marginLeft: espaco.sm, flex: 1 }]}>
                    {endereco.apelido ?? endereco.bairro.toLowerCase()}
                  </Text>
                  {endereco.principal ? (
                    <Text style={{ fontSize: 11, color: cores.verdeProfundo, fontWeight: '700' }}>
                      principal
                    </Text>
                  ) : null}
                </View>
                <Text style={[fonte.corpo, { marginTop: espaco.sm }]}>
                  {endereco.logradouro}, {endereco.numero}
                  {endereco.complemento ? ` — ${endereco.complemento}` : ''}
                </Text>
                <Text style={fonte.pequeno}>
                  {endereco.bairro} · Itinga
                </Text>
                {endereco.referencia ? (
                  <Text style={[fonte.pequeno, { fontStyle: 'italic', marginTop: 2 }]}>
                    {endereco.referencia}
                  </Text>
                ) : null}
              </Cartao>
            ))}
            <Botao titulo="adicionar endereço" icone="add" aoTocar={() => setCriando(true)} />
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------
// conta de recebimento (recebedor da pagar.me)
// ---------------------------------------------------------------------------

export function TelaContaDeRecebimento({
  navigation,
}: NativeStackScreenProps<ParametrosApp, 'ContaDeRecebimento'>) {
  const { usuario, recarregar } = useAutenticacao();
  const [titular, setTitular] = useState(usuario?.nome ?? '');
  const [documento, setDocumento] = useState('');
  const [banco, setBanco] = useState('');
  const [agencia, setAgencia] = useState('');
  const [conta, setConta] = useState('');
  const [contaDigito, setContaDigito] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const jaCadastrado = usuario?.recebedor != null;

  async function cadastrar() {
    setErro(null);

    setEnviando(true);
    try {
      await api('/recebedores', {
        metodo: 'POST',
        corpo: {
          documento: documento.replace(/\D/g, ''),
          tipo: documento.replace(/\D/g, '').length === 14 ? 'company' : 'individual',
          titular: titular.trim(),
          banco: {
            codigo: banco.trim(),
            agencia: agencia.replace(/\D/g, ''),
            conta: conta.replace(/\D/g, ''),
            contaDigito: contaDigito.trim(),
            tipoConta: 'checking',
          },
        },
      });
      await recarregar();
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Cabecalho titulo="conta pra receber" aoVoltar={navigation.goBack} />

      <ScrollView contentContainerStyle={{ padding: espaco.lg }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        {jaCadastrado ? (
          <>
            <Aviso
              tom={usuario?.recebedor === 'ATIVO' ? 'sucesso' : 'informacao'}
              texto={
                usuario?.recebedor === 'ATIVO'
                  ? 'sua conta está ativa. o valor das vendas cai direto nela.'
                  : usuario?.recebedor === 'PENDENTE'
                    ? 'sua conta está em análise pela pagar.me. costuma levar até 1 dia útil.'
                    : 'sua conta foi recusada ou bloqueada. fale com a gente pelo suporte.'
              }
            />
            <Cartao estilo={{ marginTop: espaco.lg }}>
              <Text style={fonte.rotulo}>como funciona o repasse</Text>
              <Text style={[fonte.pequeno, { marginTop: espaco.sm }]}>
                a cada venda a gente separa a sua parte na hora do pagamento. o valor fica
                guardado durante os {DIAS_PARA_TESTAR} dias em que o comprador pode testar o
                produto e, passado esse prazo, cai na sua conta automaticamente.
              </Text>
            </Cartao>
          </>
        ) : (
          <>
            <Text style={[fonte.pequeno, { marginBottom: espaco.lg }]}>
              a conta precisa estar no seu nome (mesmo CPF ou CNPJ). é para ela que a gente
              manda o dinheiro das suas vendas.
            </Text>

            <Campo rotulo="titular da conta" valor={titular} aoMudar={setTitular} dica="nome completo" />
            <Campo
              rotulo="cpf ou cnpj"
              valor={documento}
              aoMudar={setDocumento}
              dica="só números"
              teclado="numeric"
            />
            <Campo
              rotulo="código do banco"
              valor={banco}
              aoMudar={setBanco}
              dica="ex.: 001, 237, 260"
              teclado="numeric"
              maxLength={3}
              ajuda="3 dígitos — procure por 'código do banco' no seu app bancário"
            />
            <Campo rotulo="agência" valor={agencia} aoMudar={setAgencia} dica="0000" teclado="numeric" />
            <View style={{ flexDirection: 'row', gap: espaco.md }}>
              <View style={{ flex: 3 }}>
                <Campo rotulo="conta" valor={conta} aoMudar={setConta} dica="00000" teclado="numeric" />
              </View>
              <View style={{ flex: 1 }}>
                <Campo
                  rotulo="dígito"
                  valor={contaDigito}
                  aoMudar={setContaDigito}
                  dica="0"
                  maxLength={2}
                />
              </View>
            </View>

            <Botao titulo="cadastrar conta" aoTocar={cadastrar} carregando={enviando} />
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------
// como funciona (central de ajuda)
// ---------------------------------------------------------------------------

const perguntas = [
  {
    titulo: 'como eu vendo?',
    texto:
      'tire fotos, coloque o preço e publique. quando alguém comprar, a gente avisa. você entrega ou chama nosso entregador.',
  },
  {
    titulo: 'quanto vocês cobram?',
    texto: `depende de quem entrega. se você entregar por conta própria, são só ${Math.round(
      COMISSAO * 100,
    )}% de comissão. se a entrega for pela plataforma, são ${Math.round(
      COMISSAO * 100,
    )}% mais uma tarifa fixa por faixa de preço: ${TABELA_DE_TARIFAS.map((f) =>
      Number.isFinite(f.ateInclusive)
        ? `até R$ ${(f.ateInclusive / 100).toFixed(2).replace('.', ',')} são R$ ${(f.tarifa / 100).toFixed(2).replace('.', ',')}`
        : `de R$ 500,00 pra cima são R$ ${(f.tarifa / 100).toFixed(2).replace('.', ',')}`,
    ).join('; ')}. sem mensalidade e sem taxa pra anunciar.`,
  },
  {
    titulo: 'quando eu recebo o dinheiro?',
    texto: `${DIAS_PARA_TESTAR} dias depois que o produto chega ao comprador. esse é o prazo que ele tem para testar e pedir devolução; passou o prazo, o valor cai na sua conta.`,
  },
  {
    titulo: 'qual o tamanho máximo?',
    texto: `o limite vale só para a entrega pela plataforma: até ${PESO_MAXIMO_G / 1000} kg, ${LARGURA_MAXIMA_CM} cm de largura e ${ALTURA_MAXIMA_CM} cm de altura. escolhendo entregar por conta própria, não há limite.`,
  },
  {
    titulo: 'posso devolver o que comprei?',
    texto: `pode, dentro de ${DIAS_PARA_TESTAR} dias contados da chegada. é só abrir o pedido no app e pedir devolução.`,
  },
  {
    titulo: 'o frete volta no reembolso?',
    texto: `volta sim. dentro dos ${DIAS_PARA_TESTAR} dias você recebe de volta tudo que pagou, inclusive o frete — é o que garante o artigo 49 do código de defesa do consumidor.`,
  },
  {
    titulo: 'e se eu vender e o comprador devolver?',
    texto:
      'você devolve só o valor que recebeu pela venda. a comissão e o frete são absorvidos pelo vendas itinga, não saem do seu bolso.',
  },
  {
    titulo: 'como eu recebo o produto?',
    texto:
      'o entregador busca no vendedor e leva até seu endereço. na hora, mostre o código de 4 dígitos que aparece no seu pedido — é ele que confirma a entrega.',
  },
  {
    titulo: 'a entrega é cobrada à parte?',
    texto:
      'não. em qualquer modalidade o comprador paga só o preço do produto.',
  },
  {
    titulo: 'como funciona quando o vendedor entrega?',
    texto: `vocês combinam onde e quando. na hora, o comprador informa o código de 4 dígitos que aparece no app dele e o vendedor digita para registrar a entrega. se o comprador sumir, o vendedor pode declarar a entrega e ela é confirmada automaticamente em ${DIAS_PARA_CONFIRMACAO_AUTOMATICA} dias.`,
  },
  {
    titulo: 'tem valor mínimo pra anunciar?',
    texto: `tem: R$ ${(VALOR_MINIMO_VENDA / 100).toFixed(2).replace('.', ',')}.`,
  },
];

export function TelaComoFunciona({
  navigation,
}: NativeStackScreenProps<ParametrosApp, 'ComoFunciona'>) {
  const [aberta, setAberta] = useState<number | null>(0);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <Cabecalho titulo="como funciona" aoVoltar={navigation.goBack} />

      <ScrollView contentContainerStyle={{ paddingBottom: espaco.xxl }}>
        <View style={e.faixaAjuda}>
          <Text style={{ color: cores.branco, fontSize: 18, fontWeight: '700' }}>
            comprar e vender entre vizinhos
          </Text>
          <Text style={{ color: cores.branco, marginTop: espaco.sm, fontSize: 14 }}>
            tudo dentro de Itinga, com entrega local e {DIAS_PARA_TESTAR} dias pra testar.
          </Text>
        </View>

        {perguntas.map((pergunta, indice) => (
          <React.Fragment key={pergunta.titulo}>
            <Pressable
              onPress={() => setAberta(aberta === indice ? null : indice)}
              style={e.pergunta}
              accessibilityRole="button"
              accessibilityState={{ expanded: aberta === indice }}
            >
              <Text style={[fonte.rotulo, { flex: 1 }]}>{pergunta.titulo}</Text>
              <Ionicons
                name={aberta === indice ? 'chevron-up' : 'chevron-down'}
                size={20}
                color={cores.verdeEscuro}
              />
            </Pressable>
            {aberta === indice ? (
              <Text style={[fonte.corpo, { paddingHorizontal: espaco.lg, paddingBottom: espaco.lg }]}>
                {pergunta.texto}
              </Text>
            ) : null}
            <Separador />
          </React.Fragment>
        ))}

        <Text style={[fonte.pequeno, { textAlign: 'center', padding: espaco.xl }]}>
          ainda com dúvida? fale com a gente: suporte@vendasitinga.com.br
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const e = StyleSheet.create({
  topo: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: espaco.lg,
    paddingBottom: espaco.md,
  },
  faixaAjuda: {
    backgroundColor: cores.verde,
    padding: espaco.xl,
    margin: espaco.lg,
    borderRadius: raio.lg,
  },
  pergunta: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: espaco.lg,
  },
});
