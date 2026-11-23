/**
 * Test double for `next/link`: a plain anchor that routes through the mocked
 * `next/navigation` router on a normal left click, so tests can assert navigation.
 */
import { forwardRef, type AnchorHTMLAttributes, type MouseEvent } from "react";
import { mockRouter } from "./nextNavigation";

type Props = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "href"> & {
  href: string | { pathname?: string; search?: string };
  replace?: boolean;
  scroll?: boolean;
  prefetch?: boolean | null;
};

const MockLink = forwardRef<HTMLAnchorElement, Props>(function MockLink(
  { href, replace, scroll: _scroll, prefetch: _prefetch, onClick, ...rest },
  ref
) {
  const url =
    typeof href === "string"
      ? href
      : `${href.pathname ?? ""}${href.search ?? ""}`;
  const handleClick = (e: MouseEvent<HTMLAnchorElement>) => {
    onClick?.(e);
    if (
      e.defaultPrevented ||
      e.button !== 0 ||
      e.metaKey ||
      e.ctrlKey ||
      e.shiftKey
    )
      return;
    e.preventDefault();
    if (replace) mockRouter.replace(url);
    else mockRouter.push(url);
  };
  return <a ref={ref} href={url} onClick={handleClick} {...rest} />;
});

export default MockLink;
