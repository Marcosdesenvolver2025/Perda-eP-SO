import { useCallback, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api from '../../src/api/client';
import { useAuth } from '../../src/contexts/AuthContext';
import { Button, Divider } from '../../src/components/ui';
import { colors, radius, spacing, typography } from '../../src/theme';
import { formatBRL } from '../../src/utils/format';

const MENU = [
  { icon: 'bag-check-outline', label: 'Minhas compras', route: '/purchases' },
  { icon: 'cash-outline', label: 'Painel de vendas', route: '/sales', sellerOnly: true },
  { icon: 'pricetags-outline', label: 'Meus anúncios', route: '/my-products', sellerOnly: true },
  { icon: 'heart-outline', label: 'Favoritos', route: '/favorites' },
  { icon: 'location-outline', label: 'Endereços salvos', route: '/addresses' },
  { icon: 'notifications-outline', label: 'Notificações', route: '/notifications' },
  { icon: 'bicycle-outline', label: 'Sou entregador', route: '/courier' },
];

export default function Profile() {
  const router = useRouter();
  const { user, isAuthenticated, signOut, refreshUser } = useAuth();
  const [balance, setBalance] = useState(null);

  useFocusEffect(
    useCallback(() => {
      if (!isAuthenticated) return;
      refreshUser();
      api
        .get('/users/me/balance')
        .then(({ data }) => setBalance(data))
        .catch(() => setBalance(null));
    }, [isAuthenticated, refreshUser])
  );

  if (!isAuthenticated) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.guest}>
          <Ionicons name="person-circle-outline" size={72} color={colors.primary} />
          <Text style={styles.guestTitle}>Entre para continuar</Text>
          <Text style={styles.guestText}>
            Faça login com o Google para comprar, vender e acompanhar seus pedidos.
          </Text>
          <Button
            title="Entrar com o Google"
            onPress={() => router.push('/(auth)/login')}
            style={{ marginTop: spacing.lg, minWidth: 220 }}
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: spacing.xxl }} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          {user?.avatarUrl ? (
            <Image source={{ uri: user.avatarUrl }} style={styles.avatar} contentFit="cover" />
          ) : (
            <View style={[styles.avatar, styles.avatarFallback]}>
              <Text style={styles.avatarLetter}>{user?.name?.[0]?.toUpperCase() || '?'}</Text>
            </View>
          )}

          <View style={{ flex: 1, marginLeft: spacing.lg }}>
            <Text style={styles.name}>{user?.name}</Text>
            <Text style={styles.email}>{user?.email}</Text>
            {user?.ratingCount > 0 ? (
              <View style={styles.rating}>
                <Ionicons name="star" size={13} color={colors.warning} />
                <Text style={styles.ratingText}>
                  {user.ratingAvg.toFixed(1)} ({user.ratingCount})
                </Text>
              </View>
            ) : null}
          </View>
        </View>

        {user?.isSeller ? (
          <View style={styles.balanceCard}>
            <View style={{ flex: 1 }}>
              <Text style={styles.balanceLabel}>a receber</Text>
              <Text style={styles.balanceValue}>{formatBRL(balance?.pendingCents || 0)}</Text>
            </View>
            <Divider style={{ width: 1, height: 40 }} />
            <View style={{ flex: 1, alignItems: 'flex-end' }}>
              <Text style={styles.balanceLabel}>já recebido</Text>
              <Text style={[styles.balanceValue, { color: colors.ink }]}>
                {formatBRL(balance?.receivedCents || 0)}
              </Text>
            </View>
          </View>
        ) : (
          <Pressable style={styles.sellerCta} onPress={() => router.push('/seller-account')}>
            <Ionicons name="storefront-outline" size={22} color={colors.primaryDark} />
            <View style={{ flex: 1, marginLeft: spacing.md }}>
              <Text style={styles.sellerCtaTitle}>Comece a vender</Text>
              <Text style={styles.sellerCtaText}>
                Cadastre seus dados bancários para receber pelas vendas.
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color={colors.primaryDark} />
          </Pressable>
        )}

        <View style={styles.menu}>
          {MENU.filter((item) => !item.sellerOnly || user?.isSeller).map((item) => (
            <Pressable
              key={item.route}
              style={({ pressed }) => [styles.menuItem, pressed && { backgroundColor: colors.surfaceAlt }]}
              onPress={() => router.push(item.route)}
            >
              <View style={styles.menuIcon}>
                <Ionicons name={item.icon} size={18} color={colors.primaryDark} />
              </View>
              <Text style={styles.menuLabel}>{item.label}</Text>
              <Ionicons name="chevron-forward" size={18} color={colors.textLight} />
            </Pressable>
          ))}
        </View>

        <Button
          title="Sair da conta"
          variant="outline"
          icon="log-out-outline"
          style={{ marginHorizontal: spacing.lg, marginTop: spacing.xl }}
          onPress={() =>
            Alert.alert('Sair', 'Deseja encerrar a sessão?', [
              { text: 'Cancelar', style: 'cancel' },
              {
                text: 'Sair',
                style: 'destructive',
                onPress: async () => {
                  await signOut();
                  router.replace('/(auth)/login');
                },
              },
            ])
          }
        />

        <Text style={styles.version}>Vendas Itinga · versão 1.0.0</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  guest: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  guestTitle: { ...typography.h2, marginTop: spacing.lg },
  guestText: { ...typography.small, textAlign: 'center', marginTop: 6, maxWidth: 280, lineHeight: 19 },

  header: { flexDirection: 'row', alignItems: 'center', padding: spacing.lg },
  avatar: { width: 68, height: 68, borderRadius: 34, backgroundColor: colors.primaryLight },
  avatarFallback: { alignItems: 'center', justifyContent: 'center' },
  avatarLetter: { ...typography.h1, color: colors.primaryDark },
  name: { ...typography.h2 },
  email: { ...typography.small, marginTop: 1 },
  rating: { flexDirection: 'row', alignItems: 'center', marginTop: 4 },
  ratingText: { ...typography.tiny, fontWeight: '700', marginLeft: 3 },

  balanceCard: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: spacing.lg,
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.primaryLight,
  },
  balanceLabel: { ...typography.tiny, color: colors.primaryDark, fontWeight: '800' },
  balanceValue: { ...typography.h2, color: colors.primaryDark, marginTop: 2 },

  sellerCta: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: spacing.lg,
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.primarySoft,
    borderWidth: 1.5,
    borderColor: colors.primaryLight,
  },
  sellerCtaTitle: { ...typography.h3, fontSize: 15 },
  sellerCtaText: { ...typography.tiny, marginTop: 2, lineHeight: 15 },

  menu: { marginTop: spacing.xl, paddingHorizontal: spacing.lg },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderRadius: radius.md,
  },
  menuIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  menuLabel: { ...typography.body, flex: 1, fontWeight: '600' },

  version: { ...typography.tiny, textAlign: 'center', marginTop: spacing.xl },
});
