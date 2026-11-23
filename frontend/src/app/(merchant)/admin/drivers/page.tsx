import type { Metadata } from "next";
import DriverDispatchPage from "@views/admin/DriverDispatchPage";

export const metadata: Metadata = { title: "Driver dispatch" };

export default function Page() {
  return <DriverDispatchPage />;
}
