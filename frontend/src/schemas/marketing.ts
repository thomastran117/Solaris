import { z } from 'zod';

export const createWorkflowSchema = z.object({
  name: z.string().min(1, 'Name is required').max(255),
  trigger: z.enum(['ORDER_DELIVERED', 'DAYS_SINCE_LAST_ORDER', 'CUSTOMER_BIRTHDAY', 'FIRST_ORDER_PLACED']),
  delayHours: z.number().int().min(0),
  targetSegmentId: z.string().uuid().optional().or(z.literal('')),
  actionType: z.enum(['EMAIL', 'PUSH']),
  emailSubject: z.string().max(255).optional(),
  emailBody: z.string().optional(),
  cooldownDays: z.number().int().min(0),
});

export type CreateWorkflowFormValues = z.infer<typeof createWorkflowSchema>;
