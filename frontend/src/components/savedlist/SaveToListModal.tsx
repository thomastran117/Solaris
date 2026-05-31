import { useMemo, useState } from "react";
import { Plus, Check } from "lucide-react";
import Modal from "../ui/Modal";
import {
  useAddSavedListItem,
  useSavedLists,
} from "../../hooks/useSavedLists";
import { savedListsApi } from "../../api/savedLists";
import { useQueries, useQueryClient } from "@tanstack/react-query";
import CreateListModal from "./CreateListModal";
import { LIST_TYPE_META } from "./listTypeMeta";
import type { SavedList } from "../../types/savedList";

interface Props {
  open: boolean;
  onClose: () => void;
  productId: string;
  productName: string;
}

export default function SaveToListModal({ open, onClose, productId, productName }: Props) {
  const [createOpen, setCreateOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lists = useSavedLists(undefined, open);

  // Pull each list's items so we can show "already-in" state. Cheap because
  // the user typically has only a handful of lists.
  const queryClient = useQueryClient();
  const detailQueries = useQueries({
    queries: (lists.data ?? []).map((l) => ({
      queryKey: ["savedLists", "detail", l.id] as const,
      queryFn: () => savedListsApi.get(l.id).then((r) => r.data),
      enabled: open,
    })),
  });

  const containsProduct = useMemo(() => {
    const map = new Map<string, boolean>();
    detailQueries.forEach((q, i) => {
      const list = lists.data?.[i];
      if (!list || !q.data) return;
      const has = q.data.items.some(
        (it) => it.productId === productId && it.variantId === null
      );
      map.set(list.id, has);
    });
    return map;
  }, [detailQueries, lists.data, productId]);

  return (
    <>
      <Modal
        open={open && !createOpen}
        onClose={onClose}
        title="Save to list"
        maxWidth="max-w-md"
      >
        <p className="text-sm text-white/70 mb-4 line-clamp-2">
          Save <span className="text-white font-medium">{productName}</span> to one of
          your lists.
        </p>

        {lists.isLoading && (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <div
                key={i}
                className="h-12 rounded-xl border border-white/10 bg-white/[0.04] animate-pulse"
              />
            ))}
          </div>
        )}

        {!lists.isLoading && (lists.data?.length ?? 0) === 0 && (
          <div className="rounded-xl border border-dashed border-white/15 bg-white/[0.02] p-5 text-center mb-3">
            <p className="text-sm text-white/70 mb-3">
              You don't have any lists yet.
            </p>
            <button
              type="button"
              onClick={() => setCreateOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-full bg-blue-600 hover:bg-blue-500 px-4 py-2 text-sm font-semibold text-white transition-colors"
            >
              <Plus className="w-4 h-4" />
              Create your first list
            </button>
          </div>
        )}

        {!lists.isLoading && (lists.data?.length ?? 0) > 0 && (
          <ul className="space-y-2 mb-3">
            {lists.data!.map((l) => {
              const meta = LIST_TYPE_META[l.type];
              const Icon = meta.Icon;
              const already = containsProduct.get(l.id) ?? false;
              return (
                <li key={l.id}>
                  <SaveTargetRow
                    listId={l.id}
                    name={l.name}
                    typeLabel={meta.label}
                    Icon={Icon}
                    already={already}
                    productId={productId}
                    onError={setError}
                    onSuccess={() => {
                      queryClient.invalidateQueries({
                        queryKey: ["savedLists", "detail", l.id],
                      });
                    }}
                  />
                </li>
              );
            })}
          </ul>
        )}

        {error && (
          <p className="text-sm text-red-400 mb-3">{error}</p>
        )}

        {(lists.data?.length ?? 0) > 0 && (
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className="inline-flex items-center gap-1.5 text-sm text-sky-300 hover:text-sky-200 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Create new list
          </button>
        )}
      </Modal>

      <CreateListModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(list: SavedList) => {
          // After creating a fresh list, immediately add the product to it.
          savedListsApi
            .addItem(list.id, { productId })
            .then(() => {
              queryClient.invalidateQueries({
                queryKey: ["savedLists", "detail", list.id],
              });
              queryClient.invalidateQueries({ queryKey: ["savedLists", "list"] });
            })
            .catch((err: unknown) => {
              setError(extractErrorMessage(err) ?? "Could not add to the new list.");
            });
        }}
      />
    </>
  );
}

function SaveTargetRow({
  listId,
  name,
  typeLabel,
  Icon,
  already,
  productId,
  onError,
  onSuccess,
}: {
  listId: string;
  name: string;
  typeLabel: string;
  Icon: React.ComponentType<{ className?: string }>;
  already: boolean;
  productId: string;
  onError: (msg: string) => void;
  onSuccess: () => void;
}) {
  const add = useAddSavedListItem(listId);
  const disabled = already || add.isPending;

  function handleClick() {
    if (disabled) return;
    add
      .mutateAsync({ productId })
      .then(() => onSuccess())
      .catch((err: unknown) => {
        onError(extractErrorMessage(err) ?? "Could not save to this list.");
      });
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={disabled}
      className={`w-full flex items-center gap-3 rounded-xl border px-3 py-2.5 text-left transition-colors ${
        already
          ? "border-green-400/30 bg-green-500/10 cursor-default"
          : "border-white/10 bg-white/[0.04] hover:bg-white/[0.08]"
      }`}
    >
      <div className="bg-blue-500/15 border border-white/10 rounded-lg p-1.5 flex-shrink-0">
        <Icon className="w-4 h-4 text-sky-200" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-white truncate">{name}</p>
        <p className="text-xs text-white/50">{typeLabel}</p>
      </div>
      {already ? (
        <span className="inline-flex items-center gap-1 text-xs text-green-300">
          <Check className="w-3.5 h-3.5" />
          Saved
        </span>
      ) : (
        <span className="text-xs text-sky-300">
          {add.isPending ? "Saving…" : "Save"}
        </span>
      )}
    </button>
  );
}

function extractErrorMessage(err: unknown): string | null {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { response?: { data?: { detail?: string; message?: string } } };
    return maybe.response?.data?.detail ?? maybe.response?.data?.message ?? null;
  }
  return null;
}
