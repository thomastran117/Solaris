import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { makeStore } from "../../stores";
import { hydrateCart, type CartLine } from "../../stores/cartSlice";
import { mockRouter, setMockUrl } from "../../test/nextNavigation";
import { renderWithProviders, signedIn } from "../../test/renderWithProviders";
import { saveCheckoutDraft } from "../../lib/checkoutDraft";
import type { CustomerAddress } from "../../types/address";

const { create, get, listAddresses, createAddress, quote } = vi.hoisted(() => ({
  create: vi.fn(),
  get: vi.fn(),
  listAddresses: vi.fn(),
  createAddress: vi.fn(),
  quote: vi.fn(),
}));
vi.mock("../../api/orders", () => ({
  ordersApi: {
    create,
    get,
    getShippingRates: vi.fn(),
    confirmShippingRate: vi.fn(),
  },
}));
vi.mock("../../api/addresses", () => ({
  addressKeys: { all: ["addresses"] },
  addressesApi: { list: listAddresses, create: createAddress },
}));
vi.mock("../../api/pricing", () => ({ pricingApi: { quote } }));
vi.mock("@stripe/stripe-js", () => ({
  loadStripe: vi.fn(() => Promise.resolve(null)),
}));

const CheckoutPage = (await import("../CheckoutPage")).default;

const line: CartLine = {
  kind: "product",
  lineId: "p:p1:-",
  productId: "p1",
  variantId: null,
  variantLabel: null,
  name: "Aurora Headphones",
  thumbnailUrl: null,
  price: 199.99,
  currency: "USD",
  quantity: 1,
  marketplaceId: "mp",
  companyId: "c1",
};

function renderCheckout(lines: CartLine[] = [line]) {
  const base = makeStore().getState();
  const store = makeStore({
    auth: { ...base.auth, ...signedIn(), sessionRestored: true },
  });
  store.dispatch(hydrateCart(lines));
  return renderWithProviders(<CheckoutPage stripePublishableKey={null} />, {
    store,
  });
}

beforeEach(() => {
  create.mockReset();
  get.mockReset();
  createAddress.mockReset().mockResolvedValue({ data: {} });
  listAddresses.mockReset().mockResolvedValue({ data: [] });
  quote.mockReset().mockResolvedValue({
    data: {
      subtotal: 199.99,
      promotionSavings: 0,
      couponSavings: 0,
      appliedCouponCode: null,
      taxSource: "NONE",
      taxAmount: 0,
      finalTotal: 199.99,
      currency: "USD",
      warnings: [],
      lines: [],
      appliedPromotions: [],
    },
  });
  window.sessionStorage.clear();
  window.localStorage.clear();
  setMockUrl("/checkout?currency=USD");
});

async function fillAddress(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText("Full name"), "Sam Shopper");
  await user.type(screen.getByLabelText("Street address"), "1 Main St");
  await user.type(screen.getByLabelText("City"), "Springfield");
  await user.type(screen.getByLabelText("State / province"), "IL");
  await user.type(screen.getByLabelText("Postal code"), "62701");
}

describe("CheckoutPage", () => {
  it("shows the empty state without items", async () => {
    renderCheckout([]);
    expect(await screen.findByText("Your cart is empty")).toBeInTheDocument();
  });

  it("validates the address before creating an order", async () => {
    const user = userEvent.setup();
    renderCheckout();
    await user.click(
      await screen.findByRole("button", { name: "Continue to payment" })
    );
    expect(
      await screen.findByText("Enter the recipient's name")
    ).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
  });

  it("creates the order, clears those lines and moves to payment", async () => {
    create.mockResolvedValue({
      data: {
        id: "o1",
        status: "RESERVED",
        items: [],
        fulfillmentMethod: "DELIVERY",
      },
    });
    const user = userEvent.setup();
    const { store } = renderCheckout();
    await fillAddress(user);
    await user.click(
      screen.getByRole("button", { name: "Continue to payment" })
    );

    await waitFor(() =>
      expect(mockRouter.replace).toHaveBeenCalledWith("/checkout?order=o1")
    );
    const [body, key] = create.mock.calls[0];
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
    expect(body).toMatchObject({
      currency: "USD",
      fulfillmentMethod: "DELIVERY",
      items: [{ productId: "p1", variantId: null, quantity: 1 }],
      shipRecipientName: "Sam Shopper",
      shipCountry: "US",
    });
    expect(store.getState().cart.lines).toHaveLength(0);
    // "Save this address" is on by default and runs after the order exists.
    await waitFor(() => expect(createAddress).toHaveBeenCalled());
  });

  it("asks for email confirmation on 428 and resubmits with the token", async () => {
    create
      .mockRejectedValueOnce(
        Object.assign(new Error("428"), {
          isAxiosError: true,
          response: { status: 428, data: {} },
        })
      )
      .mockResolvedValueOnce({
        data: {
          id: "o2",
          status: "RESERVED",
          items: [],
          fulfillmentMethod: "DELIVERY",
        },
      });
    const user = userEvent.setup();
    renderCheckout();
    await fillAddress(user);
    await user.click(
      screen.getByRole("button", { name: "Continue to payment" })
    );

    expect(
      await screen.findByText("Confirm your order by email")
    ).toBeInTheDocument();
    await user.type(
      screen.getByLabelText("Or paste the link here"),
      "https://shop.example/checkout?stepUpToken=tok-12345"
    );
    await user.click(screen.getByRole("button", { name: "Confirm order" }));

    await waitFor(() => expect(create).toHaveBeenCalledTimes(2));
    expect(create.mock.calls[1][0]).toMatchObject({
      riskVerificationToken: "tok-12345",
    });
    expect(create.mock.calls[1][1]).not.toBe(create.mock.calls[0][1]);
  });

  describe("resuming from the step-up email", () => {
    const cadLine: CartLine = {
      ...line,
      lineId: "p:p2:-",
      productId: "p2",
      name: "Maple Speaker",
      currency: "CAD",
    };
    const home: CustomerAddress = {
      id: "addr-1",
      label: "Home",
      recipientName: "Sam Shopper",
      street: "1 Main St",
      street2: null,
      city: "Toronto",
      state: "ON",
      postalCode: "M5V 1A1",
      country: "CA",
      phoneNumber: null,
    };
    const draftValues = {
      fulfillmentMethod: "DELIVERY" as const,
      addressId: home.id,
      address: {
        recipientName: "",
        street: "",
        street2: "",
        city: "",
        state: "",
        postalCode: "",
        country: "",
        phoneNumber: "",
      },
      saveAddress: false,
      pickupLocationId: "",
      couponCode: "",
    };
    const created = {
      data: {
        id: "o3",
        status: "RESERVED",
        items: [],
        fulfillmentMethod: "DELIVERY",
      },
    };

    it("resubmits the saved currency with the saved address once addresses load", async () => {
      let loadAddresses!: (value: { data: CustomerAddress[] }) => void;
      listAddresses.mockReturnValue(
        new Promise((resolve) => (loadAddresses = resolve))
      );
      create.mockResolvedValue(created);
      // The first currency group is USD; the draft was for the CAD items.
      saveCheckoutDraft({ currency: "CAD", values: draftValues });
      setMockUrl("/checkout?stepUpToken=tok-777");
      renderCheckout([line, cadLine]);

      await waitFor(() => expect(listAddresses).toHaveBeenCalled());
      // Not before the saved address is known.
      expect(create).not.toHaveBeenCalled();
      loadAddresses({ data: [home] });

      await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
      expect(mockRouter.replace).toHaveBeenCalledWith("/checkout?currency=CAD");
      expect(create.mock.calls[0][0]).toMatchObject({
        currency: "CAD",
        items: [{ productId: "p2" }],
        riskVerificationToken: "tok-777",
        shipRecipientName: "Sam Shopper",
        shipStreet: "1 Main St",
        shipCountry: "CA",
      });
    });

    it("asks for the address instead of sending a blank one when the saved address is gone", async () => {
      listAddresses.mockResolvedValue({ data: [] });
      saveCheckoutDraft({ currency: "USD", values: draftValues });
      setMockUrl("/checkout?stepUpToken=tok-777");
      renderCheckout();

      expect(
        await screen.findByText(/couldn't load the saved address/)
      ).toBeInTheDocument();
      expect(create).not.toHaveBeenCalled();
    });
  });

  it("shows payments as unavailable without a publishable key", async () => {
    setMockUrl("/checkout?order=o1");
    get.mockResolvedValue({
      data: {
        id: "o1",
        status: "RESERVED",
        fulfillmentMethod: "PICKUP",
        items: [],
        totalAmount: 10,
        currency: "USD",
        shippingCostCents: 0,
        paymentClientSecret: "pi_secret",
      },
    });
    renderCheckout();
    expect(
      await screen.findByText("Payments aren't configured")
    ).toBeInTheDocument();
  });

  it("sends a paid order to its order page", async () => {
    setMockUrl("/checkout?order=o1");
    get.mockResolvedValue({ data: { id: "o1", status: "PAID", items: [] } });
    renderCheckout();
    await waitFor(() =>
      expect(mockRouter.replace).toHaveBeenCalledWith("/orders/o1")
    );
  });
});
