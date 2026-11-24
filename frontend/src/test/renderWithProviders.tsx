import type { ReactElement } from "react";
import { render, type RenderResult } from "@testing-library/react";
import { Provider } from "react-redux";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { makeStore, type AppStore, type RootState } from "../stores";
import { ThemeProvider } from "../components/theme/ThemeProvider";
import { CartDrawerProvider } from "../components/cart/CartDrawer";

type AuthOverrides = Partial<RootState["auth"]>;

interface Options {
  /** Merged into the default (signed-out, session restored) auth state. */
  auth?: AuthOverrides;
  store?: AppStore;
  queryClient?: QueryClient;
}

export function signedIn(overrides: AuthOverrides = {}): AuthOverrides {
  return {
    accessToken: "token",
    email: "sam@example.test",
    role: "USER",
    companyId: null,
    tier: "FREE",
    ...overrides,
  };
}

/**
 * Renders with the app's client providers: a fresh Redux store, a TanStack
 * Query client with retries off, the theme provider and the cart drawer.
 */
export function renderWithProviders(
  ui: ReactElement,
  { auth, store, queryClient }: Options = {}
): RenderResult & { store: AppStore; queryClient: QueryClient } {
  const base = makeStore().getState().auth;
  const appStore =
    store ?? makeStore({ auth: { ...base, sessionRestored: true, ...auth } });
  const client =
    queryClient ??
    new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = render(
    <Provider store={appStore}>
      <QueryClientProvider client={client}>
        <ThemeProvider>
          <CartDrawerProvider>{ui}</CartDrawerProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>
  );
  return { ...result, store: appStore, queryClient: client };
}
