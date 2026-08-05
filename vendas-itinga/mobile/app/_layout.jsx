import { useCallback, useEffect, useState } from 'react';
import { Stack, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider, useAuth } from '../src/contexts/AuthContext';
import { CartProvider } from '../src/contexts/CartContext';
import ErrorScreen from '../src/components/ErrorScreen';
import { colors } from '../src/theme';

SplashScreen.preventAutoHideAsync().catch(() => {});

/**
 * Tela de falha do Expo Router.
 *
 * Exportar `ErrorBoundary` de um arquivo de layout faz o roteador capturar
 * qualquer erro lancado abaixo dele. Sem isso, um erro em tempo de execucao
 * derruba o app inteiro - que era o que acontecia antes.
 */
export function ErrorBoundary({ error, retry }) {
  return (
    <SafeAreaProvider>
      <ErrorScreen
        title="Algo deu errado"
        message={error?.message || 'O aplicativo encontrou um erro inesperado.'}
        onRetry={retry}
        showDiagnostics
      />
    </SafeAreaProvider>
  );
}

const screenOptions = {
  headerStyle: { backgroundColor: colors.white },
  headerTitleStyle: { fontWeight: '800', color: colors.ink, fontSize: 17 },
  headerTintColor: colors.ink,
  headerShadowVisible: false,
  contentStyle: { backgroundColor: colors.background },
};

function RootNavigator() {
  const { loading, isAuthenticated } = useAuth();
  const router = useRouter();
  const [splashHidden, setSplashHidden] = useState(false);

  const hideSplash = useCallback(() => {
    setSplashHidden((already) => {
      if (!already) SplashScreen.hideAsync().catch(() => {});
      return true;
    });
  }, []);

  // Rede de seguranca: se a checagem de sessao travar (API fora do ar,
  // DNS lento), a splash sai sozinha em 8s em vez de prender o app.
  useEffect(() => {
    const timer = setTimeout(hideSplash, 8000);
    return () => clearTimeout(timer);
  }, [hideSplash]);

  // A decisao de rota acontece DEPOIS da montagem do navegador (em efeito),
  // nunca durante a renderizacao - navegar durante o render quebra o router.
  useEffect(() => {
    if (loading) return;
    if (!isAuthenticated) router.replace('/login');
    hideSplash();
  }, [loading, isAuthenticated, router, hideSplash]);

  return (
    <Stack screenOptions={screenOptions}>
      {/* A rota "/" e o feed em (tabs)/index.jsx. Nao crie um app/index.jsx:
          os dois resolveriam para o mesmo caminho e o app entra em laco. */}
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="(auth)/login" options={{ headerShown: false }} />

      <Stack.Screen name="product/[id]" options={{ headerTransparent: true, title: '' }} />
      <Stack.Screen name="cart" options={{ title: 'Sacola' }} />
      <Stack.Screen name="checkout" options={{ title: 'Pagamento' }} />
      <Stack.Screen name="order/[id]" options={{ title: 'Pedido' }} />
      <Stack.Screen name="order/refund/[id]" options={{ title: 'Solicitar reembolso' }} />
      <Stack.Screen name="chat/[id]" options={{ title: 'Conversa' }} />
      <Stack.Screen name="purchases" options={{ title: 'Minhas compras' }} />
      <Stack.Screen name="sales" options={{ title: 'Painel de vendas' }} />
      <Stack.Screen name="my-products" options={{ title: 'Meus anúncios' }} />
      <Stack.Screen name="favorites" options={{ title: 'Favoritos' }} />
      <Stack.Screen name="addresses" options={{ title: 'Endereços' }} />
      <Stack.Screen name="notifications" options={{ title: 'Notificações' }} />
      <Stack.Screen name="seller-account" options={{ title: 'Conta de vendedor' }} />
      <Stack.Screen name="courier" options={{ title: 'Entregas' }} />
    </Stack>
  );
}

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <AuthProvider>
          <CartProvider>
            <StatusBar style="dark" backgroundColor={colors.white} />
            <RootNavigator />
          </CartProvider>
        </AuthProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
