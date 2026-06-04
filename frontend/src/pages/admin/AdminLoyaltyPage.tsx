import { useState } from "react";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ShieldAlert, Plus, Pencil, Gift } from "lucide-react";
import type { RootState } from "../../stores";
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";
import SectionGlow from "../../components/section/SectionGlow";
import { useCompanyCapabilities } from "../../hooks/useCompanyRole";
import { useAnims } from "../../hooks/useAnims";
import {
  loyaltyApi,
  type LoyaltyTier,
  type CreateLoyaltyPolicyRequest,
  type CreateLoyaltyTierRequest,
  type IssueBonusRequest,
} from "../../api/loyalty";


const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20";
const labelCls = "block text-xs font-medium text-white/60 mb-1";
const sectionCls =
  "relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm";

export default function AdminLoyaltyPage() {
  const { fadeInUp, stagger } = useAnims();
  const queryClient = useQueryClient();
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const { can, isLoading: roleLoading } = useCompanyCapabilities(companyId);

  const [editingTier, setEditingTier] = useState<LoyaltyTier | null>(null);
  const [policyError, setPolicyError] = useState<string | null>(null);
  const [policySuccess, setPolicySuccess] = useState(false);
  const [tierError, setTierError] = useState<string | null>(null);
  const [tierSuccess, setTierSuccess] = useState(false);
  const [bonusError, setBonusError] = useState<string | null>(null);
  const [bonusSuccess, setBonusSuccess] = useState(false);

  // ── Policy form state ──────────────────────────────────────────────────────
  const [policyForm, setPolicyForm] = useState<CreateLoyaltyPolicyRequest>({
    name: "",
    earnRatePerDollar: 1,
    pointValueCents: 1,
    minRedemptionPoints: 100,
    pointsExpiryDays: undefined,
    birthdayBonusPoints: 0,
    birthdayBonusCreditCents: 0,
    cashbackRatePercent: 0,
    earnMode: "POINTS",
    referralBonusPoints: 0,
    streakBonusThreshold: 3,
    streakBonusPoints: 0,
  });

  // ── Tier form state ────────────────────────────────────────────────────────
  const emptyTierForm: CreateLoyaltyTierRequest = {
    name: "",
    minPoints: 0,
    earnMultiplier: 1,
    badgeColor: "",
    displayOrder: 0,
    perksJson: "",
  };
  const [tierForm, setTierForm] = useState<CreateLoyaltyTierRequest>(emptyTierForm);

  // ── Bonus form state ───────────────────────────────────────────────────────
  const [bonusForm, setBonusForm] = useState<IssueBonusRequest>({ userId: "", points: 0, reason: "" });

  // ── Data fetching ──────────────────────────────────────────────────────────
  const { data: policy } = useQuery({
    queryKey: ["loyalty", "policy", companyId],
    queryFn: () => loyaltyApi.getPolicy(companyId!).then((r) => r.data),
    enabled: !!companyId,
    onSuccess: (p) => {
      setPolicyForm({
        name: p.name,
        earnRatePerDollar: p.earnRatePerDollar,
        pointValueCents: p.pointValueCents,
        minRedemptionPoints: p.minRedemptionPoints,
        pointsExpiryDays: p.pointsExpiryDays ?? undefined,
        birthdayBonusPoints: p.birthdayBonusPoints,
        birthdayBonusCreditCents: p.birthdayBonusCreditCents,
        cashbackRatePercent: p.cashbackRatePercent,
        earnMode: p.earnMode,
        referralBonusPoints: p.referralBonusPoints,
        streakBonusThreshold: p.streakBonusThreshold,
        streakBonusPoints: p.streakBonusPoints,
      });
    },
  } as Parameters<typeof useQuery>[0]);

  const { data: tiers = [] } = useQuery({
    queryKey: ["loyalty", "tiers", companyId],
    queryFn: () => loyaltyApi.listTiers(companyId!).then((r) => r.data),
    enabled: !!companyId,
  });

  // ── Mutations ──────────────────────────────────────────────────────────────
  const policyMutation = useMutation({
    mutationFn: () => loyaltyApi.createOrUpdatePolicy(companyId!, policyForm),
    onSuccess: () => {
      setPolicySuccess(true);
      setPolicyError(null);
      queryClient.invalidateQueries({ queryKey: ["loyalty", "policy", companyId] });
      setTimeout(() => setPolicySuccess(false), 3000);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setPolicyError(msg ?? "Failed to save policy");
    },
  });

  const tierMutation = useMutation({
    mutationFn: () =>
      editingTier
        ? loyaltyApi.updateTier(companyId!, editingTier.id, tierForm)
        : loyaltyApi.createTier(companyId!, tierForm),
    onSuccess: () => {
      setTierSuccess(true);
      setTierError(null);
      setEditingTier(null);
      setTierForm(emptyTierForm);
      queryClient.invalidateQueries({ queryKey: ["loyalty", "tiers", companyId] });
      setTimeout(() => setTierSuccess(false), 3000);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setTierError(msg ?? "Failed to save tier");
    },
  });

  const bonusMutation = useMutation({
    mutationFn: () => loyaltyApi.issueBonus(companyId!, bonusForm),
    onSuccess: () => {
      setBonusSuccess(true);
      setBonusError(null);
      setBonusForm({ userId: "", points: 0, reason: "" });
      setTimeout(() => setBonusSuccess(false), 3000);
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setBonusError(msg ?? "Failed to issue bonus");
    },
  });

  // ── Auth guards ────────────────────────────────────────────────────────────
  if (!companyId) {
    return (
      <div className="relative min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <NavyGridGlowBackground />
        <p className="text-white/60 text-sm">No company selected.</p>
      </div>
    );
  }

  if (!roleLoading && !can("MANAGE_PROMOTIONS")) {
    return (
      <div className="relative min-h-screen bg-slate-950 text-white flex items-center justify-center">
        <NavyGridGlowBackground />
        <div className="flex flex-col items-center gap-3 text-center">
          <ShieldAlert className="h-10 w-10 text-white/30" />
          <p className="text-white/60 text-sm">You don't have permission to manage loyalty settings.</p>
        </div>
      </div>
    );
  }

  const startEditTier = (tier: LoyaltyTier) => {
    setEditingTier(tier);
    setTierForm({
      name: tier.name,
      minPoints: tier.minPoints,
      earnMultiplier: tier.earnMultiplier,
      badgeColor: tier.badgeColor ?? "",
      displayOrder: tier.displayOrder,
      perksJson: tier.perksJson ?? "",
    });
  };

  const cancelEditTier = () => {
    setEditingTier(null);
    setTierForm(emptyTierForm);
    setTierError(null);
  };

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      <div className="mx-auto max-w-3xl px-4 pb-24 pt-28">
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-10">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">Admin</p>
          <h1 className="text-3xl md:text-4xl font-extrabold text-white">Loyalty Management</h1>
          <p className="text-sm text-white/60 mt-2">Configure your loyalty program, tiers, and issue bonus points.</p>
        </motion.div>

        <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-8">

          {/* ── Policy settings ───────────────────────────────────────────── */}
          <motion.section variants={fadeInUp} className={sectionCls}>
            <SectionGlow variant="a" />
            <h2 className="text-lg font-semibold text-white mb-5">Program Policy</h2>
            {!policy && (
              <p className="text-xs text-white/50 mb-4">No active policy yet. Create one below.</p>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <label className={labelCls}>Policy name</label>
                <input className={inputCls} value={policyForm.name} onChange={(e) => setPolicyForm({ ...policyForm, name: e.target.value })} placeholder="e.g. Standard Rewards" />
              </div>
              <div>
                <label className={labelCls}>Earn rate (pts / $1)</label>
                <input className={inputCls} type="number" min="0.01" step="0.01" value={policyForm.earnRatePerDollar} onChange={(e) => setPolicyForm({ ...policyForm, earnRatePerDollar: parseFloat(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Point value (cents)</label>
                <input className={inputCls} type="number" min="1" value={policyForm.pointValueCents} onChange={(e) => setPolicyForm({ ...policyForm, pointValueCents: parseInt(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Min redemption (pts)</label>
                <input className={inputCls} type="number" min="0" value={policyForm.minRedemptionPoints} onChange={(e) => setPolicyForm({ ...policyForm, minRedemptionPoints: parseInt(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Points expiry (days, blank = never)</label>
                <input className={inputCls} type="number" min="1" value={policyForm.pointsExpiryDays ?? ""} onChange={(e) => setPolicyForm({ ...policyForm, pointsExpiryDays: e.target.value ? parseInt(e.target.value) : undefined })} placeholder="Never" />
              </div>
              <div>
                <label className={labelCls}>Earn mode</label>
                <select className={inputCls} value={policyForm.earnMode} onChange={(e) => setPolicyForm({ ...policyForm, earnMode: e.target.value as "POINTS" | "CASHBACK" | "BOTH" })}>
                  <option value="POINTS">Points only</option>
                  <option value="CASHBACK">Cashback only</option>
                  <option value="BOTH">Both</option>
                </select>
              </div>
              <div>
                <label className={labelCls}>Cashback rate (%)</label>
                <input className={inputCls} type="number" min="0" step="0.01" value={policyForm.cashbackRatePercent} onChange={(e) => setPolicyForm({ ...policyForm, cashbackRatePercent: parseFloat(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Birthday bonus (pts)</label>
                <input className={inputCls} type="number" min="0" value={policyForm.birthdayBonusPoints} onChange={(e) => setPolicyForm({ ...policyForm, birthdayBonusPoints: parseInt(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Birthday credit (cents)</label>
                <input className={inputCls} type="number" min="0" value={policyForm.birthdayBonusCreditCents} onChange={(e) => setPolicyForm({ ...policyForm, birthdayBonusCreditCents: parseInt(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Referral bonus (pts to referrer)</label>
                <input className={inputCls} type="number" min="0" value={policyForm.referralBonusPoints} onChange={(e) => setPolicyForm({ ...policyForm, referralBonusPoints: parseInt(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Streak threshold (months)</label>
                <input className={inputCls} type="number" min="1" value={policyForm.streakBonusThreshold} onChange={(e) => setPolicyForm({ ...policyForm, streakBonusThreshold: parseInt(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Streak bonus (pts)</label>
                <input className={inputCls} type="number" min="0" value={policyForm.streakBonusPoints} onChange={(e) => setPolicyForm({ ...policyForm, streakBonusPoints: parseInt(e.target.value) })} />
              </div>
            </div>
            {policyError && <p className="text-xs text-red-400 mt-3">{policyError}</p>}
            {policySuccess && <p className="text-xs text-green-400 mt-3">Policy saved.</p>}
            <button
              onClick={() => policyMutation.mutate()}
              disabled={!policyForm.name || policyMutation.isPending}
              className="mt-5 rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-6 py-2 text-sm font-semibold text-white transition-colors"
            >
              {policyMutation.isPending ? "Saving…" : policy ? "Update Policy" : "Create Policy"}
            </button>
          </motion.section>

          {/* ── Tiers ─────────────────────────────────────────────────────── */}
          <motion.section variants={fadeInUp} className={sectionCls}>
            <SectionGlow variant="b" />
            <h2 className="text-lg font-semibold text-white mb-5">Tiers</h2>

            {tiers.length > 0 && (
              <ul className="space-y-2 mb-5">
                {[...tiers].sort((a, b) => a.minPoints - b.minPoints).map((tier) => (
                  <li key={tier.id} className="flex items-center justify-between rounded-xl border border-white/10 bg-white/[0.04] px-4 py-3">
                    <div>
                      <span className="text-sm font-semibold text-white">{tier.name}</span>
                      <span className="text-xs text-white/50 ml-3">{tier.minPoints.toLocaleString()} pts · {tier.earnMultiplier}×</span>
                    </div>
                    <button
                      onClick={() => startEditTier(tier)}
                      className="flex items-center gap-1 text-xs text-white/60 hover:text-white transition-colors"
                    >
                      <Pencil className="h-3.5 w-3.5" /> Edit
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <div className="border-t border-white/[0.06] pt-5">
              <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
                {editingTier ? (
                  <><Pencil className="h-4 w-4 text-sky-300" /> Edit "{editingTier.name}"</>
                ) : (
                  <><Plus className="h-4 w-4 text-sky-300" /> Create Tier</>
                )}
              </h3>
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className={labelCls}>Tier name</label>
                  <input className={inputCls} value={tierForm.name} onChange={(e) => setTierForm({ ...tierForm, name: e.target.value })} placeholder="e.g. Gold" />
                </div>
                <div>
                  <label className={labelCls}>Min lifetime pts</label>
                  <input className={inputCls} type="number" min="0" value={tierForm.minPoints} onChange={(e) => setTierForm({ ...tierForm, minPoints: parseInt(e.target.value) })} />
                </div>
                <div>
                  <label className={labelCls}>Earn multiplier</label>
                  <input className={inputCls} type="number" min="0.1" step="0.1" value={tierForm.earnMultiplier} onChange={(e) => setTierForm({ ...tierForm, earnMultiplier: parseFloat(e.target.value) })} />
                </div>
                <div>
                  <label className={labelCls}>Badge color</label>
                  <input className={inputCls} value={tierForm.badgeColor ?? ""} onChange={(e) => setTierForm({ ...tierForm, badgeColor: e.target.value })} placeholder="e.g. gold" />
                </div>
                <div>
                  <label className={labelCls}>Display order</label>
                  <input className={inputCls} type="number" min="0" value={tierForm.displayOrder} onChange={(e) => setTierForm({ ...tierForm, displayOrder: parseInt(e.target.value) })} />
                </div>
                <div className="sm:col-span-2">
                  <label className={labelCls}>Perks (JSON array of strings)</label>
                  <textarea
                    className={`${inputCls} resize-none`}
                    rows={2}
                    value={tierForm.perksJson ?? ""}
                    onChange={(e) => setTierForm({ ...tierForm, perksJson: e.target.value })}
                    placeholder='["Free shipping", "Early access"]'
                  />
                </div>
              </div>
              {tierError && <p className="text-xs text-red-400 mt-3">{tierError}</p>}
              {tierSuccess && <p className="text-xs text-green-400 mt-3">Tier saved.</p>}
              <div className="flex gap-3 mt-4">
                <button
                  onClick={() => tierMutation.mutate()}
                  disabled={!tierForm.name || tierMutation.isPending}
                  className="rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-5 py-2 text-sm font-semibold text-white transition-colors"
                >
                  {tierMutation.isPending ? "Saving…" : editingTier ? "Save Changes" : "Create Tier"}
                </button>
                {editingTier && (
                  <button onClick={cancelEditTier} className="rounded-full border border-white/20 px-5 py-2 text-sm font-medium text-white/70 hover:bg-white/10 transition-colors">
                    Cancel
                  </button>
                )}
              </div>
            </div>
          </motion.section>

          {/* ── Issue bonus ───────────────────────────────────────────────── */}
          <motion.section variants={fadeInUp} className={sectionCls}>
            <SectionGlow variant="c" />
            <div className="flex items-center gap-3 mb-5">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-blue-500/15 border border-white/10">
                <Gift className="h-4 w-4 text-sky-200" />
              </div>
              <h2 className="text-lg font-semibold text-white">Issue Bonus Points</h2>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <label className={labelCls}>User ID (UUID)</label>
                <input className={inputCls} value={bonusForm.userId} onChange={(e) => setBonusForm({ ...bonusForm, userId: e.target.value })} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
              </div>
              <div>
                <label className={labelCls}>Points</label>
                <input className={inputCls} type="number" min="1" value={bonusForm.points || ""} onChange={(e) => setBonusForm({ ...bonusForm, points: parseInt(e.target.value) })} />
              </div>
              <div>
                <label className={labelCls}>Reason (optional)</label>
                <input className={inputCls} value={bonusForm.reason ?? ""} onChange={(e) => setBonusForm({ ...bonusForm, reason: e.target.value })} placeholder="e.g. VIP event reward" />
              </div>
            </div>
            {bonusError && <p className="text-xs text-red-400 mt-3">{bonusError}</p>}
            {bonusSuccess && <p className="text-xs text-green-400 mt-3">Bonus issued successfully.</p>}
            <button
              onClick={() => bonusMutation.mutate()}
              disabled={!bonusForm.userId || bonusForm.points < 1 || bonusMutation.isPending}
              className="mt-5 rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-6 py-2 text-sm font-semibold text-white transition-colors"
            >
              {bonusMutation.isPending ? "Issuing…" : "Issue Bonus"}
            </button>
          </motion.section>

        </motion.div>
      </div>
    </div>
  );
}
