import {
  FORCE_ALL_DARK,
  LEGACY_DARK_PREFIXES,
  THEME_STORAGE_KEY,
} from "./themeConfig";

interface ThemeScriptOptions {
  forceAll: boolean;
  forcedPrefixes: readonly string[];
  storageKey: string;
}

/**
 * Builds the script that runs inline in <head> before first paint, so the page
 * never flashes the wrong theme. The output must stay self-contained ES5.
 * Resolution order: forced-dark route, stored preference, OS preference. Keep
 * it in step with `applyTheme` in components/theme/ThemeProvider.tsx.
 */
export function buildThemeScript({
  forceAll,
  forcedPrefixes,
  storageKey,
}: ThemeScriptOptions): string {
  return [
    "(function(){try{",
    "var d=document.documentElement,p=location.pathname;",
    `var F=${JSON.stringify(forcedPrefixes)};`,
    `var f=${forceAll ? "true" : "false"}||F.some(function(x){return p===x||p.indexOf(x+'/')===0});`,
    "var s=null;",
    `try{s=localStorage.getItem(${JSON.stringify(storageKey)})}catch(e){}`,
    "var t=f?'dark':(s==='light'||s==='dark')?s:",
    "(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');",
    "d.classList.toggle('dark',t==='dark');d.style.colorScheme=t;",
    "}catch(e){}})();",
  ].join("");
}

export const themeScript = buildThemeScript({
  forceAll: FORCE_ALL_DARK,
  forcedPrefixes: LEGACY_DARK_PREFIXES,
  storageKey: THEME_STORAGE_KEY,
});
