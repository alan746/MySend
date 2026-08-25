import type { Metadata } from "next";
import { DashboardExperience } from "../ui/DashboardExperience";

export const metadata: Metadata = {
  title: "Dashboard",
  description: "Create, join, and manage your active MySend ShareRooms.",
};

export default function DashboardPage() {
  return <DashboardExperience />;
}
