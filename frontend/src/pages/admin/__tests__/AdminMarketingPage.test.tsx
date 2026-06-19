import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { configureStore } from "@reduxjs/toolkit";
import AdminMarketingPage from "../AdminMarketingPage";
import type { MarketingWorkflow, WorkflowAnalytics } from "../../../types/marketing";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../../../api/marketing", () => ({
  listWorkflows: vi.fn(),
  createWorkflow: vi.fn(),
  updateWorkflow: vi.fn(),
  getWorkflowAnalytics: vi.fn(),
}));

import {
  listWorkflows,
  createWorkflow,
  updateWorkflow,
  getWorkflowAnalytics,
} from "../../../api/marketing";

const mockList = vi.mocked(listWorkflows);
const mockCreate = vi.mocked(createWorkflow);
const mockUpdate = vi.mocked(updateWorkflow);
const mockAnalytics = vi.mocked(getWorkflowAnalytics);

// ─── helpers ─────────────────────────────────────────────────────────────────

const COMPANY_ID = "company-123";

function makeStore() {
  return configureStore({
    reducer: {
      auth: () => ({ companyId: COMPANY_ID, accessToken: "token" }),
      marketplace: () => ({ currentMarketplace: null }),
      vendor: () => ({}),
      loyalty: () => ({}),
    },
  });
}

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderPage() {
  const store = makeStore();
  const client = makeClient();
  render(
    <Provider store={store}>
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <AdminMarketingPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
  return { store, client };
}

function stubWorkflow(overrides: Partial<MarketingWorkflow> = {}): MarketingWorkflow {
  return {
    id: "wf-1",
    companyId: COMPANY_ID,
    name: "Post-delivery review",
    trigger: "ORDER_DELIVERED",
    delayHours: 48,
    targetSegmentId: null,
    actionType: "EMAIL",
    emailSubject: "How was your order?",
    cooldownDays: 30,
    status: "ACTIVE",
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
    ...overrides,
  };
}

const stubAnalytics: WorkflowAnalytics = {
  workflowId: "wf-1",
  enrolledCount: 10,
  sentCount: 7,
};

// ─── tests ────────────────────────────────────────────────────────────────────

describe("AdminMarketingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAnalytics.mockResolvedValue(stubAnalytics);
  });

  it("renders workflow list from API", async () => {
    mockList.mockResolvedValue([stubWorkflow()]);

    renderPage();

    await waitFor(() =>
      expect(screen.getByText("Post-delivery review")).toBeInTheDocument()
    );
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
    expect(mockList).toHaveBeenCalledWith(COMPANY_ID);
  });

  it("shows empty state when no workflows exist", async () => {
    mockList.mockResolvedValue([]);

    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/No workflows yet/i)).toBeInTheDocument()
    );
  });

  it("opens create modal when New Workflow button is clicked", async () => {
    mockList.mockResolvedValue([]);
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => screen.getByText(/No workflows yet/i));

    await user.click(screen.getByRole("button", { name: /New Workflow/i }));

    expect(screen.getByText("New Workflow", { selector: "h2" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Post-delivery review ask/i)).toBeInTheDocument();
  });

  it("submits create form and closes modal on success", async () => {
    mockList.mockResolvedValue([]);
    mockCreate.mockResolvedValue(stubWorkflow());
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => screen.getByText(/No workflows yet/i));

    await user.click(screen.getByRole("button", { name: /New Workflow/i }));

    await user.type(screen.getByPlaceholderText(/Post-delivery review ask/i), "Win-back 90d");
    await user.type(screen.getByPlaceholderText(/Subject line/i), "We miss you");

    await user.click(screen.getByRole("button", { name: /Create Workflow/i }));

    await waitFor(() =>
      expect(mockCreate).toHaveBeenCalledWith(
        COMPANY_ID,
        expect.objectContaining({ name: "Win-back 90d" })
      )
    );
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /Create Workflow/i })).not.toBeInTheDocument()
    );
  });

  it("pause button calls PATCH with PAUSED status", async () => {
    mockList.mockResolvedValue([stubWorkflow({ status: "ACTIVE" })]);
    mockUpdate.mockResolvedValue(stubWorkflow({ status: "PAUSED" }));
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => screen.getByText("Post-delivery review"));

    await user.click(screen.getByTitle("Pause"));

    await waitFor(() =>
      expect(mockUpdate).toHaveBeenCalledWith(COMPANY_ID, "wf-1", { status: "PAUSED" })
    );
  });

  it("shows sent count from analytics on hover", async () => {
    mockList.mockResolvedValue([stubWorkflow()]);
    const user = userEvent.setup();

    renderPage();

    await waitFor(() => screen.getByText("Post-delivery review"));

    // Analytics chip loads lazily — trigger on hover
    await user.hover(screen.getByTestId("analytics-chip"));

    await waitFor(() =>
      expect(screen.getByText("7 sent")).toBeInTheDocument()
    );
  });
});
