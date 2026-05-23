import { useState, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import {
  User,
  Star,
  Zap,
  ShoppingCart,
  Heart,
  CheckCircle2,
  XCircle,
  Crown,
} from "lucide-react";
import api from "../api";
import { getPremiumStatus, createCheckoutSession, createPortalSession } from "../api/premium";
import type { RootState } from "../stores";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionGlow from "../components/section/SectionGlow";
import SectionFade from "../components/section/SectionFade";
import SectionTitle from "../components/section/SectionTitle";

// ── Animation helpers ────────────────────────────────────────────────────────
const useAnims = () => {
  const reduced = useReducedMotion();
  const fadeInUp: Variants = reduced
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };
  const stagger: Variants = reduced
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.05 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.1 } } };
  return { fadeInUp, stagger };
};

// ── Benefit definitions ──────────────────────────────────────────────────────
const BENEFITS = [
  {
    icon: Star,
    label: "5% discount on every order",
    freeValue: null,
    premiumValue: "Automatic — no code needed",
  },
  {
    icon: Zap,
    label: "Priority order processing",
    freeValue: null,
    premiumValue: "Orders flagged for faster fulfilment",
  },
  {
    icon: ShoppingCart,
    label: "Items per order",
    freeValue: "Up to 50",
    premiumValue: "Up to 200",
  },
  {
    icon: Heart,
    label: "Saved lists",
    freeValue: "Up to 5",
    premiumValue: "Unlimited",
  },
];

// ── Profile types ────────────────────────────────────────────────────────────
interface ProfileResponse {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phoneNumber: string | null;
  address: string | null;
  tier: string;
}

export default function AccountPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const tier = useSelector((s: RootState) => s.auth.tier);
  const { fadeInUp, stagger } = useAnims();

  const [upgradingLoading, setUpgradingLoading] = useState(false);
  const [portalLoading, setPortalLoading] = useState(false);
  const [successBanner, setSuccessBanner] = useState(false);

  // Show success banner when redirected back from Stripe after upgrade
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get("upgrade") === "success") {
      setSuccessBanner(true);
      // Strip query param without navigating
      navigate("/account", { replace: true });
    }
  }, [location.search, navigate]);

  const { data: profile } = useQuery({
    queryKey: ["profile"],
    queryFn: () => api.get<ProfileResponse>("/profile").then((r) => r.data),
  });

  const { data: premiumStatus, refetch: refetchStatus } = useQuery({
    queryKey: ["premium", "status"],
    queryFn: () => getPremiumStatus().then((r) => r.data),
  });

  const isPremium = (premiumStatus?.tier ?? tier) === "PREMIUM";

  const handleUpgrade = async () => {
    setUpgradingLoading(true);
    try {
      const res = await createCheckoutSession();
      window.location.href = res.data.url;
    } catch {
      setUpgradingLoading(false);
    }
  };

  const handleManage = async () => {
    setPortalLoading(true);
    try {
      const res = await createPortalSession();
      window.location.href = res.data.url;
    } catch {
      setPortalLoading(false);
    }
  };

  const renewalDate = premiumStatus?.premiumExpiresAt
    ? new Date(premiumStatus.premiumExpiresAt).toLocaleDateString("en-US", {
        month: "long",
        day: "numeric",
        year: "numeric",
      })
    : null;

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      {/* Success banner */}
      {successBanner && (
        <div className="fixed top-16 left-0 right-0 z-50 flex justify-center px-4">
          <div className="flex items-center gap-3 rounded-2xl border border-green-500/30 bg-green-500/10 backdrop-blur px-5 py-3 shadow-lg">
            <CheckCircle2 className="h-5 w-5 text-green-400 shrink-0" />
            <span className="text-sm text-white/90">
              Welcome to Premium! Your benefits are now active.
            </span>
            <button
              onClick={() => setSuccessBanner(false)}
              className="ml-2 text-white/50 hover:text-white/80 transition-colors"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      <div className="mx-auto max-w-4xl px-4 pb-24 pt-28">
        {/* ── Page header ──────────────────────────────────────────────────── */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-12"
        >
          <SectionTitle
            kicker="Your account"
            title="Account & Plan"
            subtitle="Manage your profile and Premium subscription."
            theme="dark"
          />
        </motion.div>

        <motion.div
          variants={stagger}
          initial="hidden"
          animate="visible"
          className="space-y-6"
        >
          {/* ── Profile card ─────────────────────────────────────────────── */}
          <motion.section
            variants={fadeInUp}
            className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm"
          >
            <SectionGlow variant="a" />
            <div className="flex items-start gap-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-blue-500/15 border border-white/10">
                <User className="h-6 w-6 text-sky-200" />
              </div>
              <div className="min-w-0 flex-1">
                <h3 className="text-lg font-semibold text-white">
                  {profile?.firstName || profile?.lastName
                    ? `${profile.firstName ?? ""} ${profile.lastName ?? ""}`.trim()
                    : "Your Profile"}
                </h3>
                <p className="text-sm text-white/60 mt-0.5">{profile?.email ?? "—"}</p>
                {profile?.phoneNumber && (
                  <p className="text-sm text-white/60">{profile.phoneNumber}</p>
                )}
                {profile?.address && (
                  <p className="text-sm text-white/60 mt-0.5">{profile.address}</p>
                )}
              </div>
            </div>
          </motion.section>

          {/* ── Plan card ────────────────────────────────────────────────── */}
          <motion.section
            variants={fadeInUp}
            className={`relative rounded-2xl border backdrop-blur p-6 shadow-sm transition-colors ${
              isPremium
                ? "border-blue-500/40 bg-blue-500/[0.08]"
                : "border-white/10 bg-white/[0.06]"
            }`}
          >
            <SectionGlow variant="b" />
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
              <div className="flex items-center gap-3">
                <div
                  className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border ${
                    isPremium
                      ? "bg-blue-500/20 border-blue-400/30"
                      : "bg-white/[0.08] border-white/10"
                  }`}
                >
                  <Crown
                    className={`h-6 w-6 ${isPremium ? "text-sky-200" : "text-white/40"}`}
                  />
                </div>
                <div>
                  {isPremium ? (
                    <p className="text-xs uppercase tracking-[0.25em] font-semibold text-transparent bg-clip-text bg-gradient-to-r from-sky-200 to-blue-400">
                      Premium
                    </p>
                  ) : (
                    <p className="text-xs uppercase tracking-[0.25em] font-semibold text-white/40">
                      Free
                    </p>
                  )}
                  <h3 className="text-lg font-semibold text-white">
                    {isPremium ? "ShopWave Premium" : "ShopWave Free"}
                  </h3>
                  {renewalDate && (
                    <p className="text-sm text-white/50 mt-0.5">Renews {renewalDate}</p>
                  )}
                </div>
              </div>

              {isPremium ? (
                <button
                  onClick={handleManage}
                  disabled={portalLoading}
                  className="shrink-0 rounded-full border border-white/20 px-5 py-2 text-sm font-medium text-white/80 transition-colors hover:bg-white/10 disabled:opacity-50"
                >
                  {portalLoading ? "Opening…" : "Manage Subscription"}
                </button>
              ) : (
                <button
                  onClick={handleUpgrade}
                  disabled={upgradingLoading}
                  className="shrink-0 rounded-full bg-blue-600 hover:bg-blue-500 px-6 py-2 text-sm font-semibold text-white transition-colors disabled:opacity-50"
                >
                  {upgradingLoading ? "Redirecting…" : "Upgrade to Premium"}
                </button>
              )}
            </div>
          </motion.section>

          {/* ── Benefits comparison ───────────────────────────────────────── */}
          <motion.section
            variants={fadeInUp}
            className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm"
          >
            <SectionFade />
            <SectionGlow variant="c" />
            <h3 className="text-lg font-semibold text-white mb-5">Premium Benefits</h3>

            <motion.ul
              variants={stagger}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.25 }}
              className="space-y-4"
            >
              {BENEFITS.map((b) => {
                const Icon = b.icon;
                const unlocked = isPremium;
                return (
                  <motion.li
                    key={b.label}
                    variants={fadeInUp}
                    className="flex items-start gap-4"
                  >
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-blue-500/15 border border-white/10 mt-0.5">
                      <Icon className="h-4 w-4 text-sky-200" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-white">{b.label}</p>
                      <div className="flex flex-wrap gap-4 mt-1">
                        <span className="flex items-center gap-1.5 text-xs text-white/50">
                          <XCircle className="h-3.5 w-3.5 text-white/30 shrink-0" />
                          Free: {b.freeValue ?? "Not included"}
                        </span>
                        <span
                          className={`flex items-center gap-1.5 text-xs font-medium transition-colors ${
                            unlocked ? "text-sky-300" : "text-white/40"
                          }`}
                        >
                          <CheckCircle2
                            className={`h-3.5 w-3.5 shrink-0 ${
                              unlocked ? "text-sky-400" : "text-white/25"
                            }`}
                          />
                          Premium: {b.premiumValue}
                        </span>
                      </div>
                    </div>
                  </motion.li>
                );
              })}
            </motion.ul>

            {!isPremium && (
              <div className="mt-6 flex justify-center">
                <button
                  onClick={handleUpgrade}
                  disabled={upgradingLoading}
                  className="rounded-full bg-blue-600 hover:bg-blue-500 px-8 py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
                >
                  {upgradingLoading ? "Redirecting…" : "Upgrade to Premium"}
                </button>
              </div>
            )}
          </motion.section>
        </motion.div>
      </div>
    </div>
  );
}
