import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Pause, Archive, BarChart2, Zap, X } from "lucide-react";
import { listWorkflows, createWorkflow, updateWorkflow } from "../../api/marketing";
import { createWorkflowSchema, type CreateWorkflowFormValues } from "../../schemas/marketing";
import type { MarketingWorkflow, WorkflowStatus, WorkflowTrigger } from "../../types/marketing";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const TRIGGER_LABELS: Record<WorkflowTrigger, string> = {
  ORDER_DELIVERED: "Order Delivered",
  DAYS_SINCE_LAST_ORDER: "Win-back (Inactivity)",
  CUSTOMER_BIRTHDAY: "Customer Birthday",
  FIRST_ORDER_PLACED: "First Order",
};

const STATUS_COLORS: Record<WorkflowStatus, string> = {
  ACTIVE: "bg-green-500/15 text-green-300 border-green-400/30",
  PAUSED: "bg-yellow-500/15 text-yellow-300 border-yellow-400/30",
  ARCHIVED: "bg-white/10 text-white/50 border-white/15",
};

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25";

const labelCls = "block text-xs font-semibold text-white/60 mb-1 uppercase tracking-widest";

export default function AdminMarketingPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();
  const [showCreate, setShowCreate] = useState(false);

  const { data: workflows = [], isLoading } = useQuery({
    queryKey: ["marketing", "workflows", companyId],
    queryFn: () => listWorkflows(companyId!),
    enabled: !!companyId,
  });

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
      queryClient.invalidateQueries({ queryKey: ["marketing", "workflows", companyId] });
      setShowCreate(false);
      reset();
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: WorkflowStatus }) =>
      updateWorkflow(companyId!, id, { status }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["marketing", "workflows", companyId] }),
  });

  const onSubmit = (values: CreateWorkflowFormValues) => createMutation.mutate(values);

  return (
    <div className="min-h-screen bg-slate-950 px-6 py-10">
      <div className="max-w-5xl mx-auto">

        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-1">
              Admin
            </p>
            <h1 className="text-3xl md:text-4xl font-extrabold text-white">
              Marketing Workflows
            </h1>
            <p className="text-white/60 text-sm mt-1">
              Trigger-based email and push campaigns.
            </p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-4 py-2 rounded-full transition"
          >
            <Plus className="w-4 h-4" />
            New Workflow
          </button>
        </div>

        {/* Workflow list */}
        {isLoading ? (
          <div className="text-white/50 text-sm py-12 text-center">Loading workflows…</div>
        ) : workflows.length === 0 ? (
          <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-12 text-center">
            <Zap className="w-10 h-10 text-sky-200 mx-auto mb-3 opacity-50" />
            <p className="text-white/60 text-sm">No workflows yet. Create your first one above.</p>
          </div>
        ) : (
          <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-4">
            {workflows.map((wf: MarketingWorkflow) => (
              <motion.div
                key={wf.id}
                variants={fadeInUp}
                whileHover={{ y: -4 }}
                className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md p-5 flex items-start justify-between gap-4"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <h3 className="text-lg font-semibold text-white truncate">{wf.name}</h3>
                    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${STATUS_COLORS[wf.status]}`}>
                      {wf.status}
                    </span>
                  </div>
                  <p className="text-sm text-white/60">
                    Trigger: <span className="text-white/80">{TRIGGER_LABELS[wf.trigger]}</span>
                    {wf.delayHours > 0 && (
                      <span className="ml-2 text-white/50">· {wf.delayHours}h delay</span>
                    )}
                    {wf.cooldownDays > 0 && (
                      <span className="ml-2 text-white/50">· {wf.cooldownDays}d cooldown</span>
                    )}
                  </p>
                  <p className="text-xs text-white/40 mt-1">
                    Action: {wf.actionType}
                    {wf.emailSubject && ` — "${wf.emailSubject}"`}
                  </p>
                </div>

                <div className="flex items-center gap-2 flex-shrink-0">
                  {wf.status === "ACTIVE" && (
                    <button
                      onClick={() => updateMutation.mutate({ id: wf.id, status: "PAUSED" })}
                      title="Pause"
                      className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/60 hover:text-yellow-300 transition"
                    >
                      <Pause className="w-4 h-4" />
                    </button>
                  )}
                  {wf.status === "PAUSED" && (
                    <button
                      onClick={() => updateMutation.mutate({ id: wf.id, status: "ACTIVE" })}
                      title="Resume"
                      className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/60 hover:text-green-300 transition"
                    >
                      <Zap className="w-4 h-4" />
                    </button>
                  )}
                  {wf.status !== "ARCHIVED" && (
                    <button
                      onClick={() => updateMutation.mutate({ id: wf.id, status: "ARCHIVED" })}
                      title="Archive"
                      className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/60 hover:text-red-300 transition"
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
      </div>

      {/* Create modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg rounded-3xl border border-white/10 bg-slate-900 p-8 shadow-2xl">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-extrabold text-white">New Workflow</h2>
              <button onClick={() => { setShowCreate(false); reset(); }} className="text-white/40 hover:text-white transition">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div>
                <label className={labelCls}>Name</label>
                <input {...register("name")} placeholder="e.g. Post-delivery review ask" className={inputCls} />
                {errors.name && <p className="text-red-400 text-xs mt-1">{errors.name.message}</p>}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={labelCls}>Trigger</label>
                  <select {...register("trigger")} className={inputCls}>
                    {(Object.keys(TRIGGER_LABELS) as WorkflowTrigger[]).map((t) => (
                      <option key={t} value={t}>{TRIGGER_LABELS[t]}</option>
                    ))}
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
                  <input {...register("delayHours", { valueAsNumber: true })} type="number" min={0} className={inputCls} />
                </div>
                <div>
                  <label className={labelCls}>Cooldown (days)</label>
                  <input {...register("cooldownDays", { valueAsNumber: true })} type="number" min={0} className={inputCls} />
                </div>
              </div>

              <div>
                <label className={labelCls}>Email Subject</label>
                <input {...register("emailSubject")} placeholder="Subject line" className={inputCls} />
              </div>

              <div>
                <label className={labelCls}>Email Body (HTML)</label>
                <textarea {...register("emailBody")} rows={4} placeholder="<p>Hi {{firstName}},…</p>" className={inputCls} />
              </div>

              {createMutation.isError && (
                <p className="text-red-400 text-xs">Failed to create workflow. Please try again.</p>
              )}

              <button
                type="submit"
                disabled={isSubmitting || createMutation.isPending}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-semibold py-2.5 rounded-full transition"
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

function AnalyticsChip({ companyId, workflowId }: { companyId: string; workflowId: string }) {
  const { data } = useQuery({
    queryKey: ["marketing", "analytics", workflowId],
    queryFn: () => import("../../api/marketing").then((m) => m.getWorkflowAnalytics(companyId, workflowId)),
  });

  return (
    <div className="flex items-center gap-1 text-xs text-white/50 px-2 py-1.5 rounded-xl border border-white/10 bg-white/[0.04]">
      <BarChart2 className="w-3.5 h-3.5 text-sky-200/60" />
      <span>{data?.sentCount ?? "—"} sent</span>
    </div>
  );
}
