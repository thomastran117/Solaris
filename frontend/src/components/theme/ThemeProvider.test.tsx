import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setMockUrl } from "../../test/nextNavigation";

// Exercise the real resolution logic rather than the temporary
// force-everything-dark migration setting.
vi.mock("../../lib/theme/themeConfig", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("../../lib/theme/themeConfig")>();
  const prefixes = ["/admin"];
  return {
    ...actual,
    FORCE_ALL_DARK: false,
    LEGACY_DARK_PREFIXES: prefixes,
    isForcedDarkPath: (pathname: string) =>
      prefixes.some((p) => pathname === p || pathname.startsWith(`${p}/`)),
  };
});

const { ThemeProvider, useTheme } = await import("./ThemeProvider");
const { ThemeToggle } = await import("./ThemeToggle");

function Probe() {
  const { preference, resolved, forced } = useTheme();
  return (
    <output data-testid="probe">
      {preference}/{resolved}/{forced ? "forced" : "free"}
    </output>
  );
}

function renderTheme() {
  return render(
    <ThemeProvider>
      <ThemeToggle />
      <Probe />
    </ThemeProvider>
  );
}

describe("ThemeProvider", () => {
  beforeEach(() => {
    document.documentElement.className = "";
    window.localStorage.clear();
  });
  afterEach(() => {
    window.localStorage.clear();
  });

  it("defaults to the system preference", () => {
    renderTheme();
    // The jsdom matchMedia stub reports a light system theme.
    expect(screen.getByTestId("probe")).toHaveTextContent("system/light/free");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });

  it("persists a choice made with the toggle and applies it", async () => {
    const user = userEvent.setup();
    renderTheme();
    await user.click(screen.getByRole("button", { name: "Change theme" }));
    await user.click(
      await screen.findByRole("menuitemradio", { name: /Dark/ })
    );

    expect(window.localStorage.getItem("shopwave-theme")).toBe("dark");
    expect(screen.getByTestId("probe")).toHaveTextContent("dark/dark/free");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe("dark");
  });

  it("clears the stored value when switching back to system", async () => {
    window.localStorage.setItem("shopwave-theme", "dark");
    const user = userEvent.setup();
    renderTheme();
    await user.click(screen.getByRole("button", { name: "Change theme" }));
    await user.click(
      await screen.findByRole("menuitemradio", { name: /System/ })
    );
    expect(window.localStorage.getItem("shopwave-theme")).toBeNull();
    expect(screen.getByTestId("probe")).toHaveTextContent("system/light/free");
  });

  it("forces dark on legacy routes and hides the toggle there", () => {
    window.localStorage.setItem("shopwave-theme", "light");
    setMockUrl("/admin/orders");
    renderTheme();
    expect(screen.getByTestId("probe")).toHaveTextContent("light/dark/forced");
    expect(
      screen.queryByRole("button", { name: "Change theme" })
    ).not.toBeInTheDocument();
    expect(document.documentElement.classList.contains("dark")).toBe(true);
  });
});
