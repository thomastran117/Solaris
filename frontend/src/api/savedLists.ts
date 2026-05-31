import api from "../api";
import type {
  AddSavedListItemRequest,
  CreateSavedListRequest,
  PublicSavedList,
  SavedList,
  SavedListItem,
  SavedListSummary,
  SavedListType,
  UpdateSavedListItemRequest,
  UpdateSavedListRequest,
} from "../types/savedList";

export const savedListsApi = {
  list: (type?: SavedListType) =>
    api.get<SavedListSummary[]>("/saved-lists", {
      params: type ? { type } : undefined,
    }),

  get: (id: string) => api.get<SavedList>(`/saved-lists/${id}`),

  getPublic: (slug: string) =>
    api.get<PublicSavedList>(`/saved-lists/public/${slug}`),

  create: (req: CreateSavedListRequest) =>
    api.post<SavedList>("/saved-lists", req),

  update: (id: string, req: UpdateSavedListRequest) =>
    api.put<SavedList>(`/saved-lists/${id}`, req),

  remove: (id: string) => api.delete<void>(`/saved-lists/${id}`),

  addItem: (id: string, req: AddSavedListItemRequest) =>
    api.post<SavedListItem>(`/saved-lists/${id}/items`, req),

  updateItem: (id: string, itemId: string, req: UpdateSavedListItemRequest) =>
    api.patch<SavedListItem>(`/saved-lists/${id}/items/${itemId}`, req),

  removeItem: (id: string, itemId: string) =>
    api.delete<void>(`/saved-lists/${id}/items/${itemId}`),
};
