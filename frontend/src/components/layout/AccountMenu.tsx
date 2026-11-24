"use client";

import Link from "next/link";
import { LayoutDashboard, LogIn, LogOut } from "lucide-react";
import { useAppSelector } from "../../stores/hooks";
import { useLogout } from "../../hooks/useLogout";
import { cn } from "../../lib/cn";
import { Badge } from "../ui/Badge";
import { Button } from "../ui/Button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "../ui/DropdownMenu";
import { hasAdminAccess } from "../admin/adminNav";
import { ACCOUNT_LINKS } from "./accountLinks";

function initialFor(email: string | null): string {
  return (email?.trim()[0] ?? "?").toUpperCase();
}

export function Avatar({
  email,
  className,
}: {
  email: string | null;
  className?: string;
}) {
  return (
    <span
      aria-hidden
      className={cn(
        "flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold text-foreground",
        className
      )}
    >
      {initialFor(email)}
    </span>
  );
}

/**
 * Signed out: a "Sign in" button. Signed in: an avatar menu with the customer
 * destinations, the seller workspace (when the user has one) and sign out.
 */
export function AccountMenu({
  showSellerLink = true,
}: {
  /** Hide the "Seller dashboard" entry when already inside it. */
  showSellerLink?: boolean;
}) {
  const { accessToken, email, tier, companyId, role } = useAppSelector(
    (state) => state.auth
  );
  const logout = useLogout();

  if (!accessToken) {
    return (
      <Button asChild variant="outline" size="sm">
        <Link href="/login">
          <LogIn aria-hidden />
          Sign in
        </Link>
      </Button>
    );
  }

  const seller = showSellerLink && hasAdminAccess({ companyId, role });

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label="Account menu"
          className={cn(
            "flex items-center rounded-full transition-opacity hover:opacity-80",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
          )}
        >
          <Avatar email={email} />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-60">
        <DropdownMenuLabel className="flex items-center justify-between gap-2 py-2">
          <span className="truncate text-sm font-medium text-foreground">
            {email}
          </span>
          {tier === "PREMIUM" && <Badge variant="primary">Premium</Badge>}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          {ACCOUNT_LINKS.map(({ href, label, icon: Icon }) => (
            <DropdownMenuItem key={href} asChild>
              <Link href={href}>
                <Icon aria-hidden />
                {label}
              </Link>
            </DropdownMenuItem>
          ))}
        </DropdownMenuGroup>
        {seller && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link href="/admin">
                <LayoutDashboard aria-hidden />
                Seller dashboard
              </Link>
            </DropdownMenuItem>
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void logout()}>
          <LogOut aria-hidden />
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
