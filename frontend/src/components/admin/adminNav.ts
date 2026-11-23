import {
  BarChart3,
  Boxes,
  ClipboardList,
  FileSpreadsheet,
  FileText,
  Gift,
  LayoutDashboard,
  Megaphone,
  MessageSquare,
  Package,
  PackagePlus,
  Repeat,
  Send,
  ShieldAlert,
  ShoppingBag,
  Sparkles,
  Truck,
  Users,
  Webhook,
  type LucideIcon,
} from "lucide-react";
import type { CompanyCapability } from "../../api/team";

/** Platform staff roles accepted by staff-only endpoints. */
export const STAFF_ROLES = ["SUPPORT", "MODERATOR", "ADMIN"] as const;

export interface AdminNavItem {
  href: string;
  label: string;
  description: string;
  icon: LucideIcon;
  /** Match the path exactly (for index routes). */
  end?: boolean;
  /**
   * Company items: shown to members of a company holding any of these
   * capabilities. These mirror the backend checks, which remain authoritative.
   */
  capabilities?: readonly CompanyCapability[];
  /** Platform items: shown to users with any of these platform roles. */
  roles?: readonly string[];
}

export interface AdminNavGroup {
  /** Omitted for the top, unlabelled group. */
  label?: string;
  items: readonly AdminNavItem[];
}

export const ADMIN_NAV: readonly AdminNavGroup[] = [
  {
    items: [
      {
        href: "/admin",
        label: "Overview",
        description: "Jump to the areas you manage.",
        icon: LayoutDashboard,
        end: true,
      },
    ],
  },
  {
    label: "Catalog",
    items: [
      {
        href: "/admin/products",
        label: "Products",
        description: "Create and edit products, variants and stock.",
        icon: Package,
        capabilities: ["MANAGE_PRODUCTS", "READ_PRODUCTS", "FULFILL_ORDERS"],
      },
      {
        href: "/admin/bundles",
        label: "Bundles",
        description: "Sell products together at a bundle price.",
        icon: Boxes,
        capabilities: ["MANAGE_PRODUCTS"],
      },
      {
        href: "/admin/kits",
        label: "Kits",
        description: "Configurable kits with selectable components.",
        icon: PackagePlus,
        capabilities: ["MANAGE_PRODUCTS", "READ_PRODUCTS"],
      },
      {
        href: "/admin/collections",
        label: "Collections",
        description: "Curate products into featured collections.",
        icon: Sparkles,
        capabilities: ["MANAGE_PRODUCTS", "READ_PRODUCTS"],
      },
      {
        href: "/admin/bulk-import",
        label: "Bulk import",
        description: "Import products and stock from CSV.",
        icon: FileSpreadsheet,
        capabilities: ["MANAGE_PRODUCTS", "MANAGE_INVENTORY"],
      },
    ],
  },
  {
    label: "Orders",
    items: [
      {
        href: "/admin/orders",
        label: "Orders",
        description: "Fulfil, ship and refund customer orders.",
        icon: ShoppingBag,
        capabilities: ["FULFILL_ORDERS"],
      },
      {
        href: "/admin/drivers",
        label: "Delivery dispatch",
        description: "Assign drivers and track local deliveries.",
        icon: Truck,
        capabilities: ["FULFILL_ORDERS"],
      },
      {
        href: "/admin/b2b-quotes",
        label: "B2B quotes",
        description: "Respond to wholesale quote requests.",
        icon: FileText,
        capabilities: ["MANAGE_QUOTES"],
      },
    ],
  },
  {
    label: "Inventory",
    items: [
      {
        href: "/admin/purchase-orders",
        label: "Purchase orders",
        description: "Order stock from suppliers and receive it.",
        icon: ClipboardList,
        capabilities: ["MANAGE_INVENTORY"],
      },
      {
        href: "/admin/inventory-transfers",
        label: "Transfers",
        description: "Move stock between locations.",
        icon: Repeat,
        capabilities: ["MANAGE_INVENTORY"],
      },
    ],
  },
  {
    label: "Growth",
    items: [
      {
        href: "/admin/marketing",
        label: "Marketing",
        description: "Automated customer marketing workflows.",
        icon: Send,
        capabilities: ["MANAGE_PROMOTIONS"],
      },
      {
        href: "/admin/loyalty",
        label: "Loyalty",
        description: "Points, tiers and rewards for your customers.",
        icon: Gift,
        capabilities: ["MANAGE_PROMOTIONS"],
      },
      {
        href: "/admin/announcements",
        label: "Announcements",
        description: "Banners and news on your storefront.",
        icon: Megaphone,
        capabilities: ["MANAGE_PRODUCTS", "READ_PRODUCTS"],
      },
    ],
  },
  {
    label: "Insights",
    items: [
      {
        href: "/dashboard",
        label: "Analytics",
        description: "Revenue, product performance and forecasts.",
        icon: BarChart3,
        capabilities: ["READ_ANALYTICS"],
      },
      {
        href: "/admin/reports",
        label: "Reports",
        description: "Pricing and sales reports.",
        icon: FileText,
        capabilities: ["READ_ANALYTICS"],
      },
    ],
  },
  {
    label: "Settings",
    items: [
      {
        href: "/admin/team",
        label: "Team",
        description: "Invite members and manage their roles.",
        icon: Users,
        capabilities: ["MANAGE_COMPANY"],
      },
      {
        href: "/admin/webhooks",
        label: "Webhooks",
        description: "Send store events to your own systems.",
        icon: Webhook,
        capabilities: ["MANAGE_COMPANY"],
      },
    ],
  },
  {
    label: "Platform",
    items: [
      {
        href: "/admin/disputes",
        label: "Chargebacks",
        description: "Review and respond to payment disputes.",
        icon: ShieldAlert,
        // Disputes are raised against the platform account, not a company.
        roles: STAFF_ROLES,
      },
      {
        href: "/admin/feedback",
        label: "Feedback",
        description: "Triage feedback submitted by users.",
        icon: MessageSquare,
        roles: ["ADMIN"],
      },
    ],
  },
] as const;

export interface AdminAccess {
  companyId: string | null;
  role: string | null;
  capabilities: readonly CompanyCapability[];
}

export function canSeeAdminItem(
  item: AdminNavItem,
  access: AdminAccess
): boolean {
  if (item.roles) return !!access.role && item.roles.includes(access.role);
  if (item.capabilities) {
    return (
      !!access.companyId &&
      item.capabilities.some((cap) => access.capabilities.includes(cap))
    );
  }
  return true;
}

/** The nav groups (and items) this user may see; empty groups are dropped. */
export function visibleAdminNav(access: AdminAccess): AdminNavGroup[] {
  return ADMIN_NAV.map((group) => ({
    ...group,
    items: group.items.filter((item) => canSeeAdminItem(item, access)),
  })).filter((group) => group.items.length > 0);
}

/** Whether to offer the seller/staff workspace at all. */
export function hasAdminAccess(
  access: Pick<AdminAccess, "companyId" | "role">
): boolean {
  return (
    !!access.companyId ||
    (!!access.role && (STAFF_ROLES as readonly string[]).includes(access.role))
  );
}
