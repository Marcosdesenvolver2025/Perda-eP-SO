import { useEffect, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../../src/contexts/AuthContext';
import { Button } from '../../src/components/ui';
import { API_URL } from '../../src/api/client';
import { colors, radius, spacing, typography } from '../../src/theme';

const HIGHLIGHTS = [
  { icon: 'pricetags-outline', text: 'Anuncie em minutos, direto do celular' },
  { icon: 'bicycle-outline', text: 'Entrega própria Vendas Itinga na sua região' },
  { icon: 'shield-checkmark-outline', text: '4 dias para testar o produto ou pedir reembolso' },
];

export default function Login() {
  const router = useRouter();
  const { signInWithGoogle, signingIn, googleReady, googleConfigured, isAuthenticated, connectionError } =
    useAuth();
  const [error, setError] = useState(null);

  // Navegar em efeito, nunca durante a renderizacao: chamar router.replace()
  // no corpo do componente quebra o roteador e pode gerar laco de render.
  useEffect(() => {
    if (isAuthenticated) router.replace('/(tabs)');
  }, [isAuthenticated, router]);

  async function handleGoogle() {
    setError(null);
    const result = await signInWithGoogle();
    if (!result.ok && result.message) setError(result.message);
  }

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.hero}>
          <Image source={require('../../assets/icon.png')} style={styles.logo} />
          <Text style={styles.brand}>vendas itinga</Text>
          <Text style={styles.tagline}>compre e venda pertinho de você</Text>
        </View>

        <View style={styles.highlights}>
          {HIGHLIGHTS.map((item) => (
            <View key={item.text} style={styles.highlightRow}>
              <View style={styles.highlightIcon}>
                <Ionicons name={item.icon} size={18} color={colors.primaryDark} />
              </View>
              <Text style={styles.highlightText}>{item.text}</Text>
            </View>
          ))}
        </View>

        <View style={styles.footer}>
          {connectionError ? (
            <View style={styles.banner}>
              <Ionicons name="cloud-offline-outline" size={16} color={colors.danger} />
              <Text style={styles.bannerText}>{connectionError}</Text>
            </View>
          ) : null}

          {!googleConfigured ? (
            <View style={[styles.banner, styles.bannerWarning]}>
              <Ionicons name="construct-outline" size={16} color="#B26B00" />
              <Text style={[styles.bannerText, { color: '#B26B00' }]}>
                Login do Google ainda não configurado. Preencha os client IDs no arquivo .env do app
                para habilitar a entrada.
              </Text>
            </View>
          ) : null}

          {error ? <Text style={styles.error}>{error}</Text> : null}

          <Pressable
            onPress={handleGoogle}
            disabled={signingIn || (!googleReady && googleConfigured)}
            style={({ pressed }) => [
              styles.googleButton,
              signingIn && { opacity: 0.6 },
              pressed && { opacity: 0.85 },
            ]}
          >
            <Ionicons name="logo-google" size={20} color="#EA4335" />
            <Text style={styles.googleText}>
              {signingIn ? 'Entrando...' : 'Continuar com o Google'}
            </Text>
          </Pressable>

          <Button
            title="Ver como funciona"
            variant="ghost"
            onPress={() => router.replace('/(tabs)')}
            style={{ marginTop: spacing.md }}
          />

          <Text style={styles.terms}>
            Ao entrar você concorda com os Termos de Uso e a Política de Privacidade do Vendas Itinga.
          </Text>
          {__DEV__ ? <Text style={styles.debug}>API: {API_URL}</Text> : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  scroll: { flexGrow: 1, paddingHorizontal: spacing.xl },
  hero: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: spacing.xxl },
  logo: { width: 96, height: 96, borderRadius: 26 },
  brand: { ...typography.h1, fontSize: 32, marginTop: spacing.lg },
  tagline: { ...typography.body, color: colors.textMuted, marginTop: 4 },

  highlights: { marginBottom: spacing.xxl },
  highlightRow: { flexDirection: 'row', alignItems: 'center', marginBottom: spacing.md },
  highlightIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: colors.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  highlightText: { ...typography.small, color: colors.text, flex: 1 },

  footer: { paddingBottom: spacing.lg },
  banner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.dangerLight,
    marginBottom: spacing.md,
  },
  bannerWarning: { backgroundColor: colors.warningLight },
  bannerText: { ...typography.tiny, color: colors.danger, flex: 1, marginLeft: 6, lineHeight: 16 },

  googleButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 54,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    backgroundColor: colors.white,
  },
  googleText: { ...typography.h3, fontSize: 16, marginLeft: spacing.md },
  error: {
    ...typography.small,
    color: colors.danger,
    textAlign: 'center',
    marginBottom: spacing.md,
  },
  terms: { ...typography.tiny, textAlign: 'center', marginTop: spacing.lg, lineHeight: 16 },
  debug: { ...typography.tiny, textAlign: 'center', marginTop: 4, color: colors.textLight },
});
