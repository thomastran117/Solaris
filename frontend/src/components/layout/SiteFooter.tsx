import Link from "next/link";
import { Wordmark } from "./Wordmark";

const COLUMNS = [
  {
    title: "Shop",
    links: [
      { href: "/browse", label: "Browse products" },
      { href: "/gift-cards", label: "Gift cards" },
    ],
  },
  {
    title: "Your account",
    links: [
      { href: "/orders", label: "Orders" },
      { href: "/lists", label: "Saved lists" },
      { href: "/loyalty", label: "Rewards" },
      { href: "/account", label: "Account settings" },
    ],
  },
  {
    title: "Sellers",
    links: [{ href: "/admin", label: "Seller dashboard" }],
  },
  {
    title: "Legal",
    links: [
      { href: "/privacy", label: "Privacy policy" },
      { href: "/terms-and-conditions", label: "Terms and conditions" },
    ],
  },
] as const;

export function SiteFooter() {
  return (
    <footer className="border-t bg-surface">
      <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="grid gap-10 lg:grid-cols-[1.5fr_repeat(4,1fr)]">
          <div className="space-y-3">
            <Wordmark />
            <p className="max-w-xs text-sm text-muted-foreground">
              Products, merchants and curated collections in one marketplace.
            </p>
          </div>
          {COLUMNS.map((column) => (
            <nav key={column.title} aria-label={column.title}>
              <h2 className="text-sm font-semibold text-foreground">
                {column.title}
              </h2>
              <ul className="mt-3 space-y-2">
                {column.links.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="rounded-sm text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>
        <div className="mt-12 border-t pt-6 text-sm text-muted-foreground">
          © {new Date().getFullYear()} ShopWave
        </div>
      </div>
    </footer>
  );
}
