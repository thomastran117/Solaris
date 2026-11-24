"use client";

import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  FileText,
  Check,
  X,
  ReceiptText,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import {
  listInboundQuotes,
  respondToQuote,
  listInvoices,
  listOverdueInvoices,
  markInvoicePaid,
} from "../../api/b2b";
import type {
  Quote,
  QuoteStatus,
  B2BInvoice,
  InvoiceStatus,
  PaymentTerms,
  RevisedQuoteItemPayload,
} from "../../types/b2b";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

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

const QUOTE_STATUS_COLORS: Record<QuoteStatus, string> = {
  PENDING_VENDOR: "bg-warning-subtle text-warning border-warning/40",
  PENDING_BUYER: "bg-primary/10 text-link border-primary/40",
  CONVERTED: "bg-success-subtle text-success border-success/40",
  REJECTED: "bg-danger-subtle text-danger border-danger/40",
  EXPIRED: "bg-muted text-muted-foreground border-border",
};

const INVOICE_STATUS_COLORS: Record<InvoiceStatus, string> = {
  ISSUED: "bg-primary/10 text-link border-primary/40",
  PAID: "bg-success-subtle text-success border-success/40",
  OVERDUE: "bg-danger-subtle text-danger border-danger/40",
  CANCELLED: "bg-muted text-muted-foreground border-border",
};

const inputCls =
  "w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring";
const labelCls = "block text-xs font-semibold text-muted-foreground mb-1";
const fmt = (cents: number) => `$${(cents / 100).toFixed(2)}`;

function Pager({
  page,
  meta,
  onPage,
}: {
  page: number;
  meta?: { totalPages: number; hasNext: boolean; hasPrevious: boolean };
  onPage: (p: number) => void;
}) {
  if (!meta || meta.totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-center gap-3 mt-6">
      <button
        disabled={!meta.hasPrevious}
        onClick={() => onPage(Math.max(0, page - 1))}
        className="p-2 rounded-full border border-border text-muted-foreground disabled:opacity-30 hover:bg-muted"
      >
        <ChevronLeft className="w-4 h-4" />
      </button>
      <span className="text-xs text-muted-foreground">
        {page + 1} / {meta.totalPages}
      </span>
      <button
        disabled={!meta.hasNext}
        onClick={() => onPage(page + 1)}
        className="p-2 rounded-full border border-border text-muted-foreground disabled:opacity-30 hover:bg-muted"
      >
        <ChevronRight className="w-4 h-4" />
      </button>
    </div>
  );
}

interface RespondLine extends RevisedQuoteItemPayload {
  productName: string | null;
}

export default function AdminB2BQuotesPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [tab, setTab] = useState<"quotes" | "invoices">("quotes");
  const [respondTo, setRespondTo] = useState<Quote | null>(null);
  const [action, setAction] = useState<"APPROVE" | "COUNTER">("APPROVE");
  const [vendorNote, setVendorNote] = useState("");
  const [terms, setTerms] = useState<PaymentTerms>("IMMEDIATE");
  const [lines, setLines] = useState<RespondLine[]>([]);
  const [quotesPage, setQuotesPage] = useState(0);
  const [invoicesPage, setInvoicesPage] = useState(0);

  const { data: quotePage, isLoading: quotesLoading } = useQuery({
    queryKey: ["inbound-quotes", companyId, quotesPage],
    queryFn: () => listInboundQuotes(companyId!, undefined, quotesPage),
    enabled: !!companyId,
  });
  const quotes = quotePage?.items ?? [];

  const { data: invoicePage, isLoading: invoicesLoading } = useQuery({
    queryKey: ["b2b-invoices", companyId, invoicesPage],
    queryFn: () => listInvoices(companyId!, undefined, invoicesPage),
    enabled: !!companyId && tab === "invoices",
  });
  const invoices = invoicePage?.items ?? [];

  const { data: overdue = [] } = useQuery({
    queryKey: ["b2b-overdue", companyId],
    queryFn: () => listOverdueInvoices(companyId!),
    enabled: !!companyId && tab === "invoices",
  });
  const overdueIds = new Set(overdue.map((i: B2BInvoice) => i.id));

  const invalidateQuotes = () =>
    queryClient.invalidateQueries({ queryKey: ["inbound-quotes", companyId] });
  const invalidateInvoices = () => {
    queryClient.invalidateQueries({ queryKey: ["b2b-invoices", companyId] });
    queryClient.invalidateQueries({ queryKey: ["b2b-overdue", companyId] });
  };

  const respondMutation = useMutation({
    mutationFn: () =>
      respondToQuote(companyId!, respondTo!.id, {
        action,
        vendorNote: vendorNote || undefined,
        paymentTerms: terms,
        items:
          action === "COUNTER"
            ? lines.map((l) => ({
                productId: l.productId,
                variantId: l.variantId || undefined,
                quantity: l.quantity,
                unitPriceCents: l.unitPriceCents,
              }))
            : undefined,
      }),
    onSuccess: () => {
      invalidateQuotes();
      setRespondTo(null);
    },
    onError: (e) =>
      window.alert(apiErrorMessage(e, "Could not submit response.")),
  });

  const markPaidMutation = useMutation({
    mutationFn: ({
      invoiceId,
      reference,
    }: {
      invoiceId: string;
      reference?: string;
    }) => markInvoicePaid(companyId!, invoiceId, reference),
    onSuccess: invalidateInvoices,
    onError: (e) =>
      window.alert(apiErrorMessage(e, "Could not mark invoice paid.")),
  });

  const onMarkPaid = (invoiceId: string) => {
    const reference = window.prompt(
      "Payment reference (e.g. wire / check #), or leave blank:"
    );
    if (reference === null) return; // cancelled
    markPaidMutation.mutate({
      invoiceId,
      reference: reference.trim() || undefined,
    });
  };

  const openRespond = (q: Quote) => {
    setRespondTo(q);
    setAction("APPROVE");
    setVendorNote("");
    setTerms(q.paymentTerms);
    setLines(
      q.items.map((it) => ({
        productId: it.productId,
        variantId: it.variantId ?? undefined,
        quantity: it.quantity,
        unitPriceCents: it.unitPriceCents,
        productName: it.productName,
      }))
    );
  };

  return (
    <div className="px-6 py-10">
      <div className="max-w-5xl mx-auto">
        <div className="mb-8">
          <p className="text-xs font-semibold text-link mb-1">Admin</p>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            B2B Quotes & Invoices
          </h1>
          <p className="text-muted-foreground text-sm mt-1">
            Review inbound wholesale quotes and track net-terms invoices.
          </p>
        </div>

        <div className="flex gap-2 mb-6">
          {(["quotes", "invoices"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-4 py-2 rounded-full text-sm font-semibold transition border ${
                tab === t
                  ? "bg-primary border-primary text-primary-foreground"
                  : "border-border text-muted-foreground hover:bg-muted"
              }`}
            >
              {t === "quotes" ? "Inbound Quotes" : "Invoices"}
            </button>
          ))}
        </div>

        {tab === "quotes" &&
          (quotesLoading ? (
            <div className="text-muted-foreground text-sm py-12 text-center">
              Loading…
            </div>
          ) : quotes.length === 0 ? (
            <div className="rounded-xl border border-border bg-surface p-12 text-center">
              <FileText className="w-10 h-10 text-link mx-auto mb-3 opacity-50" />
              <p className="text-muted-foreground text-sm">
                No inbound quotes.
              </p>
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
                          className={`text-[11px] px-2 py-0.5 rounded-full border font-semibold ${QUOTE_STATUS_COLORS[q.status]}`}
                        >
                          {q.status.replace("_", " ")}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {q.paymentTerms}
                        </span>
                      </div>
                      <p className="text-foreground font-semibold text-lg">
                        {fmt(q.totalCents)}
                      </p>
                      <p className="text-muted-foreground text-xs mt-0.5">
                        {q.items.length} item{q.items.length !== 1 ? "s" : ""} ·{" "}
                        {new Date(q.createdAt).toLocaleDateString()}
                      </p>
                      {q.buyerMessage && (
                        <p className="text-muted-foreground text-sm mt-2 italic">
                          “{q.buyerMessage}”
                        </p>
                      )}
                    </div>
                    {q.status === "PENDING_VENDOR" && (
                      <button
                        onClick={() => openRespond(q)}
                        className="bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-4 py-2 rounded-full transition"
                      >
                        Respond
                      </button>
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
          ))}

        {tab === "invoices" &&
          (invoicesLoading ? (
            <div className="text-muted-foreground text-sm py-12 text-center">
              Loading…
            </div>
          ) : invoices.length === 0 ? (
            <div className="rounded-xl border border-border bg-surface p-12 text-center">
              <ReceiptText className="w-10 h-10 text-link mx-auto mb-3 opacity-50" />
              <p className="text-muted-foreground text-sm">No invoices.</p>
            </div>
          ) : (
            <motion.div
              variants={stagger}
              initial="hidden"
              animate="visible"
              className="space-y-3"
            >
              {invoices.map((inv: B2BInvoice) => {
                const isOverdue =
                  overdueIds.has(inv.id) || inv.status === "OVERDUE";
                return (
                  <motion.div
                    key={inv.id}
                    variants={fadeInUp}
                    className="rounded-xl border border-border bg-surface shadow-sm p-5 flex items-center justify-between gap-4 flex-wrap"
                  >
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-foreground font-semibold">
                          {inv.invoiceNumber}
                        </span>
                        <span
                          className={`text-[11px] px-2 py-0.5 rounded-full border font-semibold ${INVOICE_STATUS_COLORS[inv.status]}`}
                        >
                          {inv.status}
                        </span>
                        {isOverdue && inv.status !== "PAID" && (
                          <span className="flex items-center gap-1 text-[11px] text-danger">
                            <AlertTriangle className="w-3 h-3" /> Overdue
                          </span>
                        )}
                      </div>
                      <p className="text-muted-foreground text-xs">
                        {fmt(inv.totalCents)} · due{" "}
                        {new Date(inv.dueDateAt).toLocaleDateString()}
                      </p>
                    </div>
                    {inv.status !== "PAID" && inv.status !== "CANCELLED" && (
                      <button
                        onClick={() => onMarkPaid(inv.id)}
                        disabled={markPaidMutation.isPending}
                        className="flex items-center gap-1.5 bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-3 py-1.5 rounded-full transition disabled:opacity-50"
                      >
                        <Check className="w-4 h-4" /> Mark Paid
                      </button>
                    )}
                  </motion.div>
                );
              })}
            </motion.div>
          ))}

        {tab === "quotes" && (
          <Pager page={quotesPage} meta={quotePage} onPage={setQuotesPage} />
        )}
        {tab === "invoices" && (
          <Pager
            page={invoicesPage}
            meta={invoicePage}
            onPage={setInvoicesPage}
          />
        )}
      </div>

      {respondTo && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-overlay p-4">
          <div className="w-full max-w-lg my-8 rounded-xl border border-border bg-overlay p-8 shadow-xl">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-foreground">
                Respond to Quote
              </h2>
              <button
                onClick={() => setRespondTo(null)}
                className="text-muted-foreground hover:text-foreground"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex gap-2 mb-5">
              {(["APPROVE", "COUNTER"] as const).map((a) => (
                <button
                  key={a}
                  onClick={() => setAction(a)}
                  className={`px-4 py-2 rounded-full text-sm font-semibold transition border ${
                    action === a
                      ? "bg-primary border-primary text-primary-foreground"
                      : "border-border text-muted-foreground hover:bg-muted"
                  }`}
                >
                  {a === "APPROVE" ? "Approve as-is" : "Counter-offer"}
                </button>
              ))}
            </div>

            <div className="space-y-4">
              <div>
                <label className={labelCls}>Payment Terms</label>
                <select
                  className={inputCls}
                  value={terms}
                  onChange={(e) => setTerms(e.target.value as PaymentTerms)}
                >
                  <option value="IMMEDIATE">Immediate (card)</option>
                  <option value="NET_30">Net 30</option>
                  <option value="NET_60">Net 60</option>
                </select>
              </div>

              {action === "COUNTER" && (
                <div>
                  <label className={labelCls}>Revised Line Prices</label>
                  <div className="space-y-2">
                    {lines.map((l, idx) => (
                      <div key={idx} className="flex items-center gap-2">
                        <span className="flex-1 text-sm text-muted-foreground truncate">
                          {l.productName ?? l.productId} × {l.quantity}
                        </span>
                        <div className="flex items-center gap-1">
                          <span className="text-muted-foreground text-sm">
                            $
                          </span>
                          <input
                            type="number"
                            min={0}
                            step="0.01"
                            className={`${inputCls} w-28`}
                            value={(l.unitPriceCents / 100).toString()}
                            onChange={(e) => {
                              const cents = Math.round(
                                parseFloat(e.target.value || "0") * 100
                              );
                              setLines((prev) =>
                                prev.map((p, i) =>
                                  i === idx
                                    ? {
                                        ...p,
                                        unitPriceCents: isNaN(cents)
                                          ? 0
                                          : cents,
                                      }
                                    : p
                                )
                              );
                            }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <label className={labelCls}>Note to Buyer</label>
                <textarea
                  className={inputCls}
                  rows={2}
                  value={vendorNote}
                  onChange={(e) => setVendorNote(e.target.value)}
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  onClick={() => setRespondTo(null)}
                  className="border border-input hover:bg-muted text-foreground text-sm font-semibold px-4 py-2 rounded-full transition"
                >
                  Cancel
                </button>
                <button
                  onClick={() => respondMutation.mutate()}
                  disabled={respondMutation.isPending}
                  className="bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-4 py-2 rounded-full transition disabled:opacity-50"
                >
                  {respondMutation.isPending ? "Sending…" : "Send Response"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
