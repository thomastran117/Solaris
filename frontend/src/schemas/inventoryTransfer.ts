import { z } from 'zod';

export const createTransferSchema = z
  .object({
    productId: z.string().uuid('Invalid product ID'),
    fromLocationId: z.string().uuid('Select a source location'),
    toLocationId: z.string().uuid('Select a destination location'),
    quantity: z.number().int().min(1, 'Quantity must be at least 1'),
    notes: z.string().max(500).optional(),
  })
  .refine((v) => v.fromLocationId !== v.toLocationId, {
    message: 'Source and destination must be different',
    path: ['toLocationId'],
  });

export type CreateTransferFormValues = z.infer<typeof createTransferSchema>;
