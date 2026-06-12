import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import type { Variants } from "framer-motion";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  type TooltipProps,
} from "recharts";
import type { RootState } from "../stores";
import { useAnims } from "../hooks/useAnims";
import {
  companyAnalyticsApi,
  type CompanyRevenueSummary,
  type CategorySalesResponse,
  type HotProductsResponse,
  type ProductPerformanceResponse,
  type ForecastSummary,
  type ReorderSuggestion,
  type SlowMoversResponse,
  type OperationsSummary,
} from "../api/analytics";

// ---- Animation helpers (respect reduced motion) ----

// ---- Loading skeleton ----
function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-2 mt-4">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-8 rounded-lg bg-white/[0.08] animate-pulse" />
      ))}
    </div>
  );
}

// ---- Recharts custom tooltip ----
function RevTooltip({ active, payload, label }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-xl border border-white/20 bg-slate-900 p-3 text-sm text-white shadow-xl">
      <p className="text-white/50 mb-1">{label}</p>
      <p className="font-semibold">
        ${Number(payload[0]?.value ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
      </p>
    </div>
  );
}

// ---- Section wrapper (glass card) ----
function Section({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-6 shadow-sm">
      {children}
    </div>
  );
}

// ---- Section header (kicker + title) ----
function SectionHead({ kicker, title }: { kicker: string; title: string }) {
  return (
    <div className="mb-5">
      <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-0.5">{kicker}</p>
      <h2 className="text-xl font-bold text-white">{title}</h2>
    </div>
  );
}

type LookbackDays = 7 | 30 | 90;

export default function DashboardPage() {
  const { fadeInUp, stagger } = useAnims();
  const companyId   = useSelector((s: RootState) => s.auth.companyId);

  // ---- Global time range ----
  const [lookbackDays, setLookbackDays] = useState<LookbackDays>(30);

  // ---- Per-section state ----
  const [revenueData,  setRevenueData]  = useState<CompanyRevenueSummary | null>(null);
  const [revenueLoad,  setRevenueLoad]  = useState(true);

  const [categoryData, setCategoryData] = useState<CategorySalesResponse | null>(null);
  const [categoryLoad, setCategoryLoad] = useState(true);

  const [hotWindow, setHotWindow]       = useState<"1h" | "24h">("1h");
  const [hotData,   setHotData]         = useState<HotProductsResponse | null>(null);
  const [hotLoad,   setHotLoad]         = useState(true);

  const [perfData,  setPerfData]        = useState<ProductPerformanceResponse | null>(null);
  const [perfLoad,  setPerfLoad]        = useState(true);

  const [forecastData,  setForecastData]  = useState<ForecastSummary | null>(null);
  const [forecastLoad,  setForecastLoad]  = useState(true);

  const [reorderData,   setReorderData]   = useState<ReorderSuggestion[]>([]);
  const [reorderLoad,   setReorderLoad]   = useState(true);

  const [slowData,  setSlowData]        = useState<SlowMoversResponse | null>(null);
  const [slowLoad,  setSlowLoad]        = useState(true);

  const [opsData,   setOpsData]         = useState<OperationsSummary | null>(null);
  const [opsLoad,   setOpsLoad]         = useState(true);

  // ---- Slow movers sub-tab ----
  const [slowTab, setSlowTab]           = useState<"slow" | "never">("slow");

  // ---- Forecasting needs min 14 days ----
  const forecastLookback = Math.max(lookbackDays, 14);

  // ---- Data fetching ----
  useEffect(() => {
    if (!companyId) return;
    setRevenueLoad(true);
    companyAnalyticsApi.getRevenueSummary(companyId, lookbackDays)
      .then(r => setRevenueData(r.data))
      .catch(console.error)
      .finally(() => setRevenueLoad(false));
  }, [companyId, lookbackDays]);

  useEffect(() => {
    if (!companyId) return;
    setCategoryLoad(true);
    companyAnalyticsApi.getCategorySales(companyId, lookbackDays)
      .then(r => setCategoryData(r.data))
      .catch(console.error)
      .finally(() => setCategoryLoad(false));
  }, [companyId, lookbackDays]);

  useEffect(() => {
    if (!companyId) return;
    setHotLoad(true);
    companyAnalyticsApi.getHotProducts(companyId, hotWindow)
      .then(r => setHotData(r.data))
      .catch(console.error)
      .finally(() => setHotLoad(false));
  }, [companyId, hotWindow]);

  useEffect(() => {
    if (!companyId) return;
    setPerfLoad(true);
    companyAnalyticsApi.getProductPerformance(companyId, lookbackDays)
      .then(r => setPerfData(r.data))
      .catch(console.error)
      .finally(() => setPerfLoad(false));
  }, [companyId, lookbackDays]);

  useEffect(() => {
    if (!companyId) return;
    setForecastLoad(true);
    companyAnalyticsApi.getForecastSummary(companyId, forecastLookback)
      .then(r => setForecastData(r.data))
      .catch(console.error)
      .finally(() => setForecastLoad(false));
  }, [companyId, forecastLookback]);

  useEffect(() => {
    if (!companyId) return;
    setReorderLoad(true);
    companyAnalyticsApi.getReorderSuggestions(companyId, forecastLookback)
      .then(r => setReorderData(r.data))
      .catch(console.error)
      .finally(() => setReorderLoad(false));
  }, [companyId, forecastLookback]);

  useEffect(() => {
    if (!companyId) return;
    setSlowLoad(true);
    companyAnalyticsApi.getSlowMovers(companyId, 90)
      .then(r => setSlowData(r.data))
      .catch(console.error)
      .finally(() => setSlowLoad(false));
  }, [companyId]);

  useEffect(() => {
    if (!companyId) return;
    setOpsLoad(true);
    companyAnalyticsApi.getOperationsSummary(companyId, lookbackDays)
      .then(r => setOpsData(r.data))
      .catch(console.error)
      .finally(() => setOpsLoad(false));
  }, [companyId, lookbackDays]);

  // ---- No company guard ----
  if (!companyId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-10 max-w-md text-center">
          <h2 className="text-2xl font-bold text-white mb-3">No Company Found</h2>
          <p className="text-white/70 mb-6 text-sm leading-relaxed">
            You don't have a company set up yet. Create one to start using the analytics dashboard.
          </p>
          <a
            href="/companies/new"
            className="inline-block rounded-full bg-blue-600 hover:bg-blue-500 text-white font-bold px-8 py-3 transition"
          >
            Create Company
          </a>
        </div>
      </div>
    );
  }

  // ---- Category data helpers ----
  const topCategories = categoryData
    ? [...categoryData.categories].slice(0, 8)
    : [];
  const othersShare = categoryData && categoryData.categories.length > 8
    ? categoryData.categories.slice(8).reduce((s, c) => s + c.revenueSharePercent, 0)
    : 0;

  // ---- Slow movers filter ----
  const filteredSlow = slowData
    ? slowData.items.filter(i => slowTab === "never" ? i.neverSold : !i.neverSold)
    : [];

  // ---- Forecast at-risk (sorted by daysOfCoverage asc) ----
  const atRisk = forecastData
    ? [...forecastData.items]
        .sort((a, b) => {
          if (a.daysOfCoverage == null) return 1;
          if (b.daysOfCoverage == null) return -1;
          return a.daysOfCoverage - b.daysOfCoverage;
        })
        .slice(0, 15)
    : [];

  const reorderBadge = {
    BELOW_THRESHOLD:        { label: "Below Threshold", cls: "border-amber-500/30 bg-amber-500/10 text-amber-300" },
    STOCKOUT_WITHIN_LEADTIME: { label: "Stockout Risk",   cls: "border-red-500/30 bg-red-500/10 text-red-300" },
    VELOCITY_SPIKE:         { label: "Velocity Spike",  cls: "border-sky-500/30 bg-sky-500/10 text-sky-300" },
  } as const;

  return (
    <main className="relative min-h-screen text-white bg-slate-950 px-4 sm:px-6 py-10">
      {/* Background glow */}
      <div className="pointer-events-none fixed inset-0 -z-10">
        <div className="absolute inset-0 bg-slate-950" />
        <div className="absolute -top-40 left-1/2 h-[520px] w-[520px] -translate-x-1/2 rounded-full bg-blue-600/14 blur-[110px]" />
        <div className="absolute bottom-0 right-0 h-[320px] w-[320px] rounded-full bg-indigo-500/10 blur-[90px]" />
      </div>

      <div className="max-w-7xl mx-auto space-y-6">

        {/* ---- Header ---- */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-1">Analytics</p>
            <h1 className="text-3xl font-extrabold text-white">Product Dashboard</h1>
          </div>
          <div className="flex items-center gap-1 rounded-xl border border-white/10 bg-white/[0.06] p-1">
            {([7, 30, 90] as LookbackDays[]).map(d => (
              <button
                key={d}
                onClick={() => setLookbackDays(d)}
                className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition ${
                  lookbackDays === d
                    ? "bg-blue-600 text-white"
                    : "text-white/60 hover:text-white hover:bg-white/10"
                }`}
              >
                {d}d
              </button>
            ))}
          </div>
        </div>

        {/* ======================================================
            SECTION 1 — Revenue Trend
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          <Section>
            <SectionHead kicker="Revenue" title="Daily Revenue Trend" />
            {revenueLoad ? (
              <SkeletonRows rows={5} />
            ) : revenueData ? (
              <>
                {/* Stat pills */}
                <div className="flex flex-wrap gap-4 mb-6">
                  {[
                    { label: "Total Revenue",   value: `$${revenueData.totalRevenue.toLocaleString()}` },
                    { label: "Total Orders",    value: revenueData.totalOrders.toLocaleString() },
                    { label: "Avg Order Value", value: `$${revenueData.avgOrderValue.toLocaleString()}` },
                  ].map(s => (
                    <div key={s.label} className="rounded-xl border border-white/10 bg-white/[0.04] px-4 py-2">
                      <p className="text-xs text-white/50">{s.label}</p>
                      <p className="text-xl font-extrabold text-white mt-0.5">{s.value}</p>
                    </div>
                  ))}
                </div>
                {/* Chart */}
                <ResponsiveContainer width="100%" height={220}>
                  <AreaChart data={revenueData.daily} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
                    <defs>
                      <linearGradient id="revGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%"  stopColor="#38bdf8" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#38bdf8" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <XAxis
                      dataKey="day"
                      tick={{ fill: "#ffffff60", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                      tickFormatter={d => d.slice(5)}
                    />
                    <YAxis
                      tick={{ fill: "#ffffff60", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                      tickFormatter={v => `$${Number(v).toLocaleString()}`}
                      width={70}
                    />
                    <Tooltip content={<RevTooltip />} />
                    <Area
                      type="monotone"
                      dataKey="totalRevenue"
                      stroke="#38bdf8"
                      strokeWidth={2}
                      fill="url(#revGrad)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </>
            ) : (
              <p className="text-white/50 text-sm py-4">No revenue data available.</p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 2 — Category Breakdown
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          <Section>
            <SectionHead kicker="Products" title="Revenue by Category" />
            {categoryLoad ? (
              <SkeletonRows rows={6} />
            ) : topCategories.length > 0 ? (
              <div className="space-y-3">
                {topCategories.map(c => (
                  <div key={c.category}>
                    <div className="flex justify-between items-baseline mb-1">
                      <span className="text-sm font-semibold text-white truncate pr-4">{c.category}</span>
                      <span className="text-xs text-white/60 shrink-0">
                        ${c.totalRevenue.toLocaleString()} &middot; {c.revenueSharePercent.toFixed(1)}%
                      </span>
                    </div>
                    <div className="h-1.5 w-full bg-white/10 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-sky-400/80 rounded-full"
                        style={{ width: `${Math.min(c.revenueSharePercent, 100)}%` }}
                      />
                    </div>
                  </div>
                ))}
                {othersShare > 0 && (
                  <div>
                    <div className="flex justify-between items-baseline mb-1">
                      <span className="text-sm text-white/50 pr-4">Other</span>
                      <span className="text-xs text-white/40">{othersShare.toFixed(1)}%</span>
                    </div>
                    <div className="h-1.5 w-full bg-white/10 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-white/20 rounded-full"
                        style={{ width: `${Math.min(othersShare, 100)}%` }}
                      />
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <p className="text-white/50 text-sm py-4">No category data available.</p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 3 — Product Performance (period-over-period)
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          <Section>
            <SectionHead kicker="Growth" title="Product Performance" />
            {perfLoad ? (
              <SkeletonRows rows={5} />
            ) : perfData && perfData.products.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-white/50 border-b border-white/10 text-left">
                      <th className="pb-2 pr-4 font-medium">Product</th>
                      <th className="pb-2 pr-4 font-medium text-right">This Period</th>
                      <th className="pb-2 pr-4 font-medium text-right">Prior Period</th>
                      <th className="pb-2 pr-4 font-medium text-right">Rev Growth</th>
                      <th className="pb-2 font-medium text-right">Units Growth</th>
                    </tr>
                  </thead>
                  <tbody>
                    {perfData.products.map(p => (
                      <tr key={p.productId} className="border-b border-white/[0.06] hover:bg-white/[0.04] transition">
                        <td className="py-2 pr-4">
                          <p className="font-semibold text-white truncate max-w-[180px]">{p.productName}</p>
                          <p className="text-xs text-white/40 font-mono">{p.sku}</p>
                        </td>
                        <td className="py-2 pr-4 text-right text-white">
                          ${p.currentRevenue.toLocaleString()}
                        </td>
                        <td className="py-2 pr-4 text-right text-white/60">
                          ${p.priorRevenue.toLocaleString()}
                        </td>
                        <td className="py-2 pr-4 text-right">
                          <GrowthBadge value={p.revenueGrowthPercent} />
                        </td>
                        <td className="py-2 text-right">
                          <GrowthBadge value={p.unitsGrowthPercent} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-white/50 text-sm py-4">No performance data available.</p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 4 — Hot Products (live demand)
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          <Section>
            <div className="flex items-center justify-between mb-5">
              <SectionHead kicker="Live Demand" title="Hot Products" />
              <div className="flex gap-1 rounded-lg border border-white/10 bg-white/[0.06] p-0.5">
                {(["1h", "24h"] as const).map(w => (
                  <button
                    key={w}
                    onClick={() => setHotWindow(w)}
                    className={`px-3 py-1 rounded-md text-sm font-semibold transition ${
                      hotWindow === w ? "bg-blue-600 text-white" : "text-white/60 hover:text-white"
                    }`}
                  >
                    {w}
                  </button>
                ))}
              </div>
            </div>
            {hotLoad ? (
              <SkeletonRows rows={5} />
            ) : hotData && hotData.products.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-white/50 border-b border-white/10 text-left">
                      <th className="pb-2 pr-4 font-medium">#</th>
                      <th className="pb-2 pr-4 font-medium">Product</th>
                      <th className="pb-2 pr-4 font-medium text-right">Units Sold</th>
                      <th className="pb-2 pr-4 font-medium text-right">Velocity/hr</th>
                      <th className="pb-2 font-medium text-right">Acceleration</th>
                    </tr>
                  </thead>
                  <tbody>
                    {hotData.products.map(p => (
                      <tr key={p.productId} className="border-b border-white/[0.06] hover:bg-white/[0.04] transition">
                        <td className="py-2 pr-4 text-white/40">{p.rank}</td>
                        <td className="py-2 pr-4">
                          <p className="font-semibold text-white">{p.productName}</p>
                          <p className="text-xs text-white/40 font-mono">{p.sku}</p>
                        </td>
                        <td className="py-2 pr-4 text-right text-white">{p.unitsSold.toLocaleString()}</td>
                        <td className="py-2 pr-4 text-right text-white">{p.velocityPerHour.toFixed(2)}</td>
                        <td className="py-2 text-right">
                          <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-bold ${
                            p.accelerationRatio >= 1.5
                              ? "bg-emerald-500/20 text-emerald-300"
                              : p.accelerationRatio >= 1.0
                              ? "bg-sky-500/20 text-sky-300"
                              : "bg-white/10 text-white/40"
                          }`}>
                            {p.accelerationRatio.toFixed(2)}×
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-white/50 text-sm py-4">No hot products in this window.</p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 5 — Reorder Suggestions
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          <Section>
            <SectionHead kicker="Procurement" title="Reorder Suggestions" />
            {reorderLoad ? (
              <SkeletonRows rows={4} />
            ) : reorderData.length > 0 ? (
              <div className="space-y-3">
                {reorderData.map(item => {
                  const badge = reorderBadge[item.reasonCode];
                  return (
                    <div
                      key={item.productId}
                      className="flex items-center justify-between gap-4 rounded-xl border border-white/10 bg-white/[0.04] px-4 py-3"
                    >
                      <div className="min-w-0">
                        <p className="font-semibold text-white truncate">{item.productName}</p>
                        <p className="text-xs text-white/40 font-mono">{item.sku}</p>
                      </div>
                      <div className="shrink-0 text-right space-y-1">
                        <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-bold border ${badge.cls}`}>
                          {badge.label}
                        </span>
                        <p className="text-xs text-white/50">
                          Order <span className="text-white font-semibold">{item.suggestedQty}</span> units
                          {item.projectedStockoutDate ? ` · stockout ${item.projectedStockoutDate}` : ""}
                        </p>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-white/50 text-sm py-4">No reorder suggestions at this time.</p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 6 — Inventory Outlook (forecast)
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          <Section>
            <SectionHead kicker="Forecasting" title="Inventory Outlook" />
            {forecastLoad ? (
              <SkeletonRows rows={6} />
            ) : forecastData ? (
              <>
                <div className="flex flex-wrap gap-4 mb-5">
                  <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-2">
                    <span className="text-2xl font-extrabold text-amber-300">{forecastData.productsNeedingReorder}</span>
                    <span className="text-xs text-amber-300/70 ml-2">need reorder</span>
                  </div>
                  <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-2">
                    <span className="text-2xl font-extrabold text-red-300">{forecastData.productsWithImminentStockout}</span>
                    <span className="text-xs text-red-300/70 ml-2">imminent stockout</span>
                  </div>
                </div>
                {atRisk.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="text-white/50 border-b border-white/10 text-left">
                          <th className="pb-2 pr-4 font-medium">Product</th>
                          <th className="pb-2 pr-4 font-medium text-right">Stock</th>
                          <th className="pb-2 pr-4 font-medium text-right">Days Left</th>
                          <th className="pb-2 pr-4 font-medium text-right">Stockout Date</th>
                          <th className="pb-2 font-medium text-right">Reorder Qty</th>
                        </tr>
                      </thead>
                      <tbody>
                        {atRisk.map(item => (
                          <tr key={item.productId} className="border-b border-white/[0.06] hover:bg-white/[0.04] transition">
                            <td className="py-2 pr-4 font-semibold text-white">{item.productName}</td>
                            <td className="py-2 pr-4 text-right text-white/80">{item.currentStock ?? "—"}</td>
                            <td className="py-2 pr-4 text-right">
                              <span className={
                                item.daysOfCoverage != null && item.daysOfCoverage <= 7
                                  ? "text-red-300 font-bold"
                                  : item.daysOfCoverage != null && item.daysOfCoverage <= 14
                                  ? "text-amber-300 font-semibold"
                                  : "text-white/70"
                              }>
                                {item.daysOfCoverage != null ? Math.round(item.daysOfCoverage) : "—"}
                              </span>
                            </td>
                            <td className="py-2 pr-4 text-right text-white/50 text-xs">
                              {item.likelyStockoutDate ?? "—"}
                            </td>
                            <td className="py-2 text-right">
                              {item.reorderUrgent ? (
                                <span className="inline-block px-2 py-0.5 rounded-full text-xs font-bold bg-red-500/20 text-red-300">
                                  {item.reorderSuggestedQty} ⚠
                                </span>
                              ) : (
                                <span className="text-white/60">{item.reorderSuggestedQty}</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="text-white/50 text-sm">All products have sufficient coverage.</p>
                )}
              </>
            ) : (
              <p className="text-white/50 text-sm py-4">No forecast data available.</p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 7 — Slow & Dead Stock
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={fadeInUp}>
          <Section>
            <div className="flex items-center justify-between mb-5">
              <SectionHead kicker="Inventory" title="Slow & Dead Stock" />
              <div className="flex gap-1 rounded-lg border border-white/10 bg-white/[0.06] p-0.5">
                {(["slow", "never"] as const).map(t => (
                  <button
                    key={t}
                    onClick={() => setSlowTab(t)}
                    className={`px-3 py-1 rounded-md text-sm font-semibold transition ${
                      slowTab === t ? "bg-blue-600 text-white" : "text-white/60 hover:text-white"
                    }`}
                  >
                    {t === "slow" ? "Slow Movers" : "Never Sold"}
                  </button>
                ))}
              </div>
            </div>
            {slowLoad ? (
              <SkeletonRows rows={4} />
            ) : filteredSlow.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-white/50 border-b border-white/10 text-left">
                      <th className="pb-2 pr-4 font-medium">Product</th>
                      <th className="pb-2 pr-4 font-medium text-right">Stock</th>
                      <th className="pb-2 pr-4 font-medium text-right">Units Sold</th>
                      <th className="pb-2 pr-4 font-medium text-right">Daily Velocity</th>
                      <th className="pb-2 font-medium text-right">Revenue</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSlow.map(item => (
                      <tr key={item.productId} className="border-b border-white/[0.06] hover:bg-white/[0.04] transition">
                        <td className="py-2 pr-4">
                          <p className="font-semibold text-white truncate max-w-[180px]">{item.productName}</p>
                          <p className="text-xs text-white/40 font-mono">{item.sku}</p>
                        </td>
                        <td className="py-2 pr-4 text-right text-white/80">{item.currentStock ?? "—"}</td>
                        <td className="py-2 pr-4 text-right text-white/80">{item.unitsSold}</td>
                        <td className="py-2 pr-4 text-right">
                          <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-bold ${
                            item.dailyVelocity < 0.1
                              ? "bg-red-500/20 text-red-300"
                              : "bg-amber-500/20 text-amber-300"
                          }`}>
                            {item.dailyVelocity < 0.1 ? "Dead" : "Slow"} · {item.dailyVelocity.toFixed(2)}/day
                          </span>
                        </td>
                        <td className="py-2 text-right text-white/60">
                          ${item.revenue.toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-white/50 text-sm py-4">
                {slowTab === "never" ? "No never-sold products with stock." : "No slow-moving products found."}
              </p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 8 — Operations Health KPIs
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={stagger}>
          <div className="mb-4">
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-0.5">Operations</p>
            <h2 className="text-xl font-bold text-white">Health KPIs</h2>
          </div>
          {opsLoad ? (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-28 rounded-2xl bg-white/[0.06] animate-pulse border border-white/10" />
              ))}
            </div>
          ) : (
            <motion.div variants={stagger} className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {[
                {
                  label: "Avg Fulfillment",
                  value: opsData?.fulfillment.avgHours != null
                    ? `${opsData.fulfillment.avgHours.toFixed(1)} hrs`
                    : "—",
                  sub: `${opsData?.fulfillment.count ?? 0} orders`,
                },
                {
                  label: "Avg Pick Delay",
                  value: opsData?.pickDelays.avgHours != null
                    ? `${opsData.pickDelays.avgHours.toFixed(1)} hrs`
                    : "—",
                  sub: `${opsData?.pickDelays.count ?? 0} tracked`,
                },
                {
                  label: "Stockout Rate",
                  value: opsData?.stockouts
                    ? `${(opsData.stockouts.outOfStockRate * 100).toFixed(1)}%`
                    : "—",
                  sub: `${opsData?.stockouts.outOfStock ?? 0} / ${opsData?.stockouts.trackedProducts ?? 0} products`,
                },
                {
                  label: "Backorder Rate",
                  value: opsData?.stockouts
                    ? `${(opsData.stockouts.backorderRate * 100).toFixed(1)}%`
                    : "—",
                  sub: `${opsData?.stockouts.ordersWithBackorders ?? 0} orders`,
                },
              ].map(kpi => (
                <motion.div
                  key={kpi.label}
                  variants={fadeInUp}
                  className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 shadow-sm"
                >
                  <p className="text-xs text-white/50 uppercase tracking-wide">{kpi.label}</p>
                  <p className="text-2xl font-extrabold text-white mt-1">{kpi.value}</p>
                  <p className="text-xs text-white/40 mt-1">{kpi.sub}</p>
                </motion.div>
              ))}
            </motion.div>
          )}
        </motion.div>

      </div>
    </main>
  );
}

// ---- Growth badge helper ----
function GrowthBadge({ value }: { value: number }) {
  if (value > 0)  return <span className="text-green-400 font-semibold">↑ {value.toFixed(1)}%</span>;
  if (value < 0)  return <span className="text-red-400 font-semibold">↓ {Math.abs(value).toFixed(1)}%</span>;
  return <span className="text-white/40">—</span>;
}
