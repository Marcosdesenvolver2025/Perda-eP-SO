import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import api, { apiError } from '../src/api/client';
import { useAuth } from '../src/contexts/AuthContext';
import { Button, Chip, Field } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';

/**
 * Cadastro do vendedor como Recebedor (Recipient) no Pagar.me.
 * Sem isso o split nao tem destino e o usuario nao pode anunciar.
 */
export default function SellerAccount() {
  const router = useRouter();
  const { user, refreshUser } = useAuth();

  const [form, setForm] = useState({
    document: '',
    holderName: user?.name || '',
    holderDocument: '',
    bankCode: '',
    branchNumber: '',
    branchCheckDigit: '',
    accountNumber: '',
    accountCheckDigit: '',
    accountType: 'checking',
  });
  const [saving, setSaving] = useState(false);

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function submit() {
    const document = form.document.replace(/\D/g, '');
    const holderDocument = (form.holderDocument || form.document).replace(/\D/g, '');

    if (![11, 14].includes(document.length)) {
      Alert.alert('Documento', 'Informe um CPF (11 dígitos) ou CNPJ (14 dígitos).');
      return;
    }
    if (form.bankCode.replace(/\D/g, '').length !== 3) {
      Alert.alert('Banco', 'O código do banco tem 3 dígitos (ex: 001, 237, 260).');
      return;
    }
    if (!form.branchNumber || !form.accountNumber || !form.accountCheckDigit) {
      Alert.alert('Conta', 'Preencha agência, conta e dígito.');
      return;
    }

    setSaving(true);
    try {
      await api.post('/users/me/seller-account', {
        document,
        bankAccount: {
          holderName: form.holderName,
          holderDocument,
          bankCode: form.bankCode.replace(/\D/g, ''),
          branchNumber: form.branchNumber.replace(/\D/g, ''),
          ...(form.branchCheckDigit ? { branchCheckDigit: form.branchCheckDigit } : {}),
          accountNumber: form.accountNumber.replace(/\D/g, ''),
          accountCheckDigit: form.accountCheckDigit,
          accountType: form.accountType,
        },
      });

      await refreshUser();
      Alert.alert('Tudo pronto!', 'Sua conta de vendedor está ativa. Agora é só anunciar.', [
        { text: 'Criar anúncio', onPress: () => router.replace('/(tabs)/sell') },
      ]);
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    } finally {
      setSaving(false);
    }
  }

  if (user?.isSeller) {
    return (
      <View style={styles.done}>
        <View style={styles.doneIcon}>
          <Ionicons name="checkmark-circle" size={44} color={colors.primary} />
        </View>
        <Text style={styles.doneTitle}>Conta de vendedor ativa</Text>
        <Text style={styles.doneText}>
          Seus recebimentos são depositados automaticamente na conta cadastrada, após o período de
          teste de 4 dias do comprador.
        </Text>
        <Button
          title="Ir para o painel de vendas"
          onPress={() => router.replace('/sales')}
          style={{ marginTop: spacing.xl, minWidth: 240 }}
        />
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.white }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.info}>
          <Ionicons name="shield-checkmark" size={20} color={colors.primaryDark} />
          <Text style={styles.infoText}>
            Seus dados vão direto para o Pagar.me, nosso processador de pagamentos. É por essa conta
            que você recebe o valor das vendas, já com a comissão descontada.
          </Text>
        </View>

        <Text style={styles.sectionTitle}>seus dados</Text>
        <Field
          label="CPF ou CNPJ"
          placeholder="Apenas números"
          keyboardType="number-pad"
          maxLength={14}
          value={form.document}
          onChangeText={(text) => update('document', text.replace(/\D/g, ''))}
        />

        <Text style={styles.sectionTitle}>conta bancária</Text>
        <Field
          label="Titular da conta"
          placeholder="Nome completo como no banco"
          value={form.holderName}
          onChangeText={(text) => update('holderName', text)}
        />
        <Field
          label="CPF/CNPJ do titular"
          placeholder="Se for o mesmo, deixe em branco"
          keyboardType="number-pad"
          maxLength={14}
          value={form.holderDocument}
          onChangeText={(text) => update('holderDocument', text.replace(/\D/g, ''))}
        />
        <Field
          label="Código do banco"
          placeholder="001, 237, 260..."
          keyboardType="number-pad"
          maxLength={3}
          value={form.bankCode}
          onChangeText={(text) => update('bankCode', text.replace(/\D/g, ''))}
          hint="3 dígitos. Ex.: 001 Banco do Brasil, 260 Nubank, 237 Bradesco"
        />

        <View style={{ flexDirection: 'row' }}>
          <Field
            label="Agência"
            keyboardType="number-pad"
            value={form.branchNumber}
            onChangeText={(text) => update('branchNumber', text.replace(/\D/g, ''))}
            style={{ flex: 2, marginRight: spacing.sm }}
          />
          <Field
            label="Dígito"
            placeholder="opcional"
            maxLength={2}
            value={form.branchCheckDigit}
            onChangeText={(text) => update('branchCheckDigit', text)}
            style={{ flex: 1 }}
          />
        </View>

        <View style={{ flexDirection: 'row' }}>
          <Field
            label="Conta"
            keyboardType="number-pad"
            value={form.accountNumber}
            onChangeText={(text) => update('accountNumber', text.replace(/\D/g, ''))}
            style={{ flex: 2, marginRight: spacing.sm }}
          />
          <Field
            label="Dígito"
            maxLength={2}
            value={form.accountCheckDigit}
            onChangeText={(text) => update('accountCheckDigit', text)}
            style={{ flex: 1 }}
          />
        </View>

        <Text style={styles.label}>Tipo de conta</Text>
        <View style={{ flexDirection: 'row', marginBottom: spacing.lg }}>
          <Chip
            label="corrente"
            selected={form.accountType === 'checking'}
            onPress={() => update('accountType', 'checking')}
          />
          <Chip
            label="poupança"
            selected={form.accountType === 'savings'}
            onPress={() => update('accountType', 'savings')}
          />
        </View>

        <Button title="Ativar conta de vendedor" size="lg" loading={saving} onPress={submit} />
      </ScrollView>
    </KeyboardAvoidingView>
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
  label: { ...typography.small, color: colors.text, fontWeight: '700', marginBottom: 6 },

  done: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, backgroundColor: colors.white },
  doneIcon: {
    width: 84,
    height: 84,
    borderRadius: 42,
    backgroundColor: colors.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
  },
  doneTitle: { ...typography.h2, marginTop: spacing.lg },
  doneText: { ...typography.small, textAlign: 'center', marginTop: spacing.sm, maxWidth: 300, lineHeight: 20 },
});
