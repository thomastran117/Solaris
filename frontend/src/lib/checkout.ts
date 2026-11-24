import type { CartLine } from "../stores/cartSlice";
import type { CustomerAddress } from "../types/address";
import type { CreateOrderItem, CreateOrderRequest } from "../types/order";
import type { PricingQuoteItem } from "../types/pricing";
import { NEW_ADDRESS, type CheckoutValues } from "../schemas/checkout";

export function toOrderItems(lines: CartLine[]): CreateOrderItem[] {
  return lines.map((line) =>
    line.kind === "product"
      ? {
          productId: line.productId,
          variantId: line.variantId,
          quantity: line.quantity,
        }
      : {
          kitId: line.kitId,
          kitSelections: line.selections,
          quantity: line.quantity,
        }
  );
}

/** Pricing quotes cover product lines only; kit lines are shown as estimates. */
export function toQuoteItems(lines: CartLine[]): PricingQuoteItem[] {
  return lines.flatMap((line) =>
    line.kind === "product"
      ? [
          {
            productId: line.productId,
            variantId: line.variantId,
            quantity: line.quantity,
          },
        ]
      : []
  );
}

/** Builds POST /orders from the cart lines for one currency and the form. */
export function buildOrderRequest({
  lines,
  currency,
  values,
  addresses,
  riskVerificationToken,
}: {
  lines: CartLine[];
  currency: string;
  values: CheckoutValues;
  addresses: CustomerAddress[];
  riskVerificationToken?: string | null;
}): CreateOrderRequest {
  const request: CreateOrderRequest = {
    items: toOrderItems(lines),
    currency,
    fulfillmentMethod: values.fulfillmentMethod,
  };
  const coupon = values.couponCode.trim();
  if (coupon) request.couponCode = coupon;
  if (riskVerificationToken)
    request.riskVerificationToken = riskVerificationToken;

  if (values.fulfillmentMethod === "PICKUP") {
    request.pickupLocationId = values.pickupLocationId;
    return request;
  }

  const saved =
    values.addressId && values.addressId !== NEW_ADDRESS
      ? addresses.find((a) => a.id === values.addressId)
      : undefined;
  const a = saved
    ? {
        recipientName: saved.recipientName,
        street: saved.street,
        street2: saved.street2 ?? "",
        city: saved.city,
        state: saved.state,
        postalCode: saved.postalCode,
        country: saved.country,
        phoneNumber: saved.phoneNumber ?? "",
      }
    : values.address;

  request.shipRecipientName = a.recipientName.trim();
  request.shipStreet = a.street.trim();
  if (a.street2.trim()) request.shipStreet2 = a.street2.trim();
  request.shipCity = a.city.trim();
  request.shipState = a.state.trim();
  request.shipPostalCode = a.postalCode.trim();
  request.shipCountry = a.country.trim().toUpperCase();
  if (a.phoneNumber.trim()) request.shipPhoneNumber = a.phoneNumber.trim();
  return request;
}

/** Pickup is only offered when every line comes from the same store. */
export function singleCompanyId(lines: CartLine[]): string | null {
  const ids = new Set(lines.map((l) => l.companyId));
  return ids.size === 1 ? [...ids][0] : null;
}
