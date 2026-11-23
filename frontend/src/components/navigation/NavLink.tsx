"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentProps, ReactNode } from "react";

type ClassName = string | ((state: { isActive: boolean }) => string);

type NavLinkProps = Omit<
  ComponentProps<typeof Link>,
  "href" | "className" | "children"
> & {
  to: string;
  /** Match the path exactly instead of as a prefix (React Router's `end`). */
  end?: boolean;
  className?: ClassName;
  children?: ReactNode;
};

export function isNavLinkActive(
  pathname: string,
  to: string,
  end = false
): boolean {
  const target = to.split(/[?#]/)[0] || "/";
  if (target === "/") return pathname === "/";
  if (pathname === target) return true;
  return (
    !end && pathname.startsWith(target.endsWith("/") ? target : `${target}/`)
  );
}

/** `next/link` with React Router–style active state (`className` callback, `aria-current`). */
export default function NavLink({
  to,
  end,
  className,
  children,
  ...rest
}: NavLinkProps) {
  const pathname = usePathname() ?? "/";
  const isActive = isNavLinkActive(pathname, to, end);
  const resolved =
    typeof className === "function" ? className({ isActive }) : className;
  return (
    <Link
      href={to}
      className={resolved}
      aria-current={isActive ? "page" : undefined}
      {...rest}
    >
      {children}
    </Link>
  );
}
