import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { savedListsApi } from "../api/savedLists";
import type {
  AddSavedListItemRequest,
  CreateSavedListRequest,
  SavedListType,
  UpdateSavedListItemRequest,
  UpdateSavedListRequest,
} from "../types/savedList";

const KEYS = {
  list: (type?: SavedListType) => ["savedLists", "list", { type: type ?? null }] as const,
  detail: (id: string) => ["savedLists", "detail", id] as const,
  public: (slug: string) => ["savedLists", "public", slug] as const,
};

export function useSavedLists(type?: SavedListType, enabled: boolean = true) {
  return useQuery({
    queryKey: KEYS.list(type),
    queryFn: () => savedListsApi.list(type).then((r) => r.data),
    enabled,
  });
}

export function useSavedList(id: string | null) {
  return useQuery({
    queryKey: KEYS.detail(id ?? ""),
    queryFn: () => savedListsApi.get(id as string).then((r) => r.data),
    enabled: id != null && id.length > 0,
  });
}

export function usePublicSavedList(slug: string | undefined) {
  return useQuery({
    queryKey: KEYS.public(slug ?? ""),
    queryFn: () => savedListsApi.getPublic(slug as string).then((r) => r.data),
    enabled: !!slug,
  });
}

function useInvalidateLists() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["savedLists", "list"] });
}

export function useCreateSavedList() {
  const invalidate = useInvalidateLists();
  return useMutation({
    mutationFn: (req: CreateSavedListRequest) => savedListsApi.create(req).then((r) => r.data),
    onSuccess: () => invalidate(),
  });
}

export function useUpdateSavedList(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: UpdateSavedListRequest) =>
      savedListsApi.update(id, req).then((r) => r.data),
    onSuccess: (data) => {
      queryClient.setQueryData(KEYS.detail(id), data);
      queryClient.invalidateQueries({ queryKey: ["savedLists", "list"] });
    },
  });
}

export function useDeleteSavedList() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => savedListsApi.remove(id),
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: KEYS.detail(id) });
      queryClient.invalidateQueries({ queryKey: ["savedLists", "list"] });
    },
  });
}

export function useAddSavedListItem(listId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: AddSavedListItemRequest) =>
      savedListsApi.addItem(listId, req).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(listId) });
      queryClient.invalidateQueries({ queryKey: ["savedLists", "list"] });
    },
  });
}

export function useUpdateSavedListItem(listId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { itemId: string; req: UpdateSavedListItemRequest }) =>
      savedListsApi.updateItem(listId, vars.itemId, vars.req).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(listId) });
      queryClient.invalidateQueries({ queryKey: ["savedLists", "list"] });
    },
  });
}

export function useRemoveSavedListItem(listId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (itemId: string) => savedListsApi.removeItem(listId, itemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.detail(listId) });
      queryClient.invalidateQueries({ queryKey: ["savedLists", "list"] });
    },
  });
}
