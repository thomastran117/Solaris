export type WorkflowTrigger =
  | 'ORDER_DELIVERED'
  | 'DAYS_SINCE_LAST_ORDER'
  | 'CUSTOMER_BIRTHDAY'
  | 'FIRST_ORDER_PLACED';

export type WorkflowStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';

export type WorkflowActionType = 'EMAIL' | 'PUSH';

export interface MarketingWorkflow {
  id: string;
  companyId: string;
  name: string;
  trigger: WorkflowTrigger;
  delayHours: number;
  targetSegmentId: string | null;
  actionType: WorkflowActionType;
  emailSubject: string | null;
  emailBody: string | null;
  cooldownDays: number;
  status: WorkflowStatus;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowAnalytics {
  workflowId: string;
  enrolledCount: number;
  sentCount: number;
}

export interface CreateWorkflowPayload {
  name: string;
  trigger: WorkflowTrigger;
  delayHours: number;
  targetSegmentId?: string;
  actionType: WorkflowActionType;
  emailSubject?: string;
  emailBody?: string;
  cooldownDays: number;
}

export interface UpdateWorkflowPayload {
  status?: WorkflowStatus;
  name?: string;
  emailSubject?: string;
  emailBody?: string;
}
