import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, shadow, spacing, typography } from '../theme';

/** Botao principal do app. Variantes: primary, outline, ghost, danger. */
export function Button({
  title,
  onPress,
  variant = 'primary',
  icon,
  loading = false,
  disabled = false,
  style,
  size = 'md',
}) {
  const isDisabled = disabled || loading;
  const palette = {
    primary: { bg: colors.primary, fg: colors.white, border: colors.primary },
    outline: { bg: 'transparent', fg: colors.ink, border: colors.borderStrong },
    ghost: { bg: colors.primaryLight, fg: colors.primaryDark, border: colors.primaryLight },
    danger: { bg: colors.dangerLight, fg: colors.danger, border: colors.dangerLight },
  }[variant];

  const height = size === 'sm' ? 40 : size === 'lg' ? 56 : 50;

  return (
    <Pressable
      onPress={isDisabled ? undefined : onPress}
      style={({ pressed }) => [
        styles.button,
        {
          height,
          backgroundColor: palette.bg,
          borderColor: palette.border,
          opacity: isDisabled ? 0.5 : pressed ? 0.85 : 1,
          transform: [{ scale: pressed && !isDisabled ? 0.99 : 1 }],
        },
        style,
      ]}
      accessibilityRole="button"
      accessibilityLabel={title}
    >
      {loading ? (
        <ActivityIndicator color={palette.fg} />
      ) : (
        <>
          {icon ? <Ionicons name={icon} size={18} color={palette.fg} style={{ marginRight: 8 }} /> : null}
          <Text style={[styles.buttonText, { color: palette.fg, fontSize: size === 'sm' ? 14 : 16 }]}>
            {title}
          </Text>
        </>
      )}
    </Pressable>
  );
}

/** Campo de texto com rotulo, dica e mensagem de erro. */
export function Field({ label, hint, error, style, ...inputProps }) {
  return (
    <View style={[{ marginBottom: spacing.lg }, style]}>
      {label ? <Text style={styles.label}>{label}</Text> : null}
      <TextInput
        placeholderTextColor={colors.textLight}
        style={[styles.input, error && { borderColor: colors.danger }]}
        {...inputProps}
      />
      {error ? (
        <Text style={styles.error}>{error}</Text>
      ) : hint ? (
        <Text style={styles.hint}>{hint}</Text>
      ) : null}
    </View>
  );
}

/** Etiqueta de status colorida. */
export function Badge({ label, tone = 'success', style }) {
  const palette = {
    success: { bg: colors.successLight, fg: colors.primaryDark },
    info: { bg: colors.infoLight, fg: colors.info },
    warning: { bg: colors.warningLight, fg: '#B26B00' },
    danger: { bg: colors.dangerLight, fg: colors.danger },
    neutral: { bg: colors.surfaceAlt, fg: colors.textMuted },
  }[tone];

  return (
    <View style={[styles.badge, { backgroundColor: palette.bg }, style]}>
      <Text style={[styles.badgeText, { color: palette.fg }]} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

/** Opcao selecionavel em formato de "pilula" (filtros, frete, condicao). */
export function Chip({ label, selected, onPress, icon, style }) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.chip,
        selected && { backgroundColor: colors.primary, borderColor: colors.primary },
        pressed && { opacity: 0.85 },
        style,
      ]}
    >
      {icon ? (
        <Ionicons
          name={icon}
          size={15}
          color={selected ? colors.white : colors.textMuted}
          style={{ marginRight: 6 }}
        />
      ) : null}
      <Text style={[styles.chipText, selected && { color: colors.white, fontWeight: '700' }]}>
        {label}
      </Text>
    </Pressable>
  );
}

/** Estado vazio com icone, titulo e acao opcional. */
export function EmptyState({ icon = 'sad-outline', title, description, actionLabel, onAction }) {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyIcon}>
        <Ionicons name={icon} size={34} color={colors.primary} />
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      {description ? <Text style={styles.emptyDescription}>{description}</Text> : null}
      {actionLabel ? (
        <Button title={actionLabel} onPress={onAction} style={{ marginTop: spacing.lg, minWidth: 200 }} />
      ) : null}
    </View>
  );
}

export function Loading({ label }) {
  return (
    <View style={styles.loading}>
      <ActivityIndicator size="large" color={colors.primary} />
      {label ? <Text style={styles.loadingLabel}>{label}</Text> : null}
    </View>
  );
}

export function Divider({ style }) {
  return <View style={[{ height: 1, backgroundColor: colors.border }, style]} />;
}

/** Linha de "chave: valor" usada nos resumos de preco. */
export function SummaryRow({ label, value, strong, tone }) {
  return (
    <View style={styles.summaryRow}>
      <Text style={[styles.summaryLabel, strong && { color: colors.ink, fontWeight: '700' }]}>
        {label}
      </Text>
      <Text
        style={[
          styles.summaryValue,
          strong && { fontSize: 18, fontWeight: '800' },
          tone === 'success' && { color: colors.primaryDark },
          tone === 'danger' && { color: colors.danger },
        ]}
      >
        {value}
      </Text>
    </View>
  );
}

export function Card({ children, style }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.pill,
    borderWidth: 1.5,
    paddingHorizontal: spacing.xl,
  },
  buttonText: { fontWeight: '700' },

  label: { ...typography.small, color: colors.text, fontWeight: '700', marginBottom: 6 },
  input: {
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: 13,
    fontSize: 15,
    color: colors.ink,
    backgroundColor: colors.white,
  },
  hint: { ...typography.tiny, marginTop: 5 },
  error: { ...typography.tiny, color: colors.danger, marginTop: 5 },

  badge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: radius.pill,
    alignSelf: 'flex-start',
  },
  badgeText: { fontSize: 11, fontWeight: '800', letterSpacing: 0.2 },

  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.white,
    marginRight: spacing.sm,
  },
  chipText: { fontSize: 13, color: colors.text, fontWeight: '600' },

  empty: { alignItems: 'center', justifyContent: 'center', padding: spacing.xxl, paddingTop: 60 },
  emptyIcon: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: colors.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  emptyTitle: { ...typography.h3, textAlign: 'center' },
  emptyDescription: {
    ...typography.small,
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 20,
    maxWidth: 280,
  },

  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  loadingLabel: { ...typography.small, marginTop: spacing.md },

  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 7,
  },
  summaryLabel: { ...typography.body, color: colors.textMuted },
  summaryValue: { ...typography.body, color: colors.ink, fontWeight: '600' },

  card: {
    backgroundColor: colors.white,
    borderRadius: radius.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadow.card,
  },
});
