"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { getQueryClient } from "../api/queryClient";
import AuthInitializer from "../components/auth/AuthInitializer";
import StoreProvider from "./StoreProvider";

export default function Providers({
  defaultMarketplaceId,
  children,
}: {
  defaultMarketplaceId: string | null;
  children: React.ReactNode;
}) {
  const queryClient = getQueryClient();
  return (
    <StoreProvider defaultMarketplaceId={defaultMarketplaceId}>
      <QueryClientProvider client={queryClient}>
        <AuthInitializer>{children}</AuthInitializer>
      </QueryClientProvider>
    </StoreProvider>
  );
}
