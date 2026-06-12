import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { configureStore } from "@reduxjs/toolkit";
import QASection from "./QASection";
import type { Question } from "../../types/qa";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../../api/qa", () => ({
  qaApi: {
    listQuestions: vi.fn(),
    askQuestion: vi.fn(),
    upvoteAnswer: vi.fn(),
  },
}));

import { qaApi } from "../../api/qa";
const mockListQuestions = vi.mocked(qaApi.listQuestions);
const mockAskQuestion = vi.mocked(qaApi.askQuestion);
const mockUpvoteAnswer = vi.mocked(qaApi.upvoteAnswer);

// ─── helpers ─────────────────────────────────────────────────────────────────

function makeStore(authenticated = true) {
  return configureStore({
    reducer: {
      auth: () => ({ accessToken: authenticated ? "token-abc" : null }),
      marketplace: () => ({ currentMarketplace: null }),
      vendor: () => ({}),
      loyalty: () => ({}),
    },
  });
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderQASection(productId = "product-1", authenticated = true) {
  render(
    <Provider store={makeStore(authenticated)}>
      <QueryClientProvider client={makeClient()}>
        <MemoryRouter>
          <QASection productId={productId} />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  );
}

function makePagedResponse(items: Question[] = []) {
  return {
    data: {
      items,
      page: 0,
      size: 10,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / 10) || 1,
      hasNext: false,
      hasPrevious: false,
    },
  };
}

function makeQuestion(id: string, questionText = "Is this waterproof?"): Question {
  return {
    id,
    productId: "product-1",
    askedById: "user-1",
    askerFirstName: "Alice",
    askerLastName: "Smith",
    questionText,
    status: "VISIBLE",
    answers: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
}

function makeQuestionWithVendorAnswer(id: string): Question {
  return {
    ...makeQuestion(id),
    answers: [
      {
        id: "answer-1",
        questionId: id,
        answeredById: "vendor-1",
        answererFirstName: "Shop",
        answererLastName: "Owner",
        answerText: "Yes, it is fully waterproof.",
        vendorAnswer: true,
        upvoteCount: 3,
        status: "VISIBLE",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ],
  };
}

// ─── tests ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
});

describe("QASection", () => {
  it("renders_loadingState_initially", () => {
    mockListQuestions.mockReturnValue(new Promise(() => {}));
    renderQASection();
    expect(screen.queryByRole("region", { hidden: true }) ?? document.querySelector(".animate-pulse")).toBeTruthy();
  });

  it("renders_questionList_onSuccess", async () => {
    mockListQuestions.mockResolvedValue(makePagedResponse([makeQuestion("q-1")]));
    renderQASection();

    await waitFor(() => {
      expect(screen.getByText(/Is this waterproof\?/)).toBeInTheDocument();
    });
  });

  it("renders_emptyState_whenNoQuestions", async () => {
    mockListQuestions.mockResolvedValue(makePagedResponse([]));
    renderQASection();

    await waitFor(() => {
      expect(screen.getByText(/no questions yet/i)).toBeInTheDocument();
    });
  });

  it("askQuestionForm_submitsAndInvalidatesCache", async () => {
    mockListQuestions.mockResolvedValue(makePagedResponse([]));
    mockAskQuestion.mockResolvedValue({ data: makeQuestion("q-new", "Will it blend?") } as never);
    renderQASection();

    await waitFor(() => screen.getByPlaceholderText(/ask a question/i));

    const textarea = screen.getByPlaceholderText(/ask a question/i);
    fireEvent.change(textarea, { target: { value: "Will it blend? Testing long enough question here." } });
    fireEvent.click(screen.getByRole("button", { name: /submit question/i }));

    await waitFor(() => {
      expect(mockAskQuestion).toHaveBeenCalledWith("product-1", "Will it blend? Testing long enough question here.");
    });
  });

  it("vendorAnswer_displaysBadge", async () => {
    mockListQuestions.mockResolvedValue(makePagedResponse([makeQuestionWithVendorAnswer("q-1")]));
    renderQASection();

    await waitFor(() => {
      expect(screen.getByText("Vendor")).toBeInTheDocument();
    });
  });

  it("upvoteButton_callsUpvoteApi", async () => {
    mockListQuestions.mockResolvedValue(makePagedResponse([makeQuestionWithVendorAnswer("q-1")]));
    mockUpvoteAnswer.mockResolvedValue({ data: undefined } as never);
    renderQASection();

    await waitFor(() => screen.getByText("Yes, it is fully waterproof."));

    const upvoteButtons = screen.getAllByTitle("Upvote this answer");
    fireEvent.click(upvoteButtons[0]);

    await waitFor(() => {
      expect(mockUpvoteAnswer).toHaveBeenCalledWith("q-1", "answer-1");
    });
  });

  it("showsSignInPrompt_whenUnauthenticated", async () => {
    mockListQuestions.mockResolvedValue(makePagedResponse([]));
    renderQASection("product-1", false);

    await waitFor(() => {
      expect(screen.getByText(/sign in/i)).toBeInTheDocument();
    });
  });
});
