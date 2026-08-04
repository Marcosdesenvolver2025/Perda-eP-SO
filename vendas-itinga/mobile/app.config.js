/**
 * Configuracao dinamica do Expo.
 *
 * Le o app.json e injeta as variaveis de ambiente em `extra`, para que
 * NENHUMA chave fique escrita no codigo-fonte. Em builds do EAS, defina
 * estas variaveis em eas.json (env) ou como secrets do projeto:
 *
 *   eas secret:create --name EXPO_PUBLIC_API_URL --value https://api.seudominio.com
 */

module.exports = ({ config }) => ({
  ...config,
  extra: {
    ...config.extra,
    apiUrl: process.env.EXPO_PUBLIC_API_URL || 'http://localhost:3333',

    // Chave PUBLICA do Pagar.me - usada apenas para tokenizar o cartao no
    // dispositivo. A chave SECRETA vive somente no backend.
    pagarmePublicKey: process.env.EXPO_PUBLIC_PAGARME_PUBLIC_KEY || '',

    // Google Sign-In (Google Cloud Console > Credenciais > OAuth 2.0)
    googleWebClientId: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID || '',
    googleAndroidClientId: process.env.EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID || '',
    googleIosClientId: process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID || '',

    // Firebase (opcional - se preferir autenticar via Firebase Auth)
    firebaseApiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY || '',
    firebaseAuthDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN || '',
    firebaseProjectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID || '',
    firebaseAppId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID || '',
  },
});
