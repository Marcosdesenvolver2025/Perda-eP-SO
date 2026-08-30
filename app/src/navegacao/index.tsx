/**
 * Navegação do app.
 *
 * Abas de baixo iguais às do vídeo: home, buscar, vendas, notificações e a
 * conta. O resto abre empilhado por cima.
 */

import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { NavigationContainer, type Theme } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

import { MODO_DEMONSTRACAO } from '../api/cliente';
import { TelaAvaliar } from '../telas/Avaliar';
import { TelaCurtidos } from '../telas/Curtidos';
import { TelaFazerOferta } from '../telas/FazerOferta';
import { TelaOfertas } from '../telas/Ofertas';
import { Carregando } from '../componentes/base';
import { useAutenticacao } from '../contextos/Autenticacao';
import { referenciaDeNavegacao } from '../demo/navegacao';
import { TelaRoteiro } from '../demo/TelaRoteiro';
import { cores } from '../tema';
import { TelaAreaDoEntregador } from '../telas/AreaDoEntregador';
import { TelaBusca } from '../telas/Busca';
import { TelaCheckout } from '../telas/Checkout';
import { TelaConfiguracoes } from '../telas/Configuracoes';
import { TelaConversa } from '../telas/Conversa';
import { TelaEntrar } from '../telas/Entrar';
import { TelaHome } from '../telas/Home';
import { TelaLoja } from '../telas/Loja';
import { TelaMinhaConta } from '../telas/MinhaConta';
import { TelaNotificacoes } from '../telas/Notificacoes';
import { TelaNovoAnuncio } from '../telas/NovoAnuncio';
import { TelaPedido } from '../telas/Pedido';
import { TelaProduto } from '../telas/Produto';
import { TelaReembolso } from '../telas/Reembolso';
import { TelaVendas } from '../telas/Vendas';
import {
  TelaComoFunciona,
  TelaContaDeRecebimento,
  TelaDadosPessoais,
  TelaEnderecos,
} from '../telas/cadastros';
import { TelaCodigoDeConfirmacao } from '../telas/CodigoDeConfirmacao';
import { TelaEntregaDoVendedor } from '../telas/EntregaDoVendedor';
import { TelaPassoDaEntrega } from '../telas/PassoDaEntrega';
import {
  TelaEscolherEntregador,
  TelaPainelAdmin,
  TelaRecusarDevolucao,
} from '../telas/PainelAdmin';
import { TelaMinhaLoja, TelaMinhasCompras, TelaMinhasVendas } from '../telas/listas';
import type { ParametrosAbas, ParametrosApp } from './tipos';

const Abas = createBottomTabNavigator<ParametrosAbas>();
const Pilha = createNativeStackNavigator<ParametrosApp>();

const icones: Record<keyof ParametrosAbas, keyof typeof Ionicons.glyphMap> = {
  Home: 'home',
  Buscar: 'search',
  Vendas: 'pricetag',
  Notificacoes: 'notifications',
  MinhaConta: 'person',
};

const rotulos: Record<keyof ParametrosAbas, string> = {
  Home: 'home',
  Buscar: 'buscar',
  Vendas: 'vendas',
  Notificacoes: 'notificações',
  MinhaConta: 'minha conta',
};

function NavegadorDeAbas() {
  return (
    <Abas.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: cores.verdeEscuro,
        tabBarInactiveTintColor: cores.textoSuave,
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
        tabBarStyle: { borderTopColor: cores.borda, height: 60, paddingBottom: 6, paddingTop: 6 },
        tabBarLabel: rotulos[route.name],
        tabBarIcon: ({ color, focused, size }) => (
          <Ionicons
            name={focused ? icones[route.name] : (`${icones[route.name]}-outline` as never)}
            size={size ?? 22}
            color={color}
          />
        ),
      })}
    >
      <Abas.Screen name="Home" component={TelaHome as never} />
      <Abas.Screen name="Buscar" component={TelaBusca as never} />
      <Abas.Screen name="Vendas" component={TelaVendas as never} />
      <Abas.Screen name="Notificacoes" component={TelaNotificacoes as never} />
      <Abas.Screen name="MinhaConta" component={TelaMinhaConta as never} />
    </Abas.Navigator>
  );
}

const tema: Theme = {
  dark: false,
  colors: {
    primary: cores.verde,
    background: cores.fundo,
    card: cores.branco,
    text: cores.texto,
    border: cores.borda,
    notification: cores.verde,
  },
  fonts: {
    regular: { fontFamily: 'System', fontWeight: '400' },
    medium: { fontFamily: 'System', fontWeight: '500' },
    bold: { fontFamily: 'System', fontWeight: '700' },
    heavy: { fontFamily: 'System', fontWeight: '800' },
  },
};

export function Navegacao() {
  const { carregando } = useAutenticacao();

  // espera a sessão salva ser lida antes de decidir a primeira tela
  if (carregando) return <Carregando texto="abrindo o vendas itinga..." />;

  return (
    <NavigationContainer theme={tema} ref={referenciaDeNavegacao}>
      <Pilha.Navigator screenOptions={{ headerShown: false }}>
        <Pilha.Screen name="Abas" component={NavegadorDeAbas} />
        <Pilha.Screen name="Entrar" component={TelaEntrar} options={{ presentation: 'modal' }} />
        <Pilha.Screen name="Busca" component={TelaBusca} />
        <Pilha.Screen name="Produto" component={TelaProduto} />
        <Pilha.Screen name="Loja" component={TelaLoja} />
        <Pilha.Screen name="Checkout" component={TelaCheckout} />
        <Pilha.Screen name="Pedido" component={TelaPedido} />
        <Pilha.Screen name="Reembolso" component={TelaReembolso} />
        <Pilha.Screen
          name="NovoAnuncio"
          component={TelaNovoAnuncio}
          options={{ presentation: 'modal' }}
        />
        <Pilha.Screen name="MinhaLoja" component={TelaMinhaLoja} />
        <Pilha.Screen name="MinhasCompras" component={TelaMinhasCompras} />
        <Pilha.Screen name="MinhasVendas" component={TelaMinhasVendas} />
        <Pilha.Screen name="Conversa" component={TelaConversa} />
        <Pilha.Screen name="Configuracoes" component={TelaConfiguracoes} />
        <Pilha.Screen name="DadosPessoais" component={TelaDadosPessoais} />
        <Pilha.Screen name="Enderecos" component={TelaEnderecos} />
        <Pilha.Screen name="ContaDeRecebimento" component={TelaContaDeRecebimento} />
        <Pilha.Screen name="AreaDoEntregador" component={TelaAreaDoEntregador} />
        <Pilha.Screen
          name="PassoDaEntrega"
          component={TelaPassoDaEntrega}
          options={{ presentation: 'modal' }}
        />
        <Pilha.Screen name="PainelAdmin" component={TelaPainelAdmin} />
        <Pilha.Screen name="EscolherEntregador" component={TelaEscolherEntregador} />
        <Pilha.Screen
          name="RecusarDevolucao"
          component={TelaRecusarDevolucao}
          options={{ presentation: 'modal' }}
        />
        <Pilha.Screen name="CodigoDeConfirmacao" component={TelaCodigoDeConfirmacao} />
        <Pilha.Screen
          name="EntregaDoVendedor"
          component={TelaEntregaDoVendedor}
          options={{ presentation: 'modal' }}
        />
        <Pilha.Screen name="ComoFunciona" component={TelaComoFunciona} />
        <Pilha.Screen
          name="FazerOferta"
          component={TelaFazerOferta}
          options={{ presentation: 'modal' }}
        />
        <Pilha.Screen name="Ofertas" component={TelaOfertas} />
        <Pilha.Screen name="Curtidos" component={TelaCurtidos} />
        <Pilha.Screen
          name="Avaliar"
          component={TelaAvaliar}
          options={{ presentation: 'modal' }}
        />
        {MODO_DEMONSTRACAO ? (
          <Pilha.Screen name="Roteiro" component={TelaRoteiro} />
        ) : null}
      </Pilha.Navigator>
    </NavigationContainer>
  );
}
