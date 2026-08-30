/**
 * Fechamento da compra.
 *
 * Aqui a pessoa escolhe como quer receber (entregador nosso ou combinar com o
 * vendedor) e como quer pagar. O resumo mostra o total antes de confirmar.
 *
 * O cartão é tokenizado no aparelho com a CHAVE PÚBLICA da pagar.me: o número
 * do cartão nunca passa pelo nosso servidor.
 */

import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { Anuncio, ModalidadeEntrega, ResumoDaCompra } from '../api/tipos';
import { Aviso, Botao, Carregando, Separador } from '../componentes/base';
import { SeloGarantia } from '../componentes/produto';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio, sombraFlutuante } from '../tema';
import { reais } from '../util/formato';
import { COMISSAO, calcularDescontos } from '../regras/limites';
import { tokenizarCartao, type DadosDoCartao } from '../pagamento/cartao';
import { FormularioDeCartao } from '../componentes/cartao';

type Props = NativeStackScreenProps<ParametrosApp, 'Checkout'>;

type FormaDePagamento = 'pix' | 'credit_card';

export function TelaCheckout({ navigation, route }: Props) {
  const [anuncio, setAnuncio] = useState<Anuncio | null>(null);
  // a modalidade vem do anúncio: quem escolhe é o vendedor
  const modalidade: ModalidadeEntrega = anuncio?.modalidadeEntrega ?? 'PLATAFORMA';
  const [forma, setForma] = useState<FormaDePagamento>('pix');
  const [cartao, setCartao] = useState<DadosDoCartao | null>(null);
  const [parcelas, setParcelas] = useState(1);
  const [resumo, setResumo] = useState<ResumoDaCompra | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [pagando, setPagando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    async function carregar() {
      try {
        setAnuncio(await api<Anuncio>(`/anuncios/${route.params.anuncioId}`, { publico: true }));
      } finally {
        setCarregando(false);
      }
    }
    void carregar();
  }, [route.params.anuncioId]);

  useEffect(() => {
    async function simular() {
      if (!anuncio) return;


      try {
        setResumo(await api<ResumoDaCompra>(
          `/pedidos/simular?anuncio=${anuncio.id}` +
            (route.params.ofertaId ? `&oferta=${route.params.ofertaId}` : ''),
        ));
      } catch (e) {
        setErro((e as Error).message);
      }
    }
    void simular();
  }, [anuncio, modalidade]);

  async function confirmar() {
    if (!anuncio) return;
    setErro(null);

    setPagando(true);
    try {
      let pagamento: Record<string, unknown> = { tipo: 'pix' };

      if (forma === 'credit_card') {
        if (!cartao) throw new Error('Preencha os dados do cartão.');
        // o cartão vira token no próprio aparelho, direto com a pagar.me.
        // Na demonstração não existe chave nem cobrança: o cartão nem sai daqui.
        const tokenCartao = MODO_DEMONSTRACAO
          ? 'token_demonstracao'
          : await tokenizarCartao(cartao);
        pagamento = { tipo: 'credit_card', tokenCartao, parcelas };
      }

      const resposta = await api<{ pedido: { id: string }; pix: { qrCode: string } | null }>(
        '/pedidos',
        {
          metodo: 'POST',
          corpo: {
            anuncioId: anuncio.id,
            // quando a compra vem de uma oferta aceita, o servidor cobra o
            // valor combinado em vez do preço do anúncio
            ...(route.params.ofertaId ? { ofertaId: route.params.ofertaId } : {}),
            pagamento,
            // o endereço é escolhido na tela de endereços; aqui vai o principal
            ...(modalidade === 'PLATAFORMA' ? { enderecoId: route.params.enderecoId } : {}),
          },
        },
      );

      navigation.replace('Pedido', {
        id: resposta.pedido.id,
        pixQrCode: resposta.pix?.qrCode ?? undefined,
      });
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setPagando(false);
    }
  }

  if (carregando || !anuncio) return <Carregando />;

  const comEntregador = modalidade === 'PLATAFORMA';

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="chevron-back" size={26} color={cores.verdeEscuro} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>finalizar compra</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: 140 }}>
        {erro ? <Aviso texto={erro} tom="alerta" /> : null}

        <Text style={[fonte.rotulo, { marginBottom: espaco.md }]}>como você recebe</Text>

        <View style={e.modalidade}>
          <Ionicons
            name={comEntregador ? 'bicycle' : 'walk'}
            size={22}
            color={cores.verdeEscuro}
          />
          <View style={{ flex: 1, marginLeft: espaco.md }}>
            <Text style={fonte.rotulo}>
              {comEntregador ? 'entrega pelo vendas itinga' : 'entrega pelo vendedor'}
            </Text>
            <Text style={fonte.pequeno}>
              {comEntregador
                ? 'a gente busca com o vendedor e leva até você, sem custo extra'
                : 'quem vende combina com você onde e quando entregar'}
            </Text>
          </View>
        </View>

        <View style={{ height: espaco.xl }} />
        <SeloGarantia dias={anuncio.entrega?.diasParaTestar ?? 7} />

        <View style={{ height: espaco.xl }} />
        <Text style={[fonte.rotulo, { marginBottom: espaco.md }]}>como você quer pagar</Text>

        <OpcaoDeEntrega
          selecionada={forma === 'pix'}
          icone="qr-code"
          titulo="pix"
          descricao="aprovação na hora"
          aoTocar={() => setForma('pix')}
        />
        <OpcaoDeEntrega
          selecionada={forma === 'credit_card'}
          icone="card"
          titulo="cartão de crédito"
          descricao="parcele em até 12x"
          aoTocar={() => setForma('credit_card')}
        />

        {forma === 'credit_card' ? (
          <View style={{ marginTop: espaco.lg }}>
            <FormularioDeCartao
              valor={cartao}
              aoMudar={setCartao}
              total={resumo?.valorTotal ?? anuncio.preco}
              parcelas={parcelas}
              aoMudarParcelas={setParcelas}
            />
          </View>
        ) : null}

        <View style={{ height: espaco.xl }} />
        <Separador />
        <View style={{ height: espaco.lg }} />

        <Text style={[fonte.rotulo, { marginBottom: espaco.md }]}>resumo</Text>
        <LinhaDeValor rotulo="produto" valor={resumo?.valorProduto ?? anuncio.preco} />
        {comEntregador ? (
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 }}>
            <Text style={fonte.corpo}>entrega</Text>
            <Text style={[fonte.corpo, { color: cores.verdeEscuro }]}>incluída</Text>
          </View>
        ) : null}
        <View style={{ height: espaco.sm }} />
        <LinhaDeValor rotulo="total" valor={resumo?.valorTotal ?? anuncio.preco} destaque />

        <Text style={[fonte.pequeno, { marginTop: espaco.lg }]}>
          o dinheiro fica guardado com a gente até você confirmar que está tudo certo. o
          vendedor só recebe depois dos {anuncio.entrega?.diasParaTestar ?? 7} dias de teste.
        </Text>
      </ScrollView>

      <View style={e.rodape}>
        <Botao
          titulo={forma === 'pix' ? 'gerar pix' : 'pagar agora'}
          aoTocar={confirmar}
          carregando={pagando}
        />
      </View>
    </SafeAreaView>
  );
}

function OpcaoDeEntrega({
  selecionada,
  icone,
  titulo,
  descricao,
  aoTocar,
  desabilitada,
}: {
  selecionada: boolean;
  icone: keyof typeof Ionicons.glyphMap;
  titulo: string;
  descricao: string;
  aoTocar: () => void;
  desabilitada?: boolean;
}) {
  return (
    <Pressable
      onPress={aoTocar}
      disabled={desabilitada}
      accessibilityRole="radio"
      accessibilityState={{ selected: selecionada, disabled: !!desabilitada }}
      style={[
        e.opcao,
        selecionada && { borderColor: cores.verde, backgroundColor: cores.verdeClaro },
        desabilitada && { opacity: 0.4 },
      ]}
    >
      <Ionicons name={icone} size={22} color={cores.verdeEscuro} />
      <View style={{ flex: 1, marginLeft: espaco.md }}>
        <Text style={fonte.rotulo}>{titulo}</Text>
        <Text style={fonte.pequeno}>{descricao}</Text>
      </View>
      <Ionicons
        name={selecionada ? 'radio-button-on' : 'radio-button-off'}
        size={20}
        color={selecionada ? cores.verde : cores.textoFraco}
      />
    </Pressable>
  );
}

function LinhaDeValor({
  rotulo,
  valor,
  destaque,
}: {
  rotulo: string;
  valor: number;
  destaque?: boolean;
}) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 }}>
      <Text style={destaque ? fonte.rotulo : fonte.corpo}>{rotulo}</Text>
      <Text style={destaque ? [fonte.rotulo, { fontSize: 17 }] : fonte.corpo}>{reais(valor)}</Text>
    </View>
  );
}

const e = StyleSheet.create({
  topo: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: espaco.lg,
    paddingBottom: espaco.md,
  },
  opcao: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: cores.borda,
    borderRadius: raio.md,
    padding: espaco.lg,
    marginBottom: espaco.md,
  },
  modalidade: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: cores.verdeClaro,
    borderRadius: raio.md,
    padding: espaco.lg,
  },
  rodape: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    padding: espaco.lg,
    paddingBottom: espaco.xl,
    backgroundColor: cores.branco,
    ...(sombraFlutuante as object),
  },
});
