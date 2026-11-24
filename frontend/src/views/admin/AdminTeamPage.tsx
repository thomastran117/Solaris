"use client";

import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { UserPlus, Trash2, ShieldAlert } from "lucide-react";
import type { RootState } from "../../stores";
import { teamApi, type CompanyRole, type TeamMembership } from "../../api/team";
import { useCompanyCapabilities } from "../../hooks/useCompanyRole";
import { useAnims } from "../../hooks/useAnims";

function roleBadgeClass(role: CompanyRole): string {
  switch (role) {
    case "OWNER":
      return "border-primary/40 text-link bg-primary/10";
    case "MANAGER":
      return "border-primary/40 text-link bg-primary/10";
    case "EMPLOYEE":
    default:
      return "border-input text-muted-foreground bg-surface";
  }
}

function statusBadgeClass(status: TeamMembership["status"]): string {
  switch (status) {
    case "ACTIVE":
      return "border-success/40 text-success";
    case "PENDING":
      return "border-warning/40 text-warning";
    case "REVOKED":
    default:
      return "border-border text-muted-foreground";
  }
}

export default function AdminTeamPage() {
  const { fadeInUp, stagger } = useAnims();
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] =
    useState<Exclude<CompanyRole, "OWNER">>("MANAGER");
  const [error, setError] = useState<string | null>(null);

  const { can, isLoading: roleLoading } = useCompanyCapabilities(companyId);
  const canManage = can("MANAGE_COMPANY");

  const teamQuery = useQuery<TeamMembership[]>({
    queryKey: ["companies", companyId, "team"],
    queryFn: () => teamApi.list(companyId!).then((r) => r.data),
    enabled: !!companyId && canManage,
  });

  const inviteMutation = useMutation({
    mutationFn: () =>
      teamApi
        .invite(companyId!, { email: inviteEmail.trim(), role: inviteRole })
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["companies", companyId, "team"],
      });
      setInviteEmail("");
      setError(null);
    },
    onError: (e: any) => {
      setError(e?.response?.data?.message ?? "Failed to send invite");
    },
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({
      membershipId,
      role,
    }: {
      membershipId: string;
      role: Exclude<CompanyRole, "OWNER">;
    }) =>
      teamApi
        .updateRole(companyId!, membershipId, { role })
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["companies", companyId, "team"],
      });
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (membershipId: string) =>
      teamApi.revoke(companyId!, membershipId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["companies", companyId, "team"],
      });
    },
  });

  if (!companyId) {
    return (
      <div className="px-6 py-12">
        <p className="text-muted-foreground">No company selected.</p>
      </div>
    );
  }

  if (!roleLoading && !canManage) {
    return (
      <div className="px-6 py-12">
        <div className="max-w-2xl mx-auto rounded-xl border border-border bg-surface shadow-sm p-8 text-center">
          <ShieldAlert className="w-8 h-8 text-link mx-auto mb-3" />
          <h2 className="text-lg font-semibold text-foreground">Owner-only</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Only the company owner can manage team members.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="px-6 py-12">
      <div className="max-w-4xl mx-auto space-y-8">
        <header>
          <p className="text-xs font-semibold text-link mb-2">Company</p>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            Team
          </h1>
          <p className="text-sm text-muted-foreground mt-2 leading-relaxed">
            Invite managers to help run the business or employees to fulfill
            orders.
          </p>
        </header>

        <section className="rounded-xl border border-border bg-surface shadow-sm p-6">
          <h2 className="text-lg font-semibold text-foreground mb-4">
            Invite teammate
          </h2>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (inviteEmail.trim()) inviteMutation.mutate();
            }}
            className="flex flex-col md:flex-row gap-3"
          >
            <input
              type="email"
              required
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              placeholder="email@example.com"
              className="flex-1 px-4 py-2 rounded-full bg-surface border border-border text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring"
            />
            <select
              value={inviteRole}
              onChange={(e) =>
                setInviteRole(e.target.value as Exclude<CompanyRole, "OWNER">)
              }
              className="px-4 py-2 rounded-full bg-surface border border-border text-foreground focus:outline-none focus:border-ring"
            >
              <option value="MANAGER">Manager</option>
              <option value="EMPLOYEE">Employee</option>
            </select>
            <button
              type="submit"
              disabled={inviteMutation.isPending || !inviteEmail.trim()}
              className="inline-flex items-center gap-2 px-5 py-2 rounded-full bg-primary hover:bg-primary-hover text-primary-foreground font-semibold transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <UserPlus className="w-4 h-4" />
              Send invite
            </button>
          </form>
          {error && <p className="text-sm text-danger mt-3">{error}</p>}
        </section>

        <section>
          <h2 className="text-lg font-semibold text-foreground mb-4">
            Members
          </h2>
          {teamQuery.isLoading ? (
            <div className="rounded-xl border border-border bg-surface shadow-sm p-8 text-center text-muted-foreground">
              Loading…
            </div>
          ) : (teamQuery.data?.length ?? 0) === 0 ? (
            <div className="rounded-xl border border-border bg-surface shadow-sm p-8 text-center text-muted-foreground">
              No team members yet.
            </div>
          ) : (
            <motion.ul
              variants={stagger}
              initial="hidden"
              animate="visible"
              className="space-y-3"
            >
              {teamQuery.data!.map((m) => (
                <motion.li
                  key={m.id}
                  variants={fadeInUp}
                  className="rounded-xl border border-border bg-surface shadow-sm hover:shadow-md p-4 flex items-center gap-3"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-foreground truncate">
                        {m.displayName ?? m.email ?? `User #${m.userId}`}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${roleBadgeClass(m.role)}`}
                      >
                        {m.role}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${statusBadgeClass(m.status)}`}
                      >
                        {m.status}
                      </span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-1">
                      {m.email}
                    </p>
                  </div>
                  {m.role !== "OWNER" && m.status === "ACTIVE" && (
                    <select
                      value={m.role}
                      onChange={(e) =>
                        updateRoleMutation.mutate({
                          membershipId: m.id,
                          role: e.target.value as Exclude<CompanyRole, "OWNER">,
                        })
                      }
                      className="px-3 py-1.5 rounded-full text-xs bg-surface border border-border text-foreground focus:outline-none focus:border-ring"
                    >
                      <option value="MANAGER">Manager</option>
                      <option value="EMPLOYEE">Employee</option>
                    </select>
                  )}
                  {m.role !== "OWNER" && (
                    <button
                      type="button"
                      onClick={() => revokeMutation.mutate(m.id)}
                      className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs border border-input text-foreground hover:bg-muted transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Revoke
                    </button>
                  )}
                </motion.li>
              ))}
            </motion.ul>
          )}
        </section>
      </div>
    </div>
  );
}
