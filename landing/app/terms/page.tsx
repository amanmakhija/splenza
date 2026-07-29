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
        Last updated: <strong>29/07/2026</strong>
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
      </ul>
      <p style={styles.p}>
        We may suspend or terminate accounts that violate these Terms.
      </p>

      <h2 style={styles.h2}>4. User Content</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          You retain ownership of any content you submit (names, notes, group
          descriptions, profile pictures).
        </li>
        <li style={styles.li}>
          By submitting content, you grant Splenza a limited license to store,
          display, and process that content as necessary to provide the
          App&apos;s functionality (e.g. showing an expense you created to other
          members of your group).
        </li>
        <li style={styles.li}>
          You are responsible for ensuring you have the right to upload any
          content (e.g. a profile picture) you submit.
        </li>
      </ul>

      <h2 style={styles.h2}>5. Accuracy of Financial Records</h2>
      <p style={styles.p}>
        Expense amounts, splits, and settlement records are entered by users,
        not verified by Splenza. We&apos;re not responsible for disputes between
        users about who owes what, whether a payment was actually made outside
        the App, or the accuracy of any user-submitted data. Splenza is a
        record-keeping convenience, not a source of financial or legal truth
        between parties.
      </p>

      <h2 style={styles.h2}>6. Push Notifications</h2>
      <p style={styles.p}>
        By using the App, you may receive push notifications related to friend
        requests, expense activity, and settlements in groups you belong to. You
        can disable notifications at any time through your device&apos;s system
        settings.
      </p>

      <h2 style={styles.h2}>7. Third-Party Sign-In</h2>
      <p style={styles.p}>
        If you sign in using Google, your use of that service is also governed
        by Google&apos;s own terms and privacy policy. We&apos;re not
        responsible for the availability or behavior of third-party sign-in
        providers.
      </p>

      <h2 style={styles.h2}>8. Service Availability</h2>
      <p style={styles.p}>
        We aim to keep Splenza available and reliable, but we don&apos;t
        guarantee uninterrupted access. The App may be unavailable occasionally
        for maintenance, updates, or issues outside our control. We&apos;re not
        liable for any loss resulting from downtime.
      </p>

      <h2 style={styles.h2}>9. Disclaimer of Warranties</h2>
      <p style={styles.legal}>
        The App is provided &quot;as is&quot; and &quot;as available&quot;
        without warranties of any kind, either express or implied, including but
        not limited to implied warranties of merchantability, fitness for a
        particular purpose, or non-infringement. We do not warrant that the App
        will be uninterrupted, error-free, or secure.
      </p>

      <h2 style={styles.h2}>10. Limitation of Liability</h2>
      <p style={styles.legal}>
        To the maximum extent permitted by law, Splenza and its developers shall
        not be liable for any indirect, incidental, special, consequential, or
        punitive damages, or any loss of data, money, goodwill, or other
        intangible losses, resulting from your use of (or inability to use) the
        App, including any disputes between users regarding expenses or
        settlements recorded in the App.
      </p>

      <h2 style={styles.h2}>11. Changes to the App or Terms</h2>
      <p style={styles.p}>
        We may update these Terms or change, suspend, or discontinue any part of
        the App at any time. We&apos;ll notify you of material changes to these
        Terms via the App or email. Continued use after a change means you
        accept the updated Terms.
      </p>

      <h2 style={styles.h2}>12. Termination</h2>
      <p style={styles.p}>
        You may stop using Splenza and delete your account at any time. We may
        suspend or terminate your account if you violate these Terms or if we
        discontinue the service, with notice where reasonably possible.
      </p>

      <h2 style={styles.h2}>13. Governing Law</h2>
      <p style={styles.p}>
        These Terms are governed by the laws of India, without regard to
        conflict-of-law principles. Any disputes will be subject to the
        exclusive jurisdiction of the courts in India.
      </p>

      <h2 style={styles.h2}>14. Contact Us</h2>
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
