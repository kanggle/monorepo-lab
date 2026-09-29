'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { CartItem } from './types';
import { calculateTotal, calculateItemCount } from '../lib/calculate-total';
import { useAuth } from '@/shared/lib/auth-context';

/**
 * TASK-FE-102 — two carts, two keys.
 *
 * - `cart` — the ACCOUNT cart (the key logout and the 401 handler already clear).
 * - `cart:guest` — the GUEST cart of a visitor who has not logged in.
 *
 * 🔴 They must not share a key. When the page opens logged out, the account cart is
 * wiped (EF-3): a previous user's cart left behind by an expired session must never
 * show up as the next visitor's guest cart. One key could not both be wiped on an
 * anonymous load and be the guest cart.
 */
export const ACCOUNT_CART_KEY = 'cart';
export const GUEST_CART_KEY = 'cart:guest';

type Owner = 'guest' | 'account';

const keyOf = (owner: Owner) => (owner === 'account' ? ACCOUNT_CART_KEY : GUEST_CART_KEY);

interface CartContextValue {
  items: CartItem[];
  totalAmount: number;
  itemCount: number;
  addItem: (item: Omit<CartItem, 'quantity'>, quantity?: number) => void;
  removeItem: (productId: string, variantId: string) => void;
  updateQuantity: (productId: string, variantId: string, quantity: number) => void;
  clearCart: () => void;
}

const CartContext = createContext<CartContextValue | null>(null);

function loadCart(key: string): CartItem[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed;
  } catch (error) {
    console.warn('Cart load failed', error);
    return [];
  }
}

function saveCart(key: string, items: CartItem[]): void {
  if (typeof window === 'undefined') return;
  try {
    localStorage.setItem(key, JSON.stringify(items));
  } catch (error) {
    console.warn('Cart save failed', error);
  }
}

function clearStoredCart(key: string): void {
  if (typeof window === 'undefined') return;
  try {
    localStorage.removeItem(key);
  } catch (error) {
    console.warn('Cart clear failed', error);
  }
}

type LineKey = Pick<CartItem, 'productId' | 'variantId'>;

const sameLine = (a: LineKey, b: LineKey) =>
  a.productId === b.productId && a.variantId === b.variantId;

/** AF-1: the guest cart joins the account cart — the same product+option adds quantities. */
export function mergeCarts(account: CartItem[], guest: CartItem[]): CartItem[] {
  const merged = account.map((item) => ({ ...item }));
  for (const g of guest) {
    const existing = merged.find((m) => sameLine(m, g));
    if (existing) {
      existing.quantity += g.quantity;
    } else {
      merged.push({ ...g });
    }
  }
  return merged;
}

interface CartState {
  /** Whose items these are. `null` until the auth state is known. */
  owner: Owner | null;
  items: CartItem[];
}

export function CartProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading: authLoading } = useAuth();
  const [cart, setCart] = useState<CartState>({ owner: null, items: [] });
  const currentOwner: Owner | null = authLoading ? null : isAuthenticated ? 'account' : 'guest';

  useEffect(() => {
    if (currentOwner === 'account') {
      const guest = loadCart(GUEST_CART_KEY);
      const items = mergeCarts(loadCart(ACCOUNT_CART_KEY), guest);
      if (guest.length > 0) {
        saveCart(ACCOUNT_CART_KEY, items);
        clearStoredCart(GUEST_CART_KEY);
      }
      setCart({ owner: 'account', items });
    } else if (currentOwner === 'guest') {
      clearStoredCart(ACCOUNT_CART_KEY);
      setCart({ owner: 'guest', items: loadCart(GUEST_CART_KEY) });
    }
  }, [currentOwner]);

  // 🔴 Persist only when the items belong to the CURRENT owner. On the render where
  // auth flips (logout), `cart.items` are still the account's while `currentOwner` is
  // already 'guest' — writing them under the guest key would hand them to the next
  // visitor. The owner check skips that render; the load effect then replaces them.
  useEffect(() => {
    if (cart.owner !== null && cart.owner === currentOwner) {
      saveCart(keyOf(cart.owner), cart.items);
    }
  }, [cart, currentOwner]);

  const addItem = useCallback((item: Omit<CartItem, 'quantity'>, quantity = 1) => {
    setCart((prev) => {
      const existing = prev.items.find((i) => sameLine(i, item));
      const items = existing
        ? prev.items.map((i) => (sameLine(i, item) ? { ...i, quantity: i.quantity + quantity } : i))
        : [...prev.items, { ...item, quantity }];
      return { ...prev, items };
    });
  }, []);

  const removeItem = useCallback((productId: string, variantId: string) => {
    setCart((prev) => ({
      ...prev,
      items: prev.items.filter((i) => !sameLine(i, { productId, variantId })),
    }));
  }, []);

  const updateQuantity = useCallback(
    (productId: string, variantId: string, quantity: number) => {
      setCart((prev) => ({
        ...prev,
        items:
          quantity <= 0
            ? prev.items.filter((i) => !sameLine(i, { productId, variantId }))
            : prev.items.map((i) =>
                sameLine(i, { productId, variantId }) ? { ...i, quantity } : i,
              ),
      }));
    },
    [],
  );

  const clearCart = useCallback(() => {
    setCart((prev) => ({ ...prev, items: [] }));
    if (currentOwner !== null) clearStoredCart(keyOf(currentOwner));
  }, [currentOwner]);

  // Same rule as persisting: never show items that belong to someone else.
  const visibleItems = useMemo(
    () => (cart.owner !== null && cart.owner === currentOwner ? cart.items : []),
    [cart, currentOwner],
  );
  const totalAmount = calculateTotal(visibleItems);
  const itemCount = calculateItemCount(visibleItems);

  const value = useMemo(
    () => ({ items: visibleItems, totalAmount, itemCount, addItem, removeItem, updateQuantity, clearCart }),
    [visibleItems, totalAmount, itemCount, addItem, removeItem, updateQuantity, clearCart],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
}
