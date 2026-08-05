import { useState } from 'react';
import { Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Button } from './ui';
import { API_URL, API_URL_WARNING, checkConnection } from '../api/client';
import { colors, radius, spacing, typography } from '../theme';

/**
 * Tela de erro amigavel: usada pelo ErrorBoundary do roteador e por
 * qualquer tela que nao consiga falar com a API.
 *
 * Com `showDiagnostics`, mostra o endereco configurado e um botao que testa
 * a conexao - resolve a maior parte dos casos de "IP errado" sem depender
 * de logs do Metro.
 */
export default function ErrorScreen({
  title = 'Erro de conexão',
  message,
  onRetry,
  showDiagnostics = true,
  icon = 'cloud-offline-outline',
}) {
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null);

  async function testConnection() {
    setTesting(true);
    setTestResult(null);
    const result = await checkConnection();
    setTestResult(
      result.ok
        ? { ok: true, text: 'Servidor respondeu. Toque em "Tentar de novo".' }
        : { ok: false, text: result.message }
    );
    setTesting(false);
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.icon}>
        <Ionicons name={icon} size={38} color={colors.danger} />
      </View>

      <Text style={styles.title}>{title}</Text>
      {message ? <Text style={styles.message}>{message}</Text> : null}

      {onRetry ? (
        <Button
          title="Tentar de novo"
          icon="refresh-outline"
          onPress={onRetry}
          style={{ marginTop: spacing.xl, minWidth: 220 }}
        />
      ) : null}

      {showDiagnostics ? (
        <View style={styles.diagnostics}>
          <Text style={styles.diagnosticsTitle}>diagnóstico</Text>

          <View style={styles.row}>
            <Text style={styles.rowLabel}>API</Text>
            <Text style={styles.rowValue} selectable>
              {API_URL}
            </Text>
          </View>

          <View style={styles.row}>
            <Text style={styles.rowLabel}>Plataforma</Text>
            <Text style={styles.rowValue}>{Platform.OS}</Text>
          </View>

          {API_URL_WARNING ? <Text style={styles.warning}>{API_URL_WARNING}</Text> : null}

          <Button
            title={testing ? 'Testando...' : 'Testar conexão com a API'}
            variant="outline"
            size="sm"
            loading={testing}
            onPress={testConnection}
            style={{ marginTop: spacing.md }}
          />

          {testResult ? (
            <Text style={[styles.testResult, testResult.ok && { color: colors.primaryDark }]}>
              {testResult.text}
            </Text>
          ) : null}

          <Text style={styles.help}>
            Confira se a API está rodando (npm run dev) e se o endereço em
            EXPO_PUBLIC_API_URL usa o IP da sua máquina na rede — não localhost.
          </Text>
        </View>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    backgroundColor: colors.white,
  },
  icon: {
    width: 78,
    height: 78,
    borderRadius: 39,
    backgroundColor: colors.dangerLight,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { ...typography.h2, marginTop: spacing.lg, textAlign: 'center' },
  message: {
    ...typography.small,
    textAlign: 'center',
    marginTop: spacing.sm,
    lineHeight: 20,
    maxWidth: 320,
  },

  diagnostics: {
    marginTop: spacing.xxl,
    padding: spacing.lg,
    borderRadius: radius.lg,
    backgroundColor: colors.surfaceAlt,
    width: '100%',
    maxWidth: 400,
  },
  diagnosticsTitle: {
    ...typography.tiny,
    fontWeight: '800',
    color: colors.ink,
    marginBottom: spacing.sm,
  },
  row: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 3 },
  rowLabel: { ...typography.tiny, marginRight: spacing.md },
  rowValue: { ...typography.tiny, color: colors.ink, fontWeight: '700', flex: 1, textAlign: 'right' },
  warning: {
    ...typography.tiny,
    color: '#B26B00',
    marginTop: spacing.sm,
    lineHeight: 16,
  },
  testResult: { ...typography.tiny, color: colors.danger, marginTop: spacing.sm, lineHeight: 16 },
  help: { ...typography.tiny, marginTop: spacing.md, lineHeight: 16 },
});
