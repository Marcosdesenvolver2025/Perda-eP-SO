import { useCallback, useState } from 'react';
import { Alert, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Location from 'expo-location';
import api, { apiError } from '../src/api/client';
import { Button, EmptyState, Field, Loading } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';

const EMPTY = {
  label: 'Casa',
  recipient: '',
  zipCode: '',
  street: '',
  number: '',
  complement: '',
  district: '',
  city: '',
  state: '',
  isDefault: false,
};

export default function Addresses() {
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);
  const [lookingUp, setLookingUp] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get('/addresses');
      setAddresses(data);
    } catch {
      setAddresses([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  /** Preenche o endereco pelo CEP usando a API publica ViaCEP. */
  async function lookupZip(zipCode) {
    const digits = zipCode.replace(/\D/g, '');
    if (digits.length !== 8) return;

    setLookingUp(true);
    try {
      const response = await fetch(`https://viacep.com.br/ws/${digits}/json/`);
      const data = await response.json();
      if (data.erro) return;

      setForm((prev) => ({
        ...prev,
        street: data.logradouro || prev.street,
        district: data.bairro || prev.district,
        city: data.localidade || prev.city,
        state: data.uf || prev.state,
      }));
    } catch {
      /* preenchimento manual continua disponivel */
    } finally {
      setLookingUp(false);
    }
  }

  async function save() {
    const digits = form.zipCode.replace(/\D/g, '');
    if (digits.length !== 8) {
      Alert.alert('CEP', 'Informe um CEP válido com 8 dígitos.');
      return;
    }
    if (!form.recipient || !form.street || !form.number || !form.district || !form.city || !form.state) {
      Alert.alert('Campos obrigatórios', 'Preencha todos os campos do endereço.');
      return;
    }

    setSaving(true);
    try {
      // Coordenadas do dispositivo: alimentam o filtro de proximidade e
      // a rota do entregador.
      let coords = null;
      const permission = await Location.getForegroundPermissionsAsync();
      if (permission.granted) {
        const position = await Location.getLastKnownPositionAsync();
        coords = position?.coords || null;
      }

      await api.post('/addresses', {
        ...form,
        zipCode: digits,
        state: form.state.toUpperCase(),
        ...(coords ? { latitude: coords.latitude, longitude: coords.longitude } : {}),
      });

      setForm(EMPTY);
      setOpen(false);
      await load();
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    } finally {
      setSaving(false);
    }
  }

  function confirmDelete(address) {
    Alert.alert('Remover endereço', `Excluir "${address.label}"?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Excluir',
        style: 'destructive',
        onPress: async () => {
          try {
            await api.delete(`/addresses/${address.id}`);
            await load();
          } catch (error) {
            Alert.alert('Ops', apiError(error));
          }
        },
      },
    ]);
  }

  if (loading) return <Loading />;

  return (
    <View style={{ flex: 1, backgroundColor: colors.white }}>
      <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: 100 }}>
        {addresses.length ? (
          addresses.map((address) => (
            <View key={address.id} style={styles.card}>
              <View style={{ flex: 1 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Text style={styles.label}>{address.label}</Text>
                  {address.isDefault ? <Text style={styles.default}>padrão</Text> : null}
                </View>
                <Text style={styles.recipient}>{address.recipient}</Text>
                <Text style={styles.text}>
                  {address.street}, {address.number}
                  {address.complement ? ` - ${address.complement}` : ''}
                  {'\n'}
                  {address.district} · {address.city}/{address.state} · CEP {address.zipCode}
                </Text>
              </View>
              <Pressable hitSlop={10} onPress={() => confirmDelete(address)}>
                <Ionicons name="trash-outline" size={18} color={colors.textMuted} />
              </Pressable>
            </View>
          ))
        ) : (
          <EmptyState
            icon="location-outline"
            title="Nenhum endereço salvo"
            description="Cadastre um endereço para receber suas compras e agilizar o checkout."
          />
        )}
      </ScrollView>

      <View style={styles.footer}>
        <Button title="Adicionar endereço" icon="add-outline" onPress={() => setOpen(true)} />
      </View>

      <Modal visible={open} animationType="slide" onRequestClose={() => setOpen(false)}>
        <View style={{ flex: 1, backgroundColor: colors.white }}>
          <View style={styles.modalHeader}>
            <Text style={typography.h2}>novo endereço</Text>
            <Pressable onPress={() => setOpen(false)} hitSlop={10}>
              <Ionicons name="close" size={24} color={colors.ink} />
            </Pressable>
          </View>

          <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: 60 }}>
            <Field
              label="Identificação"
              placeholder="Casa, Trabalho..."
              value={form.label}
              onChangeText={(text) => setForm((prev) => ({ ...prev, label: text }))}
            />
            <Field
              label="Quem recebe"
              placeholder="Nome completo"
              value={form.recipient}
              onChangeText={(text) => setForm((prev) => ({ ...prev, recipient: text }))}
            />
            <Field
              label="CEP"
              placeholder="00000000"
              keyboardType="number-pad"
              maxLength={9}
              value={form.zipCode}
              hint={lookingUp ? 'Buscando endereço...' : 'Preenchemos o resto pra você'}
              onChangeText={(text) => {
                setForm((prev) => ({ ...prev, zipCode: text }));
                lookupZip(text);
              }}
            />
            <Field
              label="Rua"
              value={form.street}
              onChangeText={(text) => setForm((prev) => ({ ...prev, street: text }))}
            />
            <View style={{ flexDirection: 'row' }}>
              <Field
                label="Número"
                keyboardType="number-pad"
                value={form.number}
                onChangeText={(text) => setForm((prev) => ({ ...prev, number: text }))}
                style={{ flex: 1, marginRight: spacing.sm }}
              />
              <Field
                label="Complemento"
                placeholder="Apto, bloco"
                value={form.complement}
                onChangeText={(text) => setForm((prev) => ({ ...prev, complement: text }))}
                style={{ flex: 1.4 }}
              />
            </View>
            <Field
              label="Bairro"
              value={form.district}
              onChangeText={(text) => setForm((prev) => ({ ...prev, district: text }))}
            />
            <View style={{ flexDirection: 'row' }}>
              <Field
                label="Cidade"
                value={form.city}
                onChangeText={(text) => setForm((prev) => ({ ...prev, city: text }))}
                style={{ flex: 2, marginRight: spacing.sm }}
              />
              <Field
                label="UF"
                maxLength={2}
                autoCapitalize="characters"
                value={form.state}
                onChangeText={(text) => setForm((prev) => ({ ...prev, state: text }))}
                style={{ flex: 1 }}
              />
            </View>

            <Pressable
              style={styles.checkbox}
              onPress={() => setForm((prev) => ({ ...prev, isDefault: !prev.isDefault }))}
            >
              <Ionicons
                name={form.isDefault ? 'checkbox' : 'square-outline'}
                size={22}
                color={form.isDefault ? colors.primary : colors.textLight}
              />
              <Text style={styles.checkboxLabel}>Usar como endereço padrão</Text>
            </Pressable>

            <Button title="Salvar endereço" loading={saving} onPress={save} style={{ marginTop: spacing.lg }} />
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
  },
  label: { ...typography.h3, fontSize: 15 },
  default: {
    ...typography.tiny,
    color: colors.primaryDark,
    fontWeight: '800',
    backgroundColor: colors.primaryLight,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: radius.pill,
    marginLeft: spacing.sm,
  },
  recipient: { ...typography.small, fontWeight: '700', color: colors.text, marginTop: 3 },
  text: { ...typography.small, marginTop: 3, lineHeight: 18 },

  footer: {
    padding: spacing.lg,
    paddingBottom: spacing.xl,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: spacing.lg,
    paddingTop: 60,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  checkbox: { flexDirection: 'row', alignItems: 'center', marginTop: spacing.sm },
  checkboxLabel: { ...typography.body, marginLeft: spacing.sm },
});
