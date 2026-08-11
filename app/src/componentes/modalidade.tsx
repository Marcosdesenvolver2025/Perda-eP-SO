/**
 * Comparação das duas modalidades de entrega, para o vendedor escolher com o
 * número na frente.
 *
 * A ideia é simples: mostrar lado a lado quanto ele recebe entregando por
 * conta própria e quanto recebe deixando com a gente, antes de decidir. Se o
 * pacote não couber na nossa entrega, a opção aparece bloqueada com o motivo.
 */

import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { cores, espaco, fonte, raio } from '../tema';
import { reais } from '../util/formato';
import {
  calcularDescontos,
  COMISSAO,
  VALOR_MINIMO_VENDA,
  type ModalidadeEntrega,
} from '../regras/limites';

interface Props {
  preco: number;
  selecionada: ModalidadeEntrega;
  aoEscolher: (modalidade: ModalidadeEntrega) => void;
  /** Quando o pacote não cabe na entrega da plataforma. */
  bloqueioDaPlataforma?: string[] | null;
}

export function EscolhaDeModalidade({
  preco,
  selecionada,
  aoEscolher,
  bloqueioDaPlataforma,
}: Props) {
  const temPreco = preco >= VALOR_MINIMO_VENDA;
  const plataformaBloqueada = !!bloqueioDaPlataforma?.length;

  const daPlataforma = temPreco ? calcularDescontos(preco, 'PLATAFORMA') : null;
  const doVendedor = temPreco ? calcularDescontos(preco, 'VENDEDOR') : null;

  return (
    <View>
      <Text style={[fonte.rotulo, { marginBottom: 4 }]}>quem entrega?</Text>
      <Text style={[fonte.pequeno, { marginBottom: espaco.md }]}>
        a taxa muda conforme a escolha — compare antes de decidir
      </Text>

      <Opcao
        titulo="entrega pela plataforma"
        descricao="a gente busca na sua casa e leva ao comprador"
        icone="bicycle"
        selecionada={selecionada === 'PLATAFORMA'}
        bloqueada={plataformaBloqueada}
        motivosDoBloqueio={bloqueioDaPlataforma ?? []}
        aoTocar={() => aoEscolher('PLATAFORMA')}
        taxa={`${Math.round(COMISSAO * 100)}% + tarifa fixa`}
        descontos={daPlataforma}
      />

      <Opcao
        titulo="entrega por sua conta"
        descricao="você combina com o comprador e entrega"
        icone="walk"
        selecionada={selecionada === 'VENDEDOR'}
        aoTocar={() => aoEscolher('VENDEDOR')}
        taxa={`só ${Math.round(COMISSAO * 100)}%, sem tarifa`}
        descontos={doVendedor}
      />

      {temPreco && daPlataforma && doVendedor ? (
        <View style={e.comparacao}>
          <Ionicons name="information-circle-outline" size={18} color={cores.verdeProfundo} />
          <Text style={[fonte.pequeno, { flex: 1, marginLeft: espaco.sm, color: cores.verdeProfundo }]}>
            entregando por conta própria você fica com{' '}
            <Text style={{ fontWeight: '700' }}>
              {reais(doVendedor.vendedor - daPlataforma.vendedor)} a mais
            </Text>
            {' '}nesta venda — mas precisa levar o produto até o comprador.
          </Text>
        </View>
      ) : null}
    </View>
  );
}

function Opcao({
  titulo,
  descricao,
  icone,
  selecionada,
  bloqueada,
  motivosDoBloqueio = [],
  aoTocar,
  taxa,
  descontos,
}: {
  titulo: string;
  descricao: string;
  icone: keyof typeof Ionicons.glyphMap;
  selecionada: boolean;
  bloqueada?: boolean;
  motivosDoBloqueio?: string[];
  aoTocar: () => void;
  taxa: string;
  descontos: ReturnType<typeof calcularDescontos> | null;
}) {
  return (
    <Pressable
      onPress={aoTocar}
      disabled={bloqueada}
      accessibilityRole="radio"
      accessibilityState={{ selected: selecionada, disabled: !!bloqueada }}
      style={[
        e.opcao,
        selecionada && !bloqueada && { borderColor: cores.verde, backgroundColor: cores.verdeClaro },
        bloqueada && { opacity: 0.6, borderStyle: 'dashed' },
      ]}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <Ionicons
          name={icone}
          size={22}
          color={bloqueada ? cores.textoFraco : cores.verdeEscuro}
        />
        <View style={{ flex: 1, marginLeft: espaco.md }}>
          <Text style={fonte.rotulo}>{titulo}</Text>
          <Text style={fonte.pequeno}>{descricao}</Text>
        </View>
        {bloqueada ? (
          <Ionicons name="lock-closed" size={18} color={cores.textoFraco} />
        ) : (
          <Ionicons
            name={selecionada ? 'radio-button-on' : 'radio-button-off'}
            size={20}
            color={selecionada ? cores.verde : cores.textoFraco}
          />
        )}
      </View>

      {bloqueada ? (
        <View style={e.bloqueio}>
          <Text style={[fonte.pequeno, { color: cores.alerta }]}>
            esse produto não cabe na nossa entrega: {motivosDoBloqueio.join('; ')}.
          </Text>
          <Text style={[fonte.pequeno, { color: cores.alerta, marginTop: 4 }]}>
            escolha entregar por conta própria para anunciar.
          </Text>
        </View>
      ) : (
        <View style={e.taxas}>
          <Linha rotulo="taxa" valor={taxa} />
          {descontos ? (
            <>
              {descontos.tarifa > 0 ? (
                <>
                  <Linha rotulo="comissão" valor={`- ${reais(descontos.comissao)}`} />
                  <Linha rotulo="tarifa" valor={`- ${reais(descontos.tarifa)}`} />
                </>
              ) : (
                <Linha rotulo="comissão" valor={`- ${reais(descontos.comissao)}`} />
              )}
              <Linha rotulo="você recebe" valor={reais(descontos.vendedor)} destaque />
            </>
          ) : (
            <Text style={fonte.pequeno}>coloque o preço para ver quanto você recebe</Text>
          )}
        </View>
      )}
    </Pressable>
  );
}

function Linha({
  rotulo,
  valor,
  destaque,
}: {
  rotulo: string;
  valor: string;
  destaque?: boolean;
}) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 2 }}>
      <Text style={destaque ? fonte.rotulo : fonte.pequeno}>{rotulo}</Text>
      <Text style={destaque ? fonte.rotulo : fonte.pequeno}>{valor}</Text>
    </View>
  );
}

const e = StyleSheet.create({
  opcao: {
    borderWidth: 1.5,
    borderColor: cores.borda,
    borderRadius: raio.md,
    padding: espaco.lg,
    marginBottom: espaco.md,
  },
  taxas: {
    marginTop: espaco.md,
    paddingTop: espaco.md,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
  },
  bloqueio: {
    marginTop: espaco.md,
    paddingTop: espaco.md,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
  },
  comparacao: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: cores.verdeClaro,
    borderRadius: raio.md,
    padding: espaco.md,
    marginBottom: espaco.md,
  },
});
