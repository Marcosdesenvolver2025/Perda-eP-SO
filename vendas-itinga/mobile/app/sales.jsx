import { useCallback, useState } from 'react';
import { Alert, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api, { apiError } from '../src/api/client';
import { Badge, Button, Chip, EmptyState, Loading } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';
import { ORDER_STATUS_LABELS, ORDER_STATUS_TONE, formatBRL, formatDate } from '../src/utils/format';

const FILTERS = [
  { key: 'ALL', label: 'todos' },
  { key: 'PAID', label: 'a enviar' },
  { key: 'SHIPPED', label: 'enviados' },
  { key: 'IN_TEST', label: 'em teste' },
  { key: 'COMPLETED', label: 'concluídos' },
  { key: 'RETURN_REQUESTED', label: 'devoluções' },
];

/** Painel de vendas: status dos pedidos, saldo e ações do vendedor. */
export default function Sales() {
  const router = useRouter();
  const [orders, setOrders] = useState([]);
  const [balance, setBalance] = useState(null);
  const [filter, setFilter] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [updating, setUpdating] = useState(null);

  const load = useCallback(async () => {
    try {
      const [salesResponse, balanceResponse] = await Promise.all([
        api.get('/orders/sales'),
        api.get('/users/me/balance').catch(() => ({ data: null })),
      ]);
      setOrders(salesResponse.data);
      setBalance(balanceResponse.data);
    } catch {
      setOrders([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  async function markShipped(order) {
    setUpdating(order.id);
    try {
      await api.patch(`/orders/${order.id}/status`, { status: 'SHIPPED' });
      await load();
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    } finally {
      setUpdating(null);
    }
  }

  const filtered = filter === 'ALL' ? orders : orders.filter((order) => order.status === filter);

  if (loading) return <Loading />;

  return (
    <View style={{ flex: 1, backgroundColor: colors.white }}>
      <View style={styles.balanceRow}>
        <View style={styles.balanceCard}>
          <Text style={styles.balanceLabel}>a receber</Text>
          <Text style={styles.balanceValue}>{formatBRL(balance?.pendingCents || 0)}</Text>
          <Text style={styles.balanceHint}>liberado após o teste de 4 dias</Text>
        </View>
        <View style={[styles.balanceCard, { backgroundColor: colors.surfaceAlt }]}>
          <Text style={[styles.balanceLabel, { color: colors.textMuted }]}>já recebido</Text>
          <Text style={[styles.balanceValue, { color: colors.ink }]}>
            {formatBRL(balance?.receivedCents || 0)}
          </Text>
          <Text style={styles.balanceHint}>{orders.length} venda(s) no total</Text>
        </View>
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.filters}
        contentContainerStyle={{ paddingHorizontal: spacing.lg }}
      >
        {FILTERS.map((item) => (
          <Chip
            key={item.key}
            label={item.label}
            selected={filter === item.key}
            onPress={() => setFilter(item.key)}
          />
        ))}
      </ScrollView>

      <FlatList
        data={filtered}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            colors={[colors.primary]}
            tintColor={colors.primary}
            onRefresh={() => {
              setRefreshing(true);
              load();
            }}
          />
        }
        renderItem={({ item }) => (
          <Pressable style={styles.card} onPress={() => router.push(`/order/${item.id}`)}>
            <View style={styles.cardHeader}>
              <Badge label={ORDER_STATUS_LABELS[item.status]} tone={ORDER_STATUS_TONE[item.status]} />
              <Text style={styles.date}>{formatDate(item.createdAt)}</Text>
            </View>

            {item.items.map((product) => (
              <View key={product.id} style={styles.product}>
                {product.image ? (
                  <Image source={{ uri: product.image }} style={styles.thumb} contentFit="cover" />
                ) : (
                  <View style={[styles.thumb, styles.thumbFallback]}>
                    <Ionicons name="cube-outline" size={18} color={colors.textLight} />
                  </View>
                )}
                <View style={{ flex: 1, marginLeft: spacing.md }}>
                  <Text style={styles.productTitle} numberOfLines={2}>
                    {product.title}
                  </Text>
                  <Text style={styles.buyer}>comprador: {item.buyer?.name}</Text>
                </View>
              </View>
            ))}

            <View style={styles.split}>
              <Text style={styles.splitLabel}>
                venda {formatBRL(item.totalCents)} · comissão {item.platformFeePercent}%
              </Text>
              <Text style={styles.splitValue}>você recebe {formatBRL(item.sellerAmountCents)}</Text>
            </View>

            {item.status === 'PAID' ? (
              <Button
                title={
                  item.shippingMode === 'PLATFORM'
                    ? 'Produto entregue à frota'
                    : 'Marcar como enviado'
                }
                size="sm"
                icon="send-outline"
                loading={updating === item.id}
                onPress={() => markShipped(item)}
                style={{ marginTop: spacing.md }}
              />
            ) : null}

            {item.status === 'RETURN_REQUESTED' ? (
              <View style={styles.returnBanner}>
                <Ionicons name="return-down-back-outline" size={15} color={colors.danger} />
                <Text style={styles.returnText}>
                  Devolução solicitada. A coleta reversa já foi acionada — toque para responder.
                </Text>
              </View>
            ) : null}
          </Pressable>
        )}
        ListEmptyComponent={
          <EmptyState
            icon="cash-outline"
            title="Nenhuma venda por aqui"
            description="Publique seu primeiro anúncio e comece a vender para a vizinhança."
            actionLabel="Criar anúncio"
            onAction={() => router.push('/(tabs)/sell')}
          />
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  balanceRow: { flexDirection: 'row', padding: spacing.lg, gap: spacing.md },
  balanceCard: { flex: 1, padding: spacing.lg, borderRadius: radius.lg, backgroundColor: colors.primaryLight },
  balanceLabel: { ...typography.tiny, color: colors.primaryDark, fontWeight: '800' },
  balanceValue: { ...typography.h2, color: colors.primaryDark, marginTop: 2 },
  balanceHint: { ...typography.tiny, marginTop: 3, lineHeight: 14 },

  filters: { flexGrow: 0, marginBottom: spacing.md },
  list: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xxl },

  card: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  date: { ...typography.tiny },
  product: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.sm },
  thumb: { width: 50, height: 58, borderRadius: radius.sm, backgroundColor: colors.surfaceAlt },
  thumbFallback: { alignItems: 'center', justifyContent: 'center' },
  productTitle: { ...typography.small, color: colors.text },
  buyer: { ...typography.tiny, marginTop: 2 },

  split: {
    marginTop: spacing.sm,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  splitLabel: { ...typography.tiny },
  splitValue: { ...typography.small, fontWeight: '800', color: colors.primaryDark, marginTop: 2 },

  returnBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: spacing.md,
    padding: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: colors.dangerLight,
  },
  returnText: { ...typography.tiny, color: colors.danger, flex: 1, marginLeft: 5, lineHeight: 15 },
});
