import type { Metadata } from "next";
import { PolicyPage } from "../ui/PolicyPage";

export const metadata: Metadata = {
  title: "Refunds and cancellation",
  description: "MySend Premium recurring billing, cancellation, and refund policy.",
};

export default function RefundsPage() {
  return (
    <PolicyPage
      eyebrow="Billing"
      title="Refunds & cancellation"
      intro="MySend Premium is a monthly subscription that you control from account settings."
    >
      <section>
        <h2>Recurring billing</h2>
        <p>
          MySend Premium is CA$9.99 per month, plus any applicable tax shown at checkout.
          By subscribing, you authorize Stripe to charge your selected payment method at
          the start of each billing period until you cancel.
        </p>
      </section>

      <section>
        <h2>Cancellation</h2>
        <p>
          You can cancel through the subscription portal linked from MySend account
          settings. Cancellation takes effect at the end of the current paid billing
          period. You keep Premium access until then and will not be charged for the next
          period.
        </p>
      </section>

      <section>
        <h2>Refunds</h2>
        <p>
          Subscription payments are generally non-refundable, and cancelling does not
          create a refund for time remaining in the current billing period. This does not
          limit any refund or cancellation right that applicable law gives you.
        </p>
      </section>

      <section>
        <h2>Billing problems</h2>
        <p>
          If you believe a charge is duplicated, incorrect, or unauthorized, contact
          {" "}<a href="mailto:mysend.support@gmail.com">mysend.support@gmail.com</a>
          {" "}with the email address used for the subscription and the charge date. Do not
          email card numbers or other complete payment credentials.
        </p>
      </section>

      <section>
        <h2>Price changes</h2>
        <p>
          If the Premium price changes, existing subscribers will receive advance notice
          when required by applicable law before a new price applies to a future renewal.
        </p>
      </section>
    </PolicyPage>
  );
}
