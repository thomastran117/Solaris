"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import {
  Plus,
  X,
  FileText,
  Check,
  Ban,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
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
    response?: {
      data?: { message?: string; error?: { details?: { detail?: string } } };
    };
  };
  return (
    e?.response?.data?.error?.details?.detail ??
    e?.apiMessage ??
    e?.response?.data?.message ??
    fallback
  );
}

const STATUS_COLORS: Record<QuoteStatus, string> = {
  PENDING_VENDOR: "bg-warning-subtle text-warning border-warning/40",
  PENDING_BUYER: "bg-primary/10 text-link border-primary/40",
  CONVERTED: "bg-success-subtle text-success border-success/40",
  REJECTED: "bg-danger-subtle text-danger border-danger/40",
  EXPIRED: "bg-muted text-muted-foreground border-border",
};

const STATUS_LABELS: Record<QuoteStatus | "ALL", string> = {
  ALL: "All",
  PENDING_VENDOR: "Awaiting Vendor",
  PENDING_BUYER: "Awaiting You",
  CONVERTED: "Converted",
  REJECTED: "Rejected",
  EXPIRED: "Expired",
};

const FILTERS: (QuoteStatus | "ALL")[] = [
  "ALL",
  "PENDING_VENDOR",
  "PENDING_BUYER",
  "CONVERTED",
  "REJECTED",
  "EXPIRED",
];

const inputCls =
  "w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring";
const labelCls = "block text-xs font-semibold text-muted-foreground mb-1";

const fmt = (cents: number) => `$${(cents / 100).toFixed(2)}`;

export default function B2BQuotesPage() {
  const queryClient = useQueryClient();
  const router = useRouter();
  const { fadeInUp, stagger } = useAnims();

  const [filter, setFilter] = useState<QuoteStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [vendorCompanyId, setVendorCompanyId] = useState("");

  const { data: quotePage, isLoading } = useQuery({
    queryKey: ["my-quotes", filter, page],
    queryFn: () => listMyQuotes(filter, page),
  });
  const quotes = quotePage?.items ?? [];

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["my-quotes"] });

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
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "items",
  });

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
    onError: (e) =>
      window.alert(apiErrorMessage(e, "Failed to submit quote request.")),
  });

  const acceptMutation = useMutation({
    mutationFn: (id: string) => acceptQuote(id),
    onSuccess: (order) => {
      invalidate();
      // Land the buyer on the created order. For IMMEDIATE terms it is RESERVED pending payment
      // capture (confirmed server-side via the Stripe webhook, same as a normal checkout); for net
      // terms it is already PAID with an invoice issued. Don't discard the returned order.
      router.push(`/orders/${order.id}`);
    },
    onError: (e) => {
      invalidate();
      window.alert(apiErrorMessage(e, "Could not accept this quote."));
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (id: string) => rejectQuote(id),
    onSuccess: () => invalidate(),
    onError: (e) =>
      window.alert(apiErrorMessage(e, "Could not reject this quote.")),
  });

  const onSubmit = (values: CreateQuoteFormValues) => {
    if (!/^[0-9a-fA-F-]{36}$/.test(vendorCompanyId)) {
      window.alert("Enter a valid vendor company ID.");
      return;
    }
    createMutation.mutate(values);
  };

  return (
    <div className="px-6 py-10">
      <div className="max-w-5xl">
        <div className="mb-8 flex items-end justify-between gap-4 flex-wrap">
          <div>
            <p className="text-xs font-semibold text-link mb-1">Wholesale</p>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              My Quotes
            </h1>
            <p className="text-muted-foreground text-sm mt-1">
              Request wholesale pricing and accept negotiated quotes.
            </p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-4 py-2 rounded-full transition"
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
                  ? "bg-primary border-primary text-primary-foreground"
                  : "border-border text-muted-foreground hover:bg-muted"
              }`}
            >
              {STATUS_LABELS[s]}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="text-muted-foreground text-sm py-12 text-center">
            Loading…
          </div>
        ) : quotes.length === 0 ? (
          <div className="rounded-xl border border-border bg-surface p-12 text-center">
            <FileText className="w-10 h-10 text-link mx-auto mb-3 opacity-50" />
            <p className="text-muted-foreground text-sm">No quotes yet.</p>
          </div>
        ) : (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="space-y-4"
          >
            {quotes.map((q: Quote) => (
              <motion.div
                key={q.id}
                variants={fadeInUp}
                className="rounded-xl border border-border bg-surface shadow-sm p-5"
              >
                <div className="flex items-start justify-between gap-4 flex-wrap">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className={`text-[11px] px-2 py-0.5 rounded-full border font-semibold ${STATUS_COLORS[q.status]}`}
                      >
                        {STATUS_LABELS[q.status]}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {q.paymentTerms}
                      </span>
                    </div>
                    <p className="text-foreground font-semibold text-lg">
                      {fmt(q.totalCents)}
                    </p>
                    <p className="text-muted-foreground text-xs mt-0.5">
                      {q.items.length} item{q.items.length !== 1 ? "s" : ""} ·
                      expires {new Date(q.expiresAt).toLocaleDateString()}
                    </p>
                    {q.vendorNote && (
                      <p className="text-muted-foreground text-sm mt-2 italic">
                        “{q.vendorNote}”
                      </p>
                    )}
                  </div>
                  {q.status === "PENDING_BUYER" && (
                    <div className="flex gap-2">
                      <button
                        onClick={() => acceptMutation.mutate(q.id)}
                        disabled={acceptMutation.isPending}
                        className="flex items-center gap-1.5 bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-3 py-1.5 rounded-full transition disabled:opacity-50"
                      >
                        <Check className="w-4 h-4" /> Accept
                      </button>
                      <button
                        onClick={() => rejectMutation.mutate(q.id)}
                        disabled={rejectMutation.isPending}
                        className="flex items-center gap-1.5 border border-input hover:bg-muted text-foreground text-sm font-semibold px-3 py-1.5 rounded-full transition disabled:opacity-50"
                      >
                        <Ban className="w-4 h-4" /> Reject
                      </button>
                    </div>
                  )}
                </div>
                <div className="mt-3 border-t border-border pt-3 space-y-1">
                  {q.items.map((it) => (
                    <div
                      key={it.id}
                      className="flex justify-between text-sm text-muted-foreground"
                    >
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
              className="p-2 rounded-full border border-border text-muted-foreground disabled:opacity-30 hover:bg-muted"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="text-xs text-muted-foreground">
              {page + 1} / {quotePage.totalPages}
            </span>
            <button
              disabled={!quotePage.hasNext}
              onClick={() => setPage((p) => p + 1)}
              className="p-2 rounded-full border border-border text-muted-foreground disabled:opacity-30 hover:bg-muted"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-overlay p-4">
          <div className="w-full max-w-lg my-8 rounded-xl border border-border bg-surface p-8 shadow-xl">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-foreground">
                Request a Quote
              </h2>
              <button
                onClick={() => setShowCreate(false)}
                className="text-muted-foreground hover:text-foreground"
              >
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
                  <p className="text-danger text-xs mt-1">
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
                  <select
                    className={inputCls}
                    {...form.register("paymentTerms")}
                  >
                    <option value="IMMEDIATE">Immediate (card)</option>
                    <option value="NET_30">Net 30</option>
                    <option value="NET_60">Net 60</option>
                  </select>
                </div>
              </div>
              <div>
                <label className={labelCls}>Billing Address</label>
                <input
                  className={inputCls}
                  {...form.register("billingAddress")}
                />
              </div>
              <div>
                <label className={labelCls}>Message</label>
                <textarea
                  className={inputCls}
                  rows={2}
                  {...form.register("message")}
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className={labelCls}>Items</label>
                  <button
                    type="button"
                    onClick={() => append({ productId: "", quantity: 1 })}
                    className="text-xs text-link hover:text-link flex items-center gap-1"
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
                          <p className="text-danger text-xs mt-1">
                            {
                              form.formState.errors.items[idx]?.productId
                                ?.message
                            }
                          </p>
                        )}
                      </div>
                      <input
                        type="number"
                        min={1}
                        className={`${inputCls} w-20`}
                        {...form.register(`items.${idx}.quantity`, {
                          valueAsNumber: true,
                        })}
                      />
                      {fields.length > 1 && (
                        <button
                          type="button"
                          onClick={() => remove(idx)}
                          className="p-2 text-muted-foreground hover:text-danger"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
                {form.formState.errors.items?.root && (
                  <p className="text-danger text-xs mt-1">
                    {form.formState.errors.items.root.message}
                  </p>
                )}
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreate(false)}
                  className="border border-input hover:bg-muted text-foreground text-sm font-semibold px-4 py-2 rounded-full transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-4 py-2 rounded-full transition disabled:opacity-50"
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
