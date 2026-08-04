import { useEffect, useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import * as Location from 'expo-location';
import api, { apiError } from '../../src/api/client';
import { useAuth } from '../../src/contexts/AuthContext';
import { Button, Chip, Field } from '../../src/components/ui';
import { colors, radius, spacing, typography } from '../../src/theme';
import { formatBRL, parseToCents } from '../../src/utils/format';

const MAX_PHOTOS = 8;

const CONDITIONS = [
  { key: 'NEW', label: 'novo' },
  { key: 'LIKE_NEW', label: 'seminovo' },
  { key: 'USED', label: 'usado' },
];

const EMPTY_FORM = {
  title: '',
  description: '',
  categoryId: null,
  condition: 'USED',
  size: '',
  brand: '',
  price: '',
  weight: '',
  height: '',
  width: '',
  length: '',
  shippingMode: 'PLATFORM',
};

export default function Sell() {
  const router = useRouter();
  const { isAuthenticated, user, refreshUser } = useAuth();

  const [rules, setRules] = useState({
    minProductPriceCents: 1000,
    maxProductWeightGrams: 20000,
    maxProductHeightCm: 60,
    platformFeePercentFleet: 20,
    platformFeePercentOwn: 15,
  });
  const [categories, setCategories] = useState([]);
  const [photos, setPhotos] = useState([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get('/config/rules').then(({ data }) => setRules(data)).catch(() => {});
    api.get('/categories').then(({ data }) => setCategories(data)).catch(() => {});
  }, []);

  const priceCents = parseToCents(form.price);
  const feePercent =
    form.shippingMode === 'PLATFORM' ? rules.platformFeePercentFleet : rules.platformFeePercentOwn;
  const sellerReceives = Math.round(priceCents * (1 - feePercent / 100));

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: null }));
  }

  /** Seleciona a foto, corta e comprime antes de enviar. */
  async function pickPhoto(fromCamera = false) {
    if (photos.length >= MAX_PHOTOS) {
      Alert.alert('Limite de fotos', `Você pode enviar até ${MAX_PHOTOS} fotos.`);
      return;
    }

    const permission = fromCamera
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();

    if (!permission.granted) {
      Alert.alert('Permissão necessária', 'Autorize o acesso para adicionar fotos ao anúncio.');
      return;
    }

    const picker = fromCamera ? ImagePicker.launchCameraAsync : ImagePicker.launchImageLibraryAsync;
    const result = await picker({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true, // ferramenta de corte nativa
      aspect: [4, 5],
      quality: 0.9,
    });

    if (result.canceled) return;

    setUploading(true);
    try {
      // Redimensiona e comprime: fotos leves = feed rapido.
      const processed = await ImageManipulator.manipulateAsync(
        result.assets[0].uri,
        [{ resize: { width: 1200 } }],
        { compress: 0.7, format: ImageManipulator.SaveFormat.JPEG, base64: true }
      );

      const { data } = await api.post('/uploads', {
        image: `data:image/jpeg;base64,${processed.base64}`,
      });

      setPhotos((prev) => [...prev, data.url]);
    } catch (error) {
      Alert.alert('Ops', apiError(error, 'Não foi possível enviar a foto.'));
    } finally {
      setUploading(false);
    }
  }

  function validate() {
    const nextErrors = {};

    if (!photos.length) nextErrors.photos = 'Adicione pelo menos 1 foto.';
    if (form.title.trim().length < 3) nextErrors.title = 'Dê um título ao seu anúncio.';
    if (form.description.trim().length < 10) nextErrors.description = 'Descreva o produto com mais detalhes.';

    if (priceCents < rules.minProductPriceCents) {
      nextErrors.price = `O valor mínimo por produto é ${formatBRL(rules.minProductPriceCents)}.`;
    }

    const weightGrams = Math.round(Number(String(form.weight).replace(',', '.')) * 1000);
    if (!weightGrams || weightGrams <= 0) {
      nextErrors.weight = 'Informe o peso.';
    } else if (weightGrams > rules.maxProductWeightGrams) {
      nextErrors.weight = `Peso máximo: ${rules.maxProductWeightGrams / 1000} kg.`;
    }

    const heightCm = Number(form.height);
    if (!heightCm || heightCm <= 0) {
      nextErrors.height = 'Informe a altura.';
    } else if (heightCm > rules.maxProductHeightCm) {
      nextErrors.height = `Altura máxima: ${rules.maxProductHeightCm} cm.`;
    }

    if (!Number(form.width)) nextErrors.width = 'Informe a largura.';
    if (!Number(form.length)) nextErrors.length = 'Informe o comprimento.';

    setErrors(nextErrors);
    return { valid: Object.keys(nextErrors).length === 0, weightGrams, heightCm };
  }

  async function handleSubmit() {
    if (!isAuthenticated) {
      router.push('/(auth)/login');
      return;
    }
    if (!user?.isSeller) {
      Alert.alert(
        'Falta pouco!',
        'Cadastre seus dados bancários para receber pelas vendas. É rápido.',
        [
          { text: 'Agora não', style: 'cancel' },
          { text: 'Cadastrar', onPress: () => router.push('/seller-account') },
        ]
      );
      return;
    }

    const { valid, weightGrams, heightCm } = validate();
    if (!valid) return;

    setSaving(true);
    try {
      // Localizacao aproximada do anuncio (usada no filtro de proximidade).
      let coords = null;
      const permission = await Location.getForegroundPermissionsAsync();
      if (permission.granted) {
        const position = await Location.getLastKnownPositionAsync();
        coords = position?.coords || null;
      }

      const { data } = await api.post('/products', {
        title: form.title.trim(),
        description: form.description.trim(),
        ...(form.categoryId ? { categoryId: form.categoryId } : {}),
        condition: form.condition,
        ...(form.size ? { size: form.size } : {}),
        ...(form.brand ? { brand: form.brand } : {}),
        priceCents,
        weightGrams,
        heightCm,
        widthCm: Number(form.width),
        lengthCm: Number(form.length),
        shippingMode: form.shippingMode,
        ...(coords ? { latitude: coords.latitude, longitude: coords.longitude } : {}),
        images: photos,
      });

      setForm(EMPTY_FORM);
      setPhotos([]);
      await refreshUser();

      Alert.alert('Anúncio publicado!', 'Seu produto já está no feed do Vendas Itinga.', [
        { text: 'Ver anúncio', onPress: () => router.push(`/product/${data.id}`) },
        { text: 'Anunciar outro', style: 'cancel' },
      ]);
    } catch (error) {
      Alert.alert('Ops', apiError(error, 'Não foi possível publicar o anúncio.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={{ flex: 1 }}
      >
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <Text style={styles.screenTitle}>anunciar produto</Text>
          <Text style={styles.screenSubtitle}>
            Boas fotos vendem mais rápido. Capriche na primeira, ela vira a capa do anúncio.
          </Text>

          {/* ------------------------- Fotos ------------------------- */}
          <Text style={styles.sectionTitle}>1. fotos</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: spacing.sm }}>
            {photos.map((uri, index) => (
              <View key={uri} style={styles.photoBox}>
                <Image source={{ uri }} style={styles.photo} contentFit="cover" />
                {index === 0 ? (
                  <View style={styles.coverTag}>
                    <Text style={styles.coverTagText}>capa</Text>
                  </View>
                ) : null}
                <Pressable
                  style={styles.removePhoto}
                  onPress={() => setPhotos((prev) => prev.filter((item) => item !== uri))}
                  hitSlop={6}
                >
                  <Ionicons name="close" size={14} color={colors.white} />
                </Pressable>
              </View>
            ))}

            <Pressable style={styles.addPhoto} onPress={() => pickPhoto(false)} disabled={uploading}>
              <Ionicons
                name={uploading ? 'cloud-upload-outline' : 'images-outline'}
                size={24}
                color={colors.primary}
              />
              <Text style={styles.addPhotoText}>{uploading ? 'enviando' : 'galeria'}</Text>
            </Pressable>

            <Pressable style={styles.addPhoto} onPress={() => pickPhoto(true)} disabled={uploading}>
              <Ionicons name="camera-outline" size={24} color={colors.primary} />
              <Text style={styles.addPhotoText}>câmera</Text>
            </Pressable>
          </ScrollView>
          {errors.photos ? <Text style={styles.error}>{errors.photos}</Text> : null}

          {/* ------------------------ Detalhes ----------------------- */}
          <Text style={styles.sectionTitle}>2. detalhes</Text>
          <Field
            label="Título"
            placeholder="ex: Tênis running tamanho 40"
            value={form.title}
            onChangeText={(text) => update('title', text)}
            error={errors.title}
            maxLength={100}
          />
          <Field
            label="Descrição"
            placeholder="Conte o estado do produto, tempo de uso, se tem marcas..."
            value={form.description}
            onChangeText={(text) => update('description', text)}
            error={errors.description}
            multiline
            numberOfLines={4}
            style={{ marginBottom: spacing.lg }}
            textAlignVertical="top"
          />

          <Text style={styles.label}>Categoria</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: spacing.lg }}>
            {categories.map((category) => (
              <Chip
                key={category.id}
                label={category.name.toLowerCase()}
                selected={form.categoryId === category.id}
                onPress={() => update('categoryId', form.categoryId === category.id ? null : category.id)}
              />
            ))}
          </ScrollView>

          <Text style={styles.label}>Condição</Text>
          <View style={styles.row}>
            {CONDITIONS.map((item) => (
              <Chip
                key={item.key}
                label={item.label}
                selected={form.condition === item.key}
                onPress={() => update('condition', item.key)}
              />
            ))}
          </View>

          <View style={[styles.row, { marginTop: spacing.lg }]}>
            <Field
              label="Tamanho (opcional)"
              placeholder="M, 42, único"
              value={form.size}
              onChangeText={(text) => update('size', text)}
              style={{ flex: 1, marginRight: spacing.sm }}
            />
            <Field
              label="Marca (opcional)"
              placeholder="Nike, Samsung..."
              value={form.brand}
              onChangeText={(text) => update('brand', text)}
              style={{ flex: 1 }}
            />
          </View>

          {/* -------------------------- Preco ------------------------ */}
          <Text style={styles.sectionTitle}>3. preço</Text>
          <Field
            label="Quanto você quer receber pelo produto?"
            placeholder="R$ 0,00"
            keyboardType="numeric"
            value={form.price ? formatBRL(priceCents) : ''}
            onChangeText={(text) => update('price', text)}
            error={errors.price}
            hint={`Valor mínimo por produto: ${formatBRL(rules.minProductPriceCents)}`}
          />

          {/* ------------------- Dimensoes e peso -------------------- */}
          <Text style={styles.sectionTitle}>4. dimensões e peso</Text>
          <Text style={styles.helper}>
            Limites da nossa logística: até {rules.maxProductWeightGrams / 1000} kg e{' '}
            {rules.maxProductHeightCm} cm de altura.
          </Text>
          <View style={styles.row}>
            <Field
              label="Peso (kg)"
              placeholder="0,5"
              keyboardType="decimal-pad"
              value={form.weight}
              onChangeText={(text) => update('weight', text)}
              error={errors.weight}
              style={{ flex: 1, marginRight: spacing.sm }}
            />
            <Field
              label="Altura (cm)"
              placeholder="20"
              keyboardType="number-pad"
              value={form.height}
              onChangeText={(text) => update('height', text)}
              error={errors.height}
              style={{ flex: 1 }}
            />
          </View>
          <View style={styles.row}>
            <Field
              label="Largura (cm)"
              placeholder="15"
              keyboardType="number-pad"
              value={form.width}
              onChangeText={(text) => update('width', text)}
              error={errors.width}
              style={{ flex: 1, marginRight: spacing.sm }}
            />
            <Field
              label="Comprimento (cm)"
              placeholder="30"
              keyboardType="number-pad"
              value={form.length}
              onChangeText={(text) => update('length', text)}
              error={errors.length}
              style={{ flex: 1 }}
            />
          </View>

          {/* --------------------------- Frete ----------------------- */}
          <Text style={styles.sectionTitle}>5. entrega</Text>
          <Pressable
            style={[styles.shippingCard, form.shippingMode === 'PLATFORM' && styles.shippingCardActive]}
            onPress={() => update('shippingMode', 'PLATFORM')}
          >
            <View style={styles.shippingHeader}>
              <Ionicons
                name={form.shippingMode === 'PLATFORM' ? 'radio-button-on' : 'radio-button-off'}
                size={20}
                color={form.shippingMode === 'PLATFORM' ? colors.primary : colors.textLight}
              />
              <Text style={styles.shippingTitle}>Entrega pela Vendas Itinga</Text>
            </View>
            <Text style={styles.shippingText}>
              Nossa frota coleta com você e entrega ao comprador. Comissão de{' '}
              {rules.platformFeePercentFleet}% sobre o produto.
            </Text>
          </Pressable>

          <Pressable
            style={[styles.shippingCard, form.shippingMode === 'SELLER' && styles.shippingCardActive]}
            onPress={() => update('shippingMode', 'SELLER')}
          >
            <View style={styles.shippingHeader}>
              <Ionicons
                name={form.shippingMode === 'SELLER' ? 'radio-button-on' : 'radio-button-off'}
                size={20}
                color={form.shippingMode === 'SELLER' ? colors.primary : colors.textLight}
              />
              <Text style={styles.shippingTitle}>Entrega própria</Text>
            </View>
            <Text style={styles.shippingText}>
              Você combina e entrega ao comprador. Comissão de {rules.platformFeePercentOwn}% sobre o
              produto.
            </Text>
          </Pressable>

          {priceCents > 0 ? (
            <View style={styles.summary}>
              <Text style={styles.summaryTitle}>você recebe</Text>
              <Text style={styles.summaryValue}>{formatBRL(sellerReceives)}</Text>
              <Text style={styles.summaryHint}>
                Já descontada a comissão de {feePercent}%. O repasse é liberado após o período de teste
                de 4 dias do comprador.
              </Text>
            </View>
          ) : null}

          <Button
            title="Publicar anúncio"
            icon="checkmark-circle-outline"
            size="lg"
            loading={saving}
            onPress={handleSubmit}
            style={{ marginTop: spacing.xl }}
          />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  content: { padding: spacing.lg, paddingBottom: 60 },
  screenTitle: { ...typography.h1, fontSize: 24 },
  screenSubtitle: { ...typography.small, marginTop: 4, marginBottom: spacing.lg, lineHeight: 19 },
  sectionTitle: {
    ...typography.h3,
    marginTop: spacing.xl,
    marginBottom: spacing.md,
    textTransform: 'lowercase',
  },
  label: { ...typography.small, color: colors.text, fontWeight: '700', marginBottom: 6 },
  helper: { ...typography.tiny, marginBottom: spacing.md, lineHeight: 16 },
  error: { ...typography.tiny, color: colors.danger, marginBottom: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'flex-start' },

  photoBox: {
    width: 96,
    height: 120,
    borderRadius: radius.md,
    overflow: 'hidden',
    marginRight: spacing.sm,
    backgroundColor: colors.surfaceAlt,
  },
  photo: { width: '100%', height: '100%' },
  coverTag: {
    position: 'absolute',
    bottom: 6,
    left: 6,
    backgroundColor: colors.primary,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: radius.pill,
  },
  coverTagText: { color: colors.white, fontSize: 9, fontWeight: '800' },
  removePhoto: {
    position: 'absolute',
    top: 6,
    right: 6,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: 'rgba(0,0,0,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  addPhoto: {
    width: 96,
    height: 120,
    borderRadius: radius.md,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.sm,
  },
  addPhotoText: { ...typography.tiny, color: colors.primaryDark, fontWeight: '700', marginTop: 4 },

  shippingCard: {
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
  },
  shippingCardActive: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  shippingHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  shippingTitle: { ...typography.h3, fontSize: 15, marginLeft: spacing.sm },
  shippingText: { ...typography.small, lineHeight: 18 },

  summary: {
    marginTop: spacing.lg,
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.primaryLight,
  },
  summaryTitle: { ...typography.tiny, color: colors.primaryDark, fontWeight: '800' },
  summaryValue: { ...typography.h1, color: colors.primaryDark, marginTop: 2 },
  summaryHint: { ...typography.tiny, color: colors.primaryDark, marginTop: 6, lineHeight: 16 },
});
