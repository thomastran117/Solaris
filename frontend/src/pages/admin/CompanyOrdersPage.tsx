import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ShoppingBag } from "lucide-react";
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";
import SectionGlow from "../../components/section/SectionGlow";
import SectionFade from "../../components/section/SectionFade";
import SectionTitle from "../../components/section/SectionTitle";
import { VendorOrderCard } from "../../components/order/OrderCard";
import { companyOrdersApi } from "../../api/companyOrders";
import type { OrderStatus } from "../../types/order";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const TABS: { label: string; value: OrderStatus | undefined }[] = [
  { label: "All", value: undefined },
  { label: "Needs Packing", value: "PAID" },
  { label: "Packed", value: "PACKED" },
  { label: "Shipped", value: "SHIPPED" },
  { label: "Delivered", value: "DELIVERED" },
];


export default function CompanyOrdersPage() {
  const { fadeInUp, stagger } = useAnims();
  const navigate = useNavigate();
  const { companyId } = useSelector((state: RootState) => state.auth);
  const [activeTab, setActiveTab] = useState<OrderStatus | undefined>(undefined);
  const [deliveryDate, setDeliveryDate] = useState<string>("");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["company-orders", companyId, { status: activeTab, deliveryDate, page }],
    queryFn: () =>
      companyOrdersApi
        .list(companyId!, { status: activeTab, deliveryDate: deliveryDate || undefined, page, size: 20 })
        .then((r) => r.data),
    enabled: !!companyId,
  });

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />
      <div className="relative z-10 max-w-3xl mx-auto px-4 py-20">
        <SectionFade top />
        <SectionGlow variant="a" />

        <motion.div initial="hidden" animate="visible" variants={stagger} className="space-y-8">
          <motion.div variants={fadeInUp}>
            <SectionTitle kicker="Fulfillment" title="Orders" subtitle="Pack, ship, and track all customer orders." />
          </motion.div>

          <motion.div variants={fadeInUp} className="flex flex-wrap gap-2">
            {TABS.map((tab) => (
              <button
                key={tab.label}
                onClick={() => { setActiveTab(tab.value); setPage(0); }}
                className={`px-4 py-1.5 rounded-full text-sm font-medium transition border ${
                  activeTab === tab.value
                    ? "bg-blue-600 border-blue-500 text-white"
                    : "border-white/20 text-white/70 hover:bg-white/10"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </motion.div>

          <motion.div variants={fadeInUp} className="flex flex-wrap items-center gap-3">
            <label htmlFor="deliveryDate" className="text-xs font-medium text-white/60">
              Delivery date
            </label>
            <input
              id="deliveryDate"
              type="date"
              value={deliveryDate}
              onChange={(e) => { setDeliveryDate(e.target.value); setPage(0); }}
              className="rounded-xl border border-white/10 bg-white/[0.04] px-3 py-1.5 text-sm text-white focus:outline-none focus:border-white/20 transition-colors"
            />
            {deliveryDate && (
              <button
                onClick={() => { setDeliveryDate(""); setPage(0); }}
                className="text-xs text-white/60 hover:text-white underline underline-offset-2 transition"
              >
                Clear
              </button>
            )}
          </motion.div>

          {isLoading ? (
            <motion.div variants={fadeInUp} className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-24 animate-pulse" />
              ))}
            </motion.div>
          ) : !data?.content?.length ? (
            <motion.div variants={fadeInUp} className="flex flex-col items-center gap-4 py-16 text-center">
              <div className="bg-blue-500/15 border border-white/10 rounded-2xl p-5">
                <ShoppingBag className="h-8 w-8 text-sky-200" />
              </div>
              <p className="text-white/60">No orders found.</p>
            </motion.div>
          ) : (
            <motion.div variants={stagger} className="space-y-3">
              {data.content.map((order) => (
                <motion.div key={order.orderId} variants={fadeInUp}>
                  <VendorOrderCard
                    order={order}
                    onClick={() => navigate(`/admin/orders/${order.orderId}`)}
                  />
                </motion.div>
              ))}
            </motion.div>
          )}

          {data && data.totalPages > 1 && (
            <motion.div variants={fadeInUp} className="flex justify-center gap-3 pt-4">
              <button disabled={!data.hasPrevious} onClick={() => setPage((p) => p - 1)}
                className="px-4 py-2 rounded-full border border-white/20 text-sm text-white/70 hover:bg-white/10 disabled:opacity-30 transition">
                Previous
              </button>
              <span className="text-sm text-white/50 py-2">Page {data.page + 1} of {data.totalPages}</span>
              <button disabled={!data.hasNext} onClick={() => setPage((p) => p + 1)}
                className="px-4 py-2 rounded-full border border-white/20 text-sm text-white/70 hover:bg-white/10 disabled:opacity-30 transition">
                Next
              </button>
            </motion.div>
          )}
        </motion.div>
        <SectionFade bottom />
      </div>
    </div>
  );
}
