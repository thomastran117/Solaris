import { afterEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import LoginPage from "../LoginPage";
import InviteAcceptPage from "../InviteAcceptPage";
import { makeStore } from "../../stores";
import { sessionRestoreSettled, setCredentials } from "../../stores/authSlice";
import {
  mockRouter,
  setMockParams,
  setMockUrl,
} from "../../test/nextNavigation";

vi.mock("../../api/team", () => ({
  teamApi: {
    previewInvite: vi.fn(() => new Promise(() => {})),
    acceptInvite: vi.fn(),
  },
}));

const token = `h.${btoa(JSON.stringify({ exp: 4_102_444_800 }))}.s`;

function signedIn() {
  const store = makeStore();
  store.dispatch(setCredentials({ accessToken: token }));
  store.dispatch(sessionRestoreSettled());
  return store;
}

function anonymous() {
  const store = makeStore();
  store.dispatch(sessionRestoreSettled());
  return store;
}

function renderWith(ui: React.ReactNode, store = makeStore()) {
  render(
    <Provider store={store}>
      <QueryClientProvider client={new QueryClient()}>{ui}</QueryClientProvider>
    </Provider>
  );
}

afterEach(() => {
  window.history.replaceState({}, "", "/");
});

describe("LoginPage return-to-next", () => {
  it("returns a restored session to a safe ?next= path (e.g. after a Stripe redirect)", async () => {
    window.history.replaceState(
      {},
      "",
      "/login?next=%2Faccount%3Fupgrade%3Dsuccess"
    );
    renderWith(<LoginPage />, signedIn());
    await waitFor(() =>
      expect(mockRouter.replace).toHaveBeenCalledWith(
        "/account?upgrade=success"
      )
    );
  });

  it("falls back to the dashboard for an unsafe next", async () => {
    window.history.replaceState({}, "", "/login?next=%2F%2Fevil.test");
    renderWith(<LoginPage />, signedIn());
    await waitFor(() =>
      expect(mockRouter.replace).toHaveBeenCalledWith("/dashboard")
    );
    expect(mockRouter.replace).not.toHaveBeenCalledWith("//evil.test");
  });

  it("does not bounce while the session restore is still pending or for anonymous visitors", () => {
    window.history.replaceState({}, "", "/login?next=%2Forders");
    renderWith(<LoginPage />, makeStore());
    renderWith(<LoginPage />, anonymous());
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });
});

describe("InviteAcceptPage", () => {
  it("waits for the session restore before redirecting", () => {
    setMockUrl("/invite/accept/tok-1", { params: { token: "tok-1" } });
    renderWith(<InviteAcceptPage />, makeStore());
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });

  it("sends an anonymous visitor to login with the invite as next", async () => {
    setMockUrl("/invite/accept/tok-1");
    setMockParams({ token: "tok-1" });
    renderWith(<InviteAcceptPage />, anonymous());
    await waitFor(() =>
      expect(mockRouter.replace).toHaveBeenCalledWith(
        "/login?next=%2Finvite%2Faccept%2Ftok-1"
      )
    );
  });

  it("keeps a signed-in visitor on the invite", () => {
    setMockUrl("/invite/accept/tok-1", { params: { token: "tok-1" } });
    renderWith(<InviteAcceptPage />, signedIn());
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });
});
