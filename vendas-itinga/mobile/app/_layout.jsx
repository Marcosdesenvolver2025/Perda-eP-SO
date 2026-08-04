import { useEffect } from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider, useAuth } from '../src/contexts/AuthContext';
import { CartProvider } from '../src/contexts/CartContext';
import { colors } from '../src/theme';

SplashScreen.preventAutoHideAsync().catch(() => {});

const screenOptions = {
  headerStyle: { backgroundColor: colors.white },
  headerTitleStyle: { fontWeight: '800', color: colors.ink, fontSize: 17 },
  headerTintColor: colors.ink,
  headerShadowVisible: false,
  contentStyle: { backgroundColor: colors.background },
};

function RootNavigator() {
  const { loading } = useAuth();

  useEffect(() => {
    if (!loading) SplashScreen.hideAsync().catch(() => {});
  }, [loading]);

  return (
    <Stack screenOptions={screenOptions}>
      <Stack.Screen name="index" options={{ headerShown: false }} />
      <Stack.Screen name="(auth)/login" options={{ headerShown: false }} />
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />

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
