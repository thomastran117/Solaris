"use client";

import { useAppSelector } from "../../stores/hooks";
import { useCompanyCapabilities } from "../../hooks/useCompanyRole";
import { visibleAdminNav, type AdminNavGroup } from "./adminNav";

/**
 * The admin nav filtered for the signed-in user. `loading` is true while the
 * company capabilities are still being fetched, so callers can show
 * placeholders instead of a nav that grows after a moment.
 */
export function useAdminNav(): { groups: AdminNavGroup[]; loading: boolean } {
  const { accessToken, companyId, role } = useAppSelector(
    (state) => state.auth
  );
  const activeCompany = accessToken ? companyId : null;
  const { capabilities, isPending } = useCompanyCapabilities(activeCompany);
  const groups = visibleAdminNav({
    companyId: activeCompany,
    role,
    capabilities,
  });
  return { groups, loading: !!activeCompany && isPending };
}
