import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Dimensions,
  FlatList,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Location from 'expo-location';
import api from '../../src/api/client';
import ProductCard from '../../src/components/ProductCard';
import { Button, Chip, EmptyState, Field, Loading } from '../../src/components/ui';
import { colors, radius, spacing, typography } from '../../src/theme';
import { parseToCents, formatBRL } from '../../src/utils/format';

const GAP = spacing.md;
const H_PADDING = spacing.lg;
const CARD_WIDTH = (Dimensions.get('window').width - H_PADDING * 2 - GAP) / 2;

const CONDITIONS = [
  { key: 'NEW', label: 'novo' },
  { key: 'LIKE_NEW', label: 'seminovo' },
  { key: 'USED', label: 'usado' },
];

const RADIUS_OPTIONS = [5, 10, 30, 50];

export default function Search() {
  // Ao chegar da página de um produto, filtramos pela loja do vendedor.
  const { sellerId } = useLocalSearchParams();
  const [term, setTerm] = useState('');
  const [results, setResults] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const [filters, setFilters] = useState({
    categoryId: null,
    condition: null,
    size: '',
    minPrice: '',
    maxPrice: '',
    radiusKm: null,
  });
  const [coords, setCoords] = useState(null);
  const debounce = useRef(null);

  useEffect(() => {
    api
      .get('/categories')
      .then(({ data }) => setCategories(data))
      .catch(() => setCategories([]));
  }, []);

  const search = useCallback(
    async (query = term, activeFilters = filters) => {
      setLoading(true);
      setSearched(true);
      try {
        const { data } = await api.get('/products', {
          params: {
            ...(query ? { q: query } : {}),
            ...(sellerId ? { sellerId } : {}),
            ...(activeFilters.categoryId ? { categoryId: activeFilters.categoryId } : {}),
            ...(activeFilters.condition ? { condition: activeFilters.condition } : {}),
            ...(activeFilters.size ? { size: activeFilters.size } : {}),
            ...(activeFilters.minPrice ? { minPrice: parseToCents(activeFilters.minPrice) } : {}),
            ...(activeFilters.maxPrice ? { maxPrice: parseToCents(activeFilters.maxPrice) } : {}),
            ...(activeFilters.radiusKm && coords
              ? { lat: coords.latitude, lng: coords.longitude, radiusKm: activeFilters.radiusKm, sort: 'nearest' }
              : {}),
            perPage: 30,
          },
        });
        setResults(data.items);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    },
    [term, filters, coords, sellerId]
  );

  // Busca com debounce enquanto o usuario digita.
  useEffect(() => {
    if (!term) return undefined;
    clearTimeout(debounce.current);
    debounce.current = setTimeout(() => search(term), 450);
    return () => clearTimeout(debounce.current);
  }, [term, search]);

  // Chegou filtrando por vendedor: lista a loja dele na hora.
  useEffect(() => {
    if (sellerId) search('');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sellerId]);

  async function enableProximity(radiusKm) {
    if (!coords) {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return;
      const position = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setCoords(position.coords);
    }
    setFilters((prev) => ({ ...prev, radiusKm: prev.radiusKm === radiusKm ? null : radiusKm }));
  }

  const activeFilterCount = [
    filters.categoryId,
    filters.condition,
    filters.size,
    filters.minPrice,
    filters.maxPrice,
    filters.radiusKm,
  ].filter(Boolean).length;

  async function toggleFavorite(product) {
    setResults((prev) =>
      prev.map((item) => (item.id === product.id ? { ...item, isFavorite: !item.isFavorite } : item))
    );
    try {
      await api.post(`/products/${product.id}/favorite`);
    } catch {
      setResults((prev) =>
        prev.map((item) =>
          item.id === product.id ? { ...item, isFavorite: product.isFavorite } : item
        )
      );
    }
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.searchRow}>
        <View style={styles.searchBar}>
          <Ionicons name="search" size={18} color={colors.textLight} />
          <TextInput
            value={term}
            onChangeText={setTerm}
            placeholder="busque por item, marca ou categoria"
            placeholderTextColor={colors.textLight}
            style={styles.input}
            returnKeyType="search"
            onSubmitEditing={() => search()}
            autoCorrect={false}
          />
          {term ? (
            <Pressable onPress={() => setTerm('')} hitSlop={8}>
              <Ionicons name="close-circle" size={18} color={colors.textLight} />
            </Pressable>
          ) : null}
        </View>

        <Pressable style={styles.filterButton} onPress={() => setFiltersOpen(true)}>
          <Ionicons name="options-outline" size={20} color={colors.ink} />
          {activeFilterCount > 0 ? (
            <View style={styles.filterBadge}>
              <Text style={styles.filterBadgeText}>{activeFilterCount}</Text>
            </View>
          ) : null}
        </Pressable>
      </View>

      {loading ? (
        <Loading label="Procurando..." />
      ) : (
        <FlatList
          data={results}
          keyExtractor={(item) => item.id}
          numColumns={2}
          columnWrapperStyle={{ gap: GAP }}
          contentContainerStyle={styles.list}
          showsVerticalScrollIndicator={false}
          renderItem={({ item }) => (
            <ProductCard product={item} width={CARD_WIDTH} onToggleFavorite={toggleFavorite} />
          )}
          ListHeaderComponent={
            results.length ? <Text style={styles.resultCount}>{results.length} resultado(s)</Text> : null
          }
          ListEmptyComponent={
            searched ? (
              <EmptyState
                icon="search-outline"
                title="Nenhum resultado"
                description="Tente outra palavra ou ajuste os filtros de preço e distância."
              />
            ) : (
              <EmptyState
                icon="compass-outline"
                title="O que você procura?"
                description="Busque por nome, marca ou categoria — e use os filtros para achar itens pertinho de você."
              />
            )
          }
        />
      )}

      <Modal visible={filtersOpen} animationType="slide" transparent onRequestClose={() => setFiltersOpen(false)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalSheet}>
            <View style={styles.modalHandle} />
            <Text style={styles.modalTitle}>filtros</Text>

            <ScrollView showsVerticalScrollIndicator={false}>
              <Text style={styles.filterLabel}>categoria</Text>
              <View style={styles.chipWrap}>
                {categories.map((category) => (
                  <Chip
                    key={category.id}
                    label={category.name.toLowerCase()}
                    selected={filters.categoryId === category.id}
                    onPress={() =>
                      setFilters((prev) => ({
                        ...prev,
                        categoryId: prev.categoryId === category.id ? null : category.id,
                      }))
                    }
                    style={{ marginBottom: spacing.sm }}
                  />
                ))}
              </View>

              <Text style={styles.filterLabel}>condição</Text>
              <View style={styles.chipWrap}>
                {CONDITIONS.map((item) => (
                  <Chip
                    key={item.key}
                    label={item.label}
                    selected={filters.condition === item.key}
                    onPress={() =>
                      setFilters((prev) => ({
                        ...prev,
                        condition: prev.condition === item.key ? null : item.key,
                      }))
                    }
                    style={{ marginBottom: spacing.sm }}
                  />
                ))}
              </View>

              <Text style={styles.filterLabel}>distância</Text>
              <View style={styles.chipWrap}>
                {RADIUS_OPTIONS.map((km) => (
                  <Chip
                    key={km}
                    label={`até ${km} km`}
                    icon="location-outline"
                    selected={filters.radiusKm === km}
                    onPress={() => enableProximity(km)}
                    style={{ marginBottom: spacing.sm }}
                  />
                ))}
              </View>

              <Text style={styles.filterLabel}>preço</Text>
              <View style={styles.priceRow}>
                <Field
                  placeholder="mínimo"
                  keyboardType="numeric"
                  value={filters.minPrice ? formatBRL(parseToCents(filters.minPrice)) : ''}
                  onChangeText={(text) => setFilters((prev) => ({ ...prev, minPrice: text }))}
                  style={{ flex: 1, marginRight: spacing.sm }}
                />
                <Field
                  placeholder="máximo"
                  keyboardType="numeric"
                  value={filters.maxPrice ? formatBRL(parseToCents(filters.maxPrice)) : ''}
                  onChangeText={(text) => setFilters((prev) => ({ ...prev, maxPrice: text }))}
                  style={{ flex: 1 }}
                />
              </View>

              <Text style={styles.filterLabel}>tamanho</Text>
              <Field
                placeholder="ex: M, 42, único"
                value={filters.size}
                onChangeText={(text) => setFilters((prev) => ({ ...prev, size: text }))}
                autoCapitalize="characters"
              />
            </ScrollView>

            <View style={styles.modalActions}>
              <Button
                title="Limpar"
                variant="outline"
                style={{ flex: 1, marginRight: spacing.sm }}
                onPress={() => {
                  const cleared = {
                    categoryId: null,
                    condition: null,
                    size: '',
                    minPrice: '',
                    maxPrice: '',
                    radiusKm: null,
                  };
                  setFilters(cleared);
                  search(term, cleared);
                  setFiltersOpen(false);
                }}
              />
              <Button
                title="Aplicar"
                style={{ flex: 1 }}
                onPress={() => {
                  setFiltersOpen(false);
                  search();
                }}
              />
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: H_PADDING,
    paddingTop: spacing.sm,
    gap: spacing.sm,
  },
  searchBar: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    height: 46,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceAlt,
  },
  input: { flex: 1, marginLeft: spacing.sm, fontSize: 15, color: colors.ink },
  filterButton: {
    width: 46,
    height: 46,
    borderRadius: 23,
    borderWidth: 1.5,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterBadge: {
    position: 'absolute',
    top: 2,
    right: 2,
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterBadgeText: { color: colors.white, fontSize: 10, fontWeight: '800' },

  list: { paddingHorizontal: H_PADDING, paddingTop: spacing.lg, paddingBottom: spacing.xxl },
  resultCount: { ...typography.small, marginBottom: spacing.md },

  modalBackdrop: { flex: 1, backgroundColor: colors.overlay, justifyContent: 'flex-end' },
  modalSheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    padding: spacing.xl,
    maxHeight: '86%',
  },
  modalHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.borderStrong,
    alignSelf: 'center',
    marginBottom: spacing.lg,
  },
  modalTitle: { ...typography.h2, marginBottom: spacing.lg },
  filterLabel: {
    ...typography.small,
    fontWeight: '800',
    color: colors.ink,
    marginBottom: spacing.sm,
    marginTop: spacing.md,
  },
  chipWrap: { flexDirection: 'row', flexWrap: 'wrap' },
  priceRow: { flexDirection: 'row' },
  modalActions: { flexDirection: 'row', marginTop: spacing.lg },
});
