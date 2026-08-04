import { useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import * as Notifications from 'expo-notifications';
import api from '../src/api/client';
import { useAuth } from '../src/contexts/AuthContext';
import { Divider } from '../src/components/ui';
import { colors, radius, spacing, typography } from '../src/theme';

const OPTIONS = [
  {
    key: 'offers',
    title: 'Ofertas e novidades',
    description: 'Avisamos quando aparecer algo do seu interesse pertinho de você.',
  },
  {
    key: 'sales',
    title: 'Minhas vendas',
    description: 'Pedidos novos, pagamentos aprovados e status de entrega.',
  },
  {
    key: 'messages',
    title: 'Mensagens',
    description: 'Quando alguém responder na negociação de um produto.',
  },
];

export default function NotificationSettings() {
  const { user, refreshUser } = useAuth();
  const [values, setValues] = useState({ offers: true, sales: true, messages: true });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (user?.notifications) setValues(user.notifications);
  }, [user]);

  async function toggle(key, value) {
    const next = { ...values, [key]: value };
    setValues(next);
    setSaving(true);

    try {
      // Ao ligar qualquer alerta, garantimos a permissao e o token de push.
      if (value) {
        const { status } = await Notifications.getPermissionsAsync();
        let granted = status === 'granted';
        if (!granted) {
          const request = await Notifications.requestPermissionsAsync();
          granted = request.status === 'granted';
        }

        if (granted) {
          try {
            const token = await Notifications.getExpoPushTokenAsync();
            await api.patch('/users/me', { notifications: next, pushToken: token.data });
            await refreshUser();
            return;
          } catch {
            /* segue salvando so as preferencias */
          }
        }
      }

      await api.patch('/users/me', { notifications: next });
      await refreshUser();
    } catch {
      setValues(values);
      Alert.alert('Ops', 'Não foi possível salvar sua preferência.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <ScrollView style={{ backgroundColor: colors.white }} contentContainerStyle={styles.content}>
      <Text style={styles.intro}>
        Escolha o que você quer receber. Você pode mudar isso quando quiser.
      </Text>

      <View style={styles.card}>
        {OPTIONS.map((option, index) => (
          <View key={option.key}>
            <View style={styles.row}>
              <View style={{ flex: 1, marginRight: spacing.lg }}>
                <Text style={styles.title}>{option.title}</Text>
                <Text style={styles.description}>{option.description}</Text>
              </View>
              <Switch
                value={values[option.key]}
                onValueChange={(value) => toggle(option.key, value)}
                disabled={saving}
                trackColor={{ false: colors.borderStrong, true: colors.primary }}
                thumbColor={colors.white}
              />
            </View>
            {index < OPTIONS.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg },
  intro: { ...typography.small, marginBottom: spacing.lg, lineHeight: 19 },
  card: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.lg, paddingHorizontal: spacing.lg },
  row: { flexDirection: 'row', alignItems: 'center', paddingVertical: spacing.lg },
  title: { ...typography.body, fontWeight: '700', color: colors.ink },
  description: { ...typography.tiny, marginTop: 2, lineHeight: 16 },
});
