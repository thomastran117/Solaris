"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  type TooltipContentProps,
  type TooltipValueType,
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
        <div key={i} className="h-8 rounded-lg bg-muted animate-pulse" />
      ))}
    </div>
  );
}

// ---- Recharts custom tooltip ----
function RevTooltip({
  active,
  payload,
  label,
}: Pick<
  TooltipContentProps<TooltipValueType, string | number>,
  "active" | "payload" | "label"
>) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-xl border border-input bg-surface p-3 text-sm text-foreground shadow-xl">
      <p className="text-muted-foreground mb-1">{label}</p>
      <p className="font-semibold">
        $
        {Number(payload[0]?.value ?? 0).toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}
      </p>
    </div>
  );
}

// ---- Section wrapper (glass card) ----
function Section({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
      {children}
    </div>
  );
}

// ---- Section header (kicker + title) ----
function SectionHead({ kicker, title }: { kicker: string; title: string }) {
  return (
    <div className="mb-5">
      <p className="text-xs font-semibold text-link mb-0.5">{kicker}</p>
      <h2 className="text-xl font-bold text-foreground">{title}</h2>
    </div>
  );
}

type LookbackDays = 7 | 30 | 90;

export default function DashboardPage() {
  const { fadeInUp, stagger } = useAnims();
  const router = useRouter();
  const companyId = useSelector((s: RootState) => s.auth.companyId);

  // ---- Global time range ----
  const [lookbackDays, setLookbackDays] = useState<LookbackDays>(30);

  // ---- Per-section state ----
  const [revenueData, setRevenueData] = useState<CompanyRevenueSummary | null>(
    null
  );
  const [revenueLoad, setRevenueLoad] = useState(true);

  const [categoryData, setCategoryData] =
    useState<CategorySalesResponse | null>(null);
  const [categoryLoad, setCategoryLoad] = useState(true);

  const [hotWindow, setHotWindow] = useState<"1h" | "24h">("1h");
  const [hotData, setHotData] = useState<HotProductsResponse | null>(null);
  const [hotLoad, setHotLoad] = useState(true);

  const [perfData, setPerfData] = useState<ProductPerformanceResponse | null>(
    null
  );
  const [perfLoad, setPerfLoad] = useState(true);

  const [forecastData, setForecastData] = useState<ForecastSummary | null>(
    null
  );
  const [forecastLoad, setForecastLoad] = useState(true);

  const [reorderData, setReorderData] = useState<ReorderSuggestion[]>([]);
  const [reorderLoad, setReorderLoad] = useState(true);

  const [slowData, setSlowData] = useState<SlowMoversResponse | null>(null);
  const [slowLoad, setSlowLoad] = useState(true);

  const [opsData, setOpsData] = useState<OperationsSummary | null>(null);
  const [opsLoad, setOpsLoad] = useState(true);

  // ---- Slow movers sub-tab ----
  const [slowTab, setSlowTab] = useState<"slow" | "never">("slow");

  // ---- Forecasting needs min 14 days ----
  const forecastLookback = Math.max(lookbackDays, 14);

  // ---- Data fetching ----
  useEffect(() => {
    if (!companyId) return;
    setRevenueLoad(true);
    companyAnalyticsApi
      .getRevenueSummary(companyId, lookbackDays)
      .then((r) => setRevenueData(r.data))
      .catch(console.error)
      .finally(() => setRevenueLoad(false));
  }, [companyId, lookbackDays]);

  useEffect(() => {
    if (!companyId) return;
    setCategoryLoad(true);
    companyAnalyticsApi
      .getCategorySales(companyId, lookbackDays)
      .then((r) => setCategoryData(r.data))
      .catch(console.error)
      .finally(() => setCategoryLoad(false));
  }, [companyId, lookbackDays]);

  useEffect(() => {
    if (!companyId) return;
    setHotLoad(true);
    companyAnalyticsApi
      .getHotProducts(companyId, hotWindow)
      .then((r) => setHotData(r.data))
      .catch(console.error)
      .finally(() => setHotLoad(false));
  }, [companyId, hotWindow]);

  useEffect(() => {
    if (!companyId) return;
    setPerfLoad(true);
    companyAnalyticsApi
      .getProductPerformance(companyId, lookbackDays)
      .then((r) => setPerfData(r.data))
      .catch(console.error)
      .finally(() => setPerfLoad(false));
  }, [companyId, lookbackDays]);

  useEffect(() => {
    if (!companyId) return;
    setForecastLoad(true);
    companyAnalyticsApi
      .getForecastSummary(companyId, forecastLookback)
      .then((r) => setForecastData(r.data))
      .catch(console.error)
      .finally(() => setForecastLoad(false));
  }, [companyId, forecastLookback]);

  useEffect(() => {
    if (!companyId) return;
    setReorderLoad(true);
    companyAnalyticsApi
      .getReorderSuggestions(companyId, forecastLookback)
      .then((r) => setReorderData(r.data))
      .catch(console.error)
      .finally(() => setReorderLoad(false));
  }, [companyId, forecastLookback]);

  useEffect(() => {
    if (!companyId) return;
    setSlowLoad(true);
    companyAnalyticsApi
      .getSlowMovers(companyId, 90)
      .then((r) => setSlowData(r.data))
      .catch(console.error)
      .finally(() => setSlowLoad(false));
  }, [companyId]);

  useEffect(() => {
    if (!companyId) return;
    setOpsLoad(true);
    companyAnalyticsApi
      .getOperationsSummary(companyId, lookbackDays)
      .then((r) => setOpsData(r.data))
      .catch(console.error)
      .finally(() => setOpsLoad(false));
  }, [companyId, lookbackDays]);

  // ---- No company guard ----
  if (!companyId) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center px-6">
        <div className="rounded-xl border border-border bg-surface p-10 max-w-md text-center">
          <h2 className="text-2xl font-bold text-foreground mb-3">
            No Company Found
          </h2>
          <p className="text-muted-foreground mb-6 text-sm leading-relaxed">
            You don't have a company set up yet. Create one to start using the
            analytics dashboard.
          </p>
          <a
            href="/companies/new"
            className="inline-block rounded-full bg-primary hover:bg-primary-hover text-primary-foreground font-bold px-8 py-3 transition"
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
  const othersShare =
    categoryData && categoryData.categories.length > 8
      ? categoryData.categories
          .slice(8)
          .reduce((s, c) => s + c.revenueSharePercent, 0)
      : 0;

  // ---- Slow movers filter ----
  const filteredSlow = slowData
    ? slowData.items.filter((i) =>
        slowTab === "never" ? i.neverSold : !i.neverSold
      )
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
    BELOW_THRESHOLD: {
      label: "Below Threshold",
      cls: "border-warning/40 bg-warning-subtle text-warning",
    },
    STOCKOUT_WITHIN_LEADTIME: {
      label: "Stockout Risk",
      cls: "border-danger/40 bg-danger-subtle text-danger",
    },
    VELOCITY_SPIKE: {
      label: "Velocity Spike",
      cls: "border-primary/40 bg-primary/10 text-link",
    },
  } as const;

  return (
    <div className="text-foreground px-4 sm:px-6 py-10">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* ---- Header ---- */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <p className="text-xs font-semibold text-link mb-1">Analytics</p>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              Product Dashboard
            </h1>
          </div>
          <div className="flex items-center gap-1 rounded-xl border border-border bg-surface p-1">
            {([7, 30, 90] as LookbackDays[]).map((d) => (
              <button
                key={d}
                onClick={() => setLookbackDays(d)}
                className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition ${
                  lookbackDays === d
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-muted"
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
                    {
                      label: "Total Revenue",
                      value: `$${revenueData.totalRevenue.toLocaleString()}`,
                    },
                    {
                      label: "Total Orders",
                      value: revenueData.totalOrders.toLocaleString(),
                    },
                    {
                      label: "Avg Order Value",
                      value: `$${revenueData.avgOrderValue.toLocaleString()}`,
                    },
                  ].map((s) => (
                    <div
                      key={s.label}
                      className="rounded-xl border border-border bg-surface px-4 py-2"
                    >
                      <p className="text-xs text-muted-foreground">{s.label}</p>
                      <p className="text-xl font-semibold text-foreground mt-0.5">
                        {s.value}
                      </p>
                    </div>
                  ))}
                </div>
                {/* Chart */}
                <ResponsiveContainer width="100%" height={220}>
                  <AreaChart
                    data={revenueData.daily}
                    margin={{ top: 4, right: 0, bottom: 0, left: 0 }}
                  >
                    <defs>
                      <linearGradient id="revGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop
                          offset="5%"
                          stopColor="var(--chart-1)"
                          stopOpacity={0.3}
                        />
                        <stop
                          offset="95%"
                          stopColor="var(--chart-1)"
                          stopOpacity={0}
                        />
                      </linearGradient>
                    </defs>
                    <XAxis
                      dataKey="day"
                      tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                      tickFormatter={(d) => d.slice(5)}
                    />
                    <YAxis
                      tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
                      axisLine={false}
                      tickLine={false}
                      tickFormatter={(v) => `$${Number(v).toLocaleString()}`}
                      width={70}
                    />
                    <Tooltip content={(props) => <RevTooltip {...props} />} />
                    <Area
                      type="monotone"
                      dataKey="totalRevenue"
                      stroke="var(--chart-1)"
                      strokeWidth={2}
                      fill="url(#revGrad)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </>
            ) : (
              <p className="text-muted-foreground text-sm py-4">
                No revenue data available.
              </p>
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
                {topCategories.map((c) => (
                  <div key={c.category}>
                    <div className="flex justify-between items-baseline mb-1">
                      <span className="text-sm font-semibold text-foreground truncate pr-4">
                        {c.category}
                      </span>
                      <span className="text-xs text-muted-foreground shrink-0">
                        ${c.totalRevenue.toLocaleString()} &middot;{" "}
                        {c.revenueSharePercent.toFixed(1)}%
                      </span>
                    </div>
                    <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                      <div
                        className="h-full bg-primary/10 rounded-full"
                        style={{
                          width: `${Math.min(c.revenueSharePercent, 100)}%`,
                        }}
                      />
                    </div>
                  </div>
                ))}
                {othersShare > 0 && (
                  <div>
                    <div className="flex justify-between items-baseline mb-1">
                      <span className="text-sm text-muted-foreground pr-4">
                        Other
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {othersShare.toFixed(1)}%
                      </span>
                    </div>
                    <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                      <div
                        className="h-full bg-muted rounded-full"
                        style={{ width: `${Math.min(othersShare, 100)}%` }}
                      />
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <p className="text-muted-foreground text-sm py-4">
                No category data available.
              </p>
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
                    <tr className="text-muted-foreground border-b border-border text-left">
                      <th className="pb-2 pr-4 font-medium">Product</th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        This Period
                      </th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        Prior Period
                      </th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        Rev Growth
                      </th>
                      <th className="pb-2 font-medium text-right">
                        Units Growth
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {perfData.products.map((p) => (
                      <tr
                        key={p.productId}
                        className="border-b border-border hover:bg-muted transition"
                      >
                        <td className="py-2 pr-4">
                          <p className="font-semibold text-foreground truncate max-w-[180px]">
                            {p.productName}
                          </p>
                          <p className="text-xs text-muted-foreground font-mono">
                            {p.sku}
                          </p>
                        </td>
                        <td className="py-2 pr-4 text-right text-foreground">
                          ${p.currentRevenue.toLocaleString()}
                        </td>
                        <td className="py-2 pr-4 text-right text-muted-foreground">
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
              <p className="text-muted-foreground text-sm py-4">
                No performance data available.
              </p>
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
              <div className="flex gap-1 rounded-lg border border-border bg-surface p-0.5">
                {(["1h", "24h"] as const).map((w) => (
                  <button
                    key={w}
                    onClick={() => setHotWindow(w)}
                    className={`px-3 py-1 rounded-md text-sm font-semibold transition ${
                      hotWindow === w
                        ? "bg-primary text-primary-foreground"
                        : "text-muted-foreground hover:text-foreground"
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
                    <tr className="text-muted-foreground border-b border-border text-left">
                      <th className="pb-2 pr-4 font-medium">#</th>
                      <th className="pb-2 pr-4 font-medium">Product</th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        Units Sold
                      </th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        Velocity/hr
                      </th>
                      <th className="pb-2 font-medium text-right">
                        Acceleration
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {hotData.products.map((p) => (
                      <tr
                        key={p.productId}
                        className="border-b border-border hover:bg-muted transition"
                      >
                        <td className="py-2 pr-4 text-muted-foreground">
                          {p.rank}
                        </td>
                        <td className="py-2 pr-4">
                          <p className="font-semibold text-foreground">
                            {p.productName}
                          </p>
                          <p className="text-xs text-muted-foreground font-mono">
                            {p.sku}
                          </p>
                        </td>
                        <td className="py-2 pr-4 text-right text-foreground">
                          {p.unitsSold.toLocaleString()}
                        </td>
                        <td className="py-2 pr-4 text-right text-foreground">
                          {p.velocityPerHour.toFixed(2)}
                        </td>
                        <td className="py-2 text-right">
                          <span
                            className={`inline-block px-2 py-0.5 rounded-full text-xs font-bold ${
                              p.accelerationRatio >= 1.5
                                ? "bg-success-subtle text-success"
                                : p.accelerationRatio >= 1.0
                                  ? "bg-primary/10 text-link"
                                  : "bg-muted text-muted-foreground"
                            }`}
                          >
                            {p.accelerationRatio.toFixed(2)}×
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-muted-foreground text-sm py-4">
                No hot products in this window.
              </p>
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
                {reorderData.map((item) => {
                  const badge = reorderBadge[item.reasonCode];
                  return (
                    <div
                      key={item.productId}
                      className="flex items-center justify-between gap-4 rounded-xl border border-border bg-surface px-4 py-3"
                    >
                      <div className="min-w-0">
                        <p className="font-semibold text-foreground truncate">
                          {item.productName}
                        </p>
                        <p className="text-xs text-muted-foreground font-mono">
                          {item.sku}
                        </p>
                      </div>
                      <div className="shrink-0 text-right space-y-1">
                        <span
                          className={`inline-block px-2 py-0.5 rounded-full text-xs font-bold border ${badge.cls}`}
                        >
                          {badge.label}
                        </span>
                        <p className="text-xs text-muted-foreground">
                          Order{" "}
                          <span className="text-foreground font-semibold">
                            {item.suggestedQty}
                          </span>{" "}
                          units
                          {item.projectedStockoutDate
                            ? ` · stockout ${item.projectedStockoutDate}`
                            : ""}
                        </p>
                        <button
                          onClick={() =>
                            router.push(
                              `/admin/purchase-orders?prefill=${item.productId}`
                            )
                          }
                          className="text-xs text-link hover:text-link transition underline-offset-2 hover:underline"
                        >
                          Create PO
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-muted-foreground text-sm py-4">
                No reorder suggestions at this time.
              </p>
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
                  <div className="rounded-xl border border-warning/40 bg-warning-subtle px-4 py-2">
                    <span className="text-2xl font-semibold text-warning">
                      {forecastData.productsNeedingReorder}
                    </span>
                    <span className="text-xs text-warning ml-2">
                      need reorder
                    </span>
                  </div>
                  <div className="rounded-xl border border-danger/40 bg-danger-subtle px-4 py-2">
                    <span className="text-2xl font-semibold text-danger">
                      {forecastData.productsWithImminentStockout}
                    </span>
                    <span className="text-xs text-danger ml-2">
                      imminent stockout
                    </span>
                  </div>
                </div>
                {atRisk.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="text-muted-foreground border-b border-border text-left">
                          <th className="pb-2 pr-4 font-medium">Product</th>
                          <th className="pb-2 pr-4 font-medium text-right">
                            Stock
                          </th>
                          <th className="pb-2 pr-4 font-medium text-right">
                            Days Left
                          </th>
                          <th className="pb-2 pr-4 font-medium text-right">
                            Stockout Date
                          </th>
                          <th className="pb-2 font-medium text-right">
                            Reorder Qty
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {atRisk.map((item) => (
                          <tr
                            key={item.productId}
                            className="border-b border-border hover:bg-muted transition"
                          >
                            <td className="py-2 pr-4 font-semibold text-foreground">
                              {item.productName}
                            </td>
                            <td className="py-2 pr-4 text-right text-foreground">
                              {item.currentStock ?? "—"}
                            </td>
                            <td className="py-2 pr-4 text-right">
                              <span
                                className={
                                  item.daysOfCoverage != null &&
                                  item.daysOfCoverage <= 7
                                    ? "text-danger font-bold"
                                    : item.daysOfCoverage != null &&
                                        item.daysOfCoverage <= 14
                                      ? "text-warning font-semibold"
                                      : "text-muted-foreground"
                                }
                              >
                                {item.daysOfCoverage != null
                                  ? Math.round(item.daysOfCoverage)
                                  : "—"}
                              </span>
                            </td>
                            <td className="py-2 pr-4 text-right text-muted-foreground text-xs">
                              {item.likelyStockoutDate ?? "—"}
                            </td>
                            <td className="py-2 text-right">
                              {item.reorderUrgent ? (
                                <span className="inline-block px-2 py-0.5 rounded-full text-xs font-bold bg-danger-subtle text-danger">
                                  {item.reorderSuggestedQty} ⚠
                                </span>
                              ) : (
                                <span className="text-muted-foreground">
                                  {item.reorderSuggestedQty}
                                </span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="text-muted-foreground text-sm">
                    All products have sufficient coverage.
                  </p>
                )}
              </>
            ) : (
              <p className="text-muted-foreground text-sm py-4">
                No forecast data available.
              </p>
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
              <div className="flex gap-1 rounded-lg border border-border bg-surface p-0.5">
                {(["slow", "never"] as const).map((t) => (
                  <button
                    key={t}
                    onClick={() => setSlowTab(t)}
                    className={`px-3 py-1 rounded-md text-sm font-semibold transition ${
                      slowTab === t
                        ? "bg-primary text-primary-foreground"
                        : "text-muted-foreground hover:text-foreground"
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
                    <tr className="text-muted-foreground border-b border-border text-left">
                      <th className="pb-2 pr-4 font-medium">Product</th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        Stock
                      </th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        Units Sold
                      </th>
                      <th className="pb-2 pr-4 font-medium text-right">
                        Daily Velocity
                      </th>
                      <th className="pb-2 font-medium text-right">Revenue</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSlow.map((item) => (
                      <tr
                        key={item.productId}
                        className="border-b border-border hover:bg-muted transition"
                      >
                        <td className="py-2 pr-4">
                          <p className="font-semibold text-foreground truncate max-w-[180px]">
                            {item.productName}
                          </p>
                          <p className="text-xs text-muted-foreground font-mono">
                            {item.sku}
                          </p>
                        </td>
                        <td className="py-2 pr-4 text-right text-foreground">
                          {item.currentStock ?? "—"}
                        </td>
                        <td className="py-2 pr-4 text-right text-foreground">
                          {item.unitsSold}
                        </td>
                        <td className="py-2 pr-4 text-right">
                          <span
                            className={`inline-block px-2 py-0.5 rounded-full text-xs font-bold ${
                              item.dailyVelocity < 0.1
                                ? "bg-danger-subtle text-danger"
                                : "bg-warning-subtle text-warning"
                            }`}
                          >
                            {item.dailyVelocity < 0.1 ? "Dead" : "Slow"} ·{" "}
                            {item.dailyVelocity.toFixed(2)}/day
                          </span>
                        </td>
                        <td className="py-2 text-right text-muted-foreground">
                          ${item.revenue.toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-muted-foreground text-sm py-4">
                {slowTab === "never"
                  ? "No never-sold products with stock."
                  : "No slow-moving products found."}
              </p>
            )}
          </Section>
        </motion.div>

        {/* ======================================================
            SECTION 8 — Operations Health KPIs
        ====================================================== */}
        <motion.div initial="hidden" animate="visible" variants={stagger}>
          <div className="mb-4">
            <p className="text-xs font-semibold text-link mb-0.5">Operations</p>
            <h2 className="text-xl font-bold text-foreground">Health KPIs</h2>
          </div>
          {opsLoad ? (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <div
                  key={i}
                  className="h-28 rounded-xl bg-surface animate-pulse border border-border"
                />
              ))}
            </div>
          ) : (
            <motion.div
              variants={stagger}
              className="grid grid-cols-2 lg:grid-cols-4 gap-4"
            >
              {[
                {
                  label: "Avg Fulfillment",
                  value:
                    opsData?.fulfillment.avgHours != null
                      ? `${opsData.fulfillment.avgHours.toFixed(1)} hrs`
                      : "—",
                  sub: `${opsData?.fulfillment.count ?? 0} orders`,
                },
                {
                  label: "Avg Pick Delay",
                  value:
                    opsData?.pickDelays.avgHours != null
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
              ].map((kpi) => (
                <motion.div
                  key={kpi.label}
                  variants={fadeInUp}
                  className="rounded-xl border border-border bg-surface p-5 shadow-sm"
                >
                  <p className="text-xs text-muted-foreground uppercase tracking-wide">
                    {kpi.label}
                  </p>
                  <p className="text-2xl font-semibold text-foreground mt-1">
                    {kpi.value}
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {kpi.sub}
                  </p>
                </motion.div>
              ))}
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
  );
}

// ---- Growth badge helper ----
function GrowthBadge({ value }: { value: number }) {
  if (value > 0)
    return (
      <span className="text-success font-semibold">↑ {value.toFixed(1)}%</span>
    );
  if (value < 0)
    return (
      <span className="text-danger font-semibold">
        ↓ {Math.abs(value).toFixed(1)}%
      </span>
    );
  return <span className="text-muted-foreground">—</span>;
}
