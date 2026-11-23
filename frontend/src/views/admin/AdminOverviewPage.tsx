"use client";

import Link from "next/link";
import { ChevronRight, Store } from "lucide-react";
import { useAdminNav } from "../../components/admin/useAdminNav";
import { Button } from "../../components/ui/Button";
import { EmptyState } from "../../components/ui/EmptyState";
import { PageHeader } from "../../components/ui/PageHeader";
import { Skeleton } from "../../components/ui/Skeleton";
import { cn } from "../../lib/cn";

/** Landing page of the seller workspace: every area this user can open. */
export default function AdminOverviewPage() {
  const { groups, loading } = useAdminNav();
  // The overview links to everything except itself.
  const sections = groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => item.href !== "/admin"),
    }))
    .filter((group) => group.items.length > 0);

  return (
    <div className="mx-auto max-w-6xl space-y-8 px-4 py-8 sm:px-6">
      <PageHeader
        title="Seller dashboard"
        description="Manage your catalog, orders, inventory and store settings."
      />

      {loading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy>
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
      ) : sections.length === 0 ? (
        <EmptyState
          icon={Store}
          title="No seller access yet"
          description="Your account isn't a member of a store. Ask a store owner to invite you, or keep shopping."
          action={
            <Button asChild>
              <Link href="/browse">Browse products</Link>
            </Button>
          }
        />
      ) : (
        sections.map((group) => (
          <section key={group.label} aria-labelledby={`admin-${group.label}`}>
            <h2
              id={`admin-${group.label}`}
              className="mb-3 text-sm font-semibold text-foreground"
            >
              {group.label}
            </h2>
            <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {group.items.map(({ href, label, description, icon: Icon }) => (
                <li key={href}>
                  <Link
                    href={href}
                    className={cn(
                      "group flex h-full items-start gap-3 rounded-xl border bg-surface p-4 shadow-xs transition-colors",
                      "hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    )}
                  >
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                      <Icon className="size-4 text-foreground" aria-hidden />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium text-foreground">
                        {label}
                      </span>
                      <span className="mt-0.5 block text-sm text-muted-foreground">
                        {description}
                      </span>
                    </span>
                    <ChevronRight
                      className="mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none"
                      aria-hidden
                    />
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </div>
  );
}
