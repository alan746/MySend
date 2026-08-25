"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Account, api } from "../lib/api";
import { BrandMark } from "./BrandMark";

type SiteHeaderProps = {
  compact?: boolean;
};

export function SiteHeader({ compact = false }: SiteHeaderProps) {
  const [account, setAccount] = useState<Account | null>(null);

  useEffect(() => {
    let active = true;
    api<Account>("/api/auth/me")
      .then((current) => {
        if (active) setAccount(current);
      })
      .catch(() => {
        if (active) setAccount(null);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <header className={`site-header ${compact ? "site-header--compact" : ""}`}>
      <Link className="brand" href="/" aria-label="MySend home">
        <BrandMark />
        <span>MySend</span>
      </Link>
      <nav aria-label="Main navigation">
        <Link className="nav-section" href="/#how-it-works">How it works</Link>
        <Link className="nav-section" href="/#plans">Plans</Link>
        {account ? (
          <Link className="nav-account" href="/settings">Settings</Link>
        ) : (
          <div className="nav-auth" aria-label="Account navigation">
            <Link className="nav-login" href="/login">Log in</Link>
            <Link className="nav-account" href="/signup">Sign up</Link>
          </div>
        )}
      </nav>
    </header>
  );
}
