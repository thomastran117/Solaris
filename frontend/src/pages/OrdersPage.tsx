import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ShoppingBag } from "lucide-react";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionGlow from "../components/section/SectionGlow";
import SectionFade from "../components/section/SectionFade";
import SectionTitle from "../components/section/SectionTitle";
import { CustomerOrderCard } from "../components/order/OrderCard";
import { ordersApi } from "../api/orders";
import type { OrderStatus } from "../types/order";
import { useAnims } from "../hooks/useAnims";

const TABS: { label: string; value: OrderStatus | undefined }[] = [
  { label: "All", value: undefined },
  { label: "Active", value: "PAID" },
  { label: "Shipped", value: "SHIPPED" },
  { label: "Delivered", value: "DELIVERED" },
  { label: "Cancelled", value: "CANCELLED" },
];


export default function OrdersPage() {
  const { fadeInUp, stagger } = useAnims();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<OrderStatus | undefined>(undefined);
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["orders", { status: activeTab, page }],
    queryFn: () => ordersApi.list({ status: activeTab, page, size: 20 }).then((r) => r.data),
  });

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />
      <div className="relative z-10 max-w-3xl mx-auto px-4 py-20">
        <SectionFade top />
        <SectionGlow variant="a" />

        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="space-y-8"
        >
          <motion.div variants={fadeInUp}>
            <SectionTitle
              kicker="My Account"
              title="Your Orders"
              subtitle="Track shipments, view past purchases, and manage returns."
            />
          </motion.div>

          {/* Tabs */}
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

          {/* Order list */}
          {isLoading ? (
            <motion.div variants={fadeInUp} className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-24 animate-pulse" />
              ))}
            </motion.div>
          ) : !data?.content?.length ? (
            <motion.div
              variants={fadeInUp}
              className="flex flex-col items-center gap-4 py-16 text-center"
            >
              <div className="bg-blue-500/15 border border-white/10 rounded-2xl p-5">
                <ShoppingBag className="h-8 w-8 text-sky-200" />
              </div>
              <p className="text-white/60">No orders found{activeTab ? ` with status "${activeTab}"` : ""}.</p>
            </motion.div>
          ) : (
            <motion.div variants={stagger} className="space-y-3">
              {data.content.map((order) => (
                <motion.div key={order.id} variants={fadeInUp}>
                  <CustomerOrderCard
                    order={order}
                    onClick={() => navigate(`/orders/${order.id}`)}
                  />
                </motion.div>
              ))}
            </motion.div>
          )}

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <motion.div variants={fadeInUp} className="flex justify-center gap-3 pt-4">
              <button
                disabled={!data.hasPrevious}
                onClick={() => setPage((p) => p - 1)}
                className="px-4 py-2 rounded-full border border-white/20 text-sm text-white/70 hover:bg-white/10 disabled:opacity-30 transition"
              >
                Previous
              </button>
              <span className="text-sm text-white/50 py-2">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <button
                disabled={!data.hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="px-4 py-2 rounded-full border border-white/20 text-sm text-white/70 hover:bg-white/10 disabled:opacity-30 transition"
              >
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
