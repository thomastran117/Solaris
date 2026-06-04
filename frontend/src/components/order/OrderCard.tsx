import { motion } from "framer-motion";
import { Package, Truck, MapPin } from "lucide-react";
import type { Order, CompanyOrder, OrderStatus, FulfillmentMethod } from "../../types/order";

const STATUS_LABELS: Record<OrderStatus, string> = {
  RESERVED: "Reserved",
  PAID: "Processing",
  PACKED: "Packed",
  PARTIALLY_FULFILLED: "Partially Shipped",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  RETURNED: "Returned",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
  REFUNDED: "Refunded",
  UNDER_REVIEW: "Under Review",
};

const STATUS_COLORS: Record<OrderStatus, string> = {
  RESERVED: "text-white/60 bg-white/10",
  PAID: "text-blue-300 bg-blue-500/15",
  PACKED: "text-sky-300 bg-sky-500/15",
  PARTIALLY_FULFILLED: "text-yellow-300 bg-yellow-500/15",
  SHIPPED: "text-indigo-300 bg-indigo-500/15",
  DELIVERED: "text-green-300 bg-green-500/15",
  RETURNED: "text-orange-300 bg-orange-500/15",
  FAILED: "text-red-300 bg-red-500/15",
  CANCELLED: "text-red-300 bg-red-500/15",
  REFUNDED: "text-orange-300 bg-orange-500/15",
  UNDER_REVIEW: "text-yellow-300 bg-yellow-500/15",
};

function FulfillmentBadge({ method }: { method: FulfillmentMethod }) {
  return method === "PICKUP" ? (
    <span className="flex items-center gap-1 text-xs text-sky-200/80 bg-sky-500/10 border border-white/10 rounded-full px-2 py-0.5">
      <MapPin className="h-3 w-3" /> Pickup
    </span>
  ) : (
    <span className="flex items-center gap-1 text-xs text-white/60 bg-white/5 border border-white/10 rounded-full px-2 py-0.5">
      <Truck className="h-3 w-3" /> Delivery
    </span>
  );
}

interface CustomerOrderCardProps {
  order: Order;
  onClick?: () => void;
}

export function CustomerOrderCard({ order, onClick }: CustomerOrderCardProps) {
  const status = order.status;
  const itemCount = order.items.reduce((sum, i) => sum + i.quantity, 0);
  const shortId = order.id.slice(0, 8).toUpperCase();
  const date = new Date(order.createdAt).toLocaleDateString(undefined, {
    year: "numeric", month: "short", day: "numeric",
  });

  return (
    <motion.div
      whileHover={{ y: -3 }}
      onClick={onClick}
      className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md p-5 cursor-pointer"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2">
            <Package className="h-4 w-4 text-sky-200" />
          </div>
          <div>
            <p className="text-sm font-semibold text-white">#{shortId}</p>
            <p className="text-xs text-white/60">{date}</p>
          </div>
        </div>
        <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_COLORS[status] ?? "text-white/60 bg-white/10"}`}>
          {STATUS_LABELS[status] ?? status}
        </span>
      </div>
      <div className="mt-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FulfillmentBadge method={order.fulfillmentMethod} />
          <span className="text-xs text-white/60">{itemCount} item{itemCount !== 1 ? "s" : ""}</span>
        </div>
        <p className="text-lg font-extrabold text-white">
          {order.currency} {order.totalAmount.toFixed(2)}
        </p>
      </div>
    </motion.div>
  );
}

interface VendorOrderCardProps {
  order: CompanyOrder;
  onClick?: () => void;
}

export function VendorOrderCard({ order, onClick }: VendorOrderCardProps) {
  const status = order.orderStatus;
  const itemCount = order.items.reduce((sum, i) => sum + i.quantity, 0);
  const shortId = order.orderId.slice(0, 8).toUpperCase();
  const date = new Date(order.createdAt).toLocaleDateString(undefined, {
    year: "numeric", month: "short", day: "numeric",
  });
  const buyerShort = order.buyerUserId.slice(0, 8);

  return (
    <motion.div
      whileHover={{ y: -3 }}
      onClick={onClick}
      className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md p-5 cursor-pointer"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2">
            <Package className="h-4 w-4 text-sky-200" />
          </div>
          <div>
            <p className="text-sm font-semibold text-white">#{shortId}</p>
            <p className="text-xs text-white/60">Buyer: {buyerShort}… · {date}</p>
          </div>
        </div>
        <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_COLORS[status] ?? "text-white/60 bg-white/10"}`}>
          {STATUS_LABELS[status] ?? status}
        </span>
      </div>
      <div className="mt-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FulfillmentBadge method={order.fulfillmentMethod} />
          <span className="text-xs text-white/60">{itemCount} item{itemCount !== 1 ? "s" : ""}</span>
        </div>
        <p className="text-lg font-extrabold text-white">
          {order.currency} {order.companyItemsTotal.toFixed(2)}
        </p>
      </div>
    </motion.div>
  );
}
