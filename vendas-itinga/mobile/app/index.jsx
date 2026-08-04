import { Redirect } from 'expo-router';
import { useAuth } from '../src/contexts/AuthContext';
import { Loading } from '../src/components/ui';

/** Porta de entrada: decide entre o login e o feed. */
export default function Index() {
  const { loading, isAuthenticated } = useAuth();

  if (loading) return <Loading label="Carregando..." />;
  return <Redirect href={isAuthenticated ? '/(tabs)' : '/(auth)/login'} />;
}
