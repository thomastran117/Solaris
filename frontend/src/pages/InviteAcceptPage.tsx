import { useEffect, useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery, useMutation } from "@tanstack/react-query";
import { CheckCircle, AlertCircle } from "lucide-react";
import { teamApi, type TeamInvitePreview } from "../api/team";
import type { RootState } from "../stores";

export default function InviteAcceptPage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const [accepted, setAccepted] = useState(false);

  const previewQuery = useQuery<TeamInvitePreview>({
    queryKey: ["invite", token],
    queryFn: () => teamApi.previewInvite(token!).then(r => r.data),
    enabled: !!token,
    retry: false,
  });

  const acceptMutation = useMutation({
    mutationFn: () => teamApi.acceptInvite(token!).then(r => r.data),
    onSuccess: () => setAccepted(true),
  });

  useEffect(() => {
    if (!accessToken && token) {
      const next = encodeURIComponent(location.pathname);
      navigate(`/login?next=${next}`, { replace: true });
    }
  }, [accessToken, token, navigate, location.pathname]);

  if (!token) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <p className="text-white/70">Invalid invite link.</p>
      </div>
    );
  }

  if (previewQuery.isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <div className="w-6 h-6 rounded-full border-2 border-sky-400/40 border-t-sky-400 animate-spin" />
      </div>
    );
  }

  if (previewQuery.error || !previewQuery.data) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <div className="max-w-md w-full rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-8 text-center">
          <AlertCircle className="w-8 h-8 text-red-400 mx-auto mb-3" />
          <h1 className="text-lg font-semibold text-white">Invite invalid or expired</h1>
          <p className="text-sm text-white/70 mt-2">
            This invite link is no longer valid. Ask the company owner to send a new one.
          </p>
        </div>
      </div>
    );
  }

  const preview = previewQuery.data;

  if (accepted) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <div className="max-w-md w-full rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-8 text-center">
          <CheckCircle className="w-8 h-8 text-green-400 mx-auto mb-3" />
          <h1 className="text-lg font-semibold text-white">You're in!</h1>
          <p className="text-sm text-white/70 mt-2">
            You have joined <strong>{preview.companyName}</strong> as a {preview.role.toLowerCase()}.
          </p>
          <button
            onClick={() => navigate("/dashboard")}
            className="mt-5 inline-flex items-center gap-2 px-5 py-2 rounded-full bg-blue-600 hover:bg-blue-500 text-white font-semibold transition-colors"
          >
            Go to dashboard
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
      <div className="max-w-md w-full rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-8 text-center">
        <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">
          Invitation
        </p>
        <h1 className="text-2xl font-extrabold text-white">{preview.companyName}</h1>
        <p className="text-sm text-white/70 mt-3 leading-relaxed">
          {preview.inviterDisplayName ? <strong>{preview.inviterDisplayName}</strong> : "Someone"}{" "}
          invited you to join as a <strong>{preview.role.toLowerCase()}</strong>.
        </p>
        <p className="text-xs text-white/55 mt-2">
          Invite sent to <strong>{preview.invitedEmail}</strong>
        </p>
        {acceptMutation.error && (
          <p className="text-sm text-red-400 mt-4">
            {(acceptMutation.error as any)?.response?.data?.message ?? "Failed to accept invite"}
          </p>
        )}
        <button
          onClick={() => acceptMutation.mutate()}
          disabled={acceptMutation.isPending}
          className="mt-5 inline-flex items-center gap-2 px-5 py-2 rounded-full bg-blue-600 hover:bg-blue-500 text-white font-semibold transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Accept invitation
        </button>
      </div>
    </div>
  );
}
