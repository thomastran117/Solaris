import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, CheckCircle, Loader2, X } from "lucide-react";
import { companyOrdersApi } from "../../api/companyOrders";
import { DELIVERY_WINDOWS, type CompanyOrder } from "../../types/order";
import {
  markSlotUnavailableSchema,
  type MarkSlotUnavailableValues,
} from "../../schemas/order";
import DeliverySlotStatusBadge from "./DeliverySlotStatusBadge";

interface Props {
  order: CompanyOrder;
  companyId: string;
  onRefresh?: () => void;
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "apiMessage" in err) {
    const msg = (err as { apiMessage?: string }).apiMessage;
    if (msg) return msg;
  }
  return fallback;
}

export default function DeliverySlotVendorPanel({
  order,
  companyId,
  onRefresh,
}: Props) {
  const qc = useQueryClient();
  const [showUnavailableModal, setShowUnavailableModal] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<MarkSlotUnavailableValues>({
    resolver: zodResolver(markSlotUnavailableSchema),
    defaultValues: { reason: "" },
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["company-orders", companyId] });
    qc.invalidateQueries({
      queryKey: ["company-order", companyId, order.orderId],
    });
    onRefresh?.();
  };

  const confirm = useMutation({
    mutationFn: () =>
      companyOrdersApi.confirmDeliverySlot(companyId, order.orderId),
    onSuccess: invalidate,
    onError: (err) =>
      setError(extractErrorMessage(err, "Failed to confirm slot")),
  });

  const markUnavailable = useMutation({
    mutationFn: (reason?: string) =>
      companyOrdersApi.markDeliverySlotUnavailable(companyId, order.orderId, {
        reason: reason?.trim() || undefined,
      }),
    onSuccess: () => {
      setShowUnavailableModal(false);
      reset({ reason: "" });
      invalidate();
    },
    onError: (err) =>
      setError(extractErrorMessage(err, "Failed to update slot")),
  });

  const openUnavailableModal = () => {
    setError(null);
    reset({ reason: "" });
    setShowUnavailableModal(true);
  };

  if (!order.deliverySlotStatus) return null;

  const canConfirm = order.deliverySlotStatus === "REQUESTED";
  // A vendor can still flag a previously-confirmed slot as unavailable if plans change.
  const canMarkUnavailable = order.deliverySlotStatus !== "UNAVAILABLE";

  return (
    <div className="rounded-xl border border-border bg-surface p-5 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs font-semibold text-link">
          Requested Delivery Slot
        </p>
        <DeliverySlotStatusBadge status={order.deliverySlotStatus} />
      </div>

      <div className="flex items-start gap-2">
        <CalendarClock className="h-4 w-4 text-link mt-0.5 shrink-0" />
        <p className="text-sm text-foreground">
          {order.preferredDeliveryDate
            ? new Date(
                order.preferredDeliveryDate + "T00:00:00"
              ).toLocaleDateString(undefined, {
                weekday: "long",
                day: "numeric",
                month: "long",
              })
            : "No date set"}
          {order.preferredDeliveryWindow && (
            <span className="text-muted-foreground">
              {" "}
              · {DELIVERY_WINDOWS[order.preferredDeliveryWindow]}
            </span>
          )}
        </p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      {(canConfirm || canMarkUnavailable) && (
        <div className="flex flex-wrap items-center gap-3">
          {canConfirm && (
            <button
              onClick={() => {
                setError(null);
                confirm.mutate();
              }}
              disabled={confirm.isPending}
              className="flex items-center gap-2 rounded-full bg-primary hover:bg-primary-hover disabled:opacity-50 px-4 py-2 text-sm text-primary-foreground font-medium transition"
            >
              {confirm.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle className="h-4 w-4" />
              )}
              Confirm Slot
            </button>
          )}
          {canMarkUnavailable && (
            <button
              onClick={openUnavailableModal}
              className="flex items-center gap-2 rounded-full border border-input hover:bg-muted px-4 py-2 text-sm text-muted-foreground hover:text-foreground transition"
            >
              <X className="h-4 w-4" /> Mark Unavailable
            </button>
          )}
        </div>
      )}

      {showUnavailableModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-overlay">
          <form
            onSubmit={handleSubmit((values) => {
              setError(null);
              markUnavailable.mutate(values.reason);
            })}
            className="rounded-xl border border-border bg-surface-raised p-6 w-full max-w-sm space-y-4 shadow-lg"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-foreground">
                Mark slot unavailable?
              </h3>
              <button
                type="button"
                onClick={() => setShowUnavailableModal(false)}
                className="text-muted-foreground hover:text-foreground transition"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <p className="text-sm text-muted-foreground">
              The customer will be emailed that you can't make their requested
              slot.
            </p>
            <textarea
              className="w-full rounded-xl bg-surface border border-border px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring"
              placeholder="Reason (optional)"
              rows={3}
              maxLength={500}
              {...register("reason")}
            />
            {errors.reason && (
              <p className="text-sm text-danger">{errors.reason.message}</p>
            )}
            {error && <p className="text-sm text-danger">{error}</p>}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowUnavailableModal(false)}
                className="flex-1 rounded-full border border-input py-2 text-sm text-muted-foreground hover:bg-muted transition"
              >
                Go back
              </button>
              <button
                type="submit"
                disabled={markUnavailable.isPending}
                className="flex-1 rounded-full bg-danger hover:bg-danger-hover disabled:opacity-50 py-2 text-sm text-danger-foreground font-medium transition"
              >
                {markUnavailable.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin mx-auto" />
                ) : (
                  "Confirm"
                )}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
