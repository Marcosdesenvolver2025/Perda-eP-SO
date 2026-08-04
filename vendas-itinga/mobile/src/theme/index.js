/**
 * Design system do Vendas Itinga.
 * Paleta oficial: branco e verde #2BCC3E.
 */

export const colors = {
  primary: '#2BCC3E',
  primaryDark: '#1FA830',
  primaryLight: '#E9FBEC',
  primarySoft: '#F4FEF6',

  white: '#FFFFFF',
  background: '#FFFFFF',
  surface: '#FFFFFF',
  surfaceAlt: '#F7F8F7',

  ink: '#151A16',
  text: '#1F2621',
  textMuted: '#6B7770',
  textLight: '#9AA5A0',

  border: '#E8EBE9',
  borderStrong: '#D6DBD8',

  danger: '#E5484D',
  dangerLight: '#FDECEC',
  warning: '#F5A524',
  warningLight: '#FEF6E7',
  info: '#2E90FA',
  infoLight: '#EAF3FF',
  success: '#2BCC3E',
  successLight: '#E9FBEC',

  overlay: 'rgba(0,0,0,0.45)',
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
};

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  pill: 999,
};

export const typography = {
  h1: { fontSize: 26, fontWeight: '800', color: colors.ink, letterSpacing: -0.5 },
  h2: { fontSize: 20, fontWeight: '800', color: colors.ink, letterSpacing: -0.3 },
  h3: { fontSize: 17, fontWeight: '700', color: colors.ink },
  body: { fontSize: 15, color: colors.text },
  small: { fontSize: 13, color: colors.textMuted },
  tiny: { fontSize: 11, color: colors.textLight },
  price: { fontSize: 18, fontWeight: '800', color: colors.ink },
};

export const shadow = {
  card: {
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 2,
  },
  float: {
    shadowColor: '#000',
    shadowOpacity: 0.14,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
};

export default { colors, spacing, radius, typography, shadow };
