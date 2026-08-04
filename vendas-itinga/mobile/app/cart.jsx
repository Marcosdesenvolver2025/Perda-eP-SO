import { useCallback, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { useCart } from '../src/contexts/CartContext';
import { Button, Divider, EmptyState, Loading, SummaryRow } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';
import { formatBRL } from '../src/utils/format';

export default function Cart() {
  const router = useRouter();
  const { cart, loading, refresh, removeItem } = useCart();
  const [removing, setRemoving] = useState(null);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  if (loading && !cart.groups.length) return <Loading />;

  if (!cart.groups.length) {
    return (
      <EmptyState
        icon="bag-outline"
        title="Sua sacola está vazia"
        description="Explore o feed e adicione aquele achado que você não pode perder."
        actionLabel="Explorar produtos"
        onAction={() => router.replace('/(tabs)')}
      />
    );
  }

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* Um bloco por vendedor: cada um vira um pedido com frete proprio. */}
        {cart.groups.map((group) => (
          <View key={group.seller.id} style={styles.group}>
            <View style={styles.groupHeader}>
              <Ionicons name="storefront-outline" size={16} color={colors.primaryDark} />
              <Text style={styles.groupSeller}>{group.seller.name}</Text>
            </View>

            {group.items.map((item) => (
              <View key={item.id} style={styles.item}>
                {item.image ? (
                  <Image source={{ uri: item.image }} style={styles.thumb} contentFit="cover" />
                ) : (
                  <View style={[styles.thumb, styles.thumbFallback]}>
                    <Ionicons name="image-outline" size={20} color={colors.textLight} />
                  </View>
                )}

                <View style={styles.itemInfo}>
                  <Text style={styles.itemTitle} numberOfLines={2}>
                    {item.title}
                  </Text>
                  <Text style={styles.itemPrice}>{formatBRL(item.priceCents)}</Text>
                  {item.quantity > 1 ? (
                    <Text style={styles.itemQty}>quantidade: {item.quantity}</Text>
                  ) : null}
                </View>

                <Pressable
                  hitSlop={10}
                  onPress={async () => {
                    setRemoving(item.id);
                    await removeItem(item.id);
                    setRemoving(null);
                  }}
                >
                  <Ionicons
                    name={removing === item.id ? 'hourglass-outline' : 'trash-outline'}
                    size={18}
                    color={colors.textMuted}
                  />
                </Pressable>
              </View>
            ))}

            <Divider style={{ marginVertical: spacing.md }} />
            <View style={styles.shippingRow}>
              <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1 }}>
                <Ionicons
                  name={group.shippingMode === 'PLATFORM' ? 'bicycle-outline' : 'person-outline'}
                  size={15}
                  color={colors.textMuted}
                />
                <Text style={styles.shippingLabel}>{group.shippingLabel}</Text>
              </View>
              <Text style={styles.shippingValue}>
                {group.shippingCents > 0 ? formatBRL(group.shippingCents) : 'a combinar'}
              </Text>
            </View>
          </View>
        ))}

        <View style={styles.summary}>
          <SummaryRow label="Produtos" value={formatBRL(cart.itemsTotalCents)} />
          <SummaryRow label="Entrega" value={formatBRL(cart.shippingTotalCents)} />
          <Divider style={{ marginVertical: spacing.sm }} />
          <SummaryRow label="Total" value={formatBRL(cart.totalCents)} strong />
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <View style={{ flex: 1 }}>
          <Text style={styles.bottomLabel}>total</Text>
          <Text style={styles.bottomValue}>{formatBRL(cart.totalCents)}</Text>
        </View>
        <Button
          title="Ir para o pagamento"
          onPress={() => router.push('/checkout')}
          style={{ flex: 1.4 }}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  content: { padding: spacing.lg, paddingBottom: spacing.xxl },

  group: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginBottom: spacing.lg,
  },
  groupHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.md },
  groupSeller: { ...typography.small, fontWeight: '800', color: colors.ink, marginLeft: 6 },

  item: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.md },
  thumb: { width: 62, height: 72, borderRadius: radius.sm, backgroundColor: colors.surfaceAlt },
  thumbFallback: { alignItems: 'center', justifyContent: 'center' },
  itemInfo: { flex: 1, marginHorizontal: spacing.md },
  itemTitle: { ...typography.small, color: colors.text },
  itemPrice: { ...typography.price, fontSize: 16, marginTop: 3 },
  itemQty: { ...typography.tiny, marginTop: 1 },

  shippingRow: { flexDirection: 'row', alignItems: 'center' },
  shippingLabel: { ...typography.small, marginLeft: 5 },
  shippingValue: { ...typography.small, fontWeight: '700', color: colors.ink },

  summary: {
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.surfaceAlt,
  },

  bottomBar: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.lg,
    paddingBottom: spacing.xl,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.white,
  },
  bottomLabel: { ...typography.tiny },
  bottomValue: { ...typography.h2 },
});
