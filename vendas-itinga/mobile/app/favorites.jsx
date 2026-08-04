import { useCallback, useState } from 'react';
import { Dimensions, FlatList } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import api from '../src/api/client';
import ProductCard from '../src/components/ProductCard';
import { EmptyState, Loading } from '../src/components/ui';
import { colors, spacing } from '../src/theme';

const GAP = spacing.md;
const H_PADDING = spacing.lg;
const CARD_WIDTH = (Dimensions.get('window').width - H_PADDING * 2 - GAP) / 2;

export default function Favorites() {
  const router = useRouter();
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get('/favorites');
      setProducts(data);
    } catch {
      setProducts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  async function toggleFavorite(product) {
    setProducts((prev) => prev.filter((item) => item.id !== product.id));
    try {
      await api.post(`/products/${product.id}/favorite`);
    } catch {
      load();
    }
  }

  if (loading) return <Loading />;

  return (
    <FlatList
      style={{ backgroundColor: colors.white }}
      data={products}
      keyExtractor={(item) => item.id}
      numColumns={2}
      columnWrapperStyle={{ gap: GAP }}
      contentContainerStyle={{ padding: H_PADDING, paddingBottom: spacing.xxl }}
      renderItem={({ item }) => (
        <ProductCard product={item} width={CARD_WIDTH} onToggleFavorite={toggleFavorite} />
      )}
      ListEmptyComponent={
        <EmptyState
          icon="heart-outline"
          title="Nenhum favorito ainda"
          description="Toque no coração dos produtos que você amou para guardá-los aqui."
          actionLabel="Explorar produtos"
          onAction={() => router.replace('/(tabs)')}
        />
      }
    />
  );
}
