import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api, { apiError } from '../api/client';
import { useAuth } from './AuthContext';

const CartContext = createContext(null);

const EMPTY = { groups: [], itemsTotalCents: 0, shippingTotalCents: 0, totalCents: 0, itemCount: 0 };

export function CartProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState(EMPTY);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setCart(EMPTY);
      return EMPTY;
    }
    setLoading(true);
    try {
      const { data } = await api.get('/cart');
      setCart(data);
      return data;
    } catch {
      return EMPTY;
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const addItem = useCallback(
    async (productId, quantity = 1) => {
      try {
        await api.post('/cart', { productId, quantity });
        await refresh();
        return { ok: true };
      } catch (error) {
        return { ok: false, message: apiError(error) };
      }
    },
    [refresh]
  );

  const removeItem = useCallback(
    async (cartItemId) => {
      try {
        await api.delete(`/cart/${cartItemId}`);
        await refresh();
        return { ok: true };
      } catch (error) {
        return { ok: false, message: apiError(error) };
      }
    },
    [refresh]
  );

  const clear = useCallback(async () => {
    try {
      await api.delete('/cart');
    } finally {
      setCart(EMPTY);
    }
  }, []);

  const value = useMemo(
    () => ({ cart, loading, refresh, addItem, removeItem, clear, count: cart.itemCount }),
    [cart, loading, refresh, addItem, removeItem, clear]
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart precisa estar dentro de <CartProvider>.');
  return context;
}
