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
    <Modal
      open={open}
      onClose={handleClose}
      title={`Report ${targetLabel[targetType]}`}
      maxWidth="max-w-lg"
    >
      {mutation.isSuccess ? (
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <CheckCircle className="h-10 w-10 text-success" />
          <p className="text-sm font-medium text-foreground">
            Report submitted
          </p>
          <p className="text-xs text-muted-foreground">
            Our moderation team will review your report for{" "}
            <span className="text-foreground">{targetName}</span>.
          </p>
          <button
            type="button"
            onClick={handleClose}
            className="mt-2 rounded-full border border-input px-5 py-2 text-sm font-semibold text-foreground hover:bg-muted hover:text-foreground"
          >
            Close
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          {/* Reason */}
          <fieldset className="space-y-2">
            <legend className="text-xs font-semibold text-link">Reason</legend>
            <div className="space-y-1.5">
              {reasons.map((r) => {
                const meta = REPORT_REASON_LABELS[r];
                return (
                  <label
                    key={r}
                    className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition ${
                      selectedReason === r
                        ? "border-primary/40 bg-primary/10"
                        : "border-border bg-surface hover:border-input"
                    }`}
                  >
                    <input
                      type="radio"
                      value={r}
                      {...register("reason")}
                      className="mt-0.5 accent-primary"
                    />
                    <div>
                      <div className="text-sm font-medium text-foreground">
                        {meta.label}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {meta.hint}
                      </div>
                    </div>
                  </label>
                );
              })}
            </div>
            {errors.reason && (
              <p className="text-xs text-danger">{errors.reason.message}</p>
            )}
          </fieldset>

          {/* Title */}
          <label className="block">
            <span className="text-xs font-semibold text-link">Title</span>
            <input
              type="text"
              {...register("title")}
              maxLength={150}
              placeholder="Brief summary of the issue"
              className="mt-2 w-full rounded-xl border border-border bg-surface px-3 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none"
            />
            <div className="mt-1 flex justify-between">
              {errors.title ? (
                <p className="text-xs text-danger">{errors.title.message}</p>
              ) : (
                <span />
              )}
              <span className="text-[10px] text-muted-foreground">
                {title.length}/150
              </span>
            </div>
          </label>

          {/* Description */}
          <label className="block">
            <span className="text-xs font-semibold text-link">Description</span>
            <textarea
              {...register("description")}
              rows={4}
              maxLength={2000}
              placeholder="Describe the issue in detail so our moderation team can act on it"
              className="mt-2 w-full resize-none rounded-xl border border-border bg-surface p-3 text-sm text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none"
            />
            <div className="mt-1 flex justify-between">
              {errors.description ? (
                <p className="text-xs text-danger">
                  {errors.description.message}
                </p>
              ) : (
                <span />
              )}
              <span className="text-[10px] text-muted-foreground">
                {description.length}/2000
              </span>
            </div>
          </label>

          {/* Screenshots */}
          <div>
            <p className="mb-2 text-xs font-semibold text-link">
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
            <p className="text-xs text-danger">
              Could not submit report. Please try again later.
            </p>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={handleClose}
              className="rounded-full border border-input px-4 py-2 text-sm font-semibold text-foreground hover:bg-muted hover:text-foreground"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || mutation.isPending}
              className="rounded-full bg-primary px-5 py-2 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:opacity-60"
            >
              {mutation.isPending ? "Submitting…" : "Submit report"}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
