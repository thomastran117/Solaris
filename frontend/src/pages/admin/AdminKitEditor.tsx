import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  ChevronLeft, Save, Plus, Trash2, ChevronDown, ChevronRight, Download,
} from "lucide-react";
import { kitsApi, type CreateKitPayload, type KitSlotPayload, type KitSlotChoicePayload } from "../../api/kits";
import { adminCollectionsApi } from "../../api/merchandising";
import type { KitStatus } from "../../types/kit";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";


const inputCls = "w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white placeholder:text-white/40 focus:outline-none focus:border-white/25";
const labelCls = "text-xs font-semibold text-white/60 uppercase tracking-wide mb-1 block";

// ---- Slot & choice form state ----

interface ChoiceForm {
  productId: string;
  variantId: string;
  priceDelta: string;
  defaultChoice: boolean;
  displayOrder: number;
}

interface SlotForm {
  name: string;
  description: string;
  required: boolean;
  minQty: number;
  maxQty: number;
  displayOrder: number;
  choices: ChoiceForm[];
  collapsed: boolean;
}

function emptySlot(order: number): SlotForm {
  return {
    name: "",
    description: "",
    required: true,
    minQty: 1,
    maxQty: 1,
    displayOrder: order,
    choices: [],
    collapsed: false,
  };
}

function emptyChoice(order: number): ChoiceForm {
  return { productId: "", variantId: "", priceDelta: "", defaultChoice: false, displayOrder: order };
}

// ---- Import-from-collection modal ----

interface ImportModalProps {
  companyId: string;
  slotIndex: number;
  onImport: (slotIndex: number, collectionId: string) => void;
  onClose: () => void;
}

function ImportModal({ companyId, slotIndex, onImport, onClose }: ImportModalProps) {
  const { data } = useQuery({
    queryKey: ["collections", companyId, "active"],
    queryFn: () =>
      adminCollectionsApi.list(companyId, { status: "ACTIVE", size: 50 }).then(r => r.data),
  });

  const [selectedCollectionId, setSelectedCollectionId] = useState("");

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-md rounded-3xl border border-white/10 bg-white/[0.08] backdrop-blur p-8"
      >
        <p className="text-lg font-extrabold text-white mb-1">Import from Collection</p>
        <p className="text-xs text-white/55 mb-6">
          All products in the selected collection will be added as choices to this slot.
        </p>

        <label className={labelCls}>Collection</label>
        <select
          value={selectedCollectionId}
          onChange={e => setSelectedCollectionId(e.target.value)}
          className={inputCls + " mb-6"}
        >
          <option value="">— Select a collection —</option>
          {(data?.items ?? []).map(c => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>

        <div className="flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 py-2.5 rounded-full text-sm font-semibold border border-white/15 text-white/70 hover:bg-white/[0.06] transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={() => { if (selectedCollectionId) onImport(slotIndex, selectedCollectionId); }}
            disabled={!selectedCollectionId}
            className="flex-1 py-2.5 rounded-full text-sm font-semibold bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            Import
          </button>
        </div>
      </motion.div>
    </div>
  );
}

// ---- Main editor ----

export default function AdminKitEditor() {
  const navigate = useNavigate();
  const { id: kitId } = useParams<{ id: string }>();
  const isEdit = Boolean(kitId);
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp } = useAnims();

  // Details form state
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [thumbnailUrl, setThumbnailUrl] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [compareAtPrice, setCompareAtPrice] = useState("");
  const [status, setStatus] = useState<KitStatus>("DRAFT");
  const [listed, setListed] = useState(false);
  const [slots, setSlots] = useState<SlotForm[]>([emptySlot(0)]);
  const [activeTab, setActiveTab] = useState<"details" | "slots">("details");
  const [importModal, setImportModal] = useState<{ slotIndex: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Load existing kit in edit mode
  const { data: existingKit } = useQuery({
    queryKey: ["admin-kit", companyId, kitId],
    queryFn: () => kitsApi.get(companyId!, kitId!).then(r => r.data),
    enabled: isEdit && !!companyId && !!kitId,
  });

  useEffect(() => {
    if (!existingKit) return;
    setName(existingKit.name);
    setDescription(existingKit.description ?? "");
    setThumbnailUrl(existingKit.thumbnailUrl ?? "");
    setCurrency(existingKit.currency);
    setCompareAtPrice(existingKit.compareAtPrice != null ? String(existingKit.compareAtPrice) : "");
    setStatus(existingKit.status);
    setListed(existingKit.listed);
    setSlots(
      existingKit.slots.map((s, si) => ({
        name: s.name,
        description: s.description ?? "",
        required: s.required,
        minQty: s.minQty,
        maxQty: s.maxQty,
        displayOrder: s.displayOrder,
        collapsed: false,
        choices: s.choices.map((c, ci) => ({
          productId: c.productId,
          variantId: c.variantId ?? "",
          priceDelta: c.priceDelta != null ? String(c.priceDelta) : "",
          defaultChoice: c.defaultChoice,
          displayOrder: c.displayOrder,
        })),
      }))
    );
  }, [existingKit]);

  function buildPayload(): CreateKitPayload {
    const slotsPayload: KitSlotPayload[] = slots.map((s, si) => ({
      name: s.name,
      description: s.description || null,
      required: s.required,
      minQty: s.minQty,
      maxQty: s.maxQty,
      displayOrder: si,
      choices: s.choices.map((c, ci): KitSlotChoicePayload => ({
        productId: c.productId,
        variantId: c.variantId || null,
        priceDelta: c.priceDelta ? parseFloat(c.priceDelta) : null,
        defaultChoice: c.defaultChoice,
        displayOrder: ci,
      })),
    }));
    return {
      name,
      description: description || null,
      thumbnailUrl: thumbnailUrl || null,
      currency,
      compareAtPrice: compareAtPrice ? parseFloat(compareAtPrice) : null,
      status,
      listed,
      slots: slotsPayload,
    };
  }

  const createMutation = useMutation({
    mutationFn: (payload: CreateKitPayload) => kitsApi.create(companyId!, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-kits", "list"] });
      navigate("/admin/kits");
    },
    onError: (err: unknown) => {
      setError((err as { message?: string }).message ?? "Failed to save kit");
    },
  });

  const updateMutation = useMutation({
    mutationFn: (payload: CreateKitPayload) => kitsApi.update(companyId!, kitId!, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-kits", "list"] });
      queryClient.invalidateQueries({ queryKey: ["admin-kit", companyId, kitId] });
      navigate("/admin/kits");
    },
    onError: (err: unknown) => {
      setError((err as { message?: string }).message ?? "Failed to save kit");
    },
  });

  const importMutation = useMutation({
    mutationFn: ({ collectionId, slotIdx }: { collectionId: string; slotIdx: number }) =>
      kitsApi.importFromCollection(companyId!, kitId!, {
        collectionId,
        slotId: existingKit?.slots[slotIdx]?.id ?? "",
      }).then(r => r.data),
    onSuccess: (updated) => {
      // Refresh editor with new choices from backend
      setSlots(
        updated.slots.map(s => ({
          name: s.name,
          description: s.description ?? "",
          required: s.required,
          minQty: s.minQty,
          maxQty: s.maxQty,
          displayOrder: s.displayOrder,
          collapsed: false,
          choices: s.choices.map(c => ({
            productId: c.productId,
            variantId: c.variantId ?? "",
            priceDelta: c.priceDelta != null ? String(c.priceDelta) : "",
            defaultChoice: c.defaultChoice,
            displayOrder: c.displayOrder,
          })),
        }))
      );
      setImportModal(null);
      queryClient.invalidateQueries({ queryKey: ["admin-kit", companyId, kitId] });
    },
  });

  function handleSubmit() {
    setError(null);
    if (!name.trim()) { setError("Kit name is required"); return; }
    if (slots.length === 0) { setError("At least one slot is required"); return; }
    const payload = buildPayload();
    if (isEdit) updateMutation.mutate(payload);
    else createMutation.mutate(payload);
  }

  const isSaving = createMutation.isPending || updateMutation.isPending;

  // ---- Slot helpers ----

  function addSlot() {
    setSlots(prev => [...prev, emptySlot(prev.length)]);
  }

  function removeSlot(idx: number) {
    setSlots(prev => prev.filter((_, i) => i !== idx));
  }

  function updateSlot(idx: number, patch: Partial<SlotForm>) {
    setSlots(prev => prev.map((s, i) => i === idx ? { ...s, ...patch } : s));
  }

  function addChoice(slotIdx: number) {
    setSlots(prev =>
      prev.map((s, i) =>
        i === slotIdx
          ? { ...s, choices: [...s.choices, emptyChoice(s.choices.length)] }
          : s
      )
    );
  }

  function removeChoice(slotIdx: number, choiceIdx: number) {
    setSlots(prev =>
      prev.map((s, i) =>
        i === slotIdx
          ? { ...s, choices: s.choices.filter((_, ci) => ci !== choiceIdx) }
          : s
      )
    );
  }

  function updateChoice(slotIdx: number, choiceIdx: number, patch: Partial<ChoiceForm>) {
    setSlots(prev =>
      prev.map((s, i) =>
        i === slotIdx
          ? { ...s, choices: s.choices.map((c, ci) => ci === choiceIdx ? { ...c, ...patch } : c) }
          : s
      )
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 pt-20 pb-16 px-4 md:px-6">
      <div className="max-w-3xl mx-auto">
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          {/* Header */}
          <div className="flex items-center gap-3 mb-8">
            <button
              onClick={() => navigate("/admin/kits")}
              className="w-9 h-9 rounded-xl border border-white/10 bg-white/[0.04] flex items-center justify-center text-white/60 hover:text-white hover:bg-white/[0.08] transition-colors"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <div>
              <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">Admin</p>
              <h1 className="text-2xl font-extrabold text-white">{isEdit ? "Edit Kit" : "New Kit"}</h1>
            </div>
          </div>

          {/* Tabs */}
          <div className="flex gap-1 mb-6 bg-white/[0.04] rounded-2xl p-1 border border-white/10 w-fit">
            {(["details", "slots"] as const).map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-4 py-2 rounded-xl text-sm font-semibold transition-colors ${
                  activeTab === tab
                    ? "bg-white/10 text-white"
                    : "text-white/50 hover:text-white/80"
                }`}
              >
                {tab === "details" ? "Details" : "Slots & Choices"}
              </button>
            ))}
          </div>

          {/* Details tab */}
          {activeTab === "details" && (
            <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 flex flex-col gap-5">
              <div>
                <label className={labelCls}>Kit Name *</label>
                <input className={inputCls} placeholder="e.g. Build Your PC" value={name} onChange={e => setName(e.target.value)} />
              </div>
              <div>
                <label className={labelCls}>Description</label>
                <textarea
                  className={inputCls + " resize-none h-24"}
                  placeholder="Describe this kit…"
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className={labelCls}>Thumbnail URL</label>
                  <input className={inputCls} placeholder="https://…" value={thumbnailUrl} onChange={e => setThumbnailUrl(e.target.value)} />
                </div>
                <div>
                  <label className={labelCls}>Currency</label>
                  <input className={inputCls} placeholder="USD" value={currency} onChange={e => setCurrency(e.target.value.toUpperCase())} maxLength={3} />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className={labelCls}>Compare-At Price</label>
                  <input className={inputCls} type="number" step="0.01" min="0" placeholder="0.00" value={compareAtPrice} onChange={e => setCompareAtPrice(e.target.value)} />
                </div>
                <div>
                  <label className={labelCls}>Status</label>
                  <select className={inputCls} value={status} onChange={e => setStatus(e.target.value as KitStatus)}>
                    <option value="DRAFT">Draft</option>
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                    <option value="ARCHIVED">Archived</option>
                  </select>
                </div>
              </div>
              <label className="flex items-center gap-3 cursor-pointer select-none">
                <div
                  onClick={() => setListed(l => !l)}
                  className={`w-10 h-6 rounded-full transition-colors relative ${listed ? "bg-blue-600" : "bg-white/15"}`}
                >
                  <div className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-transform ${listed ? "translate-x-5" : "translate-x-1"}`} />
                </div>
                <span className="text-sm text-white/70">Listed (visible to customers)</span>
              </label>
            </div>
          )}

          {/* Slots & Choices tab */}
          {activeTab === "slots" && (
            <div className="flex flex-col gap-4">
              {slots.map((slot, si) => (
                <div key={si} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur">
                  {/* Slot header */}
                  <div className="flex items-center gap-3 px-5 py-4 border-b border-white/[0.06]">
                    <button
                      onClick={() => updateSlot(si, { collapsed: !slot.collapsed })}
                      className="text-white/40 hover:text-white/70 transition-colors"
                    >
                      {slot.collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                    </button>
                    <input
                      className="flex-1 bg-transparent text-sm font-semibold text-white placeholder:text-white/35 focus:outline-none"
                      placeholder={`Slot ${si + 1} name…`}
                      value={slot.name}
                      onChange={e => updateSlot(si, { name: e.target.value })}
                    />
                    <label className="flex items-center gap-1.5 text-xs text-white/50 cursor-pointer select-none">
                      <input
                        type="checkbox"
                        checked={slot.required}
                        onChange={e => updateSlot(si, { required: e.target.checked })}
                        className="accent-blue-500"
                      />
                      Required
                    </label>
                    <button
                      onClick={() => removeSlot(si)}
                      className="text-white/30 hover:text-red-400 transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  {!slot.collapsed && (
                    <div className="px-5 py-4 flex flex-col gap-4">
                      {/* Slot settings */}
                      <div className="grid grid-cols-3 gap-3">
                        <div>
                          <label className={labelCls}>Description</label>
                          <input className={inputCls} placeholder="Optional…" value={slot.description} onChange={e => updateSlot(si, { description: e.target.value })} />
                        </div>
                        <div>
                          <label className={labelCls}>Min Qty</label>
                          <input className={inputCls} type="number" min="1" max="99" value={slot.minQty} onChange={e => updateSlot(si, { minQty: Math.max(1, parseInt(e.target.value) || 1) })} />
                        </div>
                        <div>
                          <label className={labelCls}>Max Qty</label>
                          <input className={inputCls} type="number" min="1" max="99" value={slot.maxQty} onChange={e => updateSlot(si, { maxQty: Math.max(1, parseInt(e.target.value) || 1) })} />
                        </div>
                      </div>

                      {/* Choices */}
                      {slot.choices.length > 0 && (
                        <div className="flex flex-col gap-2">
                          <p className={labelCls}>Choices</p>
                          {slot.choices.map((choice, ci) => (
                            <div key={ci} className="flex items-center gap-2 rounded-xl border border-white/[0.08] bg-white/[0.03] px-3 py-2.5">
                              <div className="flex-1 grid grid-cols-3 gap-2">
                                <input className={inputCls + " col-span-1"} placeholder="Product ID" value={choice.productId} onChange={e => updateChoice(si, ci, { productId: e.target.value })} />
                                <input className={inputCls} placeholder="Variant ID (opt)" value={choice.variantId} onChange={e => updateChoice(si, ci, { variantId: e.target.value })} />
                                <input className={inputCls} type="number" step="0.01" placeholder="Price Δ" value={choice.priceDelta} onChange={e => updateChoice(si, ci, { priceDelta: e.target.value })} />
                              </div>
                              <label className="flex items-center gap-1 text-xs text-white/50 shrink-0 cursor-pointer select-none">
                                <input
                                  type="checkbox"
                                  checked={choice.defaultChoice}
                                  onChange={e => updateChoice(si, ci, { defaultChoice: e.target.checked })}
                                  className="accent-blue-500"
                                />
                                Default
                              </label>
                              <button onClick={() => removeChoice(si, ci)} className="text-white/30 hover:text-red-400 transition-colors shrink-0">
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          ))}
                        </div>
                      )}

                      <div className="flex gap-2">
                        <button
                          onClick={() => addChoice(si)}
                          className="flex items-center gap-1.5 text-xs text-white/60 hover:text-white border border-white/10 rounded-xl px-3 py-1.5 hover:bg-white/[0.06] transition-colors"
                        >
                          <Plus className="w-3 h-3" />
                          Add Choice
                        </button>
                        {isEdit && existingKit?.slots[si] && (
                          <button
                            onClick={() => setImportModal({ slotIndex: si })}
                            className="flex items-center gap-1.5 text-xs text-sky-200/70 hover:text-sky-200 border border-sky-500/20 rounded-xl px-3 py-1.5 hover:bg-sky-500/10 transition-colors"
                          >
                            <Download className="w-3 h-3" />
                            Import from Collection
                          </button>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              ))}

              <button
                onClick={addSlot}
                className="flex items-center gap-2 text-sm text-white/60 hover:text-white border border-dashed border-white/15 rounded-2xl py-3 px-5 hover:border-white/30 hover:bg-white/[0.04] transition-colors"
              >
                <Plus className="w-4 h-4" />
                Add Slot
              </button>
            </div>
          )}

          {/* Error */}
          {error && (
            <p className="mt-4 text-sm text-red-400/90 text-center">{error}</p>
          )}

          {/* Save */}
          <div className="mt-6 flex justify-end">
            <button
              onClick={handleSubmit}
              disabled={isSaving}
              className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-6 py-2.5 rounded-full disabled:opacity-50 transition-colors"
            >
              <Save className="w-4 h-4" />
              {isSaving ? "Saving…" : isEdit ? "Save Changes" : "Create Kit"}
            </button>
          </div>
        </motion.div>
      </div>

      {importModal && companyId && (
        <ImportModal
          companyId={companyId}
          slotIndex={importModal.slotIndex}
          onImport={(slotIdx, collectionId) => {
            importMutation.mutate({ collectionId, slotIdx });
          }}
          onClose={() => setImportModal(null)}
        />
      )}
    </div>
  );
}
