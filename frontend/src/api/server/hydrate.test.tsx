import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query";
import { Hydrate } from "./hydrate";

function Reader({ queryKey }: { queryKey: readonly unknown[] }) {
  const queryFn = vi.fn(() => Promise.resolve("fetched-in-browser"));
  const { data } = useQuery({ queryKey, queryFn });
  return <p>{String(data ?? "pending")}</p>;
}

describe("Hydrate", () => {
  it("seeds prefetched data under the given key and skips null entries", () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <Hydrate
          entries={[
            [["product", "mp", "p1"], "Lamp"],
            [["product", "mp", "missing"], null],
          ]}
        >
          <Reader queryKey={["product", "mp", "p1"]} />
        </Hydrate>
      </QueryClientProvider>
    );
    expect(screen.getByText("Lamp")).toBeInTheDocument();
    expect(client.getQueryData(["product", "mp", "p1"])).toBe("Lamp");
    expect(
      client.getQueryState(["product", "mp", "missing"])?.data
    ).toBeUndefined();
  });
});
