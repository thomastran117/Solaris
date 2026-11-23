"use client";

import { useState } from "react";
import { Provider } from "react-redux";
import { bindApiStore } from "../api/authBridge";
import { makeStore, type AppStore } from "../stores";
import { marketplaceStateWithDefault } from "../stores/marketplaceSlice";

function createBrowserAwareStore(
  defaultMarketplaceId: string | null
): AppStore {
  const store = makeStore({
    marketplace: marketplaceStateWithDefault(defaultMarketplaceId),
  });
  // Bind during the first render, not in an effect: child effects (session restore,
  // queries) run before a parent effect would, and they need the bound store.
  if (typeof window !== "undefined") bindApiStore(store);
  return store;
}

export default function StoreProvider({
  defaultMarketplaceId,
  children,
}: {
  defaultMarketplaceId: string | null;
  children: React.ReactNode;
}) {
  const [store] = useState(() => createBrowserAwareStore(defaultMarketplaceId));
  return <Provider store={store}>{children}</Provider>;
}
