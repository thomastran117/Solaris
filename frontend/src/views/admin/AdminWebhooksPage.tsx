"use client";

import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, AnimatePresence } from "framer-motion";
import {
  Webhook,
  Plus,
  Trash2,
  ShieldCheck,
  X,
  Copy,
  ChevronDown,
  ChevronRight,
  PauseCircle,
} from "lucide-react";
import { webhooksApi } from "../../api/webhooks";
import type {
  WebhookEventType,
  WebhookSubscriptionStatus,
  WebhookSubscriptionResponse,
  RegisterWebhookPayload,
  WebhookDeliveryLogResponse,
  WebhookDeliveryStatus,
} from "../../types/webhook";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const ALL_EVENTS: WebhookEventType[] = [
  "ORDER_CREATED",
  "ORDER_PAID",
  "ORDER_SHIPPED",
  "ORDER_CANCELLED",
  "ORDER_DELIVERED",
  "STOCK_LOW",
];

const EVENT_LABELS: Record<WebhookEventType, string> = {
  ORDER_CREATED: "order.created",
  ORDER_PAID: "order.paid",
  ORDER_SHIPPED: "order.shipped",
  ORDER_CANCELLED: "order.cancelled",
  ORDER_DELIVERED: "order.delivered",
  STOCK_LOW: "stock.low",
};

const STATUS_CLS: Record<WebhookSubscriptionStatus, string> = {
  ACTIVE: "bg-success-subtle border-success/40 text-success",
  PENDING_VERIFICATION: "bg-warning-subtle border-warning/40 text-warning",
  DISABLED: "bg-surface border-border text-muted-foreground",
};

const DELIVERY_CLS: Record<WebhookDeliveryStatus, string> = {
  DELIVERED: "bg-success-subtle border-success/40 text-success",
  FAILED: "bg-danger-subtle border-danger/40 text-danger",
  PENDING: "bg-warning-subtle border-warning/40 text-warning",
};

const inputCls =
  "w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring";

export default function AdminWebhooksPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [showCreate, setShowCreate] = useState(false);
  const [url, setUrl] = useState("");
  const [selectedEvents, setSelectedEvents] = useState<Set<WebhookEventType>>(
    new Set()
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [expandedDeliveries, setExpandedDeliveries] = useState<string | null>(
    null
  );
  const [deliveryPage, setDeliveryPage] = useState(0);
  const [copied, setCopied] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["admin", "webhooks", companyId],
    queryFn: () => webhooksApi.list(companyId!).then((r) => r.data),
    enabled: !!companyId,
  });

  const deliveriesQuery = useQuery({
    queryKey: [
      "admin",
      "webhooks",
      companyId,
      expandedDeliveries,
      "deliveries",
      deliveryPage,
    ],
    queryFn: () =>
      webhooksApi
        .deliveries(companyId!, expandedDeliveries!, deliveryPage)
        .then((r) => r.data),
    enabled: !!companyId && !!expandedDeliveries,
  });

  const registerMutation = useMutation({
    mutationFn: (payload: RegisterWebhookPayload) =>
      webhooksApi.register(companyId!, payload),
    onSuccess: (res) => {
      queryClient.invalidateQueries({
        queryKey: ["admin", "webhooks", companyId],
      });
      setRevealedSecret(res.data.secret);
      setShowCreate(false);
      setUrl("");
      setSelectedEvents(new Set());
      setFormError(null);
    },
    onError: () =>
      setFormError("Failed to register endpoint. Check the URL and try again."),
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => webhooksApi.remove(companyId!, id),
    onSuccess: () => {
      setActionError(null);
      queryClient.invalidateQueries({
        queryKey: ["admin", "webhooks", companyId],
      });
    },
    onError: () =>
      setActionError("Failed to remove the endpoint. Please try again."),
  });

  const verifyMutation = useMutation({
    mutationFn: (id: string) => webhooksApi.verify(companyId!, id),
    onSuccess: () => {
      setActionError(null);
      queryClient.invalidateQueries({
        queryKey: ["admin", "webhooks", companyId],
      });
    },
    onError: () =>
      setActionError(
        "Verification failed. Ensure your endpoint echoes the challenge query parameter, then try again."
      ),
  });

  const disableMutation = useMutation({
    mutationFn: (id: string) => webhooksApi.disable(companyId!, id),
    onSuccess: () => {
      setActionError(null);
      queryClient.invalidateQueries({
        queryKey: ["admin", "webhooks", companyId],
      });
    },
    onError: () =>
      setActionError("Failed to disable the endpoint. Please try again."),
  });

  function toggleEvent(evt: WebhookEventType) {
    setSelectedEvents((prev) => {
      const next = new Set(prev);
      next.has(evt) ? next.delete(evt) : next.add(evt);
      return next;
    });
  }

  function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    if (!url.trim()) {
      setFormError("URL is required.");
      return;
    }
    if (selectedEvents.size === 0) {
      setFormError("Select at least one event type.");
      return;
    }
    registerMutation.mutate({
      url: url.trim(),
      events: Array.from(selectedEvents),
    });
  }

  function copySecret() {
    if (!revealedSecret) return;
    navigator.clipboard.writeText(revealedSecret).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  function toggleDeliveries(id: string) {
    if (expandedDeliveries === id) {
      setExpandedDeliveries(null);
    } else {
      setExpandedDeliveries(id);
      setDeliveryPage(0);
    }
  }

  const subs: WebhookSubscriptionResponse[] = listQuery.data ?? [];

  return (
    <div className="text-foreground">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-primary/10 border border-border flex items-center justify-center">
              <Webhook className="w-4 h-4 text-link" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-foreground">
                Webhooks
              </h1>
              <p className="text-xs text-muted-foreground mt-0.5">
                Receive real-time event notifications at your endpoints
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              setShowCreate((v) => !v);
              setFormError(null);
            }}
            className="inline-flex items-center gap-2 text-sm bg-primary hover:bg-primary-hover rounded-full px-4 py-2 text-primary-foreground font-medium transition-colors"
          >
            {showCreate ? (
              <X className="w-4 h-4" />
            ) : (
              <Plus className="w-4 h-4" />
            )}
            {showCreate ? "Cancel" : "Add Endpoint"}
          </button>
        </div>

        {/* Action error banner (verify / disable / remove failures) */}
        {actionError && (
          <div className="flex items-start justify-between gap-4 rounded-xl border border-danger/40 bg-danger-subtle p-4 mb-6">
            <p className="text-sm text-danger">{actionError}</p>
            <button
              type="button"
              onClick={() => setActionError(null)}
              className="shrink-0 text-muted-foreground hover:text-foreground transition-colors"
              aria-label="Dismiss error"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* Secret reveal banner */}
        <AnimatePresence>
          {revealedSecret && (
            <motion.div
              variants={fadeInUp}
              initial="hidden"
              animate="visible"
              exit="hidden"
              className="rounded-xl border border-warning/40 bg-warning-subtle p-5 mb-6"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-warning mb-1">
                    Webhook secret — copy it now
                  </p>
                  <p className="text-xs text-muted-foreground mb-3">
                    This secret will not be shown again. Use it to verify the{" "}
                    <code className="text-foreground">
                      X-ShopWave-Signature
                    </code>{" "}
                    header on incoming events.
                  </p>
                  <div className="flex items-center gap-2">
                    <code className="flex-1 text-xs font-mono text-warning bg-overlay rounded-lg px-3 py-2 break-all">
                      {revealedSecret}
                    </code>
                    <button
                      type="button"
                      onClick={copySecret}
                      className="shrink-0 p-2 rounded-lg border border-border text-muted-foreground hover:text-foreground hover:border-input transition-colors"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                  </div>
                  {copied && (
                    <p className="text-xs text-success mt-1">Copied!</p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => setRevealedSecret(null)}
                  className="shrink-0 text-muted-foreground hover:text-foreground transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Register form */}
        {showCreate && (
          <motion.div
            variants={fadeInUp}
            initial="hidden"
            animate="visible"
            className="rounded-xl border border-border bg-surface p-6 mb-6"
          >
            <h2 className="text-base font-semibold text-foreground mb-4">
              New Webhook Endpoint
            </h2>
            <form onSubmit={handleRegister} className="flex flex-col gap-5">
              <div>
                <label className="text-xs text-muted-foreground mb-1.5 block">
                  Endpoint URL
                </label>
                <input
                  className={inputCls}
                  type="url"
                  placeholder="https://your-server.com/webhooks/shopwave"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-2 block">
                  Events to subscribe
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {ALL_EVENTS.map((evt) => (
                    <label
                      key={evt}
                      className="flex items-center gap-2.5 cursor-pointer group"
                    >
                      <input
                        type="checkbox"
                        checked={selectedEvents.has(evt)}
                        onChange={() => toggleEvent(evt)}
                        className="w-4 h-4 rounded border border-input accent-primary"
                      />
                      <span className="text-sm text-muted-foreground group-hover:text-foreground transition-colors font-mono">
                        {EVENT_LABELS[evt]}
                      </span>
                    </label>
                  ))}
                </div>
              </div>
              {formError && <p className="text-xs text-danger">{formError}</p>}
              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={registerMutation.isPending}
                  className="inline-flex items-center gap-2 text-sm bg-primary hover:bg-primary-hover rounded-full px-5 py-2 text-primary-foreground font-medium transition-colors disabled:opacity-50"
                >
                  {registerMutation.isPending
                    ? "Registering…"
                    : "Register Endpoint"}
                </button>
              </div>
            </form>
          </motion.div>
        )}

        {/* Loading skeleton */}
        {listQuery.isLoading && (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <div
                key={i}
                className="rounded-xl border border-border bg-surface h-20 animate-pulse"
              />
            ))}
          </div>
        )}

        {/* Empty state */}
        {!listQuery.isLoading && subs.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <Webhook className="w-12 h-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-semibold text-foreground mb-2">
              No webhook endpoints
            </h3>
            <p className="text-sm text-muted-foreground">
              Add an endpoint to start receiving real-time event notifications.
            </p>
          </div>
        )}

        {/* Subscription list */}
        {!listQuery.isLoading && subs.length > 0 && (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="space-y-3"
          >
            {subs.map((sub) => (
              <motion.div
                key={sub.id}
                variants={fadeInUp}
                className="rounded-xl border border-border bg-surface overflow-hidden"
              >
                {/* Subscription row */}
                <div className="px-5 py-4 flex items-start gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-2 flex-wrap">
                      <span
                        className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${STATUS_CLS[sub.status]}`}
                      >
                        {sub.status === "PENDING_VERIFICATION"
                          ? "Pending verification"
                          : sub.status.charAt(0) +
                            sub.status.slice(1).toLowerCase()}
                      </span>
                    </div>
                    <p className="text-sm font-mono text-foreground break-all mb-2">
                      {sub.url}
                    </p>
                    <div className="flex flex-wrap gap-1">
                      {sub.events.map((evt) => (
                        <span
                          key={evt}
                          className="text-xs border border-border bg-surface rounded-full px-2 py-0.5 text-muted-foreground font-mono"
                        >
                          {EVENT_LABELS[evt]}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5 shrink-0 flex-wrap justify-end">
                    {sub.status === "PENDING_VERIFICATION" && (
                      <button
                        type="button"
                        title="Re-send verification challenge"
                        onClick={() => verifyMutation.mutate(sub.id)}
                        disabled={verifyMutation.isPending}
                        className="p-2 rounded-xl border border-primary/40 bg-primary/10 text-link hover:bg-primary/10 transition-colors disabled:opacity-50"
                      >
                        <ShieldCheck className="w-3.5 h-3.5" />
                      </button>
                    )}
                    {sub.status !== "DISABLED" && (
                      <button
                        type="button"
                        title="Disable (pause deliveries without deleting)"
                        onClick={() => {
                          if (
                            window.confirm(
                              "Disable this endpoint? Deliveries will pause until it is re-verified."
                            )
                          ) {
                            disableMutation.mutate(sub.id);
                          }
                        }}
                        disabled={disableMutation.isPending}
                        className="p-2 rounded-xl border border-border text-muted-foreground hover:text-warning hover:border-warning/40 transition-colors disabled:opacity-50"
                      >
                        <PauseCircle className="w-3.5 h-3.5" />
                      </button>
                    )}
                    <button
                      type="button"
                      title="View delivery log"
                      onClick={() => toggleDeliveries(sub.id)}
                      className="p-2 rounded-xl border border-border text-muted-foreground hover:text-foreground hover:border-input transition-colors"
                    >
                      {expandedDeliveries === sub.id ? (
                        <ChevronDown className="w-3.5 h-3.5" />
                      ) : (
                        <ChevronRight className="w-3.5 h-3.5" />
                      )}
                    </button>
                    <button
                      type="button"
                      title="Remove endpoint"
                      onClick={() => {
                        if (window.confirm("Remove this webhook endpoint?")) {
                          removeMutation.mutate(sub.id);
                        }
                      }}
                      disabled={removeMutation.isPending}
                      className="p-2 rounded-xl border border-border text-muted-foreground hover:text-danger hover:border-danger/40 transition-colors disabled:opacity-50"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Delivery log panel */}
                <AnimatePresence>
                  {expandedDeliveries === sub.id && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="overflow-hidden border-t border-border"
                    >
                      <div className="px-5 py-4 bg-surface">
                        <p className="text-xs font-semibold text-muted-foreground mb-3">
                          Delivery Log (last 7 days)
                        </p>
                        {deliveriesQuery.isLoading && (
                          <p className="text-xs text-muted-foreground">
                            Loading…
                          </p>
                        )}
                        {!deliveriesQuery.isLoading &&
                          (deliveriesQuery.data?.items ?? []).length === 0 && (
                            <p className="text-xs text-muted-foreground">
                              No deliveries in the last 7 days.
                            </p>
                          )}
                        {!deliveriesQuery.isLoading &&
                          (deliveriesQuery.data?.items ?? []).length > 0 && (
                            <div className="space-y-2">
                              {(
                                deliveriesQuery.data!
                                  .items as WebhookDeliveryLogResponse[]
                              ).map((log) => (
                                <div
                                  key={log.id}
                                  className="flex items-center gap-3 text-xs text-muted-foreground font-mono"
                                >
                                  <span
                                    className={`px-2 py-0.5 rounded-full border text-xs shrink-0 ${DELIVERY_CLS[log.status]}`}
                                  >
                                    {log.status}
                                  </span>
                                  <span className="text-muted-foreground">
                                    {EVENT_LABELS[log.eventType]}
                                  </span>
                                  <span className="text-muted-foreground">
                                    HTTP {log.responseStatus ?? "—"}
                                  </span>
                                  <span className="text-muted-foreground">
                                    {log.attemptCount} attempt
                                    {log.attemptCount !== 1 ? "s" : ""}
                                  </span>
                                  <span className="text-muted-foreground ml-auto shrink-0">
                                    {log.deliveredAt
                                      ? new Date(
                                          log.deliveredAt
                                        ).toLocaleString(undefined, {
                                          month: "short",
                                          day: "numeric",
                                          hour: "2-digit",
                                          minute: "2-digit",
                                        })
                                      : new Date(log.createdAt).toLocaleString(
                                          undefined,
                                          {
                                            month: "short",
                                            day: "numeric",
                                            hour: "2-digit",
                                            minute: "2-digit",
                                          }
                                        )}
                                  </span>
                                </div>
                              ))}
                            </div>
                          )}

                        {/* Delivery pagination */}
                        {(deliveriesQuery.data?.totalPages ?? 0) > 1 && (
                          <div className="flex gap-2 mt-4">
                            <button
                              type="button"
                              disabled={deliveryPage === 0}
                              onClick={() => setDeliveryPage((p) => p - 1)}
                              className="text-xs border border-border rounded-full px-3 py-1 text-muted-foreground hover:bg-muted disabled:opacity-30 transition-colors"
                            >
                              Previous
                            </button>
                            <button
                              type="button"
                              disabled={!deliveriesQuery.data?.hasNext}
                              onClick={() => setDeliveryPage((p) => p + 1)}
                              className="text-xs border border-border rounded-full px-3 py-1 text-muted-foreground hover:bg-muted disabled:opacity-30 transition-colors"
                            >
                              Next
                            </button>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </motion.div>
            ))}
          </motion.div>
        )}
      </div>
    </div>
  );
}
