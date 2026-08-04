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
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import api, { apiError } from '../src/api/client';
import { tokenizeCard, maskCardNumber, maskExpiry, isValidCardNumber } from '../src/api/pagarme';
import { useCart } from '../src/contexts/CartContext';
import { Button, Divider, Field, Loading, SummaryRow } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';
import { formatBRL } from '../src/utils/format';

export default function Checkout() {
  const router = useRouter();
  const { cart, refresh } = useCart();

  const [addresses, setAddresses] = useState([]);
  const [addressId, setAddressId] = useState(null);
  const [method, setMethod] = useState('PIX');
  const [card, setCard] = useState({ number: '', holderName: '', expiry: '', cvv: '' });
  const [installments, setInstallments] = useState(1);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [pixResult, setPixResult] = useState(null);

  useEffect(() => {
    api
      .get('/addresses')
      .then(({ data }) => {
        setAddresses(data);
        setAddressId(data.find((item) => item.isDefault)?.id || data[0]?.id || null);
      })
      .catch(() => setAddresses([]))
      .finally(() => setLoading(false));
  }, []);

  async function handlePay() {
    if (!addressId) {
      Alert.alert('Endereço', 'Cadastre um endereço de entrega para continuar.', [
        { text: 'Cadastrar', onPress: () => router.push('/addresses') },
      ]);
      return;
    }

    setPaying(true);
    try {
      let cardToken;

      if (method === 'CREDIT_CARD') {
        if (!isValidCardNumber(card.number)) throw new Error('Número do cartão inválido.');
        const [month, year] = card.expiry.split('/');
        if (!month || !year) throw new Error('Validade inválida. Use MM/AA.');
        if (card.cvv.length < 3) throw new Error('CVV inválido.');

        // O cartao e tokenizado no dispositivo: os dados nao passam pela
        // nossa API, apenas o token de uso unico.
        cardToken = await tokenizeCard({
          number: card.number,
          holderName: card.holderName,
          expMonth: month,
          expYear: `20${year}`,
          cvv: card.cvv,
        });
      }

      const { data } = await api.post('/orders/checkout', {
        addressId,
        paymentMethod: method,
        installments,
        ...(cardToken ? { cardToken } : {}),
      });

      await refresh();

      if (method === 'PIX') {
        const pix = data.orders.find((order) => order.pix)?.pix;
        if (pix) {
          setPixResult({ ...pix, orders: data.orders });
          return;
        }
      }

      Alert.alert('Pagamento aprovado!', 'Acompanhe a entrega em Minhas compras.', [
        { text: 'Ver pedidos', onPress: () => router.replace('/purchases') },
      ]);
    } catch (error) {
      Alert.alert('Não foi possível pagar', error.response ? apiError(error) : error.message);
    } finally {
      setPaying(false);
    }
  }

  if (loading) return <Loading />;

  // --------------------------- Tela do PIX ---------------------------
  if (pixResult) {
    return (
      <ScrollView contentContainerStyle={styles.pixContainer}>
        <View style={styles.pixIcon}>
          <Ionicons name="qr-code-outline" size={38} color={colors.primaryDark} />
        </View>
        <Text style={styles.pixTitle}>Pague com PIX para confirmar</Text>
        <Text style={styles.pixText}>
          Copie o código abaixo e cole no app do seu banco. Assim que o pagamento cair, avisamos o
          vendedor automaticamente.
        </Text>

        <View style={styles.pixCode}>
          <Text style={styles.pixCodeText} numberOfLines={4}>
            {pixResult.qrCode}
          </Text>
        </View>

        <Button
          title="Copiar código PIX"
          icon="copy-outline"
          onPress={async () => {
            await Clipboard.setStringAsync(pixResult.qrCode);
            Alert.alert('Copiado!', 'Cole o código no app do seu banco.');
          }}
          style={{ marginTop: spacing.lg }}
        />
        <Button
          title="Já paguei, ver meus pedidos"
          variant="ghost"
          onPress={() => router.replace('/purchases')}
          style={{ marginTop: spacing.md }}
        />
      </ScrollView>
    );
  }

  const installmentOptions = [1, 2, 3, 4, 6, 10, 12];

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={{ flex: 1, backgroundColor: colors.white }}
    >
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* ------------------------- Endereco ------------------------- */}
        <Text style={styles.sectionTitle}>entrega</Text>
        {addresses.length ? (
          addresses.map((address) => (
            <Pressable
              key={address.id}
              style={[styles.option, addressId === address.id && styles.optionActive]}
              onPress={() => setAddressId(address.id)}
            >
              <Ionicons
                name={addressId === address.id ? 'radio-button-on' : 'radio-button-off'}
                size={20}
                color={addressId === address.id ? colors.primary : colors.textLight}
              />
              <View style={{ flex: 1, marginLeft: spacing.md }}>
                <Text style={styles.optionTitle}>
                  {address.label} · {address.recipient}
                </Text>
                <Text style={styles.optionText}>
                  {address.street}, {address.number}
                  {address.complement ? ` - ${address.complement}` : ''} · {address.district},{' '}
                  {address.city}/{address.state}
                </Text>
              </View>
            </Pressable>
          ))
        ) : (
          <Button
            title="Cadastrar endereço"
            variant="outline"
            icon="add-outline"
            onPress={() => router.push('/addresses')}
          />
        )}

        {/* ------------------------ Pagamento ------------------------- */}
        <Text style={styles.sectionTitle}>pagamento</Text>
        <View style={styles.methods}>
          <Pressable
            style={[styles.method, method === 'PIX' && styles.methodActive]}
            onPress={() => setMethod('PIX')}
          >
            <Ionicons
              name="qr-code-outline"
              size={22}
              color={method === 'PIX' ? colors.primaryDark : colors.textMuted}
            />
            <Text style={[styles.methodText, method === 'PIX' && styles.methodTextActive]}>PIX</Text>
            <Text style={styles.methodHint}>na hora</Text>
          </Pressable>

          <Pressable
            style={[styles.method, method === 'CREDIT_CARD' && styles.methodActive]}
            onPress={() => setMethod('CREDIT_CARD')}
          >
            <Ionicons
              name="card-outline"
              size={22}
              color={method === 'CREDIT_CARD' ? colors.primaryDark : colors.textMuted}
            />
            <Text style={[styles.methodText, method === 'CREDIT_CARD' && styles.methodTextActive]}>
              Cartão
            </Text>
            <Text style={styles.methodHint}>até 12x</Text>
          </Pressable>
        </View>

        {method === 'CREDIT_CARD' ? (
          <View style={{ marginTop: spacing.lg }}>
            <Field
              label="Número do cartão"
              placeholder="0000 0000 0000 0000"
              keyboardType="number-pad"
              value={card.number}
              onChangeText={(text) => setCard((prev) => ({ ...prev, number: maskCardNumber(text) }))}
            />
            <Field
              label="Nome impresso no cartão"
              placeholder="MARIA S SILVA"
              autoCapitalize="characters"
              value={card.holderName}
              onChangeText={(text) => setCard((prev) => ({ ...prev, holderName: text }))}
            />
            <View style={{ flexDirection: 'row' }}>
              <Field
                label="Validade"
                placeholder="MM/AA"
                keyboardType="number-pad"
                value={card.expiry}
                onChangeText={(text) => setCard((prev) => ({ ...prev, expiry: maskExpiry(text) }))}
                style={{ flex: 1, marginRight: spacing.sm }}
              />
              <Field
                label="CVV"
                placeholder="123"
                keyboardType="number-pad"
                maxLength={4}
                secureTextEntry
                value={card.cvv}
                onChangeText={(text) => setCard((prev) => ({ ...prev, cvv: text.replace(/\D/g, '') }))}
                style={{ flex: 1 }}
              />
            </View>

            <Text style={styles.label}>Parcelas</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: spacing.md }}>
              {installmentOptions.map((times) => (
                <Pressable
                  key={times}
                  style={[styles.installment, installments === times && styles.installmentActive]}
                  onPress={() => setInstallments(times)}
                >
                  <Text
                    style={[
                      styles.installmentText,
                      installments === times && { color: colors.white, fontWeight: '800' },
                    ]}
                  >
                    {times}x {formatBRL(Math.round(cart.totalCents / times))}
                  </Text>
                </Pressable>
              ))}
            </ScrollView>

            <View style={styles.secureNote}>
              <Ionicons name="lock-closed" size={13} color={colors.textMuted} />
              <Text style={styles.secureText}>
                Seus dados são criptografados e enviados direto ao Pagar.me. O Vendas Itinga não
                armazena o número do seu cartão.
              </Text>
            </View>
          </View>
        ) : null}

        {/* -------------------------- Resumo -------------------------- */}
        <Text style={styles.sectionTitle}>resumo</Text>
        <View style={styles.summary}>
          <SummaryRow label="Produtos" value={formatBRL(cart.itemsTotalCents)} />
          <SummaryRow label="Entrega" value={formatBRL(cart.shippingTotalCents)} />
          <Divider style={{ marginVertical: spacing.sm }} />
          <SummaryRow label="Total" value={formatBRL(cart.totalCents)} strong />
        </View>

        <View style={styles.protection}>
          <Ionicons name="shield-checkmark" size={18} color={colors.primaryDark} />
          <Text style={styles.protectionText}>
            Você tem 4 dias após receber para testar. Se algo estiver errado, peça o reembolso pelo app.
          </Text>
        </View>

        <Button
          title={method === 'PIX' ? 'Gerar código PIX' : `Pagar ${formatBRL(cart.totalCents)}`}
          size="lg"
          loading={paying}
          onPress={handlePay}
          style={{ marginTop: spacing.xl }}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: 60 },
  sectionTitle: { ...typography.h3, marginTop: spacing.lg, marginBottom: spacing.md },
  label: { ...typography.small, color: colors.text, fontWeight: '700', marginBottom: 6 },

  option: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.lg,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: colors.border,
    marginBottom: spacing.sm,
  },
  optionActive: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  optionTitle: { ...typography.small, fontWeight: '800', color: colors.ink },
  optionText: { ...typography.tiny, marginTop: 2, lineHeight: 15 },

  methods: { flexDirection: 'row', gap: spacing.md },
  method: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: spacing.lg,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: colors.border,
  },
  methodActive: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  methodText: { ...typography.body, fontWeight: '700', marginTop: 6 },
  methodTextActive: { color: colors.primaryDark },
  methodHint: { ...typography.tiny, marginTop: 1 },

  installment: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: colors.border,
    marginRight: spacing.sm,
  },
  installmentActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  installmentText: { ...typography.small, color: colors.text, fontWeight: '600' },

  secureNote: { flexDirection: 'row', alignItems: 'flex-start', marginTop: spacing.sm },
  secureText: { ...typography.tiny, flex: 1, marginLeft: 6, lineHeight: 15 },

  summary: { padding: spacing.lg, borderRadius: radius.lg, backgroundColor: colors.surfaceAlt },
  protection: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: spacing.lg,
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.primarySoft,
  },
  protectionText: { ...typography.tiny, flex: 1, marginLeft: spacing.sm, lineHeight: 16 },

  pixContainer: { padding: spacing.xl, alignItems: 'center', backgroundColor: colors.white, flexGrow: 1 },
  pixIcon: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: colors.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: spacing.xl,
  },
  pixTitle: { ...typography.h2, marginTop: spacing.lg, textAlign: 'center' },
  pixText: { ...typography.small, textAlign: 'center', marginTop: spacing.sm, lineHeight: 19 },
  pixCode: {
    marginTop: spacing.lg,
    padding: spacing.lg,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
    width: '100%',
  },
  pixCodeText: { ...typography.tiny, fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace' },
});
