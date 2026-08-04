import { useCallback, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api, { apiError } from '../../src/api/client';
import { useAuth } from '../../src/contexts/AuthContext';
import { Badge, Button, Divider, Loading, SummaryRow } from '../../src/components/ui';
import { colors, radius, spacing, typography } from '../../src/theme';
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TONE,
  daysLeft,
  formatBRL,
  formatDateTime,
} from '../../src/utils/format';

export default function OrderDetail() {
  const { id } = useLocalSearchParams();
  const router = useRouter();
  const { user } = useAuth();

  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get(`/orders/${id}`);
      setOrder(data);
    } catch {
      setOrder(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  async function confirmReceipt() {
    setActing(true);
    try {
      await api.post(`/orders/${id}/confirm`);
      await load();
      Alert.alert('Obrigado!', 'Compra confirmada. O vendedor já pode receber o valor.');
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    } finally {
      setActing(false);
    }
  }

  async function reviewRefund(action) {
    setActing(true);
    try {
      await api.post(`/orders/${id}/refund/review`, { action });
      await load();
      Alert.alert(
        action === 'APPROVE' ? 'Reembolso aprovado' : 'Contestação registrada',
        action === 'APPROVE'
          ? 'O comprador será reembolsado e o produto volta para você.'
          : 'Nossa equipe vai analisar o caso.'
      );
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    } finally {
      setActing(false);
    }
  }

  if (loading) return <Loading />;
  if (!order) {
    return (
      <View style={styles.center}>
        <Text style={typography.body}>Pedido não encontrado.</Text>
      </View>
    );
  }

  const isBuyer = order.buyer?.id === user?.id || !order.buyer;
  const isSeller = order.seller?.id === user?.id;
  const testDays = order.status === 'IN_TEST' ? daysLeft(order.testEndsAt) : 0;
  const canRefund = isBuyer && ['IN_TEST', 'DELIVERED', 'SHIPPED'].includes(order.status) && !order.refund;

  return (
    <ScrollView style={{ backgroundColor: colors.white }} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Badge label={ORDER_STATUS_LABELS[order.status]} tone={ORDER_STATUS_TONE[order.status]} />
        <Text style={styles.orderId}>#{order.id.slice(-8).toUpperCase()}</Text>
      </View>

      {testDays > 0 ? (
        <View style={styles.testBanner}>
          <Ionicons name="shield-checkmark" size={20} color={colors.primaryDark} />
          <View style={{ flex: 1, marginLeft: spacing.md }}>
            <Text style={styles.testTitle}>
              {testDays === 1 ? 'Último dia' : `${testDays} dias`} de período de teste
            </Text>
            <Text style={styles.testText}>
              Confira o produto com calma. Até {formatDateTime(order.testEndsAt)} você pode pedir o
              reembolso se algo estiver diferente do anunciado.
            </Text>
          </View>
        </View>
      ) : null}

      {/* --------------------------- Itens --------------------------- */}
      <Text style={styles.sectionTitle}>itens</Text>
      {order.items.map((item) => (
        <View key={item.id} style={styles.item}>
          {item.image ? (
            <Image source={{ uri: item.image }} style={styles.thumb} contentFit="cover" />
          ) : (
            <View style={[styles.thumb, styles.thumbFallback]}>
              <Ionicons name="cube-outline" size={20} color={colors.textLight} />
            </View>
          )}
          <View style={{ flex: 1, marginLeft: spacing.md }}>
            <Text style={styles.itemTitle}>{item.title}</Text>
            <Text style={styles.itemPrice}>
              {formatBRL(item.priceCents)}
              {item.quantity > 1 ? ` · ${item.quantity} un.` : ''}
            </Text>
          </View>
        </View>
      ))}

      {/* -------------------------- Valores -------------------------- */}
      <Text style={styles.sectionTitle}>valores</Text>
      <View style={styles.box}>
        <SummaryRow label="Produtos" value={formatBRL(order.itemsTotalCents)} />
        <SummaryRow
          label={order.shippingMode === 'PLATFORM' ? 'Entrega Vendas Itinga' : 'Entrega do vendedor'}
          value={order.shippingCents ? formatBRL(order.shippingCents) : 'combinada'}
        />
        <Divider style={{ marginVertical: spacing.sm }} />
        <SummaryRow label="Total" value={formatBRL(order.totalCents)} strong />

        {isSeller ? (
          <>
            <Divider style={{ marginVertical: spacing.sm }} />
            <SummaryRow
              label={`Comissão da plataforma (${order.platformFeePercent}%)`}
              value={`- ${formatBRL(order.platformFeeCents)}`}
              tone="danger"
            />
            <SummaryRow
              label="Você recebe"
              value={formatBRL(order.sellerAmountCents)}
              tone="success"
              strong
            />
          </>
        ) : null}
      </View>

      {/* ------------------------- Entrega --------------------------- */}
      {order.address ? (
        <>
          <Text style={styles.sectionTitle}>entrega</Text>
          <View style={styles.box}>
            <Text style={styles.addressName}>{order.address.recipient}</Text>
            <Text style={styles.addressText}>
              {order.address.street}, {order.address.number}
              {order.address.complement ? ` - ${order.address.complement}` : ''}
              {'\n'}
              {order.address.district} · {order.address.city}/{order.address.state}
              {'\n'}
              CEP {order.address.zipCode}
            </Text>
            {order.trackingCode ? (
              <Text style={styles.tracking}>Rastreio: {order.trackingCode}</Text>
            ) : null}
          </View>
        </>
      ) : null}

      {/* ------------------------- Histórico ------------------------- */}
      {order.timeline?.length ? (
        <>
          <Text style={styles.sectionTitle}>acompanhamento</Text>
          <View style={styles.timeline}>
            {order.timeline.map((event, index) => (
              <View key={event.id} style={styles.timelineRow}>
                <View style={styles.timelineMarker}>
                  <View
                    style={[
                      styles.timelineDot,
                      index === order.timeline.length - 1 && { backgroundColor: colors.primary },
                    ]}
                  />
                  {index < order.timeline.length - 1 ? <View style={styles.timelineLine} /> : null}
                </View>
                <View style={{ flex: 1, paddingBottom: spacing.lg }}>
                  <Text style={styles.timelineStatus}>{ORDER_STATUS_LABELS[event.status]}</Text>
                  {event.note ? <Text style={styles.timelineNote}>{event.note}</Text> : null}
                  <Text style={styles.timelineDate}>{formatDateTime(event.createdAt)}</Text>
                </View>
              </View>
            ))}
          </View>
        </>
      ) : null}

      {/* -------------------------- Reembolso ------------------------ */}
      {order.refund ? (
        <View style={styles.refundBox}>
          <Text style={styles.refundTitle}>Solicitação de reembolso</Text>
          <Text style={styles.refundText}>
            Status: {order.refund.status === 'COMPLETED' ? 'concluído' : 'em andamento'}
            {order.refund.refundedAmountCents
              ? ` · devolvido ${formatBRL(order.refund.refundedAmountCents)}`
              : ''}
          </Text>
          <Text style={styles.refundHint}>{order.refund.description}</Text>

          {isSeller && order.refund.status !== 'COMPLETED' ? (
            <View style={{ flexDirection: 'row', marginTop: spacing.md, gap: spacing.sm }}>
              <Button
                title="Contestar"
                variant="outline"
                size="sm"
                style={{ flex: 1 }}
                loading={acting}
                onPress={() => reviewRefund('REJECT')}
              />
              <Button
                title="Aprovar"
                size="sm"
                style={{ flex: 1 }}
                loading={acting}
                onPress={() => reviewRefund('APPROVE')}
              />
            </View>
          ) : null}
        </View>
      ) : null}

      {/* --------------------------- Ações --------------------------- */}
      {canRefund ? (
        <Button
          title="Solicitar reembolso"
          variant="danger"
          icon="return-down-back-outline"
          style={{ marginTop: spacing.xl }}
          onPress={() => router.push(`/order/refund/${order.id}`)}
        />
      ) : null}

      {isBuyer && ['IN_TEST', 'DELIVERED'].includes(order.status) ? (
        <Button
          title="Confirmar que está tudo certo"
          icon="checkmark-circle-outline"
          loading={acting}
          style={{ marginTop: spacing.md }}
          onPress={confirmReceipt}
        />
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: 60 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  orderId: { ...typography.tiny, fontWeight: '800' },

  testBanner: {
    flexDirection: 'row',
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.primarySoft,
    marginTop: spacing.lg,
  },
  testTitle: { ...typography.h3, fontSize: 15, color: colors.primaryDark },
  testText: { ...typography.tiny, marginTop: 3, lineHeight: 16 },

  sectionTitle: { ...typography.h3, marginTop: spacing.xl, marginBottom: spacing.md },
  item: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.md },
  thumb: { width: 56, height: 64, borderRadius: radius.sm, backgroundColor: colors.surfaceAlt },
  thumbFallback: { alignItems: 'center', justifyContent: 'center' },
  itemTitle: { ...typography.small, color: colors.text },
  itemPrice: { ...typography.price, fontSize: 15, marginTop: 3 },

  box: { padding: spacing.lg, borderRadius: radius.lg, backgroundColor: colors.surfaceAlt },
  addressName: { ...typography.small, fontWeight: '800', color: colors.ink },
  addressText: { ...typography.small, marginTop: 4, lineHeight: 19 },
  tracking: { ...typography.tiny, color: colors.primaryDark, fontWeight: '700', marginTop: spacing.sm },

  timeline: { paddingLeft: 2 },
  timelineRow: { flexDirection: 'row' },
  timelineMarker: { width: 22, alignItems: 'center' },
  timelineDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.borderStrong, marginTop: 4 },
  timelineLine: { width: 2, flex: 1, backgroundColor: colors.border, marginVertical: 2 },
  timelineStatus: { ...typography.small, fontWeight: '800', color: colors.ink },
  timelineNote: { ...typography.tiny, marginTop: 2, lineHeight: 15 },
  timelineDate: { ...typography.tiny, color: colors.textLight, marginTop: 2 },

  refundBox: {
    marginTop: spacing.xl,
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.dangerLight,
  },
  refundTitle: { ...typography.h3, fontSize: 15, color: colors.danger },
  refundText: { ...typography.small, color: colors.danger, marginTop: 3 },
  refundHint: { ...typography.tiny, marginTop: 6, lineHeight: 16 },
});
