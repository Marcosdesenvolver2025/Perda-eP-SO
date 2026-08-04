import { useCallback, useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api from '../../src/api/client';
import { useAuth } from '../../src/contexts/AuthContext';
import { EmptyState, Loading } from '../../src/components/ui';
import { colors, radius, spacing, typography } from '../../src/theme';
import { timeAgo } from '../../src/utils/format';

export default function ChatList() {
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    if (!isAuthenticated) {
      setConversations([]);
      setLoading(false);
      return;
    }
    try {
      const { data } = await api.get('/conversations');
      setConversations(data);
    } catch {
      setConversations([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [isAuthenticated]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  if (loading) return <Loading />;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Text style={styles.title}>conversas</Text>

      <FlatList
        data={conversations}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ paddingHorizontal: spacing.lg, paddingBottom: spacing.xxl }}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            colors={[colors.primary]}
            tintColor={colors.primary}
            onRefresh={() => {
              setRefreshing(true);
              load();
            }}
          />
        }
        renderItem={({ item }) => (
          <Pressable
            style={({ pressed }) => [styles.row, pressed && { opacity: 0.8 }]}
            onPress={() => router.push(`/chat/${item.id}`)}
          >
            {item.product?.image ? (
              <Image source={{ uri: item.product.image }} style={styles.thumb} contentFit="cover" />
            ) : (
              <View style={[styles.thumb, styles.thumbFallback]}>
                <Ionicons name="chatbubble-outline" size={20} color={colors.textLight} />
              </View>
            )}

            <View style={styles.rowContent}>
              <View style={styles.rowHeader}>
                <Text style={styles.name} numberOfLines={1}>
                  {item.other?.name || 'Usuário'}
                </Text>
                <Text style={styles.time}>{timeAgo(item.lastMessageAt)}</Text>
              </View>
              <Text style={styles.product} numberOfLines={1}>
                {item.product?.title || 'Conversa'}
              </Text>
              <Text style={styles.preview} numberOfLines={1}>
                {item.lastMessage || 'Inicie a conversa'}
              </Text>
            </View>

            {item.unreadCount > 0 ? (
              <View style={styles.unread}>
                <Text style={styles.unreadText}>{item.unreadCount}</Text>
              </View>
            ) : null}
          </Pressable>
        )}
        ListEmptyComponent={
          <EmptyState
            icon="chatbubbles-outline"
            title="Nenhuma conversa ainda"
            description="Quando você negociar um produto, a conversa aparece aqui."
            actionLabel="Explorar produtos"
            onAction={() => router.push('/(tabs)')}
          />
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  title: { ...typography.h1, fontSize: 24, paddingHorizontal: spacing.lg, marginBottom: spacing.md },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  thumb: { width: 54, height: 54, borderRadius: radius.md, backgroundColor: colors.surfaceAlt },
  thumbFallback: { alignItems: 'center', justifyContent: 'center' },
  rowContent: { flex: 1, marginLeft: spacing.md },
  rowHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  name: { ...typography.h3, fontSize: 15, flex: 1 },
  time: { ...typography.tiny },
  product: { ...typography.tiny, color: colors.primaryDark, fontWeight: '700', marginTop: 1 },
  preview: { ...typography.small, marginTop: 2 },
  unread: {
    minWidth: 22,
    height: 22,
    paddingHorizontal: 6,
    borderRadius: 11,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: spacing.sm,
  },
  unreadText: { color: colors.white, fontSize: 11, fontWeight: '800' },
});
