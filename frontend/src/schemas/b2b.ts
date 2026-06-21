import { z } from 'zod';

const paymentTermsSchema = z.enum(['IMMEDIATE', 'NET_30', 'NET_60']);

export const createQuoteSchema = z.object({
  companyName: z.string().min(1, 'Company name is required').max(255),
  taxId: z.string().max(100).optional(),
  billingAddress: z.string().max(500).optional(),
  message: z.string().max(2000).optional(),
  paymentTerms: paymentTermsSchema,
  items: z
    .array(
      z.object({
        productId: z.string().uuid('Select a product'),
        variantId: z.string().uuid().optional().or(z.literal('')),
        quantity: z.number().int().min(1, 'Quantity must be at least 1'),
      }),
    )
    .min(1, 'Add at least one line item'),
});

export type CreateQuoteFormValues = z.infer<typeof createQuoteSchema>;

export const respondQuoteSchema = z
  .object({
    action: z.enum(['APPROVE', 'COUNTER']),
    vendorNote: z.string().max(2000).optional(),
    paymentTerms: paymentTermsSchema.optional(),
    items: z
      .array(
        z.object({
          productId: z.string().uuid(),
          variantId: z.string().uuid().optional().or(z.literal('')),
          quantity: z.number().int().min(1),
          unitPriceCents: z.number().int().min(0, 'Price must be zero or more'),
        }),
      )
      .optional(),
  })
  .refine((v) => v.action !== 'COUNTER' || (v.items && v.items.length > 0), {
    message: 'Add at least one revised line when countering',
    path: ['items'],
  });

export type RespondQuoteFormValues = z.infer<typeof respondQuoteSchema>;
