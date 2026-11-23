"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import { useSelector } from "react-redux";
import { motion, useReducedMotion } from "framer-motion";
import { MessageSquare } from "lucide-react";
import type { RootState } from "../../stores";
import FeedbackModal from "./FeedbackModal";

export default function FeedbackButton() {
  const accessToken = useSelector((state: RootState) => state.auth.accessToken);
  const [open, setOpen] = useState(false);
  const reduced = useReducedMotion();
  const pathname = usePathname();
  const [pageContext, setPageContext] = useState("");

  if (!accessToken) return null;

  return (
    <>
      <motion.button
        onClick={() => {
          // Capture the full URL at click time; reading window during render breaks SSR.
          setPageContext(pathname + window.location.search);
          setOpen(true);
        }}
        initial={reduced ? { opacity: 1 } : { opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        whileHover={reduced ? undefined : { scale: 1.05 }}
        whileTap={reduced ? undefined : { scale: 0.95 }}
        className="fixed bottom-6 right-6 z-50 flex items-center gap-2 rounded-full
                   bg-blue-600 hover:bg-blue-500 px-4 py-2.5 text-sm font-semibold
                   text-white shadow-lg shadow-blue-600/25 transition-colors"
        aria-label="Share feedback"
      >
        <MessageSquare className="h-4 w-4" />
        Feedback
      </motion.button>

      <FeedbackModal
        open={open}
        onClose={() => setOpen(false)}
        pageContext={pageContext}
      />
    </>
  );
}
