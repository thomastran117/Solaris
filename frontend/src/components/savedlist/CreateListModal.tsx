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
      const message =
        extractErrorMessage(err) ??
        "Could not create the list. Please try again.";
      setError(message);
    }
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Create a new list"
      maxWidth="max-w-md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-xs text-muted-foreground mb-1.5">
            Name
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={100}
            autoFocus
            placeholder="e.g. Gaming PC Build"
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring"
          />
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1.5">
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
                      ? "border-primary/40 bg-primary/10 text-foreground"
                      : "border-border bg-surface text-muted-foreground hover:bg-muted"
                  }`}
                >
                  <Icon className="w-4 h-4 text-link" />
                  {meta.label}
                </button>
              );
            })}
          </div>
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1.5">
            Description{" "}
            <span className="text-muted-foreground normal-case tracking-normal">
              (optional)
            </span>
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
            rows={3}
            placeholder="A short description for this list…"
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring resize-none"
          />
        </div>

        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="checkbox"
            checked={isPublic}
            onChange={(e) => setIsPublic(e.target.checked)}
            className="h-4 w-4 rounded border-input bg-surface accent-primary"
          />
          Make this list public (anyone with the link can view)
        </label>

        {error && <p className="text-sm text-danger">{error}</p>}

        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            onClick={handleClose}
            disabled={create.isPending}
            className="rounded-full border border-border px-4 py-2 text-sm text-foreground hover:bg-muted transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={create.isPending}
            className="rounded-full bg-primary hover:bg-primary-hover px-5 py-2 text-sm font-semibold text-primary-foreground transition-colors disabled:opacity-50"
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
    const maybe = err as {
      response?: { data?: { detail?: string; message?: string } };
    };
    return (
      maybe.response?.data?.detail ?? maybe.response?.data?.message ?? null
    );
  }
  return null;
}
