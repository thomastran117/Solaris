import Link from "next/link";
import { Waves } from "lucide-react";
import { cn } from "../../lib/cn";

/** The ShopWave logo: links home. */
export function Wordmark({ className }: { className?: string }) {
  return (
    <Link
      href="/"
      className={cn(
        "inline-flex items-center gap-2 rounded-md text-base font-semibold tracking-tight text-foreground",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
        className
      )}
    >
      <span className="flex size-7 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <Waves className="size-4" aria-hidden />
      </span>
      ShopWave
    </Link>
  );
}
