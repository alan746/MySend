import type { Metadata } from "next";
import { PolicyPage } from "../ui/PolicyPage";

export const metadata: Metadata = {
  title: "Terms of service",
  description: "Terms for using MySend and subscribing to MySend Premium.",
};

export default function TermsPage() {
  return (
    <PolicyPage
      eyebrow="Terms"
      title="Terms of service"
      intro="These terms explain the rules for using MySend, including its temporary ShareRooms and optional Premium subscription."
    >
      <section>
        <h2>Using MySend</h2>
        <p>
          MySend lets you create short-lived ShareRooms for text and files. You may use
          the service as a guest or create an account for additional room limits and
          account features. You are responsible for activity under your account and for
          keeping your sign-in details secure.
        </p>
      </section>

      <section>
        <h2>Temporary rooms</h2>
        <p>
          ShareRooms are temporary by design. A room becomes unavailable when its owner
          closes it, its timer ends, or its entry allowance is exhausted. MySend is not
          an archival or backup service. Keep your own copy of anything you need after a
          room closes.
        </p>
      </section>

      <section>
        <h2>Premium subscription</h2>
        <p>
          MySend Premium costs CA$9.99 per month, plus any applicable tax shown at
          checkout. It renews automatically each month until cancelled. Stripe processes
          payment and provides the hosted checkout and subscription portal. Premium
          limits apply while the subscription is active.
        </p>
        <p>
          You can manage or cancel Premium from your MySend account settings. Cancellation
          takes effect at the end of the current paid billing period unless applicable law
          requires otherwise. Our <a href="/refunds">refund and cancellation policy</a>
          provides more detail.
        </p>
      </section>

      <section>
        <h2>Acceptable use</h2>
        <p>You must not use MySend to:</p>
        <ul>
          <li>break the law or violate another person&apos;s rights;</li>
          <li>share malware, harmful code, or deceptive content;</li>
          <li>probe, disrupt, overload, or bypass the service&apos;s security and limits;</li>
          <li>access a room, account, or file without permission; or</li>
          <li>use the service in a way that harms MySend or other users.</li>
        </ul>
        <p>
          We may restrict or end access when reasonably necessary to protect the service,
          its users, or comply with law.
        </p>
      </section>

      <section>
        <h2>Your content</h2>
        <p>
          You keep ownership of content you submit. You give MySend the limited permission
          needed to receive, store, transmit, and delete that content as part of operating
          the room. You are responsible for having the right to share it.
        </p>
      </section>

      <section>
        <h2>Service availability</h2>
        <p>
          We work to keep MySend available and secure, but the service is provided on an
          as-available basis. Features and limits may change. If a change materially
          affects a paid subscription, we will provide notice as required by applicable
          law. To the extent permitted by law, MySend is not responsible for lost content,
          interrupted transfers, or indirect losses.
        </p>
      </section>

      <section>
        <h2>Questions</h2>
        <p>
          Contact <a href="mailto:mysend.support@gmail.com">mysend.support@gmail.com</a>
          {" "}with questions about these terms.
        </p>
      </section>
    </PolicyPage>
  );
}
