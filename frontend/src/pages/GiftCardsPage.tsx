import { useState } from "react";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Gift, Search, CheckCircle, XCircle } from "lucide-react";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionGlow from "../components/section/SectionGlow";
import SectionFade from "../components/section/SectionFade";
import SectionTitle from "../components/section/SectionTitle";
import { giftCardsApi } from "../api/giftCards";
import {
  redeemGiftCardSchema,
  checkBalanceSchema,
  type RedeemGiftCardFormValues,
  type CheckBalanceFormValues,
} from "../schemas/giftCard";
import type { GiftCard, GiftCardBalance, GiftCardStatus } from "../types/giftCard";
import type { RootState } from "../stores";
import { useAnims } from "../hooks/useAnims";


function formatCents(cents: number): string {
  return `$${(cents / 100).toFixed(2)}`;
}

function statusBadge(status: GiftCardStatus) {
  const cfg: Record<GiftCardStatus, { label: string; classes: string }> = {
    ACTIVE:        { label: "Active",         classes: "bg-green-500/20 text-green-400 border-green-500/30" },
    PARTIALLY_USED:{ label: "Partially Used", classes: "bg-yellow-500/20 text-yellow-400 border-yellow-500/30" },
    REDEEMED:      { label: "Redeemed",       classes: "bg-white/10 text-white/50 border-white/10" },
    VOID:          { label: "Voided",         classes: "bg-red-500/20 text-red-400 border-red-500/30" },
  };
  const { label, classes } = cfg[status];
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-semibold border ${classes}`}>
      {label}
    </span>
  );
}

function GiftCardListItem({ card }: { card: GiftCard }) {
  const maskedCode = card.code.slice(0, 4) + " **** **** " + card.code.slice(-4);
  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 flex items-start justify-between gap-4">
      <div className="flex items-center gap-3">
        <div className="bg-blue-500/15 border border-white/10 rounded-xl p-3 shrink-0">
          <Gift className="h-5 w-5 text-sky-200" />
        </div>
        <div>
          <p className="text-white font-semibold text-sm font-mono tracking-wider">{maskedCode}</p>
          <p className="text-white/60 text-xs mt-1">
            Original: {formatCents(card.originalValueCents)}
            {card.remainingBalanceCents < card.originalValueCents && (
              <> &bull; Remaining: <span className="text-white/80">{formatCents(card.remainingBalanceCents)}</span></>
            )}
          </p>
        </div>
      </div>
      <div className="shrink-0 flex flex-col items-end gap-1">
        {statusBadge(card.status)}
        <span className="text-white/40 text-xs">
          {new Date(card.createdAt).toLocaleDateString()}
        </span>
      </div>
    </div>
  );
}

function CheckBalanceSection() {
  const { fadeInUp } = useAnims();
  const [result, setResult] = useState<GiftCardBalance | null>(null);
  const [notFound, setNotFound] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CheckBalanceFormValues>({ resolver: zodResolver(checkBalanceSchema) });

  const { mutate, isPending } = useMutation({
    mutationFn: (data: CheckBalanceFormValues) => giftCardsApi.getBalance(data.code).then((r) => r.data),
    onSuccess: (data) => { setResult(data); setNotFound(false); },
    onError: () => { setResult(null); setNotFound(true); },
  });

  return (
    <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 space-y-4">
      <div className="flex items-center gap-3">
        <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2.5">
          <Search className="h-5 w-5 text-sky-200" />
        </div>
        <h3 className="text-lg font-semibold text-white">Check Balance</h3>
      </div>
      <p className="text-white/60 text-sm">Enter a gift card code to see its remaining balance. No account required.</p>

      <form onSubmit={handleSubmit((d) => mutate(d))} className="flex gap-3">
        <div className="flex-1">
          <input
            {...register("code")}
            placeholder="Enter gift card code"
            className="w-full px-4 py-2.5 rounded-xl bg-white/[0.06] border border-white/10 text-white placeholder:text-white/45 text-sm focus:outline-none focus:border-white/30 transition font-mono tracking-wider uppercase"
          />
          {errors.code && <p className="text-red-400 text-xs mt-1">{errors.code.message}</p>}
        </div>
        <button
          type="submit"
          disabled={isPending}
          className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold transition disabled:opacity-50"
        >
          Check
        </button>
      </form>

      {result && (
        <div className="rounded-xl border border-white/10 bg-white/[0.04] p-4 flex items-center justify-between">
          <div>
            <p className="text-white/60 text-xs uppercase tracking-widest font-semibold mb-1">Remaining Balance</p>
            <p className="text-white text-2xl font-extrabold">{formatCents(result.remainingBalanceCents)}</p>
          </div>
          <div className="text-right">
            {statusBadge(result.status)}
          </div>
        </div>
      )}

      {notFound && (
        <div className="flex items-center gap-2 text-red-400 text-sm">
          <XCircle className="h-4 w-4 shrink-0" />
          <span>Gift card not found. Check the code and try again.</span>
        </div>
      )}
    </motion.div>
  );
}

function RedeemSection() {
  const { fadeInUp } = useAnims();
  const queryClient = useQueryClient();
  const [redeemSuccess, setRedeemSuccess] = useState<{ code: string; amountCents: number } | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<RedeemGiftCardFormValues>({ resolver: zodResolver(redeemGiftCardSchema) });

  const { mutate, isPending, error } = useMutation({
    mutationFn: (data: RedeemGiftCardFormValues) =>
      giftCardsApi.redeem(data.code, data.amountCents).then((r) => r.data),
    onSuccess: (_, vars) => {
      setRedeemSuccess({ code: vars.code, amountCents: vars.amountCents });
      queryClient.invalidateQueries({ queryKey: ["gift-cards", "purchased"] });
      reset();
    },
  });

  function extractErrorMessage(err: unknown): string {
    if (err && typeof err === "object" && "response" in err) {
      const resp = (err as { response?: { status?: number } }).response;
      if (resp?.status === 409) return "This gift card has already been fully redeemed.";
      if (resp?.status === 410) return "This gift card has been voided and cannot be redeemed.";
      if (resp?.status === 404) return "Gift card code not found.";
    }
    return "Something went wrong. Please try again.";
  }

  return (
    <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 space-y-4">
      <div className="flex items-center gap-3">
        <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2.5">
          <Gift className="h-5 w-5 text-sky-200" />
        </div>
        <h3 className="text-lg font-semibold text-white">Redeem a Gift Card</h3>
      </div>
      <p className="text-white/60 text-sm">Enter your gift card code and the amount to add to your store credit.</p>

      {redeemSuccess ? (
        <div className="rounded-xl border border-green-500/30 bg-green-500/10 p-4 flex items-start gap-3">
          <CheckCircle className="h-5 w-5 text-green-400 mt-0.5 shrink-0" />
          <div>
            <p className="text-green-400 font-semibold text-sm">Gift card redeemed!</p>
            <p className="text-white/60 text-sm mt-0.5">
              {formatCents(redeemSuccess.amountCents)} has been added to your store credit.
            </p>
          </div>
          <button
            onClick={() => setRedeemSuccess(null)}
            className="ml-auto text-white/40 hover:text-white/70 text-xs transition"
          >
            Dismiss
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit((d) => mutate(d))} className="space-y-3">
          <div>
            <label className="text-white/60 text-xs font-semibold uppercase tracking-wider mb-1.5 block">Gift Card Code</label>
            <input
              {...register("code")}
              placeholder="e.g. ABCD1234EFGH5678"
              className="w-full px-4 py-2.5 rounded-xl bg-white/[0.06] border border-white/10 text-white placeholder:text-white/45 text-sm focus:outline-none focus:border-white/30 transition font-mono tracking-wider uppercase"
            />
            {errors.code && <p className="text-red-400 text-xs mt-1">{errors.code.message}</p>}
          </div>

          <div>
            <label className="text-white/60 text-xs font-semibold uppercase tracking-wider mb-1.5 block">Amount to Redeem (cents)</label>
            <input
              {...register("amountCents", { valueAsNumber: true })}
              type="number"
              min={1}
              placeholder="e.g. 5000 for $50.00"
              className="w-full px-4 py-2.5 rounded-xl bg-white/[0.06] border border-white/10 text-white placeholder:text-white/45 text-sm focus:outline-none focus:border-white/30 transition"
            />
            {errors.amountCents && <p className="text-red-400 text-xs mt-1">{errors.amountCents.message}</p>}
          </div>

          {error && (
            <div className="flex items-center gap-2 text-red-400 text-sm">
              <XCircle className="h-4 w-4 shrink-0" />
              <span>{extractErrorMessage(error)}</span>
            </div>
          )}

          <button
            type="submit"
            disabled={isPending}
            className="w-full py-2.5 rounded-full bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition disabled:opacity-50"
          >
            {isPending ? "Redeeming…" : "Redeem Gift Card"}
          </button>
        </form>
      )}
    </motion.div>
  );
}

export default function GiftCardsPage() {
  const { fadeInUp, stagger } = useAnims();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);

  const [page, setPage] = useState(0);
  const { data: purchased, isLoading } = useQuery({
    queryKey: ["gift-cards", "purchased", page],
    queryFn: () => giftCardsApi.list({ page, size: 20 }).then((r) => r.data),
    enabled: !!accessToken,
  });

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />
      <div className="relative z-10 max-w-2xl mx-auto px-4 py-20">
        <SectionFade top />
        <SectionGlow variant="b" />

        <motion.div initial="hidden" animate="visible" variants={stagger} className="space-y-8">
          {/* Title */}
          <motion.div variants={fadeInUp}>
            <SectionTitle
              kicker="Store"
              title="Gift Cards"
              subtitle="Check balances, redeem codes, and view your purchased gift cards."
            />
          </motion.div>

          {/* Check Balance — public */}
          <CheckBalanceSection />

          {/* Auth-gated sections */}
          {accessToken ? (
            <>
              {/* Redeem */}
              <RedeemSection />

              {/* Purchased gift cards */}
              <motion.div variants={fadeInUp} className="space-y-4">
                <h3 className="text-lg font-semibold text-white">Your Purchased Gift Cards</h3>

                {isLoading ? (
                  <div className="space-y-3">
                    {[1, 2].map((i) => (
                      <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-20 animate-pulse" />
                    ))}
                  </div>
                ) : !purchased?.content?.length ? (
                  <div className="rounded-2xl border border-white/10 bg-white/[0.04] p-8 flex flex-col items-center gap-3 text-center">
                    <div className="bg-blue-500/15 border border-white/10 rounded-2xl p-4">
                      <Gift className="h-7 w-7 text-sky-200" />
                    </div>
                    <p className="text-white/60 text-sm">You haven't purchased any gift cards yet.</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {purchased.content.map((card) => (
                      <motion.div key={card.id} variants={fadeInUp}>
                        <GiftCardListItem card={card} />
                      </motion.div>
                    ))}
                  </div>
                )}

                {purchased && purchased.totalPages > 1 && (
                  <div className="flex justify-center gap-3 pt-2">
                    <button
                      disabled={!purchased.hasPrevious}
                      onClick={() => setPage((p) => p - 1)}
                      className="px-4 py-2 rounded-full border border-white/20 text-sm text-white/70 hover:bg-white/10 disabled:opacity-30 transition"
                    >
                      Previous
                    </button>
                    <span className="text-sm text-white/50 py-2">
                      Page {purchased.page + 1} of {purchased.totalPages}
                    </span>
                    <button
                      disabled={!purchased.hasNext}
                      onClick={() => setPage((p) => p + 1)}
                      className="px-4 py-2 rounded-full border border-white/20 text-sm text-white/70 hover:bg-white/10 disabled:opacity-30 transition"
                    >
                      Next
                    </button>
                  </div>
                )}
              </motion.div>
            </>
          ) : (
            <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.04] p-8 text-center space-y-3">
              <p className="text-white/60 text-sm">Sign in to redeem gift cards and view your purchases.</p>
            </motion.div>
          )}
        </motion.div>
        <SectionFade bottom />
      </div>
    </div>
  );
}
