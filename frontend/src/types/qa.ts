export type QAStatus = "VISIBLE" | "PENDING_MODERATION" | "HIDDEN";
export type QAReportType = "QUESTION" | "ANSWER";
export type QAModerationAction = "APPROVE" | "REJECT";

export interface Answer {
  id: string;
  questionId: string;
  answeredById: string;
  answererFirstName: string;
  answererLastName: string;
  answerText: string;
  vendorAnswer: boolean;
  upvoteCount: number;
  status: QAStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Question {
  id: string;
  productId: string;
  askedById: string;
  askerFirstName: string;
  askerLastName: string;
  questionText: string;
  status: QAStatus;
  answers: Answer[];
  createdAt: string;
  updatedAt: string;
}
