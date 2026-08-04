import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import api, { apiError } from '../../../src/api/client';
import { Button, Field } from '../../../src/components/ui';
import { colors, radius, spacing, typography } from '../../../src/theme';

const REASONS = [
  { key: 'NOT_AS_DESCRIBED', label: 'Diferente do anunciado', icon: 'alert-circle-outline' },
  { key: 'DAMAGED', label: 'Chegou danificado', icon: 'bandage-outline' },
  { key: 'WRONG_ITEM', label: 'Produto errado', icon: 'swap-horizontal-outline' },
  { key: 'NOT_RECEIVED', label: 'Não recebi', icon: 'help-circle-outline' },
  { key: 'OTHER', label: 'Outro motivo', icon: 'ellipsis-horizontal-outline' },
];

export default function RefundRequest() {
  const { id } = useLocalSearchParams();
  const router = useRouter();

  const [reason, setReason] = useState(null);
  const [description, setDescription] = useState('');
  const [photos, setPhotos] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [sending, setSending] = useState(false);

  async function addPhoto() {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) return;

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
    });
    if (result.canceled) return;

    setUploading(true);
    try {
      const processed = await ImageManipulator.manipulateAsync(
        result.assets[0].uri,
        [{ resize: { width: 1000 } }],
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

  async function submit() {
    if (!reason) {
      Alert.alert('Motivo', 'Selecione o motivo do reembolso.');
      return;
    }
    if (description.trim().length < 10) {
      Alert.alert('Descrição', 'Conte com mais detalhes o que aconteceu (mínimo 10 caracteres).');
      return;
    }

    setSending(true);
    try {
      const { data } = await api.post(`/orders/${id}/refund`, {
        reason,
        description: description.trim(),
        photos,
      });

      Alert.alert('Solicitação registrada', data.message, [
        { text: 'Ver pedido', onPress: () => router.replace(`/order/${id}`) },
      ]);
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    } finally {
      setSending(false);
    }
  }

  return (
    <ScrollView style={{ backgroundColor: colors.white }} contentContainerStyle={styles.content}>
      <View style={styles.info}>
        <Ionicons name="information-circle" size={20} color={colors.primaryDark} />
        <Text style={styles.infoText}>
          Ao confirmar, um entregador do Vendas Itinga vai até o seu endereço recolher o produto e
          devolvê-lo ao vendedor. O valor pago volta para você assim que a coleta for concluída.
        </Text>
      </View>

      <Text style={styles.sectionTitle}>qual o motivo?</Text>
      {REASONS.map((item) => (
        <Pressable
          key={item.key}
          style={[styles.reason, reason === item.key && styles.reasonActive]}
          onPress={() => setReason(item.key)}
        >
          <Ionicons
            name={item.icon}
            size={20}
            color={reason === item.key ? colors.primaryDark : colors.textMuted}
          />
          <Text style={[styles.reasonLabel, reason === item.key && { color: colors.primaryDark }]}>
            {item.label}
          </Text>
          <Ionicons
            name={reason === item.key ? 'radio-button-on' : 'radio-button-off'}
            size={18}
            color={reason === item.key ? colors.primary : colors.textLight}
          />
        </Pressable>
      ))}

      <Text style={styles.sectionTitle}>conte o que aconteceu</Text>
      <Field
        placeholder="Descreva o problema com o máximo de detalhes possível."
        value={description}
        onChangeText={setDescription}
        multiline
        numberOfLines={5}
        textAlignVertical="top"
      />

      <Text style={styles.sectionTitle}>fotos (opcional, mas ajudam muito)</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false}>
        {photos.map((uri) => (
          <View key={uri} style={styles.photoBox}>
            <Image source={{ uri }} style={styles.photo} contentFit="cover" />
            <Pressable
              style={styles.removePhoto}
              onPress={() => setPhotos((prev) => prev.filter((item) => item !== uri))}
            >
              <Ionicons name="close" size={13} color={colors.white} />
            </Pressable>
          </View>
        ))}
        {photos.length < 6 ? (
          <Pressable style={styles.addPhoto} onPress={addPhoto} disabled={uploading}>
            <Ionicons
              name={uploading ? 'cloud-upload-outline' : 'camera-outline'}
              size={22}
              color={colors.primary}
            />
          </Pressable>
        ) : null}
      </ScrollView>

      <Button
        title="Enviar solicitação"
        size="lg"
        loading={sending}
        onPress={submit}
        style={{ marginTop: spacing.xl }}
      />
      <Text style={styles.disclaimer}>
        A comissão da plataforma é mantida para cobrir os custos operacionais da coleta e do
        processamento, conforme os termos de uso.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: 60 },
  info: {
    flexDirection: 'row',
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.primarySoft,
  },
  infoText: { ...typography.small, flex: 1, marginLeft: spacing.md, lineHeight: 19 },

  sectionTitle: { ...typography.h3, marginTop: spacing.xl, marginBottom: spacing.md },
  reason: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.lg,
    borderRadius: radius.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    marginBottom: spacing.sm,
  },
  reasonActive: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  reasonLabel: { ...typography.body, flex: 1, marginLeft: spacing.md, fontWeight: '600' },

  photoBox: { width: 84, height: 84, borderRadius: radius.md, overflow: 'hidden', marginRight: spacing.sm },
  photo: { width: '100%', height: '100%' },
  removePhoto: {
    position: 'absolute',
    top: 4,
    right: 4,
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: 'rgba(0,0,0,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  addPhoto: {
    width: 84,
    height: 84,
    borderRadius: radius.md,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  disclaimer: { ...typography.tiny, marginTop: spacing.md, lineHeight: 15, textAlign: 'center' },
});
