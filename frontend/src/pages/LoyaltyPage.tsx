import { useState } from "react";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Star,
  Flame,
  Copy,
  Check,
  Gift,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  TrendingUp,
  ShoppingBag,
  Cake,
  RotateCcw,
  MinusCircle,
  Zap,
  Users,
} from "lucide-react";
import type { RootState } from "../stores";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionGlow from "../components/section/SectionGlow";
import SectionFade from "../components/section/SectionFade";
import SectionTitle from "../components/section/SectionTitle";
import { loyaltyApi, type LoyaltyTransaction, type LoyaltyTier } from "../api/loyalty";
import { useAnims } from "../hooks/useAnims";


function txLabel(type: LoyaltyTransaction["type"]): string {
  switch (type) {
    case "EARN_ORDER":    return "Earned from order";
    case "EARN_BONUS":    return "Bonus points";
    case "EARN_BIRTHDAY": return "Birthday reward";
    case "EARN_REFERRAL": return "Referral bonus";
    case "EARN_STREAK":   return "Streak bonus";
    case "REDEEM_ORDER":  return "Redeemed on order";
    case "RESTORE_ORDER": return "Points restored";
    case "EARN_REVERSAL": return "Points reversed";
    case "CONVERT_TO_CREDIT": return "Converted to credit";
    case "EXPIRE":        return "Points expired";
    case "ADJUST":        return "Manual adjustment";
  }
}

function TxIcon({ type }: { type: LoyaltyTransaction["type"] }) {
  const cls = "h-4 w-4";
  switch (type) {
    case "EARN_ORDER":    return <ShoppingBag className={cls} />;
    case "EARN_BONUS":    return <Gift className={cls} />;
    case "EARN_BIRTHDAY": return <Cake className={cls} />;
    case "EARN_REFERRAL": return <Users className={cls} />;
    case "EARN_STREAK":   return <Flame className={cls} />;
    case "REDEEM_ORDER":  return <MinusCircle className={cls} />;
    case "RESTORE_ORDER": return <RotateCcw className={cls} />;
    case "EARN_REVERSAL": return <RotateCcw className={cls} />;
    case "CONVERT_TO_CREDIT": return <Zap className={cls} />;
    case "EXPIRE":        return <AlertTriangle className={cls} />;
    case "ADJUST":        return <TrendingUp className={cls} />;
  }
}

function tierProgressPct(lifetimePoints: number, current: LoyaltyTier | undefined, next: LoyaltyTier | undefined): number {
  if (!next) return 100;
  const base = current ? current.minPoints : 0;
  const range = next.minPoints - base;
  if (range <= 0) return 100;
  return Math.min(100, Math.round(((lifetimePoints - base) / range) * 100));
}

export default function LoyaltyPage() {
  const navigate = useNavigate();
  const { fadeInUp, stagger } = useAnims();
  const queryClient = useQueryClient();

  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const companyId = useSelector((s: RootState) => s.marketplace.currentMarketplace?.companyId);

  const [page, setPage] = useState(0);
  const [copied, setCopied] = useState(false);
  const [referralInput, setReferralInput] = useState("");
  const [referralError, setReferralError] = useState<string | null>(null);
  const [referralSuccess, setReferralSuccess] = useState(false);

  const enabled = !!accessToken && !!companyId;

  const { data: account } = useQuery({
    queryKey: ["loyalty", "account", companyId],
    queryFn: () => loyaltyApi.getAccount(companyId!).then((r) => r.data),
    enabled,
  });

  const { data: tiers = [] } = useQuery({
    queryKey: ["loyalty", "tiers", companyId],
    queryFn: () => loyaltyApi.listTiers(companyId!).then((r) => r.data),
    enabled,
  });

  const { data: txPage } = useQuery({
    queryKey: ["loyalty", "transactions", companyId, page],
    queryFn: () => loyaltyApi.getTransactions(companyId!, page, 10).then((r) => r.data),
    enabled,
  });

  const { data: expiry } = useQuery({
    queryKey: ["loyalty", "expiry", companyId],
    queryFn: () => loyaltyApi.getExpiryWarning(companyId!).then((r) => r.data),
    enabled,
  });

  const { data: referral } = useQuery({
    queryKey: ["loyalty", "referral", companyId],
    queryFn: () => loyaltyApi.getReferralInfo(companyId!).then((r) => r.data),
    enabled,
  });

  const applyMutation = useMutation({
    mutationFn: (code: string) => loyaltyApi.applyReferralCode(companyId!, code),
    onSuccess: () => {
      setReferralSuccess(true);
      setReferralError(null);
      setReferralInput("");
      queryClient.invalidateQueries({ queryKey: ["loyalty", "account", companyId] });
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setReferralError(msg ?? "Failed to apply referral code");
    },
  });

  if (!accessToken) {
    return (
      <div className="relative min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <NavyGridGlowBackground />
        <div className="text-center">
          <p className="text-white/70 mb-4">Sign in to view your loyalty rewards.</p>
          <button onClick={() => navigate("/login")} className="rounded-full bg-blue-600 hover:bg-blue-500 px-6 py-2 text-sm font-semibold text-white transition-colors">
            Sign in
          </button>
        </div>
      </div>
    );
  }

  if (!companyId) {
    return (
      <div className="relative min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <NavyGridGlowBackground />
        <p className="text-white/60 text-sm">Browse a store to see your loyalty rewards.</p>
      </div>
    );
  }

  const sortedTiers = [...tiers].sort((a, b) => a.minPoints - b.minPoints);
  const currentTier = sortedTiers.findLast((t) => (account?.lifetimePoints ?? 0) >= t.minPoints);
  const nextTier = currentTier ? sortedTiers.find((t) => t.minPoints > (currentTier?.minPoints ?? 0)) : sortedTiers[0];
  const progressPct = tierProgressPct(account?.lifetimePoints ?? 0, currentTier, nextTier);

  const transactions: LoyaltyTransaction[] = Array.isArray(txPage) ? txPage : ((txPage as { content?: LoyaltyTransaction[] })?.content ?? []);
  const totalPages: number = (txPage as { totalPages?: number })?.totalPages ?? 1;

  const handleCopy = () => {
    if (referral?.referralCode) {
      navigator.clipboard.writeText(referral.referralCode).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    }
  };

  const handleApplyReferral = () => {
    if (!referralInput.trim()) return;
    setReferralError(null);
    applyMutation.mutate(referralInput.trim().toUpperCase());
  };

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      <div className="mx-auto max-w-4xl px-4 pb-24 pt-28">
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-12">
          <SectionTitle
            kicker="Your Rewards"
            title="Loyalty & Points"
            subtitle="Track your points, tier progress, and referral rewards."
            theme="dark"
          />
        </motion.div>

        <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-6">

          {/* ── Expiry warning ────────────────────────────────────────────── */}
          {expiry && expiry.pointsExpiringSoon > 0 && (
            <motion.section
              variants={fadeInUp}
              className="relative rounded-2xl border border-yellow-500/40 bg-yellow-500/[0.07] backdrop-blur p-5 shadow-sm flex items-start gap-3"
            >
              <AlertTriangle className="h-5 w-5 text-yellow-400 shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-medium text-white">
                  {expiry.pointsExpiringSoon.toLocaleString()} pts expiring soon
                </p>
                {expiry.nextExpiryAt && (
                  <p className="text-xs text-white/60 mt-0.5">
                    Expires on {new Date(expiry.nextExpiryAt).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })}
                  </p>
                )}
              </div>
            </motion.section>
          )}

          {/* ── Points overview ───────────────────────────────────────────── */}
          <motion.section
            variants={fadeInUp}
            className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm"
          >
            <SectionGlow variant="a" />
            <div className="flex flex-col sm:flex-row sm:items-center gap-6">
              <div className="flex-1">
                <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-1">Points Balance</p>
                <p className="text-5xl font-extrabold text-white tracking-tight">
                  {(account?.pointsBalance ?? 0).toLocaleString()}
                </p>
                <p className="text-sm text-white/50 mt-1">
                  {(account?.lifetimePoints ?? 0).toLocaleString()} lifetime pts
                </p>
              </div>
              <div className="flex flex-col items-start sm:items-end gap-2">
                {account?.currentTierName && (
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-white/20 bg-blue-500/15 px-3 py-1 text-sm font-semibold text-sky-200">
                    <Star className="h-3.5 w-3.5 fill-sky-300 text-sky-300" />
                    {account.currentTierName}
                  </span>
                )}
                {(account?.currentStreak ?? 0) > 0 && (
                  <span className="inline-flex items-center gap-1.5 text-xs font-medium text-orange-300">
                    <Flame className="h-3.5 w-3.5" />
                    {account!.currentStreak}-month streak
                  </span>
                )}
              </div>
            </div>
          </motion.section>

          {/* ── Tier progress ─────────────────────────────────────────────── */}
          {sortedTiers.length > 0 && (
            <motion.section
              variants={fadeInUp}
              className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm"
            >
              <SectionGlow variant="b" />
              <h3 className="text-lg font-semibold text-white mb-4">Tier Progress</h3>
              <div className="flex items-center justify-between text-xs text-white/60 mb-2">
                <span>{currentTier?.name ?? "No tier"}</span>
                {nextTier && <span>{nextTier.name}</span>}
              </div>
              <div className="h-2 w-full rounded-full bg-white/10 overflow-hidden">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-sky-400 to-blue-500 transition-all duration-700"
                  style={{ width: `${progressPct}%` }}
                />
              </div>
              <div className="flex items-center justify-between mt-2 text-xs text-white/50">
                <span>{(account?.lifetimePoints ?? 0).toLocaleString()} pts</span>
                {nextTier ? (
                  <span>{(nextTier.minPoints - (account?.lifetimePoints ?? 0)).toLocaleString()} pts to {nextTier.name}</span>
                ) : (
                  <span>Max tier reached</span>
                )}
              </div>
            </motion.section>
          )}

          {/* ── Referral card ─────────────────────────────────────────────── */}
          {referral && (
            <motion.section
              variants={fadeInUp}
              className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm"
            >
              <SectionGlow variant="c" />
              <div className="flex items-center gap-3 mb-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-500/15 border border-white/10">
                  <Users className="h-5 w-5 text-sky-200" />
                </div>
                <div>
                  <h3 className="text-lg font-semibold text-white">Refer a Friend</h3>
                  <p className="text-sm text-white/60">Share your code and earn bonus points when they shop</p>
                </div>
              </div>

              <div className="flex items-center gap-2 mb-4">
                <div className="flex-1 rounded-xl border border-white/10 bg-white/[0.04] px-4 py-2.5 font-mono text-sm font-semibold text-white tracking-widest">
                  {referral.referralCode}
                </div>
                <button
                  onClick={handleCopy}
                  className="flex items-center gap-1.5 rounded-xl border border-white/20 bg-white/[0.06] px-3 py-2.5 text-sm text-white/80 hover:bg-white/10 transition-colors"
                >
                  {copied ? <Check className="h-4 w-4 text-green-400" /> : <Copy className="h-4 w-4" />}
                  {copied ? "Copied" : "Copy"}
                </button>
              </div>

              <div className="flex gap-6 text-sm text-white/60 mb-5">
                <span><span className="text-white font-semibold">{referral.convertedReferrals}</span> friends joined</span>
                <span><span className="text-white font-semibold">{referral.pointsEarnedFromReferrals.toLocaleString()}</span> pts earned</span>
              </div>

              {!referralSuccess && (
                <div>
                  <p className="text-xs text-white/50 mb-2">Have a friend's referral code?</p>
                  <div className="flex gap-2">
                    <input
                      value={referralInput}
                      onChange={(e) => setReferralInput(e.target.value.toUpperCase())}
                      maxLength={12}
                      placeholder="Enter code…"
                      className="flex-1 rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20"
                    />
                    <button
                      onClick={handleApplyReferral}
                      disabled={!referralInput.trim() || applyMutation.isPending}
                      className="rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-4 py-2 text-sm font-semibold text-white transition-colors"
                    >
                      {applyMutation.isPending ? "Applying…" : "Apply"}
                    </button>
                  </div>
                  {referralError && <p className="text-xs text-red-400 mt-1.5">{referralError}</p>}
                </div>
              )}
              {referralSuccess && (
                <p className="text-sm text-green-400 flex items-center gap-1.5">
                  <Check className="h-4 w-4" /> Referral code applied — earn points when you place your first order!
                </p>
              )}
            </motion.section>
          )}

          {/* ── Tier benefits ─────────────────────────────────────────────── */}
          {sortedTiers.length > 0 && (
            <motion.section variants={fadeInUp}>
              <h3 className="text-lg font-semibold text-white mb-4">Tier Benefits</h3>
              <motion.div variants={stagger} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {sortedTiers.map((tier) => {
                  const isActive = account?.currentTierId === tier.id;
                  let perks: string[] = [];
                  if (tier.perksJson) {
                    try { perks = JSON.parse(tier.perksJson); } catch { perks = [tier.perksJson]; }
                  }
                  return (
                    <motion.div
                      key={tier.id}
                      variants={fadeInUp}
                      whileHover={{ y: -4 }}
                      className={`relative rounded-2xl border backdrop-blur p-5 shadow-sm transition-colors ${
                        isActive
                          ? "border-blue-500/40 bg-blue-500/[0.08]"
                          : "border-white/10 bg-white/[0.06]"
                      }`}
                    >
                      <div className="flex items-center justify-between mb-3">
                        <span className="font-semibold text-white">{tier.name}</span>
                        {isActive && (
                          <span className="text-xs font-semibold text-sky-300 bg-sky-400/10 border border-sky-400/20 rounded-full px-2 py-0.5">Current</span>
                        )}
                      </div>
                      <p className="text-xs text-white/50 mb-1">{tier.minPoints.toLocaleString()} lifetime pts to unlock</p>
                      <p className="text-sm font-medium text-sky-200 mb-3">{tier.earnMultiplier}× earn multiplier</p>
                      {perks.length > 0 && (
                        <ul className="space-y-1">
                          {perks.map((perk, i) => (
                            <li key={i} className="flex items-start gap-1.5 text-xs text-white/65">
                              <Star className="h-3 w-3 text-sky-300 fill-sky-300 shrink-0 mt-0.5" />
                              {perk}
                            </li>
                          ))}
                        </ul>
                      )}
                    </motion.div>
                  );
                })}
              </motion.div>
            </motion.section>
          )}

          {/* ── Transaction history ───────────────────────────────────────── */}
          <motion.section variants={fadeInUp}>
            <SectionFade />
            <h3 className="text-lg font-semibold text-white mb-4">Transaction History</h3>
            {transactions.length === 0 ? (
              <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-8 text-center">
                <p className="text-sm text-white/50">No transactions yet. Start shopping to earn points!</p>
              </div>
            ) : (
              <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm overflow-hidden">
                <ul className="divide-y divide-white/[0.06]">
                  {transactions.map((tx) => {
                    const isEarn = tx.pointsDelta > 0;
                    return (
                      <li key={tx.id} className="flex items-center gap-4 px-5 py-3.5">
                        <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border ${isEarn ? "bg-green-500/10 border-green-500/20 text-green-400" : "bg-red-500/10 border-red-500/20 text-red-400"}`}>
                          <TxIcon type={tx.type} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-white truncate">{txLabel(tx.type)}</p>
                          <p className="text-xs text-white/50">
                            {new Date(tx.createdAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}
                          </p>
                        </div>
                        <span className={`text-sm font-semibold tabular-nums ${isEarn ? "text-green-400" : "text-red-400"}`}>
                          {isEarn ? "+" : ""}{tx.pointsDelta.toLocaleString()} pts
                        </span>
                      </li>
                    );
                  })}
                </ul>

                {totalPages > 1 && (
                  <div className="flex items-center justify-between border-t border-white/[0.06] px-5 py-3">
                    <button
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                      className="flex items-center gap-1 text-sm text-white/60 hover:text-white disabled:opacity-30 transition-colors"
                    >
                      <ChevronLeft className="h-4 w-4" /> Prev
                    </button>
                    <span className="text-xs text-white/40">Page {page + 1} of {totalPages}</span>
                    <button
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1}
                      className="flex items-center gap-1 text-sm text-white/60 hover:text-white disabled:opacity-30 transition-colors"
                    >
                      Next <ChevronRight className="h-4 w-4" />
                    </button>
                  </div>
                )}
              </div>
            )}
          </motion.section>

        </motion.div>
      </div>
    </div>
  );
}
