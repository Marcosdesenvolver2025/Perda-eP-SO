import { useEffect, useState } from 'react';
import {
  Alert,
  Dimensions,
  FlatList,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api, { apiError } from '../../src/api/client';
import { useAuth } from '../../src/contexts/AuthContext';
import { useCart } from '../../src/contexts/CartContext';
import { Badge, Button, Divider, Loading } from '../../src/components/ui';
import { colors, radius, shadow, spacing, typography } from '../../src/theme';
import { CONDITION_LABELS, formatBRL } from '../../src/utils/format';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

export default function ProductDetail() {
  const { id } = useLocalSearchParams();
  const router = useRouter();
  const { isAuthenticated, user } = useAuth();
  const { addItem } = useCart();

  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [imageIndex, setImageIndex] = useState(0);

  useEffect(() => {
    api
      .get(`/products/${id}`)
      .then(({ data }) => setProduct(data))
      .catch(() => setProduct(null))
      .finally(() => setLoading(false));
  }, [id]);

  async function handleFavorite() {
    if (!isAuthenticated) return router.push('/(auth)/login');
    setProduct((prev) => ({ ...prev, isFavorite: !prev.isFavorite }));
    try {
      await api.post(`/products/${id}/favorite`);
    } catch {
      setProduct((prev) => ({ ...prev, isFavorite: !prev.isFavorite }));
    }
    return undefined;
  }

  async function handleAddToCart(goToCart = false) {
    if (!isAuthenticated) return router.push('/(auth)/login');

    setAdding(true);
    const result = await addItem(product.id, 1);
    setAdding(false);

    if (!result.ok) {
      Alert.alert('Ops', result.message);
      return undefined;
    }
    if (goToCart) return router.push('/cart');

    Alert.alert('Adicionado à sacola', 'O produto está esperando por você na sacola.', [
      { text: 'Continuar vendo', style: 'cancel' },
      { text: 'Ir para a sacola', onPress: () => router.push('/cart') },
    ]);
    return undefined;
  }

  async function handleChat() {
    if (!isAuthenticated) return router.push('/(auth)/login');
    try {
      const { data } = await api.post('/conversations', { productId: product.id });
      return router.push(`/chat/${data.id}`);
    } catch (error) {
      Alert.alert('Ops', apiError(error));
      return undefined;
    }
  }

  if (loading) return <Loading />;

  if (!product) {
    return (
      <View style={styles.center}>
        <Ionicons name="alert-circle-outline" size={40} color={colors.textLight} />
        <Text style={styles.notFound}>Produto não encontrado ou já vendido.</Text>
        <Button title="Voltar ao feed" variant="ghost" onPress={() => router.replace('/(tabs)')} />
      </View>
    );
  }

  const isOwner = product.seller?.id === user?.id;
  const isSold = product.status === 'SOLD';

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 120 }}>
        {/* Galeria com paginacao - a foto e a estrela do anuncio. */}
        <View>
          <FlatList
            data={product.images.length ? product.images : [null]}
            keyExtractor={(item, index) => `${item || 'empty'}-${index}`}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            onMomentumScrollEnd={(event) =>
              setImageIndex(Math.round(event.nativeEvent.contentOffset.x / SCREEN_WIDTH))
            }
            renderItem={({ item }) =>
              item ? (
                <Image source={{ uri: item }} style={styles.image} contentFit="cover" transition={200} />
              ) : (
                <View style={[styles.image, styles.imageFallback]}>
                  <Ionicons name="image-outline" size={40} color={colors.textLight} />
                </View>
              )
            }
          />

          {product.images.length > 1 ? (
            <View style={styles.dots}>
              {product.images.map((image, index) => (
                <View key={image} style={[styles.dot, index === imageIndex && styles.dotActive]} />
              ))}
            </View>
          ) : null}

          <Pressable style={styles.favoriteFloat} onPress={handleFavorite}>
            <Ionicons
              name={product.isFavorite ? 'heart' : 'heart-outline'}
              size={22}
              color={product.isFavorite ? colors.danger : colors.ink}
            />
          </Pressable>
        </View>

        <View style={styles.content}>
          <View style={styles.badges}>
            <Badge label={CONDITION_LABELS[product.condition]} tone="neutral" />
            {product.shippingMode === 'PLATFORM' ? (
              <Badge label="Entrega Vendas Itinga" tone="success" style={{ marginLeft: spacing.sm }} />
            ) : (
              <Badge label="Entrega do vendedor" tone="info" style={{ marginLeft: spacing.sm }} />
            )}
            {isSold ? <Badge label="Vendido" tone="danger" style={{ marginLeft: spacing.sm }} /> : null}
          </View>

          <Text style={styles.price}>{formatBRL(product.priceCents)}</Text>
          <Text style={styles.title}>{product.title}</Text>

          {product.distanceKm != null ? (
            <View style={styles.locationRow}>
              <Ionicons name="location-outline" size={14} color={colors.primaryDark} />
              <Text style={styles.location}>
                a {product.distanceKm} km de você
                {product.city ? ` · ${product.city}/${product.state}` : ''}
              </Text>
            </View>
          ) : product.city ? (
            <View style={styles.locationRow}>
              <Ionicons name="location-outline" size={14} color={colors.textMuted} />
              <Text style={styles.location}>
                {product.city}/{product.state}
              </Text>
            </View>
          ) : null}

          <Divider style={{ marginVertical: spacing.lg }} />

          <Text style={styles.sectionTitle}>descrição</Text>
          <Text style={styles.description}>{product.description}</Text>

          <View style={styles.specs}>
            {product.brand ? <Spec label="Marca" value={product.brand} /> : null}
            {product.size ? <Spec label="Tamanho" value={product.size} /> : null}
            <Spec label="Peso" value={`${(product.weightGrams / 1000).toFixed(2)} kg`} />
            <Spec
              label="Dimensões"
              value={`${product.dimensions.heightCm} × ${product.dimensions.widthCm} × ${product.dimensions.lengthCm} cm`}
            />
          </View>

          <Divider style={{ marginVertical: spacing.lg }} />

          <Pressable style={styles.seller} onPress={() => router.push(`/(tabs)/search?sellerId=${product.seller.id}`)}>
            {product.seller?.avatarUrl ? (
              <Image source={{ uri: product.seller.avatarUrl }} style={styles.sellerAvatar} contentFit="cover" />
            ) : (
              <View style={[styles.sellerAvatar, styles.avatarFallback]}>
                <Text style={styles.avatarLetter}>{product.seller?.name?.[0]?.toUpperCase()}</Text>
              </View>
            )}
            <View style={{ flex: 1, marginLeft: spacing.md }}>
              <Text style={styles.sellerName}>{product.seller?.name}</Text>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <Ionicons name="star" size={12} color={colors.warning} />
                <Text style={styles.sellerRating}>
                  {product.seller?.ratingCount
                    ? `${product.seller.ratingAvg.toFixed(1)} · ${product.seller.ratingCount} avaliações`
                    : 'Vendedor novo por aqui'}
                </Text>
              </View>
            </View>
          </Pressable>

          <View style={styles.protection}>
            <Ionicons name="shield-checkmark" size={20} color={colors.primaryDark} />
            <Text style={styles.protectionText}>
              <Text style={{ fontWeight: '800' }}>Compra protegida.</Text> Você tem 4 dias após receber
              para testar o produto. Se não for como o anunciado, é só pedir o reembolso pelo app — a
              gente busca na sua casa.
            </Text>
          </View>
        </View>
      </ScrollView>

      {!isOwner && !isSold ? (
        <View style={styles.bottomBar}>
          <Pressable style={styles.chatButton} onPress={handleChat}>
            <Ionicons name="chatbubble-ellipses-outline" size={22} color={colors.ink} />
          </Pressable>
          <Button
            title="Adicionar"
            variant="outline"
            onPress={() => handleAddToCart(false)}
            loading={adding}
            style={{ flex: 1, marginRight: spacing.sm }}
          />
          <Button title="Comprar" onPress={() => handleAddToCart(true)} style={{ flex: 1.3 }} />
        </View>
      ) : isOwner ? (
        <View style={styles.bottomBar}>
          <Button
            title="Gerenciar anúncio"
            variant="outline"
            icon="create-outline"
            style={{ flex: 1 }}
            onPress={() => router.push('/my-products')}
          />
        </View>
      ) : null}
    </View>
  );
}

function Spec({ label, value }) {
  return (
    <View style={styles.spec}>
      <Text style={styles.specLabel}>{label}</Text>
      <Text style={styles.specValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  notFound: { ...typography.body, marginVertical: spacing.md, textAlign: 'center' },

  image: { width: SCREEN_WIDTH, height: SCREEN_WIDTH * 1.1, backgroundColor: colors.surfaceAlt },
  imageFallback: { alignItems: 'center', justifyContent: 'center' },
  dots: { flexDirection: 'row', alignSelf: 'center', position: 'absolute', bottom: 14 },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255,255,255,0.6)',
    marginHorizontal: 3,
  },
  dotActive: { backgroundColor: colors.white, width: 18 },
  favoriteFloat: {
    position: 'absolute',
    right: spacing.lg,
    bottom: -22,
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow.float,
  },

  content: { padding: spacing.lg, paddingTop: spacing.xl },
  badges: { flexDirection: 'row', marginBottom: spacing.md, flexWrap: 'wrap' },
  price: { ...typography.h1, fontSize: 30 },
  title: { ...typography.body, fontSize: 17, marginTop: 4, lineHeight: 23 },
  locationRow: { flexDirection: 'row', alignItems: 'center', marginTop: spacing.sm },
  location: { ...typography.small, marginLeft: 4 },

  sectionTitle: { ...typography.h3, marginBottom: spacing.sm },
  description: { ...typography.body, lineHeight: 22, color: colors.text },

  specs: { marginTop: spacing.lg, borderRadius: radius.md, backgroundColor: colors.surfaceAlt, padding: spacing.md },
  spec: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 5 },
  specLabel: { ...typography.small },
  specValue: { ...typography.small, color: colors.ink, fontWeight: '700' },

  seller: { flexDirection: 'row', alignItems: 'center' },
  sellerAvatar: { width: 46, height: 46, borderRadius: 23, backgroundColor: colors.primaryLight },
  avatarFallback: { alignItems: 'center', justifyContent: 'center' },
  avatarLetter: { ...typography.h3, color: colors.primaryDark },
  sellerName: { ...typography.h3, fontSize: 15 },
  sellerRating: { ...typography.tiny, marginLeft: 3 },

  protection: {
    flexDirection: 'row',
    marginTop: spacing.lg,
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.primarySoft,
  },
  protectionText: { ...typography.small, flex: 1, marginLeft: spacing.md, lineHeight: 19 },

  bottomBar: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.lg,
    paddingBottom: spacing.xl,
    backgroundColor: colors.white,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: spacing.sm,
  },
  chatButton: {
    width: 50,
    height: 50,
    borderRadius: 25,
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
