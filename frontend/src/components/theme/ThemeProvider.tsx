"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
} from "react";
import {
  THEME_STORAGE_KEY,
  isThemePreference,
  type ResolvedTheme,
  type ThemePreference,
} from "../../lib/theme/themeConfig";

interface ThemeContextValue {
  /** What the user picked (or "system" when they haven't). */
  preference: ThemePreference;
  /** The theme actually applied to the document. */
  resolved: ResolvedTheme;
  setTheme: (preference: ThemePreference) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

const DARK_QUERY = "(prefers-color-scheme: dark)";
const PREFERENCE_EVENT = "shopwave-theme-change";

// Used when localStorage is unavailable (private mode, blocked site data): the
// choice then lasts for this page view only.
let memoryPreference: ThemePreference | null = null;

function readPreference(): ThemePreference {
  if (memoryPreference) return memoryPreference;
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : "system";
  } catch {
    return "system";
  }
}

function subscribePreference(onChange: () => void): () => void {
  const onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key === THEME_STORAGE_KEY) onChange();
  };
  window.addEventListener("storage", onStorage);
  window.addEventListener(PREFERENCE_EVENT, onChange);
  return () => {
    window.removeEventListener("storage", onStorage);
    window.removeEventListener(PREFERENCE_EVENT, onChange);
  };
}

function readSystemDark(): boolean {
  return typeof window.matchMedia === "function"
    ? window.matchMedia(DARK_QUERY).matches
    : false;
}

function subscribeSystem(onChange: () => void): () => void {
  if (typeof window.matchMedia !== "function") return () => {};
  const query = window.matchMedia(DARK_QUERY);
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

/** Mirrors the inline script in lib/theme/themeScript.ts. */
export function applyTheme(theme: ResolvedTheme): void {
  const root = document.documentElement;
  if (root.classList.contains("dark") === (theme === "dark")) {
    root.style.colorScheme = theme;
    return;
  }
  root.setAttribute("data-theme-switching", "");
  root.classList.toggle("dark", theme === "dark");
  root.style.colorScheme = theme;
  // Re-enable transitions after the new colours have painted.
  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      root.removeAttribute("data-theme-switching");
    });
  });
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const preference = useSyncExternalStore<ThemePreference>(
    subscribePreference,
    readPreference,
    () => "system"
  );
  const systemDark = useSyncExternalStore(
    subscribeSystem,
    readSystemDark,
    () => false
  );

  const resolved: ResolvedTheme =
    preference === "system" ? (systemDark ? "dark" : "light") : preference;

  useEffect(() => {
    applyTheme(resolved);
  }, [resolved]);

  const setTheme = useCallback((next: ThemePreference) => {
    try {
      if (next === "system") {
        window.localStorage.removeItem(THEME_STORAGE_KEY);
      } else {
        window.localStorage.setItem(THEME_STORAGE_KEY, next);
      }
      memoryPreference = null;
    } catch {
      memoryPreference = next;
    }
    window.dispatchEvent(new Event(PREFERENCE_EVENT));
  }, []);

  const value = useMemo(
    () => ({ preference, resolved, setTheme }),
    [preference, resolved, setTheme]
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme must be used inside <ThemeProvider>");
  }
  return context;
}
