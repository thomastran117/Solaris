import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import RequireAuth from "./RequireAuth";
import { makeStore } from "../../stores";
import { setCredentials } from "../../stores/authSlice";
import { mockRouter, setMockUrl } from "../../test/nextNavigation";

const token = `h.${btoa(JSON.stringify({ exp: 4_102_444_800 }))}.s`;

describe("RequireAuth", () => {
  it("redirects anonymous visitors to /login and renders nothing", () => {
    setMockUrl("/orders/42");
    render(
      <Provider store={makeStore()}>
        <RequireAuth>
          <p>secret order</p>
        </RequireAuth>
      </Provider>
    );
    expect(screen.queryByText("secret order")).not.toBeInTheDocument();
    expect(mockRouter.replace).toHaveBeenCalledWith("/login");
  });

  it("renders children for a signed-in user", () => {
    const store = makeStore();
    store.dispatch(setCredentials({ accessToken: token }));
    render(
      <Provider store={store}>
        <RequireAuth>
          <p>secret order</p>
        </RequireAuth>
      </Provider>
    );
    expect(screen.getByText("secret order")).toBeInTheDocument();
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });
});
