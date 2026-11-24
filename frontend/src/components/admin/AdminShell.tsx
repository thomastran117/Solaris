"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowLeft, Menu } from "lucide-react";
import NavLink, { isNavLinkActive } from "../navigation/NavLink";
import FeedbackButton from "../feedback/FeedbackButton";
import { ThemeToggle } from "../theme/ThemeToggle";
import { AccountMenu } from "../layout/AccountMenu";
import { Wordmark } from "../layout/Wordmark";
import { Button } from "../ui/Button";
import { IconButton } from "../ui/IconButton";
import { Skeleton } from "../ui/Skeleton";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "../ui/Sheet";
import { cn } from "../../lib/cn";
import { useAdminNav } from "./useAdminNav";
import type { AdminNavGroup } from "./adminNav";

const itemClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
    "[&_svg]:size-4 [&_svg]:shrink-0",
    isActive
      ? "bg-muted text-foreground [&_svg]:text-foreground"
      : "text-muted-foreground hover:bg-muted hover:text-foreground"
  );

function SidebarNav({
  groups,
  loading,
  onNavigate,
}: {
  groups: AdminNavGroup[];
  loading: boolean;
  onNavigate?: () => void;
}) {
  return (
    <nav aria-label="Seller dashboard" className="space-y-6">
      {groups.map((group, index) => (
        <div key={group.label ?? index}>
          {group.label && (
            <h2 className="px-3 pb-2 text-xs font-medium text-muted-foreground">
              {group.label}
            </h2>
          )}
          <ul className="space-y-0.5">
            {group.items.map(({ href, label, icon: Icon, end }) => (
              <li key={href}>
                <NavLink
                  to={href}
                  end={end}
                  className={itemClass}
                  onClick={onNavigate}
                >
                  <Icon aria-hidden />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
      {loading && (
        <div className="space-y-2 px-3" aria-hidden>
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-5 w-full" />
          ))}
        </div>
      )}
    </nav>
  );
}

/** Title of the current section, for the top bar on small screens. */
function currentLabel(pathname: string, groups: AdminNavGroup[]): string {
  let best: { label: string; length: number } | null = null;
  for (const group of groups) {
    for (const item of group.items) {
      if (
        isNavLinkActive(pathname, item.href, item.end) &&
        item.href.length > (best?.length ?? -1)
      ) {
        best = { label: item.label, length: item.href.length };
      }
    }
  }
  return best?.label ?? "Seller dashboard";
}

/** Sidebar layout for the merchant and staff workspace (/admin, /dashboard). */
export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname() ?? "/admin";
  const { groups, loading } = useAdminNav();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="flex min-h-dvh">
      <aside className="sticky top-0 hidden h-dvh w-60 shrink-0 flex-col border-r bg-surface lg:flex">
        <div className="flex h-14 items-center border-b px-4">
          <Wordmark />
        </div>
        <div className="flex-1 overflow-y-auto px-3 py-4">
          <SidebarNav groups={groups} loading={loading} />
        </div>
        <div className="border-t p-3">
          <Button
            asChild
            variant="ghost"
            size="sm"
            className="w-full justify-start"
          >
            <Link href="/">
              <ArrowLeft aria-hidden />
              Back to store
            </Link>
          </Button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-40 flex h-14 items-center gap-2 border-b bg-background px-4 sm:px-6">
          <IconButton
            aria-label="Open navigation"
            className="-ml-2 lg:hidden"
            onClick={() => setMobileOpen(true)}
          >
            <Menu aria-hidden />
          </IconButton>
          <p className="truncate text-sm font-medium text-foreground lg:hidden">
            {currentLabel(pathname, groups)}
          </p>
          <div className="ml-auto flex items-center gap-1">
            <Button
              asChild
              variant="ghost"
              size="sm"
              className="hidden sm:inline-flex lg:hidden"
            >
              <Link href="/">View store</Link>
            </Button>
            <ThemeToggle />
            <AccountMenu showSellerLink={false} />
          </div>
        </header>
        <main
          id="main"
          tabIndex={-1}
          className="flex-1 overflow-x-clip focus:outline-none"
        >
          {children}
        </main>
      </div>

      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="left" className="max-w-xs">
          <div className="flex h-14 items-center border-b px-4">
            <SheetTitle asChild>
              <div>
                <Wordmark />
              </div>
            </SheetTitle>
            <SheetDescription className="sr-only">
              Seller dashboard navigation
            </SheetDescription>
          </div>
          <div className="flex-1 overflow-y-auto px-3 py-4">
            <SidebarNav
              groups={groups}
              loading={loading}
              onNavigate={() => setMobileOpen(false)}
            />
          </div>
          <div className="border-t p-3">
            <Button
              asChild
              variant="ghost"
              size="sm"
              className="w-full justify-start"
            >
              <Link href="/" onClick={() => setMobileOpen(false)}>
                <ArrowLeft aria-hidden />
                Back to store
              </Link>
            </Button>
          </div>
        </SheetContent>
      </Sheet>

      <FeedbackButton />
    </div>
  );
}
