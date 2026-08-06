export type DisputeStatus = 'OPEN' | 'UNDER_REVIEW' | 'CLOSED';

export type DisputeOutcome = 'PENDING' | 'WON' | 'LOST' | 'ACCEPTED';

export type DisputeEvidenceType =
  | 'ORDER_DETAILS'
  | 'TRACKING'
  | 'DELIVERY_CONFIRMATION'
  | 'CUSTOMER_COMMUNICATION'
  | 'OTHER';

export interface DisputeCase {
  id: string;
  /** Null when the disputed charge could not be mapped to an order. */
  orderId: string | null;
  stripeDisputeId: string;
  stripeChargeId: string | null;
  amountCents: number;
  currency: string;
  reason: string | null;
  status: DisputeStatus;
  outcome: DisputeOutcome;
  stripeStatus: string | null;
  evidenceDeadline: string | null;
  closedAt: string | null;
  evidenceCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface DisputeEvidence {
  id: string;
  evidenceType: DisputeEvidenceType;
  content: string;
  attachmentUrl: string | null;
  /** Null for entries generated automatically when the case was opened. */
  createdById: string | null;
  createdAt: string;
}

export interface DisputeCaseDetail {
  dispute: DisputeCase;
  evidence: DisputeEvidence[];
}

export interface AddEvidencePayload {
  evidenceType: DisputeEvidenceType;
  content: string;
  attachmentUrl?: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
