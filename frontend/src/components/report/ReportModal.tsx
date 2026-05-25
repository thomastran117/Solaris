import { useEffect } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { CheckCircle } from "lucide-react";
import Modal from "../ui/Modal";
import ReportScreenshotUploader from "./ReportScreenshotUploader";
import { reportsApi } from "../../api/reports";
import {
  reportFormSchema,
  reportFormDefaults,
  REPORT_REASON_LABELS,
  REASONS_FOR_TARGET,
  type ReportFormValues,
} from "../../schemas/report";
import type { ReportTargetType } from "../../types/report";

interface Props {
  open: boolean;
  onClose: () => void;
  targetType: ReportTargetType;
  targetId: string;
  targetName: string;
}

export default function ReportModal({
  open,
  onClose,
  targetType,
  targetId,
  targetName,
}: Props) {
  const reasons = REASONS_FOR_TARGET[targetType];

  const {
    register,
    handleSubmit,
    control,
    watch,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<ReportFormValues>({
    resolver: zodResolver(reportFormSchema),
    defaultValues: { ...reportFormDefaults, reason: reasons[0] },
    mode: "onBlur",
  });

  // Reset form and set first valid reason when target type changes
  useEffect(() => {
    reset({ ...reportFormDefaults, reason: reasons[0] });
  }, [targetType, targetId]);

  const description = watch("description");
  const title = watch("title");
  const selectedReason = watch("reason");

  const mutation = useMutation({
    mutationFn: (values: ReportFormValues) =>
      reportsApi.create({
        targetType,
        targetId,
        reason: values.reason,
        title: values.title,
        description: values.description,
        screenshotUrls: values.screenshotUrls,
      }),
  });

  function handleClose() {
    reset({ ...reportFormDefaults, reason: reasons[0] });
    mutation.reset();
    onClose();
  }

  const onSubmit = (values: ReportFormValues) => mutation.mutate(values);

  const targetLabel: Record<ReportTargetType, string> = {
    PRODUCT: "this product",
    COMPANY: "this company",
    REVIEW: "this review",
    USER: "this user",
  };

  return (
    <Modal open={open} onClose={handleClose} title={`Report ${targetLabel[targetType]}`} maxWidth="max-w-lg">
      {mutation.isSuccess ? (
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <CheckCircle className="h-10 w-10 text-green-400" />
          <p className="text-sm font-medium text-white">Report submitted</p>
          <p className="text-xs text-white/60">
            Our moderation team will review your report for{" "}
            <span className="text-white/80">{targetName}</span>.
          </p>
          <button
            type="button"
            onClick={handleClose}
            className="mt-2 rounded-full border border-white/20 px-5 py-2 text-sm font-semibold text-white/80 hover:bg-white/10 hover:text-white"
          >
            Close
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          {/* Reason */}
          <fieldset className="space-y-2">
            <legend className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
              Reason
            </legend>
            <div className="space-y-1.5">
              {reasons.map((r) => {
                const meta = REPORT_REASON_LABELS[r];
                return (
                  <label
                    key={r}
                    className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition ${
                      selectedReason === r
                        ? "border-blue-400/40 bg-blue-500/10"
                        : "border-white/10 bg-white/[0.04] hover:border-white/20"
                    }`}
                  >
                    <input
                      type="radio"
                      value={r}
                      {...register("reason")}
                      className="mt-0.5 accent-blue-500"
                    />
                    <div>
                      <div className="text-sm font-medium text-white">{meta.label}</div>
                      <div className="text-xs text-white/55">{meta.hint}</div>
                    </div>
                  </label>
                );
              })}
            </div>
            {errors.reason && (
              <p className="text-xs text-red-400">{errors.reason.message}</p>
            )}
          </fieldset>

          {/* Title */}
          <label className="block">
            <span className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
              Title
            </span>
            <input
              type="text"
              {...register("title")}
              maxLength={150}
              placeholder="Brief summary of the issue"
              className="mt-2 w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2.5 text-sm text-white placeholder:text-white/45 focus:border-blue-400/40 focus:outline-none"
            />
            <div className="mt-1 flex justify-between">
              {errors.title ? (
                <p className="text-xs text-red-400">{errors.title.message}</p>
              ) : (
                <span />
              )}
              <span className="text-[10px] text-white/45">{title.length}/150</span>
            </div>
          </label>

          {/* Description */}
          <label className="block">
            <span className="text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
              Description
            </span>
            <textarea
              {...register("description")}
              rows={4}
              maxLength={2000}
              placeholder="Describe the issue in detail so our moderation team can act on it"
              className="mt-2 w-full resize-none rounded-xl border border-white/10 bg-white/[0.04] p-3 text-sm text-white placeholder:text-white/45 focus:border-blue-400/40 focus:outline-none"
            />
            <div className="mt-1 flex justify-between">
              {errors.description ? (
                <p className="text-xs text-red-400">{errors.description.message}</p>
              ) : (
                <span />
              )}
              <span className="text-[10px] text-white/45">{description.length}/2000</span>
            </div>
          </label>

          {/* Screenshots */}
          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-[0.25em] text-sky-200/90">
              Screenshots (optional)
            </p>
            <Controller
              control={control}
              name="screenshotUrls"
              render={({ field }) => (
                <ReportScreenshotUploader
                  urls={field.value}
                  onChange={(urls) => setValue("screenshotUrls", urls)}
                />
              )}
            />
          </div>

          {mutation.isError && (
            <p className="text-xs text-red-400">
              Could not submit report. Please try again later.
            </p>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={handleClose}
              className="rounded-full border border-white/20 px-4 py-2 text-sm font-semibold text-white/80 hover:bg-white/10 hover:text-white"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || mutation.isPending}
              className="rounded-full bg-blue-600 px-5 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:opacity-60"
            >
              {mutation.isPending ? "Submitting…" : "Submit report"}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
