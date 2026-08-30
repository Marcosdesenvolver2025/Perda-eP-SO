/**
 * Peças de interface reaproveitadas em todas as telas.
 *
 * O desenho segue o app de referência do vídeo: textos em caixa baixa, botões
 * em formato de pílula, carrosséis horizontais com título + subtítulo e um
 * botão vazado embaixo. O que era roxo virou verde #00DF13.
 */

import React from 'react';
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import { cores, espaco, fonte, raio, sombra, sombraCartao, sombraVerde } from '../tema';

// ---------------------------------------------------------------------------
// Botões
// ---------------------------------------------------------------------------

interface BotaoProps {
  titulo: string;
  aoTocar: () => void;
  variante?: 'cheio' | 'vazado' | 'texto';
  carregando?: boolean;
  desabilitado?: boolean;
  icone?: keyof typeof Ionicons.glyphMap;
  estilo?: StyleProp<ViewStyle>;
}

export function Botao({
  titulo,
  aoTocar,
  variante = 'cheio',
  carregando,
  desabilitado,
  icone,
  estilo,
}: BotaoProps) {
  const inativo = desabilitado || carregando;
  const cheio = variante === 'cheio';
  const corDoTexto = cheio ? cores.branco : cores.verdeProfundo;

  return (
    <Pressable
      onPress={aoTocar}
      disabled={inativo}
      accessibilityRole="button"
      accessibilityLabel={titulo}
      accessibilityState={{ disabled: !!inativo, busy: !!carregando }}
      style={({ pressed }) => [
        e.botao,
        cheio && [{ backgroundColor: cores.verde }, sombraVerde as object],
        variante === 'vazado' && {
          borderWidth: 1.5,
          borderColor: cores.borda,
          backgroundColor: cores.branco,
        },
        variante === 'texto' && {
          backgroundColor: 'transparent',
          paddingVertical: espaco.sm,
          minHeight: 0,
        },
        inativo && { opacity: 0.45, shadowOpacity: 0, elevation: 0 },
        // afundar um pouco ao tocar dá a sensação de botão físico; sem isso
        // o toque só "pisca" e a tela parece morta
        pressed && !inativo && { transform: [{ scale: 0.97 }], opacity: 0.92 },
        estilo,
      ]}
    >
      {carregando ? (
        <ActivityIndicator color={corDoTexto} />
      ) : (
        <View style={e.linhaCentro}>
          {icone ? (
            <Ionicons name={icone} size={18} color={corDoTexto} style={{ marginRight: 7 }} />
          ) : null}
          <Text style={[e.botaoTexto, { color: corDoTexto }]}>{titulo}</Text>
        </View>
      )}
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// Textos e blocos
// ---------------------------------------------------------------------------

export function Titulo({ children, estilo }: { children: React.ReactNode; estilo?: StyleProp<TextStyle> }) {
  return <Text style={[fonte.titulo, estilo]}>{children}</Text>;
}

export function CabecalhoSecao({
  titulo,
  subtitulo,
}: {
  titulo: string;
  subtitulo?: string;
}) {
  return (
    <View style={{ paddingHorizontal: espaco.lg, marginBottom: espaco.md }}>
      <Text style={fonte.secao}>{titulo}</Text>
      {subtitulo ? <Text style={[fonte.subtitulo, { marginTop: 2 }]}>{subtitulo}</Text> : null}
    </View>
  );
}

export function Cartao({
  children,
  estilo,
  aoTocar,
}: {
  children: React.ReactNode;
  estilo?: StyleProp<ViewStyle>;
  aoTocar?: () => void;
}) {
  const conteudo = <View style={[e.cartao, estilo]}>{children}</View>;
  if (!aoTocar) return conteudo;
  return (
    <Pressable onPress={aoTocar} style={({ pressed }) => pressed && { opacity: 0.9 }}>
      {conteudo}
    </Pressable>
  );
}

/** Etiqueta pequena: "novo", "usado", "18%", etc. */
export function Selo({
  texto,
  tom = 'verde',
}: {
  texto: string;
  /** `ambar` é para o que está pendente e tem prazo correndo. */
  tom?: 'verde' | 'cinza' | 'alerta' | 'ambar';
}) {
  const fundo =
    tom === 'verde'
      ? cores.verdeClaro
      : tom === 'alerta'
        ? '#FDECEA'
        : tom === 'ambar'
          ? cores.ambarClaro
          : cores.fundoCinza;
  const cor =
    tom === 'verde'
      ? cores.verdeProfundo
      : tom === 'alerta'
        ? cores.alerta
        : tom === 'ambar'
          ? cores.ambarEscuro
          : cores.textoSuave;
  return (
    <View style={[e.selo, { backgroundColor: fundo }]}>
      <Text style={{ fontSize: 11, fontWeight: '700', color: cor }}>{texto}</Text>
    </View>
  );
}

/** Linha de menu com ícone à direita, como na tela "minha conta". */
export function ItemDeMenu({
  titulo,
  descricao,
  icone,
  aoTocar,
  destaque,
}: {
  titulo: string;
  descricao?: string;
  icone: keyof typeof Ionicons.glyphMap;
  aoTocar: () => void;
  destaque?: boolean;
}) {
  return (
    <Pressable
      onPress={aoTocar}
      accessibilityRole="button"
      style={({ pressed }) => [e.itemMenu, pressed && { backgroundColor: cores.fundoCinza }]}
    >
      <View style={{ flex: 1 }}>
        <Text style={[fonte.corpo, destaque && { color: cores.alerta }]}>{titulo}</Text>
        {descricao ? <Text style={[fonte.pequeno, { marginTop: 2 }]}>{descricao}</Text> : null}
      </View>
      <Ionicons
        name={icone}
        size={20}
        color={destaque ? cores.alerta : cores.verdeEscuro}
      />
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// Entradas
// ---------------------------------------------------------------------------

interface CampoProps {
  rotulo?: string;
  valor: string;
  aoMudar: (valor: string) => void;
  dica?: string;
  ajuda?: string;
  erro?: string | null;
  multilinha?: boolean;
  teclado?: 'default' | 'numeric' | 'email-address' | 'phone-pad';
  maxLength?: number;
  sufixo?: string;
}

export function Campo({
  rotulo,
  valor,
  aoMudar,
  dica,
  ajuda,
  erro,
  multilinha,
  teclado = 'default',
  maxLength,
  sufixo,
}: CampoProps) {
  return (
    <View style={{ marginBottom: espaco.lg }}>
      {rotulo ? <Text style={[fonte.rotulo, { marginBottom: 6 }]}>{rotulo}</Text> : null}
      <View style={[e.campo, !!erro && { borderColor: cores.alerta }]}>
        <TextInput
          value={valor}
          onChangeText={aoMudar}
          placeholder={dica}
          placeholderTextColor={cores.textoFraco}
          keyboardType={teclado}
          multiline={multilinha}
          maxLength={maxLength}
          accessibilityLabel={rotulo}
          style={[
            { flex: 1, fontSize: 15, color: cores.texto, paddingVertical: 0 },
            multilinha && { minHeight: 96, textAlignVertical: 'top' },
          ]}
        />
        {sufixo ? <Text style={[fonte.pequeno, { marginLeft: 6 }]}>{sufixo}</Text> : null}
      </View>
      {erro ? (
        <Text style={[fonte.pequeno, { color: cores.alerta, marginTop: 4 }]}>{erro}</Text>
      ) : ajuda ? (
        <Text style={[fonte.pequeno, { marginTop: 4 }]}>{ajuda}</Text>
      ) : null}
    </View>
  );
}

/** Barra de busca do topo: "busque no vendas itinga". */
export function BarraDeBusca({
  valor,
  aoMudar,
  aoEnviar,
  aoTocar,
  somenteLeitura,
}: {
  valor?: string;
  aoMudar?: (v: string) => void;
  aoEnviar?: () => void;
  aoTocar?: () => void;
  somenteLeitura?: boolean;
}) {
  const conteudo = (
    <View style={e.busca}>
      <Ionicons name="search" size={18} color={cores.textoSuave} />
      <TextInput
        value={valor}
        onChangeText={aoMudar}
        onSubmitEditing={aoEnviar}
        editable={!somenteLeitura}
        pointerEvents={somenteLeitura ? 'none' : 'auto'}
        placeholder="busque no vendas itinga"
        placeholderTextColor={cores.textoSuave}
        returnKeyType="search"
        accessibilityLabel="buscar produtos"
        style={{ flex: 1, marginLeft: espaco.sm, fontSize: 15, color: cores.texto }}
      />
    </View>
  );

  if (!aoTocar) return conteudo;
  return (
    <Pressable onPress={aoTocar} accessibilityRole="search">
      {conteudo}
    </Pressable>
  );
}

/**
 * Categorias no topo da home.
 *
 * São chips preenchidos, não abas sublinhadas. Aba sublinhada é o padrão de
 * quase todo marketplace; o chip preenchido dá peso à categoria escolhida e
 * deixa a barra com cara própria — além de ser alvo maior para o polegar.
 */
export function AbasDeCategoria({
  itens,
  ativa,
  aoTrocar,
}: {
  itens: { chave: string; rotulo: string }[];
  ativa: string;
  aoTrocar: (chave: string) => void;
}) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={{
        paddingHorizontal: espaco.lg,
        gap: espaco.sm,
        paddingVertical: espaco.sm,
      }}
      style={{ flexGrow: 0 }}
    >
      {itens.map((item) => {
        const selecionada = item.chave === ativa;
        return (
          <Pressable
            key={item.chave}
            onPress={() => aoTrocar(item.chave)}
            accessibilityRole="tab"
            accessibilityState={{ selected: selecionada }}
            style={({ pressed }) => [
              e.chipCategoria,
              selecionada && e.chipCategoriaAtivo,
              pressed && { transform: [{ scale: 0.96 }] },
            ]}
          >
            <Text
              style={{
                fontSize: 14,
                fontWeight: '700',
                letterSpacing: -0.2,
                color: selecionada ? cores.branco : cores.textoSuave,
              }}
            >
              {item.rotulo}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

export function Carregando({ texto = 'carregando...' }: { texto?: string }) {
  return (
    <View style={e.centralizado}>
      <ActivityIndicator color={cores.verde} size="large" />
      <Text style={[fonte.subtitulo, { marginTop: espaco.md }]}>{texto}</Text>
    </View>
  );
}

export function TelaVazia({
  icone = 'file-tray-outline',
  titulo,
  descricao,
  acao,
}: {
  icone?: keyof typeof Ionicons.glyphMap;
  titulo: string;
  descricao?: string;
  acao?: { titulo: string; aoTocar: () => void };
}) {
  return (
    <View style={e.centralizado}>
      <View style={e.circuloVazio}>
        <Ionicons name={icone} size={34} color={cores.verdeEscuro} />
      </View>
      <Text style={[fonte.secao, { marginTop: espaco.lg, textAlign: 'center' }]}>{titulo}</Text>
      {descricao ? (
        <Text style={[fonte.subtitulo, { marginTop: espaco.sm, textAlign: 'center' }]}>
          {descricao}
        </Text>
      ) : null}
      {acao ? (
        <Botao
          titulo={acao.titulo}
          aoTocar={acao.aoTocar}
          estilo={{ marginTop: espaco.xl, paddingHorizontal: espaco.xxl }}
        />
      ) : null}
    </View>
  );
}

export function Aviso({
  texto,
  tom = 'informacao',
}: {
  texto: string;
  tom?: 'informacao' | 'alerta' | 'sucesso';
}) {
  const fundo =
    tom === 'sucesso' ? cores.verdeClaro : tom === 'alerta' ? '#FDECEA' : '#EAF2FE';
  const cor = tom === 'sucesso' ? cores.verdeProfundo : tom === 'alerta' ? cores.alerta : cores.informacao;
  const icone = tom === 'sucesso' ? 'checkmark-circle' : tom === 'alerta' ? 'alert-circle' : 'information-circle';

  return (
    <View style={[e.aviso, { backgroundColor: fundo }]}>
      <Ionicons name={icone} size={18} color={cor} />
      <Text style={[fonte.pequeno, { color: cor, flex: 1, marginLeft: espaco.sm }]}>{texto}</Text>
    </View>
  );
}

/** Avatar redondo com as iniciais quando não há foto. */
export function Avatar({
  url,
  nome,
  tamanho = 40,
}: {
  url?: string | null;
  nome?: string;
  tamanho?: number;
}) {
  if (url) {
    return (
      <Image
        source={{ uri: url }}
        style={{ width: tamanho, height: tamanho, borderRadius: tamanho / 2 }}
        accessibilityIgnoresInvertColors
      />
    );
  }
  const iniciais = (nome ?? '?')
    .split(' ')
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? '')
    .join('');

  return (
    <View
      style={{
        width: tamanho,
        height: tamanho,
        borderRadius: tamanho / 2,
        backgroundColor: cores.verdeClaro,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Text style={{ color: cores.verdeProfundo, fontWeight: '700', fontSize: tamanho * 0.36 }}>
        {iniciais}
      </Text>
    </View>
  );
}

export function Separador() {
  return <View style={{ height: 1, backgroundColor: cores.borda }} />;
}

const e = StyleSheet.create({
  botao: {
    // canto discreto em vez de cápsula: é o formato que mais diferencia a
    // casca do app de qualquer outro marketplace de usados
    borderRadius: raio.botao,
    minHeight: 52,
    paddingVertical: 15,
    paddingHorizontal: espaco.xl,
    alignItems: 'center',
    justifyContent: 'center',
  },
  botaoTexto: { fontSize: 15.5, fontWeight: '800', letterSpacing: -0.2 },
  linhaCentro: { flexDirection: 'row', alignItems: 'center' },
  cartao: {
    backgroundColor: cores.branco,
    borderRadius: raio.cartao,
    padding: espaco.lg,
    // sombra em vez de borda: a borda desenha uma caixa, a sombra apoia o
    // bloco na página. Bloco apoiado parece produto acabado
    ...(sombraCartao as object),
  },
  selo: {
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: raio.pilula,
    alignSelf: 'flex-start',
  },
  itemMenu: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: espaco.lg,
    paddingHorizontal: espaco.lg,
  },
  campo: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: cores.borda,
    borderRadius: raio.lg,
    paddingHorizontal: espaco.lg,
    paddingVertical: 14,
    backgroundColor: cores.branco,
  },
  busca: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: cores.branco,
    borderRadius: raio.lg,
    borderWidth: 1.5,
    borderColor: cores.borda,
    paddingHorizontal: espaco.lg,
    paddingVertical: 14,
    marginHorizontal: espaco.lg,
    ...(sombra as object),
  },
  chipCategoria: {
    paddingHorizontal: espaco.lg,
    paddingVertical: 9,
    borderRadius: raio.pilula,
    backgroundColor: cores.fundoCinza,
  },
  chipCategoriaAtivo: { backgroundColor: cores.preto },
  centralizado: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: espaco.xl,
  },
  circuloVazio: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: cores.verdeClaro,
    alignItems: 'center',
    justifyContent: 'center',
  },
  aviso: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: espaco.md,
    borderRadius: raio.md,
    marginBottom: espaco.md,
  },
});
