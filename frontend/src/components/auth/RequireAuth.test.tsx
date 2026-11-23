import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import RequireAuth from "./RequireAuth";
import { makeStore } from "../../stores";
import { sessionRestoreSettled, setCredentials } from "../../stores/authSlice";
import { mockRouter, setMockUrl } from "../../test/nextNavigation";

const token = `h.${btoa(JSON.stringify({ exp: 4_102_444_800 }))}.s`;

function renderGuard(store = makeStore()) {
  render(
    <Provider store={store}>
      <RequireAuth>
        <p>secret order</p>
      </RequireAuth>
    </Provider>
  );
  return store;
}

describe("RequireAuth", () => {
  it("shows a spinner and does not redirect while the session restore is pending", () => {
    setMockUrl("/orders/42");
    renderGuard();
    expect(
      screen.getByRole("status", { name: "Loading your session" })
    ).toBeInTheDocument();
    expect(screen.queryByText("secret order")).not.toBeInTheDocument();
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });

  it("redirects anonymous visitors to /login once the restore settled", () => {
    const store = makeStore();
    store.dispatch(sessionRestoreSettled());
    renderGuard(store);
    expect(screen.queryByText("secret order")).not.toBeInTheDocument();
    expect(mockRouter.replace).toHaveBeenCalledWith("/login");
  });

  it("keeps waiting while a token exists but the restore (company lookup) is unfinished", () => {
    const store = makeStore();
    store.dispatch(setCredentials({ accessToken: token }));
    renderGuard(store);
    expect(screen.queryByText("secret order")).not.toBeInTheDocument();
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });

  it("renders children for a signed-in user after the restore settled", () => {
    const store = makeStore();
    store.dispatch(setCredentials({ accessToken: token }));
    store.dispatch(sessionRestoreSettled());
    renderGuard(store);
    expect(screen.getByText("secret order")).toBeInTheDocument();
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });
});
