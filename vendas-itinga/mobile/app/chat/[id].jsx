import { useCallback, useEffect, useRef, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useLocalSearchParams, useNavigation, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import api from '../../src/api/client';
import { Loading } from '../../src/components/ui';
import { colors, radius, spacing, typography } from '../../src/theme';
import { formatBRL, timeAgo } from '../../src/utils/format';

export default function Conversation() {
  const { id } = useLocalSearchParams();
  const router = useRouter();
  const navigation = useNavigation();
  const listRef = useRef(null);

  const [conversation, setConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get(`/conversations/${id}/messages`);
      setConversation(data);
      setMessages(data.messages);
      navigation.setOptions({ title: data.other?.name || 'Conversa' });
    } catch {
      setConversation(null);
    } finally {
      setLoading(false);
    }
  }, [id, navigation]);

  useEffect(() => {
    load();
    // Atualizacao periodica simples. Para tempo real, troque por
    // WebSocket/SSE no backend sem mudar esta tela.
    const timer = setInterval(load, 12000);
    return () => clearInterval(timer);
  }, [load]);

  async function send() {
    const body = draft.trim();
    if (!body || sending) return;

    setSending(true);
    setDraft('');

    // Mensagem otimista: aparece na hora e some se falhar.
    const optimistic = { id: `temp-${Date.now()}`, body, mine: true, createdAt: new Date().toISOString() };
    setMessages((prev) => [...prev, optimistic]);

    try {
      const { data } = await api.post(`/conversations/${id}/messages`, { body });
      setMessages((prev) => prev.map((item) => (item.id === optimistic.id ? data : item)));
    } catch {
      setMessages((prev) => prev.filter((item) => item.id !== optimistic.id));
      setDraft(body);
    } finally {
      setSending(false);
    }
  }

  if (loading) return <Loading />;

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.white }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 92 : 0}
    >
      {conversation?.product ? (
        <Pressable
          style={styles.productBar}
          onPress={() => router.push(`/product/${conversation.product.id}`)}
        >
          {conversation.product.image ? (
            <Image source={{ uri: conversation.product.image }} style={styles.productThumb} contentFit="cover" />
          ) : null}
          <View style={{ flex: 1, marginLeft: spacing.md }}>
            <Text style={styles.productTitle} numberOfLines={1}>
              {conversation.product.title}
            </Text>
            <Text style={styles.productPrice}>{formatBRL(conversation.product.priceCents)}</Text>
          </View>
          <Ionicons name="chevron-forward" size={18} color={colors.textLight} />
        </Pressable>
      ) : null}

      <FlatList
        ref={listRef}
        data={messages}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.messages}
        onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: false })}
        renderItem={({ item }) => (
          <View style={[styles.bubbleRow, item.mine && { justifyContent: 'flex-end' }]}>
            <View style={[styles.bubble, item.mine ? styles.bubbleMine : styles.bubbleOther]}>
              <Text style={[styles.bubbleText, item.mine && { color: colors.white }]}>{item.body}</Text>
              <Text style={[styles.bubbleTime, item.mine && { color: 'rgba(255,255,255,0.75)' }]}>
                {timeAgo(item.createdAt)}
              </Text>
            </View>
          </View>
        )}
        ListEmptyComponent={
          <View style={styles.emptyChat}>
            <Ionicons name="chatbubbles-outline" size={32} color={colors.textLight} />
            <Text style={styles.emptyChatText}>
              Diga oi! Pergunte sobre o estado do produto, medidas ou combine a entrega.
            </Text>
          </View>
        }
      />

      <View style={styles.composer}>
        <TextInput
          value={draft}
          onChangeText={setDraft}
          placeholder="Escreva uma mensagem..."
          placeholderTextColor={colors.textLight}
          style={styles.input}
          multiline
          maxLength={1000}
        />
        <Pressable
          style={[styles.sendButton, (!draft.trim() || sending) && { opacity: 0.5 }]}
          onPress={send}
          disabled={!draft.trim() || sending}
        >
          <Ionicons name="send" size={18} color={colors.white} />
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  productBar: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  productThumb: { width: 40, height: 46, borderRadius: radius.sm, backgroundColor: colors.surfaceAlt },
  productTitle: { ...typography.small, fontWeight: '700', color: colors.ink },
  productPrice: { ...typography.small, color: colors.primaryDark, fontWeight: '800' },

  messages: { padding: spacing.lg, flexGrow: 1 },
  bubbleRow: { flexDirection: 'row', marginBottom: spacing.sm },
  bubble: { maxWidth: '78%', padding: spacing.md, borderRadius: radius.lg },
  bubbleMine: { backgroundColor: colors.primary, borderBottomRightRadius: 4 },
  bubbleOther: { backgroundColor: colors.surfaceAlt, borderBottomLeftRadius: 4 },
  bubbleText: { ...typography.body, color: colors.text, lineHeight: 20 },
  bubbleTime: { ...typography.tiny, marginTop: 3, alignSelf: 'flex-end' },

  emptyChat: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  emptyChatText: {
    ...typography.small,
    textAlign: 'center',
    marginTop: spacing.md,
    maxWidth: 260,
    lineHeight: 19,
  },

  composer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    padding: spacing.md,
    paddingBottom: spacing.lg,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: spacing.sm,
  },
  input: {
    flex: 1,
    maxHeight: 110,
    paddingHorizontal: spacing.lg,
    paddingVertical: 11,
    borderRadius: radius.xl,
    backgroundColor: colors.surfaceAlt,
    fontSize: 15,
    color: colors.ink,
  },
  sendButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
