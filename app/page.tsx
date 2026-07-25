import type { Metadata } from "next";
import { HomeExperience } from "./ui/HomeExperience";

export const metadata: Metadata = {
  title: "Send what you need",
  description:
    "Create or join a short-lived ShareRoom for text and files with one memorable code.",
};

export default function Home() {
  return <HomeExperience />;
}
