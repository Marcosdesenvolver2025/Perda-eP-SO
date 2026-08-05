import { useCallback, useEffect, useState } from 'react';
import {
  Dimensions,
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Location from 'expo-location';
import api, { apiError, isNetworkError } from '../../src/api/client';
import { useAuth } from '../../src/contexts/AuthContext';
import { useCart } from '../../src/contexts/CartContext';
import ProductCard from '../../src/components/ProductCard';
import ErrorScreen from '../../src/components/ErrorScreen';
import { Chip, EmptyState, Loading } from '../../src/components/ui';
import { colors, radius, spacing, typography } from '../../src/theme';

const GAP = spacing.md;
const H_PADDING = spacing.lg;
const CARD_WIDTH = (Dimensions.get('window').width - H_PADDING * 2 - GAP) / 2;

const SORTS = [
  { key: 'recent', label: 'novidades', icon: 'sparkles-outline' },
  { key: 'nearest', label: 'perto de mim', icon: 'location-outline' },
  { key: 'price_asc', label: 'menor preço', icon: 'arrow-down-outline' },
  { key: 'popular', label: 'mais amados', icon: 'heart-outline' },
];

export default function Feed() {
  const router = useRouter();
  const { user, isAuthenticated } = useAuth();
  const { count } = useCart();

  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [categoryId, setCategoryId] = useState(null);
  const [sort, setSort] = useState('recent');
  const [coords, setCoords] = useState(null);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [failure, setFailure] = useState(null);

  const load = useCallback(
    async (targetPage = 1, opts = {}) => {
      const params = {
        page: targetPage,
        perPage: 20,
        sort,
        ...(categoryId ? { categoryId } : {}),
        ...(coords ? { lat: coords.latitude, lng: coords.longitude } : {}),
        ...(sort === 'nearest' && coords ? { radiusKm: 50 } : {}),
      };

      try {
        const { data } = await api.get('/products', { params });
        setProducts((prev) => (targetPage === 1 ? data.items : [...prev, ...data.items]));
        setHasMore(data.hasMore);
        setPage(targetPage);
        setFailure(null);
      } catch (error) {
        if (targetPage === 1) {
          setProducts([]);
          // So bloqueamos a tela quando e falha de rede: um erro pontual da
          // API vira lista vazia, sem tirar o app do ar.
          setFailure(isNetworkError(error) ? apiError(error) : null);
        }
      } finally {
        setLoading(false);
        setRefreshing(false);
        setLoadingMore(false);
        opts.onDone?.();
      }
    },
    [sort, categoryId, coords]
  );

  useEffect(() => {
    api
      .get('/categories')
      .then(({ data }) => setCategories(data))
      .catch(() => setCategories([]));
  }, []);

  // Localizacao para o filtro "perto de mim" e para a distancia nos cards.
  useEffect(() => {
    (async () => {
      const { status } = await Location.getForegroundPermissionsAsync();
      if (status !== 'granted') return;
      try {
        const position = await Location.getLastKnownPositionAsync();
        if (position) setCoords(position.coords);
      } catch {
        /* localizacao e opcional */
      }
    })();
  }, []);

  useEffect(() => {
    setLoading(true);
    load(1);
  }, [load]);

  async function handleSort(key) {
    if (key === 'nearest' && !coords) {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status === 'granted') {
        const position = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        setCoords(position.coords);
      }
    }
    setSort(key);
  }

  async function toggleFavorite(product) {
    if (!isAuthenticated) {
      router.push('/(auth)/login');
      return;
    }
    setProducts((prev) =>
      prev.map((item) =>
        item.id === product.id ? { ...item, isFavorite: !item.isFavorite } : item
      )
    );
    try {
      await api.post(`/products/${product.id}/favorite`);
    } catch {
      setProducts((prev) =>
        prev.map((item) =>
          item.id === product.id ? { ...item, isFavorite: product.isFavorite } : item
        )
      );
    }
  }

  const header = (
    <View>
      <View style={styles.categories}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          <Chip label="tudo" selected={!categoryId} onPress={() => setCategoryId(null)} />
          {categories.map((category) => (
            <Chip
              key={category.id}
              label={category.name.toLowerCase()}
              icon={category.icon}
              selected={categoryId === category.id}
              onPress={() => setCategoryId(category.id)}
            />
          ))}
        </ScrollView>
      </View>

      <View style={styles.sorts}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          {SORTS.map((item) => (
            <Chip
              key={item.key}
              label={item.label}
              icon={item.icon}
              selected={sort === item.key}
              onPress={() => handleSort(item.key)}
            />
          ))}
        </ScrollView>
      </View>
    </View>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.topBar}>
        <View>
          <Text style={styles.hello}>
            {user ? `oi, ${user.name.split(' ')[0].toLowerCase()}` : 'bem-vindo'}
          </Text>
          <Text style={styles.brand}>vendas itinga</Text>
        </View>

        <View style={styles.topActions}>
          <Pressable style={styles.iconButton} onPress={() => router.push('/favorites')}>
            <Ionicons name="heart-outline" size={22} color={colors.ink} />
          </Pressable>
          <Pressable style={styles.iconButton} onPress={() => router.push('/cart')}>
            <Ionicons name="bag-outline" size={22} color={colors.ink} />
            {count > 0 ? (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{count}</Text>
              </View>
            ) : null}
          </Pressable>
        </View>
      </View>

      <Pressable style={styles.searchBar} onPress={() => router.push('/(tabs)/search')}>
        <Ionicons name="search" size={18} color={colors.textLight} />
        <Text style={styles.searchPlaceholder}>o que você procura hoje?</Text>
      </Pressable>

      {loading ? (
        <Loading label="Buscando novidades..." />
      ) : failure ? (
        <ErrorScreen
          title="Sem conexão com o servidor"
          message={failure}
          onRetry={() => {
            setLoading(true);
            setFailure(null);
            load(1);
          }}
        />
      ) : (
        <FlatList
          data={products}
          keyExtractor={(item) => item.id}
          numColumns={2}
          columnWrapperStyle={{ gap: GAP }}
          contentContainerStyle={styles.list}
          ListHeaderComponent={header}
          showsVerticalScrollIndicator={false}
          renderItem={({ item }) => (
            <ProductCard product={item} width={CARD_WIDTH} onToggleFavorite={toggleFavorite} />
          )}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              tintColor={colors.primary}
              colors={[colors.primary]}
              onRefresh={() => {
                setRefreshing(true);
                load(1);
              }}
            />
          }
          onEndReachedThreshold={0.4}
          onEndReached={() => {
            if (hasMore && !loadingMore) {
              setLoadingMore(true);
              load(page + 1);
            }
          }}
          ListEmptyComponent={
            <EmptyState
              icon="bag-handle-outline"
              title="Nada por aqui ainda"
              description="Seja o primeiro a anunciar nesta categoria e apareça para toda a vizinhança."
              actionLabel="Anunciar agora"
              onAction={() => router.push('/(tabs)/sell')}
            />
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: H_PADDING,
    paddingTop: spacing.sm,
  },
  hello: { ...typography.tiny, color: colors.textMuted },
  brand: { ...typography.h1, fontSize: 24 },
  topActions: { flexDirection: 'row', gap: spacing.sm },
  iconButton: {
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 4,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: colors.white, fontSize: 10, fontWeight: '800' },

  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: H_PADDING,
    marginTop: spacing.md,
    paddingHorizontal: spacing.lg,
    height: 46,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceAlt,
  },
  searchPlaceholder: { ...typography.body, color: colors.textLight, marginLeft: spacing.sm },

  categories: { marginTop: spacing.lg, paddingLeft: H_PADDING, marginHorizontal: -H_PADDING },
  sorts: {
    marginTop: spacing.md,
    marginBottom: spacing.lg,
    paddingLeft: H_PADDING,
    marginHorizontal: -H_PADDING,
  },
  list: { paddingHorizontal: H_PADDING, paddingBottom: spacing.xxl },
});
