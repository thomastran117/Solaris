import { z } from "zod";

/**
 * Form schema for the admin collection editor. Mirrors the backend
 * {@code CreateCollectionRequest} / {@code UpdateCollectionRequest} but stores list-style
 * rule fields as comma-separated strings so the UI can keep raw text input rather than
 * forcing a tag-input widget for v1.
 *
 * <p>The cross-field rule "DYNAMIC requires at least one rule list" is mirrored on the
 * backend in {@code DynamicCollectionRules.parse} — the inline error here just gives faster
 * feedback before the network round-trip.
 */
export const collectionFormSchema = z
  .object({
    name: z.string().min(1, "Name is required").max(200, "Name must be 200 characters or fewer"),
    slug: z
      .string()
      .min(1, "Slug is required")
      .max(200, "Slug must be 200 characters or fewer")
      .regex(/^[a-z0-9][a-z0-9-]*$/, "Slug must be lowercase letters, digits, or hyphens"),
    description: z.string().max(10_000, "Description must be 10,000 characters or fewer"),
    imageUrl: z.string().max(500, "Image URL must be 500 characters or fewer"),
    type: z.enum(["STATIC", "DYNAMIC"]),
    status: z.enum(["DRAFT", "ACTIVE", "ARCHIVED"]),
    featured: z.boolean(),
    featuredRank: z
      .string()
      .refine(s => s === "" || /^\d+$/.test(s.trim()), "Featured rank must be a whole number"),
    /** Comma-separated. Empty when not applicable. */
    tagsAnyOf: z.string(),
    categoriesAnyOf: z.string(),
    brandsAnyOf: z.string(),
  })
  .superRefine((value, ctx) => {
    if (value.type === "DYNAMIC") {
      const lists = [value.tagsAnyOf, value.categoriesAnyOf, value.brandsAnyOf];
      const anyPopulated = lists.some(s => s.split(",").some(token => token.trim() !== ""));
      if (!anyPopulated) {
        ctx.addIssue({
          code: "custom",
          path: ["tagsAnyOf"],
          message: "Dynamic collections need at least one tag, category, or brand rule",
        });
      }
    }
  });

export type CollectionFormValues = z.infer<typeof collectionFormSchema>;

export const collectionFormDefaults: CollectionFormValues = {
  name: "",
  slug: "",
  description: "",
  imageUrl: "",
  type: "STATIC",
  status: "DRAFT",
  featured: false,
  featuredRank: "",
  tagsAnyOf: "",
  categoriesAnyOf: "",
  brandsAnyOf: "",
};

/**
 * Converts the comma-separated form fields into the JSON body the backend expects.
 * Returns null for STATIC collections so the rules column gets cleared server-side.
 */
export function buildRulesJson(values: CollectionFormValues): string | null {
  if (values.type !== "DYNAMIC") return null;
  const split = (s: string) =>
    s
      .split(",")
      .map(t => t.trim().toLowerCase())
      .filter(t => t.length > 0);
  return JSON.stringify({
    tagsAnyOf: split(values.tagsAnyOf),
    categoriesAnyOf: split(values.categoriesAnyOf),
    brandsAnyOf: split(values.brandsAnyOf),
  });
}

/** Reverse of buildRulesJson — used to hydrate the form when editing. */
export function parseRulesJson(json: string | null | undefined): {
  tagsAnyOf: string;
  categoriesAnyOf: string;
  brandsAnyOf: string;
} {
  const empty = { tagsAnyOf: "", categoriesAnyOf: "", brandsAnyOf: "" };
  if (!json) return empty;
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    const join = (v: unknown) =>
      Array.isArray(v) ? v.map(x => String(x)).join(", ") : "";
    return {
      tagsAnyOf: join(parsed.tagsAnyOf),
      categoriesAnyOf: join(parsed.categoriesAnyOf),
      brandsAnyOf: join(parsed.brandsAnyOf),
    };
  } catch {
    return empty;
  }
}
