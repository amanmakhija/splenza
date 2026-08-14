import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Terms & Conditions — Splenza",
  description: "The terms that govern your use of Splenza.",
};

export default function TermsPage() {
  return (
    <div style={styles.container}>
      <h1 style={styles.h1}>Terms & Conditions — Splenza</h1>
      <p style={styles.updated}>
        Last updated: <strong>15/08/2026</strong>
      </p>

      <p style={styles.p}>
        These Terms & Conditions (&quot;Terms&quot;) govern your use of Splenza
        (&quot;we&quot;, &quot;our&quot;, &quot;the App&quot;). By creating an
        account or using the App, you agree to these Terms. If you don&apos;t
        agree, please don&apos;t use the App.
      </p>

      <h2 style={styles.h2}>1. What Splenza Is</h2>
      <p style={styles.p}>
        Splenza is a tool for tracking and splitting shared expenses among
        friends and groups.{" "}
        <strong>Splenza does not process, hold, or transfer money.</strong> It
        records who owes what based on information you enter. Any actual payment
        between users happens outside the App (cash, UPI, bank transfer, or any
        other method you choose), and Splenza has no visibility into or
        responsibility for whether that payment actually occurs.
      </p>

      <h2 style={styles.h2}>2. Your Account</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          You must provide accurate information when creating an account.
        </li>
        <li style={styles.li}>
          You&apos;re responsible for keeping your login credentials secure and
          for all activity under your account.
        </li>
        <li style={styles.li}>
          You must be at least 13 years old (or the minimum age of digital
          consent in your country) to use Splenza.
        </li>
        <li style={styles.li}>
          You can delete your account at any time from within the App.
        </li>
      </ul>

      <h2 style={styles.h2}>3. Acceptable Use</h2>
      <p style={styles.p}>You agree not to:</p>
      <ul style={styles.ul}>
        <li style={styles.li}>
          Use the App for anything unlawful, fraudulent, or intended to harass
          or harm another person
        </li>
        <li style={styles.li}>
          Enter false expense or settlement records with intent to deceive
          another user
        </li>
        <li style={styles.li}>
          Attempt to interfere with, disrupt, reverse-engineer, or gain
          unauthorized access to the App or its systems
        </li>
        <li style={styles.li}>
          Use the App to collect or harvest other users&apos; personal
          information beyond what&apos;s needed to split expenses with them
        </li>
        <li style={styles.li}>
          Upload content that is defamatory, harassing, obscene, or infringes on
          the rights of others (e.g. in group names, notes, or expense
          titles/descriptions)
        </li>
        <li style={styles.li}>
          Attempt to circumvent, automate, or abuse the AI-assisted features or
          credit system described in sections 5 and 6 (e.g. scripting repeated
          scans to exhaust free credits, or exploiting the purchase flow)
        </li>
      </ul>
      <p style={styles.p}>
        We may suspend or terminate accounts that violate these Terms.
      </p>

      <h2 style={styles.h2}>4. User Content</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          You retain ownership of any content you submit (names, notes, group
          descriptions, profile pictures, receipt photos).
        </li>
        <li style={styles.li}>
          By submitting content, you grant Splenza a limited license to store,
          display, and process that content as necessary to provide the
          App&apos;s functionality (e.g. showing an expense you created to other
          members of your group, or sending a receipt photo to our AI processing
          partner as described in our Privacy Policy).
        </li>
        <li style={styles.li}>
          You are responsible for ensuring you have the right to upload any
          content (e.g. a profile picture or receipt) you submit.
        </li>
      </ul>

      <h2 style={styles.h2}>5. AI-Assisted Features</h2>
      <p style={styles.p}>
        Splenza offers optional AI-assisted features — reading details off a
        photographed receipt, and suggesting an expense category from text you
        type. These features are automated and provided purely for convenience.
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>
          Amounts, dates, merchant names, and categories extracted or suggested
          by these features <strong>may be inaccurate or incomplete</strong>.
          They are shown to you to review before anything is saved.
        </li>
        <li style={styles.li}>
          You are solely responsible for reviewing and confirming all details
          before saving an expense that used an AI-assisted feature.
        </li>
        <li style={styles.li}>
          We are not liable for financial discrepancies, disputes between users,
          or losses arising from inaccurate AI-generated data that you did not
          correct before saving.
        </li>
        <li style={styles.li}>
          These features, their daily free allowances, and their availability
          may change or be temporarily or permanently discontinued at our
          discretion.
        </li>
      </ul>

      <h2 style={styles.h2}>6. Credits and In-App Purchases</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          <strong>What credits are.</strong> Certain AI-assisted features (such
          as receipt scanning) consume &quot;credits.&quot; Each feature has its
          own limited number of free credits that reset once every 24 hours.
          Additional credits can be purchased and are shared across all
          AI-assisted features that use credits.
        </li>
        <li style={styles.li}>
          <strong>Purchases.</strong> Credit purchases are made and processed
          through Google Play Billing and are subject to Google Play&apos;s own
          terms and payment processing. We do not store your payment card or
          bank details.
        </li>
        <li style={styles.li}>
          <strong>No expiry, no cash value.</strong> Purchased credits do not
          expire and have no cash value. Credits cannot be transferred,
          exchanged for cash, or redeemed outside the App.
        </li>
        <li style={styles.li}>
          <strong>Refunds.</strong> Credit purchases are generally
          non-refundable, except where required by applicable law or Google
          Play&apos;s own refund policies. Refund requests should be made
          through Google Play. If a purchase is refunded or charged back, we
          reserve the right to deduct the corresponding credits from your
          account.
        </li>
        <li style={styles.li}>
          <strong>Free daily credits.</strong> Free daily credit allowances
          reset once every 24 hours per feature, do not roll over if unused, and
          may be changed at our discretion with reasonable notice where required
          by law.
        </li>
      </ul>

      <h2 style={styles.h2}>7. Accuracy of Financial Records</h2>
      <p style={styles.p}>
        Expense amounts, splits, and settlement records are entered by users
        (whether typed manually or filled in via an AI-assisted feature you
        chose to use and confirm), not independently verified by Splenza.
        We&apos;re not responsible for disputes between users about who owes
        what, whether a payment was actually made outside the App, or the
        accuracy of any user-submitted data. Splenza is a record-keeping
        convenience, not a source of financial or legal truth between parties.
      </p>

      <h2 style={styles.h2}>8. Push Notifications</h2>
      <p style={styles.p}>
        By using the App, you may receive push notifications related to friend
        requests, expense activity, and settlements in groups you belong to. You
        can disable notifications at any time through your device&apos;s system
        settings.
      </p>

      <h2 style={styles.h2}>9. Finding Friends via Your Contacts</h2>
      <p style={styles.p}>
        Splenza includes an optional feature that, with your permission, reads
        your device&apos;s contacts to show you which of them already use the
        App and lets you invite the ones who don&apos;t. Any invitation you
        choose to send is delivered through your own device&apos;s messaging or
        sharing apps (e.g. SMS, WhatsApp, email) — Splenza does not send
        messages to your contacts on your behalf. You&apos;re responsible for
        only inviting people you have a genuine relationship with or a
        reasonable basis to contact, and for complying with any applicable
        anti-spam or communications laws in your jurisdiction. You can decline
        or revoke contacts access at any time through your device settings
        without affecting your ability to use the rest of the App.
      </p>

      <h2 style={styles.h2}>10. Third-Party Sign-In</h2>
      <p style={styles.p}>
        If you sign in using Google, your use of that service is also governed
        by Google&apos;s own terms and privacy policy. We&apos;re not
        responsible for the availability or behavior of third-party sign-in
        providers.
      </p>

      <h2 style={styles.h2}>11. Service Availability</h2>
      <p style={styles.p}>
        We aim to keep Splenza available and reliable, but we don&apos;t
        guarantee uninterrupted access. The App may be unavailable occasionally
        for maintenance, updates, or issues outside our control. We&apos;re not
        liable for any loss resulting from downtime.
      </p>

      <h2 style={styles.h2}>12. Disclaimer of Warranties</h2>
      <p style={styles.legal}>
        The App is provided &quot;as is&quot; and &quot;as available&quot;
        without warranties of any kind, either express or implied, including but
        not limited to implied warranties of merchantability, fitness for a
        particular purpose, or non-infringement. We do not warrant that the App
        will be uninterrupted, error-free, or secure, or that any AI-assisted
        feature will produce accurate results.
      </p>

      <h2 style={styles.h2}>13. Limitation of Liability</h2>
      <p style={styles.legal}>
        To the maximum extent permitted by law, Splenza and its developers shall
        not be liable for any indirect, incidental, special, consequential, or
        punitive damages, or any loss of data, money, goodwill, or other
        intangible losses, resulting from your use of (or inability to use) the
        App, including any disputes between users regarding expenses or
        settlements recorded in the App, and including losses arising from
        inaccurate output of any AI-assisted feature.
      </p>

      <h2 style={styles.h2}>14. Changes to the App or Terms</h2>
      <p style={styles.p}>
        We may update these Terms or change, suspend, or discontinue any part of
        the App at any time. We&apos;ll notify you of material changes to these
        Terms via the App or email. Continued use after a change means you
        accept the updated Terms.
      </p>

      <h2 style={styles.h2}>15. Termination</h2>
      <p style={styles.p}>
        You may stop using Splenza and delete your account at any time. We may
        suspend or terminate your account if you violate these Terms or if we
        discontinue the service, with notice where reasonably possible.
        Purchased credits are forfeited upon account deletion unless required
        otherwise by applicable law.
      </p>

      <h2 style={styles.h2}>16. Governing Law</h2>
      <p style={styles.p}>
        These Terms are governed by the laws of India, without regard to
        conflict-of-law principles. Any disputes will be subject to the
        exclusive jurisdiction of the courts in India.
      </p>

      <h2 style={styles.h2}>17. Contact Us</h2>
      <div style={styles.contactBox}>
        <p style={{ margin: 0 }}>
          <strong>help@splenza.in</strong>
        </p>
        <p style={{ margin: 0 }}>Splenza</p>
      </div>

      <hr style={styles.hr} />
      <p style={{ color: "#666666", fontSize: 13 }}>
        Splenza — Split expenses, not friendships.
      </p>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 760,
    margin: "0 auto",
    padding: "48px 24px 96px",
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
    lineHeight: 1.65,
    color: "#1A1A1A",
  },
  h1: { fontSize: 28, fontWeight: 800, marginBottom: 4 },
  updated: { color: "#666666", fontSize: 14, marginBottom: 32 },
  h2: {
    fontSize: 19,
    fontWeight: 700,
    marginTop: 40,
    marginBottom: 12,
    borderBottom: "1px solid #E5E5E5",
    paddingBottom: 8,
  },
  p: { margin: "12px 0", fontSize: 15 },
  // Used for the two ALL-CAPS legal boilerplate clauses (Warranties / Liability).
  // Kept visually distinct (smaller, tighter) since these read as dense legal
  // text by convention, not regular prose.
  legal: {
    margin: "12px 0",
    fontSize: 13,
    lineHeight: 20,
    textTransform: "uppercase",
    color: "#333333",
  },
  ul: { paddingLeft: 20 },
  li: { margin: "6px 0", fontSize: 15 },
  contactBox: {
    background: "#F0F0FA",
    border: "1px solid #E5E5E5",
    borderRadius: 12,
    padding: 20,
    marginTop: 16,
  },
  hr: { border: "none", borderTop: "1px solid #E5E5E5", margin: "32px 0" },
};
