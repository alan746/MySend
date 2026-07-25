"use client";

import Link from "next/link";
import { BrandMark } from "./BrandMark";

type SiteHeaderProps = {
  compact?: boolean;
};

export function SiteHeader({ compact = false }: SiteHeaderProps) {
  return (
    <header className={`site-header ${compact ? "site-header--compact" : ""}`}>
      <Link className="brand" href="/" aria-label="MySend home">
        <BrandMark />
        <span>MySend</span>
      </Link>
      <nav aria-label="Main navigation">
        <Link href="/#how-it-works">How it works</Link>
        <Link href="/#plans">Plans</Link>
        <Link className="nav-account" href="/settings">
          Settings
        </Link>
      </nav>
    </header>
  );
}
