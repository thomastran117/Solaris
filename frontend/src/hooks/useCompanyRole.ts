import { useQuery } from "@tanstack/react-query";
import { teamApi, type CompanyCapability, type CompanyRoleInfo } from "../api/team";

export function useCompanyRole(companyId: number | null | undefined) {
  return useQuery<CompanyRoleInfo>({
    queryKey: ["companies", companyId, "role"],
    queryFn: () => teamApi.describeMyAccess(companyId!).then(r => r.data),
    enabled: !!companyId,
    staleTime: 60_000,
  });
}

export function useCompanyCapabilities(companyId: number | null | undefined) {
  const query = useCompanyRole(companyId);
  const capabilities = query.data?.capabilities ?? [];
  const can = (cap: CompanyCapability) => capabilities.includes(cap);
  return {
    ...query,
    capabilities,
    can,
  };
}
