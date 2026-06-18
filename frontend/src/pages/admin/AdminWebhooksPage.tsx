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
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";
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
  ACTIVE: "bg-green-500/10 border-green-400/30 text-green-300",
  PENDING_VERIFICATION: "bg-yellow-500/10 border-yellow-400/30 text-yellow-300",
  DISABLED: "bg-white/5 border-white/10 text-white/40",
};

const DELIVERY_CLS: Record<WebhookDeliveryStatus, string> = {
  DELIVERED: "bg-green-500/10 border-green-400/30 text-green-300",
  FAILED: "bg-red-500/10 border-red-400/30 text-red-300",
  PENDING: "bg-yellow-500/10 border-yellow-400/30 text-yellow-300",
};

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25";

export default function AdminWebhooksPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [showCreate, setShowCreate] = useState(false);
  const [url, setUrl] = useState("");
  const [selectedEvents, setSelectedEvents] = useState<Set<WebhookEventType>>(new Set());
  const [formError, setFormError] = useState<string | null>(null);
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [expandedDeliveries, setExpandedDeliveries] = useState<string | null>(null);
  const [deliveryPage, setDeliveryPage] = useState(0);
  const [copied, setCopied] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["admin", "webhooks", companyId],
    queryFn: () => webhooksApi.list(companyId!).then((r) => r.data),
    enabled: !!companyId,
  });

  const deliveriesQuery = useQuery({
    queryKey: ["admin", "webhooks", companyId, expandedDeliveries, "deliveries", deliveryPage],
    queryFn: () =>
      webhooksApi.deliveries(companyId!, expandedDeliveries!, deliveryPage).then((r) => r.data),
    enabled: !!companyId && !!expandedDeliveries,
  });

  const registerMutation = useMutation({
    mutationFn: (payload: RegisterWebhookPayload) =>
      webhooksApi.register(companyId!, payload),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ["admin", "webhooks", companyId] });
      setRevealedSecret(res.data.secret);
      setShowCreate(false);
      setUrl("");
      setSelectedEvents(new Set());
      setFormError(null);
    },
    onError: () => setFormError("Failed to register endpoint. Check the URL and try again."),
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => webhooksApi.remove(companyId!, id),
    onSuccess: () => {
      setActionError(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "webhooks", companyId] });
    },
    onError: () => setActionError("Failed to remove the endpoint. Please try again."),
  });

  const verifyMutation = useMutation({
    mutationFn: (id: string) => webhooksApi.verify(companyId!, id),
    onSuccess: () => {
      setActionError(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "webhooks", companyId] });
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
      queryClient.invalidateQueries({ queryKey: ["admin", "webhooks", companyId] });
    },
    onError: () => setActionError("Failed to disable the endpoint. Please try again."),
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
    if (!url.trim()) { setFormError("URL is required."); return; }
    if (selectedEvents.size === 0) { setFormError("Select at least one event type."); return; }
    registerMutation.mutate({ url: url.trim(), events: Array.from(selectedEvents) });
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
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      <div className="relative z-10 max-w-4xl mx-auto px-4 sm:px-6 py-8">

        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-500/15 border border-white/10 flex items-center justify-center">
              <Webhook className="w-4 h-4 text-sky-200" />
            </div>
            <div>
              <h1 className="text-xl font-extrabold text-white">Webhooks</h1>
              <p className="text-xs text-white/50 mt-0.5">
                Receive real-time event notifications at your endpoints
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => { setShowCreate((v) => !v); setFormError(null); }}
            className="inline-flex items-center gap-2 text-sm bg-blue-600 hover:bg-blue-500 rounded-full px-4 py-2 text-white font-medium transition-colors"
          >
            {showCreate ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
            {showCreate ? "Cancel" : "Add Endpoint"}
          </button>
        </div>

        {/* Action error banner (verify / disable / remove failures) */}
        {actionError && (
          <div className="flex items-start justify-between gap-4 rounded-2xl border border-red-400/30 bg-red-500/10 backdrop-blur p-4 mb-6">
            <p className="text-sm text-red-300">{actionError}</p>
            <button
              type="button"
              onClick={() => setActionError(null)}
              className="shrink-0 text-white/40 hover:text-white transition-colors"
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
              className="rounded-2xl border border-yellow-400/30 bg-yellow-500/10 backdrop-blur p-5 mb-6"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-yellow-200 mb-1">
                    Webhook secret — copy it now
                  </p>
                  <p className="text-xs text-white/55 mb-3">
                    This secret will not be shown again. Use it to verify the{" "}
                    <code className="text-white/80">X-ShopWave-Signature</code> header on incoming events.
                  </p>
                  <div className="flex items-center gap-2">
                    <code className="flex-1 text-xs font-mono text-yellow-100 bg-black/20 rounded-lg px-3 py-2 break-all">
                      {revealedSecret}
                    </code>
                    <button
                      type="button"
                      onClick={copySecret}
                      className="shrink-0 p-2 rounded-lg border border-white/10 text-white/60 hover:text-white hover:border-white/25 transition-colors"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                  </div>
                  {copied && <p className="text-xs text-green-400 mt-1">Copied!</p>}
                </div>
                <button
                  type="button"
                  onClick={() => setRevealedSecret(null)}
                  className="shrink-0 text-white/40 hover:text-white transition-colors"
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
            className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 mb-6"
          >
            <h2 className="text-base font-semibold text-white mb-4">New Webhook Endpoint</h2>
            <form onSubmit={handleRegister} className="flex flex-col gap-5">
              <div>
                <label className="text-xs text-white/60 mb-1.5 block">Endpoint URL</label>
                <input
                  className={inputCls}
                  type="url"
                  placeholder="https://your-server.com/webhooks/shopwave"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs text-white/60 mb-2 block">Events to subscribe</label>
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
                        className="w-4 h-4 rounded border border-white/20 accent-blue-500"
                      />
                      <span className="text-sm text-white/70 group-hover:text-white/90 transition-colors font-mono">
                        {EVENT_LABELS[evt]}
                      </span>
                    </label>
                  ))}
                </div>
              </div>
              {formError && <p className="text-xs text-red-400">{formError}</p>}
              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={registerMutation.isPending}
                  className="inline-flex items-center gap-2 text-sm bg-blue-600 hover:bg-blue-500 rounded-full px-5 py-2 text-white font-medium transition-colors disabled:opacity-50"
                >
                  {registerMutation.isPending ? "Registering…" : "Register Endpoint"}
                </button>
              </div>
            </form>
          </motion.div>
        )}

        {/* Loading skeleton */}
        {listQuery.isLoading && (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-20 animate-pulse" />
            ))}
          </div>
        )}

        {/* Empty state */}
        {!listQuery.isLoading && subs.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <Webhook className="w-12 h-12 text-white/20 mb-4" />
            <h3 className="text-lg font-semibold text-white mb-2">No webhook endpoints</h3>
            <p className="text-sm text-white/50">
              Add an endpoint to start receiving real-time event notifications.
            </p>
          </div>
        )}

        {/* Subscription list */}
        {!listQuery.isLoading && subs.length > 0 && (
          <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-3">
            {subs.map((sub) => (
              <motion.div
                key={sub.id}
                variants={fadeInUp}
                className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur overflow-hidden"
              >
                {/* Subscription row */}
                <div className="px-5 py-4 flex items-start gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-2 flex-wrap">
                      <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${STATUS_CLS[sub.status]}`}>
                        {sub.status === "PENDING_VERIFICATION"
                          ? "Pending verification"
                          : sub.status.charAt(0) + sub.status.slice(1).toLowerCase()}
                      </span>
                    </div>
                    <p className="text-sm font-mono text-white/80 break-all mb-2">{sub.url}</p>
                    <div className="flex flex-wrap gap-1">
                      {sub.events.map((evt) => (
                        <span
                          key={evt}
                          className="text-xs border border-white/15 bg-white/[0.04] rounded-full px-2 py-0.5 text-white/55 font-mono"
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
                        className="p-2 rounded-xl border border-blue-500/30 bg-blue-600/10 text-sky-300 hover:bg-blue-600/20 transition-colors disabled:opacity-50"
                      >
                        <ShieldCheck className="w-3.5 h-3.5" />
                      </button>
                    )}
                    {sub.status !== "DISABLED" && (
                      <button
                        type="button"
                        title="Disable (pause deliveries without deleting)"
                        onClick={() => {
                          if (window.confirm("Disable this endpoint? Deliveries will pause until it is re-verified.")) {
                            disableMutation.mutate(sub.id);
                          }
                        }}
                        disabled={disableMutation.isPending}
                        className="p-2 rounded-xl border border-white/10 text-white/50 hover:text-yellow-300 hover:border-yellow-400/30 transition-colors disabled:opacity-50"
                      >
                        <PauseCircle className="w-3.5 h-3.5" />
                      </button>
                    )}
                    <button
                      type="button"
                      title="View delivery log"
                      onClick={() => toggleDeliveries(sub.id)}
                      className="p-2 rounded-xl border border-white/10 text-white/50 hover:text-white hover:border-white/25 transition-colors"
                    >
                      {expandedDeliveries === sub.id
                        ? <ChevronDown className="w-3.5 h-3.5" />
                        : <ChevronRight className="w-3.5 h-3.5" />}
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
                      className="p-2 rounded-xl border border-white/10 text-white/40 hover:text-red-400 hover:border-red-400/30 transition-colors disabled:opacity-50"
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
                      className="overflow-hidden border-t border-white/10"
                    >
                      <div className="px-5 py-4 bg-white/[0.03]">
                        <p className="text-xs font-semibold text-white/50 uppercase tracking-widest mb-3">
                          Delivery Log (last 7 days)
                        </p>
                        {deliveriesQuery.isLoading && (
                          <p className="text-xs text-white/40">Loading…</p>
                        )}
                        {!deliveriesQuery.isLoading &&
                          (deliveriesQuery.data?.items ?? []).length === 0 && (
                            <p className="text-xs text-white/40">No deliveries in the last 7 days.</p>
                          )}
                        {!deliveriesQuery.isLoading &&
                          (deliveriesQuery.data?.items ?? []).length > 0 && (
                            <div className="space-y-2">
                              {(deliveriesQuery.data!.items as WebhookDeliveryLogResponse[]).map((log) => (
                                <div
                                  key={log.id}
                                  className="flex items-center gap-3 text-xs text-white/60 font-mono"
                                >
                                  <span
                                    className={`px-2 py-0.5 rounded-full border text-xs shrink-0 ${DELIVERY_CLS[log.status]}`}
                                  >
                                    {log.status}
                                  </span>
                                  <span className="text-white/70">{EVENT_LABELS[log.eventType]}</span>
                                  <span className="text-white/40">
                                    HTTP {log.responseStatus ?? "—"}
                                  </span>
                                  <span className="text-white/35">
                                    {log.attemptCount} attempt{log.attemptCount !== 1 ? "s" : ""}
                                  </span>
                                  <span className="text-white/30 ml-auto shrink-0">
                                    {log.deliveredAt
                                      ? new Date(log.deliveredAt).toLocaleString(undefined, {
                                          month: "short", day: "numeric",
                                          hour: "2-digit", minute: "2-digit",
                                        })
                                      : new Date(log.createdAt).toLocaleString(undefined, {
                                          month: "short", day: "numeric",
                                          hour: "2-digit", minute: "2-digit",
                                        })}
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
                              className="text-xs border border-white/10 rounded-full px-3 py-1 text-white/50 hover:bg-white/[0.06] disabled:opacity-30 transition-colors"
                            >
                              Previous
                            </button>
                            <button
                              type="button"
                              disabled={!deliveriesQuery.data?.hasNext}
                              onClick={() => setDeliveryPage((p) => p + 1)}
                              className="text-xs border border-white/10 rounded-full px-3 py-1 text-white/50 hover:bg-white/[0.06] disabled:opacity-30 transition-colors"
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
