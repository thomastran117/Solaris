"use client";

import { useEffect } from "react";
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";

/** Unexpected rendering failure on a storefront page (backend errors degrade without this). */
export default function StorefrontError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="relative isolate flex min-h-[70vh] items-center justify-center px-6 py-24 text-white">
      <NavyGridGlowBackground />
      <div
        role="alert"
        className="max-w-lg rounded-3xl border border-white/10 bg-white/5 p-10 text-center shadow-2xl shadow-blue-950/40 backdrop-blur-md"
      >
        <h1 className="text-2xl font-semibold tracking-tight">
          Something went wrong
        </h1>
        <p className="mt-4 text-sm leading-relaxed text-white/70">
          We couldn&apos;t load this page. Please try again in a moment.
        </p>
        <button
          type="button"
          onClick={reset}
          className="mt-8 rounded-full bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-300"
        >
          Try again
        </button>
      </div>
    </main>
  );
}
