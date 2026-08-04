import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { colors, radius, spacing, typography } from '../theme';
import { CONDITION_LABELS, formatBRL } from '../utils/format';

/**
 * Card do feed no estilo Enjoei: a foto ocupa quase todo o card, com
 * preco em destaque e o vendedor logo abaixo. O toque no coracao
 * favorita sem sair da listagem.
 */
function ProductCardComponent({ product, onToggleFavorite, width }) {
  const router = useRouter();
  const image = product.images?.[0];

  return (
    <Pressable
      onPress={() => router.push(`/product/${product.id}`)}
      style={({ pressed }) => [styles.card, { width }, pressed && { opacity: 0.9 }]}
    >
      <View style={styles.imageWrapper}>
        {image ? (
          <Image
            source={{ uri: image }}
            style={styles.image}
            contentFit="cover"
            transition={220}
            recyclingKey={product.id}
          />
        ) : (
          <View style={[styles.image, styles.imageFallback]}>
            <Ionicons name="image-outline" size={30} color={colors.textLight} />
          </View>
        )}

        <Pressable
          hitSlop={10}
          onPress={(event) => {
            event.stopPropagation?.();
            onToggleFavorite?.(product);
          }}
          style={styles.favorite}
        >
          <Ionicons
            name={product.isFavorite ? 'heart' : 'heart-outline'}
            size={18}
            color={product.isFavorite ? colors.danger : colors.ink}
          />
        </Pressable>

        {product.condition === 'NEW' ? (
          <View style={styles.tag}>
            <Text style={styles.tagText}>novo</Text>
          </View>
        ) : null}
      </View>

      <View style={styles.info}>
        <Text style={styles.price}>{formatBRL(product.priceCents)}</Text>
        <Text style={styles.title} numberOfLines={2}>
          {product.title}
        </Text>

        <View style={styles.metaRow}>
          <Text style={styles.meta} numberOfLines={1}>
            {product.seller?.name || CONDITION_LABELS[product.condition]}
          </Text>
          {product.distanceKm != null ? (
            <Text style={styles.distance}>{product.distanceKm} km</Text>
          ) : null}
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { marginBottom: spacing.xl },
  imageWrapper: {
    width: '100%',
    aspectRatio: 0.82,
    borderRadius: radius.lg,
    overflow: 'hidden',
    backgroundColor: colors.surfaceAlt,
  },
  image: { width: '100%', height: '100%' },
  imageFallback: { alignItems: 'center', justifyContent: 'center' },
  favorite: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: 'rgba(255,255,255,0.92)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  tag: {
    position: 'absolute',
    top: 8,
    left: 8,
    backgroundColor: colors.primary,
    paddingHorizontal: 9,
    paddingVertical: 3,
    borderRadius: radius.pill,
  },
  tagText: { color: colors.white, fontSize: 10, fontWeight: '800', letterSpacing: 0.3 },

  info: { paddingTop: spacing.sm, paddingHorizontal: 2 },
  price: { ...typography.price, fontSize: 16 },
  title: { ...typography.small, color: colors.text, marginTop: 2, lineHeight: 17 },
  metaRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 4 },
  meta: { ...typography.tiny, flex: 1 },
  distance: { ...typography.tiny, color: colors.primaryDark, fontWeight: '700' },
});

export default memo(ProductCardComponent);
