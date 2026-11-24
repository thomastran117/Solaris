import api from "../api";
import type { CustomerAddress, CustomerAddressInput } from "../types/address";

export const addressKeys = {
  all: ["addresses"] as const,
};

/** The signed-in customer's saved shipping addresses (`/addresses`). */
export const addressesApi = {
  list: () => api.get<CustomerAddress[]>("/addresses"),
  create: (body: CustomerAddressInput) =>
    api.post<CustomerAddress>("/addresses", body),
};
