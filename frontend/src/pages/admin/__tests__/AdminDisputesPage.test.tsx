import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import AdminDisputesPage from "../AdminDisputesPage";
import type {
  DisputeCase,
  DisputeCaseDetail,
  DisputeEvidence,
  PagedResponse,
} from "../../../types/disputes";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../../../api/disputes", () => ({
  listOpenDisputes: vi.fn(),
  getDispute: vi.fn(),
  addEvidence: vi.fn(),
  listOrderDisputes: vi.fn(),
}));

import { listOpenDisputes, getDispute, addEvidence } from "../../../api/disputes";

const mockList = vi.mocked(listOpenDisputes);
const mockGet = vi.mocked(getDispute);
const mockAdd = vi.mocked(addEvidence);

// ─── helpers ─────────────────────────────────────────────────────────────────

const CASE_ID = "11111111-1111-4111-8111-111111111111";
const ORDER_ID = "22222222-2222-4222-8222-222222222222";

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderPage() {
  render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter>
        <AdminDisputesPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

/** Deadline well in the future so the countdown copy is stable regardless of run date. */
function futureDeadline(days: number): string {
  return new Date(Date.now() + days * 86_400_000).toISOString();
}

function stubDispute(overrides: Partial<DisputeCase> = {}): DisputeCase {
  return {
    id: CASE_ID,
    orderId: ORDER_ID,
    stripeDisputeId: "dp_1",
    stripeChargeId: "ch_1",
    amountCents: 2500,
    currency: "usd",
    reason: "fraudulent",
    status: "OPEN",
    outcome: "PENDING",
    stripeStatus: "needs_response",
    evidenceDeadline: futureDeadline(10),
    closedAt: null,
    evidenceCount: 2,
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

function stubEvidence(overrides: Partial<DisputeEvidence> = {}): DisputeEvidence {
  return {
    id: "ev-1",
    evidenceType: "ORDER_DETAILS",
    content: "ORDER 2222\nItems: Disputed Widget x1",
    attachmentUrl: null,
    createdById: null,
    createdAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

function stubPage(disputes: DisputeCase[]): PagedResponse<DisputeCase> {
  return {
    items: disputes,
    page: 0,
    size: 20,
    totalElements: disputes.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

function stubDetail(overrides: Partial<DisputeCaseDetail> = {}): DisputeCaseDetail {
  return { dispute: stubDispute(), evidence: [stubEvidence()], ...overrides };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockList.mockResolvedValue(stubPage([stubDispute()]));
  mockGet.mockResolvedValue(stubDetail());
});

// ─── tests ───────────────────────────────────────────────────────────────────

describe("AdminDisputesPage", () => {
  it("renders open disputes with amount, reason and deadline", async () => {
    renderPage();

    expect(await screen.findByText(/25\.00 USD/)).toBeInTheDocument();
    expect(screen.getByText(/fraudulent/)).toBeInTheDocument();
    expect(screen.getByText("Needs response")).toBeInTheDocument();
    expect(screen.getByText(/10 days left/)).toBeInTheDocument();
    expect(screen.getByText(/2 evidence entries/)).toBeInTheDocument();
  });

  it("shows an empty state when there are no open disputes", async () => {
    mockList.mockResolvedValue(stubPage([]));
    renderPage();

    expect(await screen.findByText("No open disputes.")).toBeInTheDocument();
  });

  it("shows an error state when the list request fails", async () => {
    mockList.mockRejectedValue(new Error("boom"));
    renderPage();

    expect(await screen.findByText(/Could not load disputes/)).toBeInTheDocument();
  });

  it("flags a dispute that could not be matched to an order", async () => {
    mockList.mockResolvedValue(stubPage([stubDispute({ orderId: null })]));
    renderPage();

    expect(await screen.findByText(/reconcile this charge manually/i)).toBeInTheDocument();
  });

  it("marks an overdue deadline differently from an upcoming one", async () => {
    mockList.mockResolvedValue(
      stubPage([stubDispute({ evidenceDeadline: futureDeadline(-2) })])
    );
    renderPage();

    expect(await screen.findByText(/overdue/)).toBeInTheDocument();
  });

  /**
   * The first 24 hours past the deadline: Math.ceil yields -0 there, and -0 is neither < 0 nor
   * distinguishable from 0, so this window used to render as a neutral "due today".
   */
  it.each([
    ["an hour ago", -1 / 24],
    ["twelve hours ago", -0.5],
    ["a minute ago", -1 / 1440],
  ])("shows a deadline that passed %s as overdue, not due today", async (_label, offsetDays) => {
    mockList.mockResolvedValue(
      stubPage([stubDispute({ evidenceDeadline: futureDeadline(offsetDays) })])
    );
    renderPage();

    expect(await screen.findByText(/overdue/)).toBeInTheDocument();
    expect(screen.queryByText(/due today/)).not.toBeInTheDocument();
  });

  it("shows a deadline later today as due today, not overdue", async () => {
    mockList.mockResolvedValue(
      stubPage([stubDispute({ evidenceDeadline: futureDeadline(0.25) })])
    );
    renderPage();

    expect(await screen.findByText(/due today/)).toBeInTheDocument();
    expect(screen.queryByText(/overdue/)).not.toBeInTheDocument();
  });

  it("opens the detail view with pre-populated evidence when a dispute is selected", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));

    expect(await screen.findByText("Dispute detail")).toBeInTheDocument();
    expect(screen.getByText("Evidence (1)")).toBeInTheDocument();
    expect(screen.getByText(/Items: Disputed Widget x1/)).toBeInTheDocument();
    expect(screen.getByText("Auto-generated")).toBeInTheDocument();
    expect(mockGet).toHaveBeenCalledWith(CASE_ID);
  });

  it("submits a manual evidence entry and refreshes the case", async () => {
    const user = userEvent.setup();
    mockAdd.mockResolvedValue(stubEvidence({ id: "ev-2", createdById: "staff-1" }));
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));
    await user.click(await screen.findByRole("button", { name: /add evidence/i }));
    await user.type(
      screen.getByLabelText(/content/i),
      "Customer confirmed receipt by phone."
    );
    await user.click(screen.getByRole("button", { name: /save evidence/i }));

    await waitFor(() =>
      expect(mockAdd).toHaveBeenCalledWith(CASE_ID, {
        evidenceType: "OTHER",
        content: "Customer confirmed receipt by phone.",
        attachmentUrl: undefined,
      })
    );
    // Refetch proves the detail query was invalidated on success.
    await waitFor(() => expect(mockGet).toHaveBeenCalledTimes(2));
  });

  it("blocks submission and shows a validation error when content is empty", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));
    await user.click(await screen.findByRole("button", { name: /add evidence/i }));
    await user.click(screen.getByRole("button", { name: /save evidence/i }));

    expect(await screen.findByText("Evidence content is required")).toBeInTheDocument();
    expect(mockAdd).not.toHaveBeenCalled();
  });

  it("rejects a non-HTTPS attachment URL before submitting", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));
    await user.click(await screen.findByRole("button", { name: /add evidence/i }));
    await user.type(screen.getByLabelText(/content/i), "Signed proof of delivery.");
    await user.type(screen.getByLabelText(/attachment url/i), "http://files.example.com/pod.pdf");
    await user.click(screen.getByRole("button", { name: /save evidence/i }));

    expect(await screen.findByText("Attachment URL must use HTTPS")).toBeInTheDocument();
    expect(mockAdd).not.toHaveBeenCalled();
  });

  it("accepts an HTTPS attachment URL", async () => {
    const user = userEvent.setup();
    mockAdd.mockResolvedValue(stubEvidence({ id: "ev-3", createdById: "staff-1" }));
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));
    await user.click(await screen.findByRole("button", { name: /add evidence/i }));
    await user.type(screen.getByLabelText(/content/i), "Signed proof of delivery.");
    await user.type(screen.getByLabelText(/attachment url/i), "https://files.example.com/pod.pdf");
    await user.click(screen.getByRole("button", { name: /save evidence/i }));

    await waitFor(() =>
      expect(mockAdd).toHaveBeenCalledWith(CASE_ID, {
        evidenceType: "OTHER",
        content: "Signed proof of delivery.",
        attachmentUrl: "https://files.example.com/pod.pdf",
      })
    );
  });

  it("surfaces the backend message when adding evidence fails", async () => {
    const user = userEvent.setup();
    mockAdd.mockRejectedValue({
      response: { data: { error: { details: { detail: "Dispute is already closed" } } } },
    });
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));
    await user.click(await screen.findByRole("button", { name: /add evidence/i }));
    await user.type(screen.getByLabelText(/content/i), "late entry");
    await user.click(screen.getByRole("button", { name: /save evidence/i }));

    expect(await screen.findByText("Dispute is already closed")).toBeInTheDocument();
  });

  it("closes the detail view", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));
    expect(await screen.findByText("Dispute detail")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /close dispute detail/i }));

    await waitFor(() =>
      expect(screen.queryByText("Dispute detail")).not.toBeInTheDocument()
    );
  });

  it("pages forward through the dispute list", async () => {
    const user = userEvent.setup();
    mockList.mockResolvedValue({
      ...stubPage([stubDispute()]),
      totalElements: 3,
      totalPages: 2,
      hasNext: true,
    });
    renderPage();

    await user.click(await screen.findByRole("button", { name: /next page/i }));

    await waitFor(() => expect(mockList).toHaveBeenCalledWith(1));
  });

  it("shows an empty-evidence message when a case has no entries yet", async () => {
    const user = userEvent.setup();
    mockGet.mockResolvedValue(stubDetail({ evidence: [] }));
    renderPage();

    await user.click(await screen.findByText(/25\.00 USD/));

    expect(await screen.findByText("No evidence on this case yet.")).toBeInTheDocument();
  });
});
