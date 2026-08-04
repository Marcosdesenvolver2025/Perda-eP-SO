import { useCallback, useState } from 'react';
import { Alert, FlatList, Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import api, { apiError } from '../src/api/client';
import { Badge, Button, Chip, EmptyState, Loading } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';

const NEXT_STATUS = {
  ASSIGNED: { status: 'PICKED_UP', label: 'Confirmar coleta' },
  PICKED_UP: { status: 'IN_TRANSIT', label: 'Iniciar entrega' },
  IN_TRANSIT: { status: 'DELIVERED', label: 'Confirmar entrega' },
};

/**
 * Interface do entregador: rotas de entrega e coletas de reembolso
 * (logistica reversa, com a localizacao do comprador).
 */
export default function Courier() {
  const [tab, setTab] = useState('available');
  const [deliveries, setDeliveries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(null);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get(tab === 'available' ? '/deliveries/available' : '/deliveries/mine');
      setDeliveries(data);
    } catch {
      setDeliveries([]);
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useFocusEffect(
    useCallback(() => {
      setLoading(true);
      load();
    }, [load])
  );

  async function accept(delivery) {
    setActing(delivery.id);
    try {
      await api.post(`/deliveries/${delivery.id}/accept`);
      setTab('mine');
      Alert.alert('Rota aceita!', 'A rota está na sua lista. Boa entrega!');
    } catch (error) {
      Alert.alert('Ops', apiError(error));
      await load();
    } finally {
      setActing(null);
    }
  }

  async function advance(delivery) {
    const next = NEXT_STATUS[delivery.status];
    if (!next) return;

    setActing(delivery.id);
    try {
      await api.patch(`/deliveries/${delivery.id}`, { status: next.status });
      await load();
      if (next.status === 'DELIVERED') {
        Alert.alert(
          'Rota concluída!',
          delivery.type === 'RETURN'
            ? 'Coleta reversa finalizada. O reembolso do comprador foi processado.'
            : 'Entrega confirmada. O período de teste de 4 dias do comprador começou.'
        );
      }
    } catch (error) {
      Alert.alert('Ops', apiError(error));
    } finally {
      setActing(null);
    }
  }

  function openMaps(latitude, longitude, label) {
    const query = latitude && longitude ? `${latitude},${longitude}` : encodeURIComponent(label);
    Linking.openURL(`https://www.google.com/maps/search/?api=1&query=${query}`);
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.white }}>
      <View style={styles.tabs}>
        <Chip label="rotas disponíveis" selected={tab === 'available'} onPress={() => setTab('available')} />
        <Chip label="minhas rotas" selected={tab === 'mine'} onPress={() => setTab('mine')} />
      </View>

      {loading ? (
        <Loading />
      ) : (
        <FlatList
          data={deliveries}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => (
            <View style={styles.card}>
              <View style={styles.cardHeader}>
                <Badge
                  label={item.type === 'RETURN' ? 'Coleta de reembolso' : 'Entrega'}
                  tone={item.type === 'RETURN' ? 'danger' : 'success'}
                />
                <Text style={styles.orderId}>#{item.orderId.slice(-6).toUpperCase()}</Text>
              </View>

              {item.order?.items?.length ? (
                <Text style={styles.items} numberOfLines={2}>
                  {item.order.items.map((product) => product.titleSnapshot).join(', ')}
                </Text>
              ) : null}

              <Pressable
                style={styles.stop}
                onPress={() => openMaps(item.pickupLatitude, item.pickupLongitude, item.pickupAddress)}
              >
                <View style={[styles.stopIcon, { backgroundColor: colors.primaryLight }]}>
                  <Ionicons name="arrow-up-outline" size={15} color={colors.primaryDark} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.stopLabel}>{item.pickupLabel}</Text>
                  <Text style={styles.stopAddress}>{item.pickupAddress}</Text>
                </View>
                <Ionicons name="navigate-outline" size={18} color={colors.primaryDark} />
              </Pressable>

              <Pressable
                style={styles.stop}
                onPress={() => openMaps(item.dropoffLatitude, item.dropoffLongitude, item.dropoffAddress)}
              >
                <View style={[styles.stopIcon, { backgroundColor: colors.infoLight }]}>
                  <Ionicons name="arrow-down-outline" size={15} color={colors.info} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.stopLabel}>{item.dropoffLabel}</Text>
                  <Text style={styles.stopAddress}>{item.dropoffAddress}</Text>
                </View>
                <Ionicons name="navigate-outline" size={18} color={colors.info} />
              </Pressable>

              {tab === 'available' ? (
                <Button
                  title="Aceitar rota"
                  size="sm"
                  icon="checkmark-outline"
                  loading={acting === item.id}
                  onPress={() => accept(item)}
                  style={{ marginTop: spacing.md }}
                />
              ) : NEXT_STATUS[item.status] ? (
                <Button
                  title={NEXT_STATUS[item.status].label}
                  size="sm"
                  icon="arrow-forward-outline"
                  loading={acting === item.id}
                  onPress={() => advance(item)}
                  style={{ marginTop: spacing.md }}
                />
              ) : null}
            </View>
          )}
          ListEmptyComponent={
            <EmptyState
              icon="bicycle-outline"
              title={tab === 'available' ? 'Nenhuma rota aberta' : 'Você não tem rotas ativas'}
              description={
                tab === 'available'
                  ? 'Assim que um pedido com entrega da plataforma for pago, a rota aparece aqui.'
                  : 'Aceite uma rota disponível para começar.'
              }
            />
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  tabs: { flexDirection: 'row', padding: spacing.lg, paddingBottom: spacing.md },
  list: { paddingHorizontal: spacing.lg, paddingBottom: spacing.xxl },
  card: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  orderId: { ...typography.tiny, fontWeight: '800' },
  items: { ...typography.small, marginBottom: spacing.md },

  stop: { flexDirection: 'row', alignItems: 'center', paddingVertical: spacing.sm },
  stopIcon: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  stopLabel: { ...typography.small, fontWeight: '700', color: colors.ink },
  stopAddress: { ...typography.tiny, marginTop: 1, lineHeight: 15 },
});
