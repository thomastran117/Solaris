/** `CustomerAddressResponse` from /addresses. */
export interface CustomerAddress {
  id: string;
  label: string;
  recipientName: string;
  street: string;
  street2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  phoneNumber: string | null;
  /** Lombok's `isDefault` getter serialises as `default`; older payloads may say `isDefault`. */
  default?: boolean;
  isDefault?: boolean;
}

/** `CreateCustomerAddressRequest`. */
export interface CustomerAddressInput {
  label: string;
  recipientName: string;
  street: string;
  street2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  phoneNumber?: string;
  default?: boolean;
}

export function isDefaultAddress(address: CustomerAddress): boolean {
  return !!(address.default ?? address.isDefault);
}
