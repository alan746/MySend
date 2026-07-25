import type { Metadata } from "next";
import { SettingsExperience } from "../ui/SettingsExperience";

export const metadata: Metadata = {
  title: "Settings & membership",
  description: "Manage your MySend account, active rooms, and membership.",
};

export default function SettingsPage() {
  return <SettingsExperience />;
}
