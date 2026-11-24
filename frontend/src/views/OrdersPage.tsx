"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ShoppingBag } from "lucide-react";
import { SectionHeader } from "../components/ui/SectionHeader";
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
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<OrderStatus | undefined>(
    undefined
  );
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["orders", { status: activeTab, page }],
    queryFn: () =>
      ordersApi.list({ status: activeTab, page, size: 20 }).then((r) => r.data),
  });

  return (
    <div className="text-foreground">
      <div className="max-w-4xl">
        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="space-y-8"
        >
          <motion.div variants={fadeInUp}>
            <SectionHeader
              as="h1"
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
                onClick={() => {
                  setActiveTab(tab.value);
                  setPage(0);
                }}
                className={`px-4 py-1.5 rounded-full text-sm font-medium transition border ${
                  activeTab === tab.value
                    ? "bg-primary border-primary text-primary-foreground"
                    : "border-input text-muted-foreground hover:bg-muted"
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
                <div
                  key={i}
                  className="rounded-xl border border-border bg-surface h-24 animate-pulse"
                />
              ))}
            </motion.div>
          ) : !data?.content?.length ? (
            <motion.div
              variants={fadeInUp}
              className="flex flex-col items-center gap-4 py-16 text-center"
            >
              <div className="bg-primary/10 border border-border rounded-xl p-5">
                <ShoppingBag className="h-8 w-8 text-link" />
              </div>
              <p className="text-muted-foreground">
                No orders found{activeTab ? ` with status "${activeTab}"` : ""}.
              </p>
            </motion.div>
          ) : (
            <motion.div variants={stagger} className="space-y-3">
              {data.content.map((order) => (
                <motion.div key={order.id} variants={fadeInUp}>
                  <CustomerOrderCard
                    order={order}
                    onClick={() => router.push(`/orders/${order.id}`)}
                  />
                </motion.div>
              ))}
            </motion.div>
          )}

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <motion.div
              variants={fadeInUp}
              className="flex justify-center gap-3 pt-4"
            >
              <button
                disabled={!data.hasPrevious}
                onClick={() => setPage((p) => p - 1)}
                className="px-4 py-2 rounded-full border border-input text-sm text-muted-foreground hover:bg-muted disabled:opacity-30 transition"
              >
                Previous
              </button>
              <span className="text-sm text-muted-foreground py-2">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <button
                disabled={!data.hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="px-4 py-2 rounded-full border border-input text-sm text-muted-foreground hover:bg-muted disabled:opacity-30 transition"
              >
                Next
              </button>
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
