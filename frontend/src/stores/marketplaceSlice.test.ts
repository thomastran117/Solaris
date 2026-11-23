import { describe, expect, it } from "vitest";
import { makeStore } from "./index";
import {
  clearMarketplace,
  marketplaceStateWithDefault,
  selectActiveMarketplaceId,
  setCurrentMarketplace,
  type MarketplaceProfile,
} from "./marketplaceSlice";

const profile = { id: "mp-selected" } as MarketplaceProfile;

describe("selectActiveMarketplaceId", () => {
  it("falls back to the configured default", () => {
    const store = makeStore({
      marketplace: marketplaceStateWithDefault("mp-default"),
    });
    expect(selectActiveMarketplaceId(store.getState())).toBe("mp-default");
  });

  it("prefers an explicit selection and returns to the default when cleared", () => {
    const store = makeStore({
      marketplace: marketplaceStateWithDefault("mp-default"),
    });
    store.dispatch(setCurrentMarketplace(profile));
    expect(selectActiveMarketplaceId(store.getState())).toBe("mp-selected");
    store.dispatch(clearMarketplace());
    expect(selectActiveMarketplaceId(store.getState())).toBe("mp-default");
  });

  it("is null when nothing is configured", () => {
    expect(selectActiveMarketplaceId(makeStore().getState())).toBeNull();
  });
});
