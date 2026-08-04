import { useCallback, useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api from '../src/api/client';
import { Badge, EmptyState, Loading } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TONE,
  daysLeft,
  formatBRL,
  formatDate,
} from '../src/utils/format';

export default function Purchases() {
  const router = useRouter();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get('/orders');
      setOrders(data);
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

  if (loading) return <Loading />;

  return (
    <FlatList
      style={{ backgroundColor: colors.white }}
      data={orders}
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
      renderItem={({ item }) => {
        const testDays = item.status === 'IN_TEST' ? daysLeft(item.testEndsAt) : 0;
        return (
          <Pressable
            style={({ pressed }) => [styles.card, pressed && { opacity: 0.9 }]}
            onPress={() => router.push(`/order/${item.id}`)}
          >
            <View style={styles.cardHeader}>
              <Badge
                label={ORDER_STATUS_LABELS[item.status]}
                tone={ORDER_STATUS_TONE[item.status]}
              />
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
                  <Text style={styles.productSeller}>vendido por {item.seller?.name}</Text>
                </View>
              </View>
            ))}

            {testDays > 0 ? (
              <View style={styles.testBanner}>
                <Ionicons name="time-outline" size={15} color={colors.primaryDark} />
                <Text style={styles.testText}>
                  {testDays === 1 ? 'último dia' : `${testDays} dias`} do período de teste — se algo
                  estiver errado, peça o reembolso.
                </Text>
              </View>
            ) : null}

            <View style={styles.cardFooter}>
              <Text style={styles.total}>{formatBRL(item.totalCents)}</Text>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <Text style={styles.detailLink}>ver detalhes</Text>
                <Ionicons name="chevron-forward" size={14} color={colors.primaryDark} />
              </View>
            </View>
          </Pressable>
        );
      }}
      ListEmptyComponent={
        <EmptyState
          icon="bag-check-outline"
          title="Nenhuma compra ainda"
          description="Quando você comprar algo, o pedido e o status da entrega aparecem aqui."
          actionLabel="Explorar produtos"
          onAction={() => router.replace('/(tabs)')}
        />
      }
    />
  );
}

const styles = StyleSheet.create({
  list: { padding: spacing.lg, paddingBottom: spacing.xxl },
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
  productSeller: { ...typography.tiny, marginTop: 2 },

  testBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: colors.primaryLight,
    marginTop: spacing.sm,
  },
  testText: { ...typography.tiny, color: colors.primaryDark, flex: 1, marginLeft: 5, lineHeight: 15 },

  cardFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing.md,
  },
  total: { ...typography.price },
  detailLink: { ...typography.tiny, color: colors.primaryDark, fontWeight: '800' },
});
