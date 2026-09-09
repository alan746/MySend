import type { Metadata } from "next";
import { PolicyPage } from "../ui/PolicyPage";

export const metadata: Metadata = {
  title: "Contact",
  description: "Contact MySend support about rooms, accounts, privacy, or billing.",
};

export default function ContactPage() {
  return (
    <PolicyPage
      eyebrow="Support"
      title="Contact MySend"
      intro="Get help with a room, your account, privacy, or a Premium subscription."
    >
      <section className="contact-callout">
        <span>Email support</span>
        <a href="mailto:mysend.support@gmail.com">mysend.support@gmail.com</a>
        <p>Use the email address connected to your MySend account when possible.</p>
      </section>

      <section>
        <h2>What to include</h2>
        <ul>
          <li>a short description of what happened;</li>
          <li>the room code, if the issue is room-specific;</li>
          <li>the approximate date and time of the problem; and</li>
          <li>for billing questions, the subscription email and charge date.</li>
        </ul>
        <p>
          Never send your password, full card number, security code, or Stripe secret
          keys by email.
        </p>
      </section>

      <section>
        <h2>Report abuse</h2>
        <p>
          To report harmful or unauthorized use, email the room code and a concise
          description to the support address above. Avoid forwarding harmful files when a
          filename and description are enough.
        </p>
      </section>
    </PolicyPage>
  );
}
