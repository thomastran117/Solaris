"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { LayoutDashboard, LogIn, LogOut, Menu } from "lucide-react";
import NavLink from "../navigation/NavLink";
import SearchBar from "../search/SearchBar";
import { ThemeToggle } from "../theme/ThemeToggle";
import { IconButton } from "../ui/IconButton";
import {
  Sheet,
  SheetBody,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "../ui/Sheet";
import { Separator } from "../ui/Separator";
import { useAppSelector } from "../../stores/hooks";
import { useLogout } from "../../hooks/useLogout";
import { cn } from "../../lib/cn";
import { hasAdminAccess } from "../admin/adminNav";
import { AccountMenu } from "./AccountMenu";
import { CartButton } from "../cart/CartButton";
import { ACCOUNT_LINKS } from "./accountLinks";
import { Wordmark } from "./Wordmark";

const PRIMARY_LINKS = [
  { href: "/browse", label: "Browse" },
  { href: "/gift-cards", label: "Gift cards" },
] as const;

const desktopLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "rounded-md px-3 py-2 text-sm font-medium transition-colors",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
    isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground"
  );

const mobileLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
    "[&_svg]:size-4 [&_svg]:text-muted-foreground",
    isActive ? "bg-muted text-foreground" : "text-foreground hover:bg-muted"
  );

/** Storefront and account header: logo, primary nav, search, theme, account. */
export function SiteHeader() {
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const search = (q: string) =>
    router.push(`/browse?q=${encodeURIComponent(q)}`);

  return (
    // The one place a backdrop blur is allowed (see DESIGN.md).
    <header className="sticky top-0 z-40 border-b bg-background/85 backdrop-blur supports-[backdrop-filter]:bg-background/70">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center gap-3 lg:gap-6">
          <IconButton
            aria-label="Open menu"
            className="-ml-2 lg:hidden"
            onClick={() => setMobileOpen(true)}
          >
            <Menu aria-hidden />
          </IconButton>

          <Wordmark />

          <nav aria-label="Primary" className="hidden items-center lg:flex">
            {PRIMARY_LINKS.map((link) => (
              <NavLink
                key={link.href}
                to={link.href}
                className={desktopLinkClass}
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          <SearchBar
            onSearch={search}
            placeholder="Search products and brands"
            className="ml-auto hidden w-full max-w-md md:block"
          />

          <div className="ml-auto flex items-center gap-1 md:ml-0">
            <ThemeToggle />
            <CartButton />
            <AccountMenu />
          </div>
        </div>

        <SearchBar
          onSearch={search}
          placeholder="Search products and brands"
          className="pb-3 md:hidden"
        />
      </div>

      <MobileNav open={mobileOpen} onOpenChange={setMobileOpen} />
    </header>
  );
}

function MobileNav({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { accessToken, email, companyId, role } = useAppSelector(
    (state) => state.auth
  );
  const logout = useLogout();
  const close = () => onOpenChange(false);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="left" className="max-w-xs">
        <SheetHeader>
          <SheetTitle asChild>
            <div>
              <Wordmark />
            </div>
          </SheetTitle>
          <SheetDescription className="sr-only">
            Site navigation
          </SheetDescription>
        </SheetHeader>
        <SheetBody className="space-y-4">
          <nav aria-label="Primary" className="grid gap-1">
            {PRIMARY_LINKS.map((link) => (
              <NavLink
                key={link.href}
                to={link.href}
                className={mobileLinkClass}
                onClick={close}
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
          <Separator />
          {accessToken ? (
            <nav aria-label="Account" className="grid gap-1">
              <p className="truncate px-3 pb-1 text-xs font-medium text-muted-foreground">
                {email}
              </p>
              {ACCOUNT_LINKS.map(({ href, label, icon: Icon }) => (
                <NavLink
                  key={href}
                  to={href}
                  className={mobileLinkClass}
                  onClick={close}
                >
                  <Icon aria-hidden />
                  {label}
                </NavLink>
              ))}
              {hasAdminAccess({ companyId, role }) && (
                <NavLink
                  to="/admin"
                  className={mobileLinkClass}
                  onClick={close}
                >
                  <LayoutDashboard aria-hidden />
                  Seller dashboard
                </NavLink>
              )}
              <button
                type="button"
                className={mobileLinkClass({ isActive: false })}
                onClick={() => {
                  close();
                  void logout();
                }}
              >
                <LogOut aria-hidden />
                Sign out
              </button>
            </nav>
          ) : (
            <Link
              href="/login"
              className={mobileLinkClass({ isActive: false })}
              onClick={close}
            >
              <LogIn aria-hidden />
              Sign in
            </Link>
          )}
        </SheetBody>
      </SheetContent>
    </Sheet>
  );
}
