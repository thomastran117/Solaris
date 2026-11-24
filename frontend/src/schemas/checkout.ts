import { z } from "zod";

/**
 * Shipping address. Limits mirror CreateOrderRequest's ship* fields and
 * CreateCustomerAddressRequest.
 */
export const shippingAddressSchema = z.object({
  recipientName: z
    .string()
    .trim()
    .min(1, "Enter the recipient's name")
    .max(150, "Use 150 characters or fewer"),
  street: z
    .string()
    .trim()
    .min(1, "Enter a street address")
    .max(255, "Use 255 characters or fewer"),
  street2: z.string().trim().max(255, "Use 255 characters or fewer"),
  city: z
    .string()
    .trim()
    .min(1, "Enter a city")
    .max(100, "Use 100 characters or fewer"),
  state: z
    .string()
    .trim()
    .min(1, "Enter a state or province")
    .max(100, "Use 100 characters or fewer"),
  postalCode: z
    .string()
    .trim()
    .min(1, "Enter a postal code")
    .max(20, "Use 20 characters or fewer"),
  country: z
    .string()
    .trim()
    .regex(/^[A-Za-z]{2}$/, "Use a 2-letter country code, e.g. US"),
  phoneNumber: z.string().trim().max(30, "Use 30 characters or fewer"),
});
export type ShippingAddressValues = z.infer<typeof shippingAddressSchema>;

export const NEW_ADDRESS = "new";

export const emptyAddress: ShippingAddressValues = {
  recipientName: "",
  street: "",
  street2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "US",
  phoneNumber: "",
};

/**
 * Checkout details. Delivery needs a saved address or a complete new one;
 * pickup needs a location. Fields that don't apply to the chosen method are
 * ignored, so switching methods never blocks submission.
 */
export const checkoutSchema = z
  .object({
    fulfillmentMethod: z.enum(["DELIVERY", "PICKUP"]),
    /** A saved address id, or NEW_ADDRESS to use `address`. */
    addressId: z.string(),
    address: z.object({
      recipientName: z.string(),
      street: z.string(),
      street2: z.string(),
      city: z.string(),
      state: z.string(),
      postalCode: z.string(),
      country: z.string(),
      phoneNumber: z.string(),
    }),
    saveAddress: z.boolean(),
    pickupLocationId: z.string(),
    couponCode: z.string().trim().max(50, "Use 50 characters or fewer"),
  })
  .superRefine((values, ctx) => {
    if (values.fulfillmentMethod === "PICKUP") {
      if (!values.pickupLocationId) {
        ctx.addIssue({
          code: "custom",
          path: ["pickupLocationId"],
          message: "Choose a pickup location",
        });
      }
      return;
    }
    if (values.addressId && values.addressId !== NEW_ADDRESS) return;
    const parsed = shippingAddressSchema.safeParse(values.address);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        ctx.addIssue({
          code: "custom",
          path: ["address", ...issue.path],
          message: issue.message,
        });
      }
    }
  });
export type CheckoutValues = z.infer<typeof checkoutSchema>;
