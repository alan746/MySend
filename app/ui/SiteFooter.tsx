export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer__brand">
        <span>MySend</span>
        <p>Share what matters. Keep nothing longer than necessary.</p>
      </div>
      <nav aria-label="Policies and support">
        <a href="/terms">Terms</a>
        <a href="/privacy">Privacy</a>
        <a href="/refunds">Refunds &amp; cancellation</a>
        <a href="/contact">Contact</a>
      </nav>
      <a className="site-footer__account" href="/settings">Account &amp; settings</a>
    </footer>
  );
}
