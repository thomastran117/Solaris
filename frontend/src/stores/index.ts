import { combineReducers, configureStore } from "@reduxjs/toolkit";
import authReducer from "./authSlice";
import vendorReducer from "./vendorSlice";
import marketplaceReducer from "./marketplaceSlice";
import loyaltyReducer from "./loyaltySlice";
import cartReducer from "./cartSlice";

const rootReducer = combineReducers({
  auth: authReducer,
  vendor: vendorReducer,
  marketplace: marketplaceReducer,
  loyalty: loyaltyReducer,
  cart: cartReducer,
});

export type RootState = ReturnType<typeof rootReducer>;

/**
 * Creates a Redux store. Next.js renders client components on the server too, so a
 * module-level store would be shared between requests; `StoreProvider` creates one
 * store per browser session (and per server render) instead.
 */
export function makeStore(preloadedState?: Partial<RootState>) {
  return configureStore({ reducer: rootReducer, preloadedState });
}

export type AppStore = ReturnType<typeof makeStore>;
export type AppDispatch = AppStore["dispatch"];
