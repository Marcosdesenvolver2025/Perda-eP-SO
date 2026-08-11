/**
 * Criar anúncio.
 *
 * A tela avisa sobre os limites (20 kg e 60 cm) enquanto a pessoa digita, e
 * não deixa publicar fora deles — é melhor barrar aqui do que descobrir na
 * hora da coleta que o entregador não consegue levar.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { api, MODO_DEMONSTRACAO } from '../api/cliente';
import type { CondicaoProduto } from '../api/tipos';
import { Aviso, Botao, Campo, Selo } from '../componentes/base';
import type { ParametrosApp } from '../navegacao/tipos';
import { cores, espaco, fonte, raio } from '../tema';
import { EscolhaDeModalidade } from '../componentes/modalidade';
import {
  ALTURA_MAXIMA_CM,
  LARGURA_MAXIMA_CM,
  PESO_MAXIMO_G,
  VALOR_MINIMO_VENDA,
  cabeNaEntregaDaPlataforma,
  calcularDescontos,
  type ModalidadeEntrega,
} from '../regras/limites';

type Props = NativeStackScreenProps<ParametrosApp, 'NovoAnuncio'>;

interface EnderecoDeColeta {
  id: string;
  logradouro: string;
  numero: string;
  bairro: string;
  principal: boolean;
}

const condicoes: { chave: CondicaoProduto; rotulo: string }[] = [
  { chave: 'NOVO', rotulo: 'novo' },
  { chave: 'SEMINOVO', rotulo: 'seminovo' },
  { chave: 'USADO', rotulo: 'usado' },
];

/** Converte "R$ 129,90" ou "129,90" em 12990 centavos. */
function paraCentavos(texto: string): number {
  const digitos = texto.replace(/\D/g, '');
  return digitos ? Number(digitos) : 0;
}

function formatarPreco(centavos: number): string {
  if (!centavos) return '';
  return (centavos / 100).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  });
}

export function TelaNovoAnuncio({ navigation }: Props) {
  const [fotos, setFotos] = useState<string[]>([]);
  const [titulo, setTitulo] = useState('');
  const [descricao, setDescricao] = useState('');
  const [preco, setPreco] = useState(0);
  const [condicao, setCondicao] = useState<CondicaoProduto>('USADO');
  const [marca, setMarca] = useState('');
  const [pesoKg, setPesoKg] = useState('');
  const [comprimento, setComprimento] = useState('');
  const [largura, setLargura] = useState('');
  const [altura, setAltura] = useState('');
  const [modalidadeEntrega, setModalidadeEntrega] = useState<ModalidadeEntrega>('PLATAFORMA');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [enderecos, setEnderecos] = useState<EnderecoDeColeta[]>([]);
  const [enderecoColetaId, setEnderecoColetaId] = useState<string | null>(null);

  // o entregador precisa saber onde buscar; a lista vem dos endereços
  // que o vendedor já cadastrou
  useFocusEffect(
    useCallback(() => {
      async function carregar() {
        if (MODO_DEMONSTRACAO) return;
        try {
          const r = await api<{ itens: EnderecoDeColeta[] }>('/conta/enderecos');
          setEnderecos(r.itens);
          setEnderecoColetaId(
            (atual) => atual ?? r.itens.find((e) => e.principal)?.id ?? r.itens[0]?.id ?? null,
          );
        } catch {
          setEnderecos([]);
        }
      }
      void carregar();
    }, []),
  );

  const medidas = useMemo(
    () => ({
      pesoG: Math.round(Number(pesoKg.replace(',', '.')) * 1000) || 0,
      comprimentoCm: Number(comprimento) || 0,
      larguraCm: Number(largura) || 0,
      alturaCm: Number(altura) || 0,
    }),
    [pesoKg, comprimento, largura, altura],
  );

  // só reclama depois que a pessoa começou a preencher as medidas
  const preencheuMedidas =
    medidas.pesoG > 0 && medidas.comprimentoCm > 0 && medidas.larguraCm > 0 && medidas.alturaCm > 0;

  // se o pacote não cabe na nossa entrega, a opção fica bloqueada e o app cai
  // sozinho para "entrega por sua conta"
  const cabe = preencheuMedidas ? cabeNaEntregaDaPlataforma(medidas) : { cabe: true, motivos: [] };

  useEffect(() => {
    if (!cabe.cabe && modalidadeEntrega === 'PLATAFORMA') {
      setModalidadeEntrega('VENDEDOR');
    }
  }, [cabe.cabe, modalidadeEntrega]);

  async function escolherFotos() {
    const permissao = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permissao.granted) {
      setErro('Precisamos da sua permissão para acessar as fotos.');
      return;
    }

    const resultado = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsMultipleSelection: true,
      selectionLimit: 10 - fotos.length,
      quality: 0.7,
    });

    if (!resultado.canceled) {
      setFotos((atuais) => [...atuais, ...resultado.assets.map((a) => a.uri)].slice(0, 10));
    }
  }

  const podePublicar =
    fotos.length > 0 &&
    titulo.trim().length >= 4 &&
    descricao.trim().length >= 10 &&
    preco >= VALOR_MINIMO_VENDA &&
    preencheuMedidas &&
    // na modalidade PLATAFORMA o pacote precisa caber e ter endereço de coleta
    (modalidadeEntrega === 'VENDEDOR' ||
      (cabe.cabe && (!!enderecoColetaId || MODO_DEMONSTRACAO)));

  async function publicar() {
    setErro(null);

    if (MODO_DEMONSTRACAO) {
      setErro('Modo demonstração: configure o servidor para publicar de verdade.');
      return;
    }

    setEnviando(true);
    try {
      // TODO na sua infra: subir as fotos para o storage (S3/Cloudinary) e
      // mandar as URLs. Aqui vão as URIs locais como placeholder.
      await api('/anuncios', {
        metodo: 'POST',
        corpo: {
          titulo: titulo.trim(),
          descricao: descricao.trim(),
          preco,
          condicao,
          marca: marca.trim() || undefined,
          ...medidas,
          modalidadeEntrega,
          ...(modalidadeEntrega === 'PLATAFORMA' && enderecoColetaId
            ? { enderecoColetaId }
            : {}),
          fotos,
        },
      });
      navigation.goBack();
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: cores.fundo }} edges={['top']}>
      <View style={e.topo}>
        <Pressable onPress={() => navigation.goBack()} hitSlop={12} accessibilityLabel="voltar">
          <Ionicons name="close" size={26} color={cores.texto} />
        </Pressable>
        <Text style={[fonte.secao, { marginLeft: espaco.md }]}>novo anúncio</Text>
      </View>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={{ padding: espaco.lg, paddingBottom: 120 }}>
          {erro ? <Aviso texto={erro} tom="alerta" /> : null}

          <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>fotos</Text>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ gap: espaco.sm, marginBottom: espaco.lg }}
          >
            <Pressable onPress={escolherFotos} style={e.adicionarFoto} accessibilityRole="button">
              <Ionicons name="camera-outline" size={26} color={cores.verdeEscuro} />
              <Text style={[fonte.pequeno, { marginTop: 4 }]}>adicionar</Text>
            </Pressable>

            {fotos.map((uri, indice) => (
              <View key={uri} style={e.miniatura}>
                <Image source={{ uri }} style={{ width: '100%', height: '100%' }} />
                <Pressable
                  onPress={() => setFotos((atuais) => atuais.filter((_, i) => i !== indice))}
                  style={e.removerFoto}
                  hitSlop={8}
                  accessibilityLabel="remover foto"
                >
                  <Ionicons name="close" size={14} color={cores.branco} />
                </Pressable>
                {indice === 0 ? (
                  <View style={e.capaSelo}>
                    <Text style={{ color: cores.branco, fontSize: 10, fontWeight: '700' }}>capa</Text>
                  </View>
                ) : null}
              </View>
            ))}
          </ScrollView>

          <Campo
            rotulo="o que você está vendendo"
            valor={titulo}
            aoMudar={setTitulo}
            dica="ex.: bicicleta aro 26"
            maxLength={120}
          />

          <Campo
            rotulo="preço"
            valor={formatarPreco(preco)}
            aoMudar={(v) => setPreco(paraCentavos(v))}
            dica="R$ 0,00"
            teclado="numeric"
            ajuda={
              preco >= VALOR_MINIMO_VENDA
                ? `você recebe ${formatarPreco(
                    calcularDescontos(preco, modalidadeEntrega).vendedor,
                  )} — veja o detalhe das taxas mais abaixo`
                : `valor mínimo de venda: ${formatarPreco(VALOR_MINIMO_VENDA)}`
            }
          />

          <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>estado do produto</Text>
          <View style={{ flexDirection: 'row', gap: espaco.sm, marginBottom: espaco.lg }}>
            {condicoes.map((c) => (
              <Pressable
                key={c.chave}
                onPress={() => setCondicao(c.chave)}
                accessibilityRole="radio"
                accessibilityState={{ selected: condicao === c.chave }}
                style={[e.opcaoCondicao, condicao === c.chave && e.opcaoCondicaoAtiva]}
              >
                <Text
                  style={{
                    fontWeight: '700',
                    fontSize: 13,
                    color: condicao === c.chave ? cores.branco : cores.textoSuave,
                  }}
                >
                  {c.rotulo}
                </Text>
              </Pressable>
            ))}
          </View>

          <Campo
            rotulo="descrição"
            valor={descricao}
            aoMudar={setDescricao}
            dica="conte o estado, o tempo de uso, se tem marca de uso..."
            multilinha
            maxLength={4000}
          />

          <Campo rotulo="marca (opcional)" valor={marca} aoMudar={setMarca} dica="ex.: caloi" />

          <View style={e.blocoMedidas}>
            <Text style={[fonte.rotulo, { marginBottom: 4 }]}>tamanho e peso do pacote</Text>
            <Text style={[fonte.pequeno, { marginBottom: espaco.lg }]}>
              nossos entregadores levam até {PESO_MAXIMO_G / 1000} kg, {LARGURA_MAXIMA_CM} cm de
              largura e {ALTURA_MAXIMA_CM} cm de altura. acima disso, você mesmo entrega.
            </Text>

            <Campo
              rotulo="peso"
              valor={pesoKg}
              aoMudar={setPesoKg}
              dica="0"
              teclado="numeric"
              sufixo="kg"
            />

            <View style={{ flexDirection: 'row', gap: espaco.md }}>
              <View style={{ flex: 1 }}>
                <Campo
                  rotulo="comprimento"
                  valor={comprimento}
                  aoMudar={setComprimento}
                  dica="0"
                  teclado="numeric"
                  sufixo="cm"
                />
              </View>
              <View style={{ flex: 1 }}>
                <Campo
                  rotulo="largura"
                  valor={largura}
                  aoMudar={setLargura}
                  dica="0"
                  teclado="numeric"
                  sufixo="cm"
                />
              </View>
              <View style={{ flex: 1 }}>
                <Campo
                  rotulo="altura"
                  valor={altura}
                  aoMudar={setAltura}
                  dica="0"
                  teclado="numeric"
                  sufixo="cm"
                />
              </View>
            </View>

            {preencheuMedidas ? (
              cabe.cabe ? (
                <Aviso texto="cabe na nossa entrega — você pode escolher as duas opções." tom="sucesso" />
              ) : (
                <Aviso
                  texto={`esse produto não cabe na nossa entrega (${cabe.motivos.join('; ')}), então a entrega fica por sua conta.`}
                  tom="alerta"
                />
              )
            ) : null}
          </View>

          <View style={{ marginTop: espaco.xl }}>
            <EscolhaDeModalidade
              preco={preco}
              selecionada={modalidadeEntrega}
              aoEscolher={setModalidadeEntrega}
              bloqueioDaPlataforma={cabe.cabe ? null : cabe.motivos}
            />
          </View>

          {modalidadeEntrega === 'PLATAFORMA' ? (
            <View style={e.blocoColeta}>
              <Text style={[fonte.rotulo, { marginBottom: 4 }]}>onde buscar o produto</Text>
              <Text style={[fonte.pequeno, { marginBottom: espaco.md }]}>
                é o endereço onde o entregador vai passar para pegar
              </Text>

              {enderecos.length === 0 ? (
                <>
                  <Aviso
                    texto="você ainda não tem endereço cadastrado. cadastre um para usar nossos entregadores."
                    tom="alerta"
                  />
                  <Botao
                    titulo="cadastrar endereço"
                    variante="vazado"
                    aoTocar={() => navigation.navigate('Enderecos')}
                  />
                </>
              ) : (
                enderecos.map((endereco) => (
                  <Pressable
                    key={endereco.id}
                    onPress={() => setEnderecoColetaId(endereco.id)}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: enderecoColetaId === endereco.id }}
                    style={[
                      e.opcaoEndereco,
                      enderecoColetaId === endereco.id && {
                        borderColor: cores.verde,
                        backgroundColor: cores.branco,
                      },
                    ]}
                  >
                    <Ionicons
                      name={enderecoColetaId === endereco.id ? 'radio-button-on' : 'radio-button-off'}
                      size={20}
                      color={enderecoColetaId === endereco.id ? cores.verde : cores.textoFraco}
                    />
                    <Text style={[fonte.corpo, { marginLeft: espaco.md, flex: 1 }]}>
                      {endereco.logradouro}, {endereco.numero} — {endereco.bairro}
                    </Text>
                  </Pressable>
                ))
              )}
            </View>
          ) : null}
        </ScrollView>
      </KeyboardAvoidingView>

      <View style={e.rodape}>
        <Botao
          titulo="publicar anúncio"
          aoTocar={publicar}
          carregando={enviando}
          desabilitado={!podePublicar}
        />
      </View>
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
  adicionarFoto: {
    width: 88,
    height: 88,
    borderRadius: raio.md,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: cores.verde,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: cores.verdeClaro,
  },
  miniatura: {
    width: 88,
    height: 88,
    borderRadius: raio.md,
    overflow: 'hidden',
    backgroundColor: cores.fundoCinza,
  },
  removerFoto: {
    position: 'absolute',
    top: 4,
    right: 4,
    backgroundColor: 'rgba(0,0,0,0.6)',
    borderRadius: 10,
    padding: 3,
  },
  capaSelo: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: cores.verde,
    alignItems: 'center',
    paddingVertical: 2,
  },
  opcaoCondicao: {
    flex: 1,
    paddingVertical: espaco.md,
    borderRadius: raio.md,
    backgroundColor: cores.fundoCinza,
    alignItems: 'center',
  },
  opcaoCondicaoAtiva: { backgroundColor: cores.verde },
  blocoMedidas: {
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.lg,
    padding: espaco.lg,
    marginTop: espaco.sm,
  },
  escolha: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: espaco.md,
  },
  blocoColeta: {
    backgroundColor: cores.fundoCinza,
    borderRadius: raio.lg,
    padding: espaco.lg,
    marginTop: espaco.sm,
    marginBottom: espaco.md,
  },
  opcaoEndereco: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: cores.borda,
    borderRadius: raio.md,
    padding: espaco.md,
    marginBottom: espaco.sm,
  },
  rodape: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    padding: espaco.lg,
    backgroundColor: cores.branco,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
  },
});
