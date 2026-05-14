import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { ChevronLeft, Save, AlertCircle, History as HistoryIcon, Pencil, Eye } from "lucide-react";
import { useCompanyCapabilities } from "../../hooks/useCompanyRole";
import {
  adminProductsApi,
  type AdminProduct,
  type AdminProductWritePayload,
} from "../../api/catalog";
import ProductHistoryTimeline from "../../components/product/ProductHistoryTimeline";
import {
  productFormSchema,
  productFormDefaults,
  type ProductFormValues,
} from "../../schemas/product";
import type { RootState } from "../../stores";

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };
  return { fadeInUp };
};

/**
 * Converts an ISO/Instant string from the API into the local-naive format that
 * `<input type="datetime-local">` understands. Returns "" when the input is null.
 *
 * <p>The HTML control speaks "YYYY-MM-DDTHH:mm" with no timezone — we slice the
 * locale-formatted Date to get there. Round-tripping through {@code Date} normalises
 * to the user's local zone, which is the right thing for vendor admins setting a
 * launch time in their own tz.
 */
function toLocalInputValue(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  );
}

function buildPayload(values: ProductFormValues): AdminProductWritePayload {
  const blank = (s: string) => (s === "" ? null : s);
  const numOrNull = (s: string) => (s === "" ? null : Number(s));
  const intOrNull = (s: string) => (s === "" ? null : Number(s));
  return {
    name: values.name,
    description: blank(values.description),
    sku: blank(values.sku),
    price: Number(values.price),
    compareAtPrice: numOrNull(values.compareAtPrice),
    currency: values.currency || "USD",
    category: blank(values.category),
    brand: blank(values.brand),
    tags: blank(values.tags),
    thumbnailUrl: blank(values.thumbnailUrl),
    stock: intOrNull(values.stock),
    status: values.status,
    scheduledPublishAt:
      values.status === "SCHEDULED" && values.scheduledPublishAt
        ? new Date(values.scheduledPublishAt).toISOString()
        : null,
    featured: values.featured,
    purchasable: values.purchasable,
    listed: values.listed,
    boostWeight: intOrNull(values.boostWeight),
    pinnedUntil: values.pinnedUntil
      ? new Date(values.pinnedUntil).toISOString()
      : null,
    pinnedRank: values.pinnedUntil ? intOrNull(values.pinnedRank) : null,
  };
}

function productToFormValues(p: AdminProduct): ProductFormValues {
  return {
    name: p.name,
    description: p.description ?? "",
    sku: p.sku ?? "",
    price: String(p.price),
    compareAtPrice: p.compareAtPrice == null ? "" : String(p.compareAtPrice),
    currency: p.currency || "USD",
    category: p.category ?? "",
    brand: p.brand ?? "",
    tags: p.tags ?? "",
    thumbnailUrl: p.thumbnailUrl ?? "",
    stock: p.stock == null ? "" : String(p.stock),
    status: p.status,
    scheduledPublishAt: toLocalInputValue(p.scheduledPublishAt),
    featured: p.featured,
    purchasable: p.purchasable,
    listed: p.listed,
    boostWeight: p.boostWeight == null ? "" : String(p.boostWeight),
    pinnedUntil: toLocalInputValue(p.pinnedUntil),
    pinnedRank: p.pinnedRank == null ? "" : String(p.pinnedRank),
  };
}

interface FieldProps {
  label: string;
  hint?: string;
  error?: string;
  children: React.ReactNode;
}

function Field({ label, hint, error, children }: FieldProps) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs uppercase tracking-[0.2em] font-semibold text-white/65">{label}</span>
      {children}
      {hint && !error && <span className="text-xs text-white/45">{hint}</span>}
      {error && (
        <span className="inline-flex items-center gap-1 text-xs text-red-400">
          <AlertCircle className="w-3 h-3" />
          {error}
        </span>
      )}
    </label>
  );
}

const inputBase =
  "w-full rounded-xl border border-white/15 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-sky-400/60 focus:bg-white/[0.06] transition-colors";

export default function AdminProductEditor() {
  const navigate = useNavigate();
  const { id } = useParams<{ id?: string }>();
  const isEdit = id !== undefined && id !== "new";
  const productId = isEdit ? Number(id) : null;
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp } = useAnims();
  const [tab, setTab] = useState<"edit" | "history">("edit");
  const { can } = useCompanyCapabilities(companyId);
  const canEdit = can("MANAGE_PRODUCTS");

  const queryKey = useMemo(
    () => ["admin-products", "detail", { companyId, productId }],
    [companyId, productId]
  );

  const { data: existing, isLoading: isLoadingExisting } = useQuery({
    queryKey,
    queryFn: () => adminProductsApi.get(companyId!, productId!).then(r => r.data),
    enabled: !!companyId && isEdit && productId !== null,
  });

  const form = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: productFormDefaults,
    mode: "onBlur",
  });

  // Hydrate the form once existing product data lands. Reset is the canonical RHF
  // way to swap defaults after async load — preferred over `setValue` per field.
  useEffect(() => {
    if (existing) {
      form.reset(productToFormValues(existing));
    }
  }, [existing, form]);

  const status = form.watch("status");

  const createMutation = useMutation({
    mutationFn: (payload: AdminProductWritePayload) => adminProductsApi.create(companyId!, payload).then(r => r.data),
    onSuccess: created => {
      queryClient.invalidateQueries({ queryKey: ["admin-products", "list"] });
      navigate(`/admin/products/${created.id}`, { replace: true });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (payload: AdminProductWritePayload) =>
      adminProductsApi.update(companyId!, productId!, payload).then(r => r.data),
    onSuccess: updated => {
      queryClient.invalidateQueries({ queryKey: ["admin-products", "list"] });
      queryClient.setQueryData(queryKey, updated);
    },
  });

  const onSubmit = form.handleSubmit(values => {
    const payload = buildPayload(values);
    if (isEdit) updateMutation.mutate(payload);
    else createMutation.mutate(payload);
  });

  if (!companyId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <p className="text-white/60 text-sm">Sign in with a vendor account to manage products.</p>
      </div>
    );
  }

  if (isEdit && isLoadingExisting) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="w-8 h-8 rounded-full border-2 border-sky-400/40 border-t-sky-400 animate-spin" />
      </div>
    );
  }

  const submitting = createMutation.isPending || updateMutation.isPending;
  const submitError =
    (createMutation.error as Error | null)?.message ?? (updateMutation.error as Error | null)?.message ?? null;
  const errors = form.formState.errors;

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
      </div>

      <div className="relative z-10 max-w-3xl mx-auto px-4 sm:px-6 py-10">
        <button
          type="button"
          onClick={() => navigate("/admin/products")}
          className="inline-flex items-center gap-1.5 text-sm text-white/60 hover:text-white/90 transition-colors mb-6"
        >
          <ChevronLeft className="w-4 h-4" />
          Back to products
        </button>

        <motion.header
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-6"
        >
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">
            {isEdit ? "Edit product" : "New product"}
          </p>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white">
            {isEdit ? existing?.name ?? "Loading…" : "Add a new product"}
          </h1>
        </motion.header>

        {isEdit && (
          <div className="mb-6 inline-flex items-center gap-1 rounded-full border border-white/10 bg-white/[0.04] backdrop-blur p-1">
            <button
              type="button"
              onClick={() => setTab("edit")}
              className={`inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                tab === "edit" ? "bg-white/10 text-white" : "text-white/65 hover:text-white"
              }`}
            >
              <Pencil className="w-3.5 h-3.5" />
              Edit
            </button>
            <button
              type="button"
              onClick={() => setTab("history")}
              className={`inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                tab === "history" ? "bg-white/10 text-white" : "text-white/65 hover:text-white"
              }`}
            >
              <HistoryIcon className="w-3.5 h-3.5" />
              History
            </button>
          </div>
        )}

        {isEdit && tab === "history" && productId !== null && (
          <ProductHistoryTimeline
            companyId={companyId}
            productId={productId}
            onReverted={() => {
              queryClient.invalidateQueries({ queryKey });
            }}
          />
        )}

        {(!isEdit || tab === "edit") && (
        <motion.form
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          onSubmit={onSubmit}
          className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6 sm:p-8 space-y-6"
        >
          <Field label="Name" error={errors.name?.message}>
            <input type="text" className={inputBase} placeholder="Black Friday Bundle" {...form.register("name")} />
          </Field>

          <Field label="Description" error={errors.description?.message}>
            <textarea
              rows={4}
              className={inputBase}
              placeholder="Optional"
              {...form.register("description")}
            />
          </Field>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <Field label="SKU" error={errors.sku?.message}>
              <input type="text" className={inputBase} placeholder="BLK-FRI-001" {...form.register("sku")} />
            </Field>
            <Field label="Currency" error={errors.currency?.message}>
              <input type="text" className={inputBase} maxLength={3} {...form.register("currency")} />
            </Field>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
            <Field label="Price" error={errors.price?.message}>
              <input
                type="number"
                step="0.01"
                min="0"
                className={inputBase}
                {...form.register("price")}
              />
            </Field>
            <Field
              label="Compare-at"
              hint="Original price; rendered as strikethrough next to the live price."
              error={errors.compareAtPrice?.message as string | undefined}
            >
              <input
                type="number"
                step="0.01"
                min="0"
                className={inputBase}
                {...form.register("compareAtPrice")}
              />
            </Field>
            <Field label="Stock" hint="Leave blank for untracked inventory." error={errors.stock?.message as string | undefined}>
              <input
                type="number"
                step="1"
                min="0"
                className={inputBase}
                {...form.register("stock")}
              />
            </Field>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <Field label="Category" error={errors.category?.message}>
              <input type="text" className={inputBase} placeholder="Electronics" {...form.register("category")} />
            </Field>
            <Field label="Brand" error={errors.brand?.message}>
              <input type="text" className={inputBase} placeholder="Acme" {...form.register("brand")} />
            </Field>
          </div>

          <Field label="Tags" hint="Comma or space separated." error={errors.tags?.message}>
            <input type="text" className={inputBase} placeholder="sale, holiday" {...form.register("tags")} />
          </Field>

          <Field label="Thumbnail URL" error={errors.thumbnailUrl?.message}>
            <input
              type="url"
              className={inputBase}
              placeholder="https://…"
              {...form.register("thumbnailUrl")}
            />
          </Field>

          {/* Lifecycle */}
          <div className="rounded-xl border border-white/10 bg-white/[0.04] p-5 space-y-5">
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">Lifecycle</p>

            <Field label="Status" error={errors.status?.message}>
              <select className={inputBase} {...form.register("status")}>
                <option value="DRAFT">Draft — invisible to customers</option>
                <option value="SCHEDULED">Scheduled — auto-publish at a future time</option>
                <option value="ACTIVE">Active — live in the catalogue</option>
                <option value="ARCHIVED">Archived — hidden, not deleted</option>
              </select>
            </Field>

            {status === "SCHEDULED" && (
              <Field
                label="Publish at"
                hint="The product flips to Active automatically at this time. Use your local timezone."
                error={errors.scheduledPublishAt?.message}
              >
                <input
                  type="datetime-local"
                  className={inputBase}
                  {...form.register("scheduledPublishAt")}
                />
              </Field>
            )}
          </div>

          {/* Visibility flags */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <label className="inline-flex items-center gap-2 text-sm text-white/80">
              <input type="checkbox" {...form.register("listed")} className="accent-sky-400" />
              Listed in catalogue
            </label>
            <label className="inline-flex items-center gap-2 text-sm text-white/80">
              <input type="checkbox" {...form.register("featured")} className="accent-sky-400" />
              Featured
            </label>
            <label className="inline-flex items-center gap-2 text-sm text-white/80">
              <input type="checkbox" {...form.register("purchasable")} className="accent-sky-400" />
              Purchasable
            </label>
          </div>

          {/* Merchandising */}
          <div className="rounded-xl border border-white/10 bg-white/[0.04] p-5 space-y-5">
            <div className="flex items-baseline justify-between">
              <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
                Merchandising
              </p>
              <p className="text-xs text-white/45">
                Boost ranks higher in search; pin forces to the top until expiry.
              </p>
            </div>

            <Field
              label="Boost weight"
              hint="1–10. Multiplies relevance — leave blank for neutral."
              error={errors.boostWeight?.message as string | undefined}
            >
              <input
                type="number"
                step="1"
                min="1"
                max="10"
                className={inputBase}
                placeholder="e.g. 5"
                {...form.register("boostWeight")}
              />
            </Field>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <Field
                label="Pin until"
                hint="Local time. Empty = not pinned."
                error={errors.pinnedUntil?.message as string | undefined}
              >
                <input
                  type="datetime-local"
                  className={inputBase}
                  {...form.register("pinnedUntil")}
                />
              </Field>
              <Field
                label="Pin order"
                hint="Lower wins among pinned products. Optional."
                error={errors.pinnedRank?.message as string | undefined}
              >
                <input
                  type="number"
                  step="1"
                  min="0"
                  className={inputBase}
                  placeholder="0"
                  {...form.register("pinnedRank")}
                />
              </Field>
            </div>
          </div>

          {submitError && (
            <p className="inline-flex items-center gap-2 text-sm text-red-400">
              <AlertCircle className="w-4 h-4" />
              {submitError}
            </p>
          )}

          <div className="flex items-center justify-end gap-3 pt-2 border-t border-white/10">
            <button
              type="button"
              onClick={() => navigate("/admin/products")}
              className="px-4 py-2 rounded-full border border-white/20 text-sm font-semibold text-white/80 hover:bg-white/10 transition-colors"
            >
              {canEdit ? "Cancel" : "Back"}
            </button>
            {canEdit ? (
              <button
                type="submit"
                disabled={submitting}
                className="inline-flex items-center gap-2 px-5 py-2 rounded-full bg-blue-600 hover:bg-blue-500 text-sm font-semibold text-white transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <Save className="w-4 h-4" />
                {submitting ? "Saving…" : isEdit ? "Save changes" : "Create product"}
              </button>
            ) : (
              <span className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-white/20 text-xs font-semibold text-white/65">
                <Eye className="w-3.5 h-3.5" />
                Read-only
              </span>
            )}
          </div>
        </motion.form>
        )}
      </div>
    </div>
  );
}
