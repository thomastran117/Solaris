import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ThemeProvider, useTheme } from "./ThemeProvider";
import { ThemeToggle } from "./ThemeToggle";

function Probe() {
  const { preference, resolved } = useTheme();
  return (
    <output data-testid="probe">
      {preference}/{resolved}
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
    expect(screen.getByTestId("probe")).toHaveTextContent("system/light");
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
    expect(screen.getByTestId("probe")).toHaveTextContent("dark/dark");
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
    expect(screen.getByTestId("probe")).toHaveTextContent("system/light");
  });

  it("follows a choice made in another tab", () => {
    renderTheme();
    act(() => {
      window.localStorage.setItem("shopwave-theme", "dark");
      window.dispatchEvent(
        new StorageEvent("storage", { key: "shopwave-theme" })
      );
    });
    expect(screen.getByTestId("probe")).toHaveTextContent("dark/dark");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
  });
});
