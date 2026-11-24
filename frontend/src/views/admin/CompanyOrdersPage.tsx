"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ShoppingBag } from "lucide-react";
import { SectionHeader } from "../../components/ui/SectionHeader";
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
  const router = useRouter();
  const { companyId } = useSelector((state: RootState) => state.auth);
  const [activeTab, setActiveTab] = useState<OrderStatus | undefined>(
    undefined
  );
  const [deliveryDate, setDeliveryDate] = useState<string>("");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: [
      "company-orders",
      companyId,
      { status: activeTab, deliveryDate, page },
    ],
    queryFn: () =>
      companyOrdersApi
        .list(companyId!, {
          status: activeTab,
          deliveryDate: deliveryDate || undefined,
          page,
          size: 20,
        })
        .then((r) => r.data),
    enabled: !!companyId,
  });

  return (
    <div className="text-foreground">
      <div className="max-w-4xl mx-auto px-4 py-10 sm:px-6">
        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="space-y-8"
        >
          <motion.div variants={fadeInUp}>
            <SectionHeader
              align="center"
              kicker="Fulfillment"
              title="Orders"
              subtitle="Pack, ship, and track all customer orders."
            />
          </motion.div>

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

          <motion.div
            variants={fadeInUp}
            className="flex flex-wrap items-center gap-3"
          >
            <label
              htmlFor="deliveryDate"
              className="text-xs font-medium text-muted-foreground"
            >
              Delivery date
            </label>
            <input
              id="deliveryDate"
              type="date"
              value={deliveryDate}
              onChange={(e) => {
                setDeliveryDate(e.target.value);
                setPage(0);
              }}
              className="rounded-xl border border-border bg-surface px-3 py-1.5 text-sm text-foreground focus:outline-none focus:border-ring transition-colors"
            />
            {deliveryDate && (
              <button
                onClick={() => {
                  setDeliveryDate("");
                  setPage(0);
                }}
                className="text-xs text-muted-foreground hover:text-foreground underline underline-offset-2 transition"
              >
                Clear
              </button>
            )}
          </motion.div>

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
              <p className="text-muted-foreground">No orders found.</p>
            </motion.div>
          ) : (
            <motion.div variants={stagger} className="space-y-3">
              {data.content.map((order) => (
                <motion.div key={order.orderId} variants={fadeInUp}>
                  <VendorOrderCard
                    order={order}
                    onClick={() =>
                      router.push(`/admin/orders/${order.orderId}`)
                    }
                  />
                </motion.div>
              ))}
            </motion.div>
          )}

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
