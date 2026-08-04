import { useCallback, useState } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api, { apiError } from '../src/api/client';
import { useAuth } from '../src/contexts/AuthContext';
import { Badge, EmptyState, Loading } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';
import { formatBRL } from '../src/utils/format';

const STATUS_META = {
  ACTIVE: { label: 'Ativo', tone: 'success' },
  PAUSED: { label: 'Pausado', tone: 'warning' },
  SOLD: { label: 'Vendido', tone: 'info' },
  DELETED: { label: 'Excluído', tone: 'danger' },
};

export default function MyProducts() {
  const router = useRouter();
  const { user } = useAuth();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!user?.id) return;
    try {
      const { data } = await api.get('/products', { params: { sellerId: user.id, perPage: 50 } });
      setProducts(data.items);
    } catch {
      setProducts([]);
    } finally {
      setLoading(false);
    }
  }, [user?.id]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  async function toggleStatus(product) {
    const nextStatus = product.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
    try {
      await api.patch(`/products/${product.id}/status`, { status: nextStatus });
      setProducts((prev) =>
        prev.map((item) => (item.id === product.id ? { ...item, status: nextStatus } : item))
      );
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    }
  }

  function confirmDelete(product) {
    Alert.alert('Excluir anúncio', `Remover "${product.title}" do Vendas Itinga?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Excluir',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.delete(`/products/${product.id}`);
            setProducts((prev) => prev.filter((item) => item.id !== product.id));
          } catch (error) {
            Alert.alert('Ops', apiError(error));
          }
        },
      },
    ]);
  }

  if (loading) return <Loading />;

  return (
    <FlatList
      style={{ backgroundColor: colors.white }}
      data={products}
      keyExtractor={(item) => item.id}
      contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxl }}
      renderItem={({ item }) => {
        const meta = STATUS_META[item.status] || STATUS_META.ACTIVE;
        return (
          <View style={styles.card}>
            <Pressable style={styles.main} onPress={() => router.push(`/product/${item.id}`)}>
              {item.images?.[0] ? (
                <Image source={{ uri: item.images[0] }} style={styles.thumb} contentFit="cover" />
              ) : (
                <View style={[styles.thumb, styles.thumbFallback]}>
                  <Ionicons name="image-outline" size={20} color={colors.textLight} />
                </View>
              )}

              <View style={{ flex: 1, marginLeft: spacing.md }}>
                <Text style={styles.title} numberOfLines={2}>
                  {item.title}
                </Text>
                <Text style={styles.price}>{formatBRL(item.priceCents)}</Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4 }}>
                  <Badge label={meta.label} tone={meta.tone} />
                  <View style={{ flexDirection: 'row', alignItems: 'center', marginLeft: spacing.sm }}>
                    <Ionicons name="heart-outline" size={12} color={colors.textLight} />
                    <Text style={styles.stat}>{item.favoriteCount}</Text>
                  </View>
                </View>
              </View>
            </Pressable>

            {item.status !== 'SOLD' ? (
              <View style={styles.actions}>
                <Pressable style={styles.action} onPress={() => toggleStatus(item)}>
                  <Ionicons
                    name={item.status === 'ACTIVE' ? 'pause-outline' : 'play-outline'}
                    size={17}
                    color={colors.textMuted}
                  />
                  <Text style={styles.actionText}>
                    {item.status === 'ACTIVE' ? 'pausar' : 'reativar'}
                  </Text>
                </Pressable>
                <Pressable style={styles.action} onPress={() => confirmDelete(item)}>
                  <Ionicons name="trash-outline" size={17} color={colors.danger} />
                  <Text style={[styles.actionText, { color: colors.danger }]}>excluir</Text>
                </Pressable>
              </View>
            ) : null}
          </View>
        );
      }}
      ListEmptyComponent={
        <EmptyState
          icon="pricetags-outline"
          title="Você ainda não anunciou"
          description="Publique seu primeiro produto e apareça no feed da vizinhança."
          actionLabel="Criar anúncio"
          onAction={() => router.push('/(tabs)/sell')}
        />
      }
    />
  );
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  main: { flexDirection: 'row', alignItems: 'center' },
  thumb: { width: 64, height: 74, borderRadius: radius.sm, backgroundColor: colors.surfaceAlt },
  thumbFallback: { alignItems: 'center', justifyContent: 'center' },
  title: { ...typography.small, color: colors.text },
  price: { ...typography.price, fontSize: 16, marginTop: 2 },
  stat: { ...typography.tiny, marginLeft: 3 },

  actions: {
    flexDirection: 'row',
    marginTop: spacing.md,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  action: { flexDirection: 'row', alignItems: 'center', marginRight: spacing.xl },
  actionText: { ...typography.tiny, fontWeight: '700', marginLeft: 4 },
});
