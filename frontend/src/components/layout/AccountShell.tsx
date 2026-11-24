"use client";

import type { ReactNode } from "react";
import NavLink from "../navigation/NavLink";
import { Container } from "../ui/Container";
import { useAppSelector } from "../../stores/hooks";
import { cn } from "../../lib/cn";
import { ACCOUNT_LINKS } from "./accountLinks";

/** Customer-account links; gift cards live on the storefront. */
const LINKS = ACCOUNT_LINKS.filter((link) => link.href !== "/gift-cards");

const sideLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
    "[&_svg]:size-4 [&_svg]:shrink-0",
    isActive
      ? "bg-muted text-foreground"
      : "text-muted-foreground hover:bg-muted hover:text-foreground"
  );

const tabLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "-mb-px inline-flex shrink-0 items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring",
    "[&_svg]:size-4",
    isActive
      ? "border-primary text-foreground"
      : "border-transparent text-muted-foreground hover:text-foreground"
  );

/**
 * Layout for signed-in customer pages: a sidebar of account destinations on
 * large screens and a scrollable tab strip on small ones. Wraps the page
 * content only; the site header and footer come from SiteShell.
 */
export function AccountShell({ children }: { children: ReactNode }) {
  const email = useAppSelector((state) => state.auth.email);

  return (
    <Container className="py-6 lg:py-10">
      <div className="lg:grid lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-10">
        <aside className="hidden lg:block">
          <div className="sticky top-24 space-y-4">
            <div className="px-3">
              <p className="text-sm font-semibold text-foreground">
                Your account
              </p>
              {email && (
                <p className="truncate text-xs text-muted-foreground">
                  {email}
                </p>
              )}
            </div>
            <nav aria-label="Account">
              <ul className="space-y-0.5">
                {LINKS.map(({ href, label, icon: Icon }) => (
                  <li key={href}>
                    <NavLink to={href} className={sideLinkClass}>
                      <Icon aria-hidden />
                      {label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>
          </div>
        </aside>

        <nav
          aria-label="Account"
          className="-mx-4 mb-6 overflow-x-auto border-b px-4 [scrollbar-width:none] sm:-mx-6 sm:px-6 lg:hidden"
        >
          <ul className="flex gap-1">
            {LINKS.map(({ href, label, icon: Icon }) => (
              <li key={href}>
                <NavLink to={href} className={tabLinkClass}>
                  <Icon aria-hidden />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="min-w-0">{children}</div>
      </div>
    </Container>
  );
}
