import { z } from 'zod';

export const addEvidenceSchema = z.object({
  evidenceType: z.enum([
    'ORDER_DETAILS',
    'TRACKING',
    'DELIVERY_CONFIRMATION',
    'CUSTOMER_COMMUNICATION',
    'OTHER',
  ]),
  content: z
    .string()
    .trim()
    .min(1, 'Evidence content is required')
    .max(8000, 'Evidence content must not exceed 8000 characters'),
  // HTTPS only, matching the server-side @Pattern on AddEvidenceRequest — the value is
  // rendered back as an <a href>, so a javascript: URL must never reach the page.
  attachmentUrl: z
    .string()
    .trim()
    .max(500, 'Attachment URL must not exceed 500 characters')
    .url('Enter a valid URL')
    .startsWith('https://', 'Attachment URL must use HTTPS')
    .optional()
    .or(z.literal('')),
});

export type AddEvidenceFormValues = z.infer<typeof addEvidenceSchema>;
