import type { ReactNode } from "react";
import { SiteFooter } from "./SiteFooter";
import { SiteHeader } from "./SiteHeader";

type PolicyPageProps = {
  eyebrow: string;
  title: string;
  intro: string;
  children: ReactNode;
};

export function PolicyPage({ eyebrow, title, intro, children }: PolicyPageProps) {
  return (
    <div className="legal-page">
      <SiteHeader compact />
      <main className="legal-layout">
        <nav className="legal-nav" aria-label="Policies and support">
          <span>Information</span>
          <a href="/terms">Terms of service</a>
          <a href="/privacy">Privacy policy</a>
          <a href="/refunds">Refunds &amp; cancellation</a>
          <a href="/contact">Contact</a>
        </nav>
        <article className="legal-card">
          <header>
            <span className="section-kicker">{eyebrow}</span>
            <h1>{title}</h1>
            <p>{intro}</p>
            <time dateTime="2026-09-09">Effective September 9, 2026</time>
          </header>
          <div className="legal-content">{children}</div>
        </article>
      </main>
      <SiteFooter />
    </div>
  );
}
