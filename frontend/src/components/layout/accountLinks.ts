import {
  Bell,
  Bookmark,
  FileText,
  Gift,
  Heart,
  MessageSquare,
  Newspaper,
  Package,
  Settings,
  Star,
  type LucideIcon,
} from "lucide-react";

export interface AccountLink {
  href: string;
  label: string;
  icon: LucideIcon;
}

/** Signed-in customer destinations, shared by the account menu and mobile nav. */
export const ACCOUNT_LINKS: readonly AccountLink[] = [
  { href: "/orders", label: "Orders", icon: Package },
  { href: "/lists", label: "Saved lists", icon: Bookmark },
  { href: "/loyalty", label: "Rewards", icon: Star },
  { href: "/price-watches", label: "Price alerts", icon: Bell },
  { href: "/following", label: "Following", icon: Heart },
  { href: "/feed", label: "Feed", icon: Newspaper },
  { href: "/b2b/quotes", label: "Wholesale quotes", icon: FileText },
  { href: "/gift-cards", label: "Gift cards", icon: Gift },
  { href: "/feedback", label: "My feedback", icon: MessageSquare },
  { href: "/account", label: "Account settings", icon: Settings },
];
