import { motion } from "framer-motion";
import { Package, Truck, MapPin } from "lucide-react";
import type {
  Order,
  CompanyOrder,
  OrderStatus,
  FulfillmentMethod,
} from "../../types/order";

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
  RESERVED: "text-muted-foreground bg-muted",
  PAID: "text-link bg-primary/10",
  PACKED: "text-link bg-primary/10",
  PARTIALLY_FULFILLED: "text-warning bg-warning-subtle",
  SHIPPED: "text-link bg-primary/10",
  DELIVERED: "text-success bg-success-subtle",
  RETURNED: "text-warning bg-warning-subtle",
  FAILED: "text-danger bg-danger-subtle",
  CANCELLED: "text-danger bg-danger-subtle",
  REFUNDED: "text-warning bg-warning-subtle",
  UNDER_REVIEW: "text-warning bg-warning-subtle",
};

function FulfillmentBadge({ method }: { method: FulfillmentMethod }) {
  return method === "PICKUP" ? (
    <span className="flex items-center gap-1 text-xs text-link bg-primary/10 border border-border rounded-full px-2 py-0.5">
      <MapPin className="h-3 w-3" /> Pickup
    </span>
  ) : (
    <span className="flex items-center gap-1 text-xs text-muted-foreground bg-surface border border-border rounded-full px-2 py-0.5">
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
    year: "numeric",
    month: "short",
    day: "numeric",
  });

  return (
    <motion.div
      whileHover={{ y: -3 }}
      onClick={onClick}
      className="rounded-xl border border-border bg-surface shadow-sm hover:shadow-md p-5 cursor-pointer"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="bg-primary/10 border border-border rounded-xl p-2">
            <Package className="h-4 w-4 text-link" />
          </div>
          <div>
            <p className="text-sm font-semibold text-foreground">#{shortId}</p>
            <p className="text-xs text-muted-foreground">{date}</p>
          </div>
        </div>
        <span
          className={`text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_COLORS[status] ?? "text-muted-foreground bg-muted"}`}
        >
          {STATUS_LABELS[status] ?? status}
        </span>
      </div>
      <div className="mt-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FulfillmentBadge method={order.fulfillmentMethod} />
          <span className="text-xs text-muted-foreground">
            {itemCount} item{itemCount !== 1 ? "s" : ""}
          </span>
        </div>
        <p className="text-lg font-semibold text-foreground">
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
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  const buyerShort = order.buyerUserId.slice(0, 8);

  return (
    <motion.div
      whileHover={{ y: -3 }}
      onClick={onClick}
      className="rounded-xl border border-border bg-surface shadow-sm hover:shadow-md p-5 cursor-pointer"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="bg-primary/10 border border-border rounded-xl p-2">
            <Package className="h-4 w-4 text-link" />
          </div>
          <div>
            <p className="text-sm font-semibold text-foreground">#{shortId}</p>
            <p className="text-xs text-muted-foreground">
              Buyer: {buyerShort}… · {date}
            </p>
          </div>
        </div>
        <span
          className={`text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_COLORS[status] ?? "text-muted-foreground bg-muted"}`}
        >
          {STATUS_LABELS[status] ?? status}
        </span>
      </div>
      <div className="mt-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FulfillmentBadge method={order.fulfillmentMethod} />
          <span className="text-xs text-muted-foreground">
            {itemCount} item{itemCount !== 1 ? "s" : ""}
          </span>
        </div>
        <p className="text-lg font-semibold text-foreground">
          {order.currency} {order.companyItemsTotal.toFixed(2)}
        </p>
      </div>
    </motion.div>
  );
}
