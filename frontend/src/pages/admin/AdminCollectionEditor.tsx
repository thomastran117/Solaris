import { useEffect, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { ChevronLeft, Save, AlertCircle, Sparkles } from "lucide-react";
import { adminCollectionsApi, type CollectionWritePayload } from "../../api/merchandising";
import {
  collectionFormSchema,
  collectionFormDefaults,
  buildRulesJson,
  parseRulesJson,
  type CollectionFormValues,
} from "../../schemas/collection";
import type { Collection } from "../../types/collection";
import type { RootState } from "../../stores";

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };
  return { fadeInUp };
};

function buildPayload(values: CollectionFormValues): CollectionWritePayload {
  const blank = (s: string) => (s === "" ? null : s);
  const intOrNull = (s: string) => (s === "" ? null : Number(s));
  return {
    name: values.name,
    slug: values.slug,
    description: blank(values.description),
    imageUrl: blank(values.imageUrl),
    type: values.type,
    status: values.status,
    featured: values.featured,
    featuredRank: intOrNull(values.featuredRank),
    rulesJson: buildRulesJson(values),
  };
}

function collectionToFormValues(c: Collection): CollectionFormValues {
  const rules = parseRulesJson(c.rulesJson);
  return {
    name: c.name,
    slug: c.slug,
    description: c.description ?? "",
    imageUrl: c.imageUrl ?? "",
    type: c.type,
    status: c.status,
    featured: c.featured,
    featuredRank: c.featuredRank == null ? "" : String(c.featuredRank),
    tagsAnyOf: rules.tagsAnyOf,
    categoriesAnyOf: rules.categoriesAnyOf,
    brandsAnyOf: rules.brandsAnyOf,
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

export default function AdminCollectionEditor() {
  const navigate = useNavigate();
  const { id } = useParams<{ id?: string }>();
  const isEdit = id !== undefined && id !== "new";
  const collectionId = isEdit ? id : null;
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp } = useAnims();

  const queryKey = useMemo(
    () => ["admin-collections", "detail", { companyId, collectionId }],
    [companyId, collectionId]
  );

  const { data: existing, isLoading: isLoadingExisting } = useQuery({
    queryKey,
    queryFn: () => adminCollectionsApi.get(companyId!, collectionId!).then(r => r.data),
    enabled: !!companyId && isEdit && collectionId !== null,
  });

  const form = useForm<CollectionFormValues>({
    resolver: zodResolver(collectionFormSchema),
    defaultValues: collectionFormDefaults,
    mode: "onBlur",
  });

  useEffect(() => {
    if (existing) {
      form.reset(collectionToFormValues(existing));
    }
  }, [existing, form]);

  const type = form.watch("type");

  const createMutation = useMutation({
    mutationFn: (payload: CollectionWritePayload) =>
      adminCollectionsApi.create(companyId!, payload).then(r => r.data),
    onSuccess: created => {
      queryClient.invalidateQueries({ queryKey: ["admin-collections", "list"] });
      navigate(`/admin/collections/${created.id}/products`, { replace: true });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (payload: CollectionWritePayload) =>
      adminCollectionsApi.update(companyId!, collectionId!, payload).then(r => r.data),
    onSuccess: updated => {
      queryClient.invalidateQueries({ queryKey: ["admin-collections", "list"] });
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
        <p className="text-white/60 text-sm">Sign in with a vendor account to manage collections.</p>
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
          onClick={() => navigate("/admin/collections")}
          className="inline-flex items-center gap-1.5 text-sm text-white/60 hover:text-white/90 transition-colors mb-6"
        >
          <ChevronLeft className="w-4 h-4" />
          Back to collections
        </button>

        <motion.header variants={fadeInUp} initial="hidden" animate="visible" className="mb-6">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">
            {isEdit ? "Edit collection" : "New collection"}
          </p>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white">
            {isEdit ? existing?.name ?? "Loading…" : "Add a new collection"}
          </h1>
        </motion.header>

        <motion.form
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          onSubmit={onSubmit}
          className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6 sm:p-8 space-y-6"
        >
          <Field label="Name" error={errors.name?.message}>
            <input type="text" className={inputBase} placeholder="Summer essentials" {...form.register("name")} />
          </Field>

          <Field
            label="Slug"
            hint="Used in the public URL. Lowercase letters, digits, and hyphens only."
            error={errors.slug?.message}
          >
            <input type="text" className={inputBase} placeholder="summer-essentials" {...form.register("slug")} />
          </Field>

          <Field label="Description" error={errors.description?.message}>
            <textarea rows={3} className={inputBase} placeholder="Optional" {...form.register("description")} />
          </Field>

          <Field label="Header image URL" error={errors.imageUrl?.message}>
            <input type="url" className={inputBase} placeholder="https://…" {...form.register("imageUrl")} />
          </Field>

          <div className="rounded-xl border border-white/10 bg-white/[0.04] p-5 space-y-5">
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">Type</p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label className="flex items-start gap-3 rounded-xl border border-white/10 bg-white/[0.04] p-3 cursor-pointer hover:border-white/20">
                <input type="radio" value="STATIC" {...form.register("type")} className="mt-1 accent-sky-400" />
                <div>
                  <p className="text-sm font-semibold text-white">Static</p>
                  <p className="text-xs text-white/55">Pick products by hand.</p>
                </div>
              </label>
              <label className="flex items-start gap-3 rounded-xl border border-white/10 bg-white/[0.04] p-3 cursor-pointer hover:border-white/20">
                <input type="radio" value="DYNAMIC" {...form.register("type")} className="mt-1 accent-sky-400" />
                <div>
                  <p className="text-sm font-semibold text-white inline-flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-sky-200" />
                    Dynamic
                  </p>
                  <p className="text-xs text-white/55">Auto-populate by tag, category, or brand.</p>
                </div>
              </label>
            </div>

            {type === "DYNAMIC" && (
              <div className="space-y-4 pt-2">
                <Field
                  label="Tags (any of)"
                  hint="Comma-separated. e.g. summer, sale"
                  error={errors.tagsAnyOf?.message}
                >
                  <input type="text" className={inputBase} {...form.register("tagsAnyOf")} />
                </Field>
                <Field
                  label="Categories (any of)"
                  hint="Comma-separated. e.g. Apparel, Footwear"
                  error={errors.categoriesAnyOf?.message}
                >
                  <input type="text" className={inputBase} {...form.register("categoriesAnyOf")} />
                </Field>
                <Field
                  label="Brands (any of)"
                  hint="Comma-separated. e.g. Acme, Globex"
                  error={errors.brandsAnyOf?.message}
                >
                  <input type="text" className={inputBase} {...form.register("brandsAnyOf")} />
                </Field>
              </div>
            )}
          </div>

          <div className="rounded-xl border border-white/10 bg-white/[0.04] p-5 space-y-5">
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">Visibility</p>

            <Field label="Status" error={errors.status?.message}>
              <select className={inputBase} {...form.register("status")}>
                <option value="DRAFT">Draft</option>
                <option value="ACTIVE">Active — shoppers can see it</option>
                <option value="ARCHIVED">Archived</option>
              </select>
            </Field>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <label className="inline-flex items-center gap-2 text-sm text-white/80">
                <input type="checkbox" {...form.register("featured")} className="accent-sky-400" />
                Feature on marketplace home
              </label>
              <Field
                label="Featured rank"
                hint="Lower = surfaces first. Optional."
                error={errors.featuredRank?.message as string | undefined}
              >
                <input type="number" step="1" min="0" className={inputBase} {...form.register("featuredRank")} />
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
              onClick={() => navigate("/admin/collections")}
              className="px-4 py-2 rounded-full border border-white/20 text-sm font-semibold text-white/80 hover:bg-white/10 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 px-5 py-2 rounded-full bg-blue-600 hover:bg-blue-500 text-sm font-semibold text-white transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <Save className="w-4 h-4" />
              {submitting ? "Saving…" : isEdit ? "Save changes" : "Create collection"}
            </button>
          </div>
        </motion.form>
      </div>
    </div>
  );
}
