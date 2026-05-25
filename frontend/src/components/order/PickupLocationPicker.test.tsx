import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import PickupLocationPicker from "./PickupLocationPicker";
import type { NearbyPickupLocation } from "../../types/order";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../../api/locations", () => ({
  locationsApi: { nearbyPickup: vi.fn() },
}));

import { locationsApi } from "../../api/locations";
const mockNearbyPickup = vi.mocked(locationsApi.nearbyPickup);

// ─── helpers ─────────────────────────────────────────────────────────────────

function makeLocation(overrides: Partial<NearbyPickupLocation> = {}): NearbyPickupLocation {
  return {
    id: "loc-1", name: "Downtown Store", code: "DT1",
    address: "123 Main St", city: "Seattle", country: "US",
    distanceKm: 2.3, pickupReadyHours: 2, type: "STORE",
    ...overrides,
  };
}

function renderPicker(selectedId: string | null = null, onSelect = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <PickupLocationPicker companyId="company-1" selectedId={selectedId} onSelect={onSelect} />
    </QueryClientProvider>
  );
  return { onSelect };
}

function mockGeoSuccess(lat = 47.6062, lng = -122.3321) {
  vi.stubGlobal("navigator", {
    geolocation: {
      getCurrentPosition: vi.fn((onSuccess: PositionCallback) =>
        onSuccess({ coords: { latitude: lat, longitude: lng } } as GeolocationPosition)
      ),
    },
  });
}

function mockGeoDenied() {
  vi.stubGlobal("navigator", {
    geolocation: {
      getCurrentPosition: vi.fn((_: PositionCallback, onError: PositionErrorCallback) =>
        onError({ code: 1, message: "User denied geolocation" } as GeolocationPositionError)
      ),
    },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

// ─── geolocation ─────────────────────────────────────────────────────────────

describe("PickupLocationPicker", () => {
  it("calls navigator.geolocation.getCurrentPosition on mount", () => {
    const getCurrentPosition = vi.fn();
    vi.stubGlobal("navigator", { geolocation: { getCurrentPosition } });

    renderPicker();

    expect(getCurrentPosition).toHaveBeenCalledTimes(1);
  });

  it("shows loading spinner while detecting location", () => {
    vi.stubGlobal("navigator", {
      geolocation: { getCurrentPosition: vi.fn() }, // never resolves
    });

    renderPicker();

    expect(screen.getByText(/Detecting your location/i)).toBeInTheDocument();
  });

  it("shows permission-denied message when geolocation is rejected", async () => {
    mockGeoDenied();

    renderPicker();

    await waitFor(() =>
      expect(screen.getByText(/Location permission denied/i)).toBeInTheDocument()
    );
  });

  it("shows store cards after geolocation succeeds", async () => {
    mockGeoSuccess();
    mockNearbyPickup.mockResolvedValue({ data: [makeLocation()] } as never);

    renderPicker();

    await waitFor(() =>
      expect(screen.getByText("Downtown Store")).toBeInTheDocument()
    );
  });

  it("each card shows location name, distance, and pickup ready time", async () => {
    mockGeoSuccess();
    mockNearbyPickup.mockResolvedValue({
      data: [makeLocation({ distanceKm: 2.3, pickupReadyHours: 4 })],
    } as never);

    renderPicker();

    await waitFor(() => screen.getByText("Downtown Store"));
    expect(screen.getByText("2.3 km")).toBeInTheDocument();
    expect(screen.getByText(/Ready in ~4h/i)).toBeInTheDocument();
  });

  it("shows distance in metres when under 1 km", async () => {
    mockGeoSuccess();
    mockNearbyPickup.mockResolvedValue({
      data: [makeLocation({ distanceKm: 0.45 })],
    } as never);

    renderPicker();

    await waitFor(() => screen.getByText("Downtown Store"));
    expect(screen.getByText("450 m")).toBeInTheDocument();
  });

  it("calls onSelect with locationId when a card is clicked", async () => {
    mockGeoSuccess();
    mockNearbyPickup.mockResolvedValue({
      data: [makeLocation({ id: "loc-abc" })],
    } as never);

    const { onSelect } = renderPicker();

    await waitFor(() => screen.getByText("Downtown Store"));
    await userEvent.click(screen.getByText("Downtown Store"));

    expect(onSelect).toHaveBeenCalledWith("loc-abc");
  });

  it("shows loading spinner while fetching nearby locations", async () => {
    mockGeoSuccess();
    mockNearbyPickup.mockReturnValue(new Promise(() => {})); // never resolves

    renderPicker();

    await waitFor(() =>
      expect(screen.getByText(/Finding nearby stores/i)).toBeInTheDocument()
    );
  });

  it("shows no locations message when list is empty", async () => {
    mockGeoSuccess();
    mockNearbyPickup.mockResolvedValue({ data: [] } as never);

    renderPicker();

    await waitFor(() =>
      expect(screen.getByText(/No pickup locations found/i)).toBeInTheDocument()
    );
  });
});
