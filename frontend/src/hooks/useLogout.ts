"use client";

import { useCallback } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import api from "../api";
import { useAppDispatch } from "../stores/hooks";
import { clearCredentials } from "../stores/authSlice";

/**
 * Signs out: revokes the session server-side (best effort), clears local
 * credentials and every cached server response, then goes to /login.
 */
export function useLogout(): () => Promise<void> {
  const router = useRouter();
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();

  return useCallback(async () => {
    try {
      await api.post("/auth/logout");
    } catch {
      // The token may already be expired; local state is cleared regardless.
    }
    dispatch(clearCredentials());
    // Drop every cached response so the next user never sees this one's data.
    queryClient.clear();
    router.push("/login");
  }, [dispatch, queryClient, router]);
}
