import { afterEach, describe, expect, it, vi } from "vitest";
import { buildOrderRequest, singleCompanyId, toQuoteItems } from "./checkout";
import {
  CHECKOUT_DRAFT_TTL_MS,
  extractStepUpToken,
  loadCheckoutDraft,
  saveCheckoutDraft,
} from "./checkoutDraft";
import { NEW_ADDRESS, checkoutSchema, emptyAddress } from "../schemas/checkout";
import type { CartLine } from "../stores/cartSlice";

const lines: CartLine[] = [
  {
    kind: "product",
    lineId: "p:p1:v1",
    productId: "p1",
    variantId: "v1",
    variantLabel: null,
    name: "A",
    thumbnailUrl: null,
    price: 10,
    currency: "USD",
    quantity: 2,
    marketplaceId: "mp",
    companyId: "c1",
  },
  {
    kind: "kit",
    lineId: "k:k1:x",
    kitId: "k1",
    selections: [
      { slotId: "s", productId: "p9", variantId: null, quantity: 1 },
    ],
    summary: "",
    name: "Kit",
    thumbnailUrl: null,
    price: 99,
    currency: "USD",
    quantity: 1,
    marketplaceId: "mp",
    companyId: "c2",
  },
];

const address = {
  recipientName: " Sam Shopper ",
  street: "1 Main St",
  street2: "",
  city: "Springfield",
  state: "IL",
  postalCode: "62701",
  country: "us",
  phoneNumber: "",
};

describe("buildOrderRequest", () => {
  it("maps products and kits and a new delivery address", () => {
    const request = buildOrderRequest({
      lines,
      currency: "USD",
      addresses: [],
      values: {
        fulfillmentMethod: "DELIVERY",
        addressId: NEW_ADDRESS,
        address,
        saveAddress: false,
        pickupLocationId: "",
        couponCode: "  SAVE10 ",
      },
    });
    expect(request).toEqual({
      items: [
        { productId: "p1", variantId: "v1", quantity: 2 },
        {
          kitId: "k1",
          kitSelections: [
            { slotId: "s", productId: "p9", variantId: null, quantity: 1 },
          ],
          quantity: 1,
        },
      ],
      currency: "USD",
      fulfillmentMethod: "DELIVERY",
      couponCode: "SAVE10",
      shipRecipientName: "Sam Shopper",
      shipStreet: "1 Main St",
      shipCity: "Springfield",
      shipState: "IL",
      shipPostalCode: "62701",
      shipCountry: "US",
    });
  });

  it("uses a saved address and omits address fields for pickup", () => {
    const saved = {
      id: "a1",
      label: "Home",
      recipientName: "Saved Name",
      street: "9 Elm",
      street2: null,
      city: "Town",
      state: "CA",
      postalCode: "90001",
      country: "US",
      phoneNumber: "555",
    };
    const delivery = buildOrderRequest({
      lines,
      currency: "USD",
      addresses: [saved],
      riskVerificationToken: "tok",
      values: {
        fulfillmentMethod: "DELIVERY",
        addressId: "a1",
        address: emptyAddress,
        saveAddress: false,
        pickupLocationId: "",
        couponCode: "",
      },
    });
    expect(delivery).toMatchObject({
      shipRecipientName: "Saved Name",
      shipPhoneNumber: "555",
      riskVerificationToken: "tok",
    });
    expect(delivery.couponCode).toBeUndefined();

    const pickup = buildOrderRequest({
      lines,
      currency: "USD",
      addresses: [saved],
      values: {
        fulfillmentMethod: "PICKUP",
        addressId: "a1",
        address,
        saveAddress: false,
        pickupLocationId: "loc-1",
        couponCode: "",
      },
    });
    expect(pickup.pickupLocationId).toBe("loc-1");
    expect(pickup.shipStreet).toBeUndefined();
  });

  it("quotes only product lines and offers pickup only for one store", () => {
    expect(toQuoteItems(lines)).toEqual([
      { productId: "p1", variantId: "v1", quantity: 2 },
    ]);
    expect(singleCompanyId(lines)).toBeNull();
    expect(singleCompanyId([lines[0]])).toBe("c1");
  });
});

describe("checkoutSchema", () => {
  const base = {
    fulfillmentMethod: "DELIVERY" as const,
    addressId: NEW_ADDRESS,
    address: emptyAddress,
    saveAddress: true,
    pickupLocationId: "",
    couponCode: "",
  };

  it("requires a complete new address for delivery", () => {
    const result = checkoutSchema.safeParse(base);
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((i) => i.path.join("."))).toEqual(
      expect.arrayContaining([
        "address.recipientName",
        "address.street",
        "address.city",
      ])
    );
    expect(checkoutSchema.safeParse({ ...base, address }).success).toBe(true);
  });

  it("skips address validation for a saved address or pickup", () => {
    expect(checkoutSchema.safeParse({ ...base, addressId: "a1" }).success).toBe(
      true
    );
    expect(
      checkoutSchema.safeParse({ ...base, fulfillmentMethod: "PICKUP" }).success
    ).toBe(false);
    expect(
      checkoutSchema.safeParse({
        ...base,
        fulfillmentMethod: "PICKUP",
        pickupLocationId: "loc",
      }).success
    ).toBe(true);
  });
});

describe("checkout draft", () => {
  afterEach(() => {
    vi.useRealTimers();
    window.localStorage.clear();
  });

  it("restores a recent draft and expires old ones", () => {
    const values = checkoutSchema.parse({
      fulfillmentMethod: "PICKUP",
      addressId: NEW_ADDRESS,
      address: emptyAddress,
      saveAddress: false,
      pickupLocationId: "loc",
      couponCode: "",
    });
    saveCheckoutDraft({ currency: "USD", values });
    expect(loadCheckoutDraft()?.values).toEqual(values);
    expect(
      loadCheckoutDraft(Date.now() + CHECKOUT_DRAFT_TTL_MS + 1)
    ).toBeNull();
    expect(window.localStorage.length).toBe(0);
  });

  it("extracts the token from a pasted link or a bare token", () => {
    expect(
      extractStepUpToken(
        "https://shop.example/checkout?stepUpToken=abc-123-def"
      )
    ).toBe("abc-123-def");
    expect(extractStepUpToken(" 3f2c9a1e-0000-4c1d-9a1e-1234567890ab ")).toBe(
      "3f2c9a1e-0000-4c1d-9a1e-1234567890ab"
    );
    expect(extractStepUpToken("hello world")).toBeNull();
    expect(extractStepUpToken("https://shop.example/checkout")).toBeNull();
  });
});
