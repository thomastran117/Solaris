"use client";

import { useEffect } from "react";
import { useAppStore } from "../../stores/hooks";
import { hydrateCart } from "../../stores/cartSlice";
import {
  CART_STORAGE_KEY,
  readStoredCart,
  writeStoredCart,
} from "../../stores/cartPersistence";

/**
 * Keeps the cart in localStorage. Storage is read after mount (never during
 * render, so server and client markup match), written whenever the lines
 * change, and re-read when another tab changes it.
 */
export function CartPersistence() {
  const store = useAppStore();

  useEffect(() => {
    store.dispatch(hydrateCart(readStoredCart()));
    let last = store.getState().cart.lines;

    const unsubscribe = store.subscribe(() => {
      const lines = store.getState().cart.lines;
      if (lines !== last) {
        last = lines;
        writeStoredCart(lines);
      }
    });

    const onStorage = (event: StorageEvent) => {
      if (event.key !== CART_STORAGE_KEY && event.key !== null) return;
      store.dispatch(hydrateCart(readStoredCart()));
      last = store.getState().cart.lines;
    };
    window.addEventListener("storage", onStorage);

    return () => {
      unsubscribe();
      window.removeEventListener("storage", onStorage);
    };
  }, [store]);

  return null;
}
