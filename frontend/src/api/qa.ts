import api from "../api";
import type { Answer, QAModerationAction, QAReportType, Question } from "../types/qa";

interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface QuestionListParams {
  page?: number;
  size?: number;
}

export const qaApi = {
  listQuestions: (productId: string, params: QuestionListParams = {}) =>
    api.get<PagedResponse<Question>>(`/products/${productId}/questions`, { params }),

  askQuestion: (productId: string, questionText: string) =>
    api.post<Question>(`/products/${productId}/questions`, { questionText }),

  submitAnswer: (questionId: string, answerText: string) =>
    api.post<Answer>(`/questions/${questionId}/answers`, { answerText }),

  upvoteAnswer: (questionId: string, answerId: string) =>
    api.post<void>(`/questions/${questionId}/answers/${answerId}/upvote`),

  reportQuestion: (questionId: string, reason: string) =>
    api.post<void>(`/questions/${questionId}/report`, { reason }),

  reportAnswer: (answerId: string, reason: string) =>
    api.post<void>(`/answers/${answerId}/report`, { reason }),
};

export const qaAdminApi = {
  moderate: (type: QAReportType, targetId: string, action: QAModerationAction) =>
    api.patch<void>(`/admin/qa/${type}/${targetId}/moderate`, { action }),
};
