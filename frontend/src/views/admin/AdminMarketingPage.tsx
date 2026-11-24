"use client";

import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Plus,
  Pause,
  Archive,
  BarChart2,
  Zap,
  X,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import {
  listWorkflows,
  createWorkflow,
  updateWorkflow,
} from "../../api/marketing";
import {
  createWorkflowSchema,
  type CreateWorkflowFormValues,
} from "../../schemas/marketing";
import type {
  MarketingWorkflow,
  SpringPage,
  WorkflowStatus,
  WorkflowTrigger,
} from "../../types/marketing";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const TRIGGER_LABELS: Record<WorkflowTrigger, string> = {
  ORDER_DELIVERED: "Order Delivered",
  DAYS_SINCE_LAST_ORDER: "Win-back (Inactivity)",
  CUSTOMER_BIRTHDAY: "Customer Birthday",
  FIRST_ORDER_PLACED: "First Order",
};

const STATUS_COLORS: Record<WorkflowStatus, string> = {
  ACTIVE: "bg-success-subtle text-success border-border",
  PAUSED: "bg-warning-subtle text-warning border-border",
  ARCHIVED: "bg-muted text-muted-foreground border-border",
};

const inputCls =
  "w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring";

const labelCls = "block text-xs font-semibold text-muted-foreground mb-1";

export default function AdminMarketingPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger, hoverLift } = useAnims();
  const [showCreate, setShowCreate] = useState(false);
  const [page, setPage] = useState(0);

  const { data: workflowPage, isLoading } = useQuery<
    SpringPage<MarketingWorkflow>
  >({
    queryKey: ["marketing", "workflows", companyId, page],
    queryFn: () => listWorkflows(companyId!, page),
    enabled: !!companyId,
  });
  const workflows = workflowPage?.content ?? [];

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateWorkflowFormValues>({
    resolver: zodResolver(createWorkflowSchema),
    defaultValues: {
      trigger: "ORDER_DELIVERED",
      actionType: "EMAIL",
      delayHours: 0,
      cooldownDays: 0,
    },
  });

  const createMutation = useMutation({
    mutationFn: (values: CreateWorkflowFormValues) =>
      createWorkflow(companyId!, {
        ...values,
        targetSegmentId: values.targetSegmentId || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["marketing", "workflows", companyId],
      });
      setPage(0);
      setShowCreate(false);
      reset();
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: WorkflowStatus }) =>
      updateWorkflow(companyId!, id, { status }),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ["marketing", "workflows", companyId],
      }),
  });

  const onSubmit = (values: CreateWorkflowFormValues) =>
    createMutation.mutate(values);

  return (
    <div className="px-6 py-10">
      <div className="max-w-5xl mx-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <p className="text-xs font-semibold text-link mb-1">Admin</p>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              Marketing Workflows
            </h1>
            <p className="text-muted-foreground text-sm mt-1">
              Trigger-based email and push campaigns.
            </p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-4 py-2 rounded-full transition"
          >
            <Plus className="w-4 h-4" />
            New Workflow
          </button>
        </div>

        {/* Workflow list */}
        {isLoading ? (
          <div className="text-muted-foreground text-sm py-12 text-center">
            Loading workflows…
          </div>
        ) : workflows.length === 0 ? (
          <div className="rounded-xl border border-border bg-surface p-12 text-center">
            <Zap className="w-10 h-10 text-link mx-auto mb-3 opacity-50" />
            <p className="text-muted-foreground text-sm">
              No workflows yet. Create your first one above.
            </p>
          </div>
        ) : (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="space-y-4"
          >
            {workflows.map((wf: MarketingWorkflow) => (
              <motion.div
                key={wf.id}
                variants={fadeInUp}
                whileHover={hoverLift}
                className="rounded-xl border border-border bg-surface shadow-sm hover:shadow-md p-5 flex items-start justify-between gap-4"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <h3 className="text-lg font-semibold text-foreground truncate">
                      {wf.name}
                    </h3>
                    <span
                      className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${STATUS_COLORS[wf.status]}`}
                    >
                      {wf.status}
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Trigger:{" "}
                    <span className="text-foreground">
                      {TRIGGER_LABELS[wf.trigger]}
                    </span>
                    {wf.delayHours > 0 && (
                      <span className="ml-2 text-muted-foreground">
                        · {wf.delayHours}h delay
                      </span>
                    )}
                    {wf.cooldownDays > 0 && (
                      <span className="ml-2 text-muted-foreground">
                        · {wf.cooldownDays}d cooldown
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    Action: {wf.actionType}
                    {wf.emailSubject && ` — "${wf.emailSubject}"`}
                  </p>
                </div>

                <div className="flex items-center gap-2 flex-shrink-0">
                  {wf.status === "ACTIVE" && (
                    <button
                      onClick={() =>
                        updateMutation.mutate({ id: wf.id, status: "PAUSED" })
                      }
                      title="Pause"
                      className="p-2 rounded-xl border border-border hover:bg-muted text-muted-foreground hover:text-warning transition"
                    >
                      <Pause className="w-4 h-4" />
                    </button>
                  )}
                  {wf.status === "PAUSED" && (
                    <button
                      onClick={() =>
                        updateMutation.mutate({ id: wf.id, status: "ACTIVE" })
                      }
                      title="Resume"
                      className="p-2 rounded-xl border border-border hover:bg-muted text-muted-foreground hover:text-success transition"
                    >
                      <Zap className="w-4 h-4" />
                    </button>
                  )}
                  {wf.status !== "ARCHIVED" && (
                    <button
                      onClick={() =>
                        updateMutation.mutate({ id: wf.id, status: "ARCHIVED" })
                      }
                      title="Archive"
                      className="p-2 rounded-xl border border-border hover:bg-muted text-muted-foreground hover:text-danger transition"
                    >
                      <Archive className="w-4 h-4" />
                    </button>
                  )}
                  <AnalyticsChip companyId={companyId!} workflowId={wf.id} />
                </div>
              </motion.div>
            ))}
          </motion.div>
        )}

        {/* Pagination */}
        {workflowPage && workflowPage.totalPages > 1 && (
          <div className="flex items-center justify-between mt-6">
            <p className="text-xs text-muted-foreground">
              Showing {page * workflowPage.size + 1}–
              {Math.min(
                (page + 1) * workflowPage.size,
                workflowPage.totalElements
              )}{" "}
              of {workflowPage.totalElements} workflows
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage((p) => p - 1)}
                disabled={workflowPage.first}
                className="p-2 rounded-xl border border-border hover:bg-muted text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition"
                aria-label="Previous page"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="text-xs text-muted-foreground">
                {page + 1} / {workflowPage.totalPages}
              </span>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={workflowPage.last}
                className="p-2 rounded-xl border border-border hover:bg-muted text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition"
                aria-label="Next page"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Create modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-overlay p-4">
          <div className="w-full max-w-lg rounded-xl border border-border bg-surface-raised p-8 shadow-lg">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-foreground">
                New Workflow
              </h2>
              <button
                onClick={() => {
                  setShowCreate(false);
                  reset();
                }}
                className="text-muted-foreground hover:text-foreground transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div>
                <label className={labelCls}>Name</label>
                <input
                  {...register("name")}
                  placeholder="e.g. Post-delivery review ask"
                  className={inputCls}
                />
                {errors.name && (
                  <p className="text-danger text-xs mt-1">
                    {errors.name.message}
                  </p>
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={labelCls}>Trigger</label>
                  <select {...register("trigger")} className={inputCls}>
                    {(Object.keys(TRIGGER_LABELS) as WorkflowTrigger[]).map(
                      (t) => (
                        <option key={t} value={t}>
                          {TRIGGER_LABELS[t]}
                        </option>
                      )
                    )}
                  </select>
                </div>
                <div>
                  <label className={labelCls}>Action</label>
                  <select {...register("actionType")} className={inputCls}>
                    <option value="EMAIL">Email</option>
                    <option value="PUSH">Push</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={labelCls}>Delay (hours)</label>
                  <input
                    {...register("delayHours", { valueAsNumber: true })}
                    type="number"
                    min={0}
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className={labelCls}>Cooldown (days)</label>
                  <input
                    {...register("cooldownDays", { valueAsNumber: true })}
                    type="number"
                    min={0}
                    className={inputCls}
                  />
                </div>
              </div>

              <div>
                <label className={labelCls}>Email Subject</label>
                <input
                  {...register("emailSubject")}
                  placeholder="Subject line"
                  className={inputCls}
                />
                {errors.emailSubject && (
                  <p className="text-danger text-xs mt-1">
                    {errors.emailSubject.message}
                  </p>
                )}
              </div>

              <div>
                <label className={labelCls}>Email Body (HTML)</label>
                <textarea
                  {...register("emailBody")}
                  rows={4}
                  placeholder="<p>Hi {{firstName}},…</p>"
                  className={inputCls}
                />
              </div>

              {createMutation.isError && (
                <p className="text-danger text-xs">
                  Failed to create workflow. Please try again.
                </p>
              )}

              <button
                type="submit"
                disabled={isSubmitting || createMutation.isPending}
                className="w-full bg-primary hover:bg-primary-hover disabled:opacity-50 text-primary-foreground font-semibold py-2.5 rounded-full transition"
              >
                {createMutation.isPending ? "Creating…" : "Create Workflow"}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function AnalyticsChip({
  companyId,
  workflowId,
}: {
  companyId: string;
  workflowId: string;
}) {
  const [hovered, setHovered] = useState(false);
  const { data } = useQuery({
    queryKey: ["marketing", "analytics", workflowId],
    queryFn: () =>
      import("../../api/marketing").then((m) =>
        m.getWorkflowAnalytics(companyId, workflowId)
      ),
    enabled: hovered,
  });

  return (
    <div
      data-testid="analytics-chip"
      onMouseEnter={() => setHovered(true)}
      className="flex items-center gap-1 text-xs text-muted-foreground px-2 py-1.5 rounded-xl border border-border bg-surface"
    >
      <BarChart2 className="w-3.5 h-3.5 text-link" />
      <span>{data?.sentCount ?? "—"} sent</span>
    </div>
  );
}
