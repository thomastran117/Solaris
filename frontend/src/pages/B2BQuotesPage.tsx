import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import { Plus, X, FileText, Check, Ban, ChevronLeft, ChevronRight } from "lucide-react";
import {
  listMyQuotes,
  requestQuote,
  acceptQuote,
  rejectQuote,
} from "../api/b2b";
import { createQuoteSchema, type CreateQuoteFormValues } from "../schemas/b2b";
import type { Quote, QuoteStatus } from "../types/b2b";
import { useAnims } from "../hooks/useAnims";

function apiErrorMessage(error: unknown, fallback: string): string {
  const e = error as {
    apiMessage?: string;
    response?: { data?: { message?: string; error?: { details?: { detail?: string } } } };
  };
  return (
    e?.response?.data?.error?.details?.detail ??
    e?.apiMessage ??
    e?.response?.data?.message ??
    fallback
  );
}

const STATUS_COLORS: Record<QuoteStatus, string> = {
  DRAFT: "bg-white/10 text-white/60 border-white/10",
  PENDING_VENDOR: "bg-yellow-500/15 text-yellow-400 border-yellow-500/20",
  PENDING_BUYER: "bg-blue-500/15 text-blue-400 border-blue-500/20",
  ACCEPTED: "bg-green-500/15 text-green-400 border-green-500/20",
  CONVERTED: "bg-green-500/15 text-green-400 border-green-500/20",
  REJECTED: "bg-red-500/15 text-red-400 border-red-500/20",
  EXPIRED: "bg-white/10 text-white/50 border-white/10",
};

const STATUS_LABELS: Record<QuoteStatus | "ALL", string> = {
  ALL: "All",
  DRAFT: "Draft",
  PENDING_VENDOR: "Awaiting Vendor",
  PENDING_BUYER: "Awaiting You",
  ACCEPTED: "Accepted",
  CONVERTED: "Converted",
  REJECTED: "Rejected",
  EXPIRED: "Expired",
};

const FILTERS: (QuoteStatus | "ALL")[] = [
  "ALL", "PENDING_VENDOR", "PENDING_BUYER", "CONVERTED", "REJECTED", "EXPIRED",
];

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25";
const labelCls = "block text-xs font-semibold text-white/60 mb-1 uppercase tracking-widest";

const fmt = (cents: number) => `$${(cents / 100).toFixed(2)}`;

export default function B2BQuotesPage() {
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [filter, setFilter] = useState<QuoteStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [vendorCompanyId, setVendorCompanyId] = useState("");

  const { data: quotePage, isLoading } = useQuery({
    queryKey: ["my-quotes", filter, page],
    queryFn: () => listMyQuotes(page),
  });
  const allQuotes = quotePage?.items ?? [];
  const quotes = filter ? allQuotes.filter((q) => q.status === filter) : allQuotes;

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["my-quotes"] });

  const form = useForm<CreateQuoteFormValues>({
    resolver: zodResolver(createQuoteSchema),
    defaultValues: {
      companyName: "",
      taxId: "",
      billingAddress: "",
      message: "",
      paymentTerms: "IMMEDIATE",
      items: [{ productId: "", quantity: 1 }],
    },
  });
  const { fields, append, remove } = useFieldArray({ control: form.control, name: "items" });

  const createMutation = useMutation({
    mutationFn: (values: CreateQuoteFormValues) =>
      requestQuote(vendorCompanyId, {
        ...values,
        items: values.items.map((i) => ({
          productId: i.productId,
          variantId: i.variantId || undefined,
          quantity: i.quantity,
        })),
      }),
    onSuccess: () => {
      invalidate();
      setShowCreate(false);
      setVendorCompanyId("");
      form.reset();
    },
    onError: (e) => window.alert(apiErrorMessage(e, "Failed to submit quote request.")),
  });

  const acceptMutation = useMutation({
    mutationFn: (id: string) => acceptQuote(id),
    onSuccess: () => invalidate(),
    onError: (e) => {
      invalidate();
      window.alert(apiErrorMessage(e, "Could not accept this quote."));
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (id: string) => rejectQuote(id),
    onSuccess: () => invalidate(),
    onError: (e) => window.alert(apiErrorMessage(e, "Could not reject this quote.")),
  });

  const onSubmit = (values: CreateQuoteFormValues) => {
    if (!/^[0-9a-fA-F-]{36}$/.test(vendorCompanyId)) {
      window.alert("Enter a valid vendor company ID.");
      return;
    }
    createMutation.mutate(values);
  };

  return (
    <div className="min-h-screen bg-slate-950 px-6 py-10">
      <div className="max-w-5xl mx-auto">
        <div className="mb-8 flex items-end justify-between gap-4 flex-wrap">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-1">
              Wholesale
            </p>
            <h1 className="text-3xl md:text-4xl font-extrabold text-white">My Quotes</h1>
            <p className="text-white/60 text-sm mt-1">
              Request wholesale pricing and accept negotiated quotes.
            </p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-4 py-2 rounded-full transition"
          >
            <Plus className="w-4 h-4" />
            Request Quote
          </button>
        </div>

        <div className="flex flex-wrap gap-2 mb-5">
          {FILTERS.map((s) => (
            <button
              key={s}
              onClick={() => {
                setFilter(s === "ALL" ? undefined : (s as QuoteStatus));
                setPage(0);
              }}
              className={`text-xs px-3 py-1 rounded-full border transition font-semibold ${
                (s === "ALL" && !filter) || s === filter
                  ? "bg-blue-600 border-blue-500 text-white"
                  : "border-white/10 text-white/50 hover:bg-white/10"
              }`}
            >
              {STATUS_LABELS[s]}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="text-white/50 text-sm py-12 text-center">Loading…</div>
        ) : quotes.length === 0 ? (
          <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-12 text-center">
            <FileText className="w-10 h-10 text-sky-200 mx-auto mb-3 opacity-50" />
            <p className="text-white/60 text-sm">No quotes yet.</p>
          </div>
        ) : (
          <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-4">
            {quotes.map((q: Quote) => (
              <motion.div
                key={q.id}
                variants={fadeInUp}
                className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-5"
              >
                <div className="flex items-start justify-between gap-4 flex-wrap">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className={`text-[11px] px-2 py-0.5 rounded-full border font-semibold ${STATUS_COLORS[q.status]}`}
                      >
                        {STATUS_LABELS[q.status]}
                      </span>
                      <span className="text-xs text-white/50">{q.paymentTerms}</span>
                    </div>
                    <p className="text-white font-semibold text-lg">{fmt(q.totalCents)}</p>
                    <p className="text-white/55 text-xs mt-0.5">
                      {q.items.length} item{q.items.length !== 1 ? "s" : ""} · expires{" "}
                      {new Date(q.expiresAt).toLocaleDateString()}
                    </p>
                    {q.vendorNote && (
                      <p className="text-white/60 text-sm mt-2 italic">“{q.vendorNote}”</p>
                    )}
                  </div>
                  {q.status === "PENDING_BUYER" && (
                    <div className="flex gap-2">
                      <button
                        onClick={() => acceptMutation.mutate(q.id)}
                        disabled={acceptMutation.isPending}
                        className="flex items-center gap-1.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-3 py-1.5 rounded-full transition disabled:opacity-50"
                      >
                        <Check className="w-4 h-4" /> Accept
                      </button>
                      <button
                        onClick={() => rejectMutation.mutate(q.id)}
                        disabled={rejectMutation.isPending}
                        className="flex items-center gap-1.5 border border-white/20 hover:bg-white/10 text-white text-sm font-semibold px-3 py-1.5 rounded-full transition disabled:opacity-50"
                      >
                        <Ban className="w-4 h-4" /> Reject
                      </button>
                    </div>
                  )}
                </div>
                <div className="mt-3 border-t border-white/10 pt-3 space-y-1">
                  {q.items.map((it) => (
                    <div key={it.id} className="flex justify-between text-sm text-white/70">
                      <span>
                        {it.productName ?? it.productId} × {it.quantity}
                      </span>
                      <span>{fmt(it.totalPriceCents)}</span>
                    </div>
                  ))}
                </div>
              </motion.div>
            ))}
          </motion.div>
        )}

        {quotePage && quotePage.totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 mt-6">
            <button
              disabled={!quotePage.hasPrevious}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="p-2 rounded-full border border-white/10 text-white/70 disabled:opacity-30 hover:bg-white/10"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="text-xs text-white/50">
              {page + 1} / {quotePage.totalPages}
            </span>
            <button
              disabled={!quotePage.hasNext}
              onClick={() => setPage((p) => p + 1)}
              className="p-2 rounded-full border border-white/10 text-white/70 disabled:opacity-30 hover:bg-white/10"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg my-8 rounded-3xl border border-white/10 bg-slate-900/95 backdrop-blur p-8 shadow-xl">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-extrabold text-white">Request a Quote</h2>
              <button onClick={() => setShowCreate(false)} className="text-white/60 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <div>
                <label className={labelCls}>Vendor Company ID</label>
                <input
                  className={inputCls}
                  value={vendorCompanyId}
                  onChange={(e) => setVendorCompanyId(e.target.value)}
                  placeholder="UUID of the vendor company"
                />
              </div>
              <div>
                <label className={labelCls}>Your Company Name</label>
                <input className={inputCls} {...form.register("companyName")} />
                {form.formState.errors.companyName && (
                  <p className="text-red-400 text-xs mt-1">
                    {form.formState.errors.companyName.message}
                  </p>
                )}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={labelCls}>Tax ID</label>
                  <input className={inputCls} {...form.register("taxId")} />
                </div>
                <div>
                  <label className={labelCls}>Payment Terms</label>
                  <select className={inputCls} {...form.register("paymentTerms")}>
                    <option value="IMMEDIATE">Immediate (card)</option>
                    <option value="NET_30">Net 30</option>
                    <option value="NET_60">Net 60</option>
                  </select>
                </div>
              </div>
              <div>
                <label className={labelCls}>Billing Address</label>
                <input className={inputCls} {...form.register("billingAddress")} />
              </div>
              <div>
                <label className={labelCls}>Message</label>
                <textarea className={inputCls} rows={2} {...form.register("message")} />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className={labelCls}>Items</label>
                  <button
                    type="button"
                    onClick={() => append({ productId: "", quantity: 1 })}
                    className="text-xs text-sky-200 hover:text-sky-100 flex items-center gap-1"
                  >
                    <Plus className="w-3 h-3" /> Add
                  </button>
                </div>
                <div className="space-y-2">
                  {fields.map((f, idx) => (
                    <div key={f.id} className="flex gap-2 items-start">
                      <div className="flex-1">
                        <input
                          className={inputCls}
                          placeholder="Product ID"
                          {...form.register(`items.${idx}.productId`)}
                        />
                        {form.formState.errors.items?.[idx]?.productId && (
                          <p className="text-red-400 text-xs mt-1">
                            {form.formState.errors.items[idx]?.productId?.message}
                          </p>
                        )}
                      </div>
                      <input
                        type="number"
                        min={1}
                        className={`${inputCls} w-20`}
                        {...form.register(`items.${idx}.quantity`, { valueAsNumber: true })}
                      />
                      {fields.length > 1 && (
                        <button
                          type="button"
                          onClick={() => remove(idx)}
                          className="p-2 text-white/50 hover:text-red-400"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
                {form.formState.errors.items?.root && (
                  <p className="text-red-400 text-xs mt-1">
                    {form.formState.errors.items.root.message}
                  </p>
                )}
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreate(false)}
                  className="border border-white/20 hover:bg-white/10 text-white text-sm font-semibold px-4 py-2 rounded-full transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-4 py-2 rounded-full transition disabled:opacity-50"
                >
                  {createMutation.isPending ? "Submitting…" : "Submit Request"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
