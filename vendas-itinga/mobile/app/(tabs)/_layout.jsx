import { Tabs } from 'expo-router';
import { View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors } from '../../src/theme';

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primaryDark,
        tabBarInactiveTintColor: colors.textLight,
        tabBarStyle: {
          backgroundColor: colors.white,
          borderTopColor: colors.border,
          height: 62,
          paddingBottom: 8,
          paddingTop: 6,
        },
        tabBarLabelStyle: { fontSize: 11, fontWeight: '700' },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'início',
          tabBarIcon: ({ color, size }) => <Ionicons name="home-outline" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="search"
        options={{
          title: 'buscar',
          tabBarIcon: ({ color, size }) => <Ionicons name="search-outline" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="sell"
        options={{
          title: 'vender',
          tabBarIcon: ({ focused }) => (
            <View
              style={{
                width: 44,
                height: 44,
                borderRadius: 22,
                backgroundColor: focused ? colors.primaryDark : colors.primary,
                alignItems: 'center',
                justifyContent: 'center',
                marginTop: -14,
              }}
            >
              <Ionicons name="add" size={26} color={colors.white} />
            </View>
          ),
        }}
      />
      <Tabs.Screen
        name="chat"
        options={{
          title: 'conversas',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="chatbubble-ellipses-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: 'perfil',
          tabBarIcon: ({ color, size }) => <Ionicons name="person-outline" size={size} color={color} />,
        }}
      />
    </Tabs>
  );
}
