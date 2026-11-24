"use client";

import { useEffect } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { markHydrated } from "../hooks/useHydrated";
import { getQueryClient } from "../api/queryClient";
import AuthInitializer from "../components/auth/AuthInitializer";
import StoreProvider from "./StoreProvider";
import { ThemeProvider } from "../components/theme/ThemeProvider";
import { Toaster } from "../components/ui/Toaster";
import { CartDrawerProvider } from "../components/cart/CartDrawer";
import { CartPersistence } from "../components/cart/CartPersistence";

export default function Providers({
  defaultMarketplaceId,
  children,
}: {
  defaultMarketplaceId: string | null;
  children: React.ReactNode;
}) {
  const queryClient = getQueryClient();
  // Root effects run after the whole initial tree has hydrated; from then on newly
  // mounted content may play entrance animations (see hooks/useHydrated).
  useEffect(() => {
    markHydrated();
  }, []);
  return (
    <StoreProvider defaultMarketplaceId={defaultMarketplaceId}>
      <QueryClientProvider client={queryClient}>
        <AuthInitializer>
          <ThemeProvider>
            <CartDrawerProvider>
              {children}
              <CartPersistence />
            </CartDrawerProvider>
            <Toaster />
          </ThemeProvider>
        </AuthInitializer>
      </QueryClientProvider>
    </StoreProvider>
  );
}
