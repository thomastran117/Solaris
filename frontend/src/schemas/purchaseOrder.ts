import { z } from 'zod';

export const createSupplierSchema = z.object({
  name: z.string().min(1, 'Name is required').max(255),
  email: z.string().email('Invalid email').max(255).optional().or(z.literal('')),
  phone: z.string().max(30).optional(),
  address: z.string().max(500).optional(),
  notes: z.string().max(2000).optional(),
});

export type CreateSupplierFormValues = z.infer<typeof createSupplierSchema>;

const poLineItemSchema = z.object({
  productId: z.string().uuid('Invalid product ID'),
  orderedQty: z.number().int().min(1, 'Quantity must be at least 1'),
  unitCostCents: z.number().int().min(0, 'Cost cannot be negative'),
});

export const createPOSchema = z.object({
  supplierId: z.string().uuid('Select a supplier'),
  expectedArrivalDate: z.string().optional(),
  notes: z.string().max(2000).optional(),
  restockRequestId: z.string().uuid().optional().or(z.literal('')),
  items: z.array(poLineItemSchema).min(1, 'At least one line item is required'),
});

export type CreatePOFormValues = z.infer<typeof createPOSchema>;

export const receiveItemsSchema = z.object({
  items: z.array(z.object({
    itemId: z.string().uuid(),
    receivedQty: z.number().int().min(1, 'Quantity must be at least 1'),
  })).min(1),
});

export type ReceiveItemsFormValues = z.infer<typeof receiveItemsSchema>;
