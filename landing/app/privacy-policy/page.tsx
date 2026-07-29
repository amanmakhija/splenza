import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy — Splenza",
  description: "How Splenza collects, uses, and protects your information.",
};

export default function PrivacyPolicyPage() {
  return (
    <div style={styles.container}>
      <h1 style={styles.h1}>Privacy Policy — Splenza</h1>
      <p style={styles.updated}>
        Last updated: <strong>29/07/2026</strong>
      </p>

      <p style={styles.p}>
        This Privacy Policy explains how Splenza (&quot;we&quot;,
        &quot;our&quot;, &quot;the App&quot;) collects, uses, stores, and
        protects your information when you use our mobile application. By using
        Splenza, you agree to the practices described in this policy.
      </p>
      <p style={styles.p}>
        If you have questions, contact us at:{" "}
        <a href="mailto:help@splenza.in" style={styles.a}>
          help@splenza.in
        </a>
      </p>

      <h2 style={styles.h2}>1. Information We Collect</h2>

      <p style={styles.p}>
        <strong>1.1 Information you provide directly</strong>
      </p>
      <table style={styles.table}>
        <tbody>
          <tr>
            <th style={styles.th}>Data</th>
            <th style={styles.th}>Purpose</th>
            <th style={styles.th}>Required?</th>
          </tr>
          <tr>
            <td style={styles.td}>Name</td>
            <td style={styles.td}>
              Displayed to friends/group members you split expenses with
            </td>
            <td style={styles.td}>Yes</td>
          </tr>
          <tr>
            <td style={styles.td}>Email address</td>
            <td style={styles.td}>
              Account login, verification, password reset, notifications
            </td>
            <td style={styles.td}>Yes</td>
          </tr>
          <tr>
            <td style={styles.td}>Phone number</td>
            <td style={styles.td}>
              Optional — lets friends find you by phone number
            </td>
            <td style={styles.td}>No</td>
          </tr>
          <tr>
            <td style={styles.td}>Password</td>
            <td style={styles.td}>
              Account authentication (stored as a one-way bcrypt hash — we never
              store or can view your plain-text password)
            </td>
            <td style={styles.td}>Yes (unless signing in with Google)</td>
          </tr>
          <tr>
            <td style={styles.td}>Profile picture</td>
            <td style={styles.td}>Displayed to friends/group members</td>
            <td style={styles.td}>No</td>
          </tr>
          <tr>
            <td style={styles.td}>
              Expense, group, friend, and settlement data
            </td>
            <td style={styles.td}>
              Core app functionality — recording who paid what, group
              memberships, and money owed/settled between users
            </td>
            <td style={styles.td}>Yes, to use the app</td>
          </tr>
        </tbody>
      </table>

      <p style={styles.p}>
        <strong>1.2 Information collected automatically</strong>
      </p>
      <table style={styles.table}>
        <tbody>
          <tr>
            <th style={styles.th}>Data</th>
            <th style={styles.th}>Purpose</th>
          </tr>
          <tr>
            <td style={styles.td}>
              Device push notification token (FCM token)
            </td>
            <td style={styles.td}>
              Delivering notifications (friend requests, expense updates,
              settlements) to your device
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Device platform (Android/iOS)</td>
            <td style={styles.td}>
              Sending platform-appropriate push notifications
            </td>
          </tr>
          <tr>
            <td style={styles.td}>
              App activity log (e.g. &quot;expense created,&quot; &quot;group
              joined&quot;)
            </td>
            <td style={styles.td}>
              Powering the in-app activity feed within your groups
            </td>
          </tr>
        </tbody>
      </table>

      <p style={styles.p}>
        <strong>1.3 Information from third-party sign-in</strong>
      </p>
      <p style={styles.p}>
        If you choose to sign in with Google, we receive your Google
        account&apos;s name, email address, and profile picture from Google, per
        Google&apos;s own privacy practices, to create or log into your Splenza
        account. We do not receive your Google password.
      </p>

      <p style={styles.p}>
        <strong>1.4 Information we do not collect</strong>
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>
          We do not collect precise or continuous location data.
        </li>
        <li style={styles.li}>
          We do not access your contacts list automatically.
        </li>
        <li style={styles.li}>
          We do not read your device&apos;s SMS, call logs, or photos beyond an
          image you explicitly choose to upload as a profile picture.
        </li>
        <li style={styles.li}>
          We do not use advertising identifiers or serve third-party ads.
        </li>
      </ul>

      <h2 style={styles.h2}>2. How We Use Your Information</h2>
      <p style={styles.p}>We use the information above to:</p>
      <ul style={styles.ul}>
        <li style={styles.li}>Create and manage your account</li>
        <li style={styles.li}>
          Authenticate you and keep your session secure (via access/refresh
          tokens)
        </li>
        <li style={styles.li}>
          Let you split expenses, create groups, add friends, and settle
          balances with other users
        </li>
        <li style={styles.li}>
          Send you transactional push notifications (friend requests, expense
          activity, settlements) and account-related emails (OTP verification,
          password reset)
        </li>
        <li style={styles.li}>
          Maintain an activity log within groups you belong to, visible to other
          members of that group
        </li>
        <li style={styles.li}>
          Enforce fair use of the service (e.g. rate-limiting abusive request
          patterns)
        </li>
        <li style={styles.li}>Diagnose and fix technical problems</li>
      </ul>
      <p style={styles.p}>
        We do <strong>not</strong> sell your personal information to third
        parties, and we do not use your data for targeted advertising.
      </p>

      <h2 style={styles.h2}>3. Who Can See Your Information</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          <strong>Other users in your groups</strong> can see your name, profile
          picture, and the expenses/settlements you&apos;re involved in within
          shared groups — this is core to how the app functions.
        </li>
        <li style={styles.li}>
          <strong>Friends you&apos;ve connected with</strong> can see your name,
          email, and (if provided) phone number, and your shared balance/expense
          history with them.
        </li>
        <li style={styles.li}>
          <strong>We (the Splenza team)</strong> can access account and
          financial-record data as needed to operate, maintain, and troubleshoot
          the service. We do not read your data for any purpose beyond operating
          the app or complying with law.
        </li>
      </ul>

      <h2 style={styles.h2}>4. Third-Party Services We Use</h2>
      <table style={styles.table}>
        <tbody>
          <tr>
            <th style={styles.th}>Service</th>
            <th style={styles.th}>Purpose</th>
            <th style={styles.th}>Data shared</th>
          </tr>
          <tr>
            <td style={styles.td}>Google Sign-In</td>
            <td style={styles.td}>Optional authentication method</td>
            <td style={styles.td}>
              Google ID, name, email, profile picture (only if you use this
              sign-in method)
            </td>
          </tr>
          <tr>
            <td style={styles.td}>Firebase Cloud Messaging</td>
            <td style={styles.td}>Delivering push notifications</td>
            <td style={styles.td}>Device push token</td>
          </tr>
          <tr>
            <td style={styles.td}>Firebase Crashlytics</td>
            <td style={styles.td}>Crash reporting to help us fix bugs</td>
            <td style={styles.td}>
              Device/app diagnostic data, not your personal expense data
            </td>
          </tr>
        </tbody>
      </table>
      <p style={styles.p}>
        We do not share your personal data with any other third party for their
        own marketing purposes.
      </p>

      <h2 style={styles.h2}>5. Data Security</h2>
      <ul style={styles.ul}>
        <li style={styles.li}>
          Passwords are stored using one-way bcrypt hashing — we cannot recover
          or view your original password.
        </li>
        <li style={styles.li}>
          All communication between the app and our servers is encrypted in
          transit via HTTPS/TLS.
        </li>
        <li style={styles.li}>
          Access and refresh tokens are stored securely on your device using
          platform-provided secure storage (Android Keystore / iOS Keychain via
          Expo SecureStore).
        </li>
        <li style={styles.li}>
          We apply rate-limiting and authentication safeguards to protect
          against abuse and unauthorized access.
        </li>
      </ul>
      <p style={styles.p}>
        No method of transmission or storage is 100% secure, and we cannot
        guarantee absolute security, but we take reasonable, industry-standard
        measures to protect your information.
      </p>

      <h2 style={styles.h2}>6. Data Retention</h2>
      <p style={styles.p}>
        We retain your account and financial-record data for as long as your
        account is active. If you delete your account, we will delete or
        anonymize your personal information within 7 days, except where
        we&apos;re required to retain certain records by law, or where data is
        necessary to preserve the integrity of other users&apos; shared
        financial records (e.g. an expense you were involved in may remain
        visible to other group members in an anonymized form, since deleting it
        would corrupt their balance history).
      </p>

      <h2 style={styles.h2}>7. Your Rights and Choices</h2>
      <p style={styles.p}>
        Depending on your location, you may have the right to:
      </p>
      <ul style={styles.ul}>
        <li style={styles.li}>Access the personal data we hold about you</li>
        <li style={styles.li}>Correct inaccurate data</li>
        <li style={styles.li}>
          Request deletion of your account and associated data
        </li>
        <li style={styles.li}>
          Withdraw consent for optional data (e.g. phone number) at any time by
          removing it from your profile
        </li>
      </ul>
      <p style={styles.p}>
        To exercise any of these rights, contact us at{" "}
        <a href="mailto:help@splenza.in" style={styles.a}>
          help@splenza.in
        </a>
        .
      </p>

      <h2 style={styles.h2}>8. Children&apos;s Privacy</h2>
      <p style={styles.p}>
        Splenza is not directed at children under 13 (or the relevant minimum
        age in your jurisdiction), and we do not knowingly collect personal
        information from children. If you believe a child has provided us with
        personal information, contact us and we will delete it.
      </p>

      <h2 style={styles.h2}>9. Changes to This Policy</h2>
      <p style={styles.p}>
        We may update this Privacy Policy from time to time. We will notify you
        of material changes via the app or email, and update the &quot;Last
        updated&quot; date above.
      </p>

      <h2 style={styles.h2}>10. Contact Us</h2>
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
  a: { color: "#4B4FE0", textDecoration: "none" },
  table: {
    width: "100%",
    borderCollapse: "collapse",
    margin: "16px 0",
    fontSize: 14,
  },
  th: {
    textAlign: "left",
    padding: "10px 12px",
    border: "1px solid #E5E5E5",
    background: "#F0F0FA",
    fontWeight: 700,
  },
  td: {
    textAlign: "left",
    padding: "10px 12px",
    border: "1px solid #E5E5E5",
    verticalAlign: "top",
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
