import { z } from "zod";

/**
 * Form-side schema for the admin product editor.
 *
 * <p>Numeric fields are kept as <em>strings</em> at the form layer so that:
 * <ul>
 *   <li>The Zod input and output types match (no {@code z.coerce} divergence that breaks
 *       react-hook-form resolver typing).</li>
 *   <li>An empty {@code <input type="number">} stays as the empty string instead of
 *       round-tripping through {@code NaN}.</li>
 *   <li>The submit-time conversion to the wire-shape API payload happens in one place
 *       ({@code buildPayload} in the editor), not scattered across the form.</li>
 * </ul>
 *
 * <p>The cross-field rule "if status === SCHEDULED then scheduledPublishAt must be a
 * future timestamp" is mirrored on the backend in {@code ProductServiceImpl#applyStatusTransition}.
 * Re-stating it here gives the user a fast inline error before they hit submit.
 */
const requiredNumber = (label: string) =>
  z
    .string()
    .min(1, `${label} is required`)
    .refine(s => !Number.isNaN(Number(s)), `${label} must be a number`)
    .refine(s => Number(s) >= 0, `${label} must be 0 or greater`)
    .refine(s => Number(s) <= 99_999_999.99, `${label} is too large`);

const optionalNumber = (label: string) =>
  z
    .string()
    .refine(s => s === "" || !Number.isNaN(Number(s)), `${label} must be a number`)
    .refine(s => s === "" || Number(s) >= 0, `${label} must be 0 or greater`)
    .refine(s => s === "" || Number(s) <= 99_999_999.99, `${label} is too large`);

const optionalInt = (label: string) =>
  z
    .string()
    .refine(s => s === "" || /^\d+$/.test(s.trim()), `${label} must be a whole number`);

export const productFormSchema = z
  .object({
    name: z
      .string()
      .min(1, "Name is required")
      .max(255, "Name must be 255 characters or fewer"),

    description: z.string().max(10_000, "Description must be 10,000 characters or fewer"),
    sku: z.string().max(100, "SKU must be 100 characters or fewer"),

    price: requiredNumber("Price"),
    compareAtPrice: optionalNumber("Compare-at price"),

    currency: z.string().length(3, "Currency must be a 3-letter ISO code"),

    category: z.string().max(100),
    brand: z.string().max(100),
    tags: z.string().max(500),
    thumbnailUrl: z.string().max(500),

    stock: optionalInt("Stock"),

    status: z.enum(["DRAFT", "ACTIVE", "SCHEDULED", "ARCHIVED"]),

    /** Local datetime string from `<input type="datetime-local">`. Empty when not scheduled. */
    scheduledPublishAt: z.string(),

    featured: z.boolean(),
    purchasable: z.boolean(),
    listed: z.boolean(),
    preorderEnabled: z.boolean(),
    /** Local datetime string from `<input type="datetime-local">`. Empty when not set. */
    preorderExpectedDate: z.string(),

    /**
     * Merchandising — pin and boost. Stored as strings on the form (mirrors price/stock)
     * so the empty state is preserved. `buildPayload` converts these to numbers / null
     * before sending to the API.
     */
    boostWeight: z
      .string()
      .refine(s => s === "" || /^\d+$/.test(s.trim()), "Boost weight must be a whole number")
      .refine(
        s => s === "" || (Number(s) >= 1 && Number(s) <= 10),
        "Boost weight must be between 1 and 10"
      ),
    pinnedUntil: z.string(),
    pinnedRank: z
      .string()
      .refine(s => s === "" || /^\d+$/.test(s.trim()), "Pin rank must be a whole number"),
  })
  .superRefine((value, ctx) => {
    if (value.status === "SCHEDULED") {
      if (!value.scheduledPublishAt) {
        ctx.addIssue({
          code: "custom",
          path: ["scheduledPublishAt"],
          message: "Pick a publish date and time",
        });
        return;
      }
      const target = new Date(value.scheduledPublishAt).getTime();
      if (Number.isNaN(target)) {
        ctx.addIssue({
          code: "custom",
          path: ["scheduledPublishAt"],
          message: "Invalid date",
        });
        return;
      }
      if (target <= Date.now()) {
        ctx.addIssue({
          code: "custom",
          path: ["scheduledPublishAt"],
          message: "Publish time must be in the future",
        });
      }
    }
    // Pin rules: pinnedUntil must be in the future when set; pinnedRank without a pin
    // window is meaningless and gets cleared by the backend, so flag it as an error here too.
    if (value.pinnedUntil) {
      const target = new Date(value.pinnedUntil).getTime();
      if (Number.isNaN(target)) {
        ctx.addIssue({
          code: "custom",
          path: ["pinnedUntil"],
          message: "Invalid date",
        });
      } else if (target <= Date.now()) {
        ctx.addIssue({
          code: "custom",
          path: ["pinnedUntil"],
          message: "Pin expiry must be in the future",
        });
      }
    } else if (value.pinnedRank) {
      ctx.addIssue({
        code: "custom",
        path: ["pinnedRank"],
        message: "Set a pin expiry first or clear the rank",
      });
    }
  });

export type ProductFormValues = z.infer<typeof productFormSchema>;

/**
 * Defaults used when constructing the editor for a new product. Status starts as DRAFT
 * to mirror the backend default and avoid accidentally publishing an empty product.
 */
export const productFormDefaults: ProductFormValues = {
  name: "",
  description: "",
  sku: "",
  price: "",
  compareAtPrice: "",
  currency: "USD",
  category: "",
  brand: "",
  tags: "",
  thumbnailUrl: "",
  stock: "",
  status: "DRAFT",
  scheduledPublishAt: "",
  featured: false,
  purchasable: true,
  listed: true,
  preorderEnabled: false,
  preorderExpectedDate: "",
  boostWeight: "",
  pinnedUntil: "",
  pinnedRank: "",
};
