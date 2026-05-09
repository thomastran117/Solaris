import { useState, type FormEvent } from "react";
import Modal from "../ui/Modal";
import { useCreateSavedList } from "../../hooks/useSavedLists";
import type { SavedList, SavedListType } from "../../types/savedList";
import { ALL_TYPES, LIST_TYPE_META } from "./listTypeMeta";

interface Props {
  open: boolean;
  onClose: () => void;
  /** Optional: callback once a list is created. Useful for chaining (e.g. add product to it). */
  onCreated?: (list: SavedList) => void;
  defaultType?: SavedListType;
}

export default function CreateListModal({
  open,
  onClose,
  onCreated,
  defaultType = "WISHLIST",
}: Props) {
  const [name, setName] = useState("");
  const [type, setType] = useState<SavedListType>(defaultType);
  const [description, setDescription] = useState("");
  const [isPublic, setIsPublic] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = useCreateSavedList();

  function reset() {
    setName("");
    setType(defaultType);
    setDescription("");
    setIsPublic(false);
    setError(null);
  }

  function handleClose() {
    if (create.isPending) return;
    reset();
    onClose();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Please give your list a name.");
      return;
    }
    if (trimmed.length > 100) {
      setError("Name must be 100 characters or fewer.");
      return;
    }
    try {
      const list = await create.mutateAsync({
        name: trimmed,
        type,
        description: description.trim() || undefined,
        isPublic,
      });
      reset();
      onCreated?.(list);
      onClose();
    } catch (err: unknown) {
      const message = extractErrorMessage(err) ?? "Could not create the list. Please try again.";
      setError(message);
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Create a new list" maxWidth="max-w-md">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-xs uppercase tracking-wider text-white/60 mb-1.5">
            Name
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={100}
            autoFocus
            placeholder="e.g. Gaming PC Build"
            className="w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25"
          />
        </div>

        <div>
          <label className="block text-xs uppercase tracking-wider text-white/60 mb-1.5">
            Type
          </label>
          <div className="grid grid-cols-2 gap-2">
            {ALL_TYPES.map((t) => {
              const meta = LIST_TYPE_META[t];
              const Icon = meta.Icon;
              const active = t === type;
              return (
                <button
                  type="button"
                  key={t}
                  onClick={() => setType(t)}
                  className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-sm transition-colors ${
                    active
                      ? "border-blue-400/60 bg-blue-500/15 text-white"
                      : "border-white/10 bg-white/[0.04] text-white/70 hover:bg-white/[0.06]"
                  }`}
                >
                  <Icon className="w-4 h-4 text-sky-200" />
                  {meta.label}
                </button>
              );
            })}
          </div>
        </div>

        <div>
          <label className="block text-xs uppercase tracking-wider text-white/60 mb-1.5">
            Description <span className="text-white/40 normal-case tracking-normal">(optional)</span>
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
            rows={3}
            placeholder="A short description for this list…"
            className="w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25 resize-none"
          />
        </div>

        <label className="flex items-center gap-2 text-sm text-white/80">
          <input
            type="checkbox"
            checked={isPublic}
            onChange={(e) => setIsPublic(e.target.checked)}
            className="h-4 w-4 rounded border-white/20 bg-white/[0.06] accent-blue-500"
          />
          Make this list public (anyone with the link can view)
        </label>

        {error && (
          <p className="text-sm text-red-400">{error}</p>
        )}

        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            onClick={handleClose}
            disabled={create.isPending}
            className="rounded-full border border-white/15 px-4 py-2 text-sm text-white/80 hover:bg-white/10 transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={create.isPending}
            className="rounded-full bg-blue-600 hover:bg-blue-500 px-5 py-2 text-sm font-semibold text-white transition-colors disabled:opacity-50"
          >
            {create.isPending ? "Creating…" : "Create list"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function extractErrorMessage(err: unknown): string | null {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { response?: { data?: { detail?: string; message?: string } } };
    return maybe.response?.data?.detail ?? maybe.response?.data?.message ?? null;
  }
  return null;
}
