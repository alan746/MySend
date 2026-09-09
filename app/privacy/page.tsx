import type { Metadata } from "next";
import { PolicyPage } from "../ui/PolicyPage";

export const metadata: Metadata = {
  title: "Privacy policy",
  description: "How MySend handles account, room, security, and billing data.",
};

export default function PrivacyPage() {
  return (
    <PolicyPage
      eyebrow="Privacy"
      title="Privacy policy"
      intro="MySend collects the information needed to run temporary ShareRooms, protect the service, and manage accounts and subscriptions."
    >
      <section>
        <h2>Information we handle</h2>
        <ul>
          <li>account details, including your email address and secured password data;</li>
          <li>room settings, clipboard text, uploaded files, and room activity;</li>
          <li>technical and security data used for sessions, diagnostics, and abuse prevention;</li>
          <li>email-verification and password-recovery records; and</li>
          <li>Stripe customer, checkout, and subscription identifiers for Premium members.</li>
        </ul>
        <p>
          Stripe handles payment-card details through its hosted payment pages. MySend
          does not receive or store your full card number.
        </p>
      </section>

      <section>
        <h2>How we use information</h2>
        <p>
          We use information to create and operate rooms, authenticate accounts, send
          requested account emails, apply plan limits, process subscription status,
          prevent abuse, troubleshoot problems, and comply with legal obligations. MySend
          does not sell personal information.
        </p>
      </section>

      <section>
        <h2>Room lifecycle and retention</h2>
        <p>
          Room content is temporary. A room becomes unavailable when it is closed,
          expires, or uses all allowed entries. Its stored content is then queued for
          deletion. Account, billing, transaction, security, and operational records may
          be retained longer when needed to provide the service, resolve disputes,
          prevent abuse, or meet legal requirements.
        </p>
      </section>

      <section>
        <h2>Service providers</h2>
        <p>
          MySend uses service providers to host the application and database, deliver
          account email, and process subscriptions. These currently include Railway,
          Resend, and Stripe. They process relevant information under their own terms and
          privacy practices. Information may be processed outside your province, state,
          or country.
        </p>
      </section>

      <section>
        <h2>Cookies and security</h2>
        <p>
          MySend uses secure session cookies to keep accounts and rooms working. We use
          technical safeguards designed to protect information, but no online service can
          guarantee absolute security. Do not place sensitive information in a room unless
          you are comfortable sharing it with everyone who can enter that room.
        </p>
      </section>

      <section>
        <h2>Your choices</h2>
        <p>
          You can close rooms, manage your subscription, and update supported billing
          details from account settings. To ask about, correct, or request deletion of
          personal information, email
          {" "}<a href="mailto:mysend.support@gmail.com">mysend.support@gmail.com</a>.
          We may need to verify your identity before completing a request.
        </p>
      </section>

      <section>
        <h2>Policy changes</h2>
        <p>
          We may update this policy as MySend changes. The effective date on this page
          identifies the current version.
        </p>
      </section>
    </PolicyPage>
  );
}
