import type { Metadata } from "next";
import { SettingsExperience } from "../ui/SettingsExperience";

export const metadata: Metadata = {
  title: "Account settings",
  description: "Manage your MySend email, membership, and account security.",
};

export default function SettingsPage() {
  return <SettingsExperience />;
}
