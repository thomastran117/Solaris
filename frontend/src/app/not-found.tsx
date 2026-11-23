import type { Metadata } from "next";
import Link from "next/link";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";

export const metadata: Metadata = {
  title: "Page not found",
  robots: { index: false },
};

export default function NotFound() {
  return (
    <main className="relative isolate flex min-h-[70vh] items-center justify-center px-6 py-24 text-white">
      <NavyGridGlowBackground />
      <div className="max-w-lg rounded-3xl border border-white/10 bg-white/5 p-10 text-center shadow-2xl shadow-blue-950/40 backdrop-blur-md">
        <p className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
          404
        </p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight">
          Page not found
        </h1>
        <p className="mt-4 text-sm leading-relaxed text-white/70">
          The page you are looking for does not exist or has moved.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link
            href="/"
            className="rounded-full bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-300"
          >
            Go home
          </Link>
          <Link
            href="/browse"
            className="rounded-full border border-white/15 bg-white/5 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-white/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-300"
          >
            Browse products
          </Link>
        </div>
      </div>
    </main>
  );
}
