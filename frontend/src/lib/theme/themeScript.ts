import { THEME_STORAGE_KEY } from "./themeConfig";

/**
 * Builds the script that runs inline in <head> before first paint, so the page
 * never flashes the wrong theme. The output must stay self-contained ES5.
 * Resolution order: stored preference, then OS preference. Keep it in step with
 * `applyTheme` in components/theme/ThemeProvider.tsx.
 */
export function buildThemeScript(storageKey: string): string {
  return [
    "(function(){try{",
    "var d=document.documentElement,s=null;",
    `try{s=localStorage.getItem(${JSON.stringify(storageKey)})}catch(e){}`,
    "var t=(s==='light'||s==='dark')?s:",
    "(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');",
    "d.classList.toggle('dark',t==='dark');d.style.colorScheme=t;",
    "}catch(e){}})();",
  ].join("");
}

export const themeScript = buildThemeScript(THEME_STORAGE_KEY);
